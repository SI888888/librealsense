package com.intel.realsense.recording;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JobListActivity extends AppCompatActivity {

    private List<String> jobList = new ArrayList<>();
    private ArrayAdapter<String> jobAdapter;
    private static final String PREF_NAME = "JobPrefs";
    private static final String JOBS_KEY = "jobs";
    private static final int REQUEST_CODE_RECORD = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_list);

        ListView jobListView = findViewById(R.id.job_list_view);
        Button addJobFab = findViewById(R.id.add_job_fab);
        Button quickRecordButton = findViewById(R.id.btn_quick_record);

        quickRecordButton.setOnClickListener(v -> showQuickRecordConfirmationDialog());

        loadJobs();

        jobAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, jobList);
        jobListView.setAdapter(jobAdapter);

        addJobFab.setOnClickListener(v -> showAddJobDialog());

        jobListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedJob = jobList.get(position);
            Intent intent = new Intent(JobListActivity.this, TaskListActivity.class);
            intent.putExtra("jobName", selectedJob);
            startActivity(intent);
        });

        // 长按删除或重命名 Job
        jobListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedJob = jobList.get(position);
            showJobOptionsDialog(selectedJob, position);
            return true; // 表示事件已处理
        });
    }

    private void loadJobs() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        Set<String> jobs = prefs.getStringSet(JOBS_KEY, new HashSet<>());
        jobList.clear();
        jobList.addAll(jobs);
    }

    private void saveJobs() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        Set<String> jobs = new HashSet<>(jobList);
        prefs.edit().putStringSet(JOBS_KEY, jobs).apply();
    }

    private void showAddJobDialog() {
        EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Add New Job")
                .setMessage("Enter the name of the new job:")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String jobName = input.getText().toString().trim();
                    if (!jobName.isEmpty()) {
                        if (jobList.contains(jobName)) {
                            Toast.makeText(this, "Job already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            jobList.add(jobName);
                            saveJobs();
                            jobAdapter.notifyDataSetChanged();
                        }
                    } else {
                        Toast.makeText(this, "Job name cannot be empty!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showQuickRecordConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Quick Record Confirmation")
                .setMessage("This will create a new Job -> Task -> Clip structure. Do you want to continue?")
                .setPositiveButton("Confirm", (dialog, which) -> performQuickRecord())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void performQuickRecord() {
        String defaultJobBaseName = "job_default_";
        int jobIndex = 1;

        while (jobList.contains(defaultJobBaseName + jobIndex)) {
            jobIndex++;
        }

        String defaultJobName = defaultJobBaseName + jobIndex;
        String defaultTaskName = "task_default_1";
        String defaultClipName = "clip_default_1";

        jobList.add(defaultJobName);
        saveJobs();
        runOnUiThread(() -> jobAdapter.notifyDataSetChanged());

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String taskKey = defaultJobName + "_" + defaultTaskName;
        prefs.edit().putStringSet(taskKey, new HashSet<>()).apply();

        String clipKey = taskKey + "_clips";
        Set<String> clips = new HashSet<>();
        clips.add(defaultClipName);
        prefs.edit().putStringSet(clipKey, clips).apply();

        Intent intent = new Intent(JobListActivity.this, MainActivity.class);
        intent.putExtra("jobName", defaultJobName);
        intent.putExtra("taskName", defaultTaskName);
        intent.putExtra("clipName", defaultClipName);
        intent.putExtra("quickRecord", true);
        startActivityForResult(intent, REQUEST_CODE_RECORD);
    }

    private void showJobOptionsDialog(String jobName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Job Options")
                .setItems(new CharSequence[]{"Rename", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showRenameJobDialog(jobName, position);
                    } else if (which == 1) {
                        showDeleteJobDialog(jobName, position);
                    }
                })
                .show();
    }

    private void showRenameJobDialog(String oldJobName, int position) {
        EditText input = new EditText(this);
        input.setText(oldJobName);
        new AlertDialog.Builder(this)
                .setTitle("Rename Job")
                .setMessage("Enter the new name for the job:")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newJobName = input.getText().toString().trim();
                    if (!newJobName.isEmpty() && !jobList.contains(newJobName)) {
                        renameJob(oldJobName, newJobName, position);
                    } else {
                        Toast.makeText(this, "Invalid or duplicate job name!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void renameJob(String oldJobName, String newJobName, int position) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Update associated tasks in SharedPreferences
        Set<String> taskKeys = prefs.getAll().keySet();
        for (String key : taskKeys) {
            if (key.startsWith(oldJobName + "_")) {
                String newKey = key.replaceFirst(oldJobName + "_", newJobName + "_");
                editor.putStringSet(newKey, prefs.getStringSet(key, new HashSet<>()));
                editor.remove(key);
            }
        }

        // Update job name in the list
        jobList.set(position, newJobName);
        saveJobs();
        editor.apply();

        runOnUiThread(() -> {
            jobAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Job renamed to: " + newJobName, Toast.LENGTH_SHORT).show();
        });
    }

    private void showDeleteJobDialog(String jobName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Job")
                .setMessage("Are you sure you want to delete this job and all associated tasks?")
                .setPositiveButton("Delete", (dialog, which) -> deleteJob(jobName, position))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteJob(String jobName, int position) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Set<String> taskKeys = prefs.getAll().keySet();
        for (String key : taskKeys) {
            if (key.startsWith(jobName + "_")) {
                editor.remove(key);
            }
        }

        jobList.remove(position);
        saveJobs();
        editor.apply();

        runOnUiThread(() -> {
            jobAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Job deleted: " + jobName, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RECORD) {
            String videoPath = data != null ? data.getStringExtra("videoPath") : null;

            if (resultCode == RESULT_OK && videoPath != null) {
                Toast.makeText(this, "Quick Record Saved: " + videoPath, Toast.LENGTH_SHORT).show();
            } else {
                String lastJob = jobList.get(jobList.size() - 1);
                if (lastJob.startsWith("job_default_")) {
                    jobList.remove(lastJob);
                    saveJobs();
                    runOnUiThread(() -> jobAdapter.notifyDataSetChanged());
                    Toast.makeText(this, "Quick Record Cancelled: Empty Job Removed", Toast.LENGTH_SHORT).show();
                }
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
//import android.widget.EditText;
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
//
//public class JobListActivity extends AppCompatActivity {
//
//    private List<String> jobList = new ArrayList<>(); // 存储所有 Job 的列表
//    private ArrayAdapter<String> jobAdapter;         // ListView 的数据适配器
//    private static final String PREF_NAME = "JobPrefs"; // SharedPreferences 名称
//    private static final String JOBS_KEY = "jobs";  // 用于存储 Job 的键值
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_job_list); // 绑定布局文件
//
//        // 绑定布局中的组件
//        ListView jobListView = findViewById(R.id.job_list_view);
//        Button addJobFab = findViewById(R.id.add_job_fab);
//        Button quickRecordButton = findViewById(R.id.btn_quick_record);//0114
//        quickRecordButton.setOnClickListener(v->{ //0114
//            performQuickRecord();//0114
//        });//0114
//
//        // 加载已存储的 Job 数据
//        loadJobs();
//
//        // 初始化 ListView 的适配器
//        jobAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, jobList);
//        jobListView.setAdapter(jobAdapter);
//
//        // 添加新 Job 的按钮点击事件
//        addJobFab.setOnClickListener(v -> {
//            showAddJobDialog();
//        });
//
//        jobListView.setOnItemClickListener((parent, view, position, id) -> {
//            String selectedJob = jobList.get(position);
//            Intent intent = new Intent(JobListActivity.this, TaskListActivity.class);
//            intent.putExtra("jobName", selectedJob); // 传递选中的 Job 名称
//            startActivity(intent);
//        });
//    }
//
//    /**
//     * 加载已存储的 Job 数据
//     */
//    private void loadJobs() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        Set<String> jobs = prefs.getStringSet(JOBS_KEY, new HashSet<>());
//        jobList.clear();
//        jobList.addAll(jobs);
//    }
//
//    /**
//     * 保存 Job 数据到 SharedPreferences
//     */
//    private void saveJobs() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        Set<String> jobs = new HashSet<>(jobList);
//        prefs.edit().putStringSet(JOBS_KEY, jobs).apply();
//    }
//
//    /**
//     * 显示添加新 Job 的对话框
//     */
//    private void showAddJobDialog() {
//        EditText input = new EditText(this);
//        new AlertDialog.Builder(this)
//                .setTitle("Add New Job")
//                .setMessage("Enter the name of the new job:")
//                .setView(input)
//                .setPositiveButton("Add", (dialog, which) -> {
//                    String jobName = input.getText().toString().trim();
//                    if (!jobName.isEmpty()) {
//                        if (jobList.contains(jobName)) {
//                            Toast.makeText(this, "Job already exists!", Toast.LENGTH_SHORT).show();
//                        } else {
//                            jobList.add(jobName);
//                            saveJobs(); // 保存到 SharedPreferences
//                            jobAdapter.notifyDataSetChanged(); // 更新列表
//                        }
//                    } else {
//                        Toast.makeText(this, "Job name cannot be empty!", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
//
//    private void performQuickRecord() {
//        // 动态生成唯一的 Job 名称
//        String defaultJobBaseName = "job_default_";
//        int jobIndex = 1;
//
//        while (jobList.contains(defaultJobBaseName + jobIndex)) {
//            jobIndex++;
//        }
//
//        String defaultJobName = defaultJobBaseName + jobIndex;
//        String defaultTaskName = "task_default_1";
//        String defaultClipName = "clip_default_1";
//
//        // 添加新 Job 到列表并保存
//        jobList.add(defaultJobName);
//        saveJobs();
//
//        // 创建默认 Task 的存储键
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        String taskKey = defaultJobName + "_" + defaultTaskName;
//        prefs.edit().putStringSet(taskKey, new HashSet<>()).apply();
//
//        // 创建默认 Clip 的存储键并初始化 Clip 数据
//        String clipKey = taskKey + "_clips";
//        Set<String> clips = new HashSet<>();
//        clips.add(defaultClipName);
//        prefs.edit().putStringSet(clipKey, clips).apply();
//
//        // 跳转到 MainActivity 开始录制
//        Intent intent = new Intent(JobListActivity.this, MainActivity.class);
//        intent.putExtra("jobName", defaultJobName);
//        intent.putExtra("taskName", defaultTaskName);
//        intent.putExtra("clipName", defaultClipName);
//        intent.putExtra("quickRecord", true); // 添加快捷录制标志
//        startActivity(intent);
//    }
//
//
//}

