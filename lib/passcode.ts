/**
 * Utility for fetching and verifying the download passcode dynamically
 * from the GitHub repository README.
 */

const DEFAULT_REPO = "cric120021/spasiba";
const DEFAULT_BRANCH = "main";
const DEFAULT_FILE = "README.md";

export function extractPasscodeFromMarkdown(content: string): string | null {
  if (!content || typeof content !== "string") return null;

  // Pattern 1: HTML comment e.g. <!-- DOWNLOAD_PASSCODE: 12345 --> or <!-- PASSCODE: 12345 -->
  const commentMatch = content.match(
    /<!--\s*(?:DOWNLOAD_)?PASS(?:CODE|WORD)\s*:\s*([^\r\n>]+?)\s*-->/i
  );
  if (commentMatch && commentMatch[1]?.trim()) {
    const val = cleanPasscode(commentMatch[1]);
    if (val) return val;
  }

  // Pattern 2: Markdown header followed by value on subsequent line
  // ### Download Passcode\n12345
  const headerMatch = content.match(
    /#{1,6}\s*(?:Download\s+)?Pass(?:code|word)\s*\r?\n+([^\r\n#]+)/i
  );
  if (headerMatch && headerMatch[1]?.trim()) {
    const val = cleanPasscode(headerMatch[1]);
    if (val) return val;
  }

  // Pattern 3: Line with Passcode/Password label e.g.
  // Passcode: 12345, **Download Passcode:** `12345`, DOWNLOAD_PASSCODE = 12345
  const lineMatch = content.match(
    /(?:^|\n)[ \t]*(?:\*\*)?(?:(?:DOWNLOAD|APK)[_ -]?)?PASS(?:CODE|WORD)(?:\*\*)?[ \t]*[:=][ \t]*([^\r\n]+)/i
  );
  if (lineMatch && lineMatch[1]?.trim()) {
    const val = cleanPasscode(lineMatch[1]);
    if (val) return val;
  }

  return null;
}

function cleanPasscode(raw: string): string {
  // Strip markdown formatting symbols (`*_"[]) and outer spaces
  return raw.replace(/[`"'*_\[\]]/g, "").trim();
}

export async function getGitHubPasscode(): Promise<{
  passcode: string | null;
  source: "github_raw" | "github_api" | "env" | "none";
  error?: string;
}> {
  const repo = process.env.GITHUB_PASSCODE_REPO || DEFAULT_REPO;
  const branch = process.env.GITHUB_PASSCODE_BRANCH || DEFAULT_BRANCH;
  const file = process.env.GITHUB_PASSCODE_FILE || DEFAULT_FILE;
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;

  // 1. Try fetching directly via raw GitHub content (cache-busted)
  try {
    const rawUrl = `https://raw.githubusercontent.com/${repo}/${branch}/${file}?_t=${Date.now()}`;
    const rawRes = await fetch(rawUrl, {
      cache: "no-store",
      headers: {
        "Cache-Control": "no-cache, no-store, must-revalidate",
        Pragma: "no-cache",
        "User-Agent": "Spasiba-Passcode-Verifier",
      },
    });

    if (rawRes.ok) {
      const markdown = await rawRes.text();
      const extracted = extractPasscodeFromMarkdown(markdown);
      if (extracted) {
        return { passcode: extracted, source: "github_raw" };
      }
    }
  } catch (_rawErr) {
    // Fall through to GitHub API
  }

  // 2. Try fetching via GitHub API (either readme or contents endpoint)
  try {
    const apiUrl =
      file === "README.md"
        ? `https://api.github.com/repos/${repo}/readme?ref=${branch}`
        : `https://api.github.com/repos/${repo}/contents/${file}?ref=${branch}`;

    const headers: Record<string, string> = {
      Accept: "application/vnd.github.raw",
      "User-Agent": "Spasiba-Passcode-Verifier",
      "Cache-Control": "no-cache, no-store, must-revalidate",
      Pragma: "no-cache",
    };

    if (token) {
      headers.Authorization = `token ${token}`;
    }

    const apiRes = await fetch(apiUrl, {
      cache: "no-store",
      headers,
    });

    if (apiRes.ok) {
      const markdown = await apiRes.text();
      const extracted = extractPasscodeFromMarkdown(markdown);
      if (extracted) {
        return { passcode: extracted, source: "github_api" };
      }
    }
  } catch (_apiErr) {
    // Fall through to env fallback
  }

  // 3. Fallback to process.env.DOWNLOAD_PASSCODE if present
  if (process.env.DOWNLOAD_PASSCODE && process.env.DOWNLOAD_PASSCODE.trim()) {
    return {
      passcode: process.env.DOWNLOAD_PASSCODE.trim(),
      source: "env",
    };
  }

  return {
    passcode: null,
    source: "none",
    error: `Passcode not found in GitHub README (${repo}/${file})`,
  };
}

export async function verifyPasscode(
  inputPasscode: string | null | undefined
): Promise<{ valid: boolean; error?: string }> {
  const cleanInput = (inputPasscode || "").trim();

  if (!cleanInput) {
    return { valid: false, error: "Password is required." };
  }

  const result = await getGitHubPasscode();

  if (!result.passcode) {
    return {
      valid: false,
      error: "Password verification is temporarily unavailable. Please try again later.",
    };
  }

  if (cleanInput !== result.passcode) {
    return {
      valid: false,
      error: "Incorrect password. Please try again.",
    };
  }

  return { valid: true };
}
