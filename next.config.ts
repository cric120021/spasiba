import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["yt-search", "cheerio", "@distube/ytdl-core", "youtubei.js"],
  images: {
    qualities: [70, 75],
    remotePatterns: [
      {
        protocol: "https",
        hostname: "i.ytimg.com"
      },
      {
        protocol: "https",
        hostname: "img.youtube.com"
      },
      {
        protocol: "https",
        hostname: "i9.ytimg.com"
      }
    ]
  }
};

export default nextConfig;
