# CineFlix by MJ — Google TV & Android TV Application

Native 10-foot Google TV / Android TV application built with **Kotlin**, **Jetpack Compose for TV**, **AndroidX Media3 ExoPlayer**, and a **Hybrid TV WebView** playback engine, backed by the **CineFlix** (`https://cineflixbymj.vercel.app/`) TMDB v3 catalog, streaming server nodes, and Firebase Cloud Sync.

## Architecture

### 1. Native 10-Foot Google TV UI (`com.cineflix.tv.ui`)
- **Dark Obsidian & Crimson Design System** (`#080808` canvas, `#0E0E12` elevated surfaces, `#E50914` crimson focus rings)
- **D-Pad Focus Animations**: Smooth spring scale (`1.07x`), 3dp crimson focus borders, and unboxed tabular metadata (`★ Rating · Year · Media Type`)
- **Screens**:
  - `HomeScreen`: Full-bleed 16:9 Featured Hero Carousel + Continue Watching rail + 11 live TMDB horizontal poster rails (`Trending Now`, `Recommended For You`, `Popular Movies`, `Popular TV Shows`, `Top Rated Movies`, `Action & Adventure`, `Sci-Fi`, `Upcoming Movies`, `Top Rated TV Shows`, `Comedy`, `Horror`)
  - `BrowseScreen`: Category & Genre discovery for Movies, TV Shows, and Trending with D-Pad genre chips, sort filters, and pagination
  - `SearchScreen`: Real-time debounced TMDB `/search/multi` with D-Pad Quick Keyboard Strip + hardware keyboard support + type/sort filters
  - `DetailsScreen`: Full-bleed backdrop hero, Match % affinity score, runtime/season counts, Cast rail, Season & Episode 16:9 browser, and Recommended/Similar rails
  - `LibraryScreen`: Watchlist (`My List`) & Viewing History with resume progress bars and CineFlix Cloud Sync
  - `SettingsScreen`: TMDB BYOK credential verification, default streaming node selector, 8-second auto-failover toggle, custom direct stream URL, and Firebase Cloud Sync login
  - `TvApiSetupGuideScreen`: First-launch TMDB setup with a built-in **Phone-to-TV Wi-Fi Companion Server** (`http://<tv-ip>:8765`) so you can paste your TMDB key from your phone browser

### 2. Hybrid Playback Engine (`PlayerScreen.kt`)
- **Native AndroidX Media3 ExoPlayer**: Automatically used for direct `.mp4`, `.m3u8` (HLS), `.mpd` (DASH), `.webm`, and `.mkv` streams (Server 6 or intercepted direct media streams), featuring:
  - D-Pad Play/Pause, Seek -10s/+10s, playback speed (`0.75x`–`1.5x`), and Next Episode controls
  - Live **Audio Track Selection** (`TrackSelectionOverride` on `C.TRACK_TYPE_AUDIO`)
  - Live **Subtitle / Closed Captions Selection** (`TrackSelectionOverride` on `C.TRACK_TYPE_TEXT`)
  - Automatic fallback to Hybrid WebView if an incompatible container/stream error occurs
- **Hybrid TV WebView Player**: Supports CineFlix's 5 cloud embed nodes (`vidsrc.pm`, `vidsrc.to`, `vidsrc.sh`, `smashystream.xyz`, `anyembed.xyz`) and TMDB Official Trailers (`youtube-nocookie.com`) with desktop 16:9 theatre rendering, JavaScript & third-party cookie persistence, fullscreen HTML5 video, popup/ad-redirect blocking, 8-second auto-failover watchdog, and direct stream sniffing.

## Building & Installing the APK

### Option A: Open in Android Studio
1. Open the `android-tv/` folder in **Android Studio Ladybug (2024.2+)** or newer.
2. Let Gradle sync (`compileSdk = 35`, `minSdk = 26`).
3. Select an **Android TV / Google TV Emulator** (1080p/4K Landscape) or connected physical Google TV device over ADB.
4. Click **Run (`Shift+F10`)** or **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Option B: Build from Command Line
```bash
cd android-tv
./gradlew assembleDebug
```
Output APK: `android-tv/app/build/outputs/apk/debug/app-debug.apk`

### Sideload onto Google TV / Android TV via ADB
```bash
adb connect <YOUR_GOOGLE_TV_IP>:5555
adb install -r android-tv/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.cineflix.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```
