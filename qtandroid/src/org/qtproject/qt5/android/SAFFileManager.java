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
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    private final HashMap<Uri, CachedDocumentFile> mCachedDocumentFiles = new HashMap<>();
    private final HashMap<Integer, ParcelFileDescriptor> m_parcelFileDescriptors = new HashMap<>();

    private final FileError mError = new FileError();
    private List<UriPermission> mCachedPermissions;
    private final ArrayList<Uri> mCachedListDocumentFiles = new ArrayList<>();

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

    public static SAFFileManager instance(Context context) {
        if (sSafFileManager == null) {
            sSafFileManager = new SAFFileManager(context);
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

    void resetCachedPermission() {
        mCachedPermissions = mCtx.getContentResolver().getPersistedUriPermissions();
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
        if (mCachedPermissions == null) {
            resetCachedPermission();
        }
        final String uriPath = uri.getPath();

        for (int i = 0; i < mCachedPermissions.size(); ++i) {
            Uri iterUri = mCachedPermissions.get(i).getUri();
            boolean isRightPermission = mCachedPermissions.get(i).isReadPermission();

            if (!openMode.equals("r"))
                isRightPermission = mCachedPermissions.get(i).isWritePermission();

            if (iterUri.getPath().equals(uriPath) && isRightPermission) {
                return iterUri;
            }
        }

        // TODO(sh_zam): check encoding
        // TODO(sh_zam): verify if intent has to exist
        // check if we received permission from an Intent
        return (QtNative.activity() != null &&
                QtNative.activity().getIntent() != null &&
                checkImplicitUriPermission(uri, openMode)) ? uri : null;
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
    private CachedDocumentFile getDocumentFileWithValidPermissions(String url,
                                                                   String openMode,
                                                                   boolean dontCreateDoc) {
        final Uri uri = Uri.parse(url);

        // it is a file in tree, so we create a new file if "w"
        if (isTreeUri(uri)) {
            SAFFile rawSafFile = nearestTreeUri(uri);
            if (rawSafFile == null) {
                mError.setError(FileError.PERMISSIONS_ERROR);
                mError.setErrorString("No permission to access the Document Tree");
                return null;
            }

            CachedDocumentFile foundFile = findFileInTree(rawSafFile);

            if (foundFile != null) {
                return foundFile;
            }

            // we shouldn't create a file here
            if ("r".equals(openMode) || dontCreateDoc) {
                return null;
            }

            return createFile(rawSafFile, false);
        } else {
            Uri resultUri = getProperlyEncodedUriWithPermissions(uri, openMode);
            if (resultUri != null) {
                return CachedDocumentFile.fromFileUri(mCtx, resultUri);
            }
        }

        mError.setError(FileError.PERMISSIONS_ERROR);
        mError.setErrorString("No permission to access the Uri");
        return null;
    }

    private CachedDocumentFile getDocumentFileWithValidPermissions(String url,
                                                                   String openMode) {
        return getDocumentFileWithValidPermissions(url, openMode, false);
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    private boolean launchUri(String url, String mime) {
        Uri uri;
        if (url.startsWith("content:")) {
            uri = getDocumentFileWithValidPermissions(url, "r").getUri();
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
    public int openFileDescriptor(String contentUrl, String openMode) {
        int retry = 0;
        while (retry < 2) {
            CachedDocumentFile file =
                    getDocumentFileWithValidPermissions(contentUrl, openMode);

            if (file == null) {
                return -1;
            }

            // take this out
            try {
                ContentResolver resolver = mCtx.getContentResolver();
                ParcelFileDescriptor fdDesc =
                        resolver.openFileDescriptor(file.getUri(), openMode);
                m_parcelFileDescriptors.put(fdDesc.getFd(), fdDesc);

                mError.unsetError();
                return fdDesc.getFd();
            } catch (Exception e) {
                Log.w(TAG, "openFileDescriptor(): Failed query: " + e);
                mCachedDocumentFiles.remove(file.getUri());
                retry++;
            }
        }

        mError.setError(FileError.WRITE_ERROR);
        mError.setErrorString("Couldn't open file for writing");
        return -1;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean closeFileDescriptor(int fd) {
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
    public long getSize(String contentUrl) {
        CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "r");

        if (file != null) {
            return file.getSize();
        } else {
            mError.setUnknownError();
            return 0;
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean exists(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "r");

        if (file != null && file.exists()) {
            mError.unsetError();
            return true;
        } else {
            mError.setUnknownError();
            return false;
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean canWrite(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "w", true);

        if (file != null) {
            if (file.canWrite()) {
                mError.unsetError();
                return true;
            } else if (isArc()) {
                // HACK: some files on ChromeOS don't have file flags! So, if
                // we have write permissions on the Uri, it seems we can assume
                // FLAG_SUPPORTS_WRITE
                return true;
            }
        }
        return false;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public String getFileName(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "r");

        if (file != null) {
            mError.unsetError();
            return file.getName();
        }

        return null;
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
     * @return a {@link SAFFile} through which we can get the base Uri and path segments
     * which are to be created or tested
     */
    private SAFFile nearestTreeUri(Uri treeUri) {
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
                    return new SAFFile(testUri, paths.subList(i, paths.size()));
                } else {
                    return new SAFFile(testUri, new ArrayList<String>());
                }
            }
        }

        Log.d(TAG, "nearestTreeUri(): No permissions to Uri: " + treeUri);
        return null;
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean delete(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "rw", true);
        if (file == null) {
            return false;
        }

        mCachedDocumentFiles.remove(file.getUri());
        if (file.isDirectory()) {
            invalidateCachedDocuments(file.getUri());
        }
        return deleteFile(file.getUri());
    }

    // Native usage
    public String[] listFileNames(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "r");

        if (file == null || !file.isDirectory()) {
            return null;
        }

        List<CachedDocumentFile> files = listFiles(file.getUri());
        String[] result = new String[files.size()];
        for (int i = 0; i < files.size(); ++i) {
            CachedDocumentFile docFile = files.get(i);
            result[i] = docFile.getName();
            mCachedDocumentFiles.put(docFile.getUri(), docFile);
            mCachedListDocumentFiles.add(docFile.getUri());
        }

        return result;
    }

    // Native usage
    void resetListCache() {
        for (Uri uri : mCachedListDocumentFiles) {
            mCachedDocumentFiles.remove(uri);
        }
        mCachedListDocumentFiles.clear();
    }

    /**
     * A dumb heuristic method to remove the subdirectories if the parent directory
     * is removed.
     *
     * @param removedUri The document to be deleted
     */
    private void invalidateCachedDocuments(Uri removedUri) {
        String dirname = removedUri.getLastPathSegment();
        Iterator<Map.Entry<Uri, CachedDocumentFile>> iterator =
                mCachedDocumentFiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Uri, CachedDocumentFile> entry = iterator.next();
            if (entry.getKey().getPath().contains(dirname)) {
                iterator.remove();
            }
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean isDir(String contentUrl) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "rw", true);
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
    public boolean isTreeUri(String contentUrl) {
        return isTreeUri(Uri.parse(contentUrl));
    }

    public boolean rename(String contentUrl, String displayName) {
        final CachedDocumentFile file =
                getDocumentFileWithValidPermissions(contentUrl, "rw", true);
        if (file == null) {
            return false;
        }

        final Uri oldUri = file.getUri();
        if (file.rename(displayName)) {
            mCachedDocumentFiles.remove(oldUri);
            invalidateCachedDocuments(oldUri);
            mCachedDocumentFiles.put(file.getUri(), file);
            resetCachedPermission();
            return true;
        } else {
            return false;
        }
    }

    // Native usage
    @SuppressWarnings("UnusedDeclaration")
    public boolean mkdir(String contentUrl, boolean createParentDirectories) {
        if (isDir(contentUrl)) {
            return true;
        }

        final Uri uri = Uri.parse(contentUrl);
        // "tree" and document id make the first two parts of the path
        if (uri.getPathSegments().size() > 3 && !createParentDirectories) {
            return false;
        }
        final SAFFile rawSafFile = nearestTreeUri(uri);
        if (rawSafFile == null) {
            mError.setError(FileError.PERMISSIONS_ERROR);
            mError.setErrorString("No permission to access the Document Tree");
            return false;
        }

        if (createDirectories(rawSafFile) != null) {
            mError.unsetError();
            return true;
        } else {
            return false;
        }
    }

    Uri createDirectories(SAFFile file) {
        final Uri treeUri = file.getBaseUri();
        List<String> pathSegments = file.getSegments();

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
            mCachedDocumentFiles.put(newFile.getUri(), newFile);
        }

        mError.unsetError();
        return parent;
    }

    private CachedDocumentFile createFile(SAFFile file, boolean force) {

        List<String> pathSegments = file.getSegments();

        Log.d(TAG, "Creating new file: " + file.getBaseUri() + ", filename = "
                + stringJoin(File.separator, pathSegments));

        final Uri parent = createDirectories(
                new SAFFile(file.getBaseUri(),
                        pathSegments.subList(0, pathSegments.size() - 1)));
        if (parent == null) {
            return null;
        }

        final String filename = pathSegments.get(pathSegments.size() - 1);
        final String mimeType = getMimeTypeFromFilename(filename);

        final CachedDocumentFile foundFile = findFile(parent, filename);
        if (foundFile != null && foundFile.isFile() && !force) {
            return foundFile;
        }

        final CachedDocumentFile newFile = createDocumentImpl(parent, filename, mimeType);
        if (newFile == null) {
            return null;
        }
        mCachedDocumentFiles.put(newFile.getUri(), newFile);
        return newFile;
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
                DocumentsContract.Document.COLUMN_SIZE
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
                final String docId = SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                final Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(documentTreeUri, docId);
                cachedDocumentFiles.add(new CachedDocumentFile(mCtx,
                        SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        docId,
                        SAFUtils.getColumnValStringOrNull(cursor, DocumentsContract.Document.COLUMN_MIME_TYPE),
                        SAFUtils.getColumnValIntegerOrDefault(cursor, DocumentsContract.Document.COLUMN_SIZE, -1),
                        fileUri));
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid document Uri: " + documentTreeUri);
            // TODO(sh_zam): a test is needed
            mCachedDocumentFiles.remove(documentTreeUri);
            invalidateCachedDocuments(documentTreeUri);
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
     * @param safFile {@link SAFFile}
     * @return {@link CachedDocumentFile} if the document is found, null otherwise.
     */
    private CachedDocumentFile findFileInTree(SAFFile safFile) {
        List<String> pathSegments = safFile.getSegments();
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(safFile.getBaseUri(),
                DocumentsContract.getTreeDocumentId(safFile.getBaseUri()));

        CachedDocumentFile documentFile;
        if (mCachedDocumentFiles.containsKey(parent)) {
            documentFile = mCachedDocumentFiles.get(parent);
        } else {
            documentFile = CachedDocumentFile.fromFileUri(mCtx, parent);
            mCachedDocumentFiles.put(documentFile.getUri(), documentFile);
        }

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
            if (mCachedDocumentFiles.containsKey(expectedUri)) {
                return mCachedDocumentFiles.get(expectedUri);
            }
        }

        // check the tree now
        List<CachedDocumentFile> cachedDocumentFiles = listFiles(documentTreeUri);
        for (CachedDocumentFile file : cachedDocumentFiles) {
            if (filename.equals(file.getName())) {
                mCachedDocumentFiles.put(file.getUri(), file);
                return file;
            }
        }

        return null;
    }

    private CachedDocumentFile createDocumentImpl(Uri parent, String displayName, String mimeType) {
        try {
            final Uri fileUri = DocumentsContract.createDocument(mCtx.getContentResolver(),
                    parent, mimeType, displayName);
            return new CachedDocumentFile(mCtx, displayName,
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

    private boolean deleteFile(Uri documentUri) {
        try {
            return DocumentsContract.deleteDocument(mCtx.getContentResolver(),
                    documentUri);
        } catch (Exception e) {
            mError.setUnknownError();
            Log.e(TAG, "Error deleting a file: uri = " + documentUri);
            return false;
        }
    }

    private CachedDocumentFile createDirectory(Uri parent, String displayName) {
        return createDocumentImpl(parent, displayName, DocumentsContract.Document.MIME_TYPE_DIR);
    }

    // we need some workarounds on ChromeOS
    public static boolean isArc() {
        return (Build.DEVICE != null) && Build.DEVICE.matches(".+_cheets|cheets_.+");
    }
}
