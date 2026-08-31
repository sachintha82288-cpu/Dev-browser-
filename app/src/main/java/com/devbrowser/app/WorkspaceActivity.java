package com.devbrowser.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.devbrowser.app.util.Prefs;

/** Tiny HTML/CSS/JS workspace with a live preview (pure data: URL, offline). */
public class WorkspaceActivity extends Activity {

    private EditText htmlEditor, cssEditor, jsEditor;
    private WebView preview;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView root = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(6), dp(10), dp(10));
        root.addView(col);

        htmlEditor = editor("HTML", "Text/HTML");
        cssEditor = editor("CSS", "Text/CSS");
        jsEditor = editor("JS", "Text/JS");

        col.addView(label("HTML"));
        col.addView(htmlEditor, h(130));
        col.addView(label("CSS"));
        col.addView(cssEditor, h(90));
        col.addView(label("JS"));
        col.addView(jsEditor, h(90));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button run = btn(getString(R.string.console_run));
        Button save = btn(getString(R.string.save));
        Button reset = btn("Reset");
        Button share = btn("Share");
        buttons.addView(run);
        buttons.addView(save);
        buttons.addView(reset);
        buttons.addView(share);
        col.addView(buttons);

        status = new TextView(this);
        status.setTextColor(0xFF55556A);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        status.setPadding(dp(6), dp(4), dp(6), dp(4));
        col.addView(status);

        preview = new WebView(this);
        WebSettings s = preview.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        preview.setBackgroundColor(Color.TRANSPARENT);
        preview.setWebViewClient(new WebViewClient());
        col.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        setContentView(root);
        loadContent();

        run.setOnClickListener(v -> render());
        save.setOnClickListener(v -> {
            Prefs.setWorkspaceHtml(htmlEditor.getText().toString());
            Prefs.setWorkspaceCss(cssEditor.getText().toString());
            Prefs.setWorkspaceJs(jsEditor.getText().toString());
            Toast.makeText(this, R.string.save + " ✓", Toast.LENGTH_SHORT).show();
        });
        reset.setOnClickListener(v -> {
            htmlEditor.setText("<!doctype html><html><body><h1>Hello</h1><p>DevBrowser</p></body></html>");
            cssEditor.setText("body{background:#0d0d13;color:#00e7ff;font-family:sans-serif;}h1{color:#ff3ea5;}");
            jsEditor.setText("document.body.append('\\nJS works: ' + (1+1));");
            Prefs.setWorkspaceHtml(htmlEditor.getText().toString());
            Prefs.setWorkspaceCss(cssEditor.getText().toString());
            Prefs.setWorkspaceJs(jsEditor.getText().toString());
            render();
        });
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/html");
            send.putExtra(Intent.EXTRA_TEXT, buildDocument());
            startActivity(Intent.createChooser(send, "Share workspace"));
        });
        render();
    }

    private void loadContent() {
        htmlEditor.setText(Prefs.workspaceHtml());
        cssEditor.setText(Prefs.workspaceCss());
        jsEditor.setText(Prefs.workspaceJs());
        if (htmlEditor.getText().toString().isEmpty()) {
            htmlEditor.setText("<!doctype html><html><body><h1>Hello</h1><p>DevBrowser</p></body></html>");
            cssEditor.setText("body{background:#0d0d13;color:#00e7ff;font-family:sans-serif;}h1{color:#ff3ea5;}");
            jsEditor.setText("document.body.append('\\nJS works: ' + (1+1));");
        }
    }

    private String buildDocument() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<style>").append(cssEditor.getText()).append("</style></head><body>")
          .append(htmlEditor.getText())
          .append("<script>").append(jsEditor.getText()).append("</script></body></html>");
        return sb.toString();
    }

    private void render() {
        String doc = buildDocument();
        preview.loadUrl("data:text/html;charset=utf-8," + Uri.encode(doc));
        status.setText("preview · " + doc.length() + " chars");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.setWorkspaceHtml(htmlEditor.getText().toString());
        Prefs.setWorkspaceCss(cssEditor.getText().toString());
        Prefs.setWorkspaceJs(jsEditor.getText().toString());
    }

    @Override
    protected void onDestroy() {
        preview.destroy();
        super.onDestroy();
    }

    private EditText editor(String tag, String mime) {
        EditText e = new EditText(this);
        e.setSingleLine(false);
        e.setMinLines(3);
        e.setTextColor(0xFFE8F4FF);
        e.setHintTextColor(0xFF55556A);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        e.setTypeface(android.graphics.Typeface.MONOSPACE);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFF00E7FF);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(dp(6), dp(8), dp(6), dp(2));
        return v;
    }

    private Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setBackgroundResource(R.drawable.bg_btn_ghost);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private LinearLayout.LayoutParams h(int px) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
