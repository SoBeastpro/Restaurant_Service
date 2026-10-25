import AsyncStorage from "@react-native-async-storage/async-storage";
import { paths } from "./paths";
import { loadTokens, saveTokens } from "./http";
import {
  activateSubscriptionRemote,
  assignTableDraft,
  changePasswordRemote,
  closeShiftRemote,
  createHallRemote,
  createStaffRemote,
  createTableRemote,
  createTaskRemote,
  hydrateRemote,
  loginRemote,
  logoutRemote,
  markAllNotificationsReadRemote,
  markNotificationReadRemote,
  moveTaskRemote,
  openShiftRemote,
  redeemInviteRemote,
  registerRemote,
  saveFirstHallRemote,
  saveVenueRemote,
  setTableStatusRemote,
  unsupported,
  updateTableRemote,
} from "./remote";
import { STORE_VERSION, buildView, statsFor } from "../domain/engine";

const STORAGE_KEY = "rest.store.v1";

function emptyState() {
  return {
    version: STORE_VERSION,
    seq: 1,
    users: [],
    venues: [],
    halls: [],
    tables: [],
    tasks: [],
    favorites: [],
    notifications: [],
    history: [],
    shifts: [],
    attendance: [],
    plans: [],
    currentShiftId: null,
  };
}

let state = emptyState();
let liveUser = null;
const listeners = new Set();
let hydratePromise = null;

function emit() {
  for (const listener of listeners) {
    listener();
  }
}

