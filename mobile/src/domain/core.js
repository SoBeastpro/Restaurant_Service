export const STORE_VERSION = 3;

// Учебный хеш только для офлайн-демо. На сервере будет нормальный парольный хеш.
export function hashPassword(password) {
  let hash = 5381;
  const text = String(password);
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash << 5) + hash + text.charCodeAt(index);
  }
  return `h${hash >>> 0}`;
}

export function checkPassword(password, hash) {
  return Boolean(hash) && hashPassword(password) === hash;
}

export function clone(state) {
  return JSON.parse(JSON.stringify(state));
}

export function fail(error) {
  return { ok: false, error };
}

export function ok(state, data) {
  return { ok: true, state, data };
}

export function take(state) {
  const id = state.seq;
  state.seq += 1;
  return id;
}

// Учебный токен: не криптографический, только чтобы прототип выдавал разные ссылки.
export function makeToken(state) {
  const alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  state.tokenNonce = (state.tokenNonce ?? 0) + 1;
  let value = Date.now() + state.tokenNonce * 104729 + state.seq * 7919;
  let token = "";
  for (let index = 0; index < 24; index += 1) {
    token += alphabet[value % alphabet.length];
    value = Math.floor(value / alphabet.length) + 17 + index;
  }
  return token;
}

export function displayName(firstName, lastName) {
  return [firstName, lastName].filter(Boolean).join(" ").trim();
}

export function userById(state, id) {
  return state.users.find((user) => user.id === id && !user.deletedAt) ?? null;
}

export function venueOf(state, user) {
  return state.venues.find((venue) => venue.id === user?.venueId) ?? null;
}

export function publish(state, user) {
  if (!user || user.deletedAt) {
    return null;
  }
  const venue = venueOf(state, user);
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    firstName: user.firstName || "",
    lastName: user.lastName || "",
    fullName: user.fullName,
    role: user.role,
    position: user.role,
    venueId: user.venueId,
    restaurantId: user.venueId,
    venue: venue?.name || "Заведение",
    restaurantName: venue?.name || "Заведение",
    permissions: user.permissions,
    status: user.status,
    photo: user.photo,
    emailConfirmed: user.emailConfirmed,
    setupStep: user.role === "MANAGER" ? venue?.setupStep ?? "done" : "done",
    subscription: venue?.subscription ?? "none",
    createdAt: user.createdAt,
    invitationToken: user.invitationToken ?? null,
    invitationUrl: user.invitationToken ? `rest://invite/${user.invitationToken}` : null,
    qrContent: user.invitationToken ? `rest://invite/${user.invitationToken}` : null,
    inviteUses: user.inviteUses ?? 0,
  };
}

export function notify(state, { userId, title, body, kind }) {
  if (!userId) {
    return;
  }
  state.notifications.unshift({
    id: take(state),
    userId,
    title,
    body,
    kind,
    read: false,
    createdAt: new Date().toISOString(),
  });
}

export function managersOf(state, venueId) {
  return state.users.filter(
    (user) => user.venueId === venueId && user.role === "MANAGER" && !user.deletedAt && user.status !== "BLOCKED",
  );
}

export function findLogin(state, login) {
  const key = login.trim().toLowerCase();
  return (
    state.users.find(
      (user) =>
        !user.deletedAt &&
        (user.email.toLowerCase() === key || (user.username && user.username.toLowerCase() === key)),
    ) ?? null
  );
}

export function table(id, hallId, number, seats, status, assigneeId, problem = null) {
  return { id, hallId, number, seats, status, assigneeId, problem, deletedAt: null };
}

export function task(fields) {
  return {
    venueId: 1,
    description: "",
    overdueNotified: false,
    startedAt: null,
    doneAt: null,
    deletedAt: null,
    ...fields,
  };
}

export function favorite(id, title, description, type, priority) {
  return { id, venueId: 1, title, description, type, priority, deletedAt: null };
}
