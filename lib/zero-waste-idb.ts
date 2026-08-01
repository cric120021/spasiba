"use client";

import type { ListenerProfile, SearchResponse, Song, StreamResponse } from "@/lib/types";

export type ZeroWasteStoreName =
  | "tracks"
  | "search_cache"
  | "related_cache"
  | "quick_picks_cache"
  | "stream_cache"
  | "listener_profile";

type CachePriority = 1 | 2 | 3 | 4 | 5;

type ZeroWasteRecord<T> = {
  key: string;
  value: T;
  storedAt: number;
  expiresAt: number | null;
  lastAccessedAt: number;
  sizeBytes: number;
  priority: CachePriority;
};

type SearchCachePayload = SearchResponse & {
  requestKey: string;
};

const DB_NAME = "spasiba-zero-waste-v1";
const DB_VERSION = 1;
const SOFT_CAP_BYTES = 75 * 1024 * 1024;
const MAINTENANCE_THROTTLE_MS = 60_000;
const STORE_NAMES: ZeroWasteStoreName[] = [
  "tracks",
  "search_cache",
  "related_cache",
  "quick_picks_cache",
  "stream_cache",
  "listener_profile"
];

const STORE_PRIORITIES: Record<ZeroWasteStoreName, CachePriority> = {
  tracks: 2,
  search_cache: 1,
  related_cache: 1,
  quick_picks_cache: 4,
  stream_cache: 5,
  listener_profile: 4
};
const STORE_MAX_ENTRIES: Partial<Record<ZeroWasteStoreName, number>> = {
  search_cache: 5000
};

let dbPromise: Promise<IDBDatabase> | null = null;
let maintenancePromise: Promise<void> | null = null;
let lastMaintenanceAt = 0;

function canUseIndexedDb() {
  return typeof window !== "undefined" && typeof window.indexedDB !== "undefined";
}

function openDatabase() {
  if (!canUseIndexedDb()) {
    return Promise.reject(new Error("IndexedDB is unavailable."));
  }

  if (dbPromise) {
    return dbPromise;
  }

  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
    const request = window.indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const database = request.result;

      for (const storeName of STORE_NAMES) {
        if (!database.objectStoreNames.contains(storeName)) {
          database.createObjectStore(storeName, { keyPath: "key" });
        }
      }
    };

    request.onsuccess = () => {
      const database = request.result;
      database.onversionchange = () => {
        database.close();
      };
      resolve(database);
    };

    request.onerror = () => {
      reject(request.error || new Error("Unable to open IndexedDB."));
    };
  }).catch((error) => {
    dbPromise = null;
    throw error;
  });

  return dbPromise;
}

function estimateSizeBytes(value: unknown) {
  try {
    return Math.max(32, new Blob([JSON.stringify(value)]).size);
  } catch {
    return 1024;
  }
}

function getRecordPriority(storeName: ZeroWasteStoreName) {
  return STORE_PRIORITIES[storeName];
}

async function runTransaction<T>(
  storeNames: ZeroWasteStoreName[],
  mode: IDBTransactionMode,
  handler: (stores: Record<ZeroWasteStoreName, IDBObjectStore>) => Promise<T> | T
) {
  const database = await openDatabase();

  return new Promise<T>((resolve, reject) => {
    const transaction = database.transaction(storeNames, mode);
    const stores = Object.fromEntries(
      storeNames.map((storeName) => [storeName, transaction.objectStore(storeName)])
    ) as Record<ZeroWasteStoreName, IDBObjectStore>;
    let settled = false;
    let result: T;

    transaction.oncomplete = () => {
      if (!settled) {
        settled = true;
        resolve(result);
      }
    };
    transaction.onerror = () => {
      if (!settled) {
        settled = true;
        reject(transaction.error || new Error("IndexedDB transaction failed."));
      }
    };
    transaction.onabort = () => {
      if (!settled) {
        settled = true;
        reject(transaction.error || new Error("IndexedDB transaction aborted."));
      }
    };

    Promise.resolve(handler(stores))
      .then((value) => {
        result = value;
      })
      .catch((error) => {
        if (!settled) {
          settled = true;
          transaction.abort();
          reject(error);
        }
      });
  });
}

function requestAsPromise<T>(request: IDBRequest<T>) {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error("IndexedDB request failed."));
  });
}

async function getStoredRecord<T>(storeName: ZeroWasteStoreName, key: string) {
  try {
    const record = await runTransaction([storeName], "readonly", (stores) =>
      requestAsPromise(stores[storeName].get(key) as IDBRequest<ZeroWasteRecord<T> | undefined>)
    );

    if (!record) {
      return null;
    }

    if (record.expiresAt !== null && record.expiresAt <= Date.now()) {
      void deleteStoredRecord(storeName, key);
      return null;
    }

    void touchStoredRecord(storeName, key, record);
    return record;
  } catch {
    return null;
  }
}

