# Spasiba UI / UX Handbook

This document explains the current user interface and user experience of Spasiba as it exists in this workspace today.

It is intentionally product-facing rather than backend-facing.

Use this file when you want to understand:

- what the app feels like
- how screens are structured
- how navigation works
- how playback and queueing are presented
- how the visual system stays consistent across pages
- which interaction rules are deliberate and should be preserved

The technical architecture still lives in [README.md](./README.md). This document is the companion handbook for the experience layer.

## 1. Product Intent

Spasiba is designed to feel like a real music app first and a website second.

The UI is built around a few consistent goals:

- mobile-first before desktop-first
- music-first before chrome-first
- fast action before deep configuration
- dark, immersive surfaces over browser-like white layouts
- persistent playback as the emotional center of the product
- minimal friction between seeing a track and hearing it

The app should feel:

- immediate
- moody
- personal
- low-clutter
- app-like in PWA form

## 2. Current UX Principles

### 2.1 One-hand-friendly primary actions

Most important actions are designed for thumb reach:

- play
- pause
- expand player
- open search
- open profile
- add to playlist
- reorder queue

The player lives at the bottom because playback is the persistent action surface.

### 2.2 Browse first, details second

Most surfaces prioritize recognizable artwork, song title, and a single fast action. The user is encouraged to start playback quickly instead of reading dense metadata.

### 2.3 Search should feel alive, not empty

The search surface prefers:

- history
- local suggestions
- cached search results
- live search only when needed

This is both a performance choice and a UX choice. The user should not feel punished for repeating a search.

### 2.4 The app shell should disappear

The shell exists to support the music experience, not compete with it.

That is why the app uses:

- black first paint
- dark full-screen shell
- translucent panels instead of boxed page layouts
- minimal browser-like framing

### 2.5 Queue joy matters

The queue is not hidden deep in a settings-like layer. It is part of the expanded player and treated as a living continuation of what the user is already hearing.

## 3. Visual System

### 3.1 Color direction

The default visual mood is dark, glossy, slightly cinematic.

Main traits:

- black or near-black foundations
- soft blue/teal atmospheric accents
- white text with muted slate secondary text
- category-specific accent backdrops on mood pages
- glass-panel surfaces for information blocks

The shell lives in [app/globals.css](./app/globals.css).

Important characteristics:

- full dark color scheme
- fixed `#app` viewport shell
- hidden browser-style overflow outside the app container
- safe-area-aware spacing for notches and home indicators
- category backdrop overlays that fade as the user scrolls

### 3.2 Shape language

Spasiba uses rounded geometry almost everywhere:

- rounded chips
- rounded quick-pick rows
- rounded card artwork
- rounded modal sheets
- rounded player controls

The app favors soft corners over sharp-edged utility styling.

### 3.3 Typography

The type hierarchy is simple and music-app oriented:

- large bold titles for current context
- compact uppercase eyebrow labels for state and mood
- smaller muted metadata for authors, durations, and counts

The visual tone is modern, soft, and slightly premium rather than technical.

### 3.4 Surface hierarchy

There are three main surface strengths:

- background shell
- glass panel
- strong glass panel / player shell

This helps the app separate:

- page background
- content modules
- persistent playback controls

## 4. App Shell and Navigation

The shell is defined mainly by:

- [app/layout.tsx](/Users/jigar/Desktop/Music%20App/app/layout.tsx)
- [components/PwaBootstrap.tsx](/Users/jigar/Desktop/Music%20App/components/PwaBootstrap.tsx)
- [components/SearchBar.tsx](/Users/jigar/Desktop/Music%20App/components/SearchBar.tsx)
- [components/Player.tsx](/Users/jigar/Desktop/Music%20App/components/Player.tsx)

### 4.1 What is always present

Across the app, the user is usually living inside four persistent layers:

1. splash / PWA boot shell
2. header with search and navigation chips
3. scrollable page content
4. bottom player dock

This gives Spasiba a strong "always-on" feeling.

### 4.2 Search bar as top navigation

The top header is not just a search input. It is the main navigation control surface.

It includes:

- brand presence
- search input
- profile entry
- category chips on home and category pages
- suggestions and history dropdown
- voice search trigger

### 4.3 Back behavior

The app is designed so search behaves like a real route. Entering search should produce actual route history, so back returns the user to the previous surface instead of exiting the app.

### 4.4 PWA behavior

The app shell is explicitly tuned for installed-PWA feel:

