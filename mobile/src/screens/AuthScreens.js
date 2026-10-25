import { useState } from "react";
import { Share, StyleSheet, Text, View } from "react-native";
import QRCode from "react-native-qrcode-svg";
import { STAFF_ROLES } from "../domain/catalog";
import { api } from "../api/store";
import { Avatar, Choice, Panel } from "../components/blocks";
import { useSession } from "../session";
import { colors, type as font } from "../theme";
import { useRestaurant } from "../useRestaurant";
import {
  emailLogin,
  firstError,
  hallName,
  hoursRange,
  inviteToken,
  loginValue,
  passwordMatch,
  passwordValue,
  personName,
  seatCount,
  tableCount,
  venueAddress,
  venueName,
} from "../validation";
import { BackLink, Button, Field, Lead, Screen, Step, Title } from "../ui";

export function RegisterScreen({ navigation }) {
  const { adopt } = useSession();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [hours, setHours] = useState("12:00-23:00");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  return (
    <Screen
      scroll
      footer={
        <Button
          label={busy ? "Создаём" : "Продолжить"}
          disabled={busy}
          onPress={async () => {
            const message = firstError(
              personName(firstName, "имя"),
              personName(lastName, "фамилию"),
              emailLogin(email),
              passwordValue(password),
              passwordMatch(password, repeat),
              venueName(name),
              venueAddress(address),
              hoursRange(hours),
            );
            if (message) {
              setError(message);
              return;
            }
            setBusy(true);
            try {
              const result = await api.registerManager({
                firstName,
                lastName,
                email,
                password,
                name,
                address,
                hours,
              });
              await adopt(result.user ?? result);
            } catch (caught) {
              setError(caught.message);
            } finally {
              setBusy(false);
            }
          }}
        />
      }
    >
      <BackLink onPress={() => navigation.goBack()} />
      <Step>1 из 2</Step>
      <Title>Создание ресторана</Title>
      <Lead>Аккаунт директора и данные заведения. Пароль от 8 символов.</Lead>
      <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" maxLength={100} />
      <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" maxLength={100} />
      <Field label="Почта" value={email} onChangeText={setEmail} keyboard="email-address" maxLength={100} />
      <Field label="Пароль" value={password} onChangeText={setPassword} secure maxLength={72} />
      <Field label="Повтор пароля" value={repeat} onChangeText={setRepeat} secure maxLength={72} />
      <Field label="Название ресторана" value={name} onChangeText={setName} capitalize="words" maxLength={200} />
      <Field label="Адрес" value={address} onChangeText={setAddress} capitalize="sentences" maxLength={500} />
      <Field label="Часы работы" value={hours} onChangeText={setHours} error={error} />
    </Screen>
  );
}

