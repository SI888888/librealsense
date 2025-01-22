package com.intel.realsense.recording;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClipListActivity extends AppCompatActivity {

    private List<String> clipList = new ArrayList<>();
    private ArrayAdapter<String> clipAdapter;
    private String currentJobName;
    private String currentTaskName;
    private static final String PREF_NAME = "JobPrefs";
    private static final int REQUEST_CODE_RECORD = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clip_list);

        // 绑定视图组件
        ListView clipListView = findViewById(R.id.clip_list_view);
        Button recordClipFab = findViewById(R.id.record_clip_fab);
        Button return_cliptotask = findViewById(R.id.btn_return_cliptotask);

        // 获取传递的 Job 和 Task 名称
        currentJobName = getIntent().getStringExtra("jobName");
        currentTaskName = getIntent().getStringExtra("taskName");

        // 加载已保存的 Clips
        loadClips();

        // 初始化 ListView 的适配器
        clipAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, clipList);
        clipListView.setAdapter(clipAdapter);

        // 点击录制按钮，跳转到 MainActivity 进行视频录制
        recordClipFab.setOnClickListener(v -> {
            Intent intent = new Intent(ClipListActivity.this, MainActivity.class);
            intent.putExtra("jobName", currentJobName);
            intent.putExtra("taskName", currentTaskName);
            startActivityForResult(intent, REQUEST_CODE_RECORD);
        });

        // 返回按钮
        return_cliptotask.setOnClickListener(v -> {
            finish();
        });

        // 长按 Clip 删除
        clipListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String clipToDelete = clipList.get(position);
            showDeleteClipDialog(clipToDelete, position);
            return true;
        });
    }

    /**
     * 加载当前 Task 的视频列表
     */
    private void loadClips() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String key = generateKey(currentJobName, currentTaskName);
        Set<String> clips = prefs.getStringSet(key, new HashSet<>());
        clipList.clear();
        clipList.addAll(clips);
    }

    /**
     * 保存当前 Task 的视频列表
     */
    private void saveClips() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String key = generateKey(currentJobName, currentTaskName);
        prefs.edit().putStringSet(key, new HashSet<>(clipList)).apply();
    }

    /**
     * 生成唯一的 SharedPreferences 键值
     */
    private String generateKey(String jobName, String taskName) {
        return jobName + "_" + taskName + "_clips";
    }

    /**
     * 显示删除 Clip 的对话框
     */
    private void showDeleteClipDialog(String clipName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Clip")
                .setMessage("Are you sure you want to delete this clip?")
                .setPositiveButton("Delete", (dialog, which) -> deleteClip(clipName, position))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * 删除 Clip 的逻辑
     */
    private void deleteClip(String clipName, int position) {
        clipList.remove(position);
        saveClips(); // 更新保存
        runOnUiThread(() -> {
            clipAdapter.notifyDataSetChanged(); // 刷新列表
            Toast.makeText(this, "Clip deleted: " + clipName, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 处理录制视频的返回结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RECORD && resultCode == RESULT_OK && data != null) {
            String clipPath = data.getStringExtra("videoPath");
            if (clipPath != null) {
                clipList.add(clipPath);
                saveClips(); // 保存视频路径
                clipAdapter.notifyDataSetChanged(); // 更新列表
                Toast.makeText(this, "Clip added: " + clipPath, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No clip path received!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

//package com.intel.realsense.recording;
//
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.os.Bundle;
//import android.widget.ArrayAdapter;
//import android.widget.Button;
//import android.widget.ListView;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.google.android.material.floatingactionbutton.FloatingActionButton;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
//public class ClipListActivity extends AppCompatActivity {
//
//    private List<String> clipList = new ArrayList<>();
//    private ArrayAdapter<String> clipAdapter;
//    private String currentJobName;
//    private String currentTaskName;
//    private static final String PREF_NAME = "JobPrefs";
//    private static final int REQUEST_CODE_RECORD = 1;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_clip_list);
//
//        // 绑定视图组件
//        ListView clipListView = findViewById(R.id.clip_list_view);
//        Button recordClipFab = findViewById(R.id.record_clip_fab);
//        Button return_cliptotask = findViewById(R.id.btn_return_cliptotask);
//
//        // 获取传递的 Job 和 Task 名称
//        currentJobName = getIntent().getStringExtra("jobName");
//        currentTaskName = getIntent().getStringExtra("taskName");
//
//        // 加载已保存的 Clips
//        loadClips();
//
//        // 初始化 ListView 的适配器
//        clipAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, clipList);
//        clipListView.setAdapter(clipAdapter);
//
//        // 点击录制按钮，跳转到 MainActivity 进行视频录制
//        recordClipFab.setOnClickListener(v -> {
//            Intent intent = new Intent(ClipListActivity.this, MainActivity.class);
//            intent.putExtra("jobName", currentJobName);
//            intent.putExtra("taskName", currentTaskName);
//            startActivityForResult(intent, REQUEST_CODE_RECORD);
//        });
//
//        return_cliptotask.setOnClickListener(v -> {
//            finish();
//        });
//
//        // 长按列表项删除某个 Clip
//        clipListView.setOnItemLongClickListener((parent, view, position, id) -> {
//            String clipToDelete = clipList.get(position);
//            new AlertDialog.Builder(this)
//                    .setTitle("Delete Clip")
//                    .setMessage("Are you sure you want to delete this clip?")
//                    .setPositiveButton("Yes", (dialog, which) -> {
//                        clipList.remove(clipToDelete);
//                        saveClips(); // 更新保存
//                        clipAdapter.notifyDataSetChanged(); // 刷新列表
//                        Toast.makeText(this, "Clip deleted", Toast.LENGTH_SHORT).show();
//                    })
//                    .setNegativeButton("Cancel", null)
//                    .show();
//            return true;
//        });
//    }
//
//    /**
//     * 加载当前 Task 的视频列表
//     */
//    private void loadClips() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        String key = generateKey(currentJobName, currentTaskName);
//        Set<String> clips = prefs.getStringSet(key, new HashSet<>());
//        clipList.clear();
//        clipList.addAll(clips);
//    }
//
//    /**
//     * 保存当前 Task 的视频列表
//     */
//    private void saveClips() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        String key = generateKey(currentJobName, currentTaskName);
//        prefs.edit().putStringSet(key, new HashSet<>(clipList)).apply();
//    }
//
//    /**
//     * 生成唯一的 SharedPreferences 键值
//     */
//    private String generateKey(String jobName, String taskName) {
//        return jobName + "_" + taskName + "_clips";
//    }
//
//    /**
//     * 处理录制视频的返回结果
//     */
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == REQUEST_CODE_RECORD && resultCode == RESULT_OK && data != null) {
//            String clipPath = data.getStringExtra("videoPath");
//            if (clipPath != null) {
//                clipList.add(clipPath);
//                saveClips(); // 保存视频路径
//                clipAdapter.notifyDataSetChanged(); // 更新列表
//                Toast.makeText(this, "Clip added: " + clipPath, Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(this, "No clip path received!", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }
//
//}


