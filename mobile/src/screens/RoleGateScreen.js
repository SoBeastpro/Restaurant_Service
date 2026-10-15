import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { colors, radius, shadow, space, type as font } from "../theme";
import { Screen } from "../ui";

export function RoleGateScreen({ onPick }) {
  const [intent, setIntent] = useState("manager");

  return (
    <Screen>
      <Text style={styles.brand}>REST</Text>
      <Text style={styles.title}>Кто вы в смене?</Text>
      <Text style={styles.lead}>
        От роли зависит, что откроется после входа: зал целиком или свои столы.
      </Text>
      <View style={styles.choices}>
        <Choice
          title="Управляю рестораном"
          body="Зал, сотрудники и смена."
          selected={intent === "manager"}
          onPress={() => {
            setIntent("manager");
            onPick("manager");
          }}
        />
        <Choice
          title="Я сотрудник"
          body="Почта от управляющего или код приглашения."
          selected={intent === "staff"}
          onPress={() => {
            setIntent("staff");
            onPick("staff");
          }}
        />
      </View>
    </Screen>
  );
}

function Choice({ title, body, selected, onPress }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [
        styles.choice,
        selected ? styles.on : styles.off,
        pressed ? styles.pressed : null,
      ]}
    >
      <Text style={styles.choiceTitle}>{title}</Text>
      <Text style={styles.choiceBody}>{body}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  brand: { fontFamily: font.bold, color: colors.ink, fontSize: 13, marginTop: space.sm },
  title: {
    fontFamily: font.bold,
    fontSize: 32,
    letterSpacing: -0.8,
    color: colors.ink,
    lineHeight: 38,
  },
  lead: { fontFamily: font.regular, fontSize: 16, lineHeight: 22, color: colors.mute },
  choices: { gap: space.sm, marginTop: space.md },
  choice: {
    borderRadius: radius.choice,
    borderWidth: 2,
    borderColor: colors.ink,
    padding: 18,
    gap: 6,
    minHeight: 88,
  },
  on: { backgroundColor: colors.accent, boxShadow: shadow.hard },
  off: { backgroundColor: colors.white },
  pressed: { transform: [{ translateX: 2 }, { translateY: 2 }] },
  choiceTitle: { fontFamily: font.bold, fontSize: 18, color: colors.ink },
  choiceBody: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.ink },
});
