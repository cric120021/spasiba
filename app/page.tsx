import HomeClassic from "@/components/HomeClassic";
import HomeDiscovery from "@/components/HomeDiscovery";
import { HOME_UI_VARIANT } from "@/lib/ui-config";
import { DISCOVERY_SECTIONS, getTrendingSections } from "@/lib/youtube";

export const runtime = "nodejs";
export const revalidate = 604800;

export default async function HomePage() {
  const sections = await getTrendingSections().catch(() =>
    DISCOVERY_SECTIONS.map((section) => ({
      ...section,
      tracks: []
    }))
  );

  return HOME_UI_VARIANT === "discovery" ? <HomeDiscovery sections={sections} /> : <HomeClassic sections={sections} />;
}
