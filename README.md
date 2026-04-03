# Spasiba

Spasiba is a fullscreen, mobile-first music web app / PWA built with Next.js.
It is designed to feel closer to a real music app than a typical website while still staying fast on the web stack.

The app is intentionally built around a simple product idea:

- search fast
- play instantly
- keep the player elegant
- make playlists easy
- let YouTube drive discovery where YouTube is strongest
- add only a light layer of Spasiba intelligence on top

This README is a living technical handbook for the current app state in this workspace.
It is meant to explain not just what exists, but why it was built this way and what logic the app currently uses.

---

## 1. Product Goals

The app is trying to solve these user experience goals:

1. feel like a native music app on phone
2. keep search immediate and uncluttered
3. let the player stay smooth even when the queue is changing
4. make playlists persistent and shareable
5. keep queue quality close to YouTube Music instead of random YouTube search junk
6. add lightweight personalization without slowing playback

The design philosophy is:

- YouTube is the content source
- Spasiba is the experience layer
- speed is more important than clever-but-slow logic
- queue quality matters more than “AI for the sake of AI”

---

## 2. Current Feature Set

### Playback

- sticky mini player
- fullscreen expanded player
- audio / video mode switch
- seek bar
- previous / next
- queue view
- volume control
- queue reorder support
- toast feedback for actions
- media session integration for mobile lock screen where the browser allows it

### Search

- search suggestions dropdown
- no forced navigation while typing
- results open only when the user taps a suggestion or presses search
- result cards support play, queue, playlist actions

### Discovery

- category-based home rails
- horizontal scrolling rails instead of tall vertical stacks
- larger category pools instead of tiny one-screen shelves
- discovery filtering to avoid playlist dumps, mashups, reels, interviews, and generic junk

### Playlists

- create playlist
- rename playlist
- delete playlist
- add song to playlist from cards
- add song to playlist from player menus
- playlist detail page
- playlist playback with `Mix on / Mix off`

### Shared Playlists

- share by link
- share by code
- share by username grants
- multiple people can be granted access to the same playlist
- owner can revoke one user or revoke all sharing
- followers see live updates when the owner adds more songs

### Profile

- display name
- username
- username copy button
- profile photo add / replace / delete
- monthly listening history
- liked songs
- clear viewed history
- clear suggestion data

### PWA / App Feel

- standalone installable app
- manifest + theme color + safe areas
- black first paint
- static branded splash screen
- install prompt
- service worker shell caching

---

## 3. Tech Stack

### Frontend

- Next.js App Router
- React
- Tailwind CSS v4
- client-side external player store

### Backend / Data

- Next.js route handlers
- Turso / libSQL

### YouTube / Discovery / Streams

- `yt-search` for broad search candidate collection
- `youtubei.js` for YouTube / YouTube Music structured metadata and related rails
- `@distube/ytdl-core` as a final stream fallback
- `yt-dlp` as the first direct stream resolver on the server when possible

### Deployment

- Vercel

---

## 4. High-Level Architecture

The app is split into 4 main layers:

### 4.1 UI shell layer

Responsible for:

- layout
- sticky header
- splash
- install prompt
- safe areas
- player mount

Main files:

- `app/layout.tsx`
- `app/globals.css`
- `components/PwaBootstrap.tsx`
- `components/InstallPrompt.tsx`

### 4.2 Player + queue layer

Responsible for:

- current track
- queue
- history
- seeking
- loading
- background behavior
- queue refill
- recommendation ranking

Main files:

- `lib/audio-controller.ts`
- `store/playerStore.ts`
- `components/Player.tsx`
- `components/PlayerBootstrap.tsx`
- `lib/youtube-iframe.ts`

### 4.3 Content / YouTube data layer

Responsible for:

- search
- related songs
- category rails
- trending sections
- stream resolution

Main files:

- `lib/youtube.ts`
- `app/api/search/route.ts`
- `app/api/stream/route.ts`

