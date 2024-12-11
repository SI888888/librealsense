package com.intel.realsense.capture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.renderscript.Float3;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
//import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.intel.realsense.librealsense.Colorizer;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.DeviceList;
import com.intel.realsense.librealsense.DeviceListener;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.Frame;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;
import com.intel.realsense.librealsense.MotionFrame;
import com.intel.realsense.librealsense.Pipeline;
import com.intel.realsense.librealsense.PipelineProfile;
import com.intel.realsense.librealsense.RsContext;
import com.intel.realsense.librealsense.StreamFormat;
import com.intel.realsense.librealsense.StreamType;
import com.intel.realsense.librealsense.VideoFrame;

import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "librs capture example";
    private static final int PERMISSIONS_REQUEST_CAMERA = 0;

    private boolean mPermissionsGranted = false;

    private Context mAppContext;
    private TextView mBackGroundText;
    private GLRsSurfaceView mGLSurfaceView;
    private boolean mIsStreaming = false;
    private final Handler mHandler = new Handler();

    private Pipeline mPipeline;
    private Colorizer mColorizer;
    private RsContext mRsContext;
    private PoseLandmarker poseLandmarker; // new added 1210

    private void initializePoseLandmarker() { // new added 1210
        String modelPath = "pose_landmarker_lite.task";
        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build();

        PoseLandmarker.PoseLandmarkerOptions options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE) // 或者 RunningMode.LIVE_STREAM 视具体需求
                .build();

        poseLandmarker = PoseLandmarker.createFromOptions(this, options);
    } // new added 1210

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializePoseLandmarker();// new added 1210
        mAppContext = getApplicationContext();
        mBackGroundText = findViewById(R.id.connectCameraText);
        mGLSurfaceView = findViewById(R.id.glSurfaceView);
        mGLSurfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Android 9 also requires camera permissions
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }

        mPermissionsGranted = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLSurfaceView.close();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }
        mPermissionsGranted = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mPermissionsGranted)
            init();
        else
            Log.e(TAG, "missing permissions");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mRsContext != null)
            mRsContext.close();
        stop();
        mColorizer.close();
        mPipeline.close();
    }

    private void init(){
        //RsContext.init must be called once in the application lifetime before any interaction with physical RealSense devices.
        //For multi activities applications use the application context instead of the activity context
        RsContext.init(mAppContext);

        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
        mRsContext = new RsContext();
        mRsContext.setDevicesChangedCallback(mListener);

        mPipeline = new Pipeline();
        mColorizer = new Colorizer();

        try(DeviceList dl = mRsContext.queryDevices()){
            if(dl.getDeviceCount() > 0) {
                showConnectLabel(false);
                start();
            }
        }
    }

    private void showConnectLabel(final boolean state){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBackGroundText.setVisibility(state ? View.VISIBLE : View.GONE);
            }
        });
    }

    private DeviceListener mListener = new DeviceListener() {
        @Override
        public void onDeviceAttach() {
            showConnectLabel(false);
        }

        @Override
        public void onDeviceDetach() {
            showConnectLabel(true);
            stop();
        }
    };

    private long lastDialogTime = 0; // 用于记录上次显示对话框的时间 1210

    private void showCustomDialog(String title, String message) {
        long currentTime = System.currentTimeMillis(); // 获取当前时间（毫秒）

        // 检查当前时间与上次显示对话框的时间差是否超过 1 分钟（60,000 毫秒）
        if (currentTime - lastDialogTime >= 1000) { // 60000 毫秒 = 1 分钟
            lastDialogTime = currentTime; // 更新上次显示对话框的时间

            // 显示对话框
            runOnUiThread(() -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(title)  // 设置弹窗标题
                        .setMessage(message)  // 设置弹窗内容
                        .setPositiveButton("Learn More", (dialog, which) -> {
                            // 用户点击“确定”按钮的逻辑
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // 用户点击“取消”按钮的逻辑
                            dialog.dismiss();
                        })
                        .show();
            });
        } else {
            Log.d("CustomDialog", "Dialog skipped due to interval constraint");
        }
    } //1210

    private Bitmap drawPoseOnBitmap(Bitmap bitmap, PoseLandmarkerResult result) {//new added 1210
        Bitmap outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(outputBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5);

        // 遍历所有检测到的姿势
        for (List<NormalizedLandmark> poseLandmarks : result.landmarks()) {
            for (NormalizedLandmark landmark : poseLandmarks) {
                float x = landmark.x() * bitmap.getWidth();
                float y = landmark.y() * bitmap.getHeight();
                canvas.drawCircle(x, y, 8, paint); // 在每个地标点绘制一个圆点
            }
        }

        return outputBitmap;
    }//new added 1210

    private Bitmap convertFrameToBitmap(Frame colorFrame) {//new added 1210
        if (!colorFrame.is(Extension.VIDEO_FRAME)) {
            throw new IllegalArgumentException("Provided frame is not a video frame");
        }

        // 将 Frame 转换为 VideoFrame
        VideoFrame videoFrame = colorFrame.as(Extension.VIDEO_FRAME);

        // 获取宽度和高度
        int width = videoFrame.getWidth();
        int height = videoFrame.getHeight();

        // 获取帧数据（byte[]）
        byte[] data = new byte[width * height * 3]; // 假设是 RGB 格式
        videoFrame.getData(data);

        // 创建 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // 将 byte[] 数据转换为 IntArray
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int r = data[i * 3] & 0xFF;
            int g = data[i * 3 + 1] & 0xFF;
            int b = data[i * 3 + 2] & 0xFF;
            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        // 将像素填充到 Bitmap
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return bitmap;
    }//new added 1210

    Runnable mStreaming = new Runnable() {
        @Override
        public void run() {
            try {
                try(FrameSet frames = mPipeline.waitForFrames()) {
                    try(FrameSet processed = frames.applyFilter(mColorizer)) {
                        mGLSurfaceView.upload(processed);
                    }
                    Frame colorFrame = frames.first(StreamType.COLOR);
                    if (colorFrame != null) { //new added 1210
                        Bitmap rgbBitmap = convertFrameToBitmap(colorFrame);

                        // 使用 MediaPipe Pose Landmarker 处理
                        MPImage mpImage = new BitmapImageBuilder(rgbBitmap).build();
                        PoseLandmarkerResult result = poseLandmarker.detect(mpImage);

                        // 将骨架粘贴到原始图像
                        Bitmap outputBitmap = drawPoseOnBitmap(rgbBitmap, result);

                        // 更新 UI
                        runOnUiThread(() -> {
                            ImageView imageView = findViewById(R.id.imageView); // 替换 GLRsSurfaceView 为 ImageView
                            imageView.setImageBitmap(outputBitmap); // 设置处理后的 Bitmap
                        });
                    }//new added 1210
                    try (Frame gyroFrame = frames.first(StreamType.GYRO)) {
                        if (gyroFrame != null && gyroFrame.is(Extension.MOTION_FRAME)) {
                            MotionFrame motion = gyroFrame.as(Extension.MOTION_FRAME);
                            Float3 gyroData = motion.getMotionData(); // 提取三轴角速度
                            double angularVelocity = Math.sqrt(
                                    Math.pow(gyroData.x, 2) +
                                            Math.pow(gyroData.y, 2) +
                                            Math.pow(gyroData.z, 2)
                            );
                            double accelX = gyroData.x;
                            double accelY = gyroData.y;
                            double accelZ = gyroData.z;
                            double tiltAngle = Math.toDegrees(Math.acos(accelZ / Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)));

                            if (angularVelocity > 5.0f) {
                                runOnUiThread(() -> showCustomDialog("ATTENTION",
                                        "The rotation of camera is too fast!\nAngular Velocity: " + angularVelocity));
                            }
                        }
                    }

                    try (Frame accelFrame = frames.first(StreamType.ACCEL)) {
                        if (accelFrame != null && accelFrame.is(Extension.MOTION_FRAME)) {
                            MotionFrame motion = accelFrame.as(Extension.MOTION_FRAME);
                            Float3 accelData = motion.getMotionData(); // 提取三轴加速度
                            double accelerationMagnitude = Math.sqrt(
                                    Math.pow(accelData.x, 2) +
                                            Math.pow(accelData.y, 2) +
                                            Math.pow(accelData.z, 2)
                            );

                            // 如果线速度超过阈值，触发警告
                            if (accelerationMagnitude > 12.0f) {
                                runOnUiThread(() -> showCustomDialog("ATTENTION",
                                        "The movement of camera is too fast!\nAcceleration: " + accelerationMagnitude));
                            }
                        }
                    }
                }
                mHandler.post(mStreaming);
            }
            catch (Exception e) {
                Log.e(TAG, "streaming, error: " + e.getMessage());
            }
        }
    };

    private void configAndStart() throws Exception {
        try(Config config  = new Config())
        {
            config.enableStream(StreamType.DEPTH, 640, 480);
            config.enableStream(StreamType.COLOR, 640, 480);
            config.enableStream(StreamType.GYRO, StreamFormat.MOTION_XYZ32F);
            config.enableStream(StreamType.ACCEL, StreamFormat.MOTION_XYZ32F);
            try(PipelineProfile pp = mPipeline.start(config)){}
        }
    }

    private synchronized void start() {
        if(mIsStreaming)
            return;
        try{
            Log.d(TAG, "try start streaming");
            mGLSurfaceView.clear();
            configAndStart();
            mIsStreaming = true;
            mHandler.post(mStreaming);
            Log.d(TAG, "streaming started successfully");
        } catch (Exception e) {
            Log.d(TAG, "failed to start streaming");
        }
    }

    private synchronized void stop() {
        if(!mIsStreaming)
            return;
        try {
            Log.d(TAG, "try stop streaming");
            mIsStreaming = false;
            mHandler.removeCallbacks(mStreaming);
            mPipeline.stop();
            mGLSurfaceView.clear();
            Log.d(TAG, "streaming stopped successfully");
        } catch (Exception e) {
            Log.d(TAG, "failed to stop streaming");
        }
    }
}
