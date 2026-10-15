import { Outfit_500Medium, Outfit_600SemiBold, Outfit_700Bold, useFonts } from "@expo-google-fonts/outfit";
import { StatusBar } from "expo-status-bar";
import { View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { SessionProvider } from "./src/session";
import { colors } from "./src/theme";

export default function App() {
  const [fontsLoaded] = useFonts({
    Outfit_500Medium,
    Outfit_600SemiBold,
    Outfit_700Bold,
  });

  if (!fontsLoaded) {
    return null;
  }

  return (
    <SafeAreaProvider>
      <SessionProvider>
        <Shell />
      </SessionProvider>
    </SafeAreaProvider>
  );
}

function Shell() {
  return (
    <View style={{ flex: 1, backgroundColor: "#E7DCC8" }}>
      <SafeAreaView style={{ flex: 1, width: "100%", maxWidth: 480, alignSelf: "center", backgroundColor: colors.paper }}>
        <StatusBar style="dark" />
        <RootNavigator />
      </SafeAreaView>
    </View>
  );
}