### 4.4 User data layer

Responsible for:

- playlists
- likes
- profile
- shared playlist access
- engagement events
- listener profile

Main files:

- `lib/db.ts`
- `lib/playlists.ts`
- `lib/profile.ts`
- `lib/likes.ts`
- `lib/engagement.ts`
- `lib/listener-profile.ts`

---

## 5. Page Structure

### Home

File:

- `app/page.tsx`

Responsibilities:

- hero panel
- discovery category rails
- personal library preview rails

### Search

File:

- `app/search/page.tsx`

Responsibilities:

- result list
- result-card playback
- queue / playlist actions

### Library

Files:

- `app/library/page.tsx`
- `app/library/[playlistId]/page.tsx`

Responsibilities:

- personal playlists
- shared playlists
- playlist detail playback

### Profile

Files:

- `app/profile/page.tsx`
- `app/profile/history/page.tsx`
- `app/profile/liked/page.tsx`

Responsibilities:

- profile basics
- username
- profile photo
- quick previews
- full history
- full liked songs

### Shared playlist entry page

File:

- `app/shared/[shareToken]/page.tsx`

Responsibilities:

- open public shared links
- save shared playlist into the recipient’s library

---

## 6. Search Logic

### User experience decision

Search was intentionally changed away from “navigate while typing”.

Current behavior:

- typing keeps the keyboard open
- suggestions appear in a dropdown
- the results page opens only on:
  - tapping a suggestion
  - pressing search

Why:

- this is much closer to Spotify / YouTube search UX
- mobile typing stays stable
- the keyboard does not collapse unexpectedly

### Search backend

Route:

- `app/api/search/route.ts`

The route supports two modes:

1. normal query search via `q`
2. related-song search via `relatedTo`

Important search parameters:

- `limit`
- `pages`
- `category`

Current caps:

- normal visible search usually uses small result counts
- fallback recommendation search can request much larger banks
- route limit is clamped to `100`
- pages are clamped to `5`

### Search data sources

Primary search source:

- `yt-search`

Additional structured source for related rails:

- `youtubei.js`

---

## 7. Discovery Rails

Main file:

- `lib/youtube.ts`

Discovery rails are not simple one-query shelves anymore.
Each category now uses a stronger query bank plus heavy filtering.

Current categories:

- Hindi
- English (Global / Hollywood)
- Punjabi
- Dogri
- Lofi / Chill
- Love / Romantic
- Sad / Emotional
- High Energy / Workout
- Trending / Viral

### Why `Hindi` instead of `Bollywood`

The app uses `Hindi` as the category label because:

- it is broader than only film soundtracks
- it includes both mainstream film and non-film Hindi tracks
- it is clearer for discovery than a narrower `Bollywood` label

### Discovery filtering

Discovery rails reject noisy candidates like:

- mashups
- mixes
- jukeboxes
- playlist videos
- interview clips
- reels / shorts / status edits
- top-list dump videos
- “new english songs list” style compilation spam
- lyric / slowed / remix variants when they overwhelm the rail

### Discovery diversity

Rails also cap repeated artists so a single artist does not dominate a whole category row.

---

## 8. Playback Engine

Main file:

- `lib/audio-controller.ts`

The playback system has two engines:

1. direct audio engine using `HTMLAudioElement`
2. hidden YouTube iframe engine as a fallback path

### Why two engines exist

Direct audio is better when available because it gives:

- smoother local control
- real audio element timing
- better chance at background continuity

The hidden YouTube engine exists because:

- direct stream resolution is not always possible
- hosted environments can be blocked by YouTube
- some browsers/platforms behave better with the embedded path in specific cases

### Stream resolution order

Inside `lib/youtube.ts`, direct stream resolution currently tries:

1. `yt-dlp`
2. `youtubei.js`
3. `@distube/ytdl-core`

That order exists because:

