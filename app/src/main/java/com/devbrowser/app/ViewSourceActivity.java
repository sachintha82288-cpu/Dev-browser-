package com.devbrowser.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ViewSourceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Button copy = new Button(this);
        copy.setText(R.string.toast_source_copied);
        copy.setTextColor(Color.WHITE);
        copy.setBackgroundResource(R.drawable.bg_btn_primary);

        TextView src = new TextView(this);
        src.setText(VolatileSource.get());
        src.setBackgroundColor(0xFF10101A);
        src.setTextColor(0xFFC8E6FF);
        src.setTextSize(11);
        src.setTypeface(android.graphics.Typeface.MONOSPACE);
        src.setPadding(16, 12, 16, 12);
        src.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(src);
        root.addView(copy, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("source", VolatileSource.get()));
            Toast.makeText(this, R.string.toast_source_copied, Toast.LENGTH_SHORT).show();
        });
    }
}
