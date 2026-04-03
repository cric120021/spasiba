const USERNAME_PATTERN = /^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])?$/;

export function normalizeUsername(value: string | null | undefined) {
  return (typeof value === "string" ? value : "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "");
}

export function validateUsername(value: string) {
  const normalized = normalizeUsername(value);

  if (!normalized) {
    throw new Error("Username is required.");
  }

  if (normalized.length < 3) {
    throw new Error("Username must be at least 3 characters.");
  }

  if (normalized.length > 30) {
    throw new Error("Username must be 30 characters or less.");
  }

  if (!USERNAME_PATTERN.test(normalized)) {
    throw new Error("Use only letters, numbers, dots, dashes, or underscores.");
  }

  return normalized;
}
