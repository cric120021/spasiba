import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { Suspense } from "react";
import Script from "next/script";

import InstallPrompt from "@/components/InstallPrompt";
import Player from "@/components/Player";
import PlayerBootstrap from "@/components/PlayerBootstrap";
import PwaBootstrap from "@/components/PwaBootstrap";
import SearchBar from "@/components/SearchBar";
import SessionDataBootstrap from "@/components/SessionDataBootstrap";

import "@/app/globals.css";

export const metadata: Metadata = {
  applicationName: "Spasiba",
  title: "Spasiba",
  description: "Simple streaming music web app powered by YouTube audio streams.",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "Spasiba"
  },
  icons: {
    icon: [
      {
        url: "/icon.svg",
        type: "image/svg+xml"
      },
      {
        url: "/icon",
        sizes: "512x512",
        type: "image/png"
      }
    ],
    apple: [
      {
        url: "/apple-icon",
        sizes: "180x180",
        type: "image/png"
      }
    ]
  },
  other: {
    "mobile-web-app-capable": "yes"
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#000000"
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en" data-ui-mode="night" style={{ background: "#000000", colorScheme: "dark" }}>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
        <meta name="theme-color" content="#000000" />
        <meta name="theme-color" content="#000000" media="(prefers-color-scheme: dark)" />
        <meta name="theme-color" content="#000000" media="(prefers-color-scheme: light)" />
        <meta name="color-scheme" content="dark" />
        <meta name="msapplication-navbutton-color" content="#000000" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <style>{`html,body{margin:0;background:#000!important;color:#f8fafc;color-scheme:dark}#initial-loader{position:fixed;inset:0;background:#000;z-index:100}html.session-splash-seen #initial-loader{opacity:0;pointer-events:none}`}</style>
        <link rel="dns-prefetch" href="//i.ytimg.com" />
        <link rel="dns-prefetch" href="//www.youtube.com" />
        <link rel="preconnect" href="https://i.ytimg.com" crossOrigin="" />
        <link rel="preconnect" href="https://www.youtube.com" crossOrigin="" />
        <link rel="preload" href="/pwa-shell.css" as="style" />
        <link rel="stylesheet" href="/pwa-shell.css" />
        <link rel="preload" href="/pwa-shell.js" as="script" />
        <link rel="apple-touch-startup-image" href="/apple-splash.svg" />
      </head>
      <body suppressHydrationWarning style={{ background: "#000000", color: "#f8fafc", colorScheme: "dark" }}>
        <Script
          id="spasiba-splash-session-guard"
          strategy="beforeInteractive"
        >{`try{if(window.sessionStorage.getItem("spasiba_splash_seen_v1")==="1"){document.documentElement.classList.add("session-splash-seen");}}catch(_error){}`}</Script>
        <Script src="/pwa-shell.js" strategy="beforeInteractive" />
        <div id="initial-loader" aria-hidden="true">
          <div className="initial-splash">
            <div className="initial-splash__halo" />
            <div className="initial-splash__brand">
              <div className="initial-splash__badge">SP</div>
              <div className="initial-splash__copy">
                <p>Music</p>
                <h1>Spasiba</h1>
              </div>
            </div>
            <div className="initial-splash__divider" />
            <p className="initial-splash__tagline">
              <span>Simple</span>
              <span className="initial-splash__tag-separator" aria-hidden="true">
                •
              </span>
              <span>Classy</span>
              <span className="initial-splash__tag-separator" aria-hidden="true">
                •
              </span>
              <span>Yours</span>
            </p>
          </div>
        </div>
        <div id="app">
          <PwaBootstrap />
          <PlayerBootstrap />
          <SessionDataBootstrap />
          <div id="app-scroll" className="app-scroll">
            <Suspense
              fallback={
                <div className="app-header sticky top-0 z-40 h-[116px] border-b border-white/8 bg-[#050816]/72 shadow-[0_18px_60px_rgba(0,0,0,0.42)] sm:h-[76px]" />
              }
            >
              <SearchBar />
            </Suspense>
            <main id="app-main" className="app-main mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">
              {children}
            </main>
          </div>
          <Player />
          <InstallPrompt />
        </div>
      </body>
    </html>
  );
}
