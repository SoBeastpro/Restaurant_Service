import AsyncStorage from "@react-native-async-storage/async-storage";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AppState } from "react-native";
import { api, hydrateStore, loginOnServer, logoutOnServer, restoreLiveSession, subscribe } from "./api/store";

const USER_KEY = "rest.session";
const INTRO_KEY = "rest.intro";
const IDLE_MS = 12 * 60 * 60 * 1000;
const SessionContext = createContext(null);

function samePublic(left, right) {
  if (!left || !right) {
    return false;
  }
  return (
    left.id === right.id &&
    left.fullName === right.fullName &&
    left.role === right.role &&
    left.venue === right.venue &&
    left.setupStep === right.setupStep &&
    left.subscription === right.subscription &&
    left.status === right.status &&
    left.photo === right.photo &&
    left.emailConfirmed === right.emailConfirmed &&
    JSON.stringify(left.permissions) === JSON.stringify(right.permissions)
  );
}

export function SessionProvider({ children }) {
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState(null);
  const [seenIntro, setSeenIntro] = useState(false);

  const persistUser = useCallback(async (next, seenAt = Date.now()) => {
    if (!next) {
      await AsyncStorage.removeItem(USER_KEY);
      return;
    }
    await AsyncStorage.setItem(USER_KEY, JSON.stringify({ ...next, seenAt }));
  }, []);

  useEffect(() => {
    let alive = true;
    (async () => {
      await hydrateStore();
      const [storedUser, storedIntro] = await Promise.all([
        AsyncStorage.getItem(USER_KEY),
        AsyncStorage.getItem(INTRO_KEY),
      ]);
      if (!alive) {
        return;
      }
      setSeenIntro(storedIntro === "1");
      if (storedUser) {
        const parsed = JSON.parse(storedUser);
        const seenAt = parsed.seenAt ?? 0;
        if (Date.now() - seenAt > IDLE_MS) {
          await AsyncStorage.removeItem(USER_KEY);
        } else {
          const restored = await restoreLiveSession(parsed);
          if (restored) {
            setUser(restored);
            await persistUser(restored, Date.now());
          } else {
            await AsyncStorage.removeItem(USER_KEY);
          }
        }
      }
      setReady(true);
    })();
    return () => {
      alive = false;
    };
  }, [persistUser]);

  useEffect(() => {
    return subscribe(() => {
      setUser((current) => {
        if (!current) {
          return current;
        }
        const fresh = api.publishUser(current.id);
        if (!fresh) {
          void AsyncStorage.removeItem(USER_KEY);
          return null;
        }
        if (samePublic(fresh, current)) {
          return current;
        }
        void persistUser(fresh, current.seenAt ?? Date.now());
        return { ...fresh, seenAt: current.seenAt };
      });
    });
  }, [persistUser]);

  useEffect(() => {
    const sub = AppState.addEventListener("change", (next) => {
      if (next !== "active") {
        return;
      }
      setUser((current) => {
        if (!current?.seenAt) {
          return current;
        }
        if (Date.now() - current.seenAt > IDLE_MS) {
          void AsyncStorage.removeItem(USER_KEY);
          return null;
        }
        const touched = { ...current, seenAt: Date.now() };
        void persistUser(touched, touched.seenAt);
        return touched;
      });
    });
    return () => sub.remove();
  }, [persistUser]);

  const finishIntro = useCallback(async () => {
    setSeenIntro(true);
    await AsyncStorage.setItem(INTRO_KEY, "1");
  }, []);

  const adopt = useCallback(
    async (next) => {
      const withSeen = { ...next, seenAt: Date.now() };
      setUser(withSeen);
      await persistUser(withSeen, withSeen.seenAt);
    },
    [persistUser],
  );

  const signIn = useCallback(
    async (loginName, password, profileType) => {
      const trimmed = loginName.trim();
      if (!trimmed || !password) {
        return "Введите логин и пароль.";
      }
      try {
        const remote = await loginOnServer(trimmed, password);
        if (remote === "bad") {
          return "Неверный логин или пароль.";
        }
        await adopt(remote);
        return null;
      } catch (error) {
        return error.message;
      }
    },
    [adopt],
  );

  const signOut = useCallback(async () => {
    const token = user?.accessToken;
    setUser(null);
    await AsyncStorage.removeItem(USER_KEY);
    await logoutOnServer(token);
  }, [user]);

  const value = useMemo(
    () => ({ ready, user, seenIntro, finishIntro, signIn, signOut, adopt }),
    [ready, user, seenIntro, finishIntro, signIn, signOut, adopt],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("SessionProvider is missing");
  }
  return value;
}
