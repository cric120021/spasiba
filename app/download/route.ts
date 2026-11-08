import { NextResponse } from "next/server";
import { verifyPasscode } from "@/lib/passcode";

export const dynamic = "force-dynamic";

const ANDROID_APK_DOWNLOAD_URL =
  "https://github.com/cric120021/spasiba/releases/download/v1.0.0/spasiba.apk";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const code = searchParams.get("code") || searchParams.get("passcode");

  if (code) {
    const verification = await verifyPasscode(code);
    if (verification.valid) {
      // Passcode is verified! Proceed with download redirect.
      return NextResponse.redirect(ANDROID_APK_DOWNLOAD_URL, 307);
    }

    // Invalid passcode supplied
    return renderPasscodePage({
      error: verification.error || "Incorrect passcode. Please try again.",
      initialValue: code,
    });
  }

  // No passcode supplied: render the passcode entry page
  return renderPasscodePage();
}

function renderPasscodePage(options?: { error?: string; initialValue?: string }) {
  const errorHtml = options?.error
    ? `<div class="error-banner animate-fade-in">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <span>${escapeHtml(options.error)}</span>
      </div>`
    : "";

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title>Download Spasiba APK</title>
  <link rel="icon" href="/icon" />
  <style>
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-font-smoothing: antialiased;
    }
    body {
      background-color: #0c0c0e;
      color: #f1f5f9;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .card {
      width: 100%;
      max-width: 360px;
      background: #1e222a;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 28px;
      padding: 32px 24px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.85);
      text-align: left;
    }
    .icon-wrap {
      width: 52px;
      height: 52px;
      border-radius: 18px;
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 20px;
    }
    .icon-wrap svg {
      width: 28px;
      height: 28px;
      fill: currentColor;
    }
    h1 {
      font-size: 20px;
      font-weight: 600;
      color: #ffffff;
      letter-spacing: -0.02em;
    }
    .file-meta {
      font-size: 14px;
      color: #cbd5e1;
      font-weight: 500;
      margin-top: 6px;
    }
    .file-size {
      color: #94a3b8;
      font-weight: normal;
    }
    .description {
      font-size: 13px;
      color: #94a3b8;
      line-height: 1.5;
      margin-top: 8px;
    }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      background: rgba(244, 63, 94, 0.12);
      border: 1px solid rgba(244, 63, 94, 0.3);
      color: #fb7185;
      padding: 10px 14px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      margin-top: 18px;
    }
    .form-group {
      margin-top: 20px;
    }
    label {
      display: block;
      font-size: 12px;
      font-weight: 500;
      color: #cbd5e1;
      margin-bottom: 6px;
    }
    .input-wrap {
      position: relative;
    }
    input[type="password"],
    input[type="text"] {
      width: 100%;
      background: rgba(0, 0, 0, 0.45);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 14px;
      padding: 12px 42px 12px 14px;
      color: #ffffff;
      font-size: 14px;
      outline: none;
      transition: all 0.2s ease;
    }
    input:focus {
      border-color: #34d399;
      box-shadow: 0 0 0 1px #34d399;
    }
    .toggle-eye {
      position: absolute;
      right: 12px;
      top: 50%;
      transform: translateY(-50%);
      background: none;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .toggle-eye:hover {
      color: #ffffff;
    }
    .btn-download {
      display: block;
      width: 100%;
      background: #34d399;
      color: #020617;
      font-weight: 600;
      font-size: 14px;
      border-radius: 9999px;
      padding: 12px 20px;
      border: none;
      cursor: pointer;
      margin-top: 22px;
      transition: all 0.2s ease;
      box-shadow: 0 4px 14px rgba(52, 211, 153, 0.25);
    }
    .btn-download:hover {
      background: #6ee7b7;
    }
    .btn-download:active {
      transform: scale(0.98);
    }
    .footer-link {
      display: block;
      text-align: center;
      margin-top: 18px;
      font-size: 13px;
      color: #94a3b8;
      text-decoration: none;
      transition: color 0.2s;
    }
    .footer-link:hover {
      color: #f1f5f9;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon-wrap">
      <svg viewBox="0 0 24 24">
        <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.551 0 .9993.4482.9993.9993.0001.5511-.4483.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4482-.9993.9993-.9993c.5511 0 .9993.4482.9993.9993 0 .5511-.4482.9997-.9993.9997m11.4045-6.02l1.996-3.4572c.1056-.1828.043-.4167-.14-.5223-.1828-.1055-.4169-.043-.5224.1399l-2.0252 3.5078c-1.5794-.7224-3.3756-1.1245-5.3129-1.1245s-3.7335.4021-5.3129 1.1245L4.5369 5.4818c-.1055-.1829-.3396-.2454-.5224-.1399-.183.1056-.2456.3395-.14.5223l1.996 3.4572C2.6889 11.0858 0 14.8872 0 19.3418h24c0-4.4546-2.6889-8.256-5.877-10.0204" />
      </svg>
    </div>

    <h1>Download Spasiba</h1>
    <div class="file-meta">spasiba.apk <span class="file-size">(72.1 MB)</span></div>
    <p class="description">Enter the passcode from the GitHub README to download Spasiba for Android.</p>

    ${errorHtml}

    <form method="GET" action="/download" class="form-group">
      <label for="passcode-input">Passcode</label>
      <div class="input-wrap">
        <input
          id="passcode-input"
          type="password"
          name="code"
          placeholder="Enter passcode"
          value="${escapeHtml(options?.initialValue || "")}"
          required
          autofocus
          autocomplete="off"
        />
        <button type="button" class="toggle-eye" id="toggle-eye-btn" title="Toggle visibility">
          <svg id="eye-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
            <circle cx="12" cy="12" r="3"></circle>
          </svg>
        </button>
      </div>

      <button type="submit" class="btn-download">Download APK</button>
    </form>

    <a href="/" class="footer-link">← Return to Spasiba</a>
  </div>

  <script>
    const input = document.getElementById("passcode-input");
    const toggleBtn = document.getElementById("toggle-eye-btn");
    toggleBtn.addEventListener("click", () => {
      const isPass = input.type === "password";
      input.type = isPass ? "text" : "password";
    });
  </script>
</body>
</html>`;

  return new NextResponse(html, {
    status: options?.error ? 401 : 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store, no-cache, must-revalidate",
    },
  });
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
