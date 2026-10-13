export type StreamCandidate = {
  url: string;
  mimeType: string | null;
  expiresAt: number | null;
};

export type EngagementAction =
  | "play_start"
  | "skip_early"
  | "skip_mid"
  | "skip_late"
  | "complete"
  | "queue_add"
  | "playlist_add"
  | "like"
  | "unlike";

export type EngagementPayload = {
  action: EngagementAction;
  song: Song;
  progress?: number | null;
};

export type ListenerProfile = {
  artists: Record<string, number>;
  tracks: Record<string, number>;
  signals: Record<string, number>;
  updatedAt: string;
};

export type Song = {
  id: string;
  videoId: string;
  title: string;
  thumbnail: string;
  duration: string;
  durationSeconds: number;
  author: string;
  url: string;
  views: number;
  streamUrl?: string;
  streamMimeType?: string | null;
  streamExpiresAt?: number | null;
  streamCandidates?: StreamCandidate[];
};

export type SearchResponse = {
  query: string;
  results: Song[];
  cachedAt: string;
};

export type StreamResponse = {
  streamUrl: string;
  mimeType: string | null;
  expiresAt: number | null;
  streamCandidates: StreamCandidate[];
};

export type TrendingSection = {
  id: string;
  title: string;
  blurb: string;
  query: string;
  tracks: Song[];
};

export type Playlist = {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  shareToken?: string | null;
  isShared?: boolean;
  ownerName?: string | null;
  trackCount: number;
  coverTrack?: Song | null;
  tracks: Song[];
};

export type PlaylistShareGrant = {
  username: string;
  displayName: string | null;
  createdAt: string;
};

export type ProfileSongEntry = Song & {
  lastListenedAt?: string | null;
  playCount?: number;
  preferenceScore?: number;
};

export type UserProfile = {
  name: string;
  username: string;
  profileImageDataUrl: string | null;
  monthLabel: string;
  monthlyHistory: ProfileSongEntry[];
  likedSongs: ProfileSongEntry[];
};
