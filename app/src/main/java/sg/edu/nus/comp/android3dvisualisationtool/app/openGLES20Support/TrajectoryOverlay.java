package sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** Draws both odometry paths using the same transform as the LiDAR point cloud. */
final class TrajectoryOverlay {
    private static final int MAX_SAMPLES = 4096;
    private static final float MIN_STEP_SQUARED = 0.05f * 0.05f;
    private static final float[] LIDAR_COLOR = {1f, 0.15f, 0.12f, 1f};
    private static final float[] ARCORE_COLOR = {0.1f, 0.95f, 1f, 1f};

    private final List<float[]> lidar = new ArrayList<>();
    private final List<float[]> arcore = new ArrayList<>();
    private int arcoreOriginId = Integer.MIN_VALUE;
    private FloatBuffer lidarBuffer;
    private FloatBuffer arcoreBuffer;
    private boolean lidarDirty = true;
    private boolean arcoreDirty = true;
    private int program;
    private int positionHandle;
    private int matrixHandle;
    private int colorHandle;

    void onContextCreated() {
        program = 0;
    }

    void clear() {
        lidar.clear();
        lidarDirty = true;
        clearArCore();
    }

    void clearArCore() {
        arcore.clear();
        arcoreOriginId = Integer.MIN_VALUE;
        arcoreDirty = true;
    }

    boolean appendLidar(float x, float y, float z) {
        boolean added = append(lidar, x, y, z);
        if (added) {
            lidarDirty = true;
        }
        return added;
    }

    boolean appendArCore(int originId, float x, float y, float z) {
        boolean changed = false;
        if (originId != arcoreOriginId) {
            arcore.clear();
            arcoreOriginId = originId;
            arcoreDirty = true;
            changed = true;
        }
        if (append(arcore, x, y, z)) {
            arcoreDirty = true;
            changed = true;
        }
        return changed;
    }

    private static boolean append(List<float[]> path, float x, float y, float z) {
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)
                || Float.isInfinite(x) || Float.isInfinite(y) || Float.isInfinite(z)) {
            return false;
        }
        if (!path.isEmpty()) {
            float[] last = path.get(path.size() - 1);
            float dx = x - last[0];
            float dy = y - last[1];
            float dz = z - last[2];
            if (dx * dx + dy * dy + dz * dz < MIN_STEP_SQUARED) {
                return false;
            }
        }
        if (path.size() == MAX_SAMPLES) {
            path.remove(0);
        }
        path.add(new float[]{x, y, z});
        return true;
    }

    void draw(float[] cloudMvp, float[] cloudTransform) {
        if (lidar.isEmpty() && arcore.isEmpty()) {
            return;
        }
        if (!ensureProgram()) {
            return;
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        float[] model = new float[16];
        float[] mvp = new float[16];
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, -cloudTransform[1], -cloudTransform[2],
                -cloudTransform[3]);
        Matrix.scaleM(model, 0, cloudTransform[0], cloudTransform[0], cloudTransform[0]);
        Matrix.multiplyMM(mvp, 0, cloudMvp, 0, model, 0);
        if (!lidar.isEmpty()) {
            if (lidarDirty) {
                lidarBuffer = rawBuffer(lidar);
                lidarDirty = false;
            }
            drawPath(lidarBuffer, lidar.size(), mvp, LIDAR_COLOR);
        }
        if (!arcore.isEmpty()) {
            if (arcoreDirty) {
                arcoreBuffer = rawBuffer(arcore);
                arcoreDirty = false;
            }
            drawPath(arcoreBuffer, arcore.size(), mvp, ARCORE_COLOR);
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private boolean ensureProgram() {
        if (program != 0) {
            return program > 0;
        }
        int vertex = GLES20Renderer.loadShader(GLES20.GL_VERTEX_SHADER,
                "uniform mat4 uMatrix; attribute vec3 aPosition;"
                        + "void main() { gl_Position = uMatrix * vec4(aPosition, 1.0);"
                        + "gl_PointSize = 7.0; }");
        int fragment = GLES20Renderer.loadShader(GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; uniform vec4 uColor;"
                        + "void main() { gl_FragColor = uColor; }");
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e("TrajectoryOverlay", "Could not link trajectory shader: "
                    + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = -1;
        }
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (program < 0) {
            return false;
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix");
        colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        return true;
    }

    private void drawPath(FloatBuffer buffer, int count, float[] mvp, float[] color) {
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0);
        GLES20.glUniform4fv(colorHandle, 1, color, 0);
        buffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);
        if (count > 1) {
            GLES20.glLineWidth(2f);
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count);
        }
        GLES20.glDrawArrays(GLES20.GL_POINTS, count - 1, 1);
        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    private static FloatBuffer rawBuffer(List<float[]> path) {
        FloatBuffer buffer = allocate(path.size() * 3);
        for (float[] position : path) {
            buffer.put(position);
        }
        buffer.position(0);
        return buffer;
    }

    private static FloatBuffer allocate(int floats) {
        return ByteBuffer.allocateDirect(floats * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
