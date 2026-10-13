"use client";

import { createQueryKey } from "@/lib/utils";

const SESSION_STORAGE_KEY = "spasiba:resource-governor:v1";
const QUICK_PICKS_BUDGET_KEY = "spasiba:quick-picks-budget:v1";
const SEARCH_API_SESSION_LIMIT = 8;
const NETWORK_FAILURE_BUDGET = 3;
const QUICK_PICKS_DAILY_LIMIT = 2;

type GovernorSessionState = {
  searchCalls: number;
  searchRequestKeys: string[];
  failedRequests: number;
  cacheOnlyMode: boolean;
  safeMode: boolean;
  lastFailureAt: number | null;
};

type QuickPicksBudgetState = {
  dayKey: string;
  computeCount: number;
  lastComputedAt: number;
};

const DEFAULT_SESSION_STATE: GovernorSessionState = {
  searchCalls: 0,
  searchRequestKeys: [],
  failedRequests: 0,
  cacheOnlyMode: false,
  safeMode: false,
  lastFailureAt: null
};

function getSessionStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function getLocalStorage() {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function readSessionState() {
  const storage = getSessionStorage();

  if (!storage) {
    return { ...DEFAULT_SESSION_STATE };
  }

  try {
    const rawValue = storage.getItem(SESSION_STORAGE_KEY);

    if (!rawValue) {
      return { ...DEFAULT_SESSION_STATE };
    }

    const parsed = JSON.parse(rawValue) as Partial<GovernorSessionState> | null;
    return {
      ...DEFAULT_SESSION_STATE,
      ...parsed,
      searchRequestKeys: Array.isArray(parsed?.searchRequestKeys) ? parsed?.searchRequestKeys : []
    };
  } catch {
    return { ...DEFAULT_SESSION_STATE };
  }
}

function writeSessionState(nextState: GovernorSessionState) {
  const storage = getSessionStorage();

  if (!storage) {
    return nextState;
  }

  try {
    storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nextState));
  } catch {
    // Ignore storage failures.
  }

  return nextState;
}

function updateSessionState(mutator: (state: GovernorSessionState) => GovernorSessionState) {
  const nextState = mutator(readSessionState());
  return writeSessionState(nextState);
}

function getDayKey(timestamp = Date.now()) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

function readQuickPicksBudget(timestamp = Date.now()) {
  const storage = getLocalStorage();
  const currentDay = getDayKey(timestamp);

  if (!storage) {
    return {
      dayKey: currentDay,
      computeCount: 0,
      lastComputedAt: 0
    } satisfies QuickPicksBudgetState;
  }

  try {
    const rawValue = storage.getItem(QUICK_PICKS_BUDGET_KEY);

    if (!rawValue) {
      return {
        dayKey: currentDay,
        computeCount: 0,
        lastComputedAt: 0
      } satisfies QuickPicksBudgetState;
    }

    const parsed = JSON.parse(rawValue) as Partial<QuickPicksBudgetState> | null;

    if (parsed?.dayKey !== currentDay) {
      return {
        dayKey: currentDay,
        computeCount: 0,
        lastComputedAt: 0
      } satisfies QuickPicksBudgetState;
    }

    return {
      dayKey: currentDay,
      computeCount: typeof parsed?.computeCount === "number" ? parsed.computeCount : 0,
      lastComputedAt: typeof parsed?.lastComputedAt === "number" ? parsed.lastComputedAt : 0
    };
  } catch {
    return {
      dayKey: currentDay,
      computeCount: 0,
      lastComputedAt: 0
    };
  }
}

function writeQuickPicksBudget(nextState: QuickPicksBudgetState) {
  const storage = getLocalStorage();

  if (!storage) {
    return nextState;
  }

  try {
    storage.setItem(QUICK_PICKS_BUDGET_KEY, JSON.stringify(nextState));
  } catch {
    // Ignore storage failures.
  }

  return nextState;
}

export function readGovernorSnapshot() {
  return readSessionState();
}

export function isSafeModeEnabled() {
  return readSessionState().safeMode;
}

export function isCacheOnlyModeEnabled() {
  const state = readSessionState();
  return state.cacheOnlyMode || state.safeMode;
}

export function noteNetworkFailure() {
  return updateSessionState((currentState) => {
    const failedRequests = currentState.failedRequests + 1;
    const safeMode = failedRequests >= NETWORK_FAILURE_BUDGET;

    return {
      ...currentState,
      failedRequests,
      lastFailureAt: Date.now(),
      cacheOnlyMode: safeMode || currentState.cacheOnlyMode,
      safeMode
    };
  });
}

export function noteNetworkSuccess() {
  return updateSessionState((currentState) => ({
    ...currentState
  }));
}

export function enableCacheOnlyMode() {
  return updateSessionState((currentState) => ({
    ...currentState,
    cacheOnlyMode: true
  }));
}

export function canIssueSearchRequest(requestKey: string) {
  const normalizedKey = createQueryKey(requestKey);
  const state = readSessionState();

  if (!normalizedKey) {
    return false;
  }

  if (state.safeMode || state.cacheOnlyMode) {
    return state.searchRequestKeys.includes(normalizedKey);
  }

  if (state.searchRequestKeys.includes(normalizedKey)) {
    return true;
  }

  return state.searchCalls < SEARCH_API_SESSION_LIMIT;
}

export function consumeSearchRequest(requestKey: string) {
  const normalizedKey = createQueryKey(requestKey);

  if (!normalizedKey) {
    return false;
  }

  const state = readSessionState();

  if (state.searchRequestKeys.includes(normalizedKey)) {
    return true;
  }

  if (state.safeMode || state.cacheOnlyMode || state.searchCalls >= SEARCH_API_SESSION_LIMIT) {
    writeSessionState({
      ...state,
      cacheOnlyMode: true
    });
    return false;
  }

  writeSessionState({
    ...state,
    searchCalls: state.searchCalls + 1,
    searchRequestKeys: [...state.searchRequestKeys, normalizedKey]
  });

  return true;
}

export function canComputeQuickPicks(reason: "daily" | "heavy") {
  const budget = readQuickPicksBudget();

  if (budget.computeCount === 0) {
    return true;
  }

  if (reason === "daily") {
    return false;
  }

  return budget.computeCount < QUICK_PICKS_DAILY_LIMIT;
}

export function markQuickPicksComputed() {
  const currentState = readQuickPicksBudget();

  return writeQuickPicksBudget({
    dayKey: currentState.dayKey,
    computeCount: Math.min(QUICK_PICKS_DAILY_LIMIT, currentState.computeCount + 1),
    lastComputedAt: Date.now()
  });
}

export function getQuickPicksBudgetSnapshot() {
  return readQuickPicksBudget();
}

export function getComputeDeadline(timeBudgetMs = 50) {
  const deadline = Date.now() + timeBudgetMs;

  return {
    exceeded() {
      return Date.now() > deadline;
    }
  };
}
