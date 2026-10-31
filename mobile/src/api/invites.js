import AsyncStorage from "@react-native-async-storage/async-storage";
import { extractInviteToken } from "../validation";

const INVITES_KEY = "rest.invites";

// Сервер отдаёт токен только в ответе create/resend, в списке приглашений его нет.
let secrets = new Map();
let loaded = null;

function alive(entry, now = Date.now()) {
  return Boolean(entry?.token) && (!entry.expiresAt || new Date(entry.expiresAt).getTime() > now);
}

async function persist() {
  const rows = Object.fromEntries([...secrets].filter(([, entry]) => alive(entry)));
  await AsyncStorage.setItem(INVITES_KEY, JSON.stringify(rows));
}

export function loadInviteSecrets() {
  if (!loaded) {
    loaded = (async () => {
      try {
        const raw = await AsyncStorage.getItem(INVITES_KEY);
        const rows = raw ? JSON.parse(raw) : {};
        secrets = new Map(Object.entries(rows).filter(([, entry]) => alive(entry)));
      } catch {
        secrets = new Map();
      }
    })();
  }
  return loaded;
}

export async function rememberInvitation(invitation) {
  const token = extractInviteToken(invitation?.invitationUrl || invitation?.qrContent);
  if (!invitation || !token) {
    return;
  }
  await loadInviteSecrets();
  const entry = { token, expiresAt: invitation.expiresAt ?? null };
  secrets.set(String(invitation.id), entry);
  if (invitation.employeeId) {
    secrets.set(String(invitation.employeeId), entry);
  }
  await persist();
}

export function inviteSecret(...ids) {
  for (const id of ids) {
    const entry = id == null ? null : secrets.get(String(id));
    if (alive(entry)) {
      return entry;
    }
  }
  return null;
}
