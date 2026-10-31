import { useCallback, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { taskStatusLabel } from "../domain/catalog";
import { formatClock } from "../domain/format";
import { api, userName } from "../api/store";
import { canMoveTask } from "../domain/engine";
import { Choice, Problems, TaskRow } from "../components/blocks";
import { colors, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { firstError, taskTitle } from "../validation";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

export function TasksScreen({ navigation }) {
  const data = useRestaurant();
  const [filter, setFilter] = useState("open");
  useFocusEffect(
    useCallback(() => {
      void api.refresh();
    }, []),
  );
  if (!data) {
    return null;
  }
  const manager = data.me.role === "MANAGER";
  const rows = data.tasks.filter((task) => {
    if (filter === "done") {
      return task.status === "COMPLETED" || task.status === "CANCELLED";
    }
    return task.status !== "COMPLETED" && task.status !== "CANCELLED";
  });

  return (
    <Screen scroll>
      <Title>Задачи</Title>
      <Problems items={data.problems} />
      <View style={styles.filters}>
        <Choice label="В работе" selected={filter === "open"} onPress={() => setFilter("open")} />
        <Choice label="Закрытые" selected={filter === "done"} onPress={() => setFilter("done")} />
      </View>
      {manager ? (
        <View style={styles.filters}>
          <Button label="Новая задача" onPress={() => navigation.navigate("TaskForm")} />
        </View>
      ) : null}
      {rows.map((task) => (
        <TaskRow
          key={task.id}
          task={task}
          subtitle={task.assigneeId ? userName(task.assigneeId) : "без исполнителя"}
          onPress={() => navigation.navigate("Task", { taskId: task.id })}
        />
      ))}
      {!rows.length ? <Lead>{filter === "open" ? "Открытых задач нет." : "Закрытых задач пока нет."}</Lead> : null}
    </Screen>
  );
}

export function TaskDetailScreen({ route, navigation }) {
  const data = useRestaurant();
  const task = data?.allTasks.find((item) => item.id === route.params.taskId) ?? data?.tasks.find((item) => item.id === route.params.taskId);
  const [error, setError] = useState(null);
  if (!data || !task) {
    return (
      <Screen>
        <BackLink onPress={() => navigation.goBack()} />
        <Title>Задача не найдена</Title>
      </Screen>
    );
  }
  const actor = data.me;

  async function move(status) {
    try {
      await api.moveTask({ actorId: actor.id, taskId: task.id, status });
      setError(null);
    } catch (caught) {
      setError(caught.message);
    }
  }

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>{task.title}</Title>
      <Lead>{task.description || "Без описания"}</Lead>
      <Text style={styles.meta}>
        {taskStatusLabel(task.status)}
      </Text>
      {task.creatorId ? <Text style={styles.meta}>Автор: {userName(task.creatorId)}</Text> : null}
      <Text style={styles.meta}>Исполнитель: {task.assigneeId ? userName(task.assigneeId) : "не назначен"}</Text>
      {task.dueAt ? <Text style={styles.meta}>Срок: {formatClock(task.dueAt)}</Text> : null}
      {task.tableId ? <Text style={styles.meta}>Стол {data.allTables.find((item) => item.id === task.tableId)?.number}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {canMoveTask(actor, task, "IN_PROGRESS") ? <Button label="Взять в работу" onPress={() => move("IN_PROGRESS")} /> : null}
      {canMoveTask(actor, task, "COMPLETED") ? <Button label="Выполнена" onPress={() => move("COMPLETED")} /> : null}
      {canMoveTask(actor, task, "CANCELLED") ? <Button tone="danger" label="Отменить" onPress={() => move("CANCELLED")} /> : null}
    </Screen>
  );
}

export function TaskFormScreen({ route, navigation }) {
  const data = useRestaurant();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [assigneeId, setAssigneeId] = useState(null);
  const [priority, setPriority] = useState("normal");
  const [error, setError] = useState(null);
  if (!data) {
    return null;
  }
  const staff = data.people.filter((person) => person.status === "ACTIVE");

  return (
    <Screen
      scroll
      footer={
        <Button
          label="Создать"
          onPress={async () => {
            const message = firstError(
              taskTitle(title),
              assigneeId ? null : "Выберите исполнителя. Задачу можно создать только на смене.",
            );
            if (message) {
              setError(message);
              return;
            }
            try {
              await api.createTask({
                actorId: data.me.id,
                title,
                description,
                assigneeId,
                tableId: route.params?.tableId ?? null,
                priority,
              });
              navigation.goBack();
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Создание задачи</Title>
      <Field label="Что сделать" value={title} onChangeText={setTitle} capitalize="sentences" maxLength={200} />
      <Field label="Описание" value={description} onChangeText={setDescription} capitalize="sentences" multiline maxLength={200} />
      <Text style={styles.section}>Кому</Text>
      <View style={styles.wrap}>
        <Choice label="Позже" selected={assigneeId == null} onPress={() => setAssigneeId(null)} />
        {staff.map((person) => (
          <Choice key={person.id} label={person.fullName} selected={assigneeId === person.id} onPress={() => setAssigneeId(person.id)} />
        ))}
      </View>
      <Text style={styles.section}>Приоритет</Text>
      <View style={styles.wrap}>
        {[
          ["low", "низкий"],
          ["normal", "средний"],
          ["high", "высокий"],
        ].map(([id, label]) => (
          <Choice key={id} label={label} selected={priority === id} onPress={() => setPriority(id)} />
        ))}
      </View>
      <Text style={styles.meta}>Шаблоны задач: на сервере ещё нет.</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </Screen>
  );
}

export function FavoritesScreen({ navigation }) {
  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Шаблоны</Title>
      <Lead>Шаблоны задач: на сервере ещё нет.</Lead>
    </Screen>
  );
}

const styles = StyleSheet.create({
  filters: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  section: { fontFamily: font.bold, fontSize: 16, color: colors.ink },
  meta: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.mute },
  error: { fontFamily: font.regular, fontSize: 14, color: colors.danger },
});
