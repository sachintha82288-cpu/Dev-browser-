# DevBrowser — Master Plan (5-month track)

Goal: turn the Phase-1 foundation into a real developer + privacy-first browser.
Every phase must leave a **buildable, signed APK** (`make apk`) and updated docs.

| # | Phase | Weeks | Deliverables | Status |
|---|-------|-------|--------------|--------|
| 1 | Foundation | — | Offline toolchain, tabs, incognito, history/bookmarks, downloads/APK, WebView proxy override, dev console, UA switch, neon GX theme, EN+SI | ✅ |
| 2 | Privacy & network layer | 1–6 | **In-app SOCKS-aware proxy server (Tor)** ✅, ad/tracker blocking (host rules + cosmetics) ✅, third-party-cookie blocking ✅, per-site proxy overrides ⏳, privacy audit UI ⏳ | 80% |
| 3 | Developer tooling | 6–12 | **Network log inspector** ✅, **DOM inspector (tap-to-inspect)** ✅, **HTML/CSS/JS workspace with live preview** ✅, cookie/local-storage viewer ⏳, CDP network panel ⏳ | 60% |
| 4 | Engine & performance | 12–18 | Optional GeckoView engine, preload/cache strategy, tab thumbnails + groups, bfcache tuning | ⏳ |
| 5 | Polish & release | 18–22 | Dynamic themes (Material You), a11y pass, sync (optional endpoint), instrumentation tests, F-Droid + Play signing, privacy policy | ⏳ |
| S | Stretch | after | JS-only extension mini-API, per-tab proxies, QR/WebShare, gesture shortcuts | ⏳ |

## Phase 2 — Privacy & network layer (now)

### 2.1 In-app proxy server (Tor over SOCKS5)
WebView's proxy override only understands an **HTTP CONNECT proxy**. So we ship
our own local proxy (`127.0.0.1:<random port>`) that:
- accepts `CONNECT` (HTTPS tunneling) and plain HTTP requests,
- opens the upstream connection through a **SOCKS5 client** (Orbot `127.0.0.1:9050`
  by default, configurable),
- relays bytes bidirectionally.

Files: `network/Socks5.java`, `network/LocalProxyServer.java`.

### 2.2 Ad/tracker blocking
- `shouldInterceptRequest` interception (API 21+, pure platform).
- Bundled host-rule list (`assets/adblock/hosts.txt`, curated ~130 rules,
  `||domain^` semantics) + user rules field in Settings.
- Cosmetic CSS injection (`assets/adblock/cosmetics.css`) on page load.
- Blocked requests are logged to the Network log and visibly counted.

### 2.3 Cookie/privacy hardening
- Third-party cookie blocking toggle (`CookieManager.setAcceptThirdPartyCookies`).
- Per-site proxy override list (host → proxy; `.onion` always → Tor).

## Phase 3 — Developer tooling (first half now)

### 3.1 Network log
In-memory ring buffer capturing every intercepted request: URL, type
(document/script/style/image/media/xhr/other), result (blocked/ok/error),
size, duration, timestamp. UI: filter + tap-for-details. Files: `core/NetworkLog.java`,
`ui/NetworkLogActivity.java`.

### 3.2 DOM inspector
"Tap to inspect" mode → JS `elementFromPoint`, returns tag/outerHTML, copy and exit.

### 3.3 Workspace (toy dev environment)
`ui/WorkspaceActivity.java`: live HTML/CSS/JS editing with an instant preview
WebView, save/reset/share. Pure data:-URL preview, no network needed.

## Later (phases 4–5 / stretch)
- GeckoView switch, preload & cache strategy, tab thumbnails/groups
- Cookie/local-storage viewer, CDP network panel
- Dynamic Material You theme, TalkBack pass, F-Droid+Play, privacy policy
- Extension mini-API, per-tab proxies, QR/gestures

## Engineering rules
- **No AndroidX** — pure platform API, keeps offline toolchain working.
- Every feature compiles with `tools/build.sh` (aapt2 → javac → D8 → zipalign →
  apksigner); APK stays tiny.
- Threading: network work off the main thread; UI callbacks on main.
- EN + සිංහල strings for every new string.
- Commit per phase with a passing `make apk`.
