# DevBrowser

An Android web browser built for **developers**, powered by **Firefox (GeckoView 157)**.

> Rebuilt from scratch on a raw GeckoView engine — no Fenix/mozilla-android-components
> dependency, just GeckoView + Jetpack Compose.

## Features

- 🖤 **OperaGX-style dark neon theme** (custom Compose Material 3 theme, AMOLED black mode)
- 🧩 **Install any extension** — from addons.mozilla.org links (resolved via the public AMO
  API, Android builds preferred), direct `.xpi` URLs, or local `.xpi` files.
  An *Allow unsigned add-ons* toggle writes `xpinstall.signatures.required=false` (plus
  `xpinstall.signatures.dev-root=true`) into the engine startup config so self-built `.xpi`
  files install; switching it off clears those prefs from the Gecko user branch again.
- 🌐 **Tor + proxy support** — manual HTTP/SOCKS 4/5 proxies with remote-DNS option,
  one-tap Orbot (Tor) preset, per-network independence (`network.proxy.*` prefs applied
  live and persisted in a startup config file)
- 🔧 **Developer tooling**
  - Remote debugging via `about:debugging` (desktop web-ext support)
  - Console output to logcat
  - `about:config` access
  - View page source, desktop-mode toggle, find-in-page
  - Physical-keyboard shortcuts: `Ctrl+T` new tab, `Ctrl+W` close tab, `Ctrl+R` reload,
    `Ctrl+F` find, `Ctrl+L` focus URL bar
- 🔒 **Privacy** — Enhanced Tracking Protection (Standard/Strict/Off), HTTPS-Only mode,
  DNS-over-HTTPS (default or custom TRR URL), private tabs, pop-up blocking,
  Global Privacy Control, clear-browsing-data
- 🏷️ Tabs tray with session restore (+ "close private tabs"), bookmarks + history library,
  downloads, full web-app prompt support (auth, alerts, choice, colour, date/time, file
  pickers, permission dialogs, share)
- 🇱🇰 English + Sinhala (සිංහල) UI — all user-facing copy lives in string resources
  (`values/` + `values-si/`), with a per-app language picker on Android 13+

## Tech

| Piece      | Choice |
|------------|--------|
| Engine     | `org.mozilla.geckoview:geckoview-omni:157.0.20260924084938` |
| UI         | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Build      | AGP 8.10.1, Kotlin 2.0.21, Gradle 8.12.1, JDK 17 |
| Min / Target SDK | 26 / 36 |
| Package    | `io.github.sachintha82288.devbrowser` |

API level 36 needs AGP 8.9.1 or newer, hence AGP 8.10.1 with the Gradle 8.12.1 wrapper.

## Build

```bash
./gradlew assembleDebug        # debug APK (per-ABI splits + universal)
./gradlew assembleRelease      # minified release
```

Requires the [Android SDK](https://developer.android.com/studio) with platform 36.
GeckoView is fetched from `maven.mozilla.org` automatically.

A GitHub Actions workflow (`.github/workflows/build.yml`) builds `assembleDebug` on every
push and pull request and uploads the APKs as artifacts.

## Project layout

```
app/src/main/java/io/github/sachintha82288/devbrowser/
├── DevBrowserApplication.kt   # engine boot, startup config, extensions init
├── MainActivity.kt            # single activity, keyboard shortcuts
├── browser/                   # GeckoEngine, StartupPrefs, TabsController, delegates, error pages
├── extensions/                # ExtensionManager (install/update/uninstall, AMO resolver)
├── downloads/                 # download handling
├── data/                      # AppSettings, Library (bookmarks/history)
├── proxy/                     # ProxyPrefs (Tor + manual proxy pref mapping)
├── util/                      # URL utilities
└── ui/                        # Compose screens (browser, tabs, prompts, menus, settings…)
```

All Gecko preferences DevBrowser owns are composed in `browser/StartupPrefs.kt`
(proxy + DoH + add-on installation) and written to `geckoview-config.yaml` at startup,
so a cold start and a live settings toggle always agree.

## Notes

- Proxy configuration uses Gecko prefs (`network.proxy.type`, `.socks`, `.socks_remote_dns`, …)
  — the GeckoView `ProxyConfig` builder API was removed in GV 157.
- Tor preset expects Orbot's SOCKS proxy on `127.0.0.1:9050`.
- Downloads go through the system `DownloadManager`: on Android 10+ into the public
  *Downloads* folder, on Android 8/9 into the app-specific external Downloads folder
  (no `WRITE_EXTERNAL_STORAGE` prompt needed).
- Release-channel Firefox builds may lock `xpinstall.signatures.required`; if that happens
  an unsigned `.xpi` is rejected with "extension is not signed by Mozilla".