- `yt-dlp` is strongest when it works
- `youtubei.js` can expose structured streaming data
- `ytdl-core` acts as a final fallback

### Cached playback behavior

The player uses:

- stream cache
- pending stream request dedupe
- blob cache
- preloaded audio cache
- related queue cache

This is done to avoid repeating expensive work when:

- a user replays a track
- a queue refill asks for the same seed again
- the player is skipping rapidly

### Player state store

Global player state lives in:

- `store/playerStore.ts`

It stores:

- `currentSong`
- `queue`
- `currentIndex`
- `sessionHistory`
- `queueSource`
- `queueSourceLabel`
- `playlistBaseTrackIds`
- `playlistMixEnabled`
- playback timing
- loading/error state
- volume
- toast state

Important design choice:

- the playback queue and the played-session history are separate concerns
- the queue is the “current playable line”
- `sessionHistory` is the session memory for tracks the user already played

This separation was added so previous / next logic can stay sane while the recommendation tail keeps changing.

---

## 9. Queue / Recommendation Logic

This is one of the most important parts of the app.

Main logic file:

- `lib/audio-controller.ts`

### Core queue philosophy

Spasiba does **not** try to fully replace YouTube Music.

Current strategy:

1. let YouTube Music / YouTube related provide the strongest candidates
2. filter junk aggressively
3. lightly rerank for quality and user taste
4. inject only occasional Spasiba suggestions when YouTube needs help

This was a deliberate correction after earlier experiments became too aggressive and started producing bad queues.

### Current queue source priority

The queue now aims to use:

1. YouTube Music `Up Next / automix`
2. YouTube related rails
3. Spasiba fallback search / rerank

### Why the app does not fully “invent” the queue

We tried more aggressive local recommendation logic earlier.
That led to problems like:

- too many same-artist songs
- same-song clones
- mashups
- compilation junk
- weak cross-genre jumps

The current system is intentionally more conservative:

- YouTube dominates
- Spasiba trims and nudges

### Queue quality rules

The queue rejects candidates that look like:

- reels
- shorts
- status clips
- WhatsApp edits
- TikTok / CapCut edits
- interviews
- podcasts
- episode videos
- reactions
- reviews
- trailers
- news clips
- mashups
- mega mixes
- playlist dumps
- jukeboxes
- long loops
- extremely short clip-style tracks

### Duplicate / variant protection

The queue also tries to block:

- lyrics versions
- live versions
- slowed / reverb
- sped-up
- karaoke
- cover overload
- same-song near-clones

### Diversity controls

The queue applies caps like:

- artist repetition caps
- identity-key deduping
- recent-track diversity penalties

This exists specifically to stop cases like:

- `Shape of You`
- followed by five more `Shape of You` variants
- followed by all-Ed-Sheeran-only radio

### Queue refill behavior

Current target:

- keep around `10` upcoming songs ahead

When the queue gets shorter:

- the app refills from the tail
- refill is based mainly on the last queue context, not a full rebuild every time

This was added to stop the queue from collapsing after repeated `next` presses.

### Playlist mode vs normal mode

There are two queue sources:

- `default`
- `playlist`

In playlist mode:

- `Mix off` means stay playlist-only
- `Mix on` means extend the playlist tail with suggestions

This is tracked with:

- `queueSource`
- `playlistBaseTrackIds`
- `playlistMixEnabled`

### Personalization influence

User behavior does influence queue scoring, but lightly.

Current philosophy:

- user taste should adjust ordering
- it should not overpower the YouTube Music base rail

So the queue is:

- not raw YouTube only
- not raw Spasiba AI only
- a YouTube-first hybrid

---

## 10. Engagement Tracking and Listener Profile

Main files:

- `lib/engagement.ts`
- `lib/listener-profile.ts`
- `app/api/engagement/route.ts`
- `lib/audio-controller.ts`

### Tracked engagement events

The app currently records:

