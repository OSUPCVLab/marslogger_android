package edu.osu.pcv.marslogger.benchmark;

import java.util.Arrays;

/** Allocation-free while adding samples; allocation and sorting only occur for snapshots. */
public final class LatencyStatistics {
    private double[] values = new double[128];
    private int size;
    private double sum;

    public synchronized void add(double value) {
        if (Double.isNaN(value)) {
            return;
        }
        if (size == values.length) {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = value;
        sum += value;
    }

    public synchronized void clear() {
        size = 0;
        sum = 0.0;
    }

    public synchronized Snapshot snapshot() {
        if (size == 0) {
            return new Snapshot(0, Double.NaN, Double.NaN, Double.NaN);
        }
        double[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return new Snapshot(size, sum / size, percentile(sorted, 0.5), percentile(sorted, 0.95));
    }

    private static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double position = fraction * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = position - lower;
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
    }

    public static final class Snapshot {
        public final int count;
        public final double mean;
        public final double median;
        public final double p95;

        Snapshot(int count, double mean, double median, double p95) {
            this.count = count;
            this.mean = mean;
            this.median = median;
            this.p95 = p95;
        }
    }
}
