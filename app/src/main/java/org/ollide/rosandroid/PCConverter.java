package org.ollide.rosandroid;

import org.jboss.netty.buffer.ChannelBuffer;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import sensor_msgs.PointCloud2;
import sensor_msgs.PointField;
import sg.edu.nus.comp.android3dvisualisationtool.app.points.Point;
import sg.edu.nus.comp.android3dvisualisationtool.app.points.VizPoint;

/** Conversion of ROS PointCloud2 data used by the visualization only. */
public final class PCConverter {
    public interface XYZConsumer {
        void accept(float x, float y, float z);
    }

    private PCConverter() { }

    public static List<Point> toPointList(PointCloud2 msg) {
        Reader reader = new Reader(msg);
        List<Point> points = new ArrayList<>(Math.max(0, reader.count()));
        int seq = msg.getHeader().getSeq();
        reader.forEach((x, y, z, intensity) ->
                points.add(new Point(new VizPoint(x, y, z, intensity), seq)), true);
        return points;
    }

    /** Visits raw LiDAR coordinates without retaining a historical scan. */
    public static void forEachXYZ(PointCloud2 msg, XYZConsumer consumer) {
        new Reader(msg).forEach((x, y, z, intensity) -> consumer.accept(x, y, z), false);
    }

    private interface XYZIConsumer {
        void accept(float x, float y, float z, float intensity);
    }

    private static final class Reader {
        private final PointCloud2 msg;
        private final ChannelBuffer data;
        private final boolean swap;
        private final PointField xField;
        private final PointField yField;
        private final PointField zField;
        private final PointField intensityField;

        Reader(PointCloud2 msg) {
            this.msg = msg;
            data = msg.getData();
            swap = (data.order() == ByteOrder.BIG_ENDIAN) != msg.getIsBigendian();
            PointField x = null, y = null, z = null, intensity = null;
            for (PointField field : msg.getFields()) {
                switch (field.getName()) {
                    case "x": x = field; break;
                    case "y": y = field; break;
                    case "z": z = field; break;
                    case "intensity": intensity = field; break;
                    case "reflectivity":
                        if (intensity == null) intensity = field;
                        break;
                    default: break;
                }
            }
            xField = x;
            yField = y;
            zField = z;
            intensityField = intensity;
        }

        int count() {
            long count = (long) msg.getHeight() * msg.getWidth();
            return count > Integer.MAX_VALUE ? 0 : (int) Math.max(0, count);
        }

        void forEach(XYZIConsumer consumer, boolean readIntensity) {
            if (xField == null || yField == null || zField == null
                    || msg.getPointStep() <= 0 || msg.getRowStep() <= 0) return;
            int width = msg.getWidth();
            int height = msg.getHeight();
            int step = msg.getPointStep();
            for (int row = 0; row < height; row++) {
                long rowStart = (long) row * msg.getRowStep();
                for (int col = 0; col < width; col++) {
                    long start = rowStart + (long) col * step;
                    if (start < 0 || start + step > data.writerIndex()) return;
                    float x = read(xField, (int) start, step);
                    float y = read(yField, (int) start, step);
                    float z = read(zField, (int) start, step);
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
                    float intensity = !readIntensity || intensityField == null ? Float.NaN
                            : read(intensityField, (int) start, step);
                    consumer.accept(x, y, z, intensity);
                }
            }
        }

        private float read(PointField field, int start, int step) {
            int offset = field.getOffset();
            int type = field.getDatatype();
            int bytes;
            switch (type) {
                case 1: case 2: bytes = 1; break;
                case 3: case 4: bytes = 2; break;
                case 5: case 6: case 7: bytes = 4; break;
                case 8: bytes = 8; break;
                default: return Float.NaN;
            }
            if (offset < 0 || offset + bytes > step) return Float.NaN;
            int at = start + offset;
            switch (type) {
                case 1: return data.getByte(at);
                case 2: return data.getUnsignedByte(at);
                case 3: return (short) read16(at);
                case 4: return read16(at) & 0xffff;
                case 5: return read32(at);
                case 6: return read32(at) & 0xffffffffL;
                case 7: return Float.intBitsToFloat(read32(at));
                case 8: return (float) Double.longBitsToDouble(read64(at));
                default: return Float.NaN;
            }
        }

        private int read16(int at) {
            short value = data.getShort(at);
            return swap ? Short.reverseBytes(value) : value;
        }

        private int read32(int at) {
            int value = data.getInt(at);
            return swap ? Integer.reverseBytes(value) : value;
        }

        private long read64(int at) {
            long value = data.getLong(at);
            return swap ? Long.reverseBytes(value) : value;
        }
    }
}