async function touchStoredRecord<T>(storeName: ZeroWasteStoreName, key: string, existing?: ZeroWasteRecord<T>) {
  try {
    const record = existing || (await getStoredRecord<T>(storeName, key));

    if (!record) {
      return;
    }

    await runTransaction([storeName], "readwrite", (stores) =>
      requestAsPromise(
        stores[storeName].put({
          ...record,
          lastAccessedAt: Date.now()
        })
      )
    );
  } catch {
    // Best-effort only.
  }
}

async function getAllRecords<T>(storeName: ZeroWasteStoreName) {
  try {
    return await runTransaction([storeName], "readonly", (stores) =>
      requestAsPromise(stores[storeName].getAll() as IDBRequest<ZeroWasteRecord<T>[]>)
    );
  } catch {
    return [];
  }
}

async function deleteStoredRecord(storeName: ZeroWasteStoreName, key: string) {
  try {
    await runTransaction([storeName], "readwrite", (stores) => requestAsPromise(stores[storeName].delete(key)));
  } catch {
    // Best-effort only.
  }
}

async function pruneExpiredRecords() {
  const now = Date.now();

  for (const storeName of STORE_NAMES) {
    const records = await getAllRecords(storeName);

    await Promise.all(
      records
        .filter((record) => record.expiresAt !== null && record.expiresAt <= now)
        .map((record) => deleteStoredRecord(storeName, record.key))
    );
  }
}

async function enforceStoreEntryCaps() {
  try {
    for (const [storeName, maxEntries] of Object.entries(STORE_MAX_ENTRIES) as Array<
      [ZeroWasteStoreName, number]
    >) {
      if (!maxEntries || maxEntries <= 0) {
        continue;
      }

      const records = await getAllRecords(storeName);

      if (records.length <= maxEntries) {
        continue;
      }

      const evictionCandidates = [...records].sort((left, right) => left.lastAccessedAt - right.lastAccessedAt);
      const recordsToDelete = evictionCandidates.slice(0, Math.max(0, records.length - maxEntries));

      await Promise.all(recordsToDelete.map((record) => deleteStoredRecord(storeName, record.key)));
    }
  } catch {
    // Entry-cap enforcement is best-effort only.
  }
}

async function enforceSoftCap() {
  try {
    const records = (
      await Promise.all(
        STORE_NAMES.map(async (storeName) => {
          const storedRecords = await getAllRecords(storeName);
          return storedRecords.map((record) => ({
            storeName,
            ...record
          }));
        })
      )
    ).flat();

    let totalBytes = records.reduce((sum, record) => sum + Math.max(0, record.sizeBytes || 0), 0);

    if (totalBytes <= SOFT_CAP_BYTES) {
      return;
    }

    const evictionCandidates = records
      .filter((record) => !["stream_cache", "quick_picks_cache", "listener_profile"].includes(record.storeName))
      .sort((left, right) => {
        if (left.priority !== right.priority) {
          return left.priority - right.priority;
        }

        return left.lastAccessedAt - right.lastAccessedAt;
      });

    for (const candidate of evictionCandidates) {
      if (totalBytes <= SOFT_CAP_BYTES) {
        break;
      }

      await deleteStoredRecord(candidate.storeName, candidate.key);
      totalBytes -= Math.max(0, candidate.sizeBytes || 0);
    }

    if (totalBytes <= SOFT_CAP_BYTES) {
      return;
    }

    const trackCandidates = records
      .filter((record) => record.storeName === "tracks")
      .sort((left, right) => left.lastAccessedAt - right.lastAccessedAt);

    for (const candidate of trackCandidates) {
      if (totalBytes <= SOFT_CAP_BYTES) {
        break;
      }

      await deleteStoredRecord(candidate.storeName, candidate.key);
      totalBytes -= Math.max(0, candidate.sizeBytes || 0);
    }
  } catch {
    // Budget enforcement is best-effort only.
  }
}

function scheduleZeroWasteMaintenance(force = false) {
  if (!canUseIndexedDb()) {
    return maintenancePromise;
  }

  const now = Date.now();

  if (!force && maintenancePromise) {
    return maintenancePromise;
  }

  if (!force && now - lastMaintenanceAt < MAINTENANCE_THROTTLE_MS) {
    return maintenancePromise;
  }

  const run = (async () => {
    await pruneExpiredRecords();
    await enforceStoreEntryCaps();
    await enforceSoftCap();
    lastMaintenanceAt = Date.now();
  })()
    .catch(() => {
      // Best-effort only.
    })
    .finally(() => {
      maintenancePromise = null;
    });

  maintenancePromise = run;
  return maintenancePromise;
}

