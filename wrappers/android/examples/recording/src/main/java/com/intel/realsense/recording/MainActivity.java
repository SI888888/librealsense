package com.intel.realsense.recording;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.renderscript.Float3;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.intel.realsense.capture.OverlayView;
import com.intel.realsense.librealsense.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "librs recording example";

    private static final int PERMISSIONS_REQUEST_CAMERA = 0;
    private static final int PERMISSIONS_REQUEST_WRITE = 1;

    private boolean mPermissionsGranted = false;

    private Context mAppContext;
    private TextView mBackGroundText;
    private GLRsSurfaceView mGLSurfaceView;
    private boolean mIsStreaming = false;
    private final Handler mHandler = new Handler();

    private Pipeline mPipeline;
    private Colorizer mColorizer;
    private RsContext mRsContext;

    private FloatingActionButton mStartRecordFab;
    private FloatingActionButton mStopRecordFab;

    private PoseLandmarker poseLandmarker;

    private final int width = 1280;
    private final int height = 720;

    private OverlayView mOverlayView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button return_camtoclip = findViewById(R.id.btn_return_camtoclip);//new added 0113

        mAppContext = getApplicationContext();
        mBackGroundText = findViewById(R.id.connectCameraText);
        mGLSurfaceView = findViewById(R.id.glSurfaceView);
        mOverlayView = findViewById(R.id.overlay);

        mStartRecordFab = findViewById(R.id.startRecordFab);
        mStopRecordFab = findViewById(R.id.stopRecordFab);

        mStartRecordFab.setOnClickListener(view -> toggleRecording());
        mStopRecordFab.setOnClickListener(view -> toggleRecording());
        return_camtoclip.setOnClickListener(v -> {
            finish();
        }); //new added 0113

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CAMERA);
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
    protected void onResume() {
        super.onResume();
        if (mPermissionsGranted) init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRsContext != null) mRsContext.close();
        stop();
    }

    private void init() {
        RsContext.init(mAppContext);
        mRsContext = new RsContext();
        mRsContext.setDevicesChangedCallback(mListener);
        mPipeline = new Pipeline();
        mColorizer = new Colorizer();

        initPoseLandmarker();

        try (DeviceList dl = mRsContext.queryDevices()) {
            if (dl.getDeviceCount() > 0) {
                showConnectLabel(false);
                start(false);
            }
        }
    }

    private void initPoseLandmarker() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_heavy.task")
                    .setDelegate(Delegate.GPU)
                    .build();

            PoseLandmarker.PoseLandmarkerOptions options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onPoseDetected)
                    .build();

            poseLandmarker = PoseLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PoseLandmarker: " + e.getMessage());
        }
    }

    private void onPoseDetected(PoseLandmarkerResult result, MPImage inputImage) {
        if (result == null) return;

        runOnUiThread(() -> mOverlayView.setResults(result, height, width, RunningMode.LIVE_STREAM));
    }

    private void showConnectLabel(final boolean state) {
        runOnUiThread(() -> {
            mBackGroundText.setVisibility(state ? View.VISIBLE : View.GONE);
            mStartRecordFab.setVisibility(!state ? View.VISIBLE : View.GONE);
            mStopRecordFab.setVisibility(View.GONE);
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

    Runnable mStreaming = new Runnable() {
        @Override
        public void run() {
            try {
                try (FrameSet frames = mPipeline.waitForFrames()) {
                    mGLSurfaceView.upload(frames);

                    Frame colorFrame = frames.first(StreamType.COLOR);
                    if (colorFrame != null) {
                        detectPose(colorFrame);
                    }
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
            } catch (Exception e) {
                Log.e(TAG, "Streaming error: " + e.getMessage());
            }
        }
    };

    private void detectPose(Frame colorFrame) {
        try {
            Bitmap bitmap = convertFrameToBitmap(colorFrame);
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            poseLandmarker.detectAsync(mpImage, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Pose detection failed: " + e.getMessage());
        }
    }

    private Bitmap convertFrameToBitmap(Frame frame) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 4);

        byte[] data = new byte[frame.getDataSize()];
        frame.getData(data);

        for (int i = 0; i < data.length; i += 3) {
            buffer.put((byte) 255);
            buffer.put(data[i]);
            buffer.put(data[i + 1]);
            buffer.put(data[i + 2]);
        }

        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private String getFilePath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateAndTime = sdf.format(new Date());
        File file = new File(getExternalFilesDir(null), currentDateAndTime + ".bag");
        return file.getAbsolutePath();
    }

    private void toggleRecording() {
        stop();
        start(mStartRecordFab.getVisibility() == View.VISIBLE);
        runOnUiThread(() -> {
            mStartRecordFab.setVisibility(mStartRecordFab.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
            mStopRecordFab.setVisibility(mStopRecordFab.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });
    }

    private synchronized void start(boolean record) {
        if (mIsStreaming) return;
        try {
            mGLSurfaceView.clear();
            Config config = new Config();
            config.enableStream(StreamType.DEPTH, width, height);
            config.enableStream(StreamType.COLOR, width, height);
            config.enableStream(StreamType.GYRO, StreamFormat.MOTION_XYZ32F);
            config.enableStream(StreamType.ACCEL, StreamFormat.MOTION_XYZ32F);
            if (record) config.enableRecordToFile(getFilePath());
            mPipeline.start(config);
            mIsStreaming = true;
            mHandler.post(mStreaming);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start streaming: " + e.getMessage());
        }
    }

    private synchronized void stop() {
        if (!mIsStreaming) return;
        try {
            mIsStreaming = false;
            mHandler.removeCallbacks(mStreaming);
            mPipeline.stop();
            mGLSurfaceView.clear();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop streaming: " + e.getMessage());
        }
    }
}
//package com.intel.realsense.recording;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import com.google.android.material.floatingactionbutton.FloatingActionButton;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.appcompat.app.AppCompatActivity;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.TextView;
//
//import com.intel.realsense.librealsense.Colorizer;
//import com.intel.realsense.librealsense.Config;
//import com.intel.realsense.librealsense.DeviceList;
//import com.intel.realsense.librealsense.DeviceListener;
//import com.intel.realsense.librealsense.FrameSet;
//import com.intel.realsense.librealsense.GLRsSurfaceView;
//import com.intel.realsense.librealsense.Pipeline;
//import com.intel.realsense.librealsense.PipelineProfile;
//import com.intel.realsense.librealsense.RsContext;
//import com.intel.realsense.librealsense.StreamType;
//
//import java.io.File;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//
//public class MainActivity extends AppCompatActivity {
//    private static final String TAG = "librs recording example";
//
//    private static final int PERMISSIONS_REQUEST_CAMERA = 0;
//    private static final int PERMISSIONS_REQUEST_WRITE = 1;
//
//    private boolean mPermissionsGranted = false;
//
//    private Context mAppContext;
//    private TextView mBackGroundText;
//    private GLRsSurfaceView mGLSurfaceView;
//    private boolean mIsStreaming = false;
//    private final Handler mHandler = new Handler();
//
//    private Pipeline mPipeline;
//
//    private Colorizer mColorizer; // 0122
//    private RsContext mRsContext;
//
//    private FloatingActionButton mStartRecordFab;
//    private FloatingActionButton mStopRecordFab;
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        Button return_camtoclip = findViewById(R.id.btn_return_camtoclip);//new added 0113
//        mAppContext = getApplicationContext();
//        mBackGroundText = findViewById(R.id.connectCameraText);
//        mGLSurfaceView = findViewById(R.id.glSurfaceView);
//
//        mStartRecordFab = findViewById(R.id.startRecordFab);
//        mStopRecordFab = findViewById(R.id.stopRecordFab);
//
//
//        mStartRecordFab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                toggleRecording();
//            }
//        });
//        mStopRecordFab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                toggleRecording();
//            }
//        });
//
//        return_camtoclip.setOnClickListener(v -> {
//            finish();
//        }); //new added 0113
//
//        // Android 9 also requires camera permissions
//        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
//            return;
//        }
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
//            return;
//        }
//
//        mPermissionsGranted = true;
//
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        mGLSurfaceView.close();
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_WRITE);
//            return;
//        }
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
//            return;
//        }
//
//        mPermissionsGranted = true;
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        if(mPermissionsGranted)
//            init();
//        else
//            Log.e(TAG, "missing permissions");
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if(mRsContext != null)
//            mRsContext.close();
//        stop();
//    }
//
//    private String getFilePath(){
//        File folder = new File(getExternalFilesDir(null).getAbsolutePath() + File.separator + "rs_bags");
//        folder.mkdir();
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
//        String currentDateAndTime = sdf.format(new Date());
//        File file = new File(folder, currentDateAndTime + ".bag");
//        return file.getAbsolutePath();
//    }
//
//    void init(){
//        //RsContext.init must be called once in the application lifetime before any interaction with physical RealSense devices.
//        //For multi activities applications use the application context instead of the activity context
//        RsContext.init(mAppContext);
//
//        //Register to notifications regarding RealSense devices attach/detach events via the DeviceListener.
//        mRsContext = new RsContext();
//        mRsContext.setDevicesChangedCallback(mListener);
//
//        mPipeline = new Pipeline();
//
//        try(DeviceList dl = mRsContext.queryDevices()){
//            if(dl.getDeviceCount() > 0) {
//                showConnectLabel(false);
//                start(false);
//            }
//        }
//    }
//
//    private void showConnectLabel(final boolean state){
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mBackGroundText.setVisibility(state ? View.VISIBLE : View.GONE);
//                mStartRecordFab.setVisibility(!state ? View.VISIBLE : View.GONE);
//                mStopRecordFab.setVisibility(View.GONE);
//            }
//        });
//    }
//
//    private void toggleRecording(){
//        stop();
//        start(mStartRecordFab.getVisibility() == View.VISIBLE);
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mStartRecordFab.setVisibility(mStartRecordFab.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
//                mStopRecordFab.setVisibility(mStopRecordFab.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
//            }
//        });
//    }
//
//    private DeviceListener mListener = new DeviceListener() {
//        @Override
//        public void onDeviceAttach() {
//            showConnectLabel(false);
//        }
//
//        @Override
//        public void onDeviceDetach() {
//            showConnectLabel(true);
//            stop();
//        }
//    };
//
//    Runnable mStreaming = new Runnable() {
//        @Override
//        public void run() {
//            try {
//                try(FrameSet frames = mPipeline.waitForFrames()) {
//                    mGLSurfaceView.upload(frames);
//                }
//                mHandler.post(mStreaming);
//            }
//            catch (Exception e) {
//                Log.e(TAG, "streaming, error: " + e.getMessage());
//            }
//        }
//    };
//
//    private synchronized void start(boolean record) {
//        if(mIsStreaming)
//            return;
//        try{
//            mGLSurfaceView.clear();
//            Log.d(TAG, "try start streaming");
//            try(Config cfg = new Config()) {
//                cfg.enableStream(StreamType.DEPTH, 640, 480);
//                cfg.enableStream(StreamType.COLOR, 640, 480);
//                if (record)
//                    cfg.enableRecordToFile(getFilePath());
//                // try statement needed here to release resources allocated by the Pipeline:start() method
//                try(PipelineProfile pp = mPipeline.start(cfg)){}
//            }
//            mIsStreaming = true;
//            mHandler.post(mStreaming);
//            Log.d(TAG, "streaming started successfully");
//        } catch (Exception e) {
//            Log.d(TAG, "failed to start streaming");
//        }
//    }
//
//    private synchronized void stop() {
//        if(!mIsStreaming)
//            return;
//        try {
//            Log.d(TAG, "try stop streaming");
//            mIsStreaming = false;
//            mHandler.removeCallbacks(mStreaming);
//            mPipeline.stop();
//            mGLSurfaceView.clear();
//
//            // 如果是录制模式，返回视频路径
//            String videoPath = getFilePath(); //0114
//            Intent resultIntent = new Intent(); //0114
//            resultIntent.putExtra("videoPath", videoPath); //0114
//            setResult(RESULT_OK, resultIntent); //0114
//
//            Log.d(TAG, "streaming stopped successfully");
//        }  catch (Exception e) {
//            Log.d(TAG, "failed to stop streaming");
//            mPipeline = null;
//        }
//    }
//}
