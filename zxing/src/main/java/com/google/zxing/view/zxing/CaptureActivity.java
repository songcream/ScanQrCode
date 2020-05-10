/*
 *  Copyright(c) 2017 lizhaotailang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.view.zxing;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.zxing.R;
import com.google.zxing.Result;
import com.google.zxing.view.zxing.camera.CameraManager;
import com.google.zxing.view.zxing.utils.BeepManager;
import com.google.zxing.view.zxing.utils.CaptureActivityHandler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public abstract class CaptureActivity extends Activity
        implements SurfaceHolder.Callback,View.OnClickListener {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private BeepManager beepManager;

    private SurfaceView surfaceView = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private LinearLayout llLight;
    private ImageView scanLine,imageViewLight;
    private TextView textViewLight;

    private Rect mCropRect = null;

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private boolean isHasSurface = false;

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_scan_prod);

        surfaceView = (SurfaceView) findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
        scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) findViewById(R.id.capture_scan_line);
        imageViewLight=findViewById(R.id.imageView_activityScan_light);
        textViewLight=findViewById(R.id.textView_activityScan_light);
        llLight=findViewById(R.id.ll_activityScan_light);

        if(llLight!=null) llLight.setOnClickListener(this);
        surfaceView.getHolder().addCallback(this);

        beepManager = new BeepManager(this);

        TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.9f);
        animation.setDuration(4500);
        animation.setRepeatCount(-1);
        animation.setRepeatMode(Animation.RESTART);
        scanLine.startAnimation(animation);

        //申请摄像头权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 1002);
        }
    }

    /**
     * DecodeThread.BARCODE_MODE = 0X100
     * DecodeThread.QRCODE_MODE = 0X200;
     * DecodeThread.ALL_MODE = 0X300;
     */
    public abstract int decodeMode();

    @Override
    protected void onResume() {
        super.onResume();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        }
    }

    private void initCamera(){
        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());
        pauseDecode=false;

        handler = null;

        if (isHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceView.getHolder(),decodeMode());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            if(requestCode==1002){
                initCamera();
            }
        }else{
            Toast.makeText(this, "您禁用了权限，功能无法使用", Toast.LENGTH_SHORT).show();
        }
    }

    public void closeCamera(){
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
//        inactivityTimer.onPause();
        pauseDecode=true;
        if(beepManager!=null) beepManager.close();
        if(cameraManager!=null) cameraManager.closeDriver();
        cameraManager=null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
//        inactivityTimer.shutdown();
        super.onDestroy();
        if (!isHasSurface && surfaceView!=null) {
            surfaceView.getHolder().removeCallback(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e("log","surface create");
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder,decodeMode());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult The contents of the barcode.
     * @param bundle    The extras
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        if(!pauseDecode) {
//        inactivityTimer.onActivity();
            beepManager.playBeepSoundAndVibrate();
            handleScanResult(rawResult);
        }
    }

    private boolean pauseDecode=false;
    public void pauseDecode(){
        pauseDecode=true;
    }

    /**
     * 处理结果，返回false表示扫描结果有异常
     * @param result
     * @return
     */
    public abstract void handleScanResult(Result result);

    public String getSnCode(List<String> result){
        for(String snCode:result){
            if(!snCode.startsWith("69") && !snCode.startsWith("A0") && !snCode.startsWith("a0") && !snCode.startsWith("86")){
                return snCode;
            }
        }
        return null;
    }

    public String getSixNineCode(List<String> result){
        for(String sixNineCode:result){
            if(sixNineCode.startsWith("69") && sixNineCode.length()==13){
                return sixNineCode;
            }
        }
        return null;
    }

    /**
     * Init the camera.
     * @param surfaceHolder The surface holder.
     * @param decodeMode 解码模式：
     *                   DecodeThread.BARCODE_MODE = 0X100
     *                   DecodeThread.QRCODE_MODE = 0X200;
     *                   DecodeThread.ALL_MODE = 0X300;
     */
    private void initCamera(SurfaceHolder surfaceHolder,int decodeMode) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if(cameraManager==null){
            return;
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager, decodeMode);
            }

            initCrop();
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        // camera error
        AlertDialog dialog = new AlertDialog.Builder(this).create();
//        dialog.setMessage(getString(R.string.unable_to_open_camera));
//        dialog.setTitle(getString(R.string.error));
        dialog.setMessage("unable to open camera");
        dialog.setTitle("error");
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        pauseDecode=false;
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * Init the interception rectangle area
     */
    private void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        // Obtain the location information of the scanning frame in layout
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1];
//        int cropTop = location[1] - getStatusBarHeight();

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        // Obtain the height and width of layout container.
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        // Compute the coordinate of the top-left vertex x
        // of the final interception rectangle.
        int x = cropLeft * cameraWidth / containerWidth;
        // Compute the coordinate of the top-left vertex y
        // of the final interception rectangle.
        int y = cropTop * cameraHeight / containerHeight;

        // Compute the width of the final interception rectangle.
        int width = cropWidth * cameraWidth / containerWidth;
        // Compute the height of the final interception rectangle.
        int height = cropHeight * cameraHeight / containerHeight;

        // Generate the final interception rectangle.
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private boolean lightOn=false;
    @Override
    public void onClick(View view) {
        if(view.getId()==R.id.ll_activityScan_light){
            lightOn=!lightOn;
            if(lightOn){
                cameraManager.turnLightOn();
                imageViewLight.setImageResource(R.drawable.diantong_open);
                textViewLight.setText("轻触关闭");
            }else{
                cameraManager.turnLightOff();
                imageViewLight.setImageResource(R.drawable.diantong);
                textViewLight.setText("轻触点亮");
            }

        }
    }
}
