import { canSetTableStatus, statusLabel } from "./catalog.js";
import { clone, fail, managersOf, notify, ok, table, take, userById } from "./core.js";

export function createHall(state, { actorId, name }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Зал создаёт управляющий.");
  }
  if (!name.trim()) {
    return fail("Введите название зала.");
  }
  const next = clone(state);
  const id = take(next);
  next.halls.push({ id, venueId: actor.venueId, name: name.trim(), deletedAt: null });
  return ok(next, { hallId: id });
}

export function createTable(state, { actorId, hallId, number, seats }) {
  const actor = userById(state, actorId);
  const parsedNumber = Number(number);
  const parsedSeats = Number(seats);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Столы добавляет управляющий.");
  }
  if (!Number.isInteger(parsedNumber) || parsedNumber < 1) {
    return fail("Номер стола должен быть целым числом.");
  }
  if (!Number.isInteger(parsedSeats) || parsedSeats < 1) {
    return fail("Укажите количество мест.");
  }
  const hall = state.halls.find((item) => item.id === hallId && !item.deletedAt);
  if (!hall || hall.venueId !== actor.venueId) {
    return fail("Зал не найден.");
  }
  if (state.tables.some((item) => item.hallId === hallId && !item.deletedAt && item.number === parsedNumber)) {
    return fail("Такой номер уже есть в этом зале.");
  }
  const next = clone(state);
  const id = take(next);
  next.tables.push(table(id, hallId, parsedNumber, parsedSeats, "READY", null));
  return ok(next, { tableId: id });
}

export function updateTable(state, { actorId, tableId, number, seats }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Стол правит управляющий.");
  }
  const next = clone(state);
  const item = next.tables.find((row) => row.id === tableId && !row.deletedAt);
  if (!item) {
    return fail("Стол не найден.");
  }
  if (number !== undefined) {
    const parsed = Number(number);
    if (!Number.isInteger(parsed) || parsed < 1) {
      return fail("Номер стола должен быть целым числом.");
    }
    if (next.tables.some((row) => row.id !== item.id && row.hallId === item.hallId && !row.deletedAt && row.number === parsed)) {
      return fail("Такой номер уже есть в этом зале.");
    }
    item.number = parsed;
  }
  if (seats !== undefined) {
    const parsed = Number(seats);
    if (!Number.isInteger(parsed) || parsed < 1) {
      return fail("Укажите количество мест.");
    }
    item.seats = parsed;
  }
  return ok(next, { tableId });
}

export function assignTable(state, { actorId, tableId, assigneeId }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Официанта закрепляет управляющий.");
  }
  const next = clone(state);
  const item = next.tables.find((row) => row.id === tableId && !row.deletedAt);
  if (!item) {
    return fail("Стол не найден.");
  }
  const assignee = assigneeId ? userById(next, assigneeId) : null;
  if (assigneeId && (!assignee || assignee.venueId !== actor.venueId)) {
    return fail("Сотрудник не найден.");
  }
  item.assigneeId = assigneeId || null;
  if (assignee) {
    notify(next, {
      userId: assignee.id,
      title: "Вас закрепили за столом",
      body: `Стол ${item.number}`,
      kind: "table",
    });
  }
  return ok(next, { tableId });
}

export function setTableStatus(state, { userId, tableId, status, comment }) {
  const next = clone(state);
  const user = userById(next, userId);
  const item = next.tables.find((row) => row.id === tableId && !row.deletedAt);
  if (!user || !item) {
    return fail("Стол не найден.");
  }
  if (!canSetTableStatus(user, item, status)) {
    return fail("Эта смена статуса вам недоступна.");
  }
  if (status === "PROBLEM" && !comment?.trim()) {
    return fail("Опишите проблему.");
  }
  const previous = item.status;
  item.status = status;
  item.problem = status === "PROBLEM" ? comment.trim() : null;
  next.history.unshift({
    id: take(next),
    tableId,
    userId,
    oldStatus: previous,
    newStatus: status,
    comment: comment?.trim() || "",
    createdAt: new Date().toISOString(),
  });
  const text = `Стол ${item.number}: ${statusLabel(status)}`;
  for (const manager of managersOf(next, user.venueId)) {
    if (manager.id !== userId) {
      notify(next, { userId: manager.id, title: "Стол сменил статус", body: `${user.fullName}: ${text}`, kind: "table" });
    }
  }
  if (item.assigneeId && item.assigneeId !== userId) {
    notify(next, { userId: item.assigneeId, title: "Стол сменил статус", body: text, kind: "table" });
  }
  return ok(next, { tableId });
}
