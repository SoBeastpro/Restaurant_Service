import { useState } from "react";
import { Pressable, StyleSheet, Text } from "react-native";
import { useNavigation, useRoute } from "@react-navigation/native";
import { useSession } from "../session";
import { colors, type as font } from "../theme";
import { BackLink, Button, Field, Screen } from "../ui";

export function LoginScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  const { signIn } = useSession();
  const intent = route.params?.intent ?? "staff";
  const manager = intent === "manager";
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

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
        maxLength={100}
      />
      <Field label="Пароль" value={password} onChangeText={setPassword} secure error={error} maxLength={72} />
      <Pressable accessibilityRole="button" onPress={() => navigation.navigate("Reset", { email: login })}>
        <Text style={styles.link}>Забыли пароль?</Text>
      </Pressable>
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
});
