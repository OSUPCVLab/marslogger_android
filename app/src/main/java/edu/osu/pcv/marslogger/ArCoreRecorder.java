package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.TrackingState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;

import timber.log.Timber;

/** Records ARCore observations without owning the Camera2 image or IMU writers. */
public final class ArCoreRecorder {
    public static final String PREFERENCE_KEY = "prefArCoreRecording";

    private final Activity activity;
    private final Object lock = new Object();
    private Session session;
    private SharedCamera sharedCamera;
    private BufferedWriter writer;
    private String pendingOutputDir;
    private boolean active;
    private int textureId;
    private long lastTimestampNs;
    private int worldOriginId;

    public ArCoreRecorder(Activity activity) {
        this.activity = activity;
    }

    public static boolean isEnabled(Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(PREFERENCE_KEY, false);
    }

    /** Called before Camera2 opens the device. A failure leaves ordinary Camera2 usable. */
    public boolean prepare(String cameraId) {
        if (!isEnabled(activity) || !(activity instanceof CameraCaptureActivity
                || activity instanceof LidarCaptureActivity)) {
            return false;
        }
        synchronized (lock) {
            if (session != null) {
                if (pendingOutputDir != null && writer == null) {
                    openWriter(pendingOutputDir);
                }
                return true;
            }
            Session candidate = null;
            try {
                ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(activity);
                if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                    report("ARCore is unavailable or not installed on this device");
                    return false;
                }
                if (ArCoreApk.getInstance().requestInstall(activity, false)
                        != ArCoreApk.InstallStatus.INSTALLED) {
                    report("ARCore installation is incomplete");
                    return false;
                }
                candidate = new Session(activity, EnumSet.of(Session.Feature.SHARED_CAMERA));
                String arCameraId = candidate.getCameraConfig().getCameraId();
                if (!cameraId.equals(arCameraId)) {
                    closeAsync(candidate);
                    report("ARCore uses a different camera. Select camera "
                            + arCameraId + " for ARCore recording");
                    return false;
                }
                Config config = candidate.getConfig();
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                candidate.configure(config);
                session = candidate;
                sharedCamera = candidate.getSharedCamera();
                if (textureId != 0) {
                    session.setCameraTextureName(textureId);
                }
                worldOriginId++;
                lastTimestampNs = 0;
                if (pendingOutputDir != null && writer == null) {
                    openWriter(pendingOutputDir);
                }
                return true;
            } catch (Exception | LinkageError error) {
                if (candidate != null) {
                    closeAsync(candidate);
                }
                session = null;
                sharedCamera = null;
                Timber.w(error, "ARCore initialization failed");
                report("ARCore could not start; Camera2 recording will continue");
                return false;
            }
        }
    }

    public SharedCamera getSharedCamera() {
        synchronized (lock) {
            return sharedCamera;
        }
    }

    public boolean isActive() {
        synchronized (lock) {
            return active;
        }
    }

    public void setAppSurface(String cameraId, Surface surface) {
        synchronized (lock) {
            if (sharedCamera != null) {
                sharedCamera.setAppSurfaces(cameraId, Collections.singletonList(surface));
            }
        }
    }

    public void resume(android.hardware.camera2.CameraCaptureSession.CaptureCallback callback,
                       Handler handler) {
        synchronized (lock) {
            if (session == null || active) {
                return;
            }
            try {
                session.resume();
                sharedCamera.setCaptureCallback(callback, handler);
                active = true;
            } catch (Exception error) {
                Timber.w(error, "ARCore resume failed");
                report("ARCore tracking failed to start; Camera2 recording will continue");
            }
        }
    }

    public void startRecording(String outputDir) {
        synchronized (lock) {
            stopRecording();
            pendingOutputDir = outputDir;
            if (session == null) {
                return;
            }
            openWriter(outputDir);
        }
    }

    private void openWriter(String outputDir) {
        try {
            writer = new BufferedWriter(new FileWriter(new File(outputDir, "arcore_poses.csv")));
            writer.write("timestamp_ns,world_origin_id,tracking_state,tx_m,ty_m,tz_m,"
                    + "qx,qy,qz,qw,fx_px,fy_px,cx_px,cy_px,image_width,image_height\n");
            lastTimestampNs = 0;
        } catch (IOException error) {
            Timber.e(error, "Could not create ARCore recording");
            report("Could not write ARCore data to the session directory");
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException closeError) {
                    Timber.w(closeError, "Could not close incomplete ARCore recording");
                }
            }
            writer = null;
        }
    }

    public void stopRecording() {
        synchronized (lock) {
            pendingOutputDir = null;
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException error) {
                    Timber.e(error, "Could not close ARCore recording");
                }
                writer = null;
            }
        }
    }

    /** Creates ARCore's own OES texture in the preview's GL context. */
    public void onGlSurfaceCreated() {
        synchronized (lock) {
            textureId = 0;
            if (isEnabled(activity) && (activity instanceof CameraCaptureActivity
                    || activity instanceof LidarCaptureActivity)) {
                createTextureIfNeeded();
            }
        }
    }

    private void createTextureIfNeeded() {
        if (textureId != 0) {
            return;
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        if (session != null) {
            session.setCameraTextureName(textureId);
        }
    }

    /** Invoked on the existing GL renderer thread, once per Camera2 preview frame. */
    public void onDrawFrame(int width, int height) {
        synchronized (lock) {
            if (!active || session == null) {
                return;
            }
            try {
                createTextureIfNeeded();
                session.setDisplayGeometry(activity.getWindowManager().getDefaultDisplay()
                        .getRotation(), width, height);
                Frame frame = session.update();
                // This is the Camera2 SENSOR_TIMESTAMP, unlike Frame.getTimestamp() whose
                // clock domain is not specified by ARCore.
                long timestampNs = frame.getAndroidCameraTimestamp();
                if (writer == null || timestampNs <= 0 || timestampNs == lastTimestampNs) {
                    return;
                }
                lastTimestampNs = timestampNs;
                com.google.ar.core.Camera camera = frame.getCamera();
                TrackingState state = camera.getTrackingState();
                Pose pose = camera.getPose();
                float[] translation = pose.getTranslation();
                float[] rotation = pose.getRotationQuaternion();
                CameraIntrinsics intrinsics = camera.getImageIntrinsics();
                float[] focal = intrinsics.getFocalLength();
                float[] principal = intrinsics.getPrincipalPoint();
                int[] dimensions = intrinsics.getImageDimensions();
                writer.write(String.format(Locale.US,
                        "%d,%d,%s,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%d,%d%n",
                        timestampNs, worldOriginId, state.name(),
                        translation[0], translation[1], translation[2],
                        rotation[0], rotation[1], rotation[2], rotation[3],
                        focal[0], focal[1], principal[0], principal[1],
                        dimensions[0], dimensions[1]));
            } catch (IOException error) {
                Timber.e(error, "ARCore recording write failed");
                stopRecording();
                report("ARCore data could not be written to disk");
            } catch (Exception error) {
                Timber.w(error, "ARCore frame update failed");
                report("ARCore tracking stopped; Camera2 recording will continue");
                active = false;
            }
        }
    }

    public void releaseSession() {
        Session oldSession;
        synchronized (lock) {
            oldSession = session;
            if (oldSession != null) {
                try {
                    oldSession.pause();
                } catch (Exception error) {
                    Timber.w(error, "ARCore pause failed");
                }
            }
            session = null;
            sharedCamera = null;
            active = false;
        }
        if (oldSession != null) {
            closeAsync(oldSession);
        }
    }

    public void reportFailure(String message) {
        report(message);
    }

    private void closeAsync(Session oldSession) {
        new Thread(() -> {
            try {
                oldSession.close();
            } catch (RuntimeException error) {
                Timber.w(error, "ARCore close failed");
            }
        }, "ArCoreClose").start();
    }

    private void report(String message) {
        Timber.w(message);
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