- `play_start`
- `skip_early`
- `skip_mid`
- `skip_late`
- `complete`
- `queue_add`
- `playlist_add`
- `like`
- `unlike`

### How the app uses this data

Engagement is used to build a compact listener profile with:

- artist preference weights
- track preference weights
- signal preference weights

Signals are things like:

- language lane
- mood lane
- category lane

### What the listener profile is used for

Right now the listener profile is used for:

- queue ranking boosts
- queue ranking penalties
- profile history views
- liked songs / preference surfaces

### What it is not yet doing

It is **not** yet:

- a fully separate recommendation engine
- collaborative filtering across multiple users
- a homepage “For You” system

So the current model is:

- YouTube gives candidates
- Spasiba taste data helps reorder them

### Write batching

Engagement writes are batched intentionally.

Why:

- avoids sending one network request for every tiny player event
- reduces DB chatter
- keeps playback hot path lighter

---

## 11. Playlist System

Main files:

- `lib/playlists.ts`
- `app/api/playlists/route.ts`
- `app/api/playlists/[playlistId]/route.ts`
- `app/api/playlists/[playlistId]/tracks/route.ts`

### Playlist capabilities

- create
- list
- rename
- delete
- add tracks
- play entire playlist
- playlist detail page

### Playlist card design

Playlist cards now show:

- playlist name
- track count
- cover image from one song inside the playlist
- 3-dot menu for share / edit / delete

### Cover image logic

Playlist cards do not fetch every track just to paint the list page.
The playlist layer loads:

- track count
- one cover track
- core metadata

This was optimized to reduce overfetching and make the library page lighter.

### Save-to-playlist flow

The add-to-playlist modal supports:

- showing existing playlists
- selecting one to add the current song
- creating a new playlist
- auto-adding the current song into the new playlist immediately

This flow exists from:

- song cards
- mini player track menu
- fullscreen player track menu

---

## 12. Shared Playlist Access Model

Main files:

- `lib/playlists.ts`
- `app/api/playlists/[playlistId]/share/route.ts`
- `app/api/shared/code/route.ts`
- `app/api/shared/[shareToken]/save/route.ts`
- `components/SharedPlaylistView.tsx`

### Why the model was changed

Originally, sharing could have been a simple public-link snapshot.
That was not good enough.

The current model is controlled access with sync:

- owner grants by username
- recipient can unlock by code or link
- recipient can save the shared playlist into their own library
- the saved shared playlist stays linked to the owner’s live playlist

### Important behavior

If the owner later adds 100 more songs:

- followers see the updated shared playlist

If the owner revokes a username:

- that playlist disappears from that user’s shared library section

### Why username grants were added

Guest-id-only sharing is not reliable enough across browser and installed PWA containers, especially on iPhone.

Username-based sharing was introduced because:

- it is much more human-friendly
- it is more stable than relying only on per-container guest ids
- it allows multi-user access control

### Current share flows

Owner can:

- open share access
- add usernames
- copy share link
- copy share code
- revoke one granted username
- fully revoke playlist sharing

Recipient can:

- open the link
- or enter the share code
- or save the shared playlist into the personal library section

---

## 13. Profile System

Main files:

- `components/ProfileDashboard.tsx`
- `lib/profile.ts`
- `lib/profile-client.ts`
- `app/api/profile/route.ts`

### What profile contains

- display name
- username
- profile photo
- monthly history
- liked songs

### Username behavior

The username is important because it is used for:

- easy human identity
- share access grants
- share-code collaboration

The UI includes:

- visible `@username`
- copy button for easy sharing

### Profile photo behavior

Photos are currently stored as a data URL in the DB.

Client-side photo flow:

1. user chooses an image
2. image is loaded in the browser
3. it is cropped to a square canvas
4. resized to `256x256`
5. exported as `webp`
6. stored in the profile

### Clear-data controls

The profile page supports:

- clear previously viewed songs
- clear suggestion data

These are intentionally separate because listening history and recommender memory are not always the same thing.

