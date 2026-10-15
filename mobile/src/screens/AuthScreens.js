import { useState } from "react";
import { Share, StyleSheet, Text, View } from "react-native";
import QRCode from "react-native-qrcode-svg";
import { STAFF_ROLES } from "../domain/catalog";
import { api } from "../api/store";
import { Avatar, Choice, Panel } from "../components/blocks";
import { useSession } from "../session";
import { colors, radius, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import { BackLink, Button, Field, Lead, Screen, Step, Title } from "../ui";

export function RegisterScreen({ navigation }) {
  const { adopt } = useSession();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [error, setError] = useState(null);

  return (
    <Screen
      scroll
      footer={
        <Button
          label="Продолжить"
          onPress={() => {
            if (password !== repeat) {
              setError("Пароли не совпадают.");
              return;
            }
            try {
              const result = api.registerManager({ firstName, lastName, email, password });
              void adopt(result.user);
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Step>1 из 5</Step>
      <Title>Создание ресторана</Title>
      <Lead>Имя, фамилия, почта и пароль от 8 символов.</Lead>
      <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" />
      <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" />
      <Field label="Почта" value={email} onChangeText={setEmail} keyboard="email-address" />
      <Field label="Пароль" value={password} onChangeText={setPassword} secure />
      <Field label="Повтор пароля" value={repeat} onChangeText={setRepeat} secure error={error} />
    </Screen>
  );
}

export function ConfirmEmailScreen() {
  const { user } = useSession();
  const data = useRestaurant();
  const [code, setCode] = useState("");
  const [error, setError] = useState(null);
  return (
    <Screen
      footer={
        <Button
          label="Подтвердить"
          onPress={() => {
            try {
              api.confirmEmail(user.id, code);
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <Step>Почта</Step>
      <Title>Подтверждение</Title>
      <Lead>Письмо никуда не уходит. Токен для этого прототипа: {data?.confirmCode}</Lead>
      <Field label="Токен" value={code} onChangeText={setCode} error={error} />
    </Screen>
  );
}

export function PaymentScreen() {
  const { user } = useSession();
  return (
    <Screen footer={<Button label="Пропустить оплату" onPress={() => api.confirmPayment(user.id)} />}>
      <Title>Подписка</Title>
      <Panel>
        <Text style={styles.name}>Смена REST</Text>
        <Text style={styles.meta}>1 ресторан, безлимит сотрудников</Text>
        <Text style={styles.soon}>скоро</Text>
      </Panel>
      <Lead>Оплата в этом прототипе не списывается.</Lead>
    </Screen>
  );
}

export function VenueScreen() {
  const { user } = useSession();
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [hours, setHours] = useState("12:00-23:00");
  const [error, setError] = useState(null);
  return (
    <Screen
      scroll
      footer={
        <Button
          label="Дальше"
          onPress={() => {
            try {
              api.saveVenue(user.id, { name, address, about: "", hours });
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <Step>2 из 5</Step>
      <Title>Профиль заведения</Title>
      <Field label="Название" value={name} onChangeText={setName} capitalize="words" />
      <Field label="Адрес" value={address} onChangeText={setAddress} capitalize="sentences" />
      <Field label="Часы работы" value={hours} onChangeText={setHours} error={error} />
    </Screen>
  );
}

export function FirstHallScreen() {
  const { user } = useSession();
  const [name, setName] = useState("Основной");
  const [tableCount, setTableCount] = useState("6");
  const [seats, setSeats] = useState("4");
  const [error, setError] = useState(null);
  const preview = Math.min(12, Math.max(0, Number(tableCount) || 0));
  return (
    <Screen
      scroll
      footer={
        <Button
          label="Сохранить зал"
          onPress={() => {
            try {
              api.saveFirstHall(user.id, { name, seats, tableCount });
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <Step>3 из 5</Step>
      <Title>Первый зал</Title>
      <Field label="Название зала" value={name} onChangeText={setName} capitalize="sentences" />
      <Field label="Столов" value={tableCount} onChangeText={setTableCount} keyboard="number-pad" />
      <Field label="Мест за столом" value={seats} onChangeText={setSeats} keyboard="number-pad" error={error} />
      {preview > 0 ? (
        <View style={styles.wrap}>
          {Array.from({ length: preview }, (_, index) => (
            <View key={index} style={styles.preview}>
              <Text style={styles.chipText}>{index + 1}</Text>
              <Text style={styles.meta}>{seats || "0"} мест</Text>
            </View>
          ))}
        </View>
      ) : null}
    </Screen>
  );
}

export function StaffSetupScreen() {
  const { user } = useSession();
  const data = useRestaurant();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [position, setPosition] = useState("WAITER");
  const [error, setError] = useState(null);
  const invited = (data?.people ?? []).filter((person) => person.status === "INVITED");
  return (
    <Screen
      scroll
      footer={<Button label="Дальше" onPress={() => api.finishStaffStep(user.id)} />}
    >
      <Step>4 из 5</Step>
      <Title>Сотрудники</Title>
      <Lead>Должность задаёт права. Имя и фамилию сотрудник подтвердит ссылкой приглашения.</Lead>
      <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" />
      <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" />
      <View style={styles.wrap}>
        {STAFF_ROLES.map((item) => (
          <Choice key={item.id} label={item.label} selected={position === item.id} onPress={() => setPosition(item.id)} />
        ))}
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <Button
        tone="ghost"
        label="Добавить"
        onPress={() => {
          try {
            api.createStaff({ actorId: user.id, firstName, lastName, position });
            setFirstName("");
            setLastName("");
            setError(null);
          } catch (caught) {
            setError(caught.message);
          }
        }}
      />
      {invited.map((person) => (
        <View key={person.id} style={styles.personRow}>
          <Avatar name={person.fullName} />
          <View style={styles.flex}>
            <Text style={styles.name}>{person.fullName}</Text>
            <Text style={styles.meta}>{person.position === "RUNNER" || person.role === "RUNNER" ? "Раннер" : "Официант"}</Text>
          </View>
        </View>
      ))}
    </Screen>
  );
}

export function InviteScreen({ navigation, route }) {
  const setup = Boolean(route?.params?.setup);
  const { user } = useSession();
  const data = useRestaurant();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [position, setPosition] = useState("WAITER");
  const [error, setError] = useState(null);
  const invited = (data?.people ?? []).filter((person) => person.invitationToken);
  return (
    <Screen
      scroll
      footer={
        setup ? <Button label="Готово" onPress={() => api.finishSetup(user.id)} /> : null
      }
    >
      {!setup && navigation?.canGoBack() ? <BackLink onPress={() => navigation.goBack()} /> : null}
      {setup ? <Step>5 из 5</Step> : null}
      <Title>Приглашение</Title>
      <Lead>Ссылка и QR создаются сразу. Сотрудник задаёт логин и пароль по этой ссылке.</Lead>
      {!setup ? (
        <>
          <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" />
          <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" />
          <View style={styles.wrap}>
            {STAFF_ROLES.map((item) => (
              <Choice key={item.id} label={item.label} selected={position === item.id} onPress={() => setPosition(item.id)} />
            ))}
          </View>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button
            tone="ghost"
            label="Добавить"
            onPress={() => {
              try {
                api.createStaff({ actorId: user.id, firstName, lastName, position });
                setFirstName("");
                setLastName("");
                setError(null);
              } catch (caught) {
                setError(caught.message);
              }
            }}
          />
        </>
      ) : null}
      {invited.map((person) => (
        <Panel key={person.id}>
          <Text style={styles.name}>{person.fullName}</Text>
          <Text style={styles.code}>{person.invitationUrl}</Text>
          <Button
            tone="ghost"
            label="Поделиться ссылкой"
            onPress={() => {
              void Share.share({ message: person.invitationUrl });
            }}
          />
          <Text style={styles.meta}>{person.inviteUses ? "Приглашение принято" : "Ожидает сотрудника"}</Text>
          <View style={styles.qr}>
            <QRCode value={person.qrContent} size={140} color={colors.ink} backgroundColor={colors.paper} />
          </View>
        </Panel>
      ))}
      {!invited.length ? <Lead>Сначала добавьте сотрудника. Код появится здесь.</Lead> : null}
    </Screen>
  );
}

export function JoinScreen({ navigation }) {
  const { adopt } = useSession();
  const [step, setStep] = useState("token");
  const [token, setToken] = useState("");
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);

  return (
    <Screen
      scroll
      footer={
        step === "token" ? (
          <Button label="Дальше" onPress={() => setStep("account")} />
        ) : (
          <Button
            label="Войти в зал"
            onPress={() => {
              try {
                const next = api.redeemInvite({ token, login, password });
                void adopt(next);
              } catch (caught) {
                setError(caught.message);
              }
            }}
          />
        )
      }
    >
      <BackLink onPress={() => (step === "account" ? setStep("token") : navigation.goBack())} />
      <Title>Приглашение в команду</Title>
      {step === "token" ? (
        <>
          <Lead>Вставьте токен из ссылки. Регистр важен.</Lead>
          <Field label="Токен приглашения" value={token} onChangeText={setToken} error={error} />
        </>
      ) : (
        <>
          <Lead>Логин задаётся один раз, пароль от 8 символов.</Lead>
          <Field label="Логин" value={login} onChangeText={setLogin} />
          <Field label="Пароль" value={password} onChangeText={setPassword} secure error={error} />
        </>
      )}
    </Screen>
  );
}

export function ResetPasswordScreen({ navigation, route }) {
  const [login, setLogin] = useState(route.params?.email ?? "");
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [asked, setAsked] = useState(false);
  const [issued, setIssued] = useState(null);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);
  return (
    <Screen
      scroll
      footer={
        <Button
          label="Задать пароль"
          onPress={() => {
            try {
              api.resetPassword({ token, newPassword: password });
              setDone(true);
              setError(null);
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Title>Новый пароль</Title>
      <Lead>Письмо с устройства не уходит. Сначала запросите токен, затем задайте пароль от 8 символов.</Lead>
      <Field label="Почта или логин" value={login} onChangeText={setLogin} />
      <Button
        tone="ghost"
        label="Получить токен"
        onPress={() => {
          try {
            const result = api.forgotPassword({ login });
            setAsked(true);
            setIssued(result.token);
            if (result.token) {
              setToken(result.token);
            }
            setError(null);
          } catch (caught) {
            setError(caught.message);
          }
        }}
      />
      {asked && issued ? <Lead>Токен восстановления: {issued}</Lead> : null}
      {asked && !issued ? <Lead>Для этой учётки токен не выдан.</Lead> : null}
      <Field label="Токен" value={token} onChangeText={setToken} />
      <Field label="Новый пароль" value={password} onChangeText={setPassword} secure error={error} />
      {done ? <Lead>Пароль обновлён. Можно входить.</Lead> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  chipText: { fontFamily: font.bold, color: colors.ink },
  error: { fontFamily: font.regular, color: colors.danger, fontSize: 14 },
  meta: { fontFamily: font.regular, fontSize: 14, color: colors.ink },
  name: { fontFamily: font.semibold, fontSize: 16, color: colors.ink },
  code: { fontFamily: font.bold, fontSize: 16, lineHeight: 22, color: colors.ink },
  qr: { padding: 8, backgroundColor: colors.paper },
  soon: {
    alignSelf: "flex-start",
    fontFamily: font.bold,
    fontSize: 13,
    color: colors.ink,
    backgroundColor: colors.accent,
    borderWidth: 2,
    borderColor: colors.ink,
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  personRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  flex: { flex: 1 },
  preview: {
    minWidth: 72,
    minHeight: 56,
    borderRadius: radius.row,
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
    padding: 6,
  },
});
