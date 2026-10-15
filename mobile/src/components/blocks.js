import { Pressable, StyleSheet, Text, View } from "react-native";
import { statusLabel, taskStatusLabel } from "../domain/catalog";
import { colors, radius, shadow, statusTint, taskTint, type as font } from "../theme";

export function TableTile({ table, onPress, hasTask = false }) {
  const label = statusLabel(table.status);
  const spoken = hasTask ? `Стол ${table.number}, ${label}, есть задача` : `Стол ${table.number}, ${label}`;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={spoken}
      onPress={onPress}
      style={({ pressed }) => [styles.tile, pressed ? styles.pressed : null]}
    >
      <View style={[styles.swatch, { backgroundColor: statusTint[table.status] }]} />
      <Text style={styles.number}>{table.number}</Text>
      <Text style={styles.caption}>{label}</Text>
      {hasTask ? <Text style={styles.taskFlag}>Задача</Text> : null}
    </Pressable>
  );
}

export function TaskRow({ task, subtitle, onPress }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${task.title}, ${taskStatusLabel(task.status)}`}
      onPress={onPress}
      style={({ pressed }) => [styles.row, pressed ? styles.pressed : null]}
    >
      <View style={[styles.swatch, { backgroundColor: taskTint[task.status] ?? colors.paper }]} />
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>{task.title}</Text>
        <Text style={styles.rowBody}>{subtitle}</Text>
      </View>
      <Text style={styles.badge}>{taskStatusLabel(task.status)}</Text>
    </Pressable>
  );
}

export function Metric({ value, label, onPress }) {
  return (
    <Pressable
      accessibilityRole={onPress ? "button" : "text"}
      accessibilityLabel={`${label}: ${value}`}
      disabled={!onPress}
      onPress={onPress}
      style={({ pressed }) => [styles.metric, pressed && onPress ? styles.pressed : null]}
    >
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
    </Pressable>
  );
}

export function Avatar({ name, size = 44 }) {
  const letter = String(name || "?").trim().charAt(0).toUpperCase();
  return (
    <View style={[styles.avatar, { width: size, height: size }]}>
      <Text style={[styles.letter, size >= 52 ? styles.letterLg : null]}>{letter}</Text>
    </View>
  );
}

export function Panel({ children, style }) {
  return <View style={[styles.panel, style]}>{children}</View>;
}

export function Choice({ label, selected, onPress }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => [styles.choice, selected ? styles.choiceOn : null, pressed ? styles.pressed : null]}
    >
      <Text style={styles.choiceLabel}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tile: {
    flexGrow: 1,
    flexBasis: "30%",
    maxWidth: "32%",
    minHeight: 108,
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
    padding: 12,
    gap: 4,
    minWidth: 0,
  },
  swatch: {
    width: 18,
    height: 18,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: colors.ink,
  },
  number: { fontFamily: font.bold, fontSize: 28, color: colors.ink, letterSpacing: -0.6 },
  caption: { fontFamily: font.semibold, fontSize: 12, lineHeight: 18, color: colors.ink },
  taskFlag: {
    alignSelf: "flex-start",
    marginTop: 2,
    backgroundColor: colors.taskSoft,
    borderWidth: 2,
    borderColor: colors.ink,
    borderRadius: 6,
    paddingHorizontal: 6,
    paddingVertical: 2,
    fontFamily: font.bold,
    fontSize: 12,
    color: colors.ink,
  },
  row: {
    minHeight: 72,
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
    padding: 14,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
  rowBody: { fontFamily: font.regular, fontSize: 13, lineHeight: 18, color: colors.mute },
  badge: { fontFamily: font.bold, fontSize: 12, color: colors.ink, maxWidth: 96, textAlign: "right" },
  metric: {
    flex: 1,
    minHeight: 84,
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
    padding: 10,
    justifyContent: "space-between",
  },
  metricValue: { fontFamily: font.bold, fontSize: 22, color: colors.ink, letterSpacing: -0.4 },
  metricLabel: { fontFamily: font.semibold, fontSize: 12, lineHeight: 16, color: colors.ink },
  choice: {
    minHeight: 48,
    paddingHorizontal: 12,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
  },
  choiceOn: { backgroundColor: colors.accent, boxShadow: shadow.tight },
  choiceLabel: { fontFamily: font.bold, fontSize: 14, color: colors.ink },
  avatar: {
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.paper,
    alignItems: "center",
    justifyContent: "center",
  },
  letter: { fontFamily: font.bold, fontSize: 18, color: colors.ink },
  letterLg: { fontSize: 22 },
  panel: {
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
    padding: 14,
    gap: 6,
  },
  pressed: { transform: [{ translateX: 1 }, { translateY: 1 }] },
});
