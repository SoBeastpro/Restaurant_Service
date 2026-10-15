import { Bell } from "phosphor-react-native";
import { useState } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { TABLE_STATUSES, canSetTableStatus, statusLabel } from "../domain/catalog";
import { formatClock, formatDay } from "../domain/format";
import { api, userName } from "../api/store";
import { Choice, Metric, TableTile } from "../components/blocks";
import { colors, radius, shadow, space, statusTint, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

function tableSpan(numbers) {
  const sorted = [...numbers].sort((left, right) => left - right);
  const parts = [];
  let start = sorted[0];
  let previous = sorted[0];
  for (let index = 1; index <= sorted.length; index += 1) {
    const current = sorted[index];
    if (current === previous + 1) {
      previous = current;
      continue;
    }
    parts.push(start === previous ? String(start) : `${start}-${previous}`);
    start = current;
    previous = current;
  }
  return parts.join(", ");
}

function openTaskIds(tasks) {
  return new Set(
    tasks
      .filter((task) => task.tableId && task.status !== "COMPLETED" && task.status !== "CANCELLED")
      .map((task) => task.tableId),
  );
}

export function HallScreen() {
  const navigation = useNavigation();
  const data = useRestaurant();
  const [hallId, setHallId] = useState(null);
  const [hallName, setHallName] = useState("");
  const [addingHall, setAddingHall] = useState(false);
  const [error, setError] = useState(null);
  if (!data) {
    return null;
  }
  const manager = data.me.role === "MANAGER";
  const activeHall = data.halls.find((hall) => hall.id === hallId) ?? data.halls[0];
  const source = manager ? data.allTables : data.tables;
  const tables = source.filter((table) => table.hallId === activeHall?.id);
  const attention = source.filter((table) => table.status !== "READY").length;
  const done = data.todayTasks.filter((task) => task.status === "COMPLETED").length;
  const withTask = openTaskIds(manager ? data.allTasks : data.tasks);
  const unread = data.notifications.filter((item) => !item.read).length;
  const staffTitle = data.tables.length
    ? `${data.me.firstName || data.me.fullName} столы ${tableSpan(data.tables.map((table) => table.number))}`
    : data.me.firstName || data.me.fullName;

  return (
    <Screen scroll>
      <View style={styles.top}>
        <View style={styles.flex}>
          <Title>{manager ? data.me.venue : staffTitle}</Title>
          <Lead>{manager ? (data.shift ? "Смена идёт" : "Смена ещё не открыта") : "Мой зал"}</Lead>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={unread ? `Уведомления, новых ${unread}` : "Уведомления"}
          onPress={() => navigation.navigate("Notifications")}
          style={styles.bell}
        >
          <View accessibilityElementsHidden importantForAccessibility="no">
            <Bell color={colors.ink} size={22} weight="regular" />
          </View>
          {unread ? (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{unread > 9 ? "9+" : unread}</Text>
            </View>
          ) : null}
        </Pressable>
      </View>
      {manager ? (
        <View style={styles.metrics}>
          <Metric value={String(data.onShift.length)} label="Сотрудники" onPress={() => navigation.navigate("Shift")} />
          <Metric value={`${done}/${data.todayTasks.length}`} label="Задачи" onPress={() => navigation.navigate("Tasks")} />
          <Metric value={String(attention)} label="Внимание" />
        </View>
      ) : null}
      {manager && data.halls.length > 1 ? (
        <View style={styles.wrap}>
          {data.halls.map((hall) => (
            <Choice key={hall.id} label={hall.name} selected={hall.id === activeHall?.id} onPress={() => setHallId(hall.id)} />
          ))}
        </View>
      ) : null}
      {activeHall ? <Text style={styles.section}>{activeHall.name}</Text> : null}
      {manager && !data.shift ? <Button label="Открыть смену" onPress={() => navigation.navigate("ShiftOpen")} /> : null}
      <View style={styles.grid}>
        {tables.map((table) => (
          <TableTile
            key={table.id}
            table={table}
            hasTask={withTask.has(table.id)}
            onPress={() => navigation.navigate("Table", { tableId: table.id })}
          />
        ))}
      </View>
      {!tables.length ? (
        <Lead>{manager ? "В этом зале пока нет столов." : "Управляющий ещё не закрепил за вами столы."}</Lead>
      ) : null}
      {manager && activeHall ? (
        <>
          <Button tone="ghost" label="Добавить стол" onPress={() => navigation.navigate("TableEdit", { hallId: activeHall.id })} />
          {addingHall ? (
            <>
              <Field label="Название зала" value={hallName} onChangeText={setHallName} capitalize="sentences" error={error} />
              <Button
                label="Создать зал"
                onPress={() => {
                  try {
                    const created = api.createHall({ actorId: data.me.id, name: hallName });
                    setHallId(created.hallId);
                    setHallName("");
                    setAddingHall(false);
                    setError(null);
                  } catch (caught) {
                    setError(caught.message);
                  }
                }}
              />
            </>
          ) : (
            <Button tone="ghost" label="Новый зал" onPress={() => setAddingHall(true)} />
          )}
        </>
      ) : null}
    </Screen>
  );
}

export function TableEditScreen({ route, navigation }) {
  const data = useRestaurant();
  const existing = data?.allTables.find((table) => table.id === route.params?.tableId);
  const [number, setNumber] = useState(existing ? String(existing.number) : "");
  const [seats, setSeats] = useState(existing ? String(existing.seats) : "4");
  const [error, setError] = useState(null);

  return (
    <Screen
      scroll
      footer={
        <Button
          label="Сохранить"
          onPress={() => {
            try {
              if (existing) {
                api.updateTable({ actorId: data.me.id, tableId: existing.id, number, seats });
              } else {
                api.createTable({ actorId: data.me.id, hallId: route.params.hallId, number, seats });
              }
              navigation.goBack();
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>{existing ? `Стол ${existing.number}` : "Новый стол"}</Title>
      <Field label="Номер" value={number} onChangeText={setNumber} keyboard="number-pad" />
      <Field label="Мест" value={seats} onChangeText={setSeats} keyboard="number-pad" error={error} />
    </Screen>
  );
}

export function TableScreen({ route, navigation }) {
  const data = useRestaurant();
  const table = data?.allTables.find((item) => item.id === route.params.tableId) ?? data?.tables.find((item) => item.id === route.params.tableId);
  const [problem, setProblem] = useState(false);
  const [comment, setComment] = useState("");
  const [error, setError] = useState(null);
  if (!data || !table) {
    return (
      <Screen>
        <BackLink onPress={() => navigation.goBack()} />
        <Title>Стол не найден</Title>
      </Screen>
    );
  }
  const assignee = data.people.find((person) => person.id === table.assigneeId);
  const allowed = TABLE_STATUSES.filter((status) => canSetTableStatus(data.me, table, status.id));
  const waiters = data.people.filter((person) => person.role === "WAITER");

  function apply(status, note) {
    try {
      api.setTableStatus({ userId: data.me.id, tableId: table.id, status, comment: note });
      setError(null);
      setProblem(false);
      setComment("");
    } catch (caught) {
      setError(caught.message);
    }
  }

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <View style={styles.statusHead}>
        <View style={[styles.bigSwatch, { backgroundColor: statusTint[table.status] }]} />
        <View style={styles.flex}>
          <Title>Стол {table.number}</Title>
          <Lead>
            {statusLabel(table.status)}, {table.seats} мест
          </Lead>
        </View>
      </View>
      <Text style={styles.line}>{assignee ? `Закреплён: ${assignee.fullName}` : "Официант не закреплён"}</Text>
      {table.problem ? <Text style={styles.line}>Проблема: {table.problem}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <View style={styles.wrap}>
        {allowed.map((status) => (
          <Pressable
            key={status.id}
            accessibilityRole="button"
            accessibilityLabel={status.label}
            onPress={() => {
              if (status.id === "PROBLEM") {
                setProblem(true);
                return;
              }
              apply(status.id);
            }}
            style={({ pressed }) => [styles.statusBtn, pressed ? styles.pressed : null]}
          >
            <View style={[styles.dot, { backgroundColor: statusTint[status.id] }]} />
            <Text style={styles.statusText}>{status.label}</Text>
          </Pressable>
        ))}
      </View>
      {data.me.role === "MANAGER" ? (
        <View style={styles.stack}>
          <Text style={styles.section}>Закрепить</Text>
          <View style={styles.wrap}>
            {waiters.map((person) => (
              <Pressable
                key={person.id}
                accessibilityRole="button"
                accessibilityState={{ selected: table.assigneeId === person.id }}
                onPress={() => api.assignTable({ actorId: data.me.id, tableId: table.id, assigneeId: person.id })}
                style={[styles.statusBtn, table.assigneeId === person.id ? styles.selected : null]}
              >
                <Text style={styles.statusText}>{person.fullName}</Text>
              </Pressable>
            ))}
          </View>
          <Button tone="ghost" label="Номер и места" onPress={() => navigation.navigate("TableEdit", { tableId: table.id, hallId: table.hallId })} />
        </View>
      ) : null}
      {data.me.role === "MANAGER" || data.me.role === "WAITER" ? (
        <Button label="Задача по столу" onPress={() => navigation.navigate("TaskForm", { tableId: table.id })} />
      ) : null}
      <Button tone="ghost" label="История стола" onPress={() => navigation.navigate("History", { tableId: table.id })} />
      <ProblemModal
        visible={problem}
        comment={comment}
        error={error}
        onChange={setComment}
        onClose={() => {
          setProblem(false);
          setError(null);
        }}
        onSave={() => apply("PROBLEM", comment)}
      />
    </Screen>
  );
}

function ProblemModal({ visible, comment, error, onChange, onClose, onSave }) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.scrim}>
        <View style={styles.sheet}>
          <Text style={styles.sheetTitle}>Что случилось?</Text>
          <Field label="Описание" value={comment} onChangeText={onChange} multiline capitalize="sentences" error={error} />
          <Button label="Сохранить проблему" onPress={onSave} />
          <Button tone="ghost" label="Отмена" onPress={onClose} />
        </View>
      </View>
    </Modal>
  );
}

export function HistoryScreen({ route, navigation }) {
  const data = useRestaurant();
  const table = data?.allTables.find((item) => item.id === route.params.tableId);
  const rows = (data?.history ?? [])
    .filter((item) => item.tableId === route.params.tableId)
    .slice()
    .sort((left, right) => new Date(right.createdAt) - new Date(left.createdAt));

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>История стола {table?.number ?? ""}</Title>
      {rows.length ? (
        rows.map((row) => (
          <View key={row.id} style={styles.history}>
            <Text style={styles.line}>
              {formatClock(row.createdAt)} {formatDay(row.createdAt)}
            </Text>
            <Text style={styles.rowTitle}>
              {statusLabel(row.newStatus)}. {userName(row.userId)}
            </Text>
            {row.comment ? <Text style={styles.muted}>{row.comment}</Text> : null}
          </View>
        ))
      ) : (
        <Lead>По этому столу ещё нет записей.</Lead>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { flexDirection: "row", alignItems: "flex-start", gap: 12 },
  bell: {
    width: 48,
    height: 48,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
    boxShadow: shadow.tight,
  },
  badge: {
    position: "absolute",
    top: -6,
    right: -6,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: colors.danger,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
  },
  badgeText: { fontFamily: font.bold, fontSize: 10, color: colors.onDanger },
  metrics: { flexDirection: "row", gap: 8 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 12 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  stack: { gap: space.sm },
  flex: { flex: 1 },
  statusHead: { flexDirection: "row", gap: 12, alignItems: "center" },
  bigSwatch: { width: 36, height: 36, borderRadius: 8, borderWidth: 2, borderColor: colors.ink },
  line: { fontFamily: font.semibold, fontSize: 15, color: colors.ink },
  muted: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.mute },
  error: { fontFamily: font.regular, fontSize: 14, color: colors.danger },
  statusBtn: {
    minHeight: 48,
    paddingHorizontal: 12,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  selected: { backgroundColor: colors.accent, boxShadow: shadow.tight },
  dot: { width: 14, height: 14, borderRadius: 4, borderWidth: 2, borderColor: colors.ink },
  statusText: { fontFamily: font.bold, fontSize: 14, color: colors.ink },
  section: { fontFamily: font.bold, fontSize: 16, color: colors.ink },
  pressed: { transform: [{ translateX: 1 }, { translateY: 1 }] },
  scrim: { flex: 1, backgroundColor: "rgba(20,20,20,0.45)", justifyContent: "flex-end", padding: 16 },
  sheet: {
    backgroundColor: colors.paper,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    padding: 16,
    gap: 12,
  },
  sheetTitle: { fontFamily: font.bold, fontSize: 22, color: colors.ink },
  history: {
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    padding: 14,
    gap: 4,
  },
  rowTitle: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
});
