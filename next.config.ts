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
        destination: "https://github.com/cric120021/spasiba/releases/download/v1.0.0/spasiba.apk",
        permanent: false
      },
      {
        source: "/api/download",
        destination: "https://github.com/cric120021/spasiba/releases/download/v1.0.0/spasiba.apk",
        permanent: false
      }
    ];
  }
};

export default nextConfig;