---

## 14. Likes

Main files:

- `lib/likes.ts`
- `app/api/likes/route.ts`
- `components/PlayerTrackMenu.tsx`

Likes are now explicit user actions, not only inferred behavior.

Places where like is exposed:

- mini player track menu
- fullscreen player track menu

Why this matters:

- “liked songs” becomes a real library surface
- not just an inferred recommendation score

---

## 15. Identity Model

Current identity model is guest-first.

### Storage keys

The app creates a long-lived visitor id and stores it in:

- `localStorage` key: `spasiba_guest_id`
- cookie name: `spasiba_guest_id`

### Current benefits

This works well for:

- same browser
- same device
- same installed PWA container

### Current limitation

This does **not** guarantee that browser and installed PWA always behave as the same identity container.

This is especially true on iPhone.

So the honest current state is:

- guest identity is stable per container
- it is not real account auth
- it is not device hardware identity
- it is not based on MAC address

### Why the app still uses it

Because it gives:

- zero-friction first use
- persistent local playlists / profile / queue taste within the same container

### What the future upgrade would be

Real account auth is still the clean future step for:

- true cross-device identity
- browser ↔ PWA identity continuity
- shared-link consistency

---

## 16. PWA / Native-Like Shell

Main files:

- `app/layout.tsx`
- `app/manifest.ts`
- `components/PwaBootstrap.tsx`
- `public/pwa-shell.css`
- `public/sw.js`

### What the shell does

- forces black first paint
- hides the white pre-render flash
- loads a branded static splash
- applies safe-area spacing
- registers the service worker
- sets theme color and standalone meta tags

### Current splash behavior

The splash is intentionally static now.

Why:

- earlier animated loading-style versions felt too “fake loading screen”
- most good app splash screens are more static and brand-led

Current splash characteristics:

- black background
- SP badge
- `Music`
- `Spasiba`
- tagline `Simple. Classy. Yours`
- minimum visible hold around `1100ms`

### Install behavior

The app supports install flow on:

- Android
- iPhone (Add to Home Screen path)

### Service worker role

The service worker mainly handles:

- app shell caching
- update path for the installed app

It is intentionally **not** used as an aggressive media downloader.

---

## 17. Background Playback Notes

### Android

Android is the friendlier platform in this stack.

Current practical behavior:

- background playback can work
- lock-screen behavior depends partly on Chrome / installed PWA runtime
- next / previous visibility is still partly browser-controlled

### iPhone

iPhone is much more restricted.

Current honest state:

- best-effort handling exists
- some background behaviors can work in favorable cases
- it is not guaranteed Apple Music style background control in a YouTube-backed PWA

### Why this limitation exists

The real limitation is not just our code.
It is the combination of:

- iOS web app restrictions
- Safari / home-screen app process behavior
- YouTube-backed playback constraints

---

## 18. Data Model

Main schema file:

- `lib/db.ts`

### Tables

#### `visitors`

Stores:

- guest id
- display name
- username
- profile image data URL
- created time

#### `playlists`

Stores:

- playlist id
- owner visitor id
- playlist name
- share token
- timestamps

#### `playlist_tracks`

Stores:

- track identity
- video metadata
- duration
- author
- url
- views

#### `engagement_events`

Stores:

- visitor id
- song metadata snapshot
- action
- progress
- signals
- created time

#### `listener_profiles`

Stores:

- compact profile JSON per visitor
- updated time

#### `liked_tracks`

Stores:

- visitor id
- song metadata
- created / updated times

#### `shared_playlist_library`

Stores:

- which visitor saved which shared playlist into their own library

#### `playlist_share_grants`

Stores:

- playlist id
- granted username
- owner id
- created time

### Indexing

The schema includes indexes for hot paths like:

- visitor playlist lookup
- recent playlist ordering
- playlist track fetch
- recent engagement lookup
- like lookup
- shared playlist lookup
- share grant lookup

