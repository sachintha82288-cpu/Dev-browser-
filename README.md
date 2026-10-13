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
| UI         | Jetpack Compose (BOM 2026.09.00), Material 3 |
| Build      | AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, Gradle 9.8.1, JDK 17 |
| Min / Compile / Target SDK | 26 / 37.1 / 36 |
| Package    | `io.github.sachintha82288.devbrowser` |

The toolchain floor is set by GeckoView, not by the app: `geckoview-omni:157` depends on
`androidx.core:core:1.19.0` and `androidx.lifecycle:*:2.11.0`, and those AARs carry metadata
that refuses to build against anything older than **compileSdk 37.1 with AGP 9.1+**. AGP 9.4.1
plus the Gradle 9.8.1 wrapper is the current stable pairing, and 2.4.20 is the Kotlin GeckoView
157 was built with. AGP 9 compiles Kotlin itself, so `org.jetbrains.kotlin.android` is no longer
applied — only the Compose compiler plugin is. `targetSdk` stays at 36, so compiling against 37
does not change how the app behaves on a device. `buildToolsVersion` is pinned to 37.0.0 to
match the platform.

## Build

```bash
./gradlew assembleDebug        # debug APK (per-ABI splits + universal)
./gradlew assembleRelease      # minified release (unsigned: no key lives in this repo)
./gradlew testDebugUnitTest    # JVM unit tests
```

Requires the [Android SDK](https://developer.android.com/studio) with platform `android-37.1`,
build-tools 37.0.0 and JDK 17.
GeckoView is fetched from `maven.mozilla.org` automatically — it is a ~230 MB download, so the
first build takes a while.

A GitHub Actions workflow (`.github/workflows/build.yml`) runs two jobs on every push and pull
request — `Debug` (`testDebugUnitTest` + `assembleDebug`) and `Release` (`assembleRelease`, so R8
and the keep rules stay covered) — with `fail-fast` off, so a problem in one cannot hide a healthy
other. Any Kotlin/AGP/R8 error is posted straight onto the pull request. The `arm64-v8a` debug APK
is uploaded as an artifact (~110 MB; the other ABIs are built too, but a GeckoView APK is ~100 MB
each, so only one is kept).

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

app/src/test/java/…/util/UrlUtilsTest.kt   # address-bar heuristics (load vs. search, http vs. https)
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