- one-session splash
- safe-area handling
- black theme color
- service worker registration
- dark first paint

## 5. Home Experience

Spasiba currently supports two home variants:

- `classic`
- `discovery`

The current stable default is `classic`, controlled in [lib/ui-config.ts](/Users/jigar/Desktop/Music%20App/lib/ui-config.ts).

### 5.1 Classic home

Defined mainly in [components/HomeClassic.tsx](/Users/jigar/Desktop/Music%20App/components/HomeClassic.tsx).

Classic home is organized like this:

1. hero area
2. Quick picks
3. Mixed for you
4. Library section
5. additional rails

### Quick picks

Quick picks are the fastest-start surface on the home page.

They are designed to be:

- denser than normal song cards
- more list-like
- optimized for immediate tap-to-play
- horizontally browsable in grouped columns

Each quick-pick row contains:

- artwork
- title
- artist + compact view count
- three-dot action menu

The row itself is the play target. The menu is secondary.

### Play all treatment

The `Play all` control in quick picks is intentionally small and chip-like. It should support the section, not dominate it.

### Mixed for you

This section is a more visual, cover-forward rail.

It gives the user:

- larger artwork
- lighter metadata
- a less dense browsing pace

### Library on home

The home page includes library access so personal collections stay near discovery, instead of feeling like a separate utility tab.

### 5.2 Discovery home

Defined mainly in [components/HomeDiscovery.tsx](/Users/jigar/Desktop/Music%20App/components/HomeDiscovery.tsx).

Discovery home is more editorial and mood-led.

It uses:

- a large visual hero
- a switchable section strip
- themed overlays by category
- a stronger "featured mood" presentation

This version is visually bolder and more magazine-like, but it is currently behind the home UI variant flag rather than the default.

## 6. Category Pages

Defined by:

- [app/category/[categoryId]/page.tsx](/Users/jigar/Desktop/Music%20App/app/category/[categoryId]/page.tsx)
- [components/CategoryFeedPage.tsx](/Users/jigar/Desktop/Music%20App/components/CategoryFeedPage.tsx)
- [lib/home-category-chips.ts](/Users/jigar/Desktop/Music%20App/lib/home-category-chips.ts)

Category pages are not just filtered grids. They are mood-specific landing pages.

Each category page uses:

- a backdrop image
- accent tinting
- a hero section
- one or more content rail layouts

### Supported section layouts

The category feed can render sections in different visual modes:

- quick-picks rows
- mixed card rail
- standard artwork rail

This keeps category pages from feeling repetitive even when the content comes from a shared feed engine.

### Scroll behavior

The category header and backdrop visually collapse as the user scrolls. The goal is to start immersive and become practical.

The page begins atmospheric, then gradually turns into a more grounded scrolling feed.

## 7. Search Experience

Defined by:

- [components/SearchBar.tsx](/Users/jigar/Desktop/Music%20App/components/SearchBar.tsx)
- [components/SearchResults.tsx](/Users/jigar/Desktop/Music%20App/components/SearchResults.tsx)

Search is one of the most important UX systems in Spasiba.

### 7.1 Search entry

The search bar is globally visible and should feel instantly available from every major surface.

### 7.2 Search states

The search experience has several layers:

- no query state
- history state
- suggestion state
- loading state
- results state
- error state

The app tries to avoid dead air between these states.

### 7.3 Search history

History is not hidden in settings. It is shown directly under the search field when appropriate.

Current behavior includes:

- recent searches visible in the suggestion panel
- swipe-to-delete on history items
- re-tap to rerun the same query

### 7.4 Suggestions

Suggestions are built to feel helpful without being noisy.

The app prefers:

- local exact-ish matches
- cached result reuse
- network suggestions only when local confidence is weak

### 7.5 Voice search

The search bar supports voice search when speech recognition is available.

Voice UX includes:

- a pending state
- a listening state
- a searching-and-playing state
- a dedicated overlay so the user knows the app is actively doing something

### 7.6 Search results page

The search results page is intentionally simple:

- clear header
- result count
- single `Results` grid
- direct play on tap

It avoids clutter because the main job is to help the user start playback fast.

## 8. Library and Playlist UX

Defined mainly by:

- [components/LibrarySection.tsx](/Users/jigar/Desktop/Music%20App/components/LibrarySection.tsx)
- [components/PlaylistDetailView.tsx](/Users/jigar/Desktop/Music%20App/components/PlaylistDetailView.tsx)
- [components/LibraryPlaylistPageClient.tsx](/Users/jigar/Desktop/Music%20App/components/LibraryPlaylistPageClient.tsx)

