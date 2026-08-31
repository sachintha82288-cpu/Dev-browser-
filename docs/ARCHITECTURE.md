# Architecture

```
app/
├── AndroidManifest.xml
├── res/                     # Neon "Opera GX-like" theme (dark + pink/cyan/purple)
│   ├── values/, values-si/  # English + සිංහල UI strings
│   ├── layout/main.xml      # toolbar, tab strip, web area, dev console
│   ├── drawable/            # vector icons + gradient/card backgrounds
│   └── mipmap-*/            # generated launcher icons (tools/make_icons.py)
└── src/main/java/com/devbrowser/app/
    ├── MainActivity.java    # tabs, toolbar, menu, dev console UI
    ├── SettingsActivity.java
    ├── HistoryActivity.java / BookmarksActivity.java / DownloadsActivity.java
    ├── ViewSourceActivity.java
    ├── DevBrowserApp.java   # app init, download dir
    ├── core/  DevTools (JS bridge, view-source, screenshot, JS eval)
    │          StartPage (built-in neon start page)
    ├── db/    Store (SQLite history + bookmarks)
    ├── model/ Tab, DownloadItem
    ├── network/ ProxyManager (WebView proxy via reflection) · TorManager (Orbot)
    ├── dl/    Downloads (own engine, honours proxy)
    ├── provider/ AppFileProvider (androidx-free FileProvider replacement)
    └── util/  Prefs, U (URL/date/MIME helpers)
```

### Design decisions
* **Zero external Android dependencies.** No androidx/Room/lifecycle — the app
  targets the pure platform API so it compiles with the offline toolchain
  (and stays tiny: the APK is ~190 KB).
* **WebView-based engine.** One `WebView` per tab, incognito via cookie
  clearing + history suppression, JS console captured through
  `WebChromeClient.onConsoleMessage`, `window.DevBrowser` bridge (title/url/
  host/version), `setWebContentsDebuggingEnabled(true)` for Chrome DevTools
  (chrome://inspect).
* **Proxy without androidx.** `androidx.webkit.ProxyController` is only a
  boundary interface; the actual provider object is created by
  `SupportLibWebViewChromiumFactory.getProxyController()` reachable through
  `android.webkit.WebViewFactory.getProvider()`. `ProxyManager` reflects into it
  (`setProxyOverride(String[][], String[], Runnable, Executor)`) with graceful
  fallback — the standard Orbot preset is `127.0.0.1:8118`.
* **Downloads + APK installs.** Custom streaming downloader (supports any proxy
  incl. Tor), app-private Downloads folder, own `ContentProvider`
  (`AppFileProvider`) so APK installs work on Android 7+ without androidx
  FileProvider; `REQUEST_INSTALL_PACKAGES` + unknown-sources flow.

### Roadmap (5-month plan)
See `docs/ROADMAP.md` for the phased plan: engine swap (GeckoView or in-app Tor
socks), ad-blocking, CDP network inspector, bookmarks sync, F-Droid release,
screen-reader & a11y passes.
