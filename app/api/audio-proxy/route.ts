import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const targetUrl = request.nextUrl.searchParams.get("url")?.trim();

  if (!targetUrl) {
    return new NextResponse("Missing url parameter", { status: 400 });
  }

  const range = request.headers.get("range");

  const headers: Record<string, string> = {
    "User-Agent":
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
    Accept: "*/*"
  };

  if (range) {
    headers["Range"] = range;
  }

  try {
    const upstream = await fetch(targetUrl, {
      headers,
      redirect: "follow"
    });

    if (!upstream.ok && upstream.status !== 206) {
      return new NextResponse(`Upstream failed: ${upstream.status}`, { status: upstream.status });
    }

    const responseHeaders = new Headers();
    const contentType = upstream.headers.get("content-type") || "audio/mp4";
    const contentLength = upstream.headers.get("content-length");
    const contentRange = upstream.headers.get("content-range");
    const acceptRanges = upstream.headers.get("accept-ranges") || "bytes";

    responseHeaders.set("Content-Type", contentType);
    responseHeaders.set("Accept-Ranges", acceptRanges);
    responseHeaders.set("Cache-Control", "public, max-age=3600, s-maxage=3600");

    if (contentLength) responseHeaders.set("Content-Length", contentLength);
    if (contentRange) responseHeaders.set("Content-Range", contentRange);

    return new NextResponse(upstream.body, {
      status: upstream.status === 206 ? 206 : 200,
      headers: responseHeaders
    });
  } catch (error) {
    return new NextResponse(error instanceof Error ? error.message : "Proxy failed", { status: 500 });
  }
}
