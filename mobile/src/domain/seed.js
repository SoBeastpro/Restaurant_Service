import { permissionsFor } from "./catalog.js";
import { STORE_VERSION, favorite, hashPassword, table, task } from "./core.js";
import { atTime, todayKey } from "./format.js";

const DEMO_PASSWORD = "smena";

export function createSeed(now = new Date()) {
  const passwordHash = hashPassword(DEMO_PASSWORD);
  return {
    version: STORE_VERSION,
    seq: 100,
    venues: [
      {
        id: 1,
        name: "Клод Моне",
        address: "Невский 48",
        about: "Учебная смена",
        hours: "12:00-23:00",
        subscription: "active",
        setupStep: "done",
        ownerId: 1,
      },
    ],
    users: [
      person(1, "irina", "irina@gosti.bar", "Ирина Ковалёва", "MANAGER", passwordHash, now),
      person(2, "anna", "anna@gosti.bar", "Анна Соколова", "WAITER", passwordHash, now),
      person(3, "maxim", "maxim@gosti.bar", "Максим Орлов", "RUNNER", passwordHash, now),
      person(4, "sergey", "sergey@gosti.bar", "Сергей Белов", "RUNNER", passwordHash, now),
    ],
    halls: [{ id: 1, venueId: 1, name: "Основной", deletedAt: null }],
    tables: [
      table(1, 1, 1, 4, "READY", 2),
      table(2, 1, 2, 2, "NEEDS_CLEANING", 2),
      table(3, 1, 3, 4, "NEEDS_SERVING", 2),
      table(4, 1, 4, 4, "PROBLEM", 2, "Нет приборов на двоих"),
      table(5, 1, 5, 6, "NEEDS_CLEANING", null),
      table(6, 1, 6, 2, "READY", null),
      table(7, 1, 7, 4, "NEEDS_CLEANING", 2),
      table(8, 1, 8, 4, "NEEDS_SERVING", null),
    ],
    history: [
      historyRow(1, 7, 2, "READY", "NEEDS_CLEANING", "", atTime(now, 10, 15)),
      historyRow(2, 7, 3, "NEEDS_CLEANING", "NEEDS_SERVING", "", atTime(now, 10, 21)),
      historyRow(3, 7, 2, "NEEDS_SERVING", "NEEDS_CLEANING", "", atTime(now, 10, 28)),
      historyRow(4, 4, 2, "NEEDS_SERVING", "PROBLEM", "Нет приборов на двоих", atTime(now, 10, 40)),
      historyRow(5, 2, 2, "READY", "NEEDS_CLEANING", "", atTime(now, 9, 50, -1)),
    ],
    shifts: [
      {
        id: 2,
        venueId: 1,
        date: todayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)),
        start: "12:00",
        end: "23:00",
        leadId: 1,
        status: "CLOSED",
        openedAt: atTime(now, 12, 0, -1),
        closedAt: atTime(now, 23, 5, -1),
        report: { created: 6, done: 5, carried: 1, cancelled: 0, left: 0 },
      },
      {
        id: 3,
        venueId: 1,
        date: todayKey(now),
        start: "12:00",
        end: "23:00",
        leadId: 1,
        status: "ACTIVE",
        openedAt: atTime(now, 11, 50),
        closedAt: null,
        report: null,
      },
    ],
    attendance: [
      { id: 1, shiftId: 2, userId: 1, present: true },
      { id: 2, shiftId: 2, userId: 2, present: true },
      { id: 3, shiftId: 2, userId: 3, present: true },
      { id: 4, shiftId: 2, userId: 4, present: true },
      { id: 5, shiftId: 3, userId: 1, present: true },
      { id: 6, shiftId: 3, userId: 2, present: true },
      { id: 7, shiftId: 3, userId: 3, present: true },
      { id: 8, shiftId: 3, userId: 4, present: true },
    ],
    tasks: [
      task({
        id: 1,
        title: "Вынести блюда",
        description: "Стол 4, горячее",
        type: "RUNNER",
        status: "IN_PROGRESS",
        creatorId: 2,
        assigneeId: 3,
        tableId: 4,
        priority: "high",
        createdAt: atTime(now, 12, 10),
        dueAt: atTime(now, 12, 40),
        startedAt: atTime(now, 12, 16),
      }),
      task({
        id: 2,
        title: "Сервировка зала",
        description: "Проверить приборы до посадки",
        type: "CLEAN",
        status: "CREATED",
        creatorId: 1,
        assigneeId: 3,
        tableId: null,
        priority: "normal",
        createdAt: atTime(now, 11, 40),
        dueAt: atTime(now, 13, 0),
      }),
      task({
        id: 3,
        title: "Проверить холодильники",
        description: "Температура и сроки",
        type: "GENERAL",
        status: "COMPLETED",
        creatorId: 1,
        assigneeId: 1,
        tableId: null,
        priority: "normal",
        createdAt: atTime(now, 11, 20),
        dueAt: atTime(now, 12, 0),
        startedAt: atTime(now, 11, 25),
        doneAt: atTime(now, 11, 42),
      }),
      task({
        id: 4,
        title: "Коктейли на стол 2",
        description: "Два бокала, без льда",
        type: "BAR",
        status: "CREATED",
        creatorId: 2,
        assigneeId: 4,
        tableId: 2,
        priority: "high",
        createdAt: atTime(now, 12, 5),
        dueAt: atTime(now, 12, 25),
      }),
      task({
        id: 5,
        title: "Унести поднос",
        description: "Осталось со вчера",
        type: "RUNNER",
        status: "CREATED",
        creatorId: 1,
        assigneeId: null,
        tableId: 7,
        priority: "low",
        createdAt: atTime(now, 18, 0, -1),
        dueAt: atTime(now, 20, 0, -1),
        overdueNotified: true,
      }),
    ],
    favorites: [
      favorite(1, "Проверить холодильники", "Температура и сроки", "GENERAL", "normal"),
      favorite(2, "Проверить санузлы", "Бумага, мыло, пол", "CLEAN", "normal"),
      favorite(3, "Проверить склад", "Ход и маркировка", "GENERAL", "low"),
    ],
    plans: [
      {
        id: 1,
        venueId: 1,
        userId: 2,
        date: todayKey(now),
        items: [
          { id: 1, text: "Брифинг перед залом", done: true },
          { id: 2, text: "Столы 1, 2, 3 и 7", done: false },
          { id: 3, text: "Контроль статусов", done: false },
          { id: 4, text: "Закрытие своих столов", done: false },
        ],
      },
    ],
    notifications: [
      note(1, 3, "Вам назначена задача", "Вынести блюда", "task", atTime(now, 12, 10), true),
      note(2, 4, "Вам назначена задача", "Коктейли на стол 2", "task", atTime(now, 12, 5), false),
      note(3, 1, "Стол сменил статус", "Анна Соколова: стол 4, Проблема", "table", atTime(now, 10, 40), false),
      note(4, 2, "Вас добавили в смену", "12:00-23:00", "shift", atTime(now, 11, 50), true),
    ],
  };
}

function person(id, username, email, fullName, role, passwordHash, now) {
  const [firstName, ...rest] = fullName.split(" ");
  return {
    id,
    username,
    email,
    passwordHash,
    firstName,
    lastName: rest.join(" "),
    fullName,
    role,
    venueId: 1,
    status: "ACTIVE",
    permissions: permissionsFor(role),
    photo: null,
    emailConfirmed: true,
    confirmCode: null,
    invitationToken: null,
    resetToken: null,
    inviteUses: 0,
    createdAt: atTime(now, 9, 0, -10),
    deletedAt: null,
    lastActiveAt: atTime(now, 11, 0),
  };
}

function historyRow(id, tableId, userId, oldStatus, newStatus, comment, createdAt) {
  return { id, tableId, userId, oldStatus, newStatus, comment, createdAt };
}

function note(id, userId, title, body, kind, createdAt, read) {
  return { id, userId, title, body, kind, createdAt, read };
}
