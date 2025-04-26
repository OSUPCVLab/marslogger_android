package edu.osu.pcv.marslogger;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Message;

import timber.log.Timber;

/**
 * Handles camera operation requests from other threads.  Necessary because the Camera
 * must only be accessed from one thread.
 * <p>
 * The object is created on the UI thread, and all handlers run there.  Messages are
 * sent from other threads, using sendMessage().
 */
public class CameraHandler extends Handler {
    public static final int MSG_SET_SURFACE_TEXTURE = 0;
    public static final int MSG_MANUAL_FOCUS = 1;

    // Weak reference to the Activity; only access this from the UI thread.
    private final CameraCapture mCameraCapture;

    public CameraHandler(CameraCapture capture) {
        mCameraCapture = capture;
    }

    public void invalidateHandler() {
    }


    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Object obj = inputMessage.obj;

        Timber.d("CameraHandler [%s]: what=%d", this.toString(), what);

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                mCameraCapture.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                break;
            case MSG_MANUAL_FOCUS:
                Camera2Proxy camera2proxy = mCameraCapture.getmCamera2Proxy();
                camera2proxy.changeManualFocusPoint((ManualFocusConfig) obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }
}