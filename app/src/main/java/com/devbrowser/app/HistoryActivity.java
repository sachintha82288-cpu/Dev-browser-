package com.devbrowser.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.devbrowser.app.db.Store;
import com.devbrowser.app.util.U;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends Activity {

    private List<Store.Row> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView empty = new TextView(this);
        empty.setText(R.string.history_empty);
        empty.setTextColor(0xFF55556A);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(24, 24, 24, 24);
        empty.setVisibility(View.GONE);

        ListView list = new ListView(this);
        list.setBackgroundColor(0xFF0D0D13);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        reload(list, empty);

        list.setOnItemClickListener((parent, view, position, id) -> {
            Store.Row r = rows.get(position);
            open(r.url);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Store.get(this).removeHistory(rows.get(position).id);
            reload(list, empty);
            return true;
        });
    }

    private void reload(ListView list, TextView empty) {
        rows = Store.get(this).history(500);
        List<String> data = new ArrayList<>();
        String day = "";
        for (Store.Row r : rows) {
            String d = U.day(r.at);
            if (!d.equals(day)) {
                day = d;
                data.add("— " + d + " —");
            }
            data.add(r.displayTitle() + "\n" + r.url + "   " + U.time(r.at));
        }
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, data);
        list.setAdapter(ad);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void open(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("url", url);
        startActivity(i);
        finish();
    }
}
