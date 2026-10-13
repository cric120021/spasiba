import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const downloadUrl = new URL("/spasiba.apk", request.url);
  return NextResponse.redirect(downloadUrl, 307);
}
