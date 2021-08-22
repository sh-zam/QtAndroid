package org.qtproject.qt5.android;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.Pair;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("unused")
class FileError {
    public static final int NO_ERROR = 0;
    public static final int READ_ERROR = 1;
    public static final int WRITE_ERROR = 2;
    public static final int FATAL_ERROR = 3;
    public static final int RESOURCE_ERROR = 4;
    public static final int OPEN_ERROR = 5;
    public static final int ABORT_ERROR = 6;
    public static final int TIME_OUT_ERROR = 7;
    public static final int UNSPECIFIED_ERROR = 8;
    public static final int REMOVE_ERROR = 9;
    public static final int RENAME_ERROR = 10;
    public static final int POSITION_ERROR = 11;
    public static final int RESIZE_ERROR = 12;
    public static final int PERMISSIONS_ERROR = 13;
    public static final int COPY_ERROR = 14;

    private String errorString;
    private int error;

    public String getErrorString() {
        return errorString;
    }

    public void setErrorString(String errorString) {
        this.errorString = errorString;
    }

    public int getError() {
        return error;
    }

    public void setError(int error) {
        this.error = error;
    }

    public void setUnknownError() {
        setError(FileError.UNSPECIFIED_ERROR);
        setErrorString("Unknown Error");
    }

    public void unsetError() {
        setError(NO_ERROR);
        setErrorString("No error");
    }
}

// Native usage
@SuppressWarnings("UnusedDeclaration")
public class SAFFileManager {

    private static final String TAG = "SAFFileManager";
    private static final String PATH_TREE = "tree";

    @SuppressLint("StaticFieldLeak") // TODO(sh_zam): we only have one activity!
    private static SAFFileManager sSafFileManager;

    private final Context mCtx;
    private final ArrayList<CachedDocumentFile> mCachedDocumentFiles = new ArrayList<>();
    private final HashMap<Integer, ParcelFileDescriptor> m_parcelFileDescriptors = new HashMap<>();

    private final FileError mError = new FileError();

