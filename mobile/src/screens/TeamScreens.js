import { useCallback, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { useSession } from "../session";
import { roleLabel, roleLine } from "../domain/catalog";
import { formatClock, formatDay } from "../domain/format";
import { plannedRunnerId, plannedWaiterId, shiftLane, shiftPlanGaps } from "../domain/shift";
import { isOnShift, samePerson } from "../domain/view";
import { api } from "../api/store";
import { Avatar, Choice, Panel, Problems } from "../components/blocks";
import { colors, space, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { clockTime, firstError } from "../validation";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

function useRefreshOnFocus() {
  useFocusEffect(
    useCallback(() => {
      void api.refresh();
    }, []),
  );
}

function shiftLead(data) {
  if (data.shift) {
    return `Смена идёт, ${data.shift.start}-${data.shift.end}`;
  }
  if (data.draftShift) {
    return `Черновик смены ${data.draftShift.start}-${data.draftShift.end}, ещё не начата`;
  }
  return "Смена ещё не открыта";
}

export function TeamScreen({ navigation }) {
  const data = useRestaurant();
  useRefreshOnFocus();
  if (!data) {
    return null;
  }
  const people = data.people.filter((person) => person.status !== "INACTIVE");

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Команда</Title>
      <Lead>{shiftLead(data)}</Lead>
      <Problems items={data.problems} />
      <Button label={data.shift ? "Состав смены" : "Открыть смену"} onPress={() => navigation.navigate("ShiftOpen")} />
      <Button tone="ghost" label="Пригласить" onPress={() => navigation.navigate("Invite")} />
      {people.map((person) => {
        const onShift = isOnShift(data, person);
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
  useRefreshOnFocus();
  if (!data) {
    return null;
  }
  const people = data.people.filter((person) => person.role !== "MANAGER" && person.status !== "INACTIVE" && person.status !== "ARCHIVED");

  return (
    <Screen scroll>
      <Title>Кто на смене?</Title>
      <Lead>{shiftLead(data)}</Lead>
      <Problems items={data.problems} />
      <Button
        label={data.shift ? "Состав" : data.draftShift ? "Продолжить смену" : "Начать смену"}
        onPress={() => navigation.navigate("ShiftOpen")}
      />
      {data.shift ? <Button tone="ghost" label="Закрыть смену" onPress={() => navigation.navigate("ShiftClose")} /> : null}
      {people.map((person) => {
        const onShift = isOnShift(data, person);
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
  const person = data?.people.find((item) => samePerson(item, route.params.userId));
  if (!data || !person) {
    return (
      <Screen>
        <BackLink onPress={() => navigation.goBack()} />
        <Title>Сотрудник не найден</Title>
      </Screen>
    );
  }
  const tasks = data.allTasks.filter(
    (task) => samePerson(person, task.assigneeId) && task.status !== "COMPLETED" && task.status !== "CANCELLED",
  );
  const manager = data.me.role === "MANAGER";

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>{person.fullName}</Title>
      <Lead>{roleLine(person)}</Lead>
      {person.createdAt ? <Text style={styles.meta}>В команде с {formatDay(person.createdAt)}</Text> : null}
      <Text style={styles.section}>Открытые задачи: {tasks.length}</Text>
      <Text style={styles.section}>{isOnShift(data, person) ? "Сейчас на смене" : "Сейчас не на смене"}</Text>
      {person.status === "INVITED" ? (
        <>
          <Text style={styles.meta}>Приглашение ещё не принято.</Text>
          {manager ? <Button tone="ghost" label="К приглашениям" onPress={() => navigation.navigate("Invite")} /> : null}
        </>
      ) : null}
      {manager && !samePerson(person, data.me.id) ? (
        <Lead>Смена роли и прав, план дня, блокировка и удаление сотрудника: на сервере ещё нет.</Lead>
      ) : null}
    </Screen>
  );
}

function holderName(people, id) {
  return people.find((person) => samePerson(person, id))?.fullName ?? null;
}

export function ShiftOpenScreen({ navigation }) {
  const data = useRestaurant();
  const base = data?.shift ?? data?.draftShift ?? null;
  const [start, setStart] = useState(base?.start || "12:00");
  const [end, setEnd] = useState(base?.end || "23:00");
  const [picked, setPicked] = useState(() => base?.memberIds ?? []);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  if (!data) {
    return null;
  }
  const active = Boolean(data.shift);
  const draft = data.draftShift;
  const staff = data.people.filter((person) => person.status === "ACTIVE" && person.role !== "MANAGER");
  const members = staff.filter((person) => picked.some((id) => samePerson(person, id)));
  const gaps = active ? [] : shiftPlanGaps({ members, tables: data.allTables });

  function toggle(person) {
    setPicked((current) =>
      current.some((id) => samePerson(person, id))
        ? current.filter((id) => !samePerson(person, id))
        : [...current, person.id],
    );
  }

  async function save(startNow) {
    const message = draft ? null : firstError(clockTime(start, "начало"), clockTime(end, "конец"));
    if (message) {
      setError(message);
      return;
    }
    if (gaps.length) {
      setError("Состав не сохранён: сначала закройте пункты из списка «Что осталось».");
      return;
    }
    setBusy(true);
    try {
      await api.saveShiftPlan({
        shiftId: draft?.id ?? null,
        start,
        end,
        memberIds: members.map((person) => person.id),
        startNow,
      });
      setError(null);
      navigation.goBack();
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen
      scroll
      footer={
        active ? null : (
          <>
            <Button label={busy ? "Сохраняем" : "Начать смену"} disabled={busy} onPress={() => save(true)} />
            <Button tone="ghost" label="Сохранить состав" disabled={busy} onPress={() => save(false)} />
          </>
        )
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>{active ? "Кто на смене" : draft ? "Черновик смены" : "Открыть смену"}</Title>
      <Problems items={data.problems} />
      {active ? (
        <>
          <Lead>
            {data.shift.start}-{data.shift.end}
          </Lead>
          <Lead>После старта состав и столы не меняются. Чтобы поменять людей, закройте смену и откройте новую.</Lead>
        </>
      ) : (
        <Lead>
          В смену берут только тех, кто принял приглашение. Каждый стол закрепите за одним официантом и одним раннером.
          «Сохранить состав» оставляет смену черновиком, «Начать смену» сохраняет и запускает.
        </Lead>
      )}
      {!active && !draft ? (
        <>
          <Field label="Начало" value={start} onChangeText={setStart} placeholder="12:00" />
          <Field label="Конец" value={end} onChangeText={setEnd} placeholder="23:00" />
        </>
      ) : null}
      {draft ? (
        <Lead>
          {draft.start}-{draft.end}
        </Lead>
      ) : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {gaps.length ? (
        <Panel>
          <Text style={styles.name}>Что осталось</Text>
          {gaps.map((gap) => (
            <Text key={gap} style={styles.meta}>
              {gap}
            </Text>
          ))}
        </Panel>
      ) : null}
      {(active ? staff.filter((person) => isOnShift(data, person)) : staff).map((person) => {
        const on = active || picked.some((id) => samePerson(person, id));
        const runner = shiftLane(person) === "runner";
        const holderOf = (table) => (runner ? plannedRunnerId(table) : plannedWaiterId(table));
        return (
          <Panel key={person.id} style={on ? styles.cardOn : null}>
            <Pressable
              accessibilityRole="checkbox"
              accessibilityState={{ checked: on, disabled: active }}
              onPress={active ? undefined : () => toggle(person)}
            >
              <Text style={styles.name}>{person.fullName}</Text>
              <Text style={styles.meta}>{roleLabel(runner ? "RUNNER" : person.role)}</Text>
            </Pressable>
            {on ? (
              <View style={styles.wrap}>
                {data.allTables
                  .filter((table) => !active || samePerson(person, holderOf(table)))
                  .map((table) => {
                    const holder = holderOf(table);
                    const mine = samePerson(person, holder);
                    const other = !mine && holder != null ? holderName(data.people, holder) : null;
                    return (
                      <Choice
                        key={table.id}
                        label={other ? `Стол ${table.number} · ${other}` : `Стол ${table.number}`}
                        selected={mine}
                        onPress={() => {
                          if (active) {
                            return;
                          }
                          api.assignTable({ actorId: data.me.id, tableId: table.id, assigneeId: person.id }).catch((caught) => {
                            setError(caught.message);
                          });
                        }}
                      />
                    );
                  })}
              </View>
            ) : null}
          </Panel>
        );
      })}
      {!staff.length ? <Lead>Пока нет сотрудников, которые приняли приглашение.</Lead> : null}
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
          onPress={async () => {
            try {
              const result = await api.closeShift({
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

export function ShiftReportScreen({ route }) {
  const { signOut } = useSession();
  const report = route.params?.report;
  return (
    <Screen scroll footer={<Button tone="danger" label="Выйти" onPress={() => void signOut()} />}>
      <Title>Отчёт смены</Title>
      {report ? (
        <>
          {report.actualStartedAt ? (
            <Lead>
              {formatDay(report.actualStartedAt)} {formatClock(report.actualStartedAt)}
              {report.actualClosedAt ? `-${formatClock(report.actualClosedAt)}` : ""}
            </Lead>
          ) : null}
          <Text style={styles.name}>Всего задач: {report.totalTasks ?? 0}</Text>
          <Text style={styles.name}>Выполнено: {report.completedTasks ?? 0}</Text>
          <Text style={styles.name}>Перенесено: {report.carriedOverTasks ?? 0}</Text>
          <Text style={styles.name}>Отменено: {report.cancelledTasks ?? 0}</Text>
        </>
      ) : (
        <Lead>Сервер не прислал отчёт. Смена закрыта, цифры появятся в отчёте смены на сервере.</Lead>
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
});
