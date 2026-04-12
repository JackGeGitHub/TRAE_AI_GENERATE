package com.capturescreen.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

public class CaptureRegionView extends View {

    private static final String TAG = "CaptureRegionView";
    
    public interface CaptureRegionListener {
        void onShare();
        void onDismiss();
    }

    private Bitmap bitmap;
    private Paint borderPaint;
    private Paint textPaint;
    private float scaleFactor = 1.0f;
    private float minScale = 100f / 300f;
    private float maxScale = 3.0f;
    private int centerX, centerY;
    private int originalSize = 300;
    private int currentSize = 300;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Handler handler = new Handler(Looper.getMainLooper());

    private boolean showZoomHint = true;
    private boolean isDragging = false;
    private float lastTouchX, lastTouchY;
    private int viewX, viewY;
    private WindowManager.LayoutParams layoutParams;
    private WindowManager windowManager;

    private CaptureRegionListener listener;

    public CaptureRegionView(Context context, Bitmap bitmap, int centerX, int centerY) {
        super(context);
        Log.i(TAG, "CaptureRegionView: constructor, center=(" + centerX + ", " + centerY + ")");
        this.bitmap = bitmap;
        this.centerX = centerX;
        this.centerY = centerY;
        this.viewX = centerX - originalSize / 2;
        this.viewY = centerY - originalSize / 2;

        init();
    }

    private void init() {
        Log.i(TAG, "init");
        windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);

        borderPaint = new Paint();
        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);

        textPaint = new Paint();
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(14 * getResources().getDisplayMetrics().density);
        textPaint.setTextAlign(Paint.Align.CENTER);

        scaleGestureDetector = new ScaleGestureDetector(getContext(), scaleListener);
        gestureDetector = new GestureDetector(getContext(), gestureListener);

        handler.postDelayed(() -> showZoomHint = false, 3000);
        Log.i(TAG, "init: completed");
    }

    public void setListener(CaptureRegionListener listener) {
        Log.i(TAG, "setListener");
        this.listener = listener;
    }

    public void performShare() {
        Log.i(TAG, "performShare");
        if (listener != null) {
            listener.onShare();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = (int) (originalSize * scaleFactor);
        int height = (int) (originalSize * scaleFactor);
        currentSize = width;

        int left = (getWidth() - width) / 2;
        int top = (getHeight() - height) / 2;

        if (bitmap != null && !bitmap.isRecycled()) {
            Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            Rect dst = new Rect(left, top, left + width, top + height);
            canvas.drawBitmap(bitmap, src, dst, null);
        }

        canvas.drawRect(left, top, left + width, top + height, borderPaint);

        if (showZoomHint) {
            canvas.drawText(getContext().getString(R.string.zoom_hint),
                    getWidth() / 2,
                    top + height + textPaint.getTextSize() + 8 * getResources().getDisplayMetrics().density,
                    textPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) (originalSize * maxScale + 100);
        setMeasuredDimension(size, size);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.i(TAG, "onTouchEvent: ACTION_DOWN at (" + event.getRawX() + ", " + event.getRawY() + ")");
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDragging && !scaleGestureDetector.isInProgress()) {
                    float dx = event.getRawX() - lastTouchX;
                    float dy = event.getRawY() - lastTouchY;
                    updateViewPosition((int) dx, (int) dy);
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    checkDropZone(event.getRawX(), event.getRawY());
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Log.i(TAG, "onTouchEvent: ACTION_UP/CANCEL, isDragging=" + isDragging);
                if (isDragging) {
                    handleDrop(event.getRawX(), event.getRawY());
                }
                isDragging = false;
                break;
        }

        return true;
    }

    private ScaleGestureDetector.OnScaleGestureListener scaleListener = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            float newScale = scaleFactor * scale;
            
            if (newScale >= minScale && newScale <= maxScale) {
                scaleFactor = newScale;
                invalidate();
                Log.i(TAG, "onScale: scaleFactor=" + String.format("%.2f", scaleFactor));
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            Log.i(TAG, "onScaleBegin");
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            Log.i(TAG, "onScaleEnd: finalScale=" + String.format("%.2f", scaleFactor));
        }
    };

    private GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.i(TAG, "onDoubleTap: resetting scale");
            animateScaleReset();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.i(TAG, "onLongPress: starting drag");
            isDragging = true;
            startDrag();
        }
    };

    private void animateScaleReset() {
        Log.i(TAG, "animateScaleReset: from=" + String.format("%.2f", scaleFactor));
        ValueAnimator animator = ValueAnimator.ofFloat(scaleFactor, 1.0f);
        animator.setDuration(200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            scaleFactor = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void startDrag() {
        Log.i(TAG, "startDrag");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ClipData data = ClipData.newPlainText("screenshot", "screenshot");
            DragShadowBuilder shadowBuilder = new DragShadowBuilder(this);
            startDragAndDrop(data, shadowBuilder, null, 0);
        }
    }

    private void updateViewPosition(int dx, int dy) {
        if (layoutParams == null) {
            layoutParams = (WindowManager.LayoutParams) getLayoutParams();
        }
        if (layoutParams != null) {
            viewX += dx;
            viewY += dy;
            layoutParams.x = viewX;
            layoutParams.y = viewY;
            windowManager.updateViewLayout(this, layoutParams);
        }
    }

    private void checkDropZone(float x, float y) {
    }

    private void handleDrop(float x, float y) {
        Log.i(TAG, "handleDrop: drop position (" + x + ", " + y + ")");
        Point screenSize = new Point();
        windowManager.getDefaultDisplay().getRealSize(screenSize);
        int hintY = (int) (screenSize.y * 0.8);
        int hintAreaHeight = 100;

        if (y >= hintY - hintAreaHeight / 2 && y <= hintY + hintAreaHeight / 2) {
            Log.i(TAG, "handleDrop: dropped in target zone!");
            if (listener != null) {
                listener.onShare();
            }
        } else {
            Log.i(TAG, "handleDrop: dropped outside target zone");
        }
    }
}
