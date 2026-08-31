package com.devbrowser.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.devbrowser.app.dl.Downloads;
import com.devbrowser.app.model.DownloadItem;
import com.devbrowser.app.provider.AppFileProvider;
import com.devbrowser.app.util.U;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends Activity {

    private ListView list;
    private TextView empty;
    private List<DownloadItem> items = new ArrayList<>();
    private final List<String> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        empty = new TextView(this);
        empty.setText(R.string.downloads_empty);
        empty.setTextColor(0xFF55556A);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(24, 24, 24, 24);
        empty.setVisibility(View.GONE);

        list = new ListView(this);
        list.setBackgroundColor(0xFF0D0D13);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        Downloads.setListener(() -> runOnUiThread(this::reload));
        list.setOnItemClickListener((parent, view, position, id) -> handle(items.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            DownloadItem it = items.get(position);
            if (it.path != null) {
                new File(it.path).delete();
                reload();
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Downloads.setListener(null);
    }

    private void reload() {
        items.clear();
        items.addAll(Downloads.active());
        items.addAll(Downloads.files(this));
        data.clear();
        for (DownloadItem it : items) {
            data.add(it.display() + "\n" + it.progressText());
        }
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, data));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void handle(DownloadItem it) {
        if (it.isApk() && it.state == DownloadItem.STATE_DONE) {
            installApk(new File(it.path));
        } else if (it.state == DownloadItem.STATE_DONE) {
            openFile(new File(it.path));
        } else {
            Toast.makeText(this, it.progressText(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(File f) {
        try {
            Uri uri = AppFileProvider.uri(f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, U.mimeFor(f.getName()));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app for this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void installApk(File f) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.download_install_permission_required, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        try {
            Uri uri = AppFileProvider.uri(f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Package installer not available", Toast.LENGTH_SHORT).show();
        }
    }
}
