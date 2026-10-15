import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSession } from "../session";
import { STAFF_ROLES, TABLE_STATUSES, permissionsFor, roleLabel, roleLine, shiftStatusLabel } from "../domain/catalog";
import { formatDay, todayKey } from "../domain/format";
import { api } from "../api/store";
import { Avatar, Choice, Panel } from "../components/blocks";
import { colors, space, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

export function TeamScreen({ navigation }) {
  const data = useRestaurant();
  if (!data) {
    return null;
  }
  const people = data.people.filter((person) => person.status !== "INACTIVE");

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Команда</Title>
      <Lead>{data.shift ? `Смена ${data.shift.start}-${data.shift.end}` : "Смена на сегодня не открыта"}</Lead>
      {data.shift ? (
        <Button label="Состав смены" onPress={() => navigation.navigate("ShiftOpen")} />
      ) : (
        <Button label="Открыть смену" onPress={() => navigation.navigate("ShiftOpen")} />
      )}
      <Button tone="ghost" label="Пригласить" onPress={() => navigation.navigate("Invite")} />
      {people.map((person) => {
        const onShift = data.onShift.includes(person.id);
        return (
          <Pressable
            key={person.id}
            accessibilityRole="button"
            accessibilityLabel={person.fullName}
            onPress={() => navigation.navigate("Person", { userId: person.id })}
            style={({ pressed }) => [pressed ? styles.pressed : null]}
          >
            <Panel>
              <Text style={styles.name}>{person.fullName}</Text>
              <Text style={styles.meta}>
                {roleLine(person)}
                {onShift ? ", на смене" : ""}
              </Text>
            </Panel>
          </Pressable>
        );
      })}
    </Screen>
  );
}

export function ShiftBoardScreen({ navigation }) {
  const data = useRestaurant();
  if (!data) {
    return null;
  }
  const people = data.people.filter((person) => person.role !== "MANAGER" && person.status !== "INACTIVE" && person.status !== "ARCHIVED");

  return (
    <Screen scroll>
      <Title>Кто на смене?</Title>
      <Lead>{data.shift ? `Смена идёт, ${data.shift.start}-${data.shift.end}` : "Смена ещё не открыта"}</Lead>
      {data.shift ? (
        <Button label="Состав" onPress={() => navigation.navigate("ShiftOpen")} />
      ) : (
        <Button label="Начать смену" onPress={() => navigation.navigate("ShiftOpen")} />
      )}
      {data.shift ? <Button tone="ghost" label="Закрыть смену" onPress={() => navigation.navigate("ShiftClose")} /> : null}
      {people.map((person) => {
        const onShift = data.onShift.includes(person.id);
        return (
          <Pressable
            key={person.id}
            accessibilityRole="button"
            accessibilityLabel={`${person.fullName}, ${onShift ? "на смене" : "отсутствует"}`}
            onPress={() => navigation.navigate("Person", { userId: person.id })}
            style={({ pressed }) => [pressed ? styles.pressed : null]}
          >
            <Panel>
              <View style={styles.personRow}>
                <Avatar name={person.fullName} />
                <View style={styles.flex}>
                  <Text style={styles.name}>{person.fullName}</Text>
                  <Text style={styles.meta}>
                    {roleLabel(person.role)}, {onShift ? "на смене" : "отсутствует"}
                  </Text>
                </View>
              </View>
            </Panel>
          </Pressable>
        );
      })}
    </Screen>
  );
}