These were added to reduce repeated heavy reads as the app grew.

---

## 19. API Route Reference

### Search / playback

- `GET /api/search`
  - normal search via `q`
  - related songs via `relatedTo`
- `GET /api/stream`
  - resolves direct audio stream candidates for a video

### Playlists

- `GET /api/playlists`
  - fetch personal playlists + shared library lists
- `POST /api/playlists`
  - create playlist
- `PATCH /api/playlists/[playlistId]`
  - rename playlist
- `DELETE /api/playlists/[playlistId]`
  - delete playlist
- `POST /api/playlists/[playlistId]/tracks`
  - add song to playlist
- `GET /api/playlists/[playlistId]/share`
  - load current sharing state
- `POST /api/playlists/[playlistId]/share`
  - grant access by username
- `DELETE /api/playlists/[playlistId]/share`
  - revoke one user or revoke all sharing

### Shared playlist entry

- `POST /api/shared/code`
  - unlock shared playlist by code
- `POST /api/shared/[shareToken]/save`
  - save shared playlist into recipient library

### Profile / personalization

- `GET /api/profile`
  - fetch profile
- `PATCH /api/profile`
  - update name / username / photo
- `DELETE /api/profile?scope=history`
  - clear listening history
- `DELETE /api/profile?scope=suggestions`
  - clear suggestion profile

### Behavior / likes

- `POST /api/engagement`
  - store engagement batch
- `GET /api/likes`
  - fetch liked songs
- `POST /api/likes`
  - like a song
- `DELETE /api/likes`
  - unlike a song

---

## 20. Main Client Components and Their Jobs

### `components/SearchBar.tsx`

- header shell
- search input
- suggestion dropdown
- profile / playlist entry buttons

### `components/SongCard.tsx`

- artwork card
- play button
- 3-dot menu
- add to queue
- add to playlist

### `components/SongGrid.tsx`

- horizontal rails
- card layout
- discovery rail presentation

### `components/Player.tsx`

- mini player
- expanded player
- queue list
- seek bar
- toast display
- playlist mix toggle

### `components/PlayerTrackMenu.tsx`

- like
- add to playlist

### `components/SaveToPlaylistButton.tsx`

- playlist modal
- existing playlist selection
- create new playlist + auto add current song

### `components/LibrarySection.tsx`

- playlist cards
- shared playlist cards
- playlist actions

### `components/ProfileDashboard.tsx`

- profile editing
- username copy
- photo management
- data clear actions
- quick previews of history / likes

---

## 21. Performance Work Already Done

The app has already had a substantial optimization pass.

### Caching

- search cache
- related queue cache
- stream cache
- pending request dedupe maps
- shared client caches for profile and playlists

### Write optimization

- engagement writes are batched
- pending engagement queue is bounded

### DB optimization

- indexes added for hot lookup paths
- lighter playlist card fetching

### UI optimization

- no-op guards in player store to avoid useless rerenders
- deferred / lighter below-the-fold rendering
- horizontal rails instead of tall heavy grids
- more appropriate image sizing for cards

### Queue optimization

- queue hot path uses cache and dedupe
- refill logic tries to extend instead of rebuilding everything
- YouTube-first queueing prevents overly expensive local recomputation

---

## 22. Important Design Decisions

### 22.1 Why YouTube should dominate the queue

YouTube Music’s own automix is usually better than ad hoc local search heuristics.

So the app deliberately moved to:

- YouTube as candidate source
- Spasiba as cleaner / rank adjuster

instead of:

- Spasiba trying to outsmart YouTube completely

### 22.2 Why not heavy ML in the hot path

Real-time playback should stay fast.

Heavy ML / LLM ranking on every play click would:

- slow startup
- add cost
- add complexity
- make the player feel worse

So the current design uses:

- simple cached scoring
- lightweight listener profile
- strict filtering

### 22.3 Why guest-first instead of forced login

Because frictionless entry matters.

