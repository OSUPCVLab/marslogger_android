package edu.osu.pcv.marslogger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingExportManagerTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void listsDatedNonemptySessionsNewestFirst() throws Exception {
        File root = temporaryFolder.newFolder("source");
        File older = new File(root, "2026_01_01_10_00_00");
        File newer = new File(root, "2026_02_01_10_00_00");
        assertTrue(older.mkdir());
        assertTrue(newer.mkdir());
        write(new File(older, "gyro_accel.csv"), new byte[]{1});
        write(new File(newer, "movie.mp4"), new byte[]{2});
        assertTrue(new File(root, "2026_03_01_10_00_00").mkdir()); // empty
        write(new File(root, "MID360_config.json"), new byte[]{3});

        List<RecordingExportManager.SessionInfo> sessions =
                RecordingExportManager.listSessions(root);
        assertEquals(2, sessions.size());
        assertEquals(newer.getName(), sessions.get(0).name);
        assertEquals(older.getName(), sessions.get(1).name);
    }

    @Test
    public void doesNotOfferOrExportAnActiveRecording() throws Exception {
        File root = temporaryFolder.newFolder("source");
        File session = new File(root, "2026_02_01_10_00_00");
        assertTrue(session.mkdir());
        write(new File(session, "movie.mp4"), new byte[]{1});
        RecordingExportManager.markRecordingStarted(session.getAbsolutePath());
        assertTrue(RecordingExportManager.listSessions(root).isEmpty());

        try {
            RecordingExportManager.export(session,
                    new LocalDirectory(temporaryFolder.newFolder("Documents"), false),
                    (copied, total, name) -> { }, new AtomicBoolean(false));
            fail("Expected active recording to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("still in progress"));
        }
        RecordingExportManager.markRecordingCompleted(session.getAbsolutePath());
        assertEquals(1, RecordingExportManager.listSessions(root).size());
    }

    @Test
    public void copiesNestedFilesAndKeepsExistingExport() throws Exception {
        File session = temporaryFolder.newFolder("2026_02_01_10_00_00");
        File nested = new File(session, "lidar/points");
        assertTrue(nested.mkdirs());
        assertTrue(new File(session, "empty").mkdir());
        byte[] bag = new byte[2_500_000];
        for (int index = 0; index < bag.length; index++) {
            bag[index] = (byte) index;
        }
        write(new File(nested, "lidar.bag"), bag);
        write(new File(session, "gyro_accel.csv"), new byte[]{4, 5, 6});

        File selected = temporaryFolder.newFolder("Documents");
        File existing = new File(selected, "MarsLogger/" + session.getName());
        assertTrue(existing.mkdirs());
        write(new File(existing, "keep.txt"), new byte[]{9});

        RecordingExportManager.ExportResult result = RecordingExportManager.export(session,
                new LocalDirectory(selected, false), (copied, total, name) -> { },
                new AtomicBoolean(false));

        assertEquals(session.getName() + " (2)", result.folderName);
        assertEquals(bag.length + 3, result.bytesCopied);
        File exported = new File(selected, "MarsLogger/" + result.folderName);
        assertArrayEquals(bag, Files.readAllBytes(new File(exported, "lidar/points/lidar.bag").toPath()));
        assertArrayEquals(new byte[]{4, 5, 6},
                Files.readAllBytes(new File(exported, "gyro_accel.csv").toPath()));
        assertTrue(new File(exported, "empty").isDirectory());
        assertTrue(new File(existing, "keep.txt").isFile());
    }

    @Test
    public void removesPartialCopyAfterWriteFailure() throws Exception {
        File session = temporaryFolder.newFolder("2026_02_01_10_00_00");
        write(new File(session, "movie.mp4"), new byte[100]);
        File selected = temporaryFolder.newFolder("Documents");

        try {
            RecordingExportManager.export(session, new LocalDirectory(selected, true),
                    (copied, total, name) -> { }, new AtomicBoolean(false));
            fail("Expected write failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("simulated write failure"));
        }
        assertFalse(new File(selected, "MarsLogger/" + session.getName()).exists());
        assertArrayEquals(new byte[100], Files.readAllBytes(new File(session, "movie.mp4").toPath()));
    }

    @Test
    public void cancellationRemovesPartialSession() throws Exception {
        File session = temporaryFolder.newFolder("2026_02_01_10_00_00");
        write(new File(session, "movie.mp4"), new byte[100]);
        File selected = temporaryFolder.newFolder("Documents");
        AtomicBoolean cancelled = new AtomicBoolean(false);

        try {
            RecordingExportManager.export(session, new LocalDirectory(selected, false),
                    (copied, total, name) -> cancelled.set(true), cancelled);
            fail("Expected cancellation");
        } catch (RecordingExportManager.ExportCancelledException expected) {
            assertFalse(new File(selected, "MarsLogger/" + session.getName()).exists());
        }
    }

    @Test
    public void changedSourceIsNotReportedAsSuccessfulExport() throws Exception {
        File session = temporaryFolder.newFolder("2026_02_01_10_00_00");
        File movie = new File(session, "movie.mp4");
        write(movie, new byte[1_500_000]);
        File selected = temporaryFolder.newFolder("Documents");
        AtomicBoolean changed = new AtomicBoolean(false);

        try {
            RecordingExportManager.export(session, new LocalDirectory(selected, false),
                    (copied, total, name) -> {
                        if (copied > 0 && changed.compareAndSet(false, true)) {
                            try (OutputStream output = new FileOutputStream(movie, true)) {
                                output.write(1);
                            } catch (IOException error) {
                                throw new RuntimeException(error);
                            }
                        }
                    }, new AtomicBoolean(false));
            fail("Expected changed-source failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Recording changed"));
            assertFalse(new File(selected, "MarsLogger/" + session.getName()).exists());
        }
    }

    private static void write(File file, byte[] data) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
    }

    private static final class LocalDirectory implements RecordingExportManager.DestinationDirectory {
        private final File directory;
        private final boolean failWrites;

        LocalDirectory(File directory, boolean failWrites) {
            this.directory = directory;
            this.failWrites = failWrites;
        }

        @Override public String getName() {
            return directory.getName();
        }

        @Override public RecordingExportManager.DestinationDirectory findDirectory(String name) {
            File child = new File(directory, name);
            return child.isDirectory() ? new LocalDirectory(child, failWrites) : null;
        }

        @Override public boolean childExists(String name) {
            return new File(directory, name).exists();
        }

        @Override public RecordingExportManager.DestinationDirectory createDirectory(String name)
                throws IOException {
            File child = new File(directory, name);
            if (!child.mkdir()) {
                throw new IOException("Could not create " + name);
            }
            return new LocalDirectory(child, failWrites);
        }

        @Override public OutputStream createFile(String name) throws IOException {
            OutputStream output = new FileOutputStream(new File(directory, name));
            if (!failWrites) {
                return output;
            }
            return new FilterOutputStream(output) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    out.write(bytes, offset, Math.min(length, 16));
                    throw new IOException("simulated write failure");
                }
            };
        }

        @Override public boolean delete() {
            deleteRecursively(directory);
            return !directory.exists();
        }

        private static void deleteRecursively(File file) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
            file.delete();
        }
    }
}
