import { NextResponse } from "next/server";
import { verifyPasscode } from "@/lib/passcode";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  try {
    const body = await request.json().catch(() => ({}));
    const passcode = typeof body?.passcode === "string" ? body.passcode : "";

    const verification = await verifyPasscode(passcode);

    if (!verification.valid) {
      return NextResponse.json(
        { success: false, error: verification.error || "Incorrect passcode" },
        { status: 401 }
      );
    }

    const downloadUrl = `/download?code=${encodeURIComponent(passcode.trim())}`;

    return NextResponse.json({
      success: true,
      downloadUrl,
    });
  } catch (error) {
    console.error("[Download Verify API] Error:", error);
    return NextResponse.json(
      { success: false, error: "Internal server error verifying passcode" },
      { status: 500 }
    );
  }
}

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const code = searchParams.get("code") || searchParams.get("passcode") || "";

  const verification = await verifyPasscode(code);

  return NextResponse.json(
    {
      success: verification.valid,
      error: verification.error,
    },
    { status: verification.valid ? 200 : 401 }
  );
}
