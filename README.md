# Dev Browser

An Android web browser built for developers: a dark OperaGX-style theme, Tor and
proxy support, real Firefox extension installation, and a set of on-device dev
tools (source editor, cookie editor, JS console, network log).

It is built on **GeckoView** — the same engine that powers Firefox — because it
is the only Android engine that can install real `.xpi` WebExtensions and can be
pointed at a SOCKS5/Tor proxy at the network-stack level.

## Features

### Browsing
- Multi-tab browsing with a horizontal tab strip and private tabs
- Smart address bar (URL vs. search detection, `.onion` and `localhost` aware)
- Bookmarks, share, desktop-site toggle, custom search engine and user agent
- Enhanced Tracking Protection (strict), toggleable

### Theme
- OperaGX-inspired dark palette: near-black surfaces (`#0B0B0F`), hot red accent
  (`#FF1B4B`) with a cyan secondary, rounded cards and a gradient load bar
- Proxy badge in the URL bar changes colour by mode: grey = direct,
  cyan = proxy, purple = Tor

### Tor & proxies — `Menu → Proxy & Tor`
- **Direct**, **Tor via Orbot** (SOCKS5 `127.0.0.1:9050`) or **Manual proxy**
- SOCKS5 or HTTP/HTTPS manual proxies with host/port
- `network.proxy.socks_remote_dns` forces DNS through the proxy to avoid leaks
- `.onion` resolution enabled (`network.dns.blockDotOnion = false`)
- Can ask Orbot to start Tor, and links to `check.torproject.org` to verify

Tor mode requires [Orbot](https://play.google.com/store/apps/details?id=org.torproject.android)
installed; Dev Browser routes the engine through Orbot's SOCKS port rather than
bundling its own Tor daemon.

### Firefox extensions — `Menu → Extensions`
- Install any add-on from an `addons.mozilla.org` `.xpi` URL
- Install a local `.xpi` file through the system file picker
- Enable / disable / uninstall installed add-ons
- Unsigned add-ons allowed (`xpinstall.signatures.required = false`)

### Developer tools — the `</>` button
| Tool | What it does |
| --- | --- |
| **Source editor** | Reads the live DOM, lets you edit the HTML, formats it, and writes it straight back into the page |
| **Cookie editor** | Lists, edits, adds and deletes cookies for the current site, including `Secure` / `HttpOnly` / `SameSite` flags |
| **Storage inspector** | Browse and edit `localStorage` and `sessionStorage` |
| **JS console** | A REPL that evaluates JavaScript in the active page's context |
| **Network log** | Live request log (method, status, type, URL) via `webRequest` |
| **View source** | Opens `view-source:` in a new tab |
| **about:config** | Full Gecko preference editor, from Settings |

Remote debugging can be enabled in Settings, which lets you attach desktop
Firefox DevTools over adb via `about:debugging`.

## How the dev tools work

The tools are powered by a built-in WebExtension bundled in
`app/src/main/assets/addons/devtools/`. It is loaded at start-up with
`ensureBuiltIn()` and opens a native port named `devtools`.
`DevToolsBridge.kt` wraps that port in a suspending request/response API, so the
Kotlin UI can `await` a cookie list or a DOM dump. Network events are pushed the
other way as streamed messages.

This is why the tools work on any page without needing a privileged build of
Gecko: they use the same `cookies`, `tabs`, `storage` and `webRequest` APIs a
regular Firefox add-on would.

## Project layout

```
app/src/main/
├── assets/addons/devtools/     built-in WebExtension (manifest, background, content)
└── java/com/devbrowser/app/
    ├── browser/                GeckoEngine, Tab, TabManager, GeckoPrefs, UrlUtils
    ├── data/Prefs.kt           typed settings storage
    ├── extensions/             ExtensionManager, DevToolsBridge
    ├── proxy/ProxyController   direct / SOCKS5 / HTTP / Tor switching
    ├── tools/                  source editor, cookie editor, console, network log
    └── ui/                     BrowserActivity, settings, proxy, extensions screens
```

## Building

Requires JDK 17 and the Android SDK (compileSdk 34, minSdk 24).

```bash
gradle wrapper --gradle-version 8.7   # first time only
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Pushing to GitHub also builds it
via `.github/workflows/android.yml` and uploads the APK as a build artifact.

## Notes

- `xpinstall.signatures.required` is disabled so you can side-load your own
  add-ons. Only install `.xpi` files you trust.
- The Gecko preference bridge (`GeckoPrefs.kt`) uses reflection because GeckoView
  exposes no public `setPref` API. Every call is guarded, so a GeckoView upgrade
  can at worst make a preference not apply — it can never crash the browser.

## Enabling CI

The build workflow is kept at `ci/android-ci.yml` because this branch was pushed
by an app without `workflows` permission. To turn it on, run:

```bash
mkdir -p .github/workflows && cp ci/android-ci.yml .github/workflows/
git add .github && git commit -m "Enable Android CI" && git push
```
