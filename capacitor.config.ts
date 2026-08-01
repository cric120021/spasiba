import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "app.spasiba.music",
  appName: "Spasiba",
  webDir: ".next",
  server: {
    url: "https://spasiba.vercel.app",
    cleartext: false,
    androidScheme: "https"
  },
  android: {
    backgroundColor: "#000000",
    allowMixedContent: false
  }
};

export default config;
