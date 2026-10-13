export type DeviceInfo = {
  isIOS: boolean;
  isAndroid: boolean;
  isStandalone: boolean;
  saveData: boolean;
  connectionType: string | null;
  deviceMemory: number | null;
};

type NavigatorWithStandalone = Navigator & {
  standalone?: boolean;
  deviceMemory?: number;
  connection?: {
    saveData?: boolean;
    effectiveType?: string;
  };
};

export function getDeviceInfo(): DeviceInfo {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return {
      isIOS: false,
      isAndroid: false,
      isStandalone: false,
      saveData: false,
      connectionType: null,
      deviceMemory: null
    };
  }

  const nav = navigator as NavigatorWithStandalone;
  const isIOS =
    /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (typeof navigator !== "undefined" && navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const isAndroid = /Android/i.test(navigator.userAgent);
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches || Boolean(nav.standalone);

  return {
    isIOS,
    isAndroid,
    isStandalone,
    saveData: Boolean(nav.connection?.saveData),
    connectionType: nav.connection?.effectiveType || null,
    deviceMemory: typeof nav.deviceMemory === "number" ? nav.deviceMemory : null
  };
}
