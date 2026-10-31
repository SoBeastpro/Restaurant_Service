import { useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { TABLE_STATUSES, roleLabel } from "../domain/catalog";
import { formatClock, formatDay } from "../domain/format";
import { api } from "../api/store";
import { useSession } from "../session";
import { Avatar, Choice, Panel } from "../components/blocks";
import { colors, radius, shadow, space, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { firstError, passwordValue } from "../validation";
import { BackLink, Button, Field, Lead, Screen, Title } from "../ui";

export function MoreScreen({ navigation }) {
  const { signOut } = useSession();
  const data = useRestaurant();
  if (!data) {
    return null;
  }
  const manager = data.me.role === "MANAGER";
  const unread = data.notifications.filter((item) => !item.read).length;

  return (
    <Screen
      scroll
      footer={
        <>
          {data.shift && manager ? (
            <Button label="Завершить смену" onPress={() => navigation.navigate("ShiftClose")} />
          ) : manager ? (
            <Button label="Открыть смену" onPress={() => navigation.navigate("ShiftOpen")} />
          ) : null}
          <Button tone="danger" label="Выйти" onPress={() => void signOut()} />
        </>
      }
    >
      <Title>{manager ? "Ещё" : "Профиль"}</Title>
      <Panel style={styles.person}>
        <Avatar name={data.me.fullName} size={56} />
        <View style={styles.flex}>
          <Text style={styles.name}>{data.me.fullName}</Text>
          <Text style={styles.meta}>
            {roleLabel(data.me.role)}, {data.me.venue}
          </Text>
        </View>
      </Panel>
      <Text style={styles.meta}>{data.me.email || `Логин ${data.me.username}`}</Text>
      {data.shift && !manager ? (
        <Text style={styles.meta}>Смену закрывает управляющий. Самостоятельный выход из смены: на сервере ещё нет.</Text>
      ) : null}
      <MenuRow label="Уведомления" body={unread ? `Новых: ${unread}` : "Всё прочитано"} onPress={() => navigation.navigate("Notifications")} />
      {manager ? <MenuRow label="Команда" body="Сотрудники, роли и приглашения" onPress={() => navigation.navigate("Team")} /> : null}
      {manager ? <MenuRow label="Статистика" body="День, неделя, месяц" onPress={() => navigation.navigate("Stats")} /> : null}
      <MenuRow label="Сменить пароль" body="Текущий и новый, от 8 символов" onPress={() => navigation.navigate("Password")} />
    </Screen>
  );
}

function MenuRow({ label, body, onPress }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={({ pressed }) => [styles.row, pressed ? styles.pressed : null]}>
      <Text style={styles.name}>{label}</Text>
      <Text style={styles.meta}>{body}</Text>
    </Pressable>
  );
}

export function NotificationsScreen({ navigation }) {
  const data = useRestaurant();
  if (!data) {
    return null;
  }
  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Уведомления</Title>
      {data.notifications.some((item) => !item.read) ? (
        <Button tone="ghost" label="Прочитать все" onPress={() => void api.markAllNotificationsRead(data.me.id)} />
      ) : null}
      {data.notifications.map((item) => (
        <Pressable
          key={item.id}
          accessibilityRole="button"
          onPress={() => void api.markNotificationRead({ userId: data.me.id, notificationId: item.id })}
          style={styles.row}
        >
          <Text style={styles.name}>{item.title}</Text>
          <Text style={styles.meta}>{item.body}</Text>
          <Text style={styles.meta}>
            {formatDay(item.createdAt)} {formatClock(item.createdAt)}
            {item.read ? "" : ", новое"}
          </Text>
        </Pressable>
      ))}
      {!data.notifications.length ? <Lead>Пока тихо. Здесь появятся задачи, столы и смена.</Lead> : null}
    </Screen>
  );
}

export function StatsScreen({ navigation }) {
  const data = useRestaurant();
  const [period, setPeriod] = useState("day");
  if (!data) {
    return null;
  }
  const stats = data.stats(period);
  const periods = [
    ["day", "Сегодня"],
    ["week", "Неделя"],
    ["month", "Месяц"],
  ];
  return (
    <Screen scroll>
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Статистика</Title>
      <View style={styles.periods}>
        {periods.map(([id, label]) => (
          <Choice key={id} label={label} selected={period === id} onPress={() => setPeriod(id)} />
        ))}
      </View>
      <Stat label="Задач создано" value={stats.created} />
      <Stat label="Выполнено" value={stats.done} />
      <Stat label="В процессе" value={stats.inProgress} />
      <Text style={styles.section}>Столы сейчас</Text>
      {TABLE_STATUSES.map((status) => (
        <Stat key={status.id} label={status.label} value={stats.current[status.id] ?? 0} />
      ))}
    </Screen>
  );
}

function Stat({ label, value }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.meta}>{label}</Text>
      <Text style={styles.statValue}>{value}</Text>
    </View>
  );
}

export function PasswordScreen({ navigation }) {
  const { signOut } = useSession();
  const data = useRestaurant();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  if (!data) {
    return null;
  }
  return (
    <Screen
      scroll
      footer={
        <Button
          label={busy ? "Сохраняем" : "Сохранить"}
          disabled={busy}
          onPress={async () => {
            const message = firstError(
              current ? null : "Введите текущий пароль.",
              passwordValue(password, "новый пароль"),
            );
            if (message) {
              setError(message);
              return;
            }
            setBusy(true);
            try {
              await api.changePassword({ userId: data.me.id, currentPassword: current, newPassword: password });
            } catch (caught) {
              setError(caught.message);
              setBusy(false);
              return;
            }
            Alert.alert("Пароль обновлён", "Сервер завершил все сессии. Войдите с новым паролем.");
            void signOut();
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Новый пароль</Title>
      <Lead>После смены пароля сервер завершит все сессии — придётся войти заново.</Lead>
      <Field label="Текущий пароль" value={current} onChangeText={setCurrent} secure />
      <Field label="Новый пароль" value={password} onChangeText={setPassword} secure error={error} maxLength={72} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  person: {
    flexDirection: "row",
    alignItems: "center",
    gap: space.sm,
  },
  flex: { flex: 1 },
  name: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
  meta: { fontFamily: font.regular, fontSize: 14, lineHeight: 20, color: colors.mute },
  row: {
    backgroundColor: colors.white,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
    padding: 14,
    gap: 4,
    minHeight: 64,
    justifyContent: "center",
  },
  pressed: { transform: [{ translateX: 1 }, { translateY: 1 }] },
  periods: { flexDirection: "row", gap: 8 },
  stat: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    minHeight: 48,
    paddingHorizontal: 4,
  },
  statValue: { fontFamily: font.bold, fontSize: 18, color: colors.ink },
  section: { fontFamily: font.bold, fontSize: 18, color: colors.ink, marginTop: space.sm },
});
