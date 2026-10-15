import {
  assignTable,
  closeShift,
  confirmEmail,
  confirmPayment,
  createSeed,
  createStaff,
  createTask,
  createTaskFromFavorite,
  finishSetup,
  finishStaffStep,
  forgotPassword,
  login,
  moveTask,
  redeemInvite,
  registerManager,
  resetPassword,
  saveFirstHall,
  saveVenue,
  setTableStatus,
} from "../src/domain/engine.js";

const now = new Date(2026, 9, 3, 13, 0, 0);
let state = createSeed(now);

function must(result, label) {
  if (!result.ok) {
    throw new Error(`${label}: ${result.error}`);
  }
  state = result.state;
  return result.data;
}

function fail(result, label) {
  if (result.ok) {
    throw new Error(`${label}: ожидалась ошибка`);
  }
}

must(login(state, "irina@gosti.bar", "smena", "MANAGER"), "вход Ирины");
fail(login(state, "irina@gosti.bar", "smena", "EMPLOYEE"), "управляющий как сотрудник");
fail(login(state, "irina@gosti.bar", "nope", "MANAGER"), "чужой пароль");
const anna = must(login(state, "anna", "smena", "EMPLOYEE"), "вход Анны по логину");
if (anna.role !== "WAITER") {
  throw new Error("роль Анны");
}

must(
  setTableStatus(state, { userId: 1, tableId: 2, status: "PROBLEM", comment: "Нет салфеток" }),
  "проблема управляющего",
);
fail(
  setTableStatus(state, { userId: 2, tableId: 2, status: "PROBLEM", comment: "Нет салфеток" }),
  "официант не ставит проблему",
);
fail(setTableStatus(state, { userId: 2, tableId: 2, status: "READY" }), "официант не ставит готов");
fail(setTableStatus(state, { userId: 4, tableId: 2, status: "NEEDS_CLEANING" }), "раннер не убирает стол");
must(setTableStatus(state, { userId: 3, tableId: 5, status: "READY" }), "раннер ставит готов");

const task = must(
  createTask(state, {
    actorId: 1,
    title: "Проверить холодильники",
    description: "Смена",
    type: "GENERAL",
    priority: "normal",
    assigneeId: 2,
    saveFavorite: true,
  }),
  "задача из формы",
);
must(moveTask(state, { actorId: 2, taskId: task.taskId, status: "IN_PROGRESS" }), "взять задачу");
must(moveTask(state, { actorId: 2, taskId: task.taskId, status: "COMPLETED" }), "закрыть задачу");
const favorite = state.favorites.find((item) => item.title === "Проверить холодильники" && !item.deletedAt);
must(createTaskFromFavorite(state, { actorId: 1, favoriteId: favorite.id, assigneeId: 3 }), "задача из избранного");

const closed = must(
  closeShift(state, {
    actorId: 1,
    unfinishedTasks: state.tasks
      .filter((item) => item.venueId === 1 && item.status !== "COMPLETED" && item.status !== "CANCELLED")
      .map((item) => ({ taskId: item.id, action: item.id === 5 ? "CARRY_OVER" : "CANCEL" })),
  }),
  "закрытие смены",
);
if (!closed.report || closed.report.carried < 1) {
  throw new Error("перенос не посчитан");
}

const registered = must(
  registerManager(state, { firstName: "Олег", lastName: "Морозов", email: "oleg@zal.test", password: "smena1234" }),
  "регистрация",
);
must(confirmEmail(state, registered.user.id, registered.code), "почта");
must(confirmPayment(state, registered.user.id), "оплата");
must(saveVenue(state, registered.user.id, { name: "Клод Моне", address: "Садовая 1", about: "Малый зал" }), "профиль");
must(saveFirstHall(state, registered.user.id, { name: "Основной", tableNumber: 1, seats: 4 }), "зал");
const staff = must(
  createStaff(state, { actorId: registered.user.id, firstName: "Лена", lastName: "Ильина", position: "WAITER" }),
  "сотрудник",
);
must(finishStaffStep(state, registered.user.id), "шаг сотрудников");
must(finishSetup(state, registered.user.id), "готово");
if (state.venues.find((venue) => venue.ownerId === registered.user.id).setupStep !== "done") {
  throw new Error("настройка не закрыта");
}
const joined = must(
  redeemInvite(state, { token: staff.invitation.token, login: "lena", password: "smena1234" }),
  "приглашение",
);
if (joined.inviteUses !== 1 || joined.position !== "WAITER") {
  throw new Error("счётчик приглашения");
}
fail(redeemInvite(state, { token: staff.invitation.token, login: "lena2", password: "smena1234" }), "повтор приглашения");
const recovery = must(forgotPassword(state, { login: "oleg@zal.test" }), "забыли пароль");
must(resetPassword(state, { token: recovery.token, newPassword: "nova12345" }), "сброс");
must(login(state, "oleg@zal.test", "nova12345", "MANAGER"), "вход после сброса");
must(assignTable(state, { actorId: 1, tableId: 6, assigneeId: 2 }), "закрепление");

console.log("flows ok");
