import AsyncStorage from "@react-native-async-storage/async-storage";
import { paths } from "./paths";

const TOKEN_KEY = "rest.tokens";

let accessToken = null;
let refreshToken = null;
let expiresIn = null;
let refreshPromise = null;

export class ApiError extends Error {
  constructor(message, { status = 0, code = null } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export function apiBase() {
  return process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "") || "";
}

export function isLiveApi() {
  return Boolean(apiBase());
}

export function getAccessToken() {
  return accessToken;
}

export function getTokens() {
  return accessToken ? { accessToken, refreshToken, expiresIn } : null;
}

export function setTokens(next) {
  accessToken = next?.accessToken ?? null;
  refreshToken = next?.refreshToken ?? null;
  expiresIn = next?.expiresIn ?? null;
}

async function storeRefreshed(next) {
  setTokens(next);
  try {
    await AsyncStorage.setItem(TOKEN_KEY, JSON.stringify(next));
  } catch {
    // Токены уже в памяти; запись повторится при следующем сохранении сессии.
  }
}

export async function refreshSession() {
  if (!refreshToken) {
    throw new ApiError("Нет сессии.", { status: 401 });
  }
  return refreshTokensOnce();
}

export async function loadTokens(storage) {
  const raw = await storage.getItem(TOKEN_KEY);
  if (!raw) {
    return null;
  }
  const parsed = JSON.parse(raw);
  setTokens(parsed);
  return parsed;
}

export async function saveTokens(storage, next) {
  setTokens(next);
  if (!next?.accessToken) {
    await storage.removeItem(TOKEN_KEY);
    return;
  }
  await storage.setItem(TOKEN_KEY, JSON.stringify(next));
}

function messageFromBody(body, fallback) {
  const list = Array.isArray(body?.violations) ? body.violations : body?.errors;
  const firstError = Array.isArray(list) ? list[0] : null;
  return (
    firstError?.defaultMessage ||
    firstError?.message ||
    body?.detail ||
    body?.message ||
    (body?.title && body.title !== "Invalid request content." ? body.title : null) ||
    fallback
  );
}

function fallbackMessage(status) {
  if (status >= 500) {
    return `Сервер не отвечает как надо (${status}). Попробуйте позже.`;
  }
  if (status === 403) {
    return "Нет доступа.";
  }
  return `Сервер ответил ${status}.`;
}

function refreshTokensOnce() {
  if (!refreshPromise) {
    refreshPromise = request(paths.refresh, {
      method: "POST",
      body: { refreshToken },
      auth: false,
      retry: false,
    })
      .then(async (next) => {
        if (!next?.accessToken) {
          throw new ApiError("Не удалось обновить сессию.", { status: 401 });
        }
        await storeRefreshed(next);
        return next;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function request(path, { method = "GET", body, auth = true, retry = true } = {}) {
  const base = apiBase();
  if (!base) {
    throw new ApiError("Сервер не задан.");
  }
  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  let response;
  try {
    response = await fetch(`${base}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError("Нет связи с сервером. Проверьте интернет.");
  }
  if (response.status === 401 && retry && refreshToken && path !== paths.refresh) {
    await refreshTokensOnce();
    return request(path, { method, body, auth, retry: false });
  }
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { message: text.slice(0, 240) };
    }
  }
  if (!response.ok) {
    throw new ApiError(messageFromBody(payload, fallbackMessage(response.status)), {
      status: response.status,
      code: payload?.code ?? payload?.errorCode ?? null,
    });
  }
  return payload;
}
