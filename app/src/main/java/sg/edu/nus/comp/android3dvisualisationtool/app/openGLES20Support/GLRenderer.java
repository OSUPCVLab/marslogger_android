package sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by panlong on 6/6/14.
 */
public abstract class GLRenderer implements GLSurfaceView.Renderer {
    private boolean isFirstDraw;
    private boolean isSurfaceCreated;
    private int mWidth;
    private int mHeight;

    public GLRenderer() {
        isFirstDraw = true;
        isSurfaceCreated = false;
        mWidth = -1;
        mHeight = -1;
    }

    @Override
    public void onSurfaceCreated(GL10 notUsed, EGLConfig config) {
        isSurfaceCreated = true;
        mWidth = -1;
        mHeight = -1;
    }

    @Override
    public void onSurfaceChanged(GL10 notUsed, int width, int height) {
        if (!isSurfaceCreated && width == mWidth && height == mHeight)
            return;

        mWidth = width;
        mHeight = height;

        onCreate(mWidth, mHeight, isSurfaceCreated);
        if (isSurfaceCreated)
            onCreate(mWidth, mHeight, false);

        isSurfaceCreated = false;
    }

    @Override
    public void onDrawFrame(GL10 notUsed) {
        onDrawFrame(isFirstDraw);
        isFirstDraw = false;
    }

    public abstract void onCreate(int width, int height, boolean isContextLost);

    public abstract void onDrawFrame(boolean isFirstDraw);
}
