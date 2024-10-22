package sg.edu.nus.comp.android3dvisualisationtool.app.axis;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support.GLES20Renderer;

/**
 * Created by panlong on 17/6/14.
 * use cuboid to draw axis
 */
public class Axis {

    private static final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private static final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private FloatBuffer vertexBuffer;
    private int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mMVPMatrixHandle;

    // number of coordinates per vertex in this array
    private static final int COORDS_PER_VERTEX = 3;

    private static final int vertexCount = 6 * 3 * 2;
    private static final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    private float[] pointCoords = new float[108];

    private float mLength, mWidth, mHeight;
    private float[] mColor;

    public Axis(float length, float width, float height, float[] color) {
        mLength = length;
        mWidth = width;
        mHeight = height;
        mColor = color;

        setUp();
    }

    /**
     * generate coordinates array for cuboid
     */
    private void generateCoordsArray() {
        float x = mLength / 2;
        float y = mWidth / 2;
        float z = mHeight / 2;

        pointCoords = new float[]{
                x, -y, -z,
                x, y, -z,
                x, y, z,
                x, -y, -z,
                x, y, z,
                x, -y, z,

                -x, -y, -z,
                -x, y, -z,
                -x, y, z,
                -x, -y, -z,
                -x, y, z,
                -x, -y, z,

                x, y, -z,
                -x, y, -z,
                -x, y, z,
                x, y, -z,
                -x, y, z,
                x, y, z,

                x, -y, -z,
                -x, -y, -z,
                -x, -y, z,
                x, -y, -z,
                -x, -y, z,
                x, -y, z,

                x, -y, z,
                x, y, z,
                -x, y, z,
                x, -y, z,
                -x, y, z,
                -x, -y, z,

                x, -y, -z,
                x, y, -z,
                -x, y, -z,
                x, -y, -z,
                -x, y, -z,
                -x, -y, -z
        };
    }

    private void prepareProgram() {
        // prepare shaders and OpenGL program
        int vertexShader = GLES20Renderer.loadShader(
                GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = GLES20Renderer.loadShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);                  // create OpenGL program executables
    }

    private void initBuffer() {
        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (number of coordinate values * 4 bytes per float)
                pointCoords.length * 4);
        // use the device hardware's native byte order
        bb.order(ByteOrder.nativeOrder());

        // create a floating point buffer from the ByteBuffer
        vertexBuffer = bb.asFloatBuffer();
        // add the coordinates to the FloatBuffer
        vertexBuffer.put(pointCoords);
        // set the buffer to read the first coordinate
        vertexBuffer.position(0);
    }

    private void setUp() {
        generateCoordsArray();
        initBuffer();
        prepareProgram();
    }

    /**
     * Encapsulates the OpenGL ES instructions for drawing this shape.
     *
     * @param mvpMatrix - The Model View Project matrix in which to draw
     *                  this shape.
     */
    public void draw(float[] mvpMatrix) {
        // Add program to OpenGL environment
        GLES20.glUseProgram(mProgram);

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        GLES20.glVertexAttribPointer(
                mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");

        GLES20.glUniform4fv(mColorHandle, 1, mColor, 0);

        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20Renderer.checkGlError("glGetUniformLocation");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20Renderer.checkGlError("glUniformMatrix4fv");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }
}