The tradeoff is:

- faster onboarding now
- less identity reliability across containers

### 22.4 Why username-based sharing was added before full auth

Because it solves real collaboration pain without forcing a full auth migration immediately.

---

## 23. Known Honest Limitations

- YouTube remains the upstream source, so weak YouTube candidate sets can still happen.
- Direct audio resolution can be blocked in hosted environments.
- iPhone background playback is not fully reliable in a web-only YouTube stack.
- Browser and installed PWA storage are not guaranteed to match.
- Guest identity is not the same as real account auth.
- The app should not implement permanent offline downloading for YouTube tracks.

---

## 24. Local Setup

### Requirements

- Node.js 20+
- Turso database

### Environment

Create `.env.local`:

```env
TURSO_DATABASE_URL=libsql://your-db-url
TURSO_AUTH_TOKEN=your-token
```

### Install

```bash
npm install
```

### Run in development

```bash
npm run dev
```

### Production build

```bash
npm run build
```

---

## 25. Deployment

Spasiba is deployed on Vercel.

Typical production deploy:

```bash
vercel --prod --yes
```

The project is already linked to a Vercel project in this workspace.

### Beginner-friendly deploy and maintenance flow

If someone new wants to recreate the deploy flow from scratch, this is the easiest sequence.

#### First-time setup on a new machine

Install the Vercel CLI:

```bash
npm install -g vercel
```

Log in to Vercel:

```bash
vercel login
```

Link the local folder to the existing Vercel project:

```bash
vercel link
```

If environment variables are not already present on Vercel, add them:

```bash
vercel env add TURSO_DATABASE_URL production
vercel env add TURSO_AUTH_TOKEN production
vercel env add TURSO_DATABASE_URL preview
vercel env add TURSO_AUTH_TOKEN preview
```

#### Everyday update flow

Install dependencies if needed:

```bash
npm install
```

Run locally:

```bash
npm run dev
```

Build locally before deploying:

```bash
npm run build
```

Deploy production:

```bash
vercel --prod --yes
```

Inspect the live deploy if needed:

```bash
vercel inspect spasiba.vercel.app
```

#### Fast mental model for maintainers

- change code
- test locally with `npm run dev`
- run `npm run build`
- deploy with `vercel --prod --yes`
- reopen the installed PWA once if cached UI does not update immediately

#### Important reality about installed PWAs

After deploy:

- API changes are live immediately
- installed PWA UI changes usually appear on next refresh / reopen
- if an installed app still shows the old version, close it fully and reopen it
- in stubborn cases, remove and reinstall the PWA once

---

## 26. Rollback Checkpoints

This project now has a named baseline:

- `Stable State 1`

The intended Git checkpoint names are:

- branch: `stable-state-1`
- tag: `stable-state-1`

Useful commands:

See the checkpoint:

```bash
git tag
git branch
```

Create a temporary branch from the checkpoint:

```bash
git switch -c restore-stable-state-1 stable-state-1
```

Move the current branch back to the checkpoint only if you intentionally want a hard rollback:

```bash
git reset --hard stable-state-1
```

Safer recommendation:

- prefer creating a restore branch from the tag first
- verify locally with `npm run build`
- then deploy if that rollback is the version you want live

---

## 27. Recommended Next Major Steps

If the product keeps growing, the strongest next upgrades are:

1. real account auth with guest merge
2. more observability around queue quality and playback failures
3. richer per-user personalization without overpowering YouTube rails
4. more refined discovery query banks for every language / category
5. eventual native wrapper / APK path if deeper mobile behavior is required

---

## 28. Summary

Spasiba today is:

- a YouTube-backed music PWA
- with a strong native-like shell
- a hybrid queue engine
- persistent playlists
- controlled shared playlists
- profile + likes + history
- lightweight personalization

The most important current product rule is:

**let YouTube Music do the heavy recommendation work, and let Spasiba improve quality, control, and user experience around it.**
