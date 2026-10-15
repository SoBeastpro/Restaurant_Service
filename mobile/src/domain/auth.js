import { permissionsFor, roleLabel } from "./catalog.js";
import {
  checkPassword,
  clone,
  displayName,
  fail,
  findLogin,
  hashPassword,
  makeToken,
  managersOf,
  notify,
  ok,
  publish,
  table,
  take,
  userById,
  venueOf,
} from "./core.js";
import { sweepOverdue } from "./tasks.js";

export function login(state, loginName, password, profileType) {
  const user = findLogin(state, loginName);
  if (!user) {
    return fail("Неверный логин или пароль.");
  }
  if (user.status === "BLOCKED" || user.status === "ARCHIVED") {
    return fail("Аккаунт заблокирован.");
  }
  if (!user.passwordHash) {
    return fail("Сначала примите приглашение и задайте пароль.");
  }
  if (!checkPassword(password, user.passwordHash)) {
    return fail("Неверный логин или пароль.");
  }
  if (profileType === "MANAGER" && user.role !== "MANAGER") {
    return fail("Этот вход для управляющего.");
  }
  if (profileType === "EMPLOYEE" && user.role === "MANAGER") {
    return fail("Этот вход для сотрудника.");
  }
  const next = clone(state);
  const found = userById(next, user.id);
  found.lastActiveAt = new Date().toISOString();
  return ok(sweepOverdue(next), publish(next, found));
}

export function registerManager(state, { firstName, lastName, email, password }) {
  const mail = email.trim().toLowerCase();
  if (!String(firstName || "").trim() || !String(lastName || "").trim()) {
    return fail("Введите имя и фамилию.");
  }
  if (!mail.includes("@") || !mail.split("@")[1]) {
    return fail("Введите почту.");
  }
  if (String(password).length < 8) {
    return fail("Пароль должен быть не короче 8 символов.");
  }
  if (state.users.some((user) => !user.deletedAt && user.email.toLowerCase() === mail)) {
    return fail("Эта почта уже занята.");
  }
  const next = clone(state);
  const venueId = take(next);
  const userId = take(next);
  const code = makeToken(next);
  next.venues.push({
    id: venueId,
    name: "",
    address: "",
    about: "",
    subscription: "none",
    setupStep: "email",
    ownerId: userId,
  });
  next.users.push({
    id: userId,
    username: mail.split("@")[0],
    email: mail,
    passwordHash: hashPassword(password),
    firstName: firstName.trim(),
    lastName: lastName.trim(),
    fullName: displayName(firstName.trim(), lastName.trim()),
    role: "MANAGER",
    venueId,
    status: "ACTIVE",
    permissions: permissionsFor("MANAGER"),
    photo: null,
    emailConfirmed: false,
    confirmCode: code,
    invitationToken: null,
    resetToken: null,
    inviteUses: 0,
    createdAt: new Date().toISOString(),
    deletedAt: null,
    lastActiveAt: new Date().toISOString(),
  });
  return ok(next, { user: publish(next, userById(next, userId)), code });
}

export function confirmEmail(state, userId, code) {
  const next = clone(state);
  const user = userById(next, userId);
  if (!user) {
    return fail("Пользователь не найден.");
  }
  if (!user.emailConfirmed && String(code).trim() !== String(user.confirmCode)) {
    return fail("Токен не совпал.");
  }
  user.emailConfirmed = true;
  const venue = venueOf(next, user);
  if (venue?.setupStep === "email") {
    venue.setupStep = "pay";
  }
  return ok(next, publish(next, user));
}

export function confirmPayment(state, userId) {
  const next = clone(state);
  const user = userById(next, userId);
  const venue = venueOf(next, user);
  if (!user || !venue) {
    return fail("Заведение не найдено.");
  }
  venue.subscription = "active";
  if (venue.setupStep === "pay") {
    venue.setupStep = "venue";
  }
  return ok(next, publish(next, user));
}

export function saveVenue(state, userId, { name, address, about, hours }) {
  if (!name.trim()) {
    return fail("Введите название.");
  }
  const next = clone(state);
  const user = userById(next, userId);
  const venue = venueOf(next, user);
  if (!venue) {
    return fail("Заведение не найдено.");
  }
  venue.name = name.trim();
  venue.address = address.trim();
  venue.about = String(about ?? "").trim();
  if (hours != null) {
    venue.hours = String(hours).trim();
  }
  if (venue.setupStep === "venue") {
    venue.setupStep = "hall";
  }
  return ok(next, publish(next, user));
}

export function saveFirstHall(state, userId, { name, tableNumber, seats, tableCount }) {
  const hallName = name.trim();
  const number = Number(tableNumber ?? 1);
  const seatCount = Number(seats);
  const count = tableCount == null ? 1 : Number(tableCount);
  if (!hallName) {
    return fail("Введите название зала.");
  }
  if (!Number.isInteger(number) || number < 1) {
    return fail("Номер стола должен быть целым числом.");
  }
  if (!Number.isInteger(count) || count < 1 || count > 40) {
    return fail("Укажите число столов от 1 до 40.");
  }
  if (!Number.isInteger(seatCount) || seatCount < 1) {
    return fail("Укажите количество мест.");
  }
  const next = clone(state);
  const user = userById(next, userId);
  const venue = venueOf(next, user);
  if (!user || user.role !== "MANAGER" || !venue) {
    return fail("Зал создаёт управляющий.");
  }
  const hallId = take(next);
  next.halls.push({ id: hallId, venueId: venue.id, name: hallName, deletedAt: null });
  const firstNumber = tableCount == null ? number : 1;
  for (let offset = 0; offset < count; offset += 1) {
    next.tables.push(table(take(next), hallId, firstNumber + offset, seatCount, "READY", null));
  }
  if (venue.setupStep === "hall") {
    venue.setupStep = "staff";
  }
  return ok(next, publish(next, user));
}

