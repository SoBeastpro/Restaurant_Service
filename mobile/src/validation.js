const WEEK_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const CLOCK = /^([01]?\d|2[0-3]):([0-5]\d)$/;
const RANGE = /^(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$/;

export function firstError(...messages) {
  return messages.find(Boolean) ?? null;
}

export function requiredText(value, label, { min = 1, max } = {}) {
  const text = String(value ?? "").trim();
  if (text.length < min) {
    return min <= 1 ? `Укажите ${label}.` : `${label}: минимум ${min} символа.`;
  }
  if (max != null && text.length > max) {
    return `${label}: не больше ${max} символов.`;
  }
  return null;
}

export function personName(value, label = "имя") {
  return requiredText(value, label, { min: 1, max: 100 });
}

export function loginValue(value, label = "логин") {
  const text = String(value ?? "").trim();
  if (!text) {
    return `Укажите ${label}.`;
  }
  if (text.length < 3 || text.length > 100) {
    return `${label}: от 3 до 100 символов.`;
  }
  return null;
}

export function emailLogin(value) {
  const text = String(value ?? "").trim();
  const lengthError = loginValue(text, "почта");
  if (lengthError) {
    return lengthError;
  }
  if (!text.includes("@") || text.startsWith("@") || text.endsWith("@")) {
    return "Укажите почту в формате name@mail.ru.";
  }
  return null;
}

export function passwordValue(value, label = "пароль") {
  const text = String(value ?? "");
  if (text.length < 8) {
    return `${label[0].toUpperCase()}${label.slice(1)} от 8 символов.`;
  }
  if (text.length > 72) {
    return `${label[0].toUpperCase()}${label.slice(1)} не длиннее 72 символов.`;
  }
  return null;
}

export function passwordMatch(password, repeat) {
  if (password !== repeat) {
    return "Пароли не совпадают.";
  }
  return null;
}

export function venueName(value) {
  return requiredText(value, "название ресторана", { max: 200 });
}

export function venueAddress(value) {
  return requiredText(value, "адрес", { max: 500 });
}

export function hallName(value) {
  return requiredText(value, "название зала", { max: 100 });
}

export function parseClock(value) {
  const match = String(value ?? "").trim().match(CLOCK);
  if (!match) {
    return null;
  }
  return `${String(match[1]).padStart(2, "0")}:${match[2]}`;
}

export function clockTime(value, label = "время") {
  if (!parseClock(value)) {
    return `${label[0].toUpperCase()}${label.slice(1)}: формат 12:00.`;
  }
  return null;
}

export function hoursRange(value) {
  const raw = String(value ?? "").trim();
  const match = raw.match(RANGE);
  if (!match || !parseClock(match[1]) || !parseClock(match[2])) {
    return "Часы работы: формат 12:00-23:00.";
  }
  return null;
}

export function openingHoursFromRange(hours) {
  const error = hoursRange(hours);
  if (error) {
    throw new Error(error);
  }
  const match = String(hours).trim().match(RANGE);
  const opensAt = parseClock(match[1]);
  const closesAt = parseClock(match[2]);
  return WEEK_DAYS.map((dayOfWeek) => ({
    dayOfWeek,
    closed: false,
    opensAt,
    closesAt,
  }));
}

export function integerInRange(value, { min, max, label }) {
  const raw = String(value ?? "").trim();
  if (!/^\d+$/.test(raw)) {
    return `Укажите ${label} целым числом.`;
  }
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    return `${label[0].toUpperCase()}${label.slice(1)}: от ${min} до ${max}.`;
  }
  return null;
}

export function tableCount(value) {
  return integerInRange(value, { min: 1, max: 12, label: "число столов" });
}

export function tableNumber(value) {
  const raw = String(value ?? "").trim();
  if (!raw) {
    return "Укажите номер стола.";
  }
  if (raw.length > 20) {
    return "Номер стола: не больше 20 символов.";
  }
  if (!/^\d+$/.test(raw) || Number(raw) < 1) {
    return "Номер стола должен быть целым числом.";
  }
  return null;
}

export function seatCount(value, label = "число мест") {
  return integerInRange(value, { min: 1, max: 100, label });
}

export const INVITE_PREFIX = "rest://invite/";
// Сервер отклоняет токен короче 20 символов (InvitationService.extractToken).
export const INVITE_TOKEN_MIN = 20;

export function extractInviteToken(value) {
  const raw = String(value ?? "").trim();
  return raw.startsWith(INVITE_PREFIX) ? raw.slice(INVITE_PREFIX.length).trim() : raw;
}

export function inviteToken(value) {
  const token = extractInviteToken(value);
  if (!token) {
    return "Укажите токен приглашения.";
  }
  if (token.length < INVITE_TOKEN_MIN) {
    return `Токен приглашения: минимум ${INVITE_TOKEN_MIN} символов. Скопируйте ссылку целиком.`;
  }
  if (token.length > 200) {
    return "Токен приглашения: не больше 200 символов.";
  }
  return null;
}

export function taskTitle(value) {
  return requiredText(value, "что сделать", { max: 200 });
}

export function problemComment(value) {
  return requiredText(value, "описание проблемы", { max: 500 });
}

export function planItem(value) {
  return requiredText(value, "пункт плана", { max: 200 });
}

export function seatsLabel(count) {
  if (count == null || count === "") {
    return "мест: нет данных";
  }
  const n = Number(count);
  if (!Number.isFinite(n)) {
    return "мест";
  }
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${n} место`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${n} места`;
  }
  return `${n} мест`;
}
