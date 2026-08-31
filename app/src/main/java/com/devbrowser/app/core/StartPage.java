package com.devbrowser.app.core;

import android.net.Uri;

/** Built-in neon start page, served as a data: URL (no network, no assets needed). */
public class StartPage {

    private static final String HTML =
            "<!doctype html><html><head><meta charset='utf-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>DevBrowser</title>" +
            "<style>" +
            "body{margin:0;background:radial-gradient(1200px 700px at 20% -10%,#2a1240 0%,transparent 55%)," +
            "radial-gradient(1000px 600px at 110% 20%,#0f2a3d 0%,transparent 50%),#0d0d13;color:#f2f2f7;" +
            "font-family:system-ui,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;}" +
            ".wrap{width:min(680px,92vw);text-align:center}" +
            "h1{font-size:34px;margin:0 0 6px;font-weight:800;letter-spacing:.5px}" +
            "h1 .pink{color:#ff3ea5;text-shadow:0 0 22px rgba(255,62,165,.6)}" +
            "h1 .cyan{color:#00e7ff;text-shadow:0 0 22px rgba(0,231,255,.5)}" +
            "p.tag{color:#9a9ab0;margin:0 0 26px}" +
            "form{display:flex}" +
            "input{flex:1;background:#1e1e2a;border:1px solid #35354a;border-radius:14px 0 0 14px;" +
            "color:#f2f2f7;padding:16px 18px;font-size:16px;outline:none}" +
            "button{background:linear-gradient(90deg,#ff3ea5,#8a5cff);border:0;color:#fff;font-weight:700;" +
            "padding:0 26px;font-size:16px;border-radius:0 14px 14px 0;cursor:pointer}" +
            ".links{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;margin-top:26px}" +
            "a{color:#00e7ff;text-decoration:none;background:#17171f;border:1px solid #2e2e40;" +
            "padding:10px 16px;border-radius:12px;font-size:14px}" +
            "a:active{filter:brightness(1.4)}" +
            ".foot{margin-top:34px;color:#55556a;font-size:12px}" +
            "</style></head><body><div class='wrap'>" +
            "<h1><span class='pink'>Dev</span><span class='cyan'>Browser</span></h1>" +
            "<p class='tag'>Developer web browser — Tor &amp; proxy ready · dev console · install apps</p>" +
            "<form onsubmit='location=\"https://duckduckgo.com/?q=\"+encodeURIComponent(this.q.value);return false'>" +
            "<input name='q' autofocus autocomplete='off' placeholder='Search or type a URL…'>" +
            "<button>Go</button></form>" +
            "<div class='links'>" +
            "<a href='https://github.com'>GitHub</a>" +
            "<a href='https://stackoverflow.com'>Stack Overflow</a>" +
            "<a href='https://developer.mozilla.org'>MDN</a>" +
            "<a href='https://www.npmjs.com'>npm</a>" +
            "<a href='https://pypi.org'>PyPI</a>" +
            "<a href='https://www.android.com'>Android</a>" +
            "</div>" +
            "<div class='foot'>window.DevBrowser → title · url · host · version</div>" +
            "</div></body></html>";

    public static String dataUrl() {
        return "data:text/html;charset=utf-8," + Uri.encode(HTML);
    }
}
