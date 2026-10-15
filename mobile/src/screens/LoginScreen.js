import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useNavigation, useRoute } from "@react-navigation/native";
import { DEMO_STAFF } from "../demo";
import { useSession } from "../session";
import { colors, space, type as font } from "../theme";
import { BackLink, Button, Field, Screen } from "../ui";

export function LoginScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  const { signIn } = useSession();
  const intent = route.params?.intent ?? "staff";
  const [login, setLogin] = useState(intent === "manager" ? "irina@gosti.bar" : "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const manager = intent === "manager";
  const hints = DEMO_STAFF.filter((person) => (manager ? person.role === "MANAGER" : person.role !== "MANAGER"));

  return (
    <Screen
      scroll
      footer={
        <>
          <Button
            label={busy ? "Входим" : "Войти"}
            disabled={busy}
            onPress={async () => {
              setBusy(true);
              const message = await signIn(login, password, intent === "manager" ? "MANAGER" : "EMPLOYEE");
              setError(message);
              setBusy(false);
            }}
          />
          <Button
            tone="ghost"
            label={manager ? "Создать заведение" : "Есть приглашение"}
            onPress={() => navigation.navigate(manager ? "Register" : "Join")}
          />
        </>
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Text style={styles.brand}>REST</Text>
      <Text style={styles.title}>Вход</Text>
      <Text style={styles.lead}>
        {manager ? "Почта и пароль управляющего." : "Логин и пароль сотрудника."}
      </Text>
      <Field
        label={manager ? "Почта" : "Логин"}
        value={login}
        onChangeText={setLogin}
        keyboard={manager ? "email-address" : "default"}
      />
      <Field label="Пароль" value={password} onChangeText={setPassword} secure error={error} />
      <Pressable accessibilityRole="button" onPress={() => navigation.navigate("Reset", { email: login })}>
        <Text style={styles.link}>Забыли пароль?</Text>
      </Pressable>
      <View style={styles.hint}>
        <Text style={styles.hintTitle}>Демо, пароль smena</Text>
        {hints.map((person) => (
          <Pressable key={person.id} onPress={() => setLogin(manager ? person.email : person.username)}>
            <Text style={styles.hintLine}>{manager ? person.email : person.username}</Text>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  brand: { fontFamily: font.bold, color: colors.ink, fontSize: 13 },
  title: {
    fontFamily: font.bold,
    fontSize: 32,
    letterSpacing: -0.8,
    color: colors.ink,
    lineHeight: 38,
  },
  lead: { fontFamily: font.regular, fontSize: 16, lineHeight: 22, color: colors.mute },
  link: { fontFamily: font.bold, fontSize: 14, color: colors.ink, paddingVertical: 8, minHeight: 48 },
  hint: { marginTop: space.sm, gap: 4 },
  hintTitle: { fontFamily: font.semibold, fontSize: 13, color: colors.mute },
  hintLine: { fontFamily: font.regular, fontSize: 14, color: colors.ink, paddingVertical: 6 },
});
