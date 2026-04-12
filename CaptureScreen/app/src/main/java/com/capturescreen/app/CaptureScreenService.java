package com.capturescreen.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class CaptureScreenService extends Service {

    private static final String TAG = "CaptureScreenService";
    public static final String ACTION_START = "com.capturescreen.app.ACTION_START";
    public static final String ACTION_STOP = "com.capturescreen.app.ACTION_STOP";
    public static final String ACTION_REQUEST_PERMISSION = "com.capturescreen.app.ACTION_REQUEST_PERMISSION";

    private static final String CHANNEL_ID = "CaptureScreenServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int IMAGE_READER_MAX_IMAGES = 2;

    private static boolean isRunning = false;
    private static int mediaProjectionResultCode = -1;
    private static Intent mediaProjectionData = null;

    private WindowManager windowManager;
    private View overlayView;
    private View captureRegionView;
    private View hintView;

    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private VirtualDisplayWrapper virtualDisplayWrapper;
    private Bitmap capturedBitmap;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInitialResize = true;

    private static class VirtualDisplayWrapper {
        MediaProjection.Callback callback;
        VirtualDisplay virtualDisplay;
        ImageReader imageReader;
        int screenWidth;
        int screenHeight;
    }

    public static void setMediaProjectionData(int resultCode, Intent data) {
        Log.i(TAG, "setMediaProjectionData: resultCode=" + resultCode + ", data=" + data);
        mediaProjectionResultCode = resultCode;
        mediaProjectionData = data;
    }

    public static boolean isRunning() {
        Log.i(TAG, "isRunning: " + isRunning);
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        createNotificationChannel();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            Log.i(TAG, "onStartCommand: action=" + action);
            if (ACTION_START.equals(action)) {
                startForegroundService();
            } else if (ACTION_STOP.equals(action)) {
                stopForegroundService();
            } else if (ACTION_REQUEST_PERMISSION.equals(action)) {
                requestPermission();
            }
        }
        return START_STICKY;
    }

    private void requestPermission() {
        Log.i(TAG, "requestPermission");
        Intent permissionIntent = new Intent(this, MainActivity.class);
        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        permissionIntent.putExtra("request_permission", true);
        startActivity(permissionIntent);
        stopSelf();
    }

    private void startForegroundService() {
        Log.i(TAG, "startForegroundService: isRunning=" + isRunning);
        if (isRunning) return;

        startForeground(NOTIFICATION_ID, createNotification());
        isRunning = true;

        initMediaProjectionAndDisplay();
        addOverlayView();
    }

    private void stopForegroundService() {
        Log.i(TAG, "stopForegroundService: isRunning=" + isRunning);
        if (!isRunning) return;

        removeAllViews();
        releaseVirtualDisplay();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        isRunning = false;
        stopForeground(true);
        stopSelf();
    }

    private void initMediaProjectionAndDisplay() {
        Log.i(TAG, "initMediaProjectionAndDisplay");
        try {
            if (mediaProjectionResultCode == -1 && mediaProjectionData != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(mediaProjectionResultCode, mediaProjectionData);
                Log.i(TAG, "initMediaProjectionAndDisplay: mediaProjection created");
                
                registerMediaProjectionCallback();
                
                WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                Display display = windowManager.getDefaultDisplay();
                Point size = new Point();
                display.getRealSize(size);
                int screenWidth = size.x;
                int screenHeight = size.y;
                
                createVirtualDisplay(screenWidth, screenHeight);
            } else {
                Log.w(TAG, "initMediaProjectionAndDisplay: failed, no projection data");
            }
        } catch (Exception e) {
            Log.e(TAG, "initMediaProjectionAndDisplay: exception=" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerMediaProjectionCallback() {
        Log.i(TAG, "registerMediaProjectionCallback");
        if (mediaProjection != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            Log.w(TAG, "MediaProjection callback: onStop called");
                            releaseVirtualDisplay();
                        }

                        @Override
                        public void onCapturedContentResize(int width, int height) {
                            super.onCapturedContentResize(width, height);
                            Log.i(TAG, "MediaProjection callback: content resized to " + width + "x" + height);
                            recreateVirtualDisplay(width, height);
                        }

                        @Override
                        public void onCapturedContentVisibilityChanged(boolean isVisible) {
                            super.onCapturedContentVisibilityChanged(isVisible);
                            Log.i(TAG, "MediaProjection callback: content visibility changed to " + isVisible);
                        }
                    }, handler);
                    Log.i(TAG, "registerMediaProjectionCallback: success (API 34+)");
                }
            } catch (Exception e) {
                Log.w(TAG, "registerMediaProjectionCallback: failed - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void createVirtualDisplay(int width, int height) {
        Log.i(TAG, "createVirtualDisplay: size=" + width + "x" + height);
        try {
            virtualDisplayWrapper = new VirtualDisplayWrapper();
            virtualDisplayWrapper.screenWidth = width;
            virtualDisplayWrapper.screenHeight = height;
            
            virtualDisplayWrapper.imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, IMAGE_READER_MAX_IMAGES);
            Log.i(TAG, "createVirtualDisplay: imageReader created");
            
            virtualDisplayWrapper.virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width,
                    height,
                    1,
                    Display.FLAG_PRESENTATION,
                    virtualDisplayWrapper.imageReader.getSurface(),
                    null,
                    null
            );
            Log.i(TAG, "createVirtualDisplay: success");
            
            handler.postDelayed(() -> {
                isInitialResize = false;
                Log.i(TAG, "createVirtualDisplay: initial resize flag cleared");
            }, 500);
        } catch (Exception e) {
            Log.e(TAG, "createVirtualDisplay: exception=" + e.getMessage());
            e.printStackTrace();
            releaseVirtualDisplay();
        }
    }

    private void recreateVirtualDisplay(int width, int height) {
        Log.i(TAG, "recreateVirtualDisplay: new size=" + width + "x" + height + ", isInitial=" + isInitialResize);
        
        if (isInitialResize) {
            Log.i(TAG, "recreateVirtualDisplay: ignoring initial resize");
            isInitialResize = false;
            return;
        }
        
        if (virtualDisplayWrapper != null && 
            virtualDisplayWrapper.screenWidth == width && 
            virtualDisplayWrapper.screenHeight == height) {
            Log.i(TAG, "recreateVirtualDisplay: size unchanged, skipping");
            return;
        }
        
        releaseVirtualDisplay();
        createVirtualDisplay(width, height);
    }

    private void releaseVirtualDisplay() {
        Log.i(TAG, "releaseVirtualDisplay");
        try {
            if (virtualDisplayWrapper != null) {
                if (virtualDisplayWrapper.virtualDisplay != null) {
                    virtualDisplayWrapper.virtualDisplay.release();
                    virtualDisplayWrapper.virtualDisplay = null;
                }
                if (virtualDisplayWrapper.imageReader != null) {
                    virtualDisplayWrapper.imageReader.close();
                    virtualDisplayWrapper.imageReader = null;
                }
                virtualDisplayWrapper = null;
            }
            Log.i(TAG, "releaseVirtualDisplay: completed");
        } catch (Exception e) {
            Log.e(TAG, "releaseVirtualDisplay: exception=" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addOverlayView() {
        Log.i(TAG, "addOverlayView");
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null);
        setupGestureDetector(overlayView);

        windowManager.addView(overlayView, params);
        Log.i(TAG, "addOverlayView: overlay view added successfully");
    }

    private void setupGestureDetector(View view) {
        Log.i(TAG, "setupGestureDetector");
        view.setOnTouchListener(new View.OnTouchListener() {
            private boolean isLongPressed = false;
            private float lastTouchX, lastTouchY;
            private Runnable longPressRunnable = null;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        isLongPressed = false;
                        longPressRunnable = () -> {
                            isLongPressed = true;
                            showRippleEffect(lastTouchX, lastTouchY);
                            performScreenshot((int) lastTouchX, (int) lastTouchY);
                        };
                        handler.postDelayed(longPressRunnable, 500);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - lastTouchX) > 10 ||
                                Math.abs(event.getRawY() - lastTouchY) > 10) {
                            if (longPressRunnable != null) {
                                handler.removeCallbacks(longPressRunnable);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (longPressRunnable != null) {
                            handler.removeCallbacks(longPressRunnable);
                        }
                        break;
                }
                return true;
            }
        });
    }

    private void showRippleEffect(float x, float y) {
        Log.i(TAG, "showRippleEffect: x=" + x + ", y=" + y);
        Toast.makeText(this, "长按有效", Toast.LENGTH_SHORT).show();
    }

    private void performScreenshot(int centerX, int centerY) {
        Log.i(TAG, "performScreenshot: centerX=" + centerX + ", centerY=" + centerY);
        try {
            if (virtualDisplayWrapper == null || virtualDisplayWrapper.imageReader == null) {
                Log.e(TAG, "performScreenshot: virtual display or imageReader is null");
                showError(getString(R.string.screenshot_failed));
                return;
            }

            capturedBitmap = ScreenCapturer.captureScreenFromReader(
                    this, 
                    virtualDisplayWrapper.imageReader, 
                    virtualDisplayWrapper.screenWidth, 
                    virtualDisplayWrapper.screenHeight, 
                    centerX, 
                    centerY
            );
            if (capturedBitmap != null) {
                Log.i(TAG, "performScreenshot: success, bitmap size=" + capturedBitmap.getWidth() + "x" + capturedBitmap.getHeight());
                showCaptureRegion(centerX, centerY);
                showHintView();
            } else {
                Log.e(TAG, "performScreenshot: failed to capture screen");
                showError(getString(R.string.screenshot_failed));
            }
        } catch (Exception e) {
            Log.e(TAG, "performScreenshot: exception=" + e.getMessage());
            e.printStackTrace();
            showError(getString(R.string.screenshot_failed));
        }
    }

    private void showCaptureRegion(int centerX, int centerY) {
        Log.i(TAG, "showCaptureRegion: centerX=" + centerX + ", centerY=" + centerY);
        if (captureRegionView != null) {
            windowManager.removeView(captureRegionView);
        }

        captureRegionView = new CaptureRegionView(this, capturedBitmap, centerX, centerY);
        ((CaptureRegionView) captureRegionView).setListener(new CaptureRegionView.CaptureRegionListener() {
            @Override
            public void onShare() {
                Log.i(TAG, "showCaptureRegion: onShare callback");
                showChatBottomSheet();
            }

            @Override
            public void onDismiss() {
                Log.i(TAG, "showCaptureRegion: onDismiss callback");
                removeAllViews();
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.START | Gravity.TOP;

        windowManager.addView(captureRegionView, params);
        Log.i(TAG, "showCaptureRegion: region view added");
    }

    private void showHintView() {
        Log.i(TAG, "showHintView");
        if (hintView != null) {
            windowManager.removeView(hintView);
        }

        hintView = new HintView(this);
        ((HintView) hintView).setListener(new HintView.HintViewListener() {
            @Override
            public void onDrop() {
                Log.i(TAG, "showHintView: onDrop callback");
                if (captureRegionView != null && captureRegionView instanceof CaptureRegionView) {
                    ((CaptureRegionView) captureRegionView).performShare();
                }
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        params.y = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);

        windowManager.addView(hintView, params);
        Log.i(TAG, "showHintView: hint view added");
    }

    private void showChatBottomSheet() {
        Log.i(TAG, "showChatBottomSheet");
        removeAllViews();

        if (capturedBitmap != null) {
            ChatBottomSheetActivity.show(this, capturedBitmap);
        }
    }

    private void removeAllViews() {
        Log.i(TAG, "removeAllViews");
        try {
            if (captureRegionView != null) {
                windowManager.removeView(captureRegionView);
                captureRegionView = null;
            }
            if (hintView != null) {
                windowManager.removeView(hintView);
                hintView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "removeAllViews: error=" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Log.e(TAG, "showError: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Notification createNotification() {
        Log.i(TAG, "createNotification");
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("区域截屏服务")
                .setContentText("长按屏幕触发截屏")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .build();
    }

    private void createNotificationChannel() {
        Log.i(TAG, "createNotificationChannel");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "区域截屏服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.i(TAG, "createNotificationChannel: channel created");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        stopForegroundService();
    }
}
