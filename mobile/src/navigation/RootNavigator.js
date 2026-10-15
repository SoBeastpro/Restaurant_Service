import { NavigationContainer, DefaultTheme, useIsFocused } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { CheckSquare, Clock, DotsThree, SquaresFour, User } from "phosphor-react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import {
  ConfirmEmailScreen,
  FirstHallScreen,
  InviteScreen,
  JoinScreen,
  PaymentScreen,
  RegisterScreen,
  ResetPasswordScreen,
  StaffSetupScreen,
  VenueScreen,
} from "../screens/AuthScreens";
import { HallScreen, HistoryScreen, TableEditScreen, TableScreen } from "../screens/HallScreens";
import { LoginScreen } from "../screens/LoginScreen";
import { MoreScreen, NotificationsScreen, PasswordScreen, StatsScreen } from "../screens/MoreScreens";
import { OnboardingScreen } from "../screens/OnboardingScreen";
import { RoleGateScreen } from "../screens/RoleGateScreen";
import { FavoritesScreen, TaskDetailScreen, TaskFormScreen, TasksScreen } from "../screens/TaskScreens";
import { PersonScreen, ShiftBoardScreen, ShiftCloseScreen, ShiftOpenScreen, ShiftReportScreen, TeamScreen } from "../screens/TeamScreens";
import { useSession } from "../session";
import { colors, radius, shadow, type as font } from "../theme";

const AuthStack = createNativeStackNavigator();
const SetupStack = createNativeStackNavigator();
const AppStack = createNativeStackNavigator();
const ManagerTabs = createBottomTabNavigator();
const StaffTabs = createBottomTabNavigator();

const setupName = {
  email: "Confirm",
  pay: "Pay",
  venue: "Venue",
  hall: "Hall",
  staff: "Staff",
  invite: "Invite",
};

const navTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: colors.paper,
    card: colors.white,
    text: colors.ink,
    border: colors.ink,
    primary: colors.accent,
  },
};

export function RootNavigator() {
  const session = useSession();

  if (!session.ready) {
    return <View style={styles.boot} />;
  }

  const setup = session.user?.role === "MANAGER" && session.user.setupStep && session.user.setupStep !== "done";
  const navKey = session.user ? `${session.user.id}:${session.user.setupStep || "done"}` : "logged-out";

  return (
    <NavigationContainer key={navKey} theme={navTheme}>
      {session.user ? (
        setup ? (
          <SetupNavigator step={session.user.setupStep} />
        ) : session.user.role === "MANAGER" ? (
          <ManagerShell />
        ) : (
          <StaffShell />
        )
      ) : (
        <LoggedOut seenIntro={session.seenIntro} finishIntro={session.finishIntro} />
      )}
    </NavigationContainer>
  );
}

function LoggedOut({ seenIntro, finishIntro }) {
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false, animation: "fade" }}>
      {seenIntro ? null : (
        <AuthStack.Screen name="Intro">
          {({ navigation }) => (
            <OnboardingScreen
              onDone={() => {
                void finishIntro();
                navigation.replace("Role");
              }}
            />
          )}
        </AuthStack.Screen>
      )}
      <AuthStack.Screen name="Role">
        {({ navigation }) => <RoleGateScreen onPick={(intent) => navigation.navigate("Login", { intent })} />}
      </AuthStack.Screen>
      <AuthStack.Screen name="Login" component={LoginScreen} />
      <AuthStack.Screen name="Register" component={RegisterScreen} />
      <AuthStack.Screen name="Join" component={JoinScreen} />
      <AuthStack.Screen name="Reset" component={ResetPasswordScreen} />
    </AuthStack.Navigator>
  );
}

function SetupNavigator({ step }) {
  return (
    <SetupStack.Navigator key={step} initialRouteName={setupName[step] ?? "Confirm"} screenOptions={{ headerShown: false }}>
      <SetupStack.Screen name="Confirm" component={ConfirmEmailScreen} />
      <SetupStack.Screen name="Pay" component={PaymentScreen} />
      <SetupStack.Screen name="Venue" component={VenueScreen} />
      <SetupStack.Screen name="Hall" component={FirstHallScreen} />
      <SetupStack.Screen name="Staff" component={StaffSetupScreen} />
      <SetupStack.Screen name="Invite" component={InviteScreen} initialParams={{ setup: true }} />
    </SetupStack.Navigator>
  );
}

function ManagerShell() {
  return (
    <AppStack.Navigator screenOptions={{ headerShown: false }}>
      <AppStack.Screen name="Tabs" component={ManagerTabBar} />
      {sharedScreens()}
    </AppStack.Navigator>
  );
}

function StaffShell() {
  return (
    <AppStack.Navigator screenOptions={{ headerShown: false }}>
      <AppStack.Screen name="Tabs" component={StaffTabBar} />
      {sharedScreens()}
    </AppStack.Navigator>
  );
}

