package sg.edu.nus.comp.android3dvisualisationtool.app.openGLES20Support;

import android.content.Context;
import android.opengl.GLSurfaceView;
import androidx.core.view.MotionEventCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import sg.edu.nus.comp.android3dvisualisationtool.app.UI.NavigationDrawerFragment;
import sg.edu.nus.comp.android3dvisualisationtool.app.UI.SliderFragment;
import sg.edu.nus.comp.android3dvisualisationtool.app.configuration.Constants;
import sg.edu.nus.comp.android3dvisualisationtool.app.points.Points;

/**
 * Created by panlong on 6/6/14.
 */
public class GLES20SurfaceView extends GLSurfaceView implements Constants {
    private GLES20Renderer mRenderer = null;
    private ScaleGestureDetector detector;
    private float scaleFactor = 1;
    private boolean isSetToOrigin = DEFAULT_IS_SET_TO_ORIGIN;
    private boolean isAxesVisible = DEFAULT_IS_AXES_VISIBLE;
    private boolean isNormalVectorVisible = DEFAULT_IS_NORMAL_VECTOR_VISIBLE;
    private boolean isCurvatureVisible = DEFAULT_IS_SELECTING_CURVATURE;
    private boolean isScaling = false;

    private float prevRadius = 1.0f;
    private float prevCurvature = 0.5f;
    private float prevCameraDistance = (float) DEFAULT_CAMERA_DISTANCE;
    private float prevFieldOfView = (float) DEFAULT_FIELD_OF_VIEW;

    public GLES20SurfaceView(Context context) {
        super(context);
        configureRenderer();
    }

    public GLES20SurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        configureRenderer();
    }

    private void configureRenderer() {
        // Create an OpenGL ES 2.0 context.
        setEGLContextClientVersion(2);

        // Set the Renderer for drawing on the GLSurfaceView
        mRenderer = new GLES20Renderer();
        setRenderer(mRenderer);

        // Render the view only when there is a change in the drawing data
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        //set up pinch gesture
        detector = new ScaleGestureDetector(getContext(), new ScaleListener());

        Thread listenCheckBox = new Thread() {
            @Override
            public void run() {
                super.run();
                while (true) {
                    try {
                        boolean reRenderNeeded = false;

                        if (NavigationDrawerFragment.getSetOrigin() != isSetToOrigin) {
                            isSetToOrigin = !isSetToOrigin;
                            Points.setOrigin(isSetToOrigin);
                            reRenderNeeded = true;
                        }

                        if (NavigationDrawerFragment.getShowAxes() != isAxesVisible) {
                            isAxesVisible = !isAxesVisible;
                            reRenderNeeded = true;
                        }

                        if (NavigationDrawerFragment.getShowCurvature() != isCurvatureVisible) {
                            isCurvatureVisible = !isCurvatureVisible;
                            Points.setSelectingCurvature(isCurvatureVisible);
                            reRenderNeeded = true;
                        }

                        if (NavigationDrawerFragment.getShowNormal() != isNormalVectorVisible) {
                            isNormalVectorVisible = !isNormalVectorVisible;
                            Points.setShowNormalVector(isNormalVectorVisible);
                            reRenderNeeded = true;
                        }

                        if (SliderFragment.getRadiusScale() != prevRadius) {
                            prevRadius = SliderFragment.getRadiusScale();
                            reRenderNeeded = true;
                        }
                        if (SliderFragment.getCurvature() != prevCurvature) {
                            prevCurvature = SliderFragment.getCurvature();
                            Points.setCurvature(prevCurvature);
                            reRenderNeeded = true;
                        }
                        if (SliderFragment.getCameraDistance() != prevCameraDistance) {
                            prevCameraDistance = SliderFragment.getCameraDistance();
                            mRenderer.setCameraDistance(prevCameraDistance);
                            reRenderNeeded = true;
                        }
                        if (SliderFragment.getFieldOfView() != prevFieldOfView) {
                            prevFieldOfView = SliderFragment.getFieldOfView();
                            mRenderer.setCameraFieldOfView(prevFieldOfView);
                            reRenderNeeded = true;
                        }

                        if (reRenderNeeded)
                            requestRender();

                        Thread.sleep(DEFAULT_SLEEP_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        listenCheckBox.start();
    }

    private float[] mPreviousX = new float[2];
    private float[] mPreviousY = new float[2];
    private final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        detector.onTouchEvent(ev);

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = MotionEventCompat.getX(ev, 0);
                final float y = MotionEventCompat.getY(ev, 0);
                // Remember where we started (for dragging)
                mPreviousX[0] = x;
                mPreviousY[0] = y;

                // Save the ID of this pointer (for dragging)
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);

                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mActivePointerId != INVALID_POINTER_ID) {
                    if (ev.getPointerCount() == 1) {
                        // Find the index of the active pointer and fetch its position
                        final float x = MotionEventCompat.getX(ev, 0);
                        final float y = MotionEventCompat.getY(ev, 0);

                        mRenderer.setRotation((int) mPreviousX[0], (int) mPreviousY[0], (int) x, (int) y);

                        requestRender();

                        // Remember this touch position for the next move event
                        mPreviousX[0] = x;
                        mPreviousY[0] = y;
                    } else if (ev.getPointerCount() >= 2) {
                        final float x0 = MotionEventCompat.getX(ev, 0);
                        final float y0 = MotionEventCompat.getY(ev, 0);
                        final float x1 = MotionEventCompat.getX(ev, 1);
                        final float y1 = MotionEventCompat.getY(ev, 1);

                        if ((x0 - mPreviousX[0]) * (x1 - mPreviousX[1])
                                + (y0 - mPreviousY[0]) * (y1 - mPreviousY[1]) > 0) {
                            isScaling = false;
                            double scale = 1.0 / (mRenderer.getWindowWidth() / (2 * DEFAULT_MAX_ABS_COORIDINATE))
                                    * (mRenderer.getCameraDistance() / DEFAULT_CAMERA_DISTANCE)
                                    * (mRenderer.getCameraFieldOfView() / DEFAULT_FIELD_OF_VIEW);

                            mRenderer.shiftCameraLookAtPoint((float) ((mPreviousX[0] - x0) * scale), (float) ((y0 - mPreviousY[0]) * scale));

                            requestRender();
                        } else {
                            isScaling = true;
                        }

                        mPreviousX[0] = x0;
                        mPreviousY[0] = y0;
                        mPreviousX[1] = x1;
                        mPreviousY[1] = y1;
                    }
                }

                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);
                if (pointerIndex == 0)
                    mActivePointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                if (ev.getPointerCount() >= 2) {
                    mPreviousX[0] = MotionEventCompat.getX(ev, 0);
                    mPreviousY[0] = MotionEventCompat.getY(ev, 0);
                    mPreviousX[1] = MotionEventCompat.getX(ev, 1);
                    mPreviousY[1] = MotionEventCompat.getY(ev, 1);

                    mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                }

                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (isScaling) {
                scaleFactor *= detector.getScaleFactor();
                mRenderer.setCameraDistanceByScale(scaleFactor);
                requestRender();
            }
            return true;
        }
    }
}
