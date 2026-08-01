# Spasiba Android Native Work

This file records the native Android app work completed for Spasiba.

## Final Outcome

Spasiba now has a real native Android app project under:

- `/Users/jigar/Desktop/Music App/android-native`

This is not the older Capacitor shell. It is a standalone native Android project using Kotlin, Compose, and ExoPlayer, while reusing the existing Spasiba backend and local-first caching ideas from the web app.

## Native Architecture Completed

The Android app now follows the same continuous shell idea as iOS/web:

1. dynamic backdrop layer
2. navigation/content layer
3. persistent player layer

Main files:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/MainActivity.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaViewModel.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/PlaybackCoordinator.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/ApiClient.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/LocalStore.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/Models.kt`

## UI / UX Implemented

### Home

- discovery-first entry
- search prompt card
- flatter category chip rail
- dense quick picks rows
- mixed rails
- persistent player overlay

### Search

- real route
- local suggestions first
- history-backed searching
- network fallback

### Category

- dedicated category route
- backdrop-driven atmosphere
- category hero
- category-specific feeds

### Profile

- native profile route
- liked songs
- listening history

### Library

- native bottom sheet presentation
- owned playlists
- shared playlists

### Player

- persistent mini player
- expanded player in same shell
- queue inside expanded player
- queue actions:
  - play now
  - move up
  - move down
  - play next
  - remove
- audio/video surface toggle

## Category Context + Dynamic Background

The Android native shell now keeps:

- `currentRoute`
- `currentCategory`
- `backdropUrl`
- `backgroundScrollProgress`

This drives:

- route-specific backdrop opacity
- category-specific immersive background
- scroll-aware darkening on category/home surfaces

Implemented in:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaViewModel.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/MainActivity.kt`

## Playback + Queue System

Playback is handled through:

- Media3 `ExoPlayer`
- Media3 `MediaSessionService`
- Android foreground service support for media playback
- local queue state
- related-track enhancement

File:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/PlaybackCoordinator.kt`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaPlaybackService.kt`

### Native Android media behavior

- the player instance is shared by the Compose UI and the media session service
- playback uses Android media audio attributes and audio-focus handling
- media items include title, artist, and artwork metadata for system surfaces
- playback starts the foreground media service so Android can expose lock-screen and notification controls
- the UI coordinator detaches listeners when the ViewModel clears without directly releasing service playback

### Queue behavior implemented

- queue snapshot restore
- `play`
- next / previous / jump-to-index
- add-next
- move queue items
- remove queue items
- queue persistence
- local-first queue enrichment
- related-track fallback via backend
- anti-repetition filtering

Mirrored queue constants:

- max queue length: `80`
- upcoming target: `10`
- related fetch limit: `24`
- minimum duration filter: `75s`
- artist repetition cap: `2`
- recent repeat window: `12`

## Local-First Caching / Storage

Android now persists a native JSON state file containing:

- visitor id
- search history
- recommendation pool
- listened songs
- queued songs
- recently played
- quick picks snapshot
- queue snapshot
- cached category feeds

Implemented in:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/LocalStore.kt`

## Quick Picks Logic

Quick Picks uses a native local-first engine that blends:

- recently played
- played tracks
- queued tracks
- recommendation pool
- previous quick picks carryover
- category feed fallback tracks

Implemented in:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/SpasibaViewModel.kt`

## Backend Reuse

The Android app reuses the current Spasiba backend for:

- category feed
- search
- related tracks
- stream resolution
- playlists
- profile
- likes

Implemented in:

- `/Users/jigar/Desktop/Music App/android-native/app/src/main/java/app/spasiba/nativeapp/ApiClient.kt`

## Project / Build Files

- `/Users/jigar/Desktop/Music App/android-native/settings.gradle`
- `/Users/jigar/Desktop/Music App/android-native/build.gradle`
- `/Users/jigar/Desktop/Music App/android-native/gradle.properties`
- `/Users/jigar/Desktop/Music App/android-native/local.properties`
- `/Users/jigar/Desktop/Music App/android-native/app/build.gradle`
- `/Users/jigar/Desktop/Music App/android-native/app/src/main/AndroidManifest.xml`

## Build Output Produced

Fresh debug APK:

- `/Users/jigar/Desktop/Music App/android-native/app/build/outputs/apk/debug/app-debug.apk`

## Verification

Latest verification completed successfully:

- `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug`
- `npm run android-native:debug`

Build status:

- `BUILD SUCCESSFUL`

## Command Used

```bash
cd "/Users/jigar/Desktop/Music App/android-native"
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

or from the workspace root:

```bash
npm run android-native:debug
```

## Notes

- the Android build uses the Android Studio bundled JDK
- the generated APK is a debug build
- `local.properties` points to `/Users/jigar/Library/Android/sdk`
- there is still an older `/Users/jigar/Desktop/Music App/android` wrapper project in the repo, but the real native Android work is in `/Users/jigar/Desktop/Music App/android-native`
