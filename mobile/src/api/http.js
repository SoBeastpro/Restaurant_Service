import { paths } from "./paths";

const TOKEN_KEY = "rest.tokens";

let accessToken = null;
let refreshToken = null;

export function apiBase() {
  return process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "") || "";
}

export function isLiveApi() {
  return Boolean(apiBase());
}

export function getAccessToken() {
  return accessToken;
}

export function setTokens(next) {
  accessToken = next?.accessToken ?? null;
  refreshToken = next?.refreshToken ?? null;
}

export async function refreshSession() {
  if (!refreshToken) {
    throw new Error("Нет сессии.");
  }
  const next = await request(paths.refresh, {
    method: "POST",
    body: { refreshToken },
    auth: false,
    retry: false,
  });
  if (!next?.accessToken) {
    throw new Error("Не удалось обновить сессию.");
  }
  setTokens(next);
  return next;
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
  const firstError = Array.isArray(body?.errors) ? body.errors[0] : null;
  return (
    firstError?.defaultMessage ||
    firstError?.message ||
    body?.detail ||
    body?.message ||
    (body?.title && body.title !== "Invalid request content." ? body.title : null) ||
    fallback
  );
}

export async function request(path, { method = "GET", body, auth = true, retry = true } = {}) {
  const base = apiBase();
  if (!base) {
    throw new Error("Сервер не задан.");
  }
  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  const response = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 401 && retry && refreshToken && path !== paths.refresh) {
    const refreshed = await request(paths.refresh, {
      method: "POST",
      body: { refreshToken },
      auth: false,
      retry: false,
    });
    if (refreshed?.accessToken) {
      setTokens(refreshed);
      return request(path, { method, body, auth, retry: false });
    }
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
    throw new Error(messageFromBody(payload, `Сервер ответил ${response.status}.`));
  }
  return payload;
}
