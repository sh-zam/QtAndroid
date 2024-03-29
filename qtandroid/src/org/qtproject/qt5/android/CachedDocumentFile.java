package org.qtproject.qt5.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

public class CachedDocumentFile {

    private static final String TAG = "CachedDocumentFile";
    private String name;
    private final String mimeType;
    private final String documentId;
    // TODO(sh_zam): do something
    private Integer size;
    private Uri uri;
    private final Context ctx;
    private Boolean exists = null;
    private Boolean writable = null;

    public CachedDocumentFile(Context context, String name, String documentId, String mimeType, Integer size, Uri uri) {
        this.name = name;
        this.documentId = documentId;
        this.mimeType = mimeType;
        this.size = size;
        this.uri = uri;
        this.ctx = context;
    }

    public CachedDocumentFile(Context context, String name, String documentId, String mimeType, Uri uri) {
        this(context, name, documentId, mimeType, -1, uri);
    }

    public static CachedDocumentFile fromFileUri(Context context, Uri uri) {
        final String[] columns = new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
        };

        Cursor cursor = null;
        try {
            final ContentResolver resolver = context.getContentResolver();
            cursor = resolver.query(uri, columns, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return new CachedDocumentFile(context,
                        SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_MIME_TYPE),
                        SAFUtils.getColumnValIntegerOrDefault(cursor, DocumentsContract.Document.COLUMN_SIZE, -1),
                        uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "fromFileUri(): " + e);
        } finally {
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

    /**
     * @return document uri
     */
    public Uri getUri() {
        return uri;
    }

    public boolean isFile() {
        return !isDirectory() && !TextUtils.isEmpty(mimeType);
    }

    public boolean isDirectory() {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    public long getSize() {
        return queryForLong(DocumentsContract.Document.COLUMN_SIZE, 0);
    }

    public boolean rename(String displayName) {
        try {
            final Uri newUri = DocumentsContract.renameDocument(
                    ctx.getContentResolver(), uri, displayName);
            if (newUri == null || newUri == uri) {
                return false;
            }
            this.name = displayName;
            this.uri = newUri;
            return true;
        } catch (Exception e) {
            // HACK: see https://crbug.com/1246925.
            if (SAFFileManager.isArc()) {
                String oldUriStr = uri.toString();
                this.uri = Uri.parse(oldUriStr.replaceFirst(this.name + "$", displayName));
                this.exists = null;
                if (exists()) {
                    this.name = displayName;
                    return true;
                } else {
                    this.uri = Uri.parse(oldUriStr);
                    return false;
                }
            }
            Log.e(TAG, "rename(): Rename failed: " + e);
            return false;
        }
    }

    public boolean canWrite() {
        if (writable != null) {
            return writable;
        }
        writable = false;
        Cursor cursor = null;
        try {
            final ContentResolver resolver = ctx.getContentResolver();
            final String[] columns = {DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.COLUMN_MIME_TYPE};
            cursor = resolver.query(uri, columns, null, null, null);
            int flags = 0;
            String mimeType = null;
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    flags = SAFUtils.getColumnValIntegerOrDefault(cursor,
                            DocumentsContract.Document.COLUMN_FLAGS, 0);
                    mimeType = SAFUtils.getColumnValStringOrNull(cursor,
                            DocumentsContract.Document.COLUMN_MIME_TYPE);
                }
            }

            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) &&
                    (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0) {
                writable = true;
            } else if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0) {
                writable = true;
            }

        } catch (Exception e) {
            Log.e(TAG, "canWrite(): Failed query: " + e);
            writable = false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return writable;
    }

    public boolean exists() {
        if (exists != null) {
            return exists;
        }

        Cursor cursor = null;
        try {
            final ContentResolver resolver = ctx.getContentResolver();
            final String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID};
            cursor = resolver.query(uri, columns, null, null, null);
            exists = cursor !=null && cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "exists(): Failed query: " + e);
            exists = false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return exists;
    }

    private long queryForLong(String column, long defaultValue) {
        Cursor cursor = null;
        try {
            final ContentResolver resolver = ctx.getContentResolver();
            final String[] columns = {column};
            cursor = resolver.query(uri, columns, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            Log.e(TAG, "queryForLong(): Failed query: " + e);
            return defaultValue;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof CachedDocumentFile) {
            return ((CachedDocumentFile) other).getUri().equals(this.getUri());
        }
        return false;
    }
}

