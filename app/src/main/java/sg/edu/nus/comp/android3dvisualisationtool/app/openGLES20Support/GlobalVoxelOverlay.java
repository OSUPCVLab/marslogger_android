package sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Draws world-frame voxel centroids in the main LiDAR view. */
final class GlobalVoxelOverlay {
    private FloatBuffer centroids;
    private int count;
    private int program;
    private int positionHandle;
    private int matrixHandle;

    void onContextCreated() {
        program = 0;
    }

    void setCentroids(float[] xyz) {
        count = xyz.length / 3;
        centroids = ByteBuffer.allocateDirect(xyz.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        centroids.put(xyz).position(0);
    }

    void clear() {
        centroids = null;
        count = 0;
    }

    void draw(float[] cloudMvp, float[] cloudTransform) {
        if (count == 0 || centroids == null || !ensureProgram()) return;
        float[] model = new float[16];
        float[] mvp = new float[16];
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, -cloudTransform[1], -cloudTransform[2],
                -cloudTransform[3]);
        Matrix.scaleM(model, 0, cloudTransform[0], cloudTransform[0], cloudTransform[0]);
        Matrix.multiplyMM(mvp, 0, cloudMvp, 0, model, 0);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0);
        centroids.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, centroids);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count);
        GLES20.glDisableVertexAttribArray(positionHandle);
    }

    private boolean ensureProgram() {
        if (program != 0) return program > 0;
        int vertex = GLES20Renderer.loadShader(GLES20.GL_VERTEX_SHADER,
                "uniform mat4 uMatrix; attribute vec3 aPosition;"
                        + "void main() { gl_Position = uMatrix * vec4(aPosition, 1.0);"
                        + "gl_PointSize = 3.0; }");
        int fragment = GLES20Renderer.loadShader(GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; void main() {"
                        + "gl_FragColor = vec4(0.18, 0.39, 0.62, 1.0); }");
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e("GlobalVoxelOverlay", "Could not link voxel shader: "
                    + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = -1;
        }
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (program < 0) return false;
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        matrixHandle = GLES20.glGetUniformLocation(program, "uMatrix");
        return true;
    }
}
