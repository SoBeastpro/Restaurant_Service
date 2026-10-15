import { useEffect, useRef, useState } from "react";
import { AccessibilityInfo, Pressable, StyleSheet, Text, View } from "react-native";
import { TableTile, TaskRow } from "../components/blocks";
import { taskStatusLabel } from "../domain/catalog";
import { colors, space, type as font } from "../theme";
import { Button } from "../ui";

const slides = [
  { key: "hall", phrase: "Статус стола виден всей смене." },
  { key: "task", phrase: "Задача приходит конкретному человеку." },
  { key: "role", phrase: "После входа открывается только ваша работа." },
];

const cycle = ["NEEDS_CLEANING", "NEEDS_SERVING", "READY", "PROBLEM"];
const taskCycle = ["CREATED", "IN_PROGRESS", "COMPLETED"];

export function OnboardingScreen({ onDone }) {
  const [index, setIndex] = useState(0);
  const [reduce, setReduce] = useState(false);
  const indexRef = useRef(0);
  const doneRef = useRef(onDone);
  const startX = useRef(0);
  const last = index === slides.length - 1;

  useEffect(() => {
    doneRef.current = onDone;
  }, [onDone]);

  useEffect(() => {
    indexRef.current = index;
  }, [index]);

  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled().then((value) => {
      if (alive) {
        setReduce(value);
      }
    });
    const subscription = AccessibilityInfo.addEventListener("reduceMotionChanged", setReduce);
    return () => {
      alive = false;
      subscription.remove();
    };
  }, []);

  function go(next) {
    if (next >= slides.length) {
      doneRef.current();
      return;
    }
    setIndex(Math.max(0, next));
  }

  function onGrant(event) {
    startX.current = event.nativeEvent.pageX;
  }

  function onRelease(event) {
    const dx = event.nativeEvent.pageX - startX.current;
    const current = indexRef.current;
    if (dx <= -48) {
      go(current + 1);
    } else if (dx >= 48) {
      go(current - 1);
    }
  }

  const slide = slides[index];

  return (
    <View style={styles.screen}>
      <View
        style={styles.pager}
        onStartShouldSetResponder={() => true}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={onGrant}
        onResponderRelease={onRelease}
      >
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.stage}>
          {slide.key === "hall" ? <HallDemo reduce={reduce} /> : null}
          {slide.key === "task" ? <TaskDemo reduce={reduce} /> : null}
          {slide.key === "role" ? <RoleDemo reduce={reduce} /> : null}
        </View>
      </View>
      <View style={styles.footer}>
        <Text style={styles.phrase}>{slide.phrase}</Text>
        <View style={styles.dots} accessibilityRole="tablist">
          {slides.map((item, dot) => (
            <View key={item.key} style={dot === index ? styles.dotOn : styles.dot} />
          ))}
        </View>
        <Button label={last ? "Начать" : "Дальше"} onPress={() => go(index + 1)} />
        {last ? null : (
          <Pressable accessibilityRole="button" accessibilityLabel="Пропустить" onPress={onDone} style={styles.skipHit}>
            <Text style={styles.skip}>Пропустить</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

function useDemoTick(reduce, interval) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (reduce) {
      return undefined;
    }
    const timer = setInterval(() => setTick((value) => value + 1), interval);
    return () => clearInterval(timer);
  }, [reduce, interval]);
  return tick;
}

function HallDemo({ reduce }) {
  const tick = useDemoTick(reduce, 1400);
  const step = reduce ? cycle.length - 1 : tick % cycle.length;
  const tables = [
    { id: 1, number: 1, status: "READY" },
    { id: 2, number: 2, status: cycle[step] },
    { id: 3, number: 3, status: "NEEDS_SERVING" },
    { id: 4, number: 4, status: "NEEDS_CLEANING", hasTask: true },
    { id: 5, number: 5, status: "PROBLEM" },
    { id: 6, number: 6, status: "READY" },
  ];
  return (
    <View style={styles.grid}>
      {tables.map((table) => (
        <TableTile key={table.id} table={table} hasTask={table.hasTask} onPress={() => {}} />
      ))}
    </View>
  );
}

function TaskDemo({ reduce }) {
  const tick = useDemoTick(reduce, 1500);
  const status = reduce ? "IN_PROGRESS" : taskCycle[tick % taskCycle.length];
  return (
    <View style={styles.taskWrap}>
      <TaskRow task={{ title: "Проверить бар", status }} subtitle={`Анна · ${taskStatusLabel(status)}`} onPress={() => {}} />
      <TaskRow task={{ title: "Сервировка зала 2", status: "CREATED" }} subtitle="Максим · Создана" onPress={() => {}} />
      <TaskRow task={{ title: "Закрыть холодильники", status: "COMPLETED" }} subtitle="Сергей · Выполнена" onPress={() => {}} />
    </View>
  );
}

function RoleDemo({ reduce }) {
  const tick = useDemoTick(reduce, 1600);
  const manager = reduce || tick % 2 === 0;
  const tabs = manager ? ["Зал", "Задачи", "Смена"] : ["Столы", "Задачи", "Профиль"];
  return (
    <View style={styles.roleBox}>
      <Text style={styles.roleWho}>{manager ? "Управляющий" : "Официант"}</Text>
      <View style={styles.tabs}>
        {tabs.map((tab, tabIndex) => (
          <View key={tab} style={[styles.tab, tabIndex === 0 ? styles.tabOn : null]}>
            <Text style={styles.tabText}>{tab}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.paper },
  pager: { flex: 1 },
  stage: { flex: 1, justifyContent: "center", paddingHorizontal: space.lg },
  footer: { paddingHorizontal: space.lg, paddingBottom: space.md, gap: space.sm },
  phrase: {
    fontFamily: font.bold,
    fontSize: 28,
    letterSpacing: -0.6,
    color: colors.ink,
    lineHeight: 34,
  },
  dots: { flexDirection: "row", justifyContent: "center", gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 8, backgroundColor: colors.ink, opacity: 0.28 },
  dotOn: { width: 22, height: 8, borderRadius: 8, backgroundColor: colors.ink },
  skipHit: { minHeight: 48, justifyContent: "center" },
  skip: { textAlign: "center", color: colors.ink, fontFamily: font.semibold, fontSize: 15 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  taskWrap: { gap: 12 },
  roleBox: { gap: 18 },
  roleWho: { fontFamily: font.bold, fontSize: 22, color: colors.ink },
  tabs: { flexDirection: "row", gap: 8 },
  tab: {
    flex: 1,
    minHeight: 56,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: colors.ink,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.white,
  },
  tabOn: { backgroundColor: colors.paper },
  tabText: { fontFamily: font.bold, fontSize: 13, color: colors.ink },
});