export function ConfirmEmailScreen() {
  const { user } = useSession();
  const [code, setCode] = useState("");
  const [error, setError] = useState(null);
  return (
    <Screen
      footer={
        <Button
          label="Подтвердить"
          onPress={async () => {
            try {
              await api.confirmEmail(user.id, code);
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <Step>Почта</Step>
      <Title>Подтверждение</Title>
      <Lead>Подтверждение почты выполняется на сервере.</Lead>
      <Field label="Токен" value={code} onChangeText={setCode} error={error} />
    </Screen>
  );
}

export function PaymentScreen() {
  const { user } = useSession();
  return (
    <Screen footer={<Button label="Пропустить оплату" onPress={() => void api.confirmPayment(user.id)} />}>
      <Title>Подписка</Title>
      <Panel>
        <Text style={styles.name}>Смена REST</Text>
        <Text style={styles.meta}>1 ресторан, безлимит сотрудников</Text>
        <Text style={styles.soon}>скоро</Text>
      </Panel>
      <Lead>Локально подписка активируется без оплаты.</Lead>
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
          onPress={async () => {
            const message = firstError(venueName(name), venueAddress(address), hoursRange(hours));
            if (message) {
              setError(message);
              return;
            }
            try {
              await api.saveVenue(user.id, { name, address, about: "", hours });
            } catch (caught) {
              setError(caught.message);
            }
          }}
        />
      }
    >
      <Step>Заведение</Step>
      <Title>Профиль заведения</Title>
      <Field label="Название" value={name} onChangeText={setName} capitalize="words" maxLength={200} />
      <Field label="Адрес" value={address} onChangeText={setAddress} capitalize="sentences" maxLength={500} />
      <Field label="Часы работы" value={hours} onChangeText={setHours} error={error} />
    </Screen>
  );
}

function resizeSeatList(current, rawCount) {
  const count = Math.min(12, Math.max(0, Number.parseInt(String(rawCount).trim(), 10) || 0));
  const next = current.slice(0, count);
  const fallback = current[current.length - 1] || "4";
  while (next.length < count) {
    next.push(fallback);
  }
  return next;
}

export function FirstHallScreen() {
  const { user } = useSession();
  const [name, setName] = useState("Основной");
  const [count, setCount] = useState("6");
  const [seats, setSeats] = useState(() => Array.from({ length: 6 }, () => "4"));
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  return (
    <Screen
      scroll
      footer={
        <Button
          label={busy ? "Сохраняем" : "Сохранить зал"}
          disabled={busy}
          onPress={async () => {
            const seatErrors = seats.map((value, index) => seatCount(value, `мест у стола ${index + 1}`));
            const message = firstError(hallName(name), tableCount(count), ...seatErrors);
            if (message) {
              setError(message);
              return;
            }
            setBusy(true);
            try {
              await api.saveFirstHall(user.id, {
                name,
                tables: seats.map((value, index) => ({ number: index + 1, seats: Number(value) })),
              });
            } catch (caught) {
              setError(caught.message);
            } finally {
              setBusy(false);
            }
          }}
        />
      }
    >
      <Step>2 из 2</Step>
      <Title>Первый зал</Title>
      <Lead>У каждого стола своё число мест, от 1 до 100.</Lead>
      <Field label="Название зала" value={name} onChangeText={setName} capitalize="sentences" maxLength={100} />
      <Field
        label="Столов"
        value={count}
        onChangeText={(value) => {
          setCount(value);
          setSeats((current) => resizeSeatList(current, value));
        }}
        keyboard="number-pad"
      />
      {seats.map((value, index) => (
        <Field
          key={index}
          label={`Стол ${index + 1}, мест`}
          value={value}
          onChangeText={(next) => {
            setSeats((current) => current.map((seatsValue, seatIndex) => (seatIndex === index ? next : seatsValue)));
          }}
          keyboard="number-pad"
          error={index === seats.length - 1 ? error : null}
        />
      ))}
      {!seats.length ? <Lead>Укажите число столов от 1 до 12.</Lead> : null}
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
      footer={<Button label="Дальше" onPress={() => void api.finishStaffStep(user.id)} />}
    >
      <Step>4 из 5</Step>
      <Title>Сотрудники</Title>
      <Lead>Должность задаёт права. Имя и фамилию сотрудник подтвердит ссылкой приглашения.</Lead>
      <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" maxLength={100} />
      <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" maxLength={100} />
      <View style={styles.wrap}>
        {STAFF_ROLES.map((item) => (
          <Choice key={item.id} label={item.label} selected={position === item.id} onPress={() => setPosition(item.id)} />
        ))}
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <Button
        tone="ghost"
        label="Добавить"
        onPress={async () => {
          const message = firstError(personName(firstName, "имя"), personName(lastName, "фамилию"));
          if (message) {
            setError(message);
            return;
          }
          try {
            await api.createStaff({ actorId: user.id, firstName, lastName, position });
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
        setup ? <Button label="Готово" onPress={() => void api.finishSetup(user.id)} /> : null
      }
    >
      {!setup && navigation?.canGoBack() ? <BackLink onPress={() => navigation.goBack()} /> : null}
      {setup ? <Step>5 из 5</Step> : null}
      <Title>Приглашение</Title>
      <Lead>Ссылка и QR создаются сразу. Сотрудник задаёт логин и пароль по этой ссылке.</Lead>
      {!setup ? (
        <>
          <Field label="Имя" value={firstName} onChangeText={setFirstName} capitalize="words" maxLength={100} />
          <Field label="Фамилия" value={lastName} onChangeText={setLastName} capitalize="words" maxLength={100} />
          <View style={styles.wrap}>
            {STAFF_ROLES.map((item) => (
              <Choice key={item.id} label={item.label} selected={position === item.id} onPress={() => setPosition(item.id)} />
            ))}
          </View>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button
            tone="ghost"
            label="Добавить"
            onPress={async () => {
              const message = firstError(personName(firstName, "имя"), personName(lastName, "фамилию"));
              if (message) {
                setError(message);
                return;
              }
              try {
                await api.createStaff({ actorId: user.id, firstName, lastName, position });
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
          <Button
            label="Дальше"
            onPress={() => {
              const message = inviteToken(token);
              if (message) {
                setError(message);
                return;
              }
              setError(null);
              setStep("account");
            }}
          />
        ) : (
          <Button
            label="Войти в зал"
            onPress={async () => {
              const message = firstError(loginValue(login), passwordValue(password));
              if (message) {
                setError(message);
                return;
              }
              try {
                const next = await api.redeemInvite({ token, login, password });
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
          <Field label="Токен приглашения" value={token} onChangeText={setToken} error={error} maxLength={200} />
        </>
      ) : (
        <>
          <Lead>Логин задаётся один раз, пароль от 8 символов.</Lead>
          <Field label="Логин" value={login} onChangeText={setLogin} maxLength={100} />
          <Field label="Пароль" value={password} onChangeText={setPassword} secure error={error} maxLength={72} />
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
          onPress={async () => {
            const message = firstError(inviteToken(token), passwordValue(password, "новый пароль"));
            if (message) {
              setError(message);
              return;
            }
            try {
              await api.resetPassword({ token, newPassword: password });
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
      <Lead>Сброс пароля выполняется только через сервер.</Lead>
      <Field label="Почта или логин" value={login} onChangeText={setLogin} />
      <Button
        tone="ghost"
        label="Получить токен"
        onPress={async () => {
          const message = loginValue(login, "почту или логин");
          if (message) {
            setError(message);
            return;
          }
          try {
            const result = await api.forgotPassword({ login });
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
      <Field label="Токен" value={token} onChangeText={setToken} maxLength={200} />
      <Field label="Новый пароль" value={password} onChangeText={setPassword} secure error={error} maxLength={72} />
      {done ? <Lead>Пароль обновлён. Можно входить.</Lead> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
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
});
