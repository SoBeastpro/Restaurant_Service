import { permissionsFor } from "../domain/catalog";
import { openingHoursFromRange } from "../validation";
import { paths } from "./paths";
import { request, refreshSession, setTokens } from "./http";

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
  const owner = profile.role === "DIRECTOR" || profile.role === "MANAGER";
  if (!owner) {
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

async function optional(loader) {
  try {
    return await loader();
  } catch {
    return null;
  }
}

function mapUser(profile, venueName, membershipId) {
  const role = uiRole(profile);
  return {
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
    permissions: permissionsFor(role === "MANAGER" ? "MANAGER" : role),
    status: profile.status || "ACTIVE",
    photo: null,
    emailConfirmed: true,
    createdAt: profile.createdAt,
    invitationToken: null,
    inviteUses: 0,
    venueName,
  };
}

const invitationSecrets = new Map();
const assignmentDraft = {
  waiter: new Map(),
  runner: new Map(),
};

function staffLane(person) {
  return person?.position === "RUNNER" || person?.role === "RUNNER" ? "runner" : "waiter";
}

export function assignTableDraft({ tableId, person }) {
  if (!person) {
    throw new Error("Сотрудник не найден.");
  }
  if (person.status === "INVITED" || person.invitationToken) {
    throw new Error("Сначала сотрудник должен принять приглашение и войти в приложение.");
  }
  if (person.status !== "ACTIVE") {
    throw new Error("Закрепить можно только активного сотрудника.");
  }
  if (person.role === "MANAGER") {
    throw new Error("Стол закрепляют за официантом или раннером.");
  }
  assignmentDraft[staffLane(person)].set(String(tableId), person.membershipId);
}

export function tableIdsByMemberFromDraft() {
  const byMember = {};
  for (const lane of ["waiter", "runner"]) {
    for (const [tableId, membershipId] of assignmentDraft[lane]) {
      if (!byMember[membershipId]) {
        byMember[membershipId] = [];
      }
      byMember[membershipId].push(tableId);
    }
  }
  return byMember;
}

export function clearAssignmentDraft() {
  assignmentDraft.waiter.clear();
  assignmentDraft.runner.clear();
}

function extractInviteToken(value) {
  const raw = String(value || "").trim();
  if (!raw) {
    return "";
  }
  const prefix = "rest://invite/";
  return raw.startsWith(prefix) ? raw.slice(prefix.length) : raw;
}

function rememberInvitation(invitation) {
  const token = extractInviteToken(invitation?.invitationUrl || invitation?.qrContent);
  if (!invitation || !token) {
    return;
  }
  invitationSecrets.set(invitation.id, token);
  if (invitation.employeeId) {
    invitationSecrets.set(invitation.employeeId, token);
  }
}

function mapInvite(row, venueId, venueName) {
  const token =
    extractInviteToken(row.invitationUrl || row.qrContent) ||
    invitationSecrets.get(row.id) ||
    invitationSecrets.get(row.employeeId) ||
    "";
  const role = uiRole({ role: row.role, position: row.position });
  return {
    id: row.employeeId || row.id,
    username: "",
    email: "",
    firstName: row.firstName || "",
    lastName: row.lastName || "",
    fullName: fullName(row),
    role,
    position: row.position || role,
    venueId,
    membershipId: row.employeeId || row.id,
    permissions: permissionsFor(role === "MANAGER" ? "MANAGER" : role),
    status: row.status === "PENDING" ? "INVITED" : row.status,
    photo: null,
    emailConfirmed: false,
    createdAt: null,
    invitationToken: token || null,
    inviteUses: row.status === "ACCEPTED" ? 1 : 0,
    venueName,
  };
}

function mapEmployee(row, venueId, venueName) {
  const role = uiRole({ role: row.position === "MANAGER" || row.position === "DIRECTOR" ? row.position : "EMPLOYEE", position: row.position });
  const id = idOf(row.userId || row.membershipId);
  return {
    id,
    username: "",
    email: "",
    firstName: row.firstName || "",
    lastName: row.lastName || "",
    fullName: fullName(row),
    role,
    position: row.position || role,
    venueId: idOf(venueId),
    membershipId: idOf(row.membershipId),
    permissions: permissionsFor(role === "MANAGER" ? "MANAGER" : role),
    status: row.status || "ACTIVE",
    photo: null,
    emailConfirmed: true,
    createdAt: null,
    invitationToken: null,
    inviteUses: 0,
    venueName,
  };
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
  const profile = await request(paths.me);
  const restaurantId = profile.restaurantId;
  const restaurant = restaurantId ? await optional(() => request(paths.restaurant(restaurantId))) : null;
  const venueName = restaurant?.name || "Заведение";
  const halls = asList(restaurantId ? await optional(() => request(paths.halls(restaurantId))) : []);
  const tables = asList(restaurantId ? await optional(() => request(paths.tables(restaurantId))) : []);
  const employees = asList(restaurantId ? await optional(() => request(paths.employees(restaurantId))) : []);
  const invitations = asList(restaurantId ? await optional(() => request(paths.invitations(restaurantId))) : []);
  const current = await optional(() => request(paths.myCurrentShift));
  const shiftList = asList(restaurantId ? await optional(() => request(paths.shifts(restaurantId))) : []);
  const notifications = asList(await optional(() => request(paths.myNotifications)));
  const activeFromList = shiftList.find((item) => item.status === "ACTIVE");
  const shiftId = idOf(current?.id || activeFromList?.id);
  let shiftDetail = current;
  if (shiftId && restaurantId) {
    shiftDetail = (await optional(() => request(`${paths.shifts(restaurantId)}/${shiftId}`))) || current;
  }
  const participants = participantsOf(shiftDetail);

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
      existing.status = existing.status === "ACTIVE" ? existing.status : mapped.status;
      continue;
    }
    people.push(mapped);
  }
  const myMembership =
    (profile.role === "MANAGER" || profile.role === "DIRECTOR") && shiftDetail?.responsibleMembershipId
      ? idOf(shiftDetail.responsibleMembershipId)
      : null;
  const meRow =
    people.find((person) => sameId(person.id, profile.id)) ?? mapUser(profile, venueName, myMembership);
  if (!people.some((person) => sameId(person.id, meRow.id))) {
    people.unshift(meRow);
  }
  for (const participant of participants) {
    if (people.some((person) => sameId(person.membershipId, participant.membershipId))) {
      continue;
    }
    people.push({
      id: idOf(participant.membershipId),
      username: "",
      email: "",
      firstName: "",
      lastName: "",
      fullName: "На смене",
      role: "EMPLOYEE",
      position: participant.position || "EMPLOYEE",
      venueId: restaurantId,
      membershipId: idOf(participant.membershipId),
      permissions: permissionsFor("WAITER"),
      status: "ACTIVE",
      photo: null,
      emailConfirmed: true,
      createdAt: null,
      invitationToken: null,
      inviteUses: 0,
      venueName,
    });
  }
  const byMembership = new Map(people.map((person) => [idOf(person.membershipId), idOf(person.id)]));
  const waiterByTable = new Map();
  const runnerByTable = new Map();
  for (const participant of participants) {
    const person = people.find((row) => sameId(row.membershipId, participant.membershipId));
    const runner = staffLane(person || { position: participant.position }) === "runner";
    const userId = byMembership.get(idOf(participant.membershipId));
    for (const tableId of participant.tableIds ?? []) {
      if (!userId) {
        continue;
      }
      if (runner) {
        runnerByTable.set(String(tableId), userId);
      } else {
        waiterByTable.set(String(tableId), userId);
      }
    }
  }
  for (const [tableId, membershipId] of assignmentDraft.waiter) {
    waiterByTable.set(String(tableId), byMembership.get(membershipId) ?? membershipId);
  }
  for (const [tableId, membershipId] of assignmentDraft.runner) {
    runnerByTable.set(String(tableId), byMembership.get(membershipId) ?? membershipId);
  }

  let mappedHalls = halls.map((hall) => ({
    id: hall.id,
    venueId: restaurantId,
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
    problem: item.status === "PROBLEM" ? "Проблема" : null,
    deletedAt: null,
  }));
  if (!mappedHalls.length && (current?.assignedTableIds?.length || mappedTables.length)) {
    mappedHalls = [{ id: "hall-local", venueId: restaurantId, name: "Зал", deletedAt: null }];
  }
  if (!mappedTables.length && current?.assignedTableIds?.length) {
    mappedTables = current.assignedTableIds.map((id, index) => ({
      id,
      hallId: mappedHalls[0]?.id,
      number: index + 1,
      seats: 4,
      status: "READY",
      assigneeId: meRow.id,
      runnerId: null,
      problem: null,
      deletedAt: null,
    }));
  }

  const rawTasks = shiftDetail?.tasks ?? current?.tasks ?? [];
  const mappedTasks = rawTasks.map((item) => ({
    id: item.id,
    venueId: restaurantId,
    title: item.description || "Задача",
    description: item.description || "",
    status: item.status,
    assigneeId: byMembership.get(item.assignedToMembershipId) ?? item.assignedToMembershipId,
    creatorId: null,
    tableId: null,
    createdAt: item.createdAt || new Date().toISOString(),
    dueAt: item.deadlineAt || null,
    startedAt: null,
    doneAt: item.completedAt || null,
    overdueNotified: false,
    deletedAt: null,
  }));

  const activeShift =
    current?.status === "ACTIVE" || shiftDetail?.status === "ACTIVE" || activeFromList
      ? shiftDetail || current || activeFromList
      : null;
  const staffOnShift = people.filter(
    (person) => person.role !== "MANAGER" && person.status === "ACTIVE" && !person.invitationToken,
  );
  let memberIds = participants
    .map((row) => {
      const membershipId = idOf(row.membershipId);
      const person = people.find((item) => sameId(item.membershipId, membershipId) || sameId(item.id, membershipId));
      return idOf(person?.id) || byMembership.get(membershipId) || membershipId;
    })
    .filter(Boolean);
  if (!memberIds.length && (activeFromList?.employeeCount > 0 || activeShift?.status === "ACTIVE")) {
    memberIds = staffOnShift.map((person) => idOf(person.id)).filter(Boolean);
  }
  const mappedShift = activeShift
    ? {
        id: idOf(activeShift.id),
        venueId: idOf(restaurantId),
        date: day(activeShift.actualStartedAt || activeShift.scheduledStartAt),
        start: clock(activeShift.actualStartedAt || activeShift.scheduledStartAt),
        end: clock(activeShift.scheduledEndAt),
        status: activeShift.status,
        memberIds,
        employeeCount: Number(activeFromList?.employeeCount ?? memberIds.length) || memberIds.length,
      }
    : null;

  return {
    tokens,
    user: {
      ...meRow,
      venue: venueName,
      restaurantName: venueName,
      restaurantId,
      setupStep: resolveSetup(profile, halls, tables),
      subscription: "active",
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresIn: tokens.expiresIn,
    },
    snapshot: {
      version: 3,
      seq: Date.now(),
      users: people,
      venues: restaurantId
        ? [{ id: restaurantId, name: venueName, setupStep: "done", subscription: "active" }]
        : [],
      halls: mappedHalls,
      tables: mappedTables,
      tasks: mappedTasks,
      favorites: [],
      notifications: notifications.map((item) => ({
        id: item.id,
        userId: profile.id,
        title: item.title,
        body: item.message,
        kind: item.type,
        read: Boolean(item.readAt),
        createdAt: item.createdAt,
      })),
      history: [],
      shifts: shiftList.map((item) => ({
        id: idOf(item.id),
        venueId: idOf(restaurantId),
        date: day(item.actualStartedAt || item.scheduledStartAt),
        start: clock(item.scheduledStartAt),
        end: clock(item.scheduledEndAt),
        status: item.status,
        memberIds: sameId(item.id, mappedShift?.id) ? memberIds : [],
        employeeCount: Number(item.employeeCount) || 0,
      })),
      attendance: memberIds.map((userId, index) => ({
        id: index + 1,
        shiftId: mappedShift?.id,
        userId,
        present: true,
      })),
      plans: [],
      currentShiftId: mappedShift?.id ?? null,
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
  return (await request(paths.tableHistory(restaurantId, tableId))) ?? [];
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

export async function openShiftRemote(restaurantId, { start, end, membershipIds, tableIdsByMember }) {
  const today = new Date();
  const [startH, startM] = String(start || "10:00").split(":").map(Number);
  const [endH, endM] = String(end || "22:00").split(":").map(Number);
  const begin = new Date(today);
  begin.setHours(startH || 10, startM || 0, 0, 0);
  const finish = new Date(today);
  finish.setHours(endH || 22, endM || 0, 0, 0);
  const created = await request(paths.shifts(restaurantId), {
    method: "POST",
    body: { scheduledStartAt: begin.toISOString(), scheduledEndAt: finish.toISOString() },
  });
  const assigned = tableIdsByMember || tableIdsByMemberFromDraft();
  if (membershipIds?.length) {
    await request(`${paths.shifts(restaurantId)}/${created.id}/participants`, {
      method: "PUT",
      body: {
        participants: membershipIds.map((membershipId) => ({
          membershipId,
          tableIds: assigned[membershipId] ?? [],
        })),
      },
    });
  }
  await request(paths.startShift(restaurantId, created.id), { method: "POST" });
  clearAssignmentDraft();
}

export async function closeShiftRemote(restaurantId, shiftId, unfinishedTasks) {
  await request(paths.closeShift(restaurantId, shiftId), {
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
  rememberInvitation(invitation);
  return invitation;
}

export async function redeemInviteRemote({ token, login, password }) {
  const tokens = await request(paths.acceptInvitation(token), {
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
