import { Star } from "phosphor-react-native";
import { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { taskStatusLabel } from "../domain/catalog";
import { formatClock } from "../domain/format";
import { api, userName } from "../api/store";
import { canMoveTask } from "../domain/engine";
import { Choice, Panel, TaskRow } from "../components/blocks";
import { colors, radius, shadow, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

export function TasksScreen({ navigation }) {
  const data = useRestaurant();
  const [filter, setFilter] = useState("open");
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
      <View style={styles.filters}>
        <Choice label="В работе" selected={filter === "open"} onPress={() => setFilter("open")} />
        <Choice label="Закрытые" selected={filter === "done"} onPress={() => setFilter("done")} />
      </View>
      {manager ? (
        <View style={styles.filters}>
          <Button label="Новая задача" onPress={() => navigation.navigate("TaskForm")} />
          <Button tone="ghost" label="Шаблоны" onPress={() => navigation.navigate("Favorites")} />
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

  function move(status) {
    try {
      api.moveTask({ actorId: actor.id, taskId: task.id, status });
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
      <Text style={styles.meta}>Автор: {userName(task.creatorId)}</Text>
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
  const [saveFavorite, setSaveFavorite] = useState(false);
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
          onPress={() => {
            try {
              api.createTask({
                actorId: data.me.id,
                title,
                description,
                assigneeId,
                tableId: route.params?.tableId ?? null,
                priority,
                saveFavorite,
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
      <Field label="Что сделать" value={title} onChangeText={setTitle} capitalize="sentences" />
      <Field label="Описание" value={description} onChangeText={setDescription} capitalize="sentences" multiline />
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
      <Pressable
        accessibilityRole="checkbox"
        accessibilityState={{ checked: saveFavorite }}
        accessibilityLabel="Сохранить как шаблон"
        hitSlop={8}
        onPress={() => setSaveFavorite((value) => !value)}
        style={styles.fav}
      >
        <View accessibilityElementsHidden importantForAccessibility="no">
          <Star color={colors.ink} size={22} weight={saveFavorite ? "fill" : "regular"} />
        </View>
        <Text style={styles.meta}>{saveFavorite ? "Будет в шаблонах" : "Сохранить как шаблон"}</Text>
      </Pressable>
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </Screen>
  );
}

export function FavoritesScreen({ navigation }) {
  const data = useRestaurant();
  const [assigneeId, setAssigneeId] = useState(null);
  const [error, setError] = useState(null);
  if (!data) {
    return null;
  }
  const staff = data.people.filter((person) => person.status === "ACTIVE");

  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Избранное</Title>
      <Lead>Избранное создаёт задачу на сегодняшнюю смену.</Lead>
      <View style={styles.wrap}>
        {staff.map((person) => (
          <Choice key={person.id} label={person.fullName} selected={assigneeId === person.id} onPress={() => setAssigneeId(person.id)} />
        ))}
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {data.favorites.map((item) => (
        <Panel key={item.id}>
          <Text style={styles.cardTitle}>{item.title}</Text>
          <Button
            label="Создать задачу"
            onPress={() => {
              try {
                api.createTaskFromFavorite({ actorId: data.me.id, favoriteId: item.id, assigneeId });
                navigation.goBack();
              } catch (caught) {
                setError(caught.message);
              }
            }}
          />
          <Button
            tone="ghost"
            label="Убрать шаблон"
            onPress={() => {
              try {
                api.deleteFavorite({ actorId: data.me.id, favoriteId: item.id });
              } catch (caught) {
                setError(caught.message);
              }
            }}
          />
        </Panel>
      ))}
      {!data.favorites.length ? <Lead>Шаблонов пока нет. Отметьте задачу при создании.</Lead> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  filters: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  section: { fontFamily: font.bold, fontSize: 16, color: colors.ink },
  meta: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.mute },
  error: { fontFamily: font.regular, fontSize: 14, color: colors.danger },
  fav: {
    minHeight: 48,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    paddingHorizontal: 12,
    boxShadow: shadow.tight,
  },
  cardTitle: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
});