function sharedScreens() {
  return (
    <>
      <AppStack.Screen name="Table" component={TableScreen} />
      <AppStack.Screen name="TableEdit" component={TableEditScreen} />
      <AppStack.Screen name="History" component={HistoryScreen} />
      <AppStack.Screen name="Task" component={TaskDetailScreen} />
      <AppStack.Screen name="TaskForm" component={TaskFormScreen} />
      <AppStack.Screen name="Favorites" component={FavoritesScreen} />
      <AppStack.Screen name="Team" component={TeamScreen} />
      <AppStack.Screen name="Person" component={PersonScreen} />
      <AppStack.Screen name="ShiftOpen" component={ShiftOpenScreen} />
      <AppStack.Screen name="ShiftClose" component={ShiftCloseScreen} />
      <AppStack.Screen name="ShiftReport" component={ShiftReportScreen} />
      <AppStack.Screen name="Invite" component={InviteScreen} />
      <AppStack.Screen name="Notifications" component={NotificationsScreen} />
      <AppStack.Screen name="Stats" component={StatsScreen} />
      <AppStack.Screen name="Password" component={PasswordScreen} />
    </>
  );
}

function withFocusGate(Component) {
  return function GatedScreen(props) {
    const focused = useIsFocused();
    return (
      <View
        style={focused ? styles.page : styles.pageHidden}
        accessibilityElementsHidden={!focused}
        importantForAccessibility={focused ? "auto" : "no-hide-descendants"}
        pointerEvents={focused ? "auto" : "none"}
      >
        <Component {...props} />
      </View>
    );
  };
}

const HallTab = withFocusGate(HallScreen);
const TasksTab = withFocusGate(TasksScreen);
const ShiftTab = withFocusGate(ShiftBoardScreen);
const MoreTab = withFocusGate(MoreScreen);

function ManagerTabBar() {
  return (
    <ManagerTabs.Navigator screenOptions={{ headerShown: false, detachInactiveScreens: true }} tabBar={(props) => <TabBar {...props} />}>
      <ManagerTabs.Screen name="Hall" component={HallTab} options={{ title: "Зал", tabBarIconName: "hall" }} />
      <ManagerTabs.Screen name="Tasks" component={TasksTab} options={{ title: "Задачи", tabBarIconName: "tasks" }} />
      <ManagerTabs.Screen name="Shift" component={ShiftTab} options={{ title: "Смена", tabBarIconName: "shift" }} />
      <ManagerTabs.Screen name="More" component={MoreTab} options={{ title: "Ещё", tabBarIconName: "more" }} />
    </ManagerTabs.Navigator>
  );
}

function StaffTabBar() {
  return (
    <StaffTabs.Navigator screenOptions={{ headerShown: false, detachInactiveScreens: true }} tabBar={(props) => <TabBar {...props} />}>
      <StaffTabs.Screen name="Tables" component={HallTab} options={{ title: "Столы", tabBarIconName: "hall" }} />
      <StaffTabs.Screen name="Tasks" component={TasksTab} options={{ title: "Задачи", tabBarIconName: "tasks" }} />
      <StaffTabs.Screen name="Profile" component={MoreTab} options={{ title: "Профиль", tabBarIconName: "profile" }} />
    </StaffTabs.Navigator>
  );
}

function TabBar({ state, descriptors, navigation }) {
  const insets = useSafeAreaInsets();
  return (
    <View testID="tab-bar" style={[styles.tabs, { paddingBottom: Math.max(insets.bottom, 8) }]}>
      {state.routes.map((route, index) => {
        const focused = state.index === index;
        const options = descriptors[route.key]?.options;
        const label = options?.title ?? route.name;
        return (
          <Pressable
            key={route.key}
            accessibilityRole="tab"
            accessibilityState={{ selected: focused }}
            accessibilityLabel={label}
            onPress={() => navigation.navigate(route.name)}
            style={[styles.tab, focused ? styles.tabOn : null]}
          >
            <TabIcon name={options?.tabBarIconName ?? "more"} focused={focused} />
            <Text style={styles.tabLabel}>{label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function TabIcon({ name, focused }) {
  const props = { color: colors.ink, size: 22, weight: focused ? "bold" : "regular" };
  if (name === "hall") return <SquaresFour {...props} />;
  if (name === "tasks") return <CheckSquare {...props} />;
  if (name === "shift") return <Clock {...props} />;
  if (name === "profile") return <User {...props} />;
  return <DotsThree {...props} />;
}

const styles = StyleSheet.create({
  boot: { flex: 1, backgroundColor: colors.paper },
  page: { flex: 1 },
  pageHidden: { display: "none" },
  tabs: {
    flexDirection: "row",
    gap: 6,
    backgroundColor: colors.white,
    borderTopWidth: 2,
    borderTopColor: colors.ink,
    paddingTop: 8,
    paddingHorizontal: 10,
  },
  tab: {
    flex: 1,
    minHeight: 56,
    borderRadius: radius.button,
    borderWidth: 2,
    borderColor: "transparent",
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
    paddingVertical: 6,
  },
  tabOn: {
    backgroundColor: colors.paper,
    borderColor: colors.ink,
    boxShadow: shadow.tight,
  },
  tabLabel: { fontFamily: font.bold, fontSize: 13, color: colors.ink },
});
