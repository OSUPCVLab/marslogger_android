package edu.osu.pcv.marslogger.benchmark;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Samples root-free Android process/device metrics on a dedicated one-Hz worker. */
public final class DeviceStatsLogger {
    public static final String CSV_HEADER =
            "session_id,elapsed_time_s,sample_timestamp_ns,process_cpu_percent,pss_kb,rss_kb," +
            "java_heap_used_kb,java_heap_max_kb,native_heap_allocated_kb,battery_percent," +
            "battery_current_ma,battery_voltage_v,battery_temperature_c,device_temperature_c," +
            "thermal_status,cpu_frequencies_khz";

    interface MetricsProvider {
        DeviceSample sample(long timestampNs);
        void reset();
    }

    private final MonotonicClock clock;
    private final MetricsProvider metrics;
    private volatile boolean active;
    private BenchmarkSession session;
    private AsyncCsvWriter<DeviceRow> writer;
    private ScheduledExecutorService sampler;

    public DeviceStatsLogger(Context context) {
        this(MonotonicClock.SYSTEM, new AndroidMetricsProvider(context.getApplicationContext()));
    }

    DeviceStatsLogger(MonotonicClock clock, MetricsProvider metrics) {
        this.clock = clock;
        this.metrics = metrics;
    }

    public synchronized void start(BenchmarkSession newSession) {
        if (active) {
            return;
        }
        session = newSession;
        metrics.reset();
        writer = new AsyncCsvWriter<>(newSession.deviceStatsFile, CSV_HEADER, 128);
        active = true;
        sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "benchmark-device-sampler");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        sampler.scheduleAtFixedRate(this::sampleOnce, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        ScheduledExecutorService samplerToStop;
        AsyncCsvWriter<DeviceRow> writerToClose;
        synchronized (this) {
            if (!active) {
                return;
            }
            active = false;
            samplerToStop = sampler;
            writerToClose = writer;
        }
        samplerToStop.shutdownNow();
        writerToClose.closeAsync();
    }

    public boolean isActive() {
        return active;
    }

    boolean awaitStopped(long timeoutMs) throws InterruptedException {
        AsyncCsvWriter<DeviceRow> currentWriter = writer;
        return currentWriter == null || currentWriter.awaitClosed(timeoutMs);
    }

    void sampleOnce() {
        if (!active) {
            return;
        }
        long nowNs = clock.nowNanos();
        DeviceSample sample = metrics.sample(nowNs);
        DeviceRow row;
        AsyncCsvWriter<DeviceRow> currentWriter;
        synchronized (this) {
            if (!active) {
                return;
            }
            row = new DeviceRow(session.sessionId,
                    (nowNs - session.startElapsedRealtimeNanos) / 1_000_000_000.0, sample);
            currentWriter = writer;
        }
        // A delayed sampler must never block; a full queue drops this device sample.
        currentWriter.offer(row);
    }

    public static final class DeviceSample {
        public final long timestampNs;
        public final double processCpuPercent;
        public final double pssKb;
        public final double rssKb;
        public final double javaHeapUsedKb;
        public final double javaHeapMaxKb;
        public final double nativeHeapAllocatedKb;
        public final double batteryPercent;
        public final double batteryCurrentMa;
        public final double batteryVoltageV;
        public final double batteryTemperatureC;
        public final double deviceTemperatureC;
        public final double thermalStatus;
        public final String cpuFrequenciesKhz;

        public DeviceSample(long timestampNs, double processCpuPercent, double pssKb,
                            double rssKb, double javaHeapUsedKb, double javaHeapMaxKb,
                            double nativeHeapAllocatedKb, double batteryPercent,
                            double batteryCurrentMa, double batteryVoltageV,
                            double batteryTemperatureC, double deviceTemperatureC,
                            double thermalStatus, String cpuFrequenciesKhz) {
            this.timestampNs = timestampNs;
            this.processCpuPercent = processCpuPercent;
            this.pssKb = pssKb;
            this.rssKb = rssKb;
            this.javaHeapUsedKb = javaHeapUsedKb;
            this.javaHeapMaxKb = javaHeapMaxKb;
            this.nativeHeapAllocatedKb = nativeHeapAllocatedKb;
            this.batteryPercent = batteryPercent;
            this.batteryCurrentMa = batteryCurrentMa;
            this.batteryVoltageV = batteryVoltageV;
            this.batteryTemperatureC = batteryTemperatureC;
            this.deviceTemperatureC = deviceTemperatureC;
            this.thermalStatus = thermalStatus;
            this.cpuFrequenciesKhz = cpuFrequenciesKhz == null ? "" : cpuFrequenciesKhz;
        }

        public static DeviceSample unavailable(long timestampNs) {
            return new DeviceSample(timestampNs, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, "");
        }
    }

    private static final class AndroidMetricsProvider implements MetricsProvider {
        private final Context context;
        private final BatteryManager batteryManager;
        private final PowerManager powerManager;
        private long priorWallNs;
        private long priorCpuTicks;

        AndroidMetricsProvider(Context context) {
            this.context = context;
            batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        }

        @Override
        public void reset() {
            priorWallNs = 0;
            priorCpuTicks = -1;
        }

