package com.devbrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.devbrowser.app.core.NetworkLog;

import java.util.ArrayList;
import java.util.List;

/** Developer network log: every intercepted request with type/status/size. */
public class NetworkLogActivity extends Activity {

    private final String[] FILTERS = {"all", "document", "script", "style",
            "image", "media", "xhr", "fetch", "download", "blocked", "failed", "other"};

    private ListView list;
    private TextView empty;
    private Spinner filter;
    private String current = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(0xFF17171F);

        filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, FILTERS));
        bar.addView(filter, new LinearLayout.LayoutParams(dp(130),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button clear = new Button(this);
        clear.setText(R.string.console_clear);
        clear.setTextColor(Color.WHITE);
        clear.setBackgroundResource(R.drawable.bg_btn_ghost);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        clear.setPadding(dp(10), 0, dp(10), 0);
        clear.setOnClickListener(v -> {
            NetworkLog.clear();
            reload();
        });
        bar.addView(clear, new LinearLayout.LayoutParams(dp(96), dp(40)));

        TextView count = new TextView(this);
        count.setGravity(Gravity.END);
        count.setPadding(dp(12), 0, dp(4), 0);
        count.setTextColor(0xFF9A9AB0);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        bar.addView(count, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(bar);

        empty = new TextView(this);
        empty.setText(R.string.downloads_empty);
        empty.setTextColor(0xFF55556A);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(24, 24, 24, 24);
        empty.setVisibility(View.GONE);

        list = new ListView(this);
        list.setBackgroundColor(0xFF0D0D13);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setEmptyView(empty);
        root.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                current = FILTERS[pos];
                reload();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        list.setOnItemClickListener((p, v, pos, id) -> showDetail(entries.get(pos)));
        reload();
    }

    private List<NetworkLog.Entry> entries = new ArrayList<>();

    private void reload() {
        entries = NetworkLog.snapshot(current);
        List<String> rows = new ArrayList<>();
        for (NetworkLog.Entry e : entries) {
            String status;
            int color;
            switch (e.status()) {
                case 2: status = "BLOCKED"; color = 0xFFFF3EA5; break;
                case 3: status = "ERROR";  color = 0xFFFF5252; break;
                default: status = "OK";    color = 0xFF4CD97B; break;
            }
            rows.add(e.type + "  " + com.devbrowser.app.util.U.time(e.start) +
                    "  " + status + "  " + e.url);
        }
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        empty.setText(NetworkLog.size() == 0 ? getString(R.string.downloads_empty) : "0 matches");
    }

    private void showDetail(NetworkLog.Entry e) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(e.url).append('\n');
        sb.append("Type: ").append(e.type).append('\n');
        sb.append("Status: ").append(e.status() == 2 ? "BLOCKED" : e.status() == 3 ? "ERROR" : "OK").append('\n');
        sb.append("Duration: ").append(e.duration()).append(" ms\n");
        if (e.size >= 0) sb.append("Size: ").append(com.devbrowser.app.util.U.humanSize(e.size)).append('\n');
        sb.append("At: ").append(com.devbrowser.app.util.U.date(e.start));

        new AlertDialog.Builder(this)
                .setTitle("Request #" + e.id)
                .setMessage(sb.toString())
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("request", sb.toString()));
                    Toast.makeText(this, "copied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
