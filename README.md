# DevBrowser

An Android web browser built for **developers**, powered by **Firefox (GeckoView 157)**.

> Rebuilt from scratch on a raw GeckoView engine — no Fenix/mozilla-android-components
> dependency, just GeckoView + Jetpack Compose.

## Features

- 🖤 **OperaGX-style dark neon theme** (custom Compose Material 3 theme, AMOLED black mode)
- 🧩 **Install any extension** — from addons.mozilla.org links (resolved via the public AMO
  API, Android builds preferred), direct `.xpi` URLs, or local `.xpi` files
  (`xpinstall.signatures.required=false` so unsigned builds install too)
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
  DNS-over-HTTPS (default or custom TRR URL), private tabs, pop-up blocking
- 🏷️ Tabs tray with session restore, bookmarks + history library, downloads,
  full web-app prompt support (auth, alerts, file pickers, permission dialogs, share)
- 🇱🇰 English + Sinhala (සිංහල) UI

## Tech

| Piece      | Choice |
|------------|--------|
| Engine     | `org.mozilla.geckoview:geckoview-omni:157.0.20260924084938` |
| UI         | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Build      | AGP 8.8.2, Kotlin 2.0.21, Gradle 8.12.1, JDK 17 |
| Min / Target SDK | 26 / 36 |
| Package    | `io.github.sachintha82288.devbrowser` |

## Build

```bash
./gradlew assembleDebug        # debug APK (per-ABI splits + universal)
./gradlew assembleRelease      # minified release
```

Requires the [Android SDK](https://developer.android.com/studio) with platform 36.
GeckoView is fetched from `maven.mozilla.org` automatically.

## Project layout

```
app/src/main/java/io/github/sachintha82288/devbrowser/
├── DevBrowserApplication.kt   # engine boot, startup config, extensions init
├── MainActivity.kt            # single activity, keyboard shortcuts
├── browser/                   # GeckoEngine, TabsController, delegates, error pages
├── extensions/                # ExtensionManager (install/update/uninstall, AMO resolver)
├── downloads/                 # download handling
├── data/                      # AppSettings, Library (bookmarks/history)
├── proxy/                     # ProxyPrefs (Tor + manual proxy pref mapping)
├── util/                      # URL utilities
└── ui/                        # Compose screens (browser, tabs, prompts, menus, settings…)
```

## Notes

- Proxy configuration uses Gecko prefs (`network.proxy.type`, `.socks`, `.socks_remote_dns`, …)
  — the GeckoView `ProxyConfig` builder API was removed in GV 157.
- Tor preset expects Orbot's SOCKS proxy on `127.0.0.1:9050`.