export function finishStaffStep(state, userId) {
  return moveSetup(state, userId, "staff", "invite");
}

export function finishSetup(state, userId) {
  return moveSetup(state, userId, "invite", "done");
}

function moveSetup(state, userId, from, to) {
  const next = clone(state);
  const user = userById(next, userId);
  const venue = venueOf(next, user);
  if (!venue) {
    return fail("Заведение не найдено.");
  }
  if (venue.setupStep === from) {
    venue.setupStep = to;
  }
  return ok(next, publish(next, user));
}

export function createStaff(state, { actorId, firstName, lastName, position }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Сотрудников добавляет управляющий.");
  }
  if (!String(firstName || "").trim() || !String(lastName || "").trim()) {
    return fail("Введите имя и фамилию.");
  }
  if (position !== "WAITER" && position !== "RUNNER") {
    return fail("Должность: официант или раннер.");
  }
  const next = clone(state);
  const id = take(next);
  const token = makeToken(next);
  const expires = new Date();
  expires.setDate(expires.getDate() + 7);
  const given = firstName.trim();
  const family = lastName.trim();
  next.users.push({
    id,
    username: "",
    email: "",
    passwordHash: null,
    firstName: given,
    lastName: family,
    fullName: displayName(given, family),
    role: position,
    venueId: actor.venueId,
    status: "INVITED",
    permissions: permissionsFor(position),
    photo: null,
    emailConfirmed: false,
    confirmCode: null,
    invitationToken: token,
    resetToken: null,
    inviteUses: 0,
    invitationExpiresAt: expires.toISOString(),
    createdAt: new Date().toISOString(),
    deletedAt: null,
    lastActiveAt: null,
  });
  return ok(next, {
    userId: id,
    invitation: {
      id,
      employeeId: id,
      firstName: given,
      lastName: family,
      position,
      status: "PENDING",
      invitationUrl: `rest://invite/${token}`,
      qrContent: `rest://invite/${token}`,
      token,
      expiresAt: expires.toISOString(),
    },
  });
}

export function redeemInvite(state, { token, login: loginName, password }) {
  const raw = String(token || "").trim();
  const marker = "rest://invite/";
  const markerAt = raw.indexOf(marker);
  const key = markerAt >= 0 ? raw.slice(markerAt + marker.length) : raw;
  const next = clone(state);
  const user = next.users.find((item) => item.invitationToken === key && !item.deletedAt);
  if (!user || key.length < 20) {
    return fail("Приглашение не найдено.");
  }
  if (user.status === "BLOCKED" || user.status === "ARCHIVED") {
    return fail("Приглашение отозвано.");
  }
  if (user.invitationExpiresAt && new Date(user.invitationExpiresAt) < new Date()) {
    return fail("Срок приглашения истёк.");
  }
  if (user.inviteUses > 0 || user.status === "ACTIVE") {
    return fail("Это приглашение уже использовано.");
  }
  const name = String(loginName || "").trim();
  if (name.length < 3 || name.length > 100) {
    return fail("Логин должен быть от 3 до 100 символов.");
  }
  if (next.users.some((item) => item.id !== user.id && item.username && item.username.toLowerCase() === name.toLowerCase())) {
    return fail("Такой логин уже занят.");
  }
  if (String(password).length < 8) {
    return fail("Пароль должен быть не короче 8 символов.");
  }
  user.username = name;
  user.passwordHash = hashPassword(password);
  user.status = "ACTIVE";
  user.inviteUses = 1;
  user.emailConfirmed = true;
  user.lastActiveAt = new Date().toISOString();
  for (const manager of managersOf(next, user.venueId)) {
    notify(next, {
      userId: manager.id,
      title: "Сотрудник подключился",
      body: `${user.fullName}, ${roleLabel(user.role)}`,
      kind: "people",
    });
  }
  return ok(next, publish(next, user));
}

export function forgotPassword(state, { login: loginName }) {
  const user = findLogin(state, String(loginName || ""));
  const next = clone(state);
  if (!user || !user.passwordHash || user.status === "INVITED") {
    return ok(next, { token: null });
  }
  const token = makeToken(next);
  userById(next, user.id).resetToken = token;
  return ok(next, { token });
}

export function resetPassword(state, { token, newPassword }) {
  const key = String(token || "").trim();
  const user = state.users.find((item) => item.resetToken && item.resetToken === key && !item.deletedAt);
  if (!user) {
    return fail("Токен восстановления не подошёл.");
  }
  if (String(newPassword).length < 8) {
    return fail("Пароль должен быть не короче 8 символов.");
  }
  const next = clone(state);
  const found = userById(next, user.id);
  found.passwordHash = hashPassword(newPassword);
  found.resetToken = null;
  return ok(next, { userId: user.id });
}

export function changePassword(state, { userId, currentPassword, newPassword }) {
  const next = clone(state);
  const user = userById(next, userId);
  if (!user || !checkPassword(currentPassword, user.passwordHash)) {
    return fail("Текущий пароль не совпал.");
  }
  if (String(newPassword).length < 8) {
    return fail("Новый пароль должен быть не короче 8 символов.");
  }
  user.passwordHash = hashPassword(newPassword);
  return ok(next, { userId });
}
