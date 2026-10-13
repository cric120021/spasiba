"use client";

type PersistedCacheRecord<T> = {
  storedAt: number;
  expiresAt: number;
  value: T;
};

const CACHE_PREFIX = "spasiba:persisted-cache:v1:";
const LOCAL_STATE_PREFIX = "spasiba:local-state:v1:";

function getStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function getNamespacedKey(key: string) {
  return `${CACHE_PREFIX}${key}`;
}

function getLocalStateKey(key: string) {
  return `${LOCAL_STATE_PREFIX}${key}`;
}

export function readPersistedCacheRecord<T>(key: string): PersistedCacheRecord<T> | undefined {
  const storage = getStorage();

  if (!storage) {
    return undefined;
  }

  try {
    const rawValue = storage.getItem(getNamespacedKey(key));

    if (!rawValue) {
      return undefined;
    }

    const parsed = JSON.parse(rawValue) as PersistedCacheRecord<T> | null;

    if (!parsed || typeof parsed.expiresAt !== "number") {
      storage.removeItem(getNamespacedKey(key));
      return undefined;
    }

    if (parsed.expiresAt <= Date.now()) {
      storage.removeItem(getNamespacedKey(key));
      return undefined;
    }

    return {
      storedAt: typeof parsed.storedAt === "number" ? parsed.storedAt : 0,
      expiresAt: parsed.expiresAt,
      value: parsed.value
    };
  } catch {
    return undefined;
  }
}

export function readPersistedCache<T>(key: string): T | undefined {
  return readPersistedCacheRecord<T>(key)?.value;
}

export function writePersistedCache<T>(key: string, value: T, ttlMs: number) {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    const record: PersistedCacheRecord<T> = {
      storedAt: Date.now(),
      expiresAt: Date.now() + ttlMs,
      value
    };

    storage.setItem(getNamespacedKey(key), JSON.stringify(record));
  } catch {
    // Best-effort only.
  }
}

export function removePersistedCache(key: string) {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    storage.removeItem(getNamespacedKey(key));
  } catch {
    // Ignore local storage failures.
  }
}

export function readLocalState<T>(key: string): T | undefined {
  const storage = getStorage();

  if (!storage) {
    return undefined;
  }

  try {
    const rawValue = storage.getItem(getLocalStateKey(key));

    if (!rawValue) {
      return undefined;
    }

    return JSON.parse(rawValue) as T;
  } catch {
    return undefined;
  }
}

export function writeLocalState<T>(key: string, value: T) {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    storage.setItem(getLocalStateKey(key), JSON.stringify(value));
  } catch {
    // Best-effort only.
  }
}

export function removeLocalState(key: string) {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    storage.removeItem(getLocalStateKey(key));
  } catch {
    // Ignore local storage failures.
  }
}
