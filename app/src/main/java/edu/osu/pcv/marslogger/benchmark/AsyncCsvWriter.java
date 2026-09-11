package edu.osu.pcv.marslogger.benchmark;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A bounded, buffered CSV sink. The caller never opens, writes, flushes, or closes a file. */
final class AsyncCsvWriter<T extends AsyncCsvWriter.CsvWritable> {
    interface CsvWritable {
        void writeCsv(BufferedWriter writer) throws IOException;
    }

    private final File file;
    private final String header;
    private final ArrayBlockingQueue<T> queue;
    private final Thread worker;
    private volatile boolean closing;
    private volatile IOException failure;

    AsyncCsvWriter(File file, String header, int capacity) {
        this.file = file;
        this.header = header;
        queue = new ArrayBlockingQueue<>(capacity);
        worker = new Thread(this::writeLoop, "benchmark-csv-writer");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    boolean offer(T row) {
        return !closing && queue.offer(row);
    }

    void closeAsync() {
        closing = true;
        worker.interrupt();
    }

    boolean awaitClosed(long timeoutMs) throws InterruptedException {
        worker.join(timeoutMs);
        return !worker.isAlive();
    }

    File getFile() {
        return file;
    }

    IOException getFailure() {
        return failure;
    }

    private void writeLoop() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            failure = new IOException("Cannot create benchmark directory: " + parent);
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false), 32 * 1024)) {
            writer.write(header);
            writer.newLine();
            int rowsSinceFlush = 0;
            while (!closing || !queue.isEmpty()) {
                T row;
                try {
                    row = queue.poll(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    continue;
                }
                if (row != null) {
                    row.writeCsv(writer);
                    writer.newLine();
                    rowsSinceFlush++;
                }
                if (rowsSinceFlush > 0 && (row == null || rowsSinceFlush >= 16)) {
                    writer.flush();
                    rowsSinceFlush = 0;
                }
            }
        } catch (IOException e) {
            failure = e;
        }
    }
}
