import { permissionsFor } from "../domain/catalog";
import { extractInviteToken, openingHoursFromRange } from "../validation";
import { paths } from "./paths";
import { getTokens, request, refreshSession, setTokens } from "./http";
import { inviteSecret, loadInviteSecrets, rememberInvitation } from "./invites";

// Пока на сервере нет чтения залов и столов для сотрудника (BE-2), эти GET доступны только директору и менеджеру.
const STAFF_READS_HALLS = process.env.EXPO_PUBLIC_STAFF_READS_HALLS === "1";

function uiRole(profile) {
  if (profile.role === "DIRECTOR" || profile.role === "MANAGER") {
    return "MANAGER";
  }
  if (profile.position === "RUNNER") {
    return "RUNNER";
  }
  if (profile.position === "WAITER") {
    return "WAITER";
  }
  return "EMPLOYEE";
}

function isOwner(profile) {
  return profile.role === "DIRECTOR" || profile.role === "MANAGER";
}

function fullName(person) {
  return [person.firstName, person.lastName].filter(Boolean).join(" ").trim() || person.login || "Сотрудник";
}

function clock(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${hours}:${minutes}`;
}

function day(value) {
  return value ? String(value).slice(0, 10) : "";
}

function asNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : value;
}

function resolveSetup(profile, halls, tables) {
  if (!isOwner(profile)) {
    return "done";
  }
  if (!profile.restaurantId) {
    return "venue";
  }
  if (!halls.length && !tables.length) {
    return "hall";
  }
  return "done";
}

function asList(value) {
  if (Array.isArray(value)) {
    return value;
  }
  if (Array.isArray(value?.content)) {
    return value.content;
  }
  if (Array.isArray(value?.value)) {
    return value.value;
  }
  return [];
}

function idOf(value) {
  return value == null || value === "" ? null : String(value);
}

function sameId(left, right) {
  return idOf(left) != null && idOf(left) === idOf(right);
}

function participantsOf(shift) {
  return asList(shift?.participants);
}

function loaderWithProblems() {
  const problems = [];
  async function load(label, loader, missing = null) {
    try {
      return await loader();
    } catch (error) {
      if (error?.status === 404) {
        return missing;
      }
      if (error?.status === 401) {
        throw error;
      }
      const text = error?.status ? `${label}: ${error.message}` : String(error?.message || "Ошибка сети.");
      if (!problems.includes(text)) {
        problems.push(text);
      }
      return missing;
    }
  }
  return { problems, load };
}

function baseFields(venueName) {
  return {
    username: "",
    email: "",
    photo: null,
    createdAt: null,
    invitationToken: null,
    invitationId: null,
    inviteExpiresAt: null,
    inviteUses: 0,
    venueName,
  };
}

function mapUser(profile, venueName, membershipId) {
  const role = uiRole(profile);
  return {
    ...baseFields(venueName),
    id: idOf(profile.id),
    username: profile.login,
    email: profile.email || "",
    firstName: profile.firstName || "",
    lastName: profile.lastName || "",
    fullName: fullName(profile),
    role,
    position: profile.position || role,
    venueId: idOf(profile.restaurantId),
    membershipId: idOf(membershipId),
    permissions: permissionsFor(role),
    status: profile.status || "ACTIVE",
    emailConfirmed: true,
    createdAt: profile.createdAt,
  };
}

const assignmentDraft = {
  waiter: new Map(),
  runner: new Map(),
};

function staffLane(person) {
  return person?.position === "RUNNER" || person?.role === "RUNNER" ? "runner" : "waiter";
}

export function assignTableDraft({ tableId, person, release = false }) {
  if (!person) {
    throw new Error("Сотрудник не найден.");
  }
  if (person.status === "INVITED") {
    throw new Error("Сначала сотрудник должен принять приглашение и войти в приложение.");
  }
  if (person.status !== "ACTIVE") {
    throw new Error("Закрепить можно только активного сотрудника.");
  }
  if (person.role === "MANAGER") {
    throw new Error("Стол закрепляют за официантом или раннером.");
  }
  assignmentDraft[staffLane(person)].set(String(tableId), release ? null : person.membershipId);
}

export function clearAssignmentDraft() {
  assignmentDraft.waiter.clear();
  assignmentDraft.runner.clear();
}

function mapInvite(row, venueId, venueName) {
  const secret = inviteSecret(row.id, row.employeeId);
  const token = extractInviteToken(row.invitationUrl || row.qrContent) || secret?.token || "";
  const role = uiRole({ role: row.role, position: row.position });
  return {
    ...baseFields(venueName),
    id: idOf(row.employeeId || row.id),
    firstName: row.firstName || "",
    lastName: row.lastName || "",
    fullName: fullName(row),
    role,
    position: row.position || role,
    venueId: idOf(venueId),
    membershipId: idOf(row.employeeId || row.id),
    permissions: permissionsFor(role),
    status: row.status === "PENDING" ? "INVITED" : row.status,
    emailConfirmed: false,
    invitationToken: token || null,
    invitationId: idOf(row.id),
    inviteExpiresAt: row.expiresAt || secret?.expiresAt || null,
    inviteUses: row.status === "ACCEPTED" ? 1 : 0,
  };
}

function mapEmployee(row, venueId, venueName) {
  const role = uiRole({ role: row.position === "MANAGER" || row.position === "DIRECTOR" ? row.position : "EMPLOYEE", position: row.position });
  return {
    ...baseFields(venueName),
    id: idOf(row.userId || row.membershipId),
    firstName: row.firstName || "",
    lastName: row.lastName || "",
    fullName: fullName(row),
    role,
    position: row.position || role,
    venueId: idOf(venueId),
    membershipId: idOf(row.membershipId),
    permissions: permissionsFor(role),
    status: row.status || "ACTIVE",
    emailConfirmed: true,
  };
}

function mapShift(row, restaurantId, memberIds) {
  const participants = participantsOf(row);
  return {
    id: idOf(row.id),
    venueId: idOf(restaurantId),
    date: day(row.actualStartedAt || row.scheduledStartAt),
    start: clock(row.actualStartedAt || row.scheduledStartAt),
    end: clock(row.scheduledEndAt),
    status: row.status,
    memberIds,
    employeeCount: participants.length || Number(row.employeeCount) || memberIds.length,
  };
}

function latest(rows) {
  return rows
    .slice()
    .sort((left, right) => new Date(right.scheduledStartAt || 0) - new Date(left.scheduledStartAt || 0))[0] ?? null;
}

export async function loginRemote(loginName, password) {
  const tokens = await request(paths.login, {
    method: "POST",
    body: { login: loginName.trim(), password },
    auth: false,
  });
  if (!tokens?.accessToken) {
    throw new Error("Сервер не выдал токен.");
  }
  setTokens(tokens);
  return hydrateRemote(tokens);
}

export async function hydrateRemote(tokens) {
  if (tokens?.accessToken) {
    setTokens(tokens);
  }
  await loadInviteSecrets();
  const { problems, load } = loaderWithProblems();
  const profile = await request(paths.me);
  const restaurantId = profile.restaurantId;
  const owner = isOwner(profile);
  const inVenue = Boolean(restaurantId);
  const readsHalls = inVenue && (owner || STAFF_READS_HALLS);

  const [restaurant, hallRows, tableRows, employeeRows, invitationRows, shiftRows, current, notificationRows] =
    await Promise.all([
      inVenue && owner ? load("Ресторан", () => request(paths.restaurant(restaurantId))) : null,
      readsHalls ? load("Залы", () => request(paths.halls(restaurantId))) : [],
      readsHalls ? load("Столы", () => request(paths.tables(restaurantId))) : [],
      inVenue ? load("Сотрудники", () => request(paths.employees(restaurantId)), []) : [],
      inVenue && owner ? load("Приглашения", () => request(paths.invitations(restaurantId)), []) : [],
      inVenue && owner ? load("Смены", () => request(paths.shifts(restaurantId)), []) : [],
      inVenue && !owner ? load("Моя смена", () => request(paths.myCurrentShift)) : null,
      load("Уведомления", () => request(paths.myNotifications), []),
    ]);
  const venueName = restaurant?.name || "Заведение";
  const hallsUnknown = hallRows === null || tableRows === null;
  const halls = asList(hallRows);
  const tables = asList(tableRows);
  const employees = asList(employeeRows);
  const invitations = asList(invitationRows);
  const shiftList = asList(shiftRows);
  const notifications = asList(notificationRows);

  const activeSummary = shiftList.find((item) => item.status === "ACTIVE") ?? null;
  const plannedSummary = latest(shiftList.filter((item) => item.status === "PLANNED"));
  const shiftDetail = (summary) =>
    summary ? load("Состав смены", () => request(`${paths.shifts(restaurantId)}/${summary.id}`)) : null;
  const [activeDetail, plannedDetail] = await Promise.all([shiftDetail(activeSummary), shiftDetail(plannedSummary)]);
  const activeShift = owner
    ? activeSummary && { ...activeSummary, ...(activeDetail || {}) }
    : current?.status === "ACTIVE"
      ? current
      : null;
  const plannedShift = plannedSummary && { ...plannedSummary, ...(plannedDetail || {}) };
  const rosterShift = activeShift || plannedShift;
  const participants = participantsOf(rosterShift);

  const people = employees.map((row) => mapEmployee(row, restaurantId, venueName));
  for (const invite of invitations) {
    if (invite.status && invite.status !== "PENDING") {
      continue;
    }
    const mapped = mapInvite(invite, restaurantId, venueName);
    const existing = people.find(
      (person) => sameId(person.membershipId, mapped.membershipId) || sameId(person.id, mapped.id),
    );
    if (existing) {
      existing.invitationToken = mapped.invitationToken;
      existing.invitationId = mapped.invitationId;
      existing.inviteExpiresAt = mapped.inviteExpiresAt;
      existing.status = existing.status === "ACTIVE" ? existing.status : mapped.status;
      continue;
    }
    people.push(mapped);
  }
  const myMembership = owner && rosterShift?.responsibleMembershipId ? idOf(rosterShift.responsibleMembershipId) : null;
  const meRow =
    people.find((person) => sameId(person.id, profile.id)) ?? mapUser(profile, venueName, myMembership);
  if (!people.some((person) => sameId(person.id, meRow.id))) {
    people.unshift(meRow);
  }
  if (owner) {
    meRow.role = "MANAGER";
    meRow.permissions = permissionsFor("MANAGER");
  }
  for (const participant of participants) {
    if (people.some((person) => sameId(person.membershipId, participant.membershipId))) {
      continue;
    }
    const role = participant.position === "RUNNER" ? "RUNNER" : "WAITER";
    people.push({
      ...baseFields(venueName),
      id: idOf(participant.membershipId),
      firstName: "",
      lastName: "",
      fullName: "Сотрудник вне списка",
      role,
      position: participant.position || role,
      venueId: idOf(restaurantId),
      membershipId: idOf(participant.membershipId),
      permissions: permissionsFor(role),
      status: "ACTIVE",
      emailConfirmed: true,
    });
  }
  const userIdByMembership = (membershipId) => {
    const person = people.find((row) => sameId(row.membershipId, membershipId) || sameId(row.id, membershipId));
    return idOf(person?.id) || idOf(membershipId);
  };
  const waiterByTable = new Map();
  const runnerByTable = new Map();
  for (const participant of participants) {
    const person = people.find((row) => sameId(row.membershipId, participant.membershipId));
    const lane = staffLane(person || { position: participant.position });
    for (const tableId of participant.tableIds ?? []) {
      (lane === "runner" ? runnerByTable : waiterByTable).set(String(tableId), userIdByMembership(participant.membershipId));
    }
  }
  const draftOf = (lane, tableId) => {
    const map = assignmentDraft[lane];
    if (!map.has(String(tableId))) {
      return undefined;
    }
    const membershipId = map.get(String(tableId));
    return membershipId ? userIdByMembership(membershipId) : null;
  };

  let mappedHalls = halls.map((hall) => ({
    id: hall.id,
    venueId: idOf(restaurantId),
    name: hall.name,
    deletedAt: hall.status === "ARCHIVED" ? hall.id : null,
  }));
  let mappedTables = tables.map((item) => ({
    id: item.id,
    hallId: item.hallId,
    number: asNumber(item.number),
    seats: item.seatsCount,
    status: item.status,
    assigneeId: waiterByTable.get(String(item.id)) ?? null,
    runnerId: runnerByTable.get(String(item.id)) ?? null,
    draftWaiterId: draftOf("waiter", item.id),
    draftRunnerId: draftOf("runner", item.id),
    problem: item.status === "PROBLEM" ? "Проблема" : null,
    deletedAt: null,
  }));
  const myTableIds = current?.assignedTableIds ?? [];
  if (!mappedHalls.length && (myTableIds.length || mappedTables.length)) {
    mappedHalls = [{ id: "hall-local", venueId: idOf(restaurantId), name: "Зал", deletedAt: null }];
  }
  if (!mappedTables.length && myTableIds.length) {
    mappedTables = myTableIds.map((id, index) => ({
      id,
      hallId: mappedHalls[0]?.id,
      number: index + 1,
      seats: null,
      status: "READY",
      assigneeId: staffLane(meRow) === "waiter" ? meRow.id : null,
      runnerId: staffLane(meRow) === "runner" ? meRow.id : null,
      problem: null,
      deletedAt: null,
    }));
  } else if (!owner && myTableIds.length) {
    const mine = new Set(myTableIds.map(String));
    for (const item of mappedTables) {
      if (mine.has(String(item.id))) {
        item[staffLane(meRow) === "runner" ? "runnerId" : "assigneeId"] = meRow.id;
      }
    }
  }

  const rawTasks = (owner ? activeShift?.tasks : current?.tasks) ?? [];
  const mappedTasks = rawTasks.map((item) => ({
    id: item.id,
    venueId: idOf(restaurantId),
    title: item.description || "Задача",
    description: item.description || "",
    status: item.status,
    assigneeId: userIdByMembership(item.assignedToMembershipId),
    creatorId: null,
    tableId: null,
    createdAt: item.createdAt || new Date().toISOString(),
    dueAt: item.deadlineAt || null,
    startedAt: null,
    doneAt: item.completedAt || null,
    overdueNotified: false,
    deletedAt: null,
  }));

  const rosterIds = participants.map((row) => userIdByMembership(row.membershipId)).filter(Boolean);
  const activeIds = owner ? (activeShift ? rosterIds : []) : activeShift ? [meRow.id] : [];
  const plannedIds = !activeShift && plannedShift ? rosterIds : [];
  const mappedShift = activeShift ? mapShift(activeShift, restaurantId, activeIds) : null;
  const shiftRowsSource = owner ? shiftList : activeShift ? [activeShift] : [];
  const mappedShifts = shiftRowsSource.map((item) => {
    if (sameId(item.id, activeShift?.id)) {
      return mappedShift;
    }
    if (sameId(item.id, plannedShift?.id)) {
      return mapShift(plannedShift, restaurantId, plannedIds);
    }
    return mapShift(item, restaurantId, []);
  });

  const live = getTokens() ?? tokens;
  return {
    tokens: live,
    user: {
      ...meRow,
      venue: venueName,
      restaurantName: venueName,
      restaurantId,
      setupStep: hallsUnknown ? "done" : resolveSetup(profile, halls, tables),
      subscription: "active",
      accessToken: live?.accessToken,
      refreshToken: live?.refreshToken,
      expiresIn: live?.expiresIn,
    },
    snapshot: {
      version: 3,
      seq: Date.now(),
      users: people,
      venues: restaurantId
        ? [{ id: idOf(restaurantId), name: venueName, setupStep: "done", subscription: "active" }]
        : [],
      halls: mappedHalls,
      tables: mappedTables,
      tasks: mappedTasks,
      favorites: [],
      notifications: notifications.map((item) => ({
        id: item.id,
        userId: idOf(profile.id),
        title: item.title,
        body: item.message,
        kind: item.type,
        read: Boolean(item.readAt),
        createdAt: item.createdAt,
      })),
      history: [],
      shifts: mappedShifts,
      attendance: activeIds.map((userId, index) => ({
        id: index + 1,
        shiftId: mappedShift?.id,
        userId,
        present: true,
      })),
      plans: [],
      currentShiftId: mappedShift?.id ?? null,
      problems,
    },
  };
}

export async function logoutRemote() {
  try {
    await request(paths.logout, { method: "POST" });
  } catch {
    // Выход локальный.
  }
  setTokens(null);
}

export async function registerRemote({ firstName, lastName, email, password, name, address, hours }) {
  await request(paths.register, {
    method: "POST",
    body: {
      login: email.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      password,
    },
    auth: false,
  });
  const tokens = await request(paths.login, {
    method: "POST",
    body: { login: email.trim(), password },
    auth: false,
  });
  if (!tokens?.accessToken) {
    throw new Error("Сервер не выдал токен.");
  }
  setTokens(tokens);
  try {
    await activateSubscriptionRemote();
  } catch {
    // Подписку активируем ещё раз на шаге заведения.
  }
  if (name?.trim() && address?.trim()) {
    try {
      await createRestaurantRemote({ name, address, hours });
    } catch (error) {
      if (!/ALREADY_EXISTS|уже есть/i.test(String(error.message || ""))) {
        return hydrateRemote(tokens);
      }
    }
    return hydrateRemote(await refreshSession());
  }
  return hydrateRemote(tokens);
}

export async function activateSubscriptionRemote() {
  return request(paths.activateSubscription, { method: "POST" });
}

export async function createRestaurantRemote({ name, address, hours }) {
  return request(paths.restaurants, {
    method: "POST",
    body: {
      name: String(name).trim(),
      address: String(address).trim(),
      openingHours: openingHoursFromRange(hours),
    },
  });
}

export async function saveVenueRemote({ name, address, hours }) {
  await activateSubscriptionRemote();
  await createRestaurantRemote({ name, address, hours });
  return hydrateRemote(await refreshSession());
}

export async function saveFirstHallRemote(restaurantId, { name, tables }) {
  if (!restaurantId) {
    throw new Error("Сначала создайте ресторан.");
  }
  const hall = await createHallRemote(restaurantId, { name: String(name || "Основной").trim() || "Основной" });
  const rows = Array.isArray(tables) ? tables : [];
  for (let index = 0; index < rows.length; index += 1) {
    const row = rows[index];
    await createTableRemote(restaurantId, {
      hallId: hall.hallId,
      number: row.number ?? index + 1,
      seats: row.seats,
      positionX: index * 80,
    });
  }
}

export async function changePasswordRemote({ currentPassword, newPassword }) {
  await request(paths.changePassword, {
    method: "POST",
    body: { currentPassword, newPassword },
  });
}

export async function createHallRemote(restaurantId, { name }) {
  const hall = await request(paths.halls(restaurantId), { method: "POST", body: { name } });
  return { hallId: hall.id };
}

export async function createTableRemote(restaurantId, { hallId, number, seats, positionX = 0, positionY = 0 }) {
  await request(`${paths.halls(restaurantId)}/${hallId}/tables`, {
    method: "POST",
    body: {
      number: String(number),
      seatsCount: Number(seats),
      positionX,
      positionY,
      shape: "RECTANGLE",
    },
  });
}

export async function updateTableRemote(restaurantId, { tableId, number, seats }) {
  await request(`${paths.tables(restaurantId)}/${tableId}`, {
    method: "PATCH",
    body: { number: String(number), seatsCount: Number(seats) },
  });
}

export async function setTableStatusRemote(restaurantId, { tableId, status, comment }) {
  await request(paths.tableStatus(restaurantId, tableId), {
    method: "POST",
    body: { status, comment: comment || null },
  });
}

export async function loadTableHistoryRemote(restaurantId, tableId) {
  return asList(await request(paths.tableHistory(restaurantId, tableId)));
}

export async function createTaskRemote(restaurantId, shiftId, { title, description, assigneeMembershipId, dueAt }) {
  if (!shiftId) {
    throw new Error("Нет активной смены — задачу создаёт сервер только в смене.");
  }
  if (!assigneeMembershipId) {
    throw new Error("Выберите исполнителя.");
  }
  await request(`${paths.shifts(restaurantId)}/${shiftId}/tasks`, {
    method: "POST",
    body: {
      description: description?.trim() || title,
      assignedToMembershipId: assigneeMembershipId,
      deadlineType: dueAt ? "TIME" : "NONE",
      deadlineAt: dueAt || null,
    },
  });
}

export async function moveTaskRemote(restaurantId, { taskId, status }) {
  await request(paths.taskStatus(restaurantId, taskId), {
    method: "POST",
    body: { status },
  });
}

function scheduleFor(start, end) {
  const today = new Date();
  const [startH, startM] = String(start || "10:00").split(":").map(Number);
  const [endH, endM] = String(end || "22:00").split(":").map(Number);
  const begin = new Date(today);
  begin.setHours(startH || 0, startM || 0, 0, 0);
  const finish = new Date(today);
  finish.setHours(endH || 0, endM || 0, 0, 0);
  if (finish <= begin) {
    finish.setDate(finish.getDate() + 1);
  }
  return { scheduledStartAt: begin.toISOString(), scheduledEndAt: finish.toISOString() };
}

// Черновик (PLANNED) переиспользуется, чтобы повторное сохранение не создавало вторую смену.
export async function saveShiftPlanRemote(restaurantId, { draftShiftId, start, end, participants, startNow }) {
  if (!participants.length || participants.some((row) => !row.tableIds.length)) {
    throw new Error("У каждого сотрудника смены должен быть хотя бы один стол.");
  }
  let shiftId = draftShiftId;
  if (!shiftId) {
    const created = await request(paths.shifts(restaurantId), { method: "POST", body: scheduleFor(start, end) });
    shiftId = created.id;
  }
  await request(paths.shiftParticipants(restaurantId, shiftId), {
    method: "PUT",
    body: { participants },
  });
  clearAssignmentDraft();
  if (startNow) {
    await request(paths.startShift(restaurantId, shiftId), { method: "POST" });
  }
  return shiftId;
}

export async function closeShiftRemote(restaurantId, shiftId, unfinishedTasks) {
  return request(paths.closeShift(restaurantId, shiftId), {
    method: "POST",
    body: {
      unfinishedTasks: (unfinishedTasks ?? []).map((item) => ({
        taskId: item.taskId,
        action: item.action,
        comment: item.comment || null,
      })),
    },
  });
}

export async function markNotificationReadRemote(notificationId) {
  await request(paths.readNotification(notificationId), { method: "PATCH" });
}

export async function markAllNotificationsReadRemote() {
  await request(paths.readAllNotifications, { method: "POST" });
}

export async function createStaffRemote(restaurantId, { firstName, lastName, position }) {
  const role = position === "MANAGER" ? "MANAGER" : "EMPLOYEE";
  const invitation = await request(paths.invitations(restaurantId), {
    method: "POST",
    body: {
      firstName: String(firstName).trim(),
      lastName: String(lastName).trim(),
      role,
      ...(role === "EMPLOYEE"
        ? { position: position === "RUNNER" ? "RUNNER" : "WAITER" }
        : {}),
    },
  });
  await rememberInvitation(invitation);
  return invitation;
}

export async function resendInvitationRemote(restaurantId, invitationId) {
  if (!invitationId) {
    throw new Error("Приглашение не найдено.");
  }
  const invitation = await request(paths.resendInvitation(restaurantId, invitationId), { method: "POST" });
  await rememberInvitation(invitation);
  return invitation;
}

export async function redeemInviteRemote({ token, login, password }) {
  const tokens = await request(paths.acceptInvitation(extractInviteToken(token)), {
    method: "POST",
    body: { login, password },
    auth: false,
  });
  setTokens(tokens);
  return hydrateRemote(tokens);
}

export function unsupported(label) {
  return Promise.reject(new Error(`${label} на сервере ещё нет.`));
}
