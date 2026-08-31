# Product roadmap — DevBrowser (≈ 5 months)

## Phase 1 — Foundation (done, this repo)
- [x] Offline build chain (JDK, aapt2, android.jar 34, D8, apksigner)
- [x] Neon GX-style theme, Sinhala + English strings
- [x] Multiple tabs, incognito, address/search bar
- [x] History, bookmarks (SQLite)
- [x] Downloads + APK install via own ContentProvider
- [x] Proxy settings + Tor/Orbot preset (WebView proxy override)
- [x] Dev console (captured console + JS eval), view-source, screenshot, UA switcher
- [x] Embedded neon start page

## Phase 2 — Real privacy/network layer (weeks 1–6)
- [x] ~~In-app Tor via VpnService~~ → implemented as **in-app SOCKS5 HTTP proxy**
      (`LocalProxyServer` + `Socks5`), which is the correct WebView-level Tor route
- [ ] Per-site proxy rules (auto-route `.onion` through Tor) — planned next
- [ ] TLS pinning / no-sniff defaults, certificate viewer — planned next
- [x] Ad/tracker blocking (host rules + user rules + cosmetic CSS, no network at runtime)

## Phase 3 — Developer tooling (weeks 6–12)
- [ ] Chrome DevTools protocol client: network waterfall panel inside the app
      (in-app **network log** shipped in v1.1; CDP panel planned)
- [x] DOM inspector (tap-to-inspect element) — full tree/computed-styles planned
- [ ] Local storage / cookie inspector
- [ ] Beautiful HTML error pages + request logging
- [x] Workspace: inline HTML/CSS/JS editor with live preview

## Phase 4 — Engine & performance (weeks 12–18)
- [ ] Multi-process / renderer tuning, preload + cache strategy
- [ ] Optional GeckoView engine (Mozilla) behind a settings switch
- [ ] Offline pages (Workbox-like SW support in data: pages)
- [ ] Back-forward cache, thumbnail tab previews, tab groups

## Phase 5 — Polish & release (weeks 18–22)
- [ ] Material You / dynamic theme variants
- [ ] Synchronised bookmarks (optional self-hosted endpoint)
- [ ] Accessibility pass (TalkBack, contrast, 48dp targets)
- [ ] Instrumentation tests (Robolectric-equivalent locally, adb on device)
- [ ] F-Droid packaging, Play-ready v2 signing, privacy policy

## Stretch
- [ ] Extension mini-API (JS-only "DevBrowser extensions")
- [ ] QR/WebShare integrations, per-tab proxies, custom shortcut gestures
