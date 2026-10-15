export const TABLE_STATUSES = [
  { id: "NEEDS_CLEANING", label: "Убрать" },
  { id: "NEEDS_SERVING", label: "Засервировать" },
  { id: "READY", label: "Готов" },
  { id: "PROBLEM", label: "Проблема" },
];

export const TASK_STATUSES = [
  { id: "CREATED", label: "Создана" },
  { id: "IN_PROGRESS", label: "Выполняется" },
  { id: "COMPLETED", label: "Выполнена" },
  { id: "CANCELLED", label: "Отменена" },
];

export const ROLES = [
  { id: "MANAGER", label: "Управляющий" },
  { id: "WAITER", label: "Официант" },
  { id: "RUNNER", label: "Раннер" },
];

export const STAFF_ROLES = ROLES.filter((role) => role.id !== "MANAGER");

export const PROFILE_TYPES = [
  { id: "MANAGER", label: "Управляющий" },
  { id: "EMPLOYEE", label: "Сотрудник" },
];

export const SHIFT_STATUSES = [
  { id: "PLANNED", label: "Запланирована" },
  { id: "ACTIVE", label: "Идёт" },
  { id: "CLOSED", label: "Закрыта" },
  { id: "CANCELLED", label: "Отменена" },
];

export const CLOSE_ACTIONS = [
  { id: "CARRY_OVER", label: "На следующую смену" },
  { id: "CANCEL", label: "Отменить" },
];

export function roleLabel(role) {
  return ROLES.find((item) => item.id === role)?.label ?? role;
}

export function roleLine(person) {
  return `${roleLabel(person.role)}${person.status === "INVITED" ? ", приглашён" : ""}${person.status === "BLOCKED" ? ", блокировка" : ""}`;
}

export function statusLabel(id) {
  return TABLE_STATUSES.find((item) => item.id === id)?.label ?? id;
}

export function taskStatusLabel(id) {
  return TASK_STATUSES.find((item) => item.id === id)?.label ?? id;
}

export function shiftStatusLabel(id) {
  return SHIFT_STATUSES.find((item) => item.id === id)?.label ?? id;
}

export function permissionsFor(role) {
  if (role === "MANAGER") {
    return {
      tableStatuses: ["NEEDS_CLEANING", "NEEDS_SERVING", "READY", "PROBLEM"],
      ownTablesOnly: false,
    };
  }
  if (role === "WAITER") {
    return { tableStatuses: ["NEEDS_CLEANING", "NEEDS_SERVING"], ownTablesOnly: true };
  }
  if (role === "RUNNER") {
    return { tableStatuses: ["READY"], ownTablesOnly: false };
  }
  return { tableStatuses: [], ownTablesOnly: false };
}

export function canSetTableStatus(user, table, status) {
  if (!user || user.deletedAt || user.status === "BLOCKED" || user.status === "ARCHIVED") {
    return false;
  }
  const allowed = user.permissions?.tableStatuses ?? [];
  if (!allowed.includes(status)) {
    return false;
  }
  if (user.role !== "MANAGER" && user.permissions?.ownTablesOnly && table.assigneeId !== user.id) {
    return false;
  }
  return true;
}
