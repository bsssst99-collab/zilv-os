package com.selfdrive.os;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;

public class FileProviderLite extends ContentProvider {

    public static final String AUTH = "com.selfdrive.os.files";

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTH + "/" + f.getName());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileOf(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) name = "shot.jpg";
        return new File(getContext().getCacheDir(), name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        File f = fileOf(uri);
        return ParcelFileDescriptor.open(f,
                ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = fileOf(uri);
        MatrixCursor c = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return "image/jpeg";
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