### 8.1 Library goals

The library is designed to make personal ownership feel light, not heavy.

It supports:

- owned playlists
- shared playlists
- create / rename / delete
- sharing by username and code
- opening a playlist directly into playback

### 8.2 Playlist cards

Playlist cards communicate:

- cover image
- playlist name
- ownership or sharing context
- tap target for opening
- menu for management

### 8.3 Modal behavior

Library actions are sheet-like overlays rather than route changes.

Important UX goals:

- keep the user near the current context
- allow scrolling inside the modal
- respect mobile safe areas
- avoid clipped top and bottom content

### 8.4 Share access

The sharing flow is designed around simplicity:

- share code
- share link
- copy code
- grant access by username
- revoke access

This gives both lightweight and controlled sharing paths.

### 8.5 Playlist detail

The playlist detail page is intentionally calm and utility-forward:

- hero card with cover and title
- `Play playlist` CTA
- track count chip
- track list below

Track list rows are action-friendly and mobile-aware.

Owned playlist rows support swipe actions for removal.

## 9. Shared Playlist UX

Defined mainly by [components/SharedPlaylistView.tsx](/Users/jigar/Desktop/Music%20App/components/SharedPlaylistView.tsx).

Shared playlists are designed to feel trustworthy and lightweight.

The user should immediately understand:

- this playlist was shared with them
- who shared it when available
- whether it is already in their shared library
- that they can start playback immediately

The screen therefore emphasizes:

- shared context
- clear play CTA
- direct track list

## 10. Profile UX

Defined mainly by:

- [components/ProfileDashboard.tsx](/Users/jigar/Desktop/Music%20App/components/ProfileDashboard.tsx)
- [components/ProfileCollectionPage.tsx](/Users/jigar/Desktop/Music%20App/components/ProfileCollectionPage.tsx)

Profile is designed as a lightweight identity + listening-memory page.

It is not meant to feel like a settings labyrinth.

### 10.1 What profile surfaces

- name
- username
- profile image
- monthly listening preview
- liked songs preview
- clear history / clear suggestions actions

### 10.2 Preview model

The dashboard uses previews rather than giant lists so the main profile page stays scannable.

Full collections live on their own pages:

- `/profile/history`
- `/profile/liked`

### 10.3 Tone

The profile copy is soft and personal rather than administrative.

The intent is:

- this is your music memory
- not your account settings spreadsheet

## 11. Player UX

Defined by [components/Player.tsx](/Users/jigar/Desktop/Music%20App/components/Player.tsx).

The player is the core UX surface in Spasiba.

It has two main states:

- minimized dock
- expanded player

And two playback modes inside expanded:

- audio mode
- video mode

### 11.1 Minimized player

The minimized player is always bottom-docked and full-bleed.

Its job is to provide:

- current song context
- immediate play/pause
- previous / next
- open player
- track menu

It should feel stable and anchored, not like a floating widget.

### 11.2 Expanded player

The expanded player is a full-screen sheet with:

- top control row
- artwork or video surface
- title and metadata
- progress bar and time
- transport controls
- volume
- queue section

The queue is part of the same sheet and must remain scrollable.

### 11.3 Audio / video mode switch

The expanded player lets the user switch between:

- pure artwork-driven audio mode
- visible YouTube video mode

Key UX rule:

- the seeker and timer must stay in sync with visible video while in video mode

### Audio mode

Audio mode is more atmospheric:

- large artwork
- softer visual halo
- cleaner visual focus on music rather than motion

### Video mode

Video mode gives the visible YouTube player for users who want the video experience.

This is treated as a mode inside the player, not as a separate page.

### 11.4 Queue UX

Queue UX is designed to balance simplicity and control.

The user can:

- see upcoming tracks
- tap to jump
- drag to reorder on desktop
- use up/down controls to reorder on mobile

Playlist-origin queues also expose mix on/off control.

### 11.5 Track actions

Track actions are kept consistent across surfaces with the same three-dot grammar:

- share song
- add to playlist
- add next in queue

This consistency matters because the user sees tracks in many contexts:

- quick picks
- grids
- player menu
- playlist rows

## 12. Reusable Interaction Grammar

Several patterns repeat across the app on purpose.

### 12.1 Tap the surface to play

The content card or row is usually the primary play target.

### 12.2 Three dots for secondary actions

The three-dot action button is the standard place for non-primary actions.