    SAFFileManager(Context ctx) {
        mCtx = ctx;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public static SAFFileManager instance() {
        if (sSafFileManager == null) {
            sSafFileManager = new SAFFileManager(QtNative.getContext());
        }
        return sSafFileManager;
    }

    private boolean checkImplicitUriPermission(Uri uri, String openMode) {
        int modeFlags = 0;
        if (openMode.startsWith("r")) {
            modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }

        if (!"r".equals(openMode)) {
            modeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }

        return mCtx.checkCallingOrSelfUriPermission(uri, modeFlags) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * The encoding of Path segments is somewhat arbitrary and something which doesn't
     * seem very safe to rely on. So if we find a matching path we just return
     * the persisted Uri.<br>
     * <p>
     * This function also handles the permission check of implicitly sent Uris.
     *
     * @param uri      content Uri, can be Tree Uri or a Single file Uri
     * @param openMode mode to open the file in
     * @return The persisted Uri opened through ACTION_OPEN_DOCUMENT,
     * ACTION_OPEN_DOCUMENT_TREE i.e only if we have permission to the Uri.
     */
    Uri getProperlyEncodedUriWithPermissions(Uri uri, String openMode) {
        // TODO(sh_zam): check if caching is needed?
        List<UriPermission> permissions =
                mCtx.getContentResolver().getPersistedUriPermissions();
        String uriStr = uri.getPath();

        for (int i = 0; i < permissions.size(); ++i) {
            Uri iterUri = permissions.get(i).getUri();
            boolean isRightPermission = permissions.get(i).isReadPermission();

            if (!openMode.equals("r"))
                isRightPermission = permissions.get(i).isWritePermission();

            if (iterUri.getPath().equals(uriStr) && isRightPermission) {
                return iterUri;
            }
        }

        // TODO(sh_zam): check encoding
        // check if we received permission from an Intent
        return checkImplicitUriPermission(uri, openMode) ? uri : null;
    }

    /**
     * Check if we have permission to the Uri. This method also handles tree Uris.
     * If a Tree Uri is passed in {@code openMode} including write permissions
     * we create the file under the tree if we have permissions to it.<br>
     * <p>
     * Note: The results are undefined for Uris of all types other than:
     * {@code tree/*} and {@code document/*}.
     *
     * @param url      content Uri, can be Tree Uri or a Single file Uri
     * @param openMode mode to open the file in
     * @return Uri of the newly created file or passed in url if we have
     * permission to access the document, otherwise null.
     */
    private Uri getUriWithValidPermission(String url, String openMode) {
        final Uri uri = Uri.parse(url);

        // it is a file in tree, so we create a new file if "w"
        if (isTreeUri(uri)) {
            Pair<Uri, Uri> separatedUriPair = nearestTreeUri(uri);
            if (separatedUriPair == null) {
                // FIXME(sh_zam): propagate error for all Log.d()s
                mError.setError(FileError.PERMISSIONS_ERROR);
                mError.setErrorString("No permission to access the Document Tree");
                return null;
            }

            CachedDocumentFile foundFile =
                    findFileInTree(separatedUriPair.first,
                            separatedUriPair.second.getPathSegments());

            if (foundFile != null) {
                return foundFile.getUri();
            }

            // we shouldn't create a file here
            if ("r".equals(openMode)) {
                return null;
            }

            return createFile(separatedUriPair.first, separatedUriPair.second, false);
        } else {
            Uri resultUri = getProperlyEncodedUriWithPermissions(uri, openMode);
            if (resultUri != null) {
                return resultUri;
            }
        }

        mError.setError(FileError.PERMISSIONS_ERROR);
        mError.setErrorString("No permission to access the Uri");
        return null;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private boolean launchUri(String url, String mime) {
        Uri uri;
        if (url.startsWith("content:")) {
            uri = getUriWithValidPermission(url, "r");
            if (uri == null) {
                Log.e(TAG, "launchUri(): No permissions to open Uri");
                return false;
            }
        } else {
            uri = Uri.parse(url);
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!mime.isEmpty())
                intent.setDataAndType(uri, mime);

            QtNative.activity().startActivity(intent);

            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "launchUri(): Invalid Uri");
            return false;
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "launchUri(): Unsupported operation for given Uri");
            return false;
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private int openFileDescriptor(String contentUrl, String openMode) {
        int error = -1;

        Uri uri = getUriWithValidPermission(contentUrl, openMode);

        if (uri == null) {
            return error;
        }

        try {
            ContentResolver resolver = mCtx.getContentResolver();
            ParcelFileDescriptor fdDesc = resolver.openFileDescriptor(uri, openMode);
            m_parcelFileDescriptors.put(fdDesc.getFd(), fdDesc);

            mError.unsetError();
            return fdDesc.getFd();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, "openFileDescriptor(): Failed query: " + e);
        }

        mError.setError(FileError.WRITE_ERROR);
        mError.setErrorString("Couldn't open file for writing");
        return error;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private boolean closeFileDescriptor(int fd) {
        ParcelFileDescriptor pfd = m_parcelFileDescriptors.get(fd);
        if (pfd == null) {
            Log.wtf(TAG, "File descriptor doesn't exist in cache");
            return false;
        }

        try {
            mError.unsetError();
            pfd.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "closeFileDescriptor(): Failed to close the FD", e);
        }
        mError.setUnknownError();
        return false;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private long getSize(String contentUrl) {
        Uri uri = getUriWithValidPermission(contentUrl, "r");
        long size = -1;

        if (uri == null) {
            return size;
        }

        try {
            ContentResolver resolver = mCtx.getContentResolver();
            final String[] columns = {DocumentsContract.Document.COLUMN_SIZE};
            Cursor cur = resolver.query(uri, columns, null, null, null);

            if (cur != null) {
                if (cur.moveToFirst())
                    size = cur.getLong(0);
                cur.close();
            }
            mError.unsetError();
            return size;
        } catch (Exception e) {
            Log.e(TAG, "getSize(): Failed query: " + e);
        }

        mError.setUnknownError();
        return size;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private boolean exists(String contentUrl) {
        final Uri uri = getUriWithValidPermission(contentUrl, "r");

        if (uri == null) {
            return false;
        }
        Cursor cursor = null;
        try {
            final ContentResolver resolver = mCtx.getContentResolver();
            final String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID};
            cursor = resolver.query(uri, columns, null, null, null);

            mError.unsetError();
            return cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "exists(): Failed query: " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        mError.setUnknownError();
        return false;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private boolean canWrite(String contentUrl) {
        final Uri uri = getUriWithValidPermission(contentUrl, "w");

        Cursor cursor = null;
        try {
            final ContentResolver resolver = mCtx.getContentResolver();
            final String[] columns = {DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.COLUMN_MIME_TYPE};
            cursor = resolver.query(uri, columns, null, null, null);
            int flags = 0;
            String mimeType = null;
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    flags = cursor.getInt(0);
                    mimeType = cursor.getString(1);
                } else {
                    return false;
                }
            }

            mError.unsetError();
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) &&
                    (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0) {
                return true;
            } else if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0) {
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "canWrite(): Failed query: " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return uri != null;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private String getFileName(String contentUrl) {
        final Uri uri = getUriWithValidPermission(contentUrl, "r");
        if (uri == null) {
            return null;
        }
        Cursor cursor = null;
        String filename = null;
        try {
            final String[] columns = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
            cursor = mCtx.getContentResolver().query(uri, columns,
                    null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                }
            }
        } catch (Exception e) {
            mError.setUnknownError();
            Log.e(TAG, "getFileName(): Failed query: " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        mError.unsetError();
        return filename;
    }

    private String stringJoin(String delimiter, List<String> list) {
        if (list.size() < 1) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); ++i) {
            builder.append(list.get(i))
                    .append(delimiter);
        }

        // remove trailing
        builder.delete(builder.length() - delimiter.length(), builder.length());
        return builder.toString();
    }

    private Uri uriAppend(Uri uri, List<String> items) {
        final StringBuilder builder = new StringBuilder(uri.toString());
        for (String item : items) {
            builder.append(Uri.encode(File.separator)).append(item);
        }
        return Uri.parse(builder.toString());
    }

    /**
     * Splits the tree uri:
     * <p>
     * {@code "content://com.externalstorage.documents/tree/Primary%3AExample/path1/path2"}
     * into: <br>
     * First  = {@code "content://com.externalstorage.documents/tree/Primary%3AExample"},
     * <br>
     * Second = {@code "path1/path2"}
     *
     * @param treeUri The Tree Uri with a path appended - which may or may not exist
     * @return returns the tree Uri that is returned from ACTION_OPEN_DOCUMENT_TREE
     * and the separated path/file which is to be created.
     */
    private Pair<Uri, Uri> nearestTreeUri(Uri treeUri) {
        final List<String> paths = treeUri.getPathSegments()
                .subList(1, treeUri.getPathSegments().size());

        // Test each subtree, going from right to left
        for (int i = paths.size(); i > 0; --i) {
            final Uri baseUri =
                    new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                            .authority(treeUri.getAuthority()).appendPath(PATH_TREE)
                            .appendPath(paths.get(0)).build();

            // we can't use appendPath, because of the weird encoding rules that SAF follows
            final Uri testUri = getProperlyEncodedUriWithPermissions(
                    uriAppend(baseUri, paths.subList(1, i)), "rw");

            // we check the permission of the subtree
            if (testUri != null) {
                if (i < paths.size()) {
                    return new Pair<>(testUri, Uri.parse(stringJoin(File.separator,
                            paths.subList(i, paths.size()))));
                } else {
                    return new Pair<>(testUri, Uri.parse(""));
                }
            }
        }

        Log.d(TAG, "nearestTreeUri(): uri: " + treeUri);
        return null;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    boolean isDir(String contentUrl) {
        final Uri uri = Uri.parse(contentUrl);
        if (!isTreeUri(uri)) {
            return false;
        }

        final Pair<Uri, Uri> separatedUriPair = nearestTreeUri(uri);
        if (separatedUriPair == null) {
            return false;
        }

        final CachedDocumentFile file =
                findFileInTree(separatedUriPair.first,
                        separatedUriPair.second.getPathSegments());
        if (file == null) {
            return false;
        }
        mError.unsetError();
        return file.isDirectory();
    }

    private boolean isTreeUri(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return DocumentsContract.isTreeUri(uri);
        } else {
            final List<String> paths = uri.getPathSegments();
            return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    boolean mkdir(String contentUrl, boolean createParentDirectories) {
        final Uri uri = Uri.parse(contentUrl);
        // "tree" and document id make the first two parts of the path
        if (uri.getPathSegments().size() > 3 && !createParentDirectories) {
            return false;
        }
        final Pair<Uri, Uri> separatedUriPair = nearestTreeUri(uri);
        if (separatedUriPair == null) {
            return false;
        }

        if (createDirectories(separatedUriPair.first,
                separatedUriPair.second.getPathSegments()) != null) {
            mError.unsetError();
            return true;
        } else {
            return false;
        }
    }

    Uri createDirectories(Uri treeUri, List<String> pathSegments) {
        Log.d(TAG, "Creating directory: " + treeUri.toString() +
                ", segments = " + stringJoin("/", pathSegments));

        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
        for (String segment : pathSegments) {
            final CachedDocumentFile existingFile = findFile(parent, segment);

            if (existingFile != null) {
                Log.d(TAG, "File exists: " + existingFile.getUri().toString());
                if (existingFile.isFile()) {
                    mError.setError(FileError.UNSPECIFIED_ERROR);
                    mError.setErrorString("Couldn't create a directory at the specified path");
                    return null;
                }

                parent = existingFile.getUri();
                continue;
            }

            final CachedDocumentFile newFile = createDirectory(parent, segment);
            if (newFile == null) {
                return null;
            }
            parent = newFile.getUri();
            mCachedDocumentFiles.add(newFile);
        }

        mError.unsetError();
        return parent;
    }

    private Uri createFile(Uri treeUri, Uri filePath, boolean force) {
        Log.d(TAG, "Creating new file: " + treeUri + ", filename = " + filePath);

        final List<String> pathSegments = filePath.getPathSegments();

        final Uri parent = createDirectories(treeUri,
                pathSegments.subList(0, pathSegments.size() - 1));
        if (parent == null) {
            return null;
        }

        final String filename = pathSegments.get(pathSegments.size() - 1);
        final String mimeType = getMimeTypeFromFilename(filename);

        final CachedDocumentFile foundFile = findFile(parent, filename);
        if (foundFile != null && foundFile.isFile() && !force) {
            return foundFile.getUri();
        }

        final CachedDocumentFile newFile = createFile(parent, filename, mimeType);
        if (newFile == null) {
            return null;
        }
        mCachedDocumentFiles.add(newFile);
        return newFile.getUri();
    }

    private String getMimeTypeFromFilename(String filename) {
        final int index = filename.lastIndexOf(".");
        String extension;
        if (index == -1 || index == filename.length() - 1) {
            extension = "";
        } else {
            extension = filename.substring(index + 1);
        }

        if (extension.isEmpty()) {
            return "application/octet-stream";
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }


    private List<CachedDocumentFile> listFiles(Uri documentTreeUri) {
        Log.d(TAG, "listFiles(): Uri = " + documentTreeUri);
        final List<CachedDocumentFile> cachedDocumentFiles = new ArrayList<>();
        // order matters!
        final String[] columns = new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
        };

        Cursor cursor = null;
        try {
            final ContentResolver resolver = mCtx.getContentResolver();
            final Uri childrenTreeUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentTreeUri,
                    DocumentsContract.getDocumentId(documentTreeUri));
            cursor = resolver.query(childrenTreeUri, columns, null, null, null);
            if (cursor == null) {
                return cachedDocumentFiles;
            }

            while (cursor.moveToNext()) {
                final String docId = cursor.getString(1);
                final Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(documentTreeUri, docId);
                cachedDocumentFiles.add(new CachedDocumentFile(
                        cursor.getString(0),  // name
                        docId,
                        cursor.getString(2),  // mimetype
                        fileUri));
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid document Uri: " + documentTreeUri);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return cachedDocumentFiles;
    }

    /**
     * Find the file under the tree. This function can take pathSegments which
     * allow looking recursively in the document's subtree.
     *
     * @param treeUri      treeUri which is returned from
     *                     {@link Intent#ACTION_OPEN_DOCUMENT_TREE}
     * @param pathSegments path to the document file
     * @return {@link CachedDocumentFile} if the document is found, null otherwise.
     */
    private CachedDocumentFile findFileInTree(Uri treeUri, List<String> pathSegments) {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));

        CachedDocumentFile documentFile = CachedDocumentFile.fromFileUri(parent);

        for (int i = 0; i < pathSegments.size(); ++i) {
            documentFile = findFile(parent, pathSegments.get(i));
            if (documentFile == null) {
                return null;
            }

            if (documentFile.isFile()) {
                if (i == pathSegments.size() - 1) {
                    return documentFile;
                } else {
                    return null;
                }
            }
            parent = documentFile.getUri();
        }

        return documentFile;
    }

    /**
     * Find the file under subtree
     *
     * @param documentTreeUri a Uri with both "tree" and "document".
     * @param filename        name of the file or directory.
     * @return {@link CachedDocumentFile} if the document is found in cache or under
     * the tree, null otherwise.
     */
    private CachedDocumentFile findFile(Uri documentTreeUri, String filename) {
        {
            final Uri expectedUri = DocumentsContract.buildDocumentUriUsingTree(documentTreeUri,
                    DocumentsContract.getDocumentId(documentTreeUri) + "/" + filename);
            // check in the cached documents first
            for (CachedDocumentFile file : mCachedDocumentFiles) {
                if (expectedUri.equals(file.getUri())) {
                    return file;
                }
            }
        }

        // check the tree now
        List<CachedDocumentFile> cachedDocumentFiles = listFiles(documentTreeUri);
        for (CachedDocumentFile file : cachedDocumentFiles) {
            if (filename.equals(file.getName())) {
                return file;
            }
        }

        return null;
    }

    private CachedDocumentFile createFile(Uri parent, String displayName, String mimeType) {
        try {
            final Uri fileUri = DocumentsContract.createDocument(mCtx.getContentResolver(),
                    parent, mimeType, displayName);
            return new CachedDocumentFile(displayName,
                    DocumentsContract.getDocumentId(fileUri),
                    mimeType,
                    fileUri);
        } catch (Exception e) {
            mError.setUnknownError();
            Log.e(TAG, "Error creating a file: uri = " + parent +
                    ", displayName = " + displayName + ", mimeType = " + mimeType);
            return null;
        }
    }

    private CachedDocumentFile createDirectory(Uri parent, String displayName) {
        return createFile(parent, displayName, DocumentsContract.Document.MIME_TYPE_DIR);
    }
}
