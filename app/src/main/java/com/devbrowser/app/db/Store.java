package com.devbrowser.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite storage for browsing history and bookmarks. */
public class Store extends SQLiteOpenHelper {

    private static final String DB_NAME = "devbrowser.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_HISTORY = "history";
    public static final String TABLE_BOOKMARKS = "bookmarks";

    private static Store instance;

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, url TEXT NOT NULL, visited_at INTEGER)");
        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, url TEXT NOT NULL UNIQUE, created_at INTEGER)");
        db.execSQL("CREATE INDEX idx_history_time ON " + TABLE_HISTORY + " (visited_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    // ---------------- history ----------------

    public void addHistory(String title, String url, long at) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        cv.put("visited_at", at);
        db.insert(TABLE_HISTORY, null, cv);
    }

    public int clearHistory() {
        return getWritableDatabase().delete(TABLE_HISTORY, null, null);
    }

    public void removeHistory(long id) {
        getWritableDatabase().delete(TABLE_HISTORY, "_id=?", new String[]{String.valueOf(id)});
    }

    /** Rows grouped by day, newest first. */
    public List<Row> history(int limit) {
        List<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_HISTORY,
                new String[]{"_id", "title", "url", "visited_at"},
                null, null, null, null, "visited_at DESC", String.valueOf(limit));
        while (c.moveToNext()) {
            out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return out;
    }

    // ---------------- bookmarks ----------------

    public boolean addBookmark(String title, String url, long at) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title == null ? "" : title);
        cv.put("url", url);
        cv.put("created_at", at);
        long id = db.insertWithOnConflict(TABLE_BOOKMARKS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        return id != -1;
    }

    public boolean isBookmarked(String url) {
        Cursor c = getReadableDatabase().query(TABLE_BOOKMARKS, new String[]{"_id"},
                "url=?", new String[]{url}, null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public void removeBookmark(long id) {
        getWritableDatabase().delete(TABLE_BOOKMARKS, "_id=?", new String[]{String.valueOf(id)});
    }

    public List<Row> bookmarks() {
        List<Row> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_BOOKMARKS,
                new String[]{"_id", "title", "url", "created_at"},
                null, null, null, null, "created_at DESC");
        while (c.moveToNext()) {
            out.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return out;
    }

    public static class Row {
        public final long id;
        public final String title;
        public final String url;
        public final long at;

        public Row(long id, String title, String url, long at) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.at = at;
        }

        public String displayTitle() {
            return title == null || title.isEmpty() ? url : title;
        }
    }
}
