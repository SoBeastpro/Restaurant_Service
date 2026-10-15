import AsyncStorage from "@react-native-async-storage/async-storage";
import { paths } from "./paths";
import {
  STORE_VERSION,
  blockStaff,
  buildView,
  changePassword,
  closeShift,
  confirmEmail,
  confirmPayment,
  createHall,
  createSeed,
  createStaff,
  createTable,
  createTask,
  createTaskFromFavorite,
  deleteFavorite,
  deleteStaff,
  finishSetup,
  finishStaffStep,
  forgotPassword,
  leaveShift,
  login,
  markAllNotificationsRead,
  markNotificationRead,
  moveTask,
  openShift,
  publish,
  redeemInvite,
  registerManager,
  resetPassword,
  saveDayPlan,
  saveFirstHall,
  saveVenue,
  setShiftMembers,
  setTableStatus,
  assignTable,
  statsFor,
  sweepOverdue,
  togglePlanItem,
  updateStaff,
  updateTable,
} from "../domain/engine";

const STORAGE_KEY = "rest.store.v1";

let state = createSeed();
const listeners = new Set();
let hydratePromise = null;

function emit() {
  for (const listener of listeners) {
    listener();
  }
}

export function getState() {
  return state;
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

async function persist() {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function hydrateStore() {
  if (!hydratePromise) {
    hydratePromise = (async () => {
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed?.version === STORE_VERSION && Array.isArray(parsed.users)) {
            state = sweepOverdue(parsed);
          }
        }
      } catch {
        state = createSeed();
      }
      await persist();
      emit();
    })();
  }
  return hydratePromise;
}

function commit(result) {
  if (!result.ok) {
    throw new Error(result.error);
  }
  state = result.state;
  emit();
  void persist();
  return result.data;
}

export const api = {
  view(sessionUser) {
    return buildView(state, sessionUser);
  },
  publishUser(userId) {
    return publish(state, state.users.find((user) => user.id === userId));
  },
  stats(venueId, period) {
    return statsFor(state, venueId, period);
  },
  login(loginName, password, profileType) {
    return commit(login(state, loginName, password, profileType));
  },
  registerManager(input) {
    return commit(registerManager(state, input));
  },
  confirmEmail(userId, code) {
    return commit(confirmEmail(state, userId, code));
  },
  confirmPayment(userId) {
    return commit(confirmPayment(state, userId));
  },
  saveVenue(userId, input) {
    return commit(saveVenue(state, userId, input));
  },
  saveFirstHall(userId, input) {
    return commit(saveFirstHall(state, userId, input));
  },
  finishStaffStep(userId) {
    return commit(finishStaffStep(state, userId));
  },
  finishSetup(userId) {
    return commit(finishSetup(state, userId));
  },
  createStaff(input) {
    return commit(createStaff(state, input));
  },
  redeemInvite(input) {
    return commit(redeemInvite(state, input));
  },
  forgotPassword(input) {
    return commit(forgotPassword(state, input));
  },
  resetPassword(input) {
    return commit(resetPassword(state, input));
  },
  changePassword(input) {
    return commit(changePassword(state, input));
  },
  createHall(input) {
    return commit(createHall(state, input));
  },
  createTable(input) {
    return commit(createTable(state, input));
  },
  updateTable(input) {
    return commit(updateTable(state, input));
  },
  assignTable(input) {
    return commit(assignTable(state, input));
  },
  setTableStatus(input) {
    return commit(setTableStatus(state, input));
  },
  openShift(input) {
    return commit(openShift(state, input));
  },
  setShiftMembers(input) {
    return commit(setShiftMembers(state, input));
  },
  leaveShift(userId) {
    return commit(leaveShift(state, userId));
  },
  closeShift(input) {
    return commit(closeShift(state, input));
  },
  createTask(input) {
    return commit(createTask(state, input));
  },
  createTaskFromFavorite(input) {
    return commit(createTaskFromFavorite(state, input));
  },
  deleteFavorite(input) {
    return commit(deleteFavorite(state, input));
  },
  moveTask(input) {
    return commit(moveTask(state, input));
  },
  saveDayPlan(input) {
    return commit(saveDayPlan(state, input));
  },
  togglePlanItem(input) {
    return commit(togglePlanItem(state, input));
  },
  updateStaff(input) {
    return commit(updateStaff(state, input));
  },
  blockStaff(input) {
    return commit(blockStaff(state, input));
  },
  deleteStaff(input) {
    return commit(deleteStaff(state, input));
  },
  markNotificationRead(input) {
    return commit(markNotificationRead(state, input));
  },
  markAllNotificationsRead(userId) {
    return commit(markAllNotificationsRead(state, userId));
  },
};

export function userName(userId) {
  return state.users.find((user) => user.id === userId)?.fullName ?? "Сотрудник";
}

export async function logoutOnServer(accessToken) {
  const base = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "");
  if (!base) {
    return;
  }
  try {
    await fetch(`${base}${paths.logout}`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    });
  } catch {
    // Выход локальный, даже если сервер не ответил.
  }
}

export async function loginOnServer(loginName, password, profileType) {
  const base = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "");
  if (!base) {
    return "offline";
  }
  try {
    const response = await fetch(`${base}${paths.login}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ login: loginName.trim(), password, profileType }),
    });
    if (response.status === 401 || response.status === 400 || response.status === 403) {
      return "bad";
    }
    if (!response.ok) {
      return "offline";
    }
    const payload = await response.json();
    if (!payload?.accessToken) {
      return "offline";
    }
    const key = loginName.trim().toLowerCase();
    const local = state.users.find(
      (user) => !user.deletedAt && (user.email?.toLowerCase() === key || user.username?.toLowerCase() === key),
    );
    if (!local) {
      return "nomatch";
    }
    return {
      ...publish(state, local),
      accessToken: payload.accessToken,
      refreshToken: payload.refreshToken,
      expiresIn: payload.expiresIn,
      restaurants: payload.restaurants ?? [],
    };
  } catch {
    return "offline";
  }
}
