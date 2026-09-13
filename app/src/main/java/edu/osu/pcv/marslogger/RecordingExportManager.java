package edu.osu.pcv.marslogger;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import timber.log.Timber;

/** Copies a finished session to a user-selected Storage Access Framework folder. */
public final class RecordingExportManager {
    private static final Pattern SESSION_NAME =
            Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}");
    private static final String ACTIVE_MARKER = ".marslogger_recording_in_progress";
    private static final int BUFFER_SIZE = 1024 * 1024;

    private RecordingExportManager() { }

    public static final class SessionInfo {
        public final String name;
        public final File directory;

        private SessionInfo(File directory) {
            this.name = directory.getName();
            this.directory = directory;
        }
    }

    public static final class ExportResult {
        public final String folderName;
        public final long bytesCopied;

        private ExportResult(String folderName, long bytesCopied) {
            this.folderName = folderName;
            this.bytesCopied = bytesCopied;
        }
    }

    public interface ProgressListener {
        void onProgress(long bytesCopied, long totalBytes, String fileName);
    }

    public static final class ExportCancelledException extends IOException {
        private ExportCancelledException() {
            super("Export cancelled");
        }
    }

    /** Matches the source directory used by CameraCapture.renewOutputDir(). */
    public static File getSessionRoot(Context context) {
        return context.getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath());
    }

    /** This marker is independent of the camera, IMU, and LiDAR file writers. */
    public static void markRecordingStarted(String outputDir) {
        try {
            if (!new File(outputDir, ACTIVE_MARKER).createNewFile()) {
                Timber.w("Recording marker already exists in %s", outputDir);
            }
        } catch (IOException | SecurityException error) {
            Timber.e(error, "Could not mark recording as active in %s", outputDir);
        }
    }

    public static void markRecordingCompleted(String outputDir) {
        if (outputDir == null) {
            return;
        }
        try {
            File marker = new File(outputDir, ACTIVE_MARKER);
            if (marker.exists() && !marker.delete()) {
                Timber.w("Could not clear recording marker in %s", outputDir);
            }
        } catch (SecurityException error) {
            Timber.e(error, "Could not clear recording marker in %s", outputDir);
        }
    }

    public static List<SessionInfo> listSessions(Context context) {
        return listSessions(getSessionRoot(context));
    }

    static List<SessionInfo> listSessions(File root) {
        if (root == null || !root.isDirectory()) {
            return Collections.emptyList();
        }
        File[] children = root.listFiles();
        if (children == null) {
            return Collections.emptyList();
        }
        List<SessionInfo> sessions = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && SESSION_NAME.matcher(child.getName()).matches() &&
                    !new File(child, ACTIVE_MARKER).exists()) {
                File[] contents = child.listFiles();
                if (contents != null && contents.length > 0) {
                    sessions.add(new SessionInfo(child));
                }
            }
        }
        sessions.sort((left, right) -> right.name.compareTo(left.name));
        return sessions;
    }

    public static SessionInfo findSession(Context context, String name) {
        if (name == null) {
            return null;
        }
        for (SessionInfo session : listSessions(context)) {
            if (name.equals(session.name)) {
                return session;
            }
        }
        return null;
    }

    public static ExportResult export(Context context, SessionInfo session, Uri treeUri,
                                      ProgressListener progress, AtomicBoolean cancelled)
            throws IOException {
        File root = getSessionRoot(context);
        if (root == null || session == null || treeUri == null ||
                !SESSION_NAME.matcher(session.name).matches() ||
                !session.directory.getCanonicalFile().getParentFile()
                        .equals(root.getCanonicalFile())) {
            throw new IOException("The recording session is no longer available");
        }
        return export(session.directory,
                new SafDirectory(context.getContentResolver(), treeUri), progress, cancelled);
    }

    interface DestinationDirectory {
        String getName() throws IOException;
        DestinationDirectory findDirectory(String name) throws IOException;
        boolean childExists(String name) throws IOException;
        DestinationDirectory createDirectory(String name) throws IOException;
        OutputStream createFile(String name) throws IOException;
        boolean delete() throws IOException;
    }

    /** Package-visible so the recursive copy can be checked without an Android document provider. */
    static ExportResult export(File source, DestinationDirectory selectedFolder,
                               ProgressListener progress, AtomicBoolean cancelled)
            throws IOException {
        if (source == null || !source.isDirectory()) {
            throw new IOException("The recording session is no longer available");
        }
        if (new File(source, ACTIVE_MARKER).exists()) {
            throw new IOException("Recording is still in progress; stop it before exporting");
        }
        if (cancelled.get()) {
            throw new ExportCancelledException();
        }
        Snapshot before = snapshot(source);
        if (before.entries.isEmpty()) {
            throw new IOException("The recording session is empty");
        }

        DestinationDirectory marsFolder = "MarsLogger".equals(selectedFolder.getName())
                ? selectedFolder : selectedFolder.findDirectory("MarsLogger");
        if (marsFolder == null) {
            marsFolder = selectedFolder.createDirectory("MarsLogger");
        }
        String folderName = source.getName();
        for (int suffix = 2; marsFolder.childExists(folderName); suffix++) {
            folderName = source.getName() + " (" + suffix + ")";
        }

        DestinationDirectory exportedSession = marsFolder.createDirectory(folderName);
        long copied = 0;
        long lastUpdateMs = 0;
        try {
            Map<String, DestinationDirectory> destinations = new HashMap<>();
            destinations.put("", exportedSession);
            byte[] buffer = new byte[BUFFER_SIZE];
            progress.onProgress(0, before.totalBytes, "");
            for (Entry entry : before.entries) {
                if (cancelled.get()) {
                    throw new ExportCancelledException();
                }
                int slash = entry.relativePath.lastIndexOf('/');
                String parentPath = slash < 0 ? "" : entry.relativePath.substring(0, slash);
                String name = slash < 0 ? entry.relativePath
                        : entry.relativePath.substring(slash + 1);
                DestinationDirectory parent = destinations.get(parentPath);
                if (parent == null) {
                    throw new IOException("Missing export folder for " + entry.relativePath);
                }
                if (entry.directory) {
                    destinations.put(entry.relativePath, parent.createDirectory(name));
                    continue;
                }
                if (!entry.matchesSource()) {
                    throw new IOException("Recording changed during export; stop recording and retry");
                }
                long fileBytes = 0;
                try (BufferedInputStream input = new BufferedInputStream(
                        new FileInputStream(entry.source), BUFFER_SIZE);
                     OutputStream output = parent.createFile(name)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (cancelled.get()) {
                            throw new ExportCancelledException();
                        }
                        output.write(buffer, 0, count);
                        fileBytes += count;
                        copied += count;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateMs >= 250) {
                            progress.onProgress(copied, before.totalBytes, entry.relativePath);
                            lastUpdateMs = now;
                        }
                    }
                }
                if (fileBytes != entry.length || !entry.matchesSource()) {
                    throw new IOException("Recording changed during export; stop recording and retry");
                }
            }
            Snapshot after = snapshot(source);
            if (new File(source, ACTIVE_MARKER).exists() || !before.sameAs(after)) {
                throw new IOException("Recording changed during export; stop recording and retry");
            }
            progress.onProgress(copied, before.totalBytes, "Complete");
            return new ExportResult(folderName, copied);
        } catch (IOException | RuntimeException error) {
            try {
                if (!exportedSession.delete()) {
                    error.addSuppressed(new IOException("Could not remove partial export"));
                }
            } catch (Exception cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    private static Snapshot snapshot(File root) throws IOException {
        List<Entry> entries = new ArrayList<>();
        collect(root, "", root.getCanonicalPath() + File.separator, entries, 0);
        long total = 0;
        for (Entry entry : entries) {
            if (!entry.directory) {
                try {
                    total = Math.addExact(total, entry.length);
                } catch (ArithmeticException error) {
                    throw new IOException("Recording is too large to measure", error);
                }
            }
        }
        return new Snapshot(entries, total);
    }

    private static void collect(File directory, String prefix, String rootPath,
                                List<Entry> entries, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("Recording directory nesting is too deep");
        }
        File[] children = directory.listFiles();
        if (children == null) {
            throw new IOException("Cannot read " + directory.getName());
        }
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (!child.getCanonicalPath().startsWith(rootPath)) {
                throw new IOException("Recording contains a link outside its session folder");
            }
            String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            if (child.isDirectory()) {
                entries.add(new Entry(child, path, true));
                collect(child, path, rootPath, entries, depth + 1);
            } else if (child.isFile()) {
                entries.add(new Entry(child, path, false));
            } else {
                throw new IOException("Cannot read " + path);
            }
        }
    }

    private static final class Entry {
        final File source;
        final String relativePath;
        final boolean directory;
        final long length;
        final long modified;

        Entry(File source, String relativePath, boolean directory) {
            this.source = source;
            this.relativePath = relativePath;
            this.directory = directory;
            this.length = directory ? 0 : source.length();
            this.modified = source.lastModified();
        }

        boolean matchesSource() {
            return source.isFile() && source.length() == length &&
                    source.lastModified() == modified;
        }

        boolean sameAs(Entry other) {
            return relativePath.equals(other.relativePath) && directory == other.directory &&
                    length == other.length && modified == other.modified;
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;
        final long totalBytes;

        Snapshot(List<Entry> entries, long totalBytes) {
            this.entries = entries;
            this.totalBytes = totalBytes;
        }

        boolean sameAs(Snapshot other) {
            if (totalBytes != other.totalBytes || entries.size() != other.entries.size()) {
                return false;
            }
            for (int index = 0; index < entries.size(); index++) {
                if (!entries.get(index).sameAs(other.entries.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class SafDirectory implements DestinationDirectory {
        private static final String[] CHILD_COLUMNS = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        private final ContentResolver resolver;
        private final Uri treeUri;
        private final Uri documentUri;

        SafDirectory(ContentResolver resolver, Uri treeUri) {
            this(resolver, treeUri, DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)));
        }

        private SafDirectory(ContentResolver resolver, Uri treeUri, Uri documentUri) {
            this.resolver = resolver;
            this.treeUri = treeUri;
            this.documentUri = documentUri;
        }

        @Override
        public String getName() throws IOException {
            try (Cursor cursor = resolver.query(documentUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    throw new IOException("Cannot read the selected destination folder");
                }
                return cursor.getString(0);
            } catch (SecurityException error) {
                throw new IOException("Access to the selected destination was denied", error);
            }
        }

        @Override
        public DestinationDirectory findDirectory(String name) throws IOException {
            Child child = findChild(name);
            if (child == null) {
                return null;
            }
            if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(child.mimeType)) {
                throw new IOException("A file named " + name + " already exists at the destination");
            }
            return new SafDirectory(resolver, treeUri, child.uri);
        }

        @Override
        public boolean childExists(String name) throws IOException {
            return findChild(name) != null;
        }

        private Child findChild(String name) throws IOException {
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                    DocumentsContract.getDocumentId(documentUri));
            try (Cursor cursor = resolver.query(childrenUri, CHILD_COLUMNS,
                    null, null, null)) {
                if (cursor == null) {
                    throw new IOException("Cannot list destination folder");
                }
                while (cursor.moveToNext()) {
                    if (name.equals(cursor.getString(1))) {
                        return new Child(DocumentsContract.buildDocumentUriUsingTree(treeUri,
                                cursor.getString(0)), cursor.getString(2));
                    }
                }
                return null;
            } catch (SecurityException error) {
                throw new IOException("Access to the destination folder was denied", error);
            }
        }

        @Override
        public DestinationDirectory createDirectory(String name) throws IOException {
            Uri uri = create(DocumentsContract.Document.MIME_TYPE_DIR, name);
            return new SafDirectory(resolver, treeUri, uri);
        }

        @Override
        public OutputStream createFile(String name) throws IOException {
            Uri uri = create("application/octet-stream", name);
            try {
                OutputStream stream = resolver.openOutputStream(uri, "w");
                if (stream == null) {
                    throw new IOException("Cannot open exported file " + name);
                }
                return stream;
            } catch (IOException | RuntimeException error) {
                try {
                    DocumentsContract.deleteDocument(resolver, uri);
                } catch (Exception cleanupError) {
                    error.addSuppressed(cleanupError);
                }
                throw error;
            }
        }

        private Uri create(String mimeType, String name) throws IOException {
            try {
                Uri uri = DocumentsContract.createDocument(resolver, documentUri, mimeType, name);
                if (uri == null) {
                    throw new IOException("Cannot create " + name + " at the destination");
                }
                SafDirectory created = new SafDirectory(resolver, treeUri, uri);
                try {
                    if (!name.equals(created.getName())) {
                        throw new IOException("Destination changed the name of " + name);
                    }
                } catch (IOException | RuntimeException error) {
                    try {
                        DocumentsContract.deleteDocument(resolver, uri);
                    } catch (Exception cleanupError) {
                        error.addSuppressed(cleanupError);
                    }
                    throw error;
                }
                return uri;
            } catch (SecurityException error) {
                throw new IOException("Access to the destination folder was denied", error);
            }
        }

        @Override
        public boolean delete() throws IOException {
            try {
                return DocumentsContract.deleteDocument(resolver, documentUri);
            } catch (SecurityException error) {
                throw new IOException("Could not remove partial export", error);
            }
        }

        private static final class Child {
            final Uri uri;
            final String mimeType;

            Child(Uri uri, String mimeType) {
                this.uri = uri;
                this.mimeType = mimeType;
            }
        }
    }
}
