import AsyncStorage from "@react-native-async-storage/async-storage";
import { paths } from "./paths";
import { getTokens, loadTokens, saveTokens } from "./http";
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
  loadTableHistoryRemote,
  loginRemote,
  logoutRemote,
  markAllNotificationsReadRemote,
  markNotificationReadRemote,
  moveTaskRemote,
  redeemInviteRemote,
  registerRemote,
  resendInvitationRemote,
  saveFirstHallRemote,
  saveHallLayoutRemote,
  saveShiftPlanRemote,
  saveVenueRemote,
  setTableStatusRemote,
  unsupported,
  updateTableRemote,
} from "./remote";
import { STORE_VERSION, buildView, statsFor } from "../domain/engine";
import { plannedRunnerId, plannedWaiterId, shiftLane, shiftPlanGaps } from "../domain/shift";
import { samePerson } from "../domain/view";

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
    problems: [],
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

function reportProblem(error) {
  const text = String(error?.message || "Не удалось обновить данные.");
  state = { ...state, seq: state.seq + 1, problems: [text] };
  emit();
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
  const result = await hydrateRemote(getTokens());
  return persistSession(applyLive(result));
}

async function dropSession() {
  liveUser = null;
  state = emptyState();
  await saveTokens(AsyncStorage, null);
  emit();
}

function membershipOf(userId) {
  return state.users.find((user) => samePerson(user, userId))?.membershipId ?? userId;
}

function requireVenue() {
  if (!liveUser?.venueId) {
    throw new Error("Сначала создайте ресторан.");
  }
  return liveUser.venueId;
}

function planError(gaps) {
  const error = new Error(`Состав не сохранён:\n${gaps.join("\n")}`);
  error.gaps = gaps;
  return error;
}

function shiftParticipants(memberIds) {
  const view = buildView(state, liveUser);
  const members = (memberIds ?? [])
    .map((id) => state.users.find((user) => samePerson(user, id)))
    .filter(Boolean);
  const tables = view?.allTables ?? [];
  const gaps = shiftPlanGaps({ members, tables });
  if (gaps.length) {
    throw planError(gaps);
  }
  return members.map((person) => {
    const lane = shiftLane(person);
    const tableIds = tables
      .filter((table) => String(lane === "runner" ? plannedRunnerId(table) : plannedWaiterId(table)) === String(person.id))
      .map((table) => table.id);
    return { membershipId: person.membershipId, tableIds };
  });
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
    } catch (error) {
      if (error?.status === 401) {
        await dropSession();
        return null;
      }
      reportProblem(error);
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
  async resendInvite({ invitationId }) {
    await resendInvitationRemote(requireVenue(), invitationId);
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
  async saveHallLayout({ hallId, draft, onCreatedTable }) {
    await saveHallLayoutRemote(requireVenue(), hallId, draft, onCreatedTable);
    return refreshLive();
  },
  async assignTable({ tableId, assigneeId }) {
    const person = state.users.find((user) => samePerson(user, assigneeId));
    const table = state.tables.find((item) => String(item.id) === String(tableId));
    const current = table ? (shiftLane(person) === "runner" ? plannedRunnerId(table) : plannedWaiterId(table)) : null;
    assignTableDraft({ tableId, person, release: current != null && samePerson(person, current) });
    return refreshLive();
  },
  async setTableStatus(input) {
    await setTableStatusRemote(requireVenue(), input);
    return refreshLive();
  },
  async loadTableHistory(tableId) {
    return loadTableHistoryRemote(requireVenue(), tableId);
  },
  async saveShiftPlan(input) {
    const venueId = requireVenue();
    await saveShiftPlanRemote(venueId, {
      draftShiftId: input.shiftId ?? null,
      start: input.start,
      end: input.end,
      participants: shiftParticipants(input.memberIds),
      startNow: Boolean(input.startNow),
    });
    return refreshLive();
  },
  openShift(input) {
    return api.saveShiftPlan({ ...input, startNow: true });
  },
  setShiftMembers(input) {
    return api.saveShiftPlan({ ...input, startNow: false });
  },
  leaveShift() {
    return unsupported("Самостоятельный выход из смены");
  },
  async closeShift(input) {
    const result = await closeShiftRemote(requireVenue(), state.currentShiftId, input.unfinishedTasks);
    await api.refresh();
    return result;
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
  return state.users.find((user) => samePerson(user, userId))?.fullName ?? "Сотрудник";
}

export async function restoreLiveSession(storedUser) {
  if (!storedUser?.accessToken) {
    return storedUser;
  }
  try {
    const stored = await loadTokens(AsyncStorage);
    const tokens = stored?.accessToken
      ? stored
      : { accessToken: storedUser.accessToken, refreshToken: storedUser.refreshToken, expiresIn: storedUser.expiresIn };
    await saveTokens(AsyncStorage, tokens);
    return persistSession(applyLive(await hydrateRemote(tokens)));
  } catch (error) {
    if (error?.status === 401) {
      await saveTokens(AsyncStorage, null);
      return null;
    }
    liveUser = storedUser;
    reportProblem(error);
    return storedUser;
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
