import { ArrowLeft } from "phosphor-react-native";
import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { colors, radius, shadow, space, type as font } from "./theme";

export function Button({ label, onPress, tone = "accent", disabled = false }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        toneStyles[tone],
        pressed && !disabled ? styles.pressed : null,
        disabled ? styles.disabled : null,
      ]}
    >
      <Text style={[styles.buttonLabel, labelStyles[tone]]}>{label}</Text>
    </Pressable>
  );
}

export function Field({
  label,
  value,
  onChangeText,
  secure = false,
  keyboard = "default",
  error,
  capitalize = "none",
  multiline = false,
  placeholder,
}) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secure}
        autoCapitalize={capitalize}
        autoCorrect={false}
        keyboardType={keyboard}
        accessibilityLabel={label}
        multiline={multiline}
        placeholder={placeholder}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        placeholderTextColor={colors.faint}
        style={[styles.input, multiline ? styles.inputMulti : null, focused ? styles.inputFocused : null]}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

export function Screen({ children, footer, scroll = false }) {
  const body = scroll ? (
    <ScrollView
      style={styles.body}
      contentContainerStyle={styles.scrollContent}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  ) : (
    <View style={styles.body}>{children}</View>
  );
  return (
    <View style={styles.screen}>
      {body}
      {footer ? <View style={styles.footer}>{footer}</View> : null}
    </View>
  );
}

export function BackLink({ onPress, label = "Назад" }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} hitSlop={8} onPress={onPress} style={styles.backHit}>
      <View accessibilityElementsHidden importantForAccessibility="no">
        <ArrowLeft color={colors.ink} size={20} weight="regular" />
      </View>
      <Text style={styles.back}>{label}</Text>
    </Pressable>
  );
}

export function Title({ children }) {
  return <Text style={styles.title}>{children}</Text>;
}

export function Lead({ children }) {
  return <Text style={styles.lead}>{children}</Text>;
}

export function Step({ children }) {
  return <Text style={styles.step}>{children}</Text>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.paper },
  body: { flex: 1, paddingHorizontal: space.lg, paddingTop: space.sm, gap: space.sm },
  scrollContent: { gap: space.sm, paddingBottom: space.lg },
  footer: { paddingHorizontal: space.lg, paddingBottom: space.sm, paddingTop: space.xs, gap: space.xs },
  backHit: { minHeight: 48, flexDirection: "row", alignItems: "center", gap: 6, alignSelf: "flex-start", paddingRight: 8 },
  back: { fontFamily: font.bold, color: colors.ink, fontSize: 16 },
  title: {
    fontFamily: font.bold,
    fontSize: 28,
    letterSpacing: -0.6,
    color: colors.ink,
    lineHeight: 34,
  },
  lead: { fontFamily: font.regular, fontSize: 16, lineHeight: 22, color: colors.mute },
  step: { fontFamily: font.semibold, fontSize: 13, color: colors.mute },
  button: {
    minHeight: 48,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: space.md,
    boxShadow: shadow.hard,
  },
  buttonLabel: { fontFamily: font.bold, fontSize: 16 },
  pressed: { transform: [{ translateX: 2 }, { translateY: 2 }], boxShadow: "0px 0px 0 #141414" },
  disabled: { opacity: 0.45 },
  field: { gap: space.xs },
  fieldLabel: { fontFamily: font.semibold, fontSize: 13, color: colors.ink },
  input: {
    minHeight: 52,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    paddingHorizontal: space.md,
    fontFamily: font.regular,
    fontSize: 16,
    color: colors.ink,
    boxShadow: shadow.hard,
  },
  inputMulti: { minHeight: 96, textAlignVertical: "top", paddingTop: 12 },
  inputFocused: { borderColor: colors.ink },
  error: { fontFamily: font.regular, fontSize: 13, color: colors.danger },
});

const toneStyles = StyleSheet.create({
  accent: { backgroundColor: colors.accent },
  ghost: { backgroundColor: colors.white },
  danger: { backgroundColor: colors.danger },
});

const labelStyles = StyleSheet.create({
  accent: { color: colors.ink },
  ghost: { color: colors.ink },
  danger: { color: colors.onDanger },
});
