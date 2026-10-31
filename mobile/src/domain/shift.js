import { clone, fail, notify, ok, take, userById } from "./core.js";
import { todayKey } from "./format.js";

export function openShiftOf(state, venueId) {
  return (
    state.shifts.find((shift) => String(shift.venueId) === String(venueId) && shift.status === "ACTIVE") ?? null
  );
}

export function shiftLane(person) {
  return person?.position === "RUNNER" || person?.role === "RUNNER" ? "runner" : "waiter";
}

export function plannedWaiterId(table) {
  return table.draftWaiterId !== undefined ? table.draftWaiterId : table.assigneeId;
}

export function plannedRunnerId(table) {
  return table.draftRunnerId !== undefined ? table.draftRunnerId : table.runnerId;
}

// Повторяет правило сервера (ShiftService.configure/start): в смене хотя бы один сотрудник,
// у каждого есть стол, каждый активный стол закреплён за одним официантом и одним раннером из состава.
export function shiftPlanGaps({ members, tables }) {
  const gaps = [];
  if (!members.length) {
    return ["Добавьте в смену хотя бы одного сотрудника."];
  }
  if (!tables.length) {
    return ["В зале нет столов — сначала добавьте столы."];
  }
  const picked = new Map(members.map((person) => [String(person.id), person]));
  const tablesOf = new Map(members.map((person) => [String(person.id), 0]));
  for (const table of tables) {
    const waiterId = plannedWaiterId(table);
    const runnerId = plannedRunnerId(table);
    const waiter = waiterId != null && picked.has(String(waiterId));
    const runner = runnerId != null && picked.has(String(runnerId));
    if (waiter) {
      tablesOf.set(String(waiterId), tablesOf.get(String(waiterId)) + 1);
    }
    if (runner) {
      tablesOf.set(String(runnerId), tablesOf.get(String(runnerId)) + 1);
    }
    if (!waiter && !runner) {
      gaps.push(`Стол ${table.number}: нет официанта и раннера.`);
    } else if (!waiter) {
      gaps.push(`Стол ${table.number}: нет официанта.`);
    } else if (!runner) {
      gaps.push(`Стол ${table.number}: нет раннера.`);
    }
  }
  for (const person of members) {
    if (person.role === "MANAGER") {
      gaps.push(`${person.fullName}: в смену со столами берут только официантов и раннеров.`);
    } else if (!tablesOf.get(String(person.id))) {
      gaps.push(`${person.fullName}: не закреплён ни один стол.`);
    }
  }
  return gaps;
}

export function openShift(state, { actorId, start, end, memberIds }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Смену открывает управляющий.");
  }
  const date = todayKey();
  if (state.shifts.some((shift) => shift.venueId === actor.venueId && shift.status === "ACTIVE")) {
    return fail("В ресторане уже есть активная смена.");
  }
  const next = clone(state);
  const shiftId = take(next);
  const members = memberIds?.length ? memberIds : [actorId];
  next.shifts.push({
    id: shiftId,
    venueId: actor.venueId,
    date,
    start: start || "12:00",
    end: end || "23:00",
    leadId: actorId,
    status: "ACTIVE",
    openedAt: new Date().toISOString(),
    closedAt: null,
    report: null,
  });
  for (const memberId of members) {
    next.attendance.push({ id: take(next), shiftId, userId: memberId, present: true });
    if (memberId !== actorId) {
      notify(next, {
        userId: memberId,
        title: "Вас добавили в смену",
        body: `${start || "12:00"}-${end || "23:00"}`,
        kind: "shift",
      });
    }
  }
  return ok(next, { shiftId });
}

export function setShiftMembers(state, { actorId, shiftId, memberIds }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Состав меняет управляющий.");
  }
  const next = clone(state);
  const shift = next.shifts.find((item) => item.id === shiftId && item.status === "ACTIVE");
  if (!shift || shift.venueId !== actor.venueId) {
    return fail("Активная смена не найдена.");
  }
  const previous = new Set(next.attendance.filter((row) => row.shiftId === shiftId).map((row) => row.userId));
  next.attendance = next.attendance.filter((row) => row.shiftId !== shiftId);
  for (const memberId of memberIds) {
    next.attendance.push({ id: take(next), shiftId, userId: memberId, present: true });
    if (!previous.has(memberId) && memberId !== actorId) {
      notify(next, { userId: memberId, title: "Вас добавили в смену", body: `${shift.start}-${shift.end}`, kind: "shift" });
    }
  }
  return ok(next, { shiftId });
}

export function leaveShift(state, userId) {
  const next = clone(state);
  const user = userById(next, userId);
  if (!user) {
    return fail("Пользователь не найден.");
  }
  const shift = openShiftOf(next, user.venueId);
  if (shift) {
    const row = next.attendance.find((item) => item.shiftId === shift.id && item.userId === userId);
    if (row) {
      row.present = false;
    }
  }
  return ok(next, { userId });
}

export function closeShift(state, { actorId, unfinishedTasks }) {
  const actor = userById(state, actorId);
  if (!actor || actor.role !== "MANAGER") {
    return fail("Смену закрывает управляющий.");
  }
  const next = clone(state);
  const shift = openShiftOf(next, actor.venueId);
  if (!shift) {
    return fail("Активной смены нет.");
  }
  const openTasks = next.tasks.filter(
    (item) => item.venueId === actor.venueId && !item.deletedAt && item.status !== "COMPLETED" && item.status !== "CANCELLED",
  );
  const decisions = new Map((unfinishedTasks ?? []).map((item) => [item.taskId, item]));
  if (openTasks.some((item) => !decisions.has(item.id))) {
    return fail("Для каждой незавершённой задачи нужно решение.");
  }
  let carried = 0;
  let cancelled = 0;
  for (const item of openTasks) {
    const action = decisions.get(item.id).action;
    if (action === "CANCEL") {
      item.status = "CANCELLED";
      item.resolution = decisions.get(item.id).comment || "Отменена при закрытии смены";
      cancelled += 1;
      if (item.assigneeId) {
        notify(next, { userId: item.assigneeId, title: "Задача отменена", body: item.title, kind: "task" });
      }
    } else if (action === "CARRY_OVER") {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      tomorrow.setHours(12, 0, 0, 0);
      item.status = "CREATED";
      item.assigneeId = null;
      item.shiftId = null;
      item.dueAt = tomorrow.toISOString();
      item.overdueNotified = false;
      carried += 1;
    } else {
      return fail("Решение: перенести или отменить.");
    }
  }
  const done = next.tasks.filter(
    (item) => item.venueId === actor.venueId && !item.deletedAt && item.status === "COMPLETED" && item.createdAt.slice(0, 10) === shift.date,
  ).length;
  shift.status = "CLOSED";
  shift.closedAt = new Date().toISOString();
  shift.report = { created: done + openTasks.length, done, carried, cancelled };
  for (const row of next.attendance.filter((item) => item.shiftId === shift.id)) {
    row.present = false;
  }
  notify(next, { userId: actor.id, title: "Смена закрыта", body: `Выполнено ${done} из ${shift.report.created}`, kind: "shift" });
  return ok(next, { report: shift.report, shiftId: shift.id });
}
