import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const ANDROID_APK_DOWNLOAD_URL =
  "https://github.com/cric120021/spasiba/releases/download/v1.0.0/spasiba.apk";

export async function GET() {
  return NextResponse.redirect(ANDROID_APK_DOWNLOAD_URL, 307);
}