export function PersonScreen({ route, navigation }) {
  const data = useRestaurant();
  const person = data?.people.find((item) => item.id === route.params.userId);
  const [error, setError] = useState(null);
  if (!data || !person) {
    return (
      <Screen>
        <BackLink onPress={() => navigation.goBack()} />
        <Title>Сотрудник не найден</Title>
      </Screen>
    );
  }
  const shifts = data.shifts.filter((shift) => data.attendance.some((row) => row.shiftId === shift.id && row.userId === person.id));
  const tasks = data.allTasks.filter((task) => task.assigneeId === person.id && task.status !== "COMPLETED" && task.status !== "CANCELLED");

  function save(role) {
    try {
      api.updateStaff({
        actorId: data.me.id,
        userId: person.id,
        fullName: person.fullName,
        role,
        permissions: permissionsFor(role),
      });
      setError(null);
    } catch (caught) {
      setError(caught.message);
    }
  }

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>{person.fullName}</Title>
      <Lead>{roleLine(person)}</Lead>
      <Text style={styles.meta}>В команде с {person.createdAt ? formatDay(person.createdAt) : "этого дня"}</Text>
      <Text style={styles.section}>Открытые задачи: {tasks.length}</Text>
      {person.invitationToken && person.inviteUses === 0 ? (
        <Text style={styles.meta}>Приглашение ещё не принято: {person.invitationUrl}</Text>
      ) : null}
      <Text style={styles.section}>Смены: {shifts.length}</Text>
      {shifts.slice(0, 5).map((shift) => (
        <Text key={shift.id} style={styles.meta}>
          {shift.date} {shift.start}-{shift.end}, {shiftStatusLabel(shift.status)}
        </Text>
      ))}
      {data.me.role === "MANAGER" && person.id !== data.me.id ? (
        <>
          <Text style={styles.section}>Роль</Text>
          <View style={styles.wrap}>
            {STAFF_ROLES.map((role) => (
              <Choice key={role.id} label={role.label} selected={person.role === role.id} onPress={() => save(role.id)} />
            ))}
          </View>
          <Text style={styles.section}>План на сегодня</Text>
          <DayPlan person={person} actorId={data.me.id} plans={data.plans} />
          <Text style={styles.section}>Статусы стола</Text>
          <View style={styles.wrap}>
            {TABLE_STATUSES.map((status) => {
              const selected = (person.permissions?.tableStatuses ?? []).includes(status.id);
              return (
                <Choice
                  key={status.id}
                  label={status.label}
                  selected={selected}
                  onPress={() => {
                    const current = person.permissions?.tableStatuses ?? [];
                    const tableStatuses = selected ? current.filter((id) => id !== status.id) : [...current, status.id];
                    api.updateStaff({
                      actorId: data.me.id,
                      userId: person.id,
                      fullName: person.fullName,
                      role: person.role,
                      permissions: {
                        tableStatuses,
                        ownTablesOnly: person.role === "WAITER",
                      },
                    });
                  }}
                />
              );
            })}
          </View>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button
            tone="ghost"
            label={person.status === "BLOCKED" ? "Разблокировать" : "Заблокировать"}
            onPress={() => api.blockStaff({ actorId: data.me.id, userId: person.id, blocked: person.status !== "BLOCKED" })}
          />
          <Button
            tone="danger"
            label="Убрать из зала"
            onPress={() => {
              try {
                api.deleteStaff({ actorId: data.me.id, userId: person.id });
                navigation.goBack();
              } catch (caught) {
                setError(caught.message);
              }
            }}
          />
        </>
      ) : null}
    </Screen>
  );
}