### 12.3 Chips for mode and state

Chips are used for:

- category switching
- lightweight status
- counts
- source labels
- toggles like playlist mix

### 12.4 Large artwork for emotional context

Artwork is not decorative only. It carries mood, recognizability, and scanning speed.

### 12.5 Immediate toast feedback

Quick actions such as sharing, removing, or saving often respond with a toast instead of a disruptive confirmation flow.

## 13. Loading, Empty, and Error States

The app generally favors calm, low-drama states:

- skeletons for search and feeds
- quiet empty cards for warming or missing data
- direct sentence-level error messages instead of giant failure pages

Examples:

- "Music is loading..."
- "This playlist does not have any songs yet."
- "Start typing in the search bar..."

The tone stays conversational and non-technical.

## 14. Mobile and PWA Details

The UX is strongly shaped by PWA constraints and mobile ergonomics.

Important behaviors include:

- safe-area aware padding
- hidden browser-like overflow outside the app shell
- bottom player dock that visually fills the lower edge
- modals and sheets that scroll on small viewports
- dark launch and splash behavior for installed app feel

## 15. What Should Not Be Broken Accidentally

These are current UX invariants worth protecting:

- the app should launch dark with no white flash
- the bottom player should feel persistent and flush with the bottom edge
- search should not feel empty on repeated queries
- quick picks should stay dense and fast to use
- category pages should feel immersive at the top and practical while scrolling
- the queue must remain scrollable in the expanded player
- video mode should keep the seeker and timer synced to the visible video
- library modals must scroll fully on mobile
- shared playlist access should stay simple enough for non-technical users

## 16. Current UI File Map

### Shell

- [app/layout.tsx](/Users/jigar/Desktop/Music%20App/app/layout.tsx)
- [app/globals.css](/Users/jigar/Desktop/Music%20App/app/globals.css)
- [components/PwaBootstrap.tsx](/Users/jigar/Desktop/Music%20App/components/PwaBootstrap.tsx)
- [components/PlayerBootstrap.tsx](/Users/jigar/Desktop/Music%20App/components/PlayerBootstrap.tsx)

### Navigation and search

- [components/SearchBar.tsx](/Users/jigar/Desktop/Music%20App/components/SearchBar.tsx)
- [components/SearchResults.tsx](/Users/jigar/Desktop/Music%20App/components/SearchResults.tsx)

### Home and discovery

- [components/HomeClassic.tsx](/Users/jigar/Desktop/Music%20App/components/HomeClassic.tsx)
- [components/HomeDiscovery.tsx](/Users/jigar/Desktop/Music%20App/components/HomeDiscovery.tsx)
- [components/CategoryFeedPage.tsx](/Users/jigar/Desktop/Music%20App/components/CategoryFeedPage.tsx)

### Content presentation

- [components/SongGrid.tsx](/Users/jigar/Desktop/Music%20App/components/SongGrid.tsx)
- [components/QuickPickRow.tsx](/Users/jigar/Desktop/Music%20App/components/QuickPickRow.tsx)
- [components/TrackActionsMenu.tsx](/Users/jigar/Desktop/Music%20App/components/TrackActionsMenu.tsx)

### Library and playlists

- [components/LibrarySection.tsx](/Users/jigar/Desktop/Music%20App/components/LibrarySection.tsx)
- [components/PlaylistDetailView.tsx](/Users/jigar/Desktop/Music%20App/components/PlaylistDetailView.tsx)
- [components/LibraryPlaylistPageClient.tsx](/Users/jigar/Desktop/Music%20App/components/LibraryPlaylistPageClient.tsx)
- [components/SharedPlaylistView.tsx](/Users/jigar/Desktop/Music%20App/components/SharedPlaylistView.tsx)

### Profile and playback

- [components/ProfileDashboard.tsx](/Users/jigar/Desktop/Music%20App/components/ProfileDashboard.tsx)
- [components/ProfileCollectionPage.tsx](/Users/jigar/Desktop/Music%20App/components/ProfileCollectionPage.tsx)
- [components/Player.tsx](/Users/jigar/Desktop/Music%20App/components/Player.tsx)

## 17. Summary

Spasiba's current UI is built around one core promise:

the app should feel like a music space, not a webpage with music inside it.

That is why the UX keeps returning to the same priorities:

- immersive dark shell
- fast search
- immediate playback
- dense quick picks
- gentle but useful library tools
- a player that behaves like the center of the app

If future work changes the interface, this is the benchmark it should still preserve.