        @Override
        public DeviceSample sample(long timestampNs) {
            long cpuTicks = readProcessCpuTicks();
            double cpuPercent = Double.NaN;
            long ticksPerSecond = sysconf(OsConstants._SC_CLK_TCK);
            if (priorWallNs > 0 && priorCpuTicks >= 0 && cpuTicks >= priorCpuTicks
                    && timestampNs > priorWallNs && ticksPerSecond > 0) {
                double cpuSeconds = (cpuTicks - priorCpuTicks) / (double) ticksPerSecond;
                double wallSeconds = (timestampNs - priorWallNs) / 1_000_000_000.0;
                cpuPercent = 100.0 * cpuSeconds / wallSeconds;
            }
            priorWallNs = timestampNs;
            priorCpuTicks = cpuTicks;

            double pssKb = Debug.getPss();
            if (pssKb <= 0) {
                pssKb = Double.NaN;
            }
            double rssKb = readRssKb();
            Runtime runtime = Runtime.getRuntime();
            double javaUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024.0;
            double javaMaxKb = runtime.maxMemory() / 1024.0;
            double nativeKb = Debug.getNativeHeapAllocatedSize() / 1024.0;

            double batteryPercent = Double.NaN;
            double voltageV = Double.NaN;
            double batteryTempC = Double.NaN;
            Intent battery = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    batteryPercent = 100.0 * level / scale;
                }
                int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                if (voltageMv > 0) {
                    voltageV = voltageMv / 1000.0;
                }
                int tempTenthsC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                        Integer.MIN_VALUE);
                if (tempTenthsC != Integer.MIN_VALUE) {
                    batteryTempC = tempTenthsC / 10.0;
                }
            }

            double currentMa = Double.NaN;
            if (batteryManager != null) {
                long currentUa = batteryManager.getLongProperty(
                        BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (currentUa != Long.MIN_VALUE) {
                    currentMa = currentUa / 1000.0;
                }
            }

            double thermalStatus = Double.NaN;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                thermalStatus = powerManager.getCurrentThermalStatus();
            }

            return new DeviceSample(timestampNs, cpuPercent, pssKb, rssKb, javaUsedKb,
                    javaMaxKb, nativeKb, batteryPercent, currentMa, voltageV, batteryTempC,
                    Double.NaN, thermalStatus, readCpuFrequencies());
        }

        private static long readProcessCpuTicks() {
            String stat = readFirstLine(new File("/proc/self/stat"));
            if (stat == null) {
                return -1;
            }
            int closingParen = stat.lastIndexOf(')');
            if (closingParen < 0 || closingParen + 2 >= stat.length()) {
                return -1;
            }
            String[] fields = stat.substring(closingParen + 2).split("\\s+");
            try {
                // The substring starts at field 3 (state); utime/stime are fields 14 and 15.
                return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }

        private static double readRssKb() {
            String statm = readFirstLine(new File("/proc/self/statm"));
            if (statm == null) {
                return Double.NaN;
            }
            String[] fields = statm.trim().split("\\s+");
            try {
                long pageSize = sysconf(OsConstants._SC_PAGESIZE);
                return pageSize > 0 ? Long.parseLong(fields[1]) * pageSize / 1024.0 : Double.NaN;
            } catch (RuntimeException ignored) {
                return Double.NaN;
            }
        }

        private static String readCpuFrequencies() {
            int cores = Runtime.getRuntime().availableProcessors();
            StringBuilder values = new StringBuilder(cores * 10);
            for (int cpu = 0; cpu < cores; cpu++) {
                File frequency = new File("/sys/devices/system/cpu/cpu" + cpu
                        + "/cpufreq/scaling_cur_freq");
                String value = readFirstLine(frequency);
                if (value != null && !value.trim().isEmpty()) {
                    if (values.length() > 0) {
                        values.append('|');
                    }
                    values.append("cpu").append(cpu).append('=').append(value.trim());
                }
            }
            return values.toString();
        }

        private static String readFirstLine(File file) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return reader.readLine();
            } catch (IOException | SecurityException ignored) {
                return null;
            }
        }

        private static long sysconf(int name) {
            try {
                return Os.sysconf(name);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }
    }

    static final class DeviceRow implements AsyncCsvWriter.CsvWritable {
        private final String sessionId;
        private final double elapsedTime;
        private final DeviceSample sample;

        DeviceRow(String sessionId, double elapsedTime, DeviceSample sample) {
            this.sessionId = sessionId;
            this.elapsedTime = elapsedTime;
            this.sample = sample;
        }

        @Override
        public void writeCsv(BufferedWriter out) throws IOException {
            out.write(sessionId);
            comma(out, elapsedTime);
            comma(out, sample.timestampNs);
            comma(out, sample.processCpuPercent);
            comma(out, sample.pssKb);
            comma(out, sample.rssKb);
            comma(out, sample.javaHeapUsedKb);
            comma(out, sample.javaHeapMaxKb);
            comma(out, sample.nativeHeapAllocatedKb);
            comma(out, sample.batteryPercent);
            comma(out, sample.batteryCurrentMa);
            comma(out, sample.batteryVoltageV);
            comma(out, sample.batteryTemperatureC);
            comma(out, sample.deviceTemperatureC);
            comma(out, sample.thermalStatus);
            comma(out, sample.cpuFrequenciesKhz);
        }

        private static void comma(BufferedWriter out, String value) throws IOException {
            out.write(',');
            out.write(value);
        }

        private static void comma(BufferedWriter out, long value) throws IOException {
            comma(out, Long.toString(value));
        }

        private static void comma(BufferedWriter out, double value) throws IOException {
            comma(out, Double.toString(value));
        }
    }
}
