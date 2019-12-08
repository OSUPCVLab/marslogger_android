package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import android.os.Bundle;
import android.widget.Toast;

import timber.log.Timber;

public class MainActivity extends Activity implements Eula.OnEulaAgreedTo {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        Eula.show(this);
    }

    @Override
    public void onEulaAgreedTo() {
        if (PermissionHelper.hasCameraPermission(this)) {
            Intent intent = new Intent(this, CameraCaptureActivity.class);
            startActivity(intent);
        } else {
            PermissionHelper.requestCameraPermission(this, false);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!PermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this,
                    "Camera permission is needed to run this app", Toast.LENGTH_LONG).show();
            PermissionHelper.launchPermissionSettings(this);
            finish();
        } else {
            Intent intent = new Intent(this, CameraCaptureActivity.class);
            startActivity(intent);
        }
    }
}
