package com.capturescreen.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCapturer {

    private static final String TAG = "ScreenCapturer";
    private static final int DEFAULT_WIDTH = 1080;
    private static final int DEFAULT_HEIGHT = 1920;
    private static final int IMAGE_READER_MAX_IMAGES = 2;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;

    public static Bitmap captureScreen(Context context, MediaProjection mediaProjection, int centerX, int centerY) {
        Log.i(TAG, "captureScreen (deprecated): centerX=" + centerX + ", centerY=" + centerY);
        return null;
    }

    public static Bitmap captureScreenFromReader(Context context, ImageReader imageReader, int screenWidth, int screenHeight, int centerX, int centerY) {
        Log.i(TAG, "captureScreenFromReader: centerX=" + centerX + ", centerY=" + centerY + ", screen=" + screenWidth + "x" + screenHeight);
        
        Bitmap resultBitmap = null;
        
        for (int retry = 0; retry < MAX_RETRY_COUNT; retry++) {
            Log.i(TAG, "captureScreenFromReader: attempt " + (retry + 1) + "/" + MAX_RETRY_COUNT);
            
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    Log.i(TAG, "captureScreenFromReader: image acquired");
                    resultBitmap = imageToBitmap(image);
                    Log.i(TAG, "captureScreenFromReader: bitmap converted");
                    break;
                } else {
                    Log.w(TAG, "captureScreenFromReader: no image available, retrying...");
                    if (retry < MAX_RETRY_COUNT - 1) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "captureScreenFromReader: interrupted during retry");
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "captureScreenFromReader: error acquiring image - " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }

        if (resultBitmap != null) {
            int cropSize = 300;
            int cropX = Math.max(0, centerX - cropSize / 2);
            int cropY = Math.max(0, centerY - cropSize / 2);
            
            if (cropX + cropSize > resultBitmap.getWidth()) {
                cropX = resultBitmap.getWidth() - cropSize;
            }
            if (cropY + cropSize > resultBitmap.getHeight()) {
                cropY = resultBitmap.getHeight() - cropSize;
            }
            if (cropX < 0) cropX = 0;
            if (cropY < 0) cropY = 0;
            
            Log.i(TAG, "captureScreenFromReader: cropping at (" + cropX + ", " + cropY + "), size=" + cropSize);

            try {
                Bitmap croppedBitmap = Bitmap.createBitmap(resultBitmap, cropX, cropY, cropSize, cropSize);
                Log.i(TAG, "captureScreenFromReader: success, cropped bitmap size=" + croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());
                return croppedBitmap;
            } catch (Exception e) {
                Log.e(TAG, "captureScreenFromReader: error cropping bitmap - " + e.getMessage());
                e.printStackTrace();
                return resultBitmap;
            }
        }

        Log.e(TAG, "captureScreenFromReader: failed to capture screen after " + MAX_RETRY_COUNT + " attempts");
        return null;
    }

    private static Bitmap imageToBitmap(Image image) {
        Log.i(TAG, "imageToBitmap: converting image to bitmap");
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);

        Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        Log.i(TAG, "imageToBitmap: conversion complete, size=" + result.getWidth() + "x" + result.getHeight());
        return result;
    }
}
