export type HomeUiVariant = "classic" | "discovery";

const envVariant = process.env.NEXT_PUBLIC_HOME_UI_VARIANT;

// Default stays on the stable classic home UI.
// To bring back the newer discovery experiment later, set:
// NEXT_PUBLIC_HOME_UI_VARIANT=discovery
export const HOME_UI_VARIANT: HomeUiVariant = envVariant === "discovery" ? "discovery" : "classic";