export function getState() {
  return state;
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function applyLive(result) {
  state = result.snapshot;
  liveUser = result.user;
  emit();
  return result.user;
}

async function persistSession(user) {
  if (!user?.accessToken) {
    return user;
  }
  await saveTokens(AsyncStorage, {
    accessToken: user.accessToken,
    refreshToken: user.refreshToken,
    expiresIn: user.expiresIn,
  });
  return user;
}

async function refreshLive() {
  if (!liveUser?.accessToken) {
    return liveUser;
  }
  const result = await hydrateRemote({
    accessToken: liveUser.accessToken,
    refreshToken: liveUser.refreshToken,
    expiresIn: liveUser.expiresIn,
  });
  return persistSession(applyLive(result));
}

function membershipOf(userId) {
  return state.users.find((user) => user.id === userId)?.membershipId ?? userId;
}

function requireVenue() {
  if (!liveUser?.venueId) {
    throw new Error("Сначала создайте ресторан.");
  }
  return liveUser.venueId;
}

export function hydrateStore() {
  if (!hydratePromise) {
    hydratePromise = (async () => {
      state = emptyState();
      await AsyncStorage.removeItem(STORAGE_KEY);
      emit();
    })();
  }
  return hydratePromise;
}

export const api = {
  view(sessionUser) {
    return buildView(state, sessionUser || liveUser);
  },
  publishUser(userId) {
    if (liveUser && String(liveUser.id) === String(userId)) {
      return liveUser;
    }
    return null;
  },
  stats(venueId, period) {
    return statsFor(state, venueId, period);
  },
  async refresh() {
    try {
      return await refreshLive();
    } catch {
      return liveUser;
    }
  },
  async login(loginName, password) {
    return persistSession(applyLive(await loginRemote(loginName, password)));
  },
  async registerManager(input) {
    return persistSession(applyLive(await registerRemote(input)));
  },
  confirmEmail() {
    return unsupported("Подтверждение почты");
  },
  async confirmPayment() {
    await activateSubscriptionRemote();
    return refreshLive();
  },
  async saveVenue(_userId, input) {
    return persistSession(applyLive(await saveVenueRemote(input)));
  },
  async saveFirstHall(_userId, input) {
    await saveFirstHallRemote(requireVenue(), input);
    return refreshLive();
  },
  finishStaffStep() {
    return refreshLive();
  },
  finishSetup() {
    return refreshLive();
  },
  async createStaff(input) {
    await createStaffRemote(requireVenue(), input);
    return refreshLive();
  },
  async redeemInvite(input) {
    return persistSession(applyLive(await redeemInviteRemote(input)));
  },
  forgotPassword() {
    return unsupported("Восстановление пароля");
  },
  resetPassword() {
    return unsupported("Сброс пароля");
  },
  async changePassword(input) {
    await changePasswordRemote(input);
    return refreshLive();
  },
  async createHall(input) {
    const created = await createHallRemote(requireVenue(), input);
    await refreshLive();
    return created;
  },
  async createTable(input) {
    await createTableRemote(requireVenue(), input);
    return refreshLive();
  },
  async updateTable(input) {
    await updateTableRemote(requireVenue(), input);
    return refreshLive();
  },
  async assignTable({ tableId, assigneeId }) {
    const person = state.users.find((user) => String(user.id) === String(assigneeId));
    assignTableDraft({ tableId, person });
    return refreshLive();
  },
  async setTableStatus(input) {
    await setTableStatusRemote(requireVenue(), input);
    return refreshLive();
  },
  async openShift(input) {
    await openShiftRemote(requireVenue(), {
      start: input.start,
      end: input.end,
      membershipIds: (input.memberIds ?? []).map(membershipOf),
    });
    return refreshLive();
  },
  async setShiftMembers(input) {
    await openShiftRemote(requireVenue(), {
      start: "10:00",
      end: "22:00",
      membershipIds: (input.memberIds ?? []).map(membershipOf),
    });
    return refreshLive();
  },
  leaveShift() {
    return unsupported("Самостоятельный выход из смены");
  },
  async closeShift(input) {
    await closeShiftRemote(requireVenue(), state.currentShiftId, input.unfinishedTasks);
    return refreshLive();
  },
  async createTask(input) {
    await createTaskRemote(requireVenue(), state.currentShiftId, {
      title: input.title,
      description: input.description,
      assigneeMembershipId: membershipOf(input.assigneeId),
      dueAt: input.dueAt ?? null,
    });
    return refreshLive();
  },
  createTaskFromFavorite() {
    return unsupported("Шаблоны задач");
  },
  deleteFavorite() {
    return unsupported("Шаблоны задач");
  },
  async moveTask(input) {
    await moveTaskRemote(requireVenue(), input);
    return refreshLive();
  },
  saveDayPlan() {
    return unsupported("План дня");
  },
  togglePlanItem() {
    return unsupported("План дня");
  },
  updateStaff() {
    return unsupported("Изменение сотрудника");
  },
  blockStaff() {
    return unsupported("Блокировка сотрудника");
  },
  deleteStaff() {
    return unsupported("Удаление сотрудника");
  },
  async markNotificationRead(input) {
    await markNotificationReadRemote(input.notificationId);
    return refreshLive();
  },
  async markAllNotificationsRead() {
    await markAllNotificationsReadRemote();
    return refreshLive();
  },
};

export function userName(userId) {
  return state.users.find((user) => user.id === userId)?.fullName ?? "Сотрудник";
}

export async function restoreLiveSession(storedUser) {
  if (!storedUser?.accessToken) {
    return storedUser;
  }
  try {
    await saveTokens(AsyncStorage, {
      accessToken: storedUser.accessToken,
      refreshToken: storedUser.refreshToken,
      expiresIn: storedUser.expiresIn,
    });
    const result = await hydrateRemote({
      accessToken: storedUser.accessToken,
      refreshToken: storedUser.refreshToken,
      expiresIn: storedUser.expiresIn,
    });
    return persistSession(applyLive(result));
  } catch {
    return null;
  }
}

export async function logoutOnServer(token) {
  if (token) {
    liveUser = { ...(liveUser || {}), accessToken: token };
  }
  await logoutRemote();
  liveUser = null;
  state = emptyState();
  await saveTokens(AsyncStorage, null);
}

export async function loginOnServer(loginName, password) {
  try {
    return await api.login(loginName, password);
  } catch (error) {
    const text = String(error.message || "");
    if (/401|неверн|парол|логин|unauthorized/i.test(text)) {
      return "bad";
    }
    throw error;
  }
}

export { loadTokens, paths };
