package com.devbrowser.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.devbrowser.app.DevBrowserApp;
import com.devbrowser.app.util.U;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Tiny androidx-fileprovider replacement: shares files from the app's
 * external-files root so APK installs and "open with" work on Android 7+.
 *
 * Content URI scheme: content://com.devbrowser.app.files/<relative path>
 */
public class AppFileProvider extends ContentProvider {

    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Build a shareable content:// URI for a file inside the app's external-files dir. */
    public static Uri uri(File file) {
        File root = DevBrowserApp.get().getExternalFilesDir(null);
        String abs = file.getAbsolutePath();
        if (!abs.startsWith(root.getAbsolutePath())) {
            throw new IllegalArgumentException("File outside provider root");
        }
        String rel = abs.substring(root.getAbsolutePath().length() + 1);
        return new android.net.Uri.Builder()
                .scheme("content")
                .authority(DevBrowserApp.FILE_AUTHORITY)
                .appendEncodedPath(rel)
                .build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String rel = uri.getPath();
        if (rel != null && rel.startsWith("/")) rel = rel.substring(1);
        File root = DevBrowserApp.get().getExternalFilesDir(null);
        File f = new File(root, rel);
        String base = root.getAbsolutePath();
        if (!f.getAbsolutePath().startsWith(base)) {
            throw new FileNotFoundException("Outside provider root");
        }
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath());
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        return new AssetFileDescriptor(openFile(uri, mode), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public String getType(Uri uri) {
        try {
            return U.mimeFor(resolve(uri).getName());
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(COLUMNS);
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
