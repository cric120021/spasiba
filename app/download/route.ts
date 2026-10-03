import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const APK_CDN_URL = "https://github.com/Jigar-23/test1/releases/download/v1.0.0/spasiba.apk";

export async function GET() {
  return NextResponse.redirect(APK_CDN_URL, 307);
}
