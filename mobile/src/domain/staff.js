import { permissionsFor, roleLabel } from "./catalog.js";
import { clone, fail, notify, ok, publish, userById } from "./core.js";

export function updateStaff(state, { actorId, userId, fullName, role, permissions }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Карточку правит управляющий.");
  }
  if (!fullName.trim()) {
    return fail("Введите имя.");
  }
  const next = clone(state);
  const user = userById(next, userId);
  if (!user || user.venueId !== actor.venueId) {
    return fail("Сотрудник не найден.");
  }
  if (user.role === "MANAGER" && role !== "MANAGER") {
    const left = next.users.filter(
      (item) => item.venueId === user.venueId && item.role === "MANAGER" && !item.deletedAt && item.id !== user.id,
    );
    if (!left.length) {
      return fail("В заведении должен остаться управляющий.");
    }
  }
  const roleChanged = user.role !== role;
  const [given, ...family] = fullName.trim().split(" ");
  user.firstName = given;
  user.lastName = family.join(" ");
  user.fullName = fullName.trim();
  user.role = role;
  user.permissions = permissions ?? permissionsFor(role);
  if (roleChanged) {
    notify(next, { userId: user.id, title: "Вам изменили роль", body: roleLabel(role), kind: "people" });
  }
  return ok(next, publish(next, user));
}

export function blockStaff(state, { actorId, userId, blocked }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Блокирует управляющий.");
  }
  if (actorId === userId) {
    return fail("Нельзя заблокировать свою учётку.");
  }
  const next = clone(state);
  const user = userById(next, userId);
  if (!user || user.venueId !== actor.venueId) {
    return fail("Сотрудник не найден.");
  }
  user.status = blocked ? "BLOCKED" : user.passwordHash ? "ACTIVE" : "INVITED";
  return ok(next, { userId });
}

export function deleteStaff(state, { actorId, userId }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Удаляет управляющий.");
  }
  if (actorId === userId) {
    return fail("Нельзя удалить свою учётку.");
  }
  const next = clone(state);
  const user = userById(next, userId);
  if (!user || user.venueId !== actor.venueId) {
    return fail("Сотрудник не найден.");
  }
  user.deletedAt = new Date().toISOString();
  user.status = "ARCHIVED";
  return ok(next, { userId });
}
