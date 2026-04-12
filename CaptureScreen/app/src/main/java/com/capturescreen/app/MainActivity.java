package com.capturescreen.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_MEDIA_PROJECTION = 1002;

    private Button btnToggleService;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        btnToggleService = findViewById(R.id.btn_toggle_service);
        btnToggleService.setOnClickListener(v -> toggleService());

        if (getIntent().getBooleanExtra("request_permission", false)) {
            launchMediaProjectionPermission(this, REQUEST_MEDIA_PROJECTION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        updateServiceButton();
    }

    private void toggleService() {
        Log.i(TAG, "toggleService: isRunning=" + isServiceRunning);
        if (isServiceRunning) {
            stopCaptureService();
        } else {
            checkPermissionsAndStart();
        }
    }

    private void checkPermissionsAndStart() {
        Log.i(TAG, "checkPermissionsAndStart");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
                return;
            }
        }
        requestMediaProjectionPermission();
    }

    private void requestOverlayPermission() {
        Log.i(TAG, "requestOverlayPermission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            Toast.makeText(this, R.string.overlay_permission_desc, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestMediaProjectionPermission() {
        Log.i(TAG, "requestMediaProjectionPermission");
        launchMediaProjectionPermission(this, REQUEST_MEDIA_PROJECTION);
    }

    private void startCaptureService() {
        Log.i(TAG, "startCaptureService");
        Intent intent = new Intent(this, CaptureScreenService.class);
        intent.setAction(CaptureScreenService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        isServiceRunning = true;
        updateServiceButton();
    }

    private void stopCaptureService() {
        Log.i(TAG, "stopCaptureService");
        Intent intent = new Intent(this, CaptureScreenService.class);
        intent.setAction(CaptureScreenService.ACTION_STOP);
        startService(intent);
        isServiceRunning = false;
        updateServiceButton();
    }

    private void updateServiceButton() {
        Log.i(TAG, "updateServiceButton");
        isServiceRunning = CaptureScreenService.isRunning();
        if (isServiceRunning) {
            btnToggleService.setText(R.string.btn_stop_service);
        } else {
            btnToggleService.setText(R.string.btn_start_service);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode 
                + " (RESULT_OK=" + RESULT_OK + ", RESULT_CANCELED=" + RESULT_CANCELED + ")"
                + ", data=" + (data != null ? "NOT_NULL" : "NULL"));

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    requestMediaProjectionPermission();
                } else {
                    Log.w(TAG, "onActivityResult: overlay permission denied");
                }
            }
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Log.i(TAG, "onActivityResult: media projection granted, starting service");
                CaptureScreenService.setMediaProjectionData(resultCode, data);
                startCaptureService();
            } else {
                Log.w(TAG, "onActivityResult: media projection denied or cancelled, resultCode=" + resultCode);
                Toast.makeText(this, R.string.media_projection_permission_desc, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void launchMediaProjectionPermission(AppCompatActivity activity, int requestCode) {
        Log.i(TAG, "launchMediaProjectionPermission: requestCode=" + requestCode);
        android.media.projection.MediaProjectionManager manager =
                (android.media.projection.MediaProjectionManager) activity.getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            Intent intent = manager.createScreenCaptureIntent();
            activity.startActivityForResult(intent, requestCode);
        }
    }
}
