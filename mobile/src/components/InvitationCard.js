import { useEffect, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import * as Clipboard from "expo-clipboard";
import QRCode from "react-native-qrcode-svg";
import { api } from "../api/store";
import { Button } from "../ui";
import { colors, type as font } from "../theme";

export function InvitationCard({ person }) {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const valid = person.invitationToken && (!person.invitationExpiresAt || Date.parse(person.invitationExpiresAt) > now);
  return <View style={styles.card}>
    <Text style={styles.name}>{person.fullName}</Text>
    {valid ? <>
      <Pressable accessibilityRole="button" accessibilityLabel="Скопировать токен приглашения" onPress={async () => {
        try { await Clipboard.setStringAsync(person.invitationToken); setCopied(true); setError(null); }
        catch { setError("Не удалось скопировать токен. Выделите и скопируйте его вручную."); }
      }}><Text selectable style={styles.token}>{person.invitationToken}</Text></Pressable>
      <Text style={styles.meta}>{copied ? "Токен скопирован" : "Нажмите на токен, чтобы скопировать"}</Text>
      <View style={styles.qr}><QRCode value={person.invitationUrl} size={140} color={colors.ink} backgroundColor={colors.paper} /></View>
    </> : <Text style={styles.meta}>Приглашение недоступно или срок истёк. Выпустите новое.</Text>}
    {person.invitationExpiresAt ? <Text style={styles.meta}>Действует до {new Date(person.invitationExpiresAt).toLocaleString("ru-RU")}</Text> : null}
    {person.invitationId ? <Button tone="ghost" disabled={busy} label={busy ? "Выпускаем…" : "Выпустить заново"} onPress={async () => {
      setBusy(true); setError(null);
      try { await api.resendInvitation({ invitationId: person.invitationId }); setCopied(false); }
      catch (caught) { setError(caught.message); }
      finally { setBusy(false); }
    }} /> : null}
    {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
  </View>;
}
const styles = StyleSheet.create({
  card: { gap: 12, borderWidth: 2, borderColor: colors.ink, borderRadius: 10, padding: 16, backgroundColor: colors.white },
  name: { fontFamily: font.bold, fontSize: 18, color: colors.ink },
  token: { fontFamily: font.semibold, fontSize: 16, color: colors.ink, paddingVertical: 12 },
  meta: { fontFamily: font.regular, fontSize: 13, lineHeight: 20, color: colors.mute },
  error: { fontFamily: font.regular, fontSize: 13, color: colors.danger },
  qr: { alignItems: "center", padding: 12 },
});
