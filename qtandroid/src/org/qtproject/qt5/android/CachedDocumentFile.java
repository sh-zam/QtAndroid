package org.qtproject.qt5.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

public class CachedDocumentFile {

    private static final String TAG = "CachedDocumentFile";
    private final String name;
    private final String mimeType;
    private final String documentId;
    private final Uri uri;

    public CachedDocumentFile(String name, String documentId, String mimeType, Uri uri) {
        this.name = name;
        this.documentId = documentId;
        this.mimeType = mimeType;
        this.uri = uri;
    }

    public static CachedDocumentFile fromFileUri(Uri uri) {

        final String[] columns = new String[] {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
        };

        Cursor cursor = null;
        try {
            final ContentResolver resolver = QtNative.getContext().getContentResolver();
            cursor = resolver.query(uri, columns, null, null, null);

            if (cursor.moveToFirst()) {
                return new CachedDocumentFile(
                        cursor.getString(0),  // display name
                        cursor.getString(1),  // document_id
                        cursor.getString(2),  // mimetype
                        uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "fromFileUri(): " + uri);
        }
        finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getDocumentId() {
        return documentId;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isFile() {
        return !isDirectory() && !TextUtils.isEmpty(mimeType);
    }

    public boolean isDirectory() {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    public boolean equals(Object other) {
        if (other instanceof CachedDocumentFile) {
            return ((CachedDocumentFile) other).getUri().equals(this.getUri());
        }
        return false;
    }
}
