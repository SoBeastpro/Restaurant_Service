import { clone, fail, managersOf, notify, ok, take, task, userById } from "./core.js";
import { todayKey } from "./format.js";

export function createTask(state, input) {
  const actor = userById(state, input.actorId);
  if (!actor) {
    return fail("Пользователь не найден.");
  }
  if (!input.title?.trim()) {
    return fail("Введите название задачи.");
  }
  const next = clone(state);
  const id = take(next);
  const assigneeId = input.assigneeId || null;
  const row = task({
    id,
    venueId: actor.venueId,
    title: input.title.trim(),
    description: input.description?.trim() || "",
    type: input.type || "GENERAL",
    status: "CREATED",
    creatorId: actor.id,
    assigneeId,
    tableId: input.tableId || null,
    priority: input.priority || "normal",
    createdAt: new Date().toISOString(),
    dueAt: input.dueAt || null,
  });
  next.tasks.unshift(row);
  if (assigneeId && assigneeId !== actor.id) {
    notify(next, { userId: assigneeId, title: "Вам назначена задача", body: row.title, kind: "task" });
  }
  if (input.saveFavorite) {
    next.favorites.unshift({
      id: take(next),
      venueId: actor.venueId,
      title: row.title,
      description: row.description,
      type: row.type,
      priority: row.priority,
      deletedAt: null,
    });
  }
  return ok(next, { taskId: id });
}

export function createTaskFromFavorite(state, { actorId, favoriteId, assigneeId, tableId }) {
  const favorite = state.favorites.find((item) => item.id === favoriteId && !item.deletedAt);
  if (!favorite) {
    return fail("Шаблон не найден.");
  }
  return createTask(state, {
    actorId,
    title: favorite.title,
    description: favorite.description,
    type: favorite.type,
    priority: favorite.priority,
    assigneeId,
    tableId,
    saveFavorite: false,
  });
}

export function deleteFavorite(state, { actorId, favoriteId }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Шаблоны хранит управляющий.");
  }
  const next = clone(state);
  const favorite = next.favorites.find((item) => item.id === favoriteId);
  if (!favorite) {
    return fail("Шаблон не найден.");
  }
  favorite.deletedAt = new Date().toISOString();
  return ok(next, { favoriteId });
}

export function moveTask(state, { actorId, taskId, status }) {
  const next = clone(state);
  const actor = userById(next, actorId);
  const item = next.tasks.find((row) => row.id === taskId && !row.deletedAt);
  if (!actor || !item || item.venueId !== actor.venueId) {
    return fail("Задача не найдена.");
  }
  if (!canMoveTask(actor, item, status)) {
    return fail("Этот переход статуса вам недоступен.");
  }
  if (status === "IN_PROGRESS" && !item.assigneeId) {
    item.assigneeId = actor.id;
  }
  item.status = status;
  if (status === "IN_PROGRESS" && !item.startedAt) {
    item.startedAt = new Date().toISOString();
  }
  if (status === "COMPLETED") {
    item.doneAt = new Date().toISOString();
  }
  const watchers = new Set([item.creatorId, item.assigneeId]);
  for (const manager of managersOf(next, item.venueId)) {
    watchers.add(manager.id);
  }
  watchers.delete(actor.id);
  const title = status === "COMPLETED" ? "Задача выполнена" : status === "CANCELLED" ? "Задача отменена" : "Задача изменена";
  for (const userId of watchers) {
    if (userId) {
      notify(next, { userId, title, body: item.title, kind: "task" });
    }
  }
  return ok(next, { taskId });
}

export function canMoveTask(actor, item, status) {
  if (!actor || item.deletedAt) {
    return false;
  }
  if (status === "CANCELLED" || (status === "COMPLETED" && actor.role === "MANAGER")) {
    return actor.role === "MANAGER" && (item.status === "CREATED" || item.status === "IN_PROGRESS");
  }
  const owns = item.assigneeId === actor.id || (item.assigneeId == null && item.status === "CREATED") || actor.role === "MANAGER";
  if (!owns) {
    return false;
  }
  if (status === "IN_PROGRESS") {
    return item.status === "CREATED";
  }
  if (status === "COMPLETED") {
    return item.status === "IN_PROGRESS" && item.assigneeId === actor.id;
  }
  return false;
}

export function saveDayPlan(state, { actorId, userId, items }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("План составляет управляющий.");
  }
  const texts = items.map((text) => text.trim()).filter(Boolean);
  if (!texts.length) {
    return fail("Добавьте хотя бы один пункт.");
  }
  const next = clone(state);
  const date = todayKey();
  let plan = next.plans.find((item) => item.userId === userId && item.date === date && item.venueId === actor.venueId);
  if (!plan) {
    plan = { id: take(next), venueId: actor.venueId, userId, date, items: [] };
    next.plans.push(plan);
  }
  plan.items = texts.map((text) => {
    const existing = plan.items.find((item) => item.text === text);
    return { id: existing?.id ?? take(next), text, done: existing?.done ?? false };
  });
  return ok(next, { planId: plan.id });
}

export function togglePlanItem(state, { actorId, planId, itemId }) {
  const next = clone(state);
  const actor = userById(next, actorId);
  const plan = next.plans.find((item) => item.id === planId);
  if (!actor || !plan) {
    return fail("Пункт не найден.");
  }
  if (actor.role !== "MANAGER" && plan.userId !== actor.id) {
    return fail("Это чужой план.");
  }
  const item = plan.items.find((row) => row.id === itemId);
  if (!item) {
    return fail("Пункт не найден.");
  }
  item.done = !item.done;
  return ok(next, { planId });
}

export function markNotificationRead(state, { userId, notificationId }) {
  const next = clone(state);
  const item = next.notifications.find((row) => row.id === notificationId && row.userId === userId);
  if (item) {
    item.read = true;
  }
  return ok(next, { notificationId });
}

export function markAllNotificationsRead(state, userId) {
  const next = clone(state);
  for (const item of next.notifications) {
    if (item.userId === userId) {
      item.read = true;
    }
  }
  return ok(next, { userId });
}

export function sweepOverdue(state, now = new Date()) {
  const next = state.notifications ? state : clone(state);
  for (const item of next.tasks) {
    if (item.deletedAt || item.overdueNotified || !item.dueAt) {
      continue;
    }
    if (item.status === "COMPLETED" || item.status === "CANCELLED") {
      continue;
    }
    if (new Date(item.dueAt) >= now) {
      continue;
    }
    item.overdueNotified = true;
    const targets = new Set([item.assigneeId, item.creatorId]);
    for (const manager of managersOf(next, item.venueId)) {
      targets.add(manager.id);
    }
    for (const userId of targets) {
      notify(next, { userId, title: "Задача просрочена", body: item.title, kind: "task" });
    }
  }
  return next;
}
