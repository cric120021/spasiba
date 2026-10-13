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
  },
  async headers() {
    return [
      {
        source: "/spasiba.apk",
        headers: [
          {
            key: "Content-Type",
            value: "application/vnd.android.package-archive"
          },
          {
            key: "Content-Disposition",
            value: 'attachment; filename="spasiba.apk"'
          }
        ]
      }
    ];
  },
  async redirects() {
    return [
      {
        source: "/download",
        destination: "/spasiba.apk",
        permanent: false
      },
      {
        source: "/api/download",
        destination: "/spasiba.apk",
        permanent: false
      }
    ];
  }
};

export default nextConfig;