export async function readZeroWasteCache<T>(storeName: ZeroWasteStoreName, key: string) {
  void scheduleZeroWasteMaintenance();
  const record = await getStoredRecord<T>(storeName, key);
  return record?.value ?? null;
}

export async function writeZeroWasteCache<T>(
  storeName: ZeroWasteStoreName,
  key: string,
  value: T,
  options?: {
    ttlMs?: number | null;
    priority?: CachePriority;
  }
) {
  const record: ZeroWasteRecord<T> = {
    key,
    value,
    storedAt: Date.now(),
    expiresAt: typeof options?.ttlMs === "number" ? Date.now() + options.ttlMs : null,
    lastAccessedAt: Date.now(),
    sizeBytes: estimateSizeBytes(value),
    priority: options?.priority ?? getRecordPriority(storeName)
  };

  try {
    await runTransaction([storeName], "readwrite", (stores) => requestAsPromise(stores[storeName].put(record)));
    void scheduleZeroWasteMaintenance(true);
  } catch {
    // Best-effort only.
  }

  return value;
}

export async function listZeroWasteCacheValues<T>(storeName: ZeroWasteStoreName, limit = 24) {
  void scheduleZeroWasteMaintenance();
  const records = await getAllRecords<T>(storeName);
  const now = Date.now();

  const validRecords = records.filter((record) => record.expiresAt === null || record.expiresAt > now);
  validRecords
    .sort((left, right) => right.lastAccessedAt - left.lastAccessedAt)
    .slice(0, Math.max(limit, 0))
    .forEach((record) => {
      void touchStoredRecord(storeName, record.key, record);
    });

  return validRecords.slice(0, Math.max(limit, 0)).map((record) => record.value);
}

export function ensureZeroWasteCacheMaintenance() {
  void scheduleZeroWasteMaintenance(true);
}

export async function rememberRecommendationTracks(tracks: Song[], ttlMs = 1000 * 60 * 60 * 24 * 14) {
  const uniqueTracks = new Map<string, Song>();

  for (const track of tracks) {
    if (!track?.id) {
      continue;
    }

    uniqueTracks.set(track.id, track);
  }

  await Promise.all(
    Array.from(uniqueTracks.values()).map((track) => writeZeroWasteCache("tracks", track.id, track, { ttlMs, priority: 2 }))
  );
}

export async function readRecommendationTrackPool(limit = 160) {
  return listZeroWasteCacheValues<Song>("tracks", limit);
}

export async function cacheSearchResponse(
  requestKey: string,
  response: SearchResponse,
  options?: {
    related?: boolean;
    ttlMs?: number;
  }
) {
  const storeName: ZeroWasteStoreName = options?.related ? "related_cache" : "search_cache";
  const ttlMs = options?.ttlMs ?? (options?.related ? 1000 * 60 * 60 * 24 * 14 : 1000 * 60 * 60 * 24 * 14);
  const payload: SearchCachePayload = {
    ...response,
    requestKey
  };

  await writeZeroWasteCache(storeName, requestKey, payload, {
    ttlMs,
    priority: options?.related ? 1 : 1
  });
  await rememberRecommendationTracks(response.results, ttlMs);
  return response;
}

export async function readCachedSearchResponse(requestKey: string, related = false) {
  const storeName: ZeroWasteStoreName = related ? "related_cache" : "search_cache";
  const cached = await readZeroWasteCache<SearchCachePayload>(storeName, requestKey);
  return cached
    ? {
        query: cached.query,
        results: cached.results,
        cachedAt: cached.cachedAt
      }
    : null;
}

export async function readCachedStreamResponse(trackKey: string) {
  return readZeroWasteCache<StreamResponse>("stream_cache", trackKey);
}

export async function cacheStreamResponse(trackKey: string, response: StreamResponse, ttlMs: number | null) {
  await writeZeroWasteCache("stream_cache", trackKey, response, {
    ttlMs,
    priority: 5
  });
  return response;
}

export async function readCachedQuickPicksSnapshot<T>(cacheKey: string) {
  return readZeroWasteCache<T>("quick_picks_cache", cacheKey);
}

export async function cacheQuickPicksSnapshot<T>(cacheKey: string, value: T, ttlMs: number) {
  await writeZeroWasteCache("quick_picks_cache", cacheKey, value, {
    ttlMs,
    priority: 4
  });
  return value;
}

export async function readListenerProfileSnapshot(cacheKey = "primary") {
  return readZeroWasteCache<ListenerProfile>("listener_profile", cacheKey);
}

export async function writeListenerProfileSnapshot(profile: ListenerProfile, cacheKey = "primary") {
  await writeZeroWasteCache("listener_profile", cacheKey, profile, {
    ttlMs: 1000 * 60 * 60 * 24 * 30,
    priority: 4
  });
  return profile;
}
