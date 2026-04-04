export type HomeCategorySectionLayout = "quick-picks" | "mixed" | "rail";

export type HomeCategorySectionConfig = {
  id: string;
  title: string;
  layout: HomeCategorySectionLayout;
  queries: readonly string[];
  searchCategory?: "music" | "";
  allowLongform?: boolean;
  limit?: number;
};

export type HomeCategoryConfig = {
  id: string;
  title: string;
  query: string;
  eyebrow: string;
  accent: string;
  headerRgb: string;
  backdropPath: string;
  sections: readonly HomeCategorySectionConfig[];
};

export const HOME_CATEGORY_CONFIGS: readonly HomeCategoryConfig[] = [
  {
    id: "podcast",
    title: "Podcast",
    query: "podcast highlights audio",
    eyebrow: "SHOWS FOR YOU",
    accent: "from-slate-100/24 via-slate-200/6 to-transparent",
    headerRgb: "148 163 184",
    backdropPath: "https://www.gstatic.com/youtube/media/ytm/images/home_backgrounds/podcast-afternoon@1500x844.png",
    sections: [
      {
        id: "shows-for-you",
        title: "Shows for you",
        layout: "rail",
        queries: ["indian business podcast", "self improvement podcast clip", "casual talk podcast india"],
        allowLongform: true
      },
      {
        id: "films",
        title: "Films",
        layout: "rail",
        queries: ["raw and real pgx clip", "latest interview podcast clip", "film discussion podcast clip"],
        allowLongform: true
      },
      {
        id: "history",
        title: "History",
        layout: "quick-picks",
        queries: ["history podcast clip india", "mystery history podcast clip", "facts podcast clip"],
        allowLongform: true
      }
    ]
  },
  {
    id: "workout",
    title: "Workout",
    query: "high energy workout songs official audio",
    eyebrow: "FOR YOUR WORKOUT",
    accent: "from-amber-200/24 via-orange-200/8 to-transparent",
    headerRgb: "251 146 60",
    backdropPath: "https://lh3.googleusercontent.com/xW41X55Pvpo2zLCniTPXXNH2wpPgrpFj0IAYqREB4e01RIcMBmE0C73J3o_D9dmJV6P9mMRkZcwDLw=w1500-h844-l90-rj",
    sections: [
      {
        id: "long-listens",
        title: "Long listens",
        layout: "quick-picks",
        queries: ["best hindi gym workout songs", "non stop workout mix songs", "bollywood workout mix"],
        allowLongform: true
      },
      {
        id: "similar-to",
        title: "Similar to your pace",
        layout: "rail",
        queries: ["bollywood workout songs official audio", "gym anthems official audio", "workout drive songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["workout supermix official audio", "high energy workout songs official audio", "motivational gym songs official audio"]
      }
    ]
  },
  {
    id: "relax",
    title: "Relax",
    query: "relax songs official audio",
    eyebrow: "FOR RELAXING",
    accent: "from-cyan-100/22 via-sky-200/8 to-transparent",
    headerRgb: "34 211 238",
    backdropPath: "https://lh3.googleusercontent.com/9_qmVOvLuS7mCYj7nw6Zgy01K3NKSaI8YvLu_spdTL6i1goKHEVoMZGVCetG7RzlI2M4e4gjM6DquZ3L=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["relax songs official audio", "soft chill songs official audio", "easy listening songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["chill mix official audio", "relax mix official audio", "soft focus mix official audio"]
      },
      {
        id: "from-community",
        title: "From the community",
        layout: "rail",
        queries: ["acoustic love songs official audio", "calm indie songs official audio", "soft romantic songs official audio"]
      }
    ]
  },
  {
    id: "energise",
    title: "Energise",
    query: "energising songs official audio",
    eyebrow: "FOR ENERGISING",
    accent: "from-rose-100/22 via-orange-200/8 to-transparent",
    headerRgb: "251 146 60",
    backdropPath: "https://lh3.googleusercontent.com/XLbDsfPCEV3i6okk5itzYlpNb0Tn9KRwvCdlEUnmkVE_J0lTQASwc0_znu6H6MYUv7QOSgswHCxkxQ=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["energising songs official audio", "power pop official audio", "hype songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["energetic mix official audio", "power mix official audio", "hype mix official audio"]
      },
      {
        id: "just-the-hits",
        title: "Just the hits",
        layout: "rail",
        queries: ["rock hits official audio", "metal hits official audio", "alt energy songs official audio"]
      }
    ]
  },
  {
    id: "feel-good",
    title: "Feel Good",
    query: "feel good songs official audio",
    eyebrow: "FOR FEEL-GOOD MOODS",
    accent: "from-lime-100/22 via-emerald-200/8 to-transparent",
    headerRgb: "132 204 22",
    backdropPath: "https://lh3.googleusercontent.com/-UsHCi3Mw29tnFKM0preAuYCNc6pGClH9DzT0WDawW30gNAjpu0gpkuKoO0kRHTxz-5aEBX6YOZhQ2o=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["feel good songs official audio", "happy songs official audio", "sunny pop songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["feel good mix official audio", "happy mix official audio", "bright pop mix official audio"]
      },
      {
        id: "easy-repeats",
        title: "Easy repeats",
        layout: "rail",
        queries: ["uplifting songs official audio", "good mood songs official audio", "positive hits official audio"]
      }
    ]
  },
  {
    id: "romance",
    title: "Romance",
    query: "romantic love songs official audio",
    eyebrow: "FOR ROMANCE",
    accent: "from-rose-100/22 via-fuchsia-200/8 to-transparent",
    headerRgb: "251 113 133",
    backdropPath: "https://lh3.googleusercontent.com/QHEWxP5BjpPhZkmZ22xsROuXPGy9ttV9QzmDoPVU_UqvLop6AQbebAm-YbFVkMQ7cJ3tmVV67O0jA_I=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["romantic love songs official audio", "soft romantic songs official audio", "bollywood romantic hits official audio"]
      },
      {
        id: "sweethearts",
        title: "Sweethearts & romance",
        layout: "rail",
        queries: ["acoustic love songs official audio", "romantic duets official audio", "slow love songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["romantic mix official audio", "love mix official audio", "soft romance mix official audio"]
      }
    ]
  },
  {
    id: "party",
    title: "Party",
    query: "party songs official audio",
    eyebrow: "FOR PARTIES",
    accent: "from-violet-100/22 via-fuchsia-200/8 to-transparent",
    headerRgb: "217 70 239",
    backdropPath: "/category-backdrops/party.svg",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["party songs official audio", "dance hits official audio", "bollywood party songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["party mix official audio", "dance mix official audio", "club mix official audio"]
      },
      {
        id: "just-the-hits",
        title: "Just the hits",
        layout: "rail",
        queries: ["dance pop official audio", "party hits official audio", "club anthems official audio"]
      }
    ]
  },
  {
    id: "commute",
    title: "Commute",
    query: "drive songs official audio",
    eyebrow: "FOR COMMUTES",
    accent: "from-amber-100/20 via-rose-200/8 to-transparent",
    headerRgb: "248 113 113",
    backdropPath: "https://lh3.googleusercontent.com/boAaN71qGdHlJ9FZUvsga4b0tk3t-BzJ1TkFVF5tWsQK9-_JAyazY1tQKyUEGP4PAl23xXOfMv-1-onF=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["drive songs official audio", "road trip songs official audio", "evening drive songs official audio"]
      },
      {
        id: "just-the-hits",
        title: "Just the hits",
        layout: "rail",
        queries: ["90s chill bollywood official audio", "indie drive songs official audio", "commute pop songs official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["commute mix official audio", "evening drive mix official audio", "city ride mix official audio"]
      }
    ]
  },
  {
    id: "sleep",
    title: "Sleep",
    query: "soft night songs official audio",
    eyebrow: "FOR SLEEPING",
    accent: "from-indigo-100/18 via-slate-200/6 to-transparent",
    headerRgb: "129 140 248",
    backdropPath: "/category-backdrops/sleep.svg",
    sections: [
      {
        id: "wind-down",
        title: "Wind down",
        layout: "quick-picks",
        queries: ["soft night songs official audio", "calm sleep songs official audio", "gentle acoustic night songs"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["sleep mix official audio", "night calm mix official audio", "soft sleep mix official audio"]
      },
      {
        id: "night-focus",
        title: "Night focus",
        layout: "rail",
        queries: ["soft focus songs official audio", "late night chill official audio", "calm study songs official audio"]
      }
    ]
  },
  {
    id: "focus",
    title: "Focus",
    query: "focus songs official audio",
    eyebrow: "FOR FOCUS",
    accent: "from-sky-100/22 via-cyan-200/8 to-transparent",
    headerRgb: "56 189 248",
    backdropPath: "https://lh3.googleusercontent.com/OXBoCLUN-ZabqHqd0iRepimpWPpD_V7pocuvX9fL83gwqMJ-uHBM6vf01EbAzXboTKkw-GMS8gGSCA=w1500-h844-l90-rj",
    sections: [
      {
        id: "quick-picks",
        title: "Quick picks",
        layout: "quick-picks",
        queries: ["focus songs official audio", "study songs official audio", "clean pop songs official audio"]
      },
      {
        id: "deep-focus",
        title: "Deep focus",
        layout: "rail",
        queries: ["focus mix official audio", "study mix official audio", "clean instrumental pop official audio"]
      },
      {
        id: "mixed-for-you",
        title: "Mixed for you",
        layout: "mixed",
        queries: ["focus mix official audio", "productivity mix official audio", "steady work mix official audio"]
      }
    ]
  }
] as const;

export const HOME_CATEGORY_CHIPS = HOME_CATEGORY_CONFIGS.map(({ id, title, query }) => ({
  id,
  title,
  query
}));

export function getHomeCategoryConfig(categoryId: string) {
  return HOME_CATEGORY_CONFIGS.find((category) => category.id === categoryId) || null;
}