export function ShiftOpenScreen({ navigation }) {
  const data = useRestaurant();
  const [start, setStart] = useState(data?.shift?.start ?? "12:00");
  const [end, setEnd] = useState(data?.shift?.end ?? "23:00");
  const [picked, setPicked] = useState(data?.onShift ?? []);
  const [error, setError] = useState(null);
  if (!data) {
    return null;
  }
  const staff = data.people.filter((person) => person.status === "ACTIVE");

  function toggle(id) {
    setPicked((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  return (
    <Screen
      scroll
      footer={
        <Button
          label={data.shift ? "Сохранить состав" : "Начать смену"}
          onPress={() => {
            try {
              if (data.shift) {
                api.setShiftMembers({ actorId: data.me.id, shiftId: data.shift.id, memberIds: picked });
              } else {
                api.openShift({ actorId: data.me.id, start, end, memberIds: picked });
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
      <Title>{data.shift ? "Кто на смене" : "Открыть смену"}</Title>
      {!data.shift ? (
        <>
          <Field label="Начало" value={start} onChangeText={setStart} placeholder="12:00" />
          <Field label="Конец" value={end} onChangeText={setEnd} placeholder="23:00" />
        </>
      ) : (
        <Lead>
          {data.shift.start}-{data.shift.end}
        </Lead>
      )}
      {staff.map((person) => {
        const on = picked.includes(person.id);
        return (
          <Pressable
            key={person.id}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: on }}
            onPress={() => toggle(person.id)}
          >
            <Panel style={on ? styles.cardOn : null}>
              <Text style={styles.name}>{person.fullName}</Text>
              <Text style={styles.meta}>{roleLabel(person.role)}</Text>
            </Panel>
          </Pressable>
        );
      })}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </Screen>
  );
}

export function ShiftCloseScreen({ navigation }) {
  const data = useRestaurant();
  const open = (data?.allTasks ?? []).filter((task) => task.status !== "COMPLETED" && task.status !== "CANCELLED");
  const [choices, setChoices] = useState({});
  const [error, setError] = useState(null);
  if (!data) {
    return null;
  }

  return (
    <Screen
      scroll
      footer={
        <Button
          label="Закрыть смену"
          onPress={() => {
            try {
              const result = api.closeShift({
                actorId: data.me.id,
                unfinishedTasks: open.map((task) => ({ taskId: task.id, action: choices[task.id] ?? "CARRY_OVER" })),
              });
              navigation.replace("ShiftReport", { report: result.report });
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Конец смены</Title>
      <Lead>{open.length ? `Не выполнено: ${open.length}` : "Все задачи закрыты."}</Lead>
      {open.map((task) => (
        <Panel key={task.id}>
          <Text style={styles.name}>{task.title}</Text>
          <View style={styles.wrap}>
            {[
              ["CARRY_OVER", "На следующую смену"],
              ["CANCEL", "Отменить"],
            ].map(([action, label]) => (
              <Choice
                key={action}
                label={label}
                selected={(choices[task.id] ?? "CARRY_OVER") === action}
                onPress={() => setChoices((current) => ({ ...current, [task.id]: action }))}
              />
            ))}
          </View>
        </Panel>
      ))}
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </Screen>
  );
}

function DayPlan({ person, actorId, plans }) {
  const plan = (plans ?? []).find((item) => item.userId === person.id && item.date === todayKey());
  const [text, setText] = useState("");
  const [error, setError] = useState(null);
  const items = plan?.items ?? [];

  return (
    <View style={styles.stack}>
      {items.map((item) => (
        <Pressable
          key={item.id}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: item.done }}
          onPress={() => api.togglePlanItem({ actorId, planId: plan.id, itemId: item.id })}
          style={styles.planRow}
        >
          <View style={[styles.check, item.done ? styles.checkOn : null]}>
            <Text style={styles.checkMark}>{item.done ? "✓" : ""}</Text>
          </View>
          <Text style={[styles.planText, item.done ? styles.done : null]}>{item.text}</Text>
        </Pressable>
      ))}
      {!items.length ? <Text style={styles.meta}>Пунктов пока нет.</Text> : null}
      <Field label="Новый пункт" value={text} onChangeText={setText} capitalize="sentences" />
      <Button
        tone="ghost"
        label="Добавить в план"
        onPress={() => {
          if (!text.trim()) {
            setError("Введите пункт.");
            return;
          }
          try {
            api.saveDayPlan({
              actorId,
              userId: person.id,
              items: [...items.map((item) => item.text), text],
            });
            setText("");
            setError(null);
          } catch (caught) {
            setError(caught.message);
          }
        }}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

export function ShiftReportScreen({ route }) {
  const { signOut } = useSession();
  const report = route.params?.report;
  return (
    <Screen scroll footer={<Button tone="danger" label="Выйти" onPress={() => void signOut()} />}>
      <Title>Отчёт смены</Title>
      {report ? (
        <>
          <Text style={styles.name}>Создано: {report.created}</Text>
          <Text style={styles.name}>Выполнено: {report.done}</Text>
          <Text style={styles.name}>Перенесено: {report.carried}</Text>
          <Text style={styles.name}>Отменено: {report.cancelled}</Text>
        </>
      ) : (
        <Lead>Отчёт появится после закрытия смены.</Lead>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  personRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  flex: { flex: 1 },
  cardOn: { backgroundColor: colors.accent },
  name: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
  meta: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.mute },
  section: { fontFamily: font.bold, fontSize: 16, color: colors.ink, marginTop: space.xs },
  error: { fontFamily: font.regular, color: colors.danger, fontSize: 14 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  pressed: { transform: [{ translateX: 1 }, { translateY: 1 }] },
  stack: { gap: 8 },
  planRow: { minHeight: 44, flexDirection: "row", alignItems: "center", gap: 10 },
  check: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
  },
  checkOn: { backgroundColor: colors.accent },
  checkMark: { fontFamily: font.bold, fontSize: 13, color: colors.ink, lineHeight: 16 },
  planText: { flex: 1, fontFamily: font.regular, fontSize: 15, color: colors.ink },
  done: { textDecorationLine: "line-through", color: colors.mute },
});
