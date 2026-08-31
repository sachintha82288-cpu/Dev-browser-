package com.devbrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.devbrowser.app.core.DevTools;
import com.devbrowser.app.core.NetworkLog;
import com.devbrowser.app.core.StartPage;
import com.devbrowser.app.db.Store;
import com.devbrowser.app.dl.Downloads;
import com.devbrowser.app.model.Tab;
import com.devbrowser.app.network.AdBlocker;
import com.devbrowser.app.util.Prefs;
import com.devbrowser.app.util.U;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Tab.Listener {

    private EditText address;
    private ImageButton back, forward, reload, home, newTab, menu;
    private HorizontalScrollView tabScroll;
    private LinearLayout tabsContainer, consolePanel;
    private FrameLayout viewContainer;
    private TextView emptyHint, consoleLog;
    private ScrollView consoleScroll;
    private EditText consoleInput;

    private final List<Tab> tabs = new ArrayList<>();
    private Tab current;
    private boolean inspectMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        address = findViewById(R.id.address);
        back = findViewById(R.id.actionBack);
        forward = findViewById(R.id.actionForward);
        reload = findViewById(R.id.actionReload);
        home = findViewById(R.id.actionHome);
        newTab = findViewById(R.id.actionNewTab);
        menu = findViewById(R.id.actionMenu);
        tabScroll = findViewById(R.id.tabScroll);
        tabsContainer = findViewById(R.id.tabsContainer);
        viewContainer = findViewById(R.id.viewContainer);
        emptyHint = findViewById(R.id.emptyHint);
        consolePanel = findViewById(R.id.consolePanel);
        consoleLog = findViewById(R.id.consoleLog);
        consoleScroll = findViewById(R.id.consoleScroll);
        consoleInput = findViewById(R.id.consoleInput);

        wireToolbar();
        wireConsole();

        openUrl(getIntent());
        if (tabs.isEmpty()) newTab(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openUrl(intent);
    }

    private void openUrl(Intent intent) {
        if (intent == null) return;
        String url = intent.getStringExtra("url");
        if (url == null && intent.getData() != null) url = intent.getData().toString();
        if (url != null && !url.isEmpty()) {
            if (current != null) {
                current.web.loadUrl(url);
            } else {
                newTab(false, url);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Toolbar
    // ------------------------------------------------------------------
    private void wireToolbar() {
        back.setOnClickListener(v -> { if (current != null && current.web.canGoBack()) current.web.goBack(); });
        forward.setOnClickListener(v -> { if (current != null && current.web.canGoForward()) current.web.goForward(); });
        reload.setOnClickListener(v -> {
            if (current == null) return;
            if (current.web.getProgress() < 100) current.web.stopLoading();
            else current.web.reload();
        });
        home.setOnClickListener(v -> { if (current != null) loadTarget(current, Prefs.homepage()); });
        newTab.setOnClickListener(v -> newTab(false));
        address.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) { navigate(); return true; }
            return false;
        });
        findViewById(R.id.actionGo).setOnClickListener(v -> navigate());
        findViewById(R.id.logo).setOnClickListener(v -> { if (current != null) current.web.loadUrl(Prefs.homepage()); });
        menu.setOnClickListener(v -> showMenu(v));
    }

    private void navigate() {
        String input = address.getText().toString();
        if (input.trim().isEmpty()) return;
        if (current != null) {
            current.web.loadUrl(U.smartUrl(input));
            current.web.requestFocus();
        }
    }

    // ------------------------------------------------------------------
    //  Tabs
    // ------------------------------------------------------------------
    private Tab newTab(boolean incognito) {
        return newTab(incognito, null);
    }

    private Tab newTab(boolean incognito, String url) {
        WebView web = new WebView(this);
        configureWebView(web, incognito);
        Tab tab = new Tab(web, incognito);
        tab.listener = this;
        DevTools.installBridge(web, tab);
        setupWebViewCallbacks(tab);
        tabs.add(tab);
        viewContainer.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        selectTab(tab);
        loadTarget(tab, (url == null || url.isEmpty()) ? Prefs.homepage() : url);
        return tab;
    }

    /** Load a URL, mapping the internal about:devhome to the built-in start page. */
    private void loadTarget(Tab tab, String target) {
        if (target == null) target = "";
        if (target.startsWith("about:devhome") || target.isEmpty()) {
            tab.web.loadUrl(StartPage.dataUrl());
        } else {
            tab.web.loadUrl(target);
        }
    }

    private void configureWebView(WebView web, boolean incognito) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(Prefs.jsEnabled());
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadsImagesAutomatically(!Prefs.blockImages());
        s.setBlockNetworkImage(Prefs.blockImages());
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(Prefs.resolveUserAgent(s.getUserAgentString()));
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setWebContentsDebuggingEnabled(true);

        // privacy hardening
        CookieManager.getInstance().setAcceptThirdPartyCookies(
                web, Prefs.thirdPartyCookiesAllowed());
        web.setOnTouchListener(inspectHandler);

        if (incognito) {
            // Incognito profile: clear cookies + localStorage on close.
            CookieManager.getInstance().removeAllCookies(null);
            web.clearHistory();
        }
    }

    // ------------------------------------------------------------------
    //  DOM inspector (tap-to-inspect)
    // ------------------------------------------------------------------
    private final android.view.View.OnTouchListener inspectHandler = (v, event) -> {
        if (!inspectMode || current == null || event.getAction() != android.view.MotionEvent.ACTION_UP) {
            return false;
        }
        final WebView web = current.web;
        float scale = web.getScale();
        final int x = (int) (event.getX() / scale);
        final int y = (int) (event.getY() / scale);
        String js = "(function(x,y){try{var e=document.elementFromPoint(x,y);" +
                "if(!e)return '{}';var h=e.outerHTML;if(h.length>1400)h=h.slice(0,1400)+'...';" +
                "return JSON.stringify({tag:e.tagName,cls:(e.getAttribute&&e.getAttribute('class'))||''," +
                "id:(e.getAttribute&&e.getAttribute('id'))||'',html:h});}catch(err){return '{}';}})(" +
                x + "," + y + ")";
        web.evaluateJavascript(js, value -> showInspector(value));
        return true;
    };

    private void showInspector(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) return;
        try {
            Object decoded = new org.json.JSONTokener(value).nextValue();
            if (!(decoded instanceof String)) return;
            JSONObject o = new JSONObject((String) decoded);
            final String tag = o.optString("tag", "?");
            final String id = o.optString("id", "");
            final String cls = o.optString("cls", "");
            final String html = o.optString("html", "");
            StringBuilder sb = new StringBuilder();
            sb.append("TAG: ").append(tag).append('\n');
            if (!id.isEmpty()) sb.append("ID: ").append(id).append('\n');
            if (!cls.isEmpty()) sb.append("CLASS: ").append(cls).append('\n');
            sb.append('\n').append(html);
            new AlertDialog.Builder(this)
                    .setTitle("<" + tag.toLowerCase() + ">")
                    .setMessage(sb.toString())
                    .setPositiveButton("Copy", (d, w) -> {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("element", html));
                    })
                    .setNeutralButton("Done", (d, w) -> inspectMode = false)
                    .setNegativeButton(R.string.close, null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    private void setupWebViewCallbacks(final Tab tab) {
        tab.web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.url = url;
                tab.progress = 0;
                tab.web.setVisibility(View.VISIBLE);
                emptyHint.setVisibility(View.GONE);
                address.setText(url);
                refreshChips();
                NetworkLog.begin(url, "document");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url;
                tab.progress = 100;
                address.setText(url);
                String t = view.getTitle();          // robust across WebView builds
                if (t != null && !t.isEmpty()) tab.title = t;
                if (tab.title == null || tab.title.isEmpty()) tab.title = U.hostOf(url);
                refreshChips();
                saveHistory(tab, url);
                injectCosmetics(tab, url);
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (!(url.startsWith("http://") || url.startsWith("https://"))) return null;
                String type = request.isForMainFrame() ? "document" : NetworkLog.typeOf(url);
                NetworkLog.Entry e = NetworkLog.begin(url, type);
                if (AdBlocker.get(MainActivity.this).shouldBlock(url)) {
                    NetworkLog.markBlocked(e);
                    try {
                        return new android.webkit.WebResourceResponse(
                                "text/plain", "UTF-8", 403, "Blocked by DevBrowser",
                                new java.util.HashMap<String, String>(),
                                new java.io.ByteArrayInputStream(new byte[0]));
                    } catch (Exception ex) {
                        return null;
                    }
                }
                return null;
            }

            @Override
            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
                new AlertDialog.Builder(MainActivity.this)
                        .setOnDismissListener(d -> handler.cancel())
                        .setTitle(R.string.more)
                        .setMessage("SSL error: " + error.getPrimaryError() + "\n" + error.getUrl() +
                                "\n\nContinue anyway? (dev tool)")
                        .setPositiveButton(R.string.ok, (d, w) -> handler.proceed())
                        .setNegativeButton(R.string.cancel, (d, w) -> handler.cancel())
                        .show();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (tab.url == null || tab.url.isEmpty()) {
                    tab.url = failingUrl == null ? "" : failingUrl;
                    emptyHint.setVisibility(View.VISIBLE);
                    emptyHint.setText("Error " + errorCode + ": " + description);
                }
                tab.log("error", errorCode + " " + description + " " + failingUrl);
                NetworkLog.Entry e = findOpenEntry(failingUrl != null ? failingUrl : tab.url);
                if (e != null) NetworkLog.markFailed(e);
                refreshPanel();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("about:") || url.startsWith("data:")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        tab.web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                tab.progress = newProgress;
                refreshChips();
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                String level = m.messageLevel().name().toLowerCase();
                tab.log(level, m.message() + "  @" + m.sourceId() + ":" + m.lineNumber());
                refreshPanel();
                return true;
            }
        });

        tab.web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
            Downloads.enqueue(MainActivity.this, url, name);
            NetworkLog.Entry e = NetworkLog.begin(url, "download");
            NetworkLog.end(e, contentLength);
            Toast.makeText(MainActivity.this,
                    getString(R.string.download_started) + ": " + name, Toast.LENGTH_SHORT).show();
        });
    }

    /** Close the open document entry for a URL (or the most recent one). */
    private NetworkLog.Entry findOpenEntry(String url) {
        for (NetworkLog.Entry e : NetworkLog.snapshot("all")) {
            if (e.end == -1 && ("document".equals(e.type) || "other".equals(e.type))
                    && (url == null || e.url.equals(url))) return e;
        }
        return null;
    }

    /** Inject cosmetic (CSS) filtering into the page after load. */
    private void injectCosmetics(Tab tab, String url) {
        if (!Prefs.adblockEnabled()) return;
        if (url == null || url.startsWith("data:") || url.startsWith("about:")) return;
        try {
            java.io.InputStream in = getAssets().open("adblock/cosmetics.css");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            final String css = bo.toString("UTF-8");
            tab.web.post(() -> tab.web.evaluateJavascript(
                    "(function(){var s=document.createElement('style');" +
                            "s.id='devbrowser-cosmetics';s.textContent=" +
                            JSONObject.quote(css) + ";" +
                            "document.head.appendChild(s);})()", null));
        } catch (Exception ignored) {
        }
    }

    private void saveHistory(Tab tab, String url) {
        if (tab.incognito && !Prefs.savePrivateHistory()) return;
        if (url == null || url.startsWith("data:") || url.startsWith("about:")) return;
        Store.get(this).addHistory(tab.title, url, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------
    //  Tab chips
    // ------------------------------------------------------------------
    private void selectTab(Tab tab) {
        current = tab;
        for (Tab t : tabs) t.web.setVisibility(t == tab ? View.VISIBLE : View.GONE);
        address.setText(tab.url == null ? "" : tab.url);
        refreshChips();
        tabScroll.post(() -> tabScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void closeTab(Tab tab) {
        int idx = tabs.indexOf(tab);
        if (idx < 0) return;
        tab.web.stopLoading();
        viewContainer.removeView(tab.web);
        tab.web.destroy();
        tabs.remove(tab);
        if (current == tab) {
            current = null;
            if (!tabs.isEmpty()) {
                selectTab(tabs.get(Math.max(0, idx - 1)));
            } else {
                refreshChips();
                emptyHint.setVisibility(View.VISIBLE);
            }
        } else {
            refreshChips();
        }
    }

    private void refreshChips() {
        tabsContainer.removeAllViews();
        for (final Tab tab : tabs) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackgroundResource(R.drawable.bg_tab);
            chip.setSelected(tab == current);
            chip.setPadding(dp(12), 0, dp(2), 0);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(118), dp(34));
            clp.setMargins(0, 0, dp(4), 0);
            chip.setLayoutParams(clp);

            TextView title = new TextView(this);
            title.setText(tab.chipTitle());
            title.setTextColor(tab == current ? Color.WHITE : 0xFF9A9AB0);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(0, 0, dp(4), 0);
            chip.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView x = new ImageView(this);
            x.setImageResource(R.drawable.ic_close);
            x.setColorFilter(0xFF77778A);
            x.setPadding(dp(8), dp(8), dp(8), dp(8));
            chip.addView(x, new LinearLayout.LayoutParams(dp(28), dp(28)));

            chip.setOnClickListener(v -> selectTab(tab));
            x.setOnClickListener(v -> closeTab(tab));
            tabsContainer.addView(chip);
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------------------
    //  Menu
    // ------------------------------------------------------------------
    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.new_tab);
        pm.getMenu().add(0, 2, 1, R.string.new_incognito_tab);
        pm.getMenu().add(0, 3, 2, R.string.menu_add_bookmark);
        pm.getMenu().add(0, 4, 3, R.string.menu_bookmarks);
        pm.getMenu().add(0, 5, 4, R.string.menu_history);
        pm.getMenu().add(0, 6, 5, R.string.menu_downloads);
        pm.getMenu().add(0, 7, 7, R.string.menu_view_source);
        pm.getMenu().add(0, 8, 8, R.string.menu_console);
        pm.getMenu().add(0, 13, 9, R.string.menu_network_log);
        pm.getMenu().add(0, 14, 10, R.string.menu_inspect);
        pm.getMenu().add(0, 15, 11, R.string.menu_workspace);
        pm.getMenu().add(0, 9, 12, R.string.menu_ua);
        pm.getMenu().add(0, 10, 13, R.string.menu_screenshot);
        pm.getMenu().add(0, 11, 14, R.string.menu_share);
        pm.getMenu().add(0, 12, 15, R.string.menu_settings);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: newTab(false); break;
                case 2: newTab(true); break;
                case 3: addBookmark(); break;
                case 4: startActivity(new Intent(this, BookmarksActivity.class)); break;
                case 5: startActivity(new Intent(this, HistoryActivity.class)); break;
                case 6: startActivity(new Intent(this, DownloadsActivity.class)); break;
                case 7: viewSource(); break;
                case 8: toggleConsole(); break;
                case 9: chooseUserAgent(); break;
                case 10: screenshot(); break;
                case 11: sharePage(); break;
                case 12: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 13: startActivity(new Intent(this, NetworkLogActivity.class)); break;
                case 14:
                    inspectMode = !inspectMode;
                    Toast.makeText(this, inspectMode ? "Inspect: tap an element"
                            : "Inspect off", Toast.LENGTH_SHORT).show();
                    break;
                case 15: startActivity(new Intent(this, WorkspaceActivity.class)); break;
            }
            return true;
        });
        pm.show();
    }

    private void addBookmark() {
        if (current == null) return;
        Store store = Store.get(this);
        if (store.isBookmarked(current.url)) {
            Toast.makeText(this, R.string.toast_bookmark_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        store.addBookmark(current.title, current.url, System.currentTimeMillis());
        Toast.makeText(this, R.string.toast_bookmark_added, Toast.LENGTH_SHORT).show();
    }

    private void viewSource() {
        if (current == null) return;
        DevTools.viewSource(current.web, source -> {
            com.devbrowser.app.VolatileSource.set(source);
            startActivity(new Intent(this, ViewSourceActivity.class));
        });
    }

    private void toggleConsole() {
        boolean show = consolePanel.getVisibility() != View.VISIBLE;
        consolePanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) refreshPanel();
    }

    private void chooseUserAgent() {
        String[] names = {
                getString(R.string.settings_ua_default),
                getString(R.string.settings_ua_desktop),
                getString(R.string.settings_ua_android),
                getString(R.string.settings_ua_custom)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_ua)
                .setSingleChoiceItems(names, Prefs.uaMode(), (d, which) -> {
                    Prefs.setUaMode(which);
                    d.dismiss();
                    Toast.makeText(this, R.string.toast_restart_for_ua, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void screenshot() {
        if (current == null) return;
        File out = DevTools.screenshot(current.web, DevBrowserApp.get().downloadDir(),
                "screenshot_" + System.currentTimeMillis() + ".png");
        Toast.makeText(this, out == null ? "Failed" : getString(R.string.toast_screenshot_saved),
                Toast.LENGTH_SHORT).show();
    }

    private void sharePage() {
        if (current == null) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, current.title + "\n" + current.url);
        startActivity(Intent.createChooser(send, getString(R.string.menu_share)));
    }

    // ------------------------------------------------------------------
    //  Dev console
    // ------------------------------------------------------------------
    private void wireConsole() {
        findViewById(R.id.consoleClose).setOnClickListener(v -> {
            consolePanel.setVisibility(View.GONE);
            Toast.makeText(this, R.string.console_hidden, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.consoleClear).setOnClickListener(v -> {
            if (current != null) current.logs.clear();
            refreshPanel();
        });
        Button run = findViewById(R.id.consoleRun);
        run.setOnClickListener(v -> evalConsole());
        consoleInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) { evalConsole(); return true; }
            return false;
        });
    }

    private void evalConsole() {
        if (current == null) return;
        String js = consoleInput.getText().toString();
        if (js.trim().isEmpty()) return;
        DevTools.evaluateAndLog(current.web, current, js);
        consoleInput.setText("");
    }

    @Override
    public void onLog(Tab tab) {
        if (tab == current) refreshPanel();
    }

    private void refreshPanel() {
        if (current == null) return;
        StringBuilder sb = new StringBuilder();
        for (String line : current.logs) sb.append(line).append('\n');
        consoleLog.setText(sb.toString());
        consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------
    @Override
    public void onBackPressed() {
        if (consolePanel.getVisibility() == View.VISIBLE) {
            consolePanel.setVisibility(View.GONE);
            return;
        }
        if (current != null && current.web.canGoBack()) {
            current.web.goBack();
            return;
        }
        if (tabs.size() > 1) {
            closeTab(current);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) {
            t.web.stopLoading();
            t.web.destroy();
        }
        super.onDestroy();
    }
}
