import { permissionsFor } from "./catalog.js";
import { publish, userById } from "./core.js";
import { periodStart, todayKey } from "./format.js";
import { openShiftOf } from "./shift.js";

export function visibleTasks(state, user) {
  const rows = state.tasks.filter((item) => item.venueId === user.venueId && !item.deletedAt);
  if (user.role === "MANAGER") {
    return rows;
  }
  return rows.filter(
    (item) => item.assigneeId === user.id || item.creatorId === user.id || (item.assigneeId == null && item.status === "CREATED"),
  );
}

export function visibleTables(state, user) {
  const halls = state.halls.filter((hall) => hall.venueId === user.venueId && !hall.deletedAt);
  const hallIds = new Set(halls.map((hall) => hall.id));
  let rows = state.tables.filter((item) => hallIds.has(item.hallId) && !item.deletedAt);
  if (user.role !== "MANAGER" && user.permissions?.ownTablesOnly) {
    rows = rows.filter((item) => item.assigneeId === user.id);
  }
  return rows.sort((left, right) => left.number - right.number);
}

export function buildView(state, sessionUser) {
  if (!sessionUser) {
    return null;
  }
  const fresh = userById(state, sessionUser.id);
  const me = fresh ? publish(state, fresh) : sessionUser;
  const halls = state.halls.filter((hall) => hall.venueId === me.venueId && !hall.deletedAt);
  const people = state.users
    .filter((user) => user.venueId === me.venueId && !user.deletedAt)
    .map((user) => publish(state, user));
  const shift = openShiftOf(state, me.venueId);
  const onShift = shift
    ? state.attendance.filter((row) => row.shiftId === shift.id && row.present).map((row) => row.userId)
    : [];
  const tasks = visibleTasks(state, me);
  const allTasks = state.tasks.filter((item) => item.venueId === me.venueId && !item.deletedAt);
  const today = todayKey();
  const todayTasks = allTasks.filter(
    (item) => item.status !== "CANCELLED" && (item.createdAt.slice(0, 10) === today || (item.dueAt && item.dueAt.slice(0, 10) === today)),
  );
  const tables = visibleTables(state, me);
  const allTables = visibleTables(state, { ...me, role: "MANAGER", permissions: permissionsFor("MANAGER") });
  const plan = state.plans.find((item) => item.userId === me.id && item.date === today && item.venueId === me.venueId) ?? null;
  return {
    me,
    halls,
    people,
    shift,
    onShift,
    tasks,
    allTasks,
    tables,
    allTables,
    todayTasks,
    plan,
    favorites: state.favorites.filter((item) => item.venueId === me.venueId && !item.deletedAt),
    notifications: state.notifications.filter((item) => item.userId === me.id),
    history: state.history,
    shifts: state.shifts.filter((item) => item.venueId === me.venueId),
    attendance: state.attendance,
    plans: state.plans.filter((item) => item.venueId === me.venueId),
    confirmCode: fresh?.confirmCode ?? null,
  };
}

export function statsFor(state, venueId, period, now = new Date()) {
  const from = periodStart(period, now);
  const tasks = state.tasks.filter((item) => item.venueId === venueId && !item.deletedAt && new Date(item.createdAt) >= from);
  const done = tasks.filter((item) => item.status === "COMPLETED");
  const inProgress = tasks.filter((item) => item.status === "IN_PROGRESS");
  const halls = state.halls.filter((hall) => hall.venueId === venueId && !hall.deletedAt);
  const hallIds = new Set(halls.map((hall) => hall.id));
  const tables = state.tables.filter((item) => hallIds.has(item.hallId) && !item.deletedAt);
  const current = {};
  for (const item of tables) {
    current[item.status] = (current[item.status] ?? 0) + 1;
  }
  return {
    created: tasks.length,
    done: done.length,
    inProgress: inProgress.length,
    current,
  };
}

export function nameOf(state, userId) {
  return state.users.find((user) => user.id === userId)?.fullName ?? "Сотрудник";
}
