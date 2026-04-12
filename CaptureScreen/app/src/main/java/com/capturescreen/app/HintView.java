package com.capturescreen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

public class HintView extends View {

    private static final String TAG = "HintView";
    
    public interface HintViewListener {
        void onDrop();
    }

    private Paint backgroundPaint;
    private Paint textPaint;
    private String hintText;
    private float cornerRadius;
    private RectF backgroundRect;
    private boolean isHighlighted = false;
    private HintViewListener listener;

    public HintView(Context context) {
        super(context);
        Log.i(TAG, "HintView: constructor");
        init();
    }

    private void init() {
        Log.i(TAG, "init");
        hintText = getContext().getString(R.string.hint_drag_to_share);
        cornerRadius = 8 * getResources().getDisplayMetrics().density;

        backgroundPaint = new Paint();
        backgroundPaint.setColor(0x99000000);
        backgroundPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(16 * getResources().getDisplayMetrics().density);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        backgroundRect = new RectF();
        Log.i(TAG, "init: completed");
    }

    public void setListener(HintViewListener listener) {
        Log.i(TAG, "setListener");
        this.listener = listener;
    }

    public void setHighlighted(boolean highlighted) {
        Log.i(TAG, "setHighlighted: highlighted=" + highlighted);
        if (isHighlighted != highlighted) {
            isHighlighted = highlighted;
            backgroundPaint.setColor(isHighlighted ? 0xCC000000 : 0x99000000);
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.6);
        
        float textWidth = textPaint.measureText(hintText);
        float textHeight = textPaint.descent() - textPaint.ascent();
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        int width = (int) Math.min(textWidth + padding * 2, maxWidth);
        int height = (int) (textHeight + padding * 2);

        Log.i(TAG, "onMeasure: size=" + width + "x" + height);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        backgroundRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint);

        float textX = getWidth() / 2f;
        float textY = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(hintText, textX, textY, textPaint);
    }
}
