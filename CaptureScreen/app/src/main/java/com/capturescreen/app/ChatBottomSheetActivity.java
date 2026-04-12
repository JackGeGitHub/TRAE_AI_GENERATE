package com.capturescreen.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatBottomSheetActivity extends AppCompatActivity {

    private static final String TAG = "ChatBottomSheet";
    private static final String EXTRA_BITMAP = "extra_bitmap";
    private static Bitmap sharedBitmap = null;

    private ImageView ivCapturedImage;
    private Button btnClose;
    private Bitmap currentBitmap;

    public static void show(Context context, Bitmap bitmap) {
        Log.i(TAG, "show: starting chat bottom sheet");
        sharedBitmap = bitmap;
        Intent intent = new Intent(context, ChatBottomSheetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_chat_bottom_sheet);

        initViews();
        setWindowSize();
        displayImage();
    }

    private void initViews() {
        Log.i(TAG, "initViews");
        ivCapturedImage = findViewById(R.id.iv_captured_image);
        btnClose = findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> closeAndSave());
    }

    private void setWindowSize() {
        Log.i(TAG, "setWindowSize");
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.4);
        getWindow().setAttributes(params);
    }

    private void displayImage() {
        Log.i(TAG, "displayImage");
        currentBitmap = sharedBitmap;
        if (currentBitmap != null) {
            Log.i(TAG, "displayImage: bitmap size=" + currentBitmap.getWidth() + "x" + currentBitmap.getHeight());
            ivCapturedImage.setImageBitmap(currentBitmap);
        } else {
            Log.w(TAG, "displayImage: bitmap is null");
        }
    }

    private void closeAndSave() {
        Log.i(TAG, "closeAndSave");
        if (currentBitmap != null) {
            saveImageToGallery(currentBitmap);
        }
        finish();
    }

    private void saveImageToGallery(Bitmap bitmap) {
        Log.i(TAG, "saveImageToGallery: starting to save image");
        try {
            String fileName = "Screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
            Log.i(TAG, "saveImageToGallery: fileName=" + fileName);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.i(TAG, "saveImageToGallery: using Android Q+ MediaStore API");
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                
                Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    Log.i(TAG, "saveImageToGallery: imageUri created=" + imageUri.toString());
                    OutputStream outputStream = getContentResolver().openOutputStream(imageUri);
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                        outputStream.close();
                        Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "saveImageToGallery: image saved successfully via MediaStore");
                    } else {
                        Log.e(TAG, "saveImageToGallery: failed to open output stream");
                    }
                } else {
                    Log.e(TAG, "saveImageToGallery: failed to insert into MediaStore");
                }
            } else {
                Log.i(TAG, "saveImageToGallery: using legacy file API");
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs();
                }
                
                File imageFile = new File(picturesDir, fileName);
                FileOutputStream fos = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
                fos.close();
                
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(imageFile));
                sendBroadcast(mediaScanIntent);
                
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "saveImageToGallery: image saved successfully to " + imageFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "saveImageToGallery: error - " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: cleaning up resources");
        if (sharedBitmap != null && !sharedBitmap.isRecycled()) {
            sharedBitmap.recycle();
        }
        sharedBitmap = null;
    }
}
