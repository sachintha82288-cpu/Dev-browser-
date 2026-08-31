package com.devbrowser.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.devbrowser.app.db.Store;
import com.devbrowser.app.network.AdBlocker;
import com.devbrowser.app.network.LocalProxyServer;
import com.devbrowser.app.network.ProxyManager;
import com.devbrowser.app.network.TorManager;
import com.devbrowser.app.util.Prefs;

public class SettingsActivity extends Activity {

    private Switch proxySwitch;
    private EditText proxyHost, proxyPort;
    private TextView proxyStatus;
    private EditText homepage;
    private Spinner engine, ua;
    private EditText customUa;
    private Switch jsSwitch, imgSwitch, privSwitch;
    private EditText socksHost, socksPort;
    private Switch localProxy, adblockSwitch, cookieSwitch;
    private EditText adblockRules;
    private TextView localProxyStatus;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView root = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(8), dp(12), dp(24));
        root.addView(col);
        setContentView(root);

        // ---------- Proxy & Tor ----------
        col.addView(section(getString(R.string.settings_proxy)));
        proxyStatus = new TextView(this);
        col.addView(proxyStatus);

        proxySwitch = new Switch(this);
        proxySwitch.setText(getString(R.string.settings_proxy_enabled));
        col.addView(switchRow(proxySwitch));

        proxyHost = field(getString(R.string.settings_proxy_host));
        proxyPort = field(getString(R.string.settings_proxy_port));
        proxyPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(proxyHost);
        col.addView(proxyPort);

        LinearLayout proxyBtns = row();
        Button apply = button(getString(R.string.settings_apply_proxy));
        Button clear = ghost(getString(R.string.settings_clear_proxy));
        proxyBtns.addView(apply);
        proxyBtns.addView(clear);
        col.addView(proxyBtns);

        LinearLayout torBtns = row();
        Button preset = button(getString(R.string.settings_use_tor));
        Button orbot = ghost(getString(R.string.settings_launch_orbot));
        torBtns.addView(preset);
        torBtns.addView(orbot);
        col.addView(torBtns);

        TextView proxyNote = note(getString(R.string.settings_proxy_best_effort));
        col.addView(proxyNote);

        // ---------- Tor SOCKS5 (in-app local proxy) ----------
        col.addView(section(getString(R.string.settings_tor_socks)));
        localProxyStatus = new TextView(this);
        col.addView(localProxyStatus);
        localProxy = new Switch(this);
        localProxy.setText(getString(R.string.settings_local_proxy));
        col.addView(switchRow(localProxy));

        socksHost = field(getString(R.string.settings_socks_host));
        socksPort = field(getString(R.string.settings_socks_port));
        socksPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(socksHost);
        col.addView(socksPort);

        LinearLayout socksBtns = row();
        Button applySocks = button(getString(R.string.settings_apply_local));
        Button stopSocks = ghost(getString(R.string.settings_stop_local));
        socksBtns.addView(applySocks);
        socksBtns.addView(stopSocks);
        col.addView(socksBtns);

        // ---------- Privacy ----------
        col.addView(section(getString(R.string.settings_privacy)));
        adblockSwitch = new Switch(this);
        adblockSwitch.setText(getString(R.string.settings_adblock));
        cookieSwitch = new Switch(this);
        cookieSwitch.setText(getString(R.string.settings_block_3p_cookies));
        col.addView(switchRow(adblockSwitch));
        col.addView(switchRow(cookieSwitch));

        adblockRules = new EditText(this);
        adblockRules.setHint(getString(R.string.settings_adblock_rules));
        adblockRules.setTextColor(Color.WHITE);
        adblockRules.setHintTextColor(0xFF55556A);
        adblockRules.setBackgroundResource(R.drawable.bg_input);
        adblockRules.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        adblockRules.setTypeface(android.graphics.Typeface.MONOSPACE);
        adblockRules.setMinLines(3);
        adblockRules.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(dp(16), dp(4), dp(16), dp(4));
        adblockRules.setLayoutParams(rlp);
        col.addView(adblockRules);

        // ---------- Browser ----------
        col.addView(section(getString(R.string.settings_browser)));
        homepage = field(getString(R.string.settings_homepage));
        col.addView(homepage);

        engine = spinner(new String[]{"DuckDuckGo", "Google", "Bing"});
        ua = spinner(new String[]{
                getString(R.string.settings_ua_default),
                getString(R.string.settings_ua_desktop),
                getString(R.string.settings_ua_android),
                getString(R.string.settings_ua_custom)});
        col.addView(label(getString(R.string.settings_default_ua)));
        col.addView(ua);

        customUa = field(getString(R.string.settings_ua_custom));
        col.addView(customUa);

        jsSwitch = new Switch(this);
        jsSwitch.setText(getString(R.string.settings_js_enabled));
        imgSwitch = new Switch(this);
        imgSwitch.setText(getString(R.string.settings_block_images));
        privSwitch = new Switch(this);
        privSwitch.setText(getString(R.string.settings_incognito_history));
        col.addView(switchRow(jsSwitch));
        col.addView(switchRow(imgSwitch));
        col.addView(switchRow(privSwitch));

        LinearLayout dataBtns = row();
        Button clearHistory = ghost(getString(R.string.settings_clear_history));
        dataBtns.addView(clearHistory);
        col.addView(dataBtns);

        // ---------- About ----------
        col.addView(section(getString(R.string.settings_about)));
        TextView about = new TextView(this);
        about.setText(getString(R.string.app_name) + " " + getString(R.string.settings_version) + " 1.1.0\n"
                + getString(R.string.app_tagline) + "\n\n"
                + "minSdk 24 · targetSdk 34 · pure WebView (no androidx)\n"
                + "Tor: Orbot SOCKS5 + in-app proxy · ad-block: " +
                AdBlocker.get(this).ruleCount() + " rules");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        about.setTextColor(0xFF9A9AB0);
        about.setPadding(dp(16), dp(8), dp(16), dp(8));
        col.addView(about);

        // ---------- Wires ----------
        load();
        proxySwitch.setOnCheckedChangeListener((b, v) -> { if (!loading) applyProxy(); });
        apply.setOnClickListener(v -> applyProxy());
        clear.setOnClickListener(v -> {
            Prefs.setProxyEnabled(false);
            Prefs.setLocalProxyEnabled(false);
            LocalProxyServer.get().stop();
            ProxyManager.clearProxy((ok, detail) -> runOnUiThread(() ->
                    Toast.makeText(this, ok ? getString(R.string.settings_proxy_state_off) : detail, Toast.LENGTH_LONG).show()));
            loadStatus();
            loadLocalStatus();
        });
        preset.setOnClickListener(v -> {
            proxyHost.setText(TorManager.TOR_PROXY.split(":")[0]);
            proxyPort.setText(TorManager.TOR_PROXY.split(":")[1]);
            proxySwitch.setChecked(true);
            applyProxy();
        });
        localProxy.setOnCheckedChangeListener((b, v) -> {
            if (loading) return;
            if (v) applyLocalProxy();
            else stopLocalProxy();
        });
        applySocks.setOnClickListener(v -> applyLocalProxy());
        stopSocks.setOnClickListener(v -> stopLocalProxy());
        adblockSwitch.setOnCheckedChangeListener((b, v) -> {
            Prefs.setAdblockEnabled(v);
            com.devbrowser.app.network.AdBlocker.get(this).reload(this);
        });
        cookieSwitch.setOnCheckedChangeListener((b, v) -> {
            Prefs.setThirdPartyCookiesAllowed(!v);
            Toast.makeText(this, "Cookies: apply to new tabs", Toast.LENGTH_SHORT).show();
        });
        orbot.setOnClickListener(v -> {
            if (!TorManager.openOrbot(this)) return;
            if (!TorManager.isOrbotInstalled(this)) {
                Toast.makeText(this, R.string.settings_orbot_missing, Toast.LENGTH_LONG).show();
            }
        });
        clearHistory.setOnClickListener(v -> {
            Store.get(this).clearHistory();
            Toast.makeText(this, R.string.title_history + " ✓", Toast.LENGTH_SHORT).show();
        });
    }

    // ------------------------------------------------------------------
    private void load() {
        loading = true;
        proxyHost.setText(Prefs.proxyHost());
        proxyPort.setText(String.valueOf(Prefs.proxyPort()));
        proxySwitch.setChecked(Prefs.proxyEnabled());
        homepage.setText(Prefs.homepage());
        engine.setSelection(Prefs.searchEngine());
        ua.setSelection(Prefs.uaMode());
        customUa.setText(Prefs.customUa());
        jsSwitch.setChecked(Prefs.jsEnabled());
        imgSwitch.setChecked(Prefs.blockImages());
        privSwitch.setChecked(Prefs.savePrivateHistory());
        socksHost.setText(Prefs.socksHost());
        socksPort.setText(String.valueOf(Prefs.socksPort()));
        localProxy.setChecked(Prefs.localProxyEnabled());
        localProxy.setChecked(LocalProxyServer.get().isRunning());
        adblockSwitch.setChecked(Prefs.adblockEnabled());
        cookieSwitch.setChecked(!Prefs.thirdPartyCookiesAllowed());
        adblockRules.setText(Prefs.adblockUserRules());
        loadStatus();
        loadLocalStatus();
        loading = false;
    }

    private void loadStatus() {
        boolean on = Prefs.proxyEnabled();
        proxyStatus.setText(on
                ? getString(R.string.settings_proxy_state_on) + " — " + Prefs.proxyHost() + ":" + Prefs.proxyPort()
                : getString(R.string.settings_proxy_state_off));
        proxyStatus.setTextColor(on ? 0xFF4CD97B : 0xFF9A9AB0);
    }

    private void applyProxy() {
        Prefs.setProxyEnabled(proxySwitch.isChecked());
        Prefs.setProxyHost(proxyHost.getText().toString().trim());
        try {
            Prefs.setProxyPort(Integer.parseInt(proxyPort.getText().toString().trim()));
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.toast_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Prefs.proxyEnabled()) {
            ProxyManager.setProxy(Prefs.proxyHost(), Prefs.proxyPort(), (ok, detail) -> runOnUiThread(() -> {
                loadStatus();
                Toast.makeText(this, ok ? getString(R.string.settings_proxy_state_on) : detail, Toast.LENGTH_LONG).show();
            }));
        } else {
            ProxyManager.clearProxy((ok, detail) -> runOnUiThread(() -> {
                loadStatus();
                if (!ok) Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
            }));
        }
    }

    private void applyLocalProxy() {
        Prefs.setSocksHost(socksHost.getText().toString().trim());
        int socks;
        try {
            socks = Integer.parseInt(socksPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.toast_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setSocksPort(socks);
        final LocalProxyServer lp = LocalProxyServer.get();
        lp.configure(Prefs.socksHost(), Prefs.socksPort(), true);
        lp.setStatusListener((ok, port, detail) -> runOnUiThread(() -> {
            if (ok) {
                Prefs.setLocalProxyEnabled(true);
                Prefs.setProxyEnabled(true);
                Prefs.setProxyHost("127.0.0.1");
                Prefs.setProxyPort(port);
                localProxy.setChecked(true);
                ProxyManager.setProxy("127.0.0.1", port, (p, d) -> runOnUiThread(() -> {
                    loadStatus();
                    loadLocalStatus();
                    Toast.makeText(this, p ? "Tor proxy: 127.0.0.1:" + port : d,
                            Toast.LENGTH_LONG).show();
                }));
            } else {
                Toast.makeText(this, "Proxy error: " + detail, Toast.LENGTH_LONG).show();
                loadLocalStatus();
            }
        }));
        lp.start();
    }

    private void stopLocalProxy() {
        LocalProxyServer.get().stop();
        Prefs.setLocalProxyEnabled(false);
        Prefs.setProxyEnabled(false);
        localProxy.setChecked(false);
        ProxyManager.clearProxy((ok, detail) -> runOnUiThread(() -> {
            loadStatus();
            loadLocalStatus();
        }));
    }

    private void loadLocalStatus() {
        boolean on = LocalProxyServer.get().isRunning();
        localProxyStatus.setText(on
                ? "127.0.0.1:" + LocalProxyServer.get().port() + " → SOCKS " + Prefs.socksHost() + ":" + Prefs.socksPort()
                : getString(R.string.settings_proxy_state_off));
        localProxyStatus.setTextColor(on ? 0xFF4CD97B : 0xFF9A9AB0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.setHomepage(homepage == null ? Prefs.homepage() : homepage.getText().toString().trim());
        if (engine != null) Prefs.setSearchEngine(engine.getSelectedItemPosition());
        if (ua != null) Prefs.setUaMode(ua.getSelectedItemPosition());
        if (customUa != null) Prefs.setCustomUa(customUa.getText().toString().trim());
        if (jsSwitch != null) Prefs.setJsEnabled(jsSwitch.isChecked());
        if (imgSwitch != null) Prefs.setBlockImages(imgSwitch.isChecked());
        if (privSwitch != null) Prefs.setSavePrivateHistory(privSwitch.isChecked());
        if (socksHost != null) Prefs.setSocksHost(socksHost.getText().toString().trim());
        if (adblockRules != null) Prefs.setAdblockUserRules(adblockRules.getText().toString());
    }

    // ------------------------------------------------------------------
    //  tiny UI builders
    // ------------------------------------------------------------------
    private TextView section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(0xFF00E7FF);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(dp(16), dp(20), dp(16), dp(4));
        return v;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(0xFF9A9AB0);
        v.setPadding(dp(16), dp(10), dp(16), dp(2));
        return v;
    }

    private TextView note(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTextColor(0xFF77778A);
        v.setPadding(dp(16), dp(8), dp(16), dp(4));
        return v;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF55556A);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.setMargins(dp(16), dp(4), dp(16), dp(4));
        e.setLayoutParams(lp);
        return e;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(ad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.setMargins(dp(16), dp(4), dp(16), dp(4));
        s.setLayoutParams(lp);
        return s;
    }

    private Switch switchRow(Switch s) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(dp(16), 0, dp(16), 0);
        s.setLayoutParams(lp);
        s.setTextColor(0xFFF2F2F7);
        return s;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(8), dp(16), dp(0));
        return r;
    }

    private Button button(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setBackgroundResource(R.drawable.bg_btn_primary);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        return b;
    }

    private Button ghost(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setBackgroundResource(R.drawable.bg_btn_ghost);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        return b;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
