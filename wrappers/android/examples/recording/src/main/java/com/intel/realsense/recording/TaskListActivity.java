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

public class TaskListActivity extends AppCompatActivity {

    private List<String> taskList = new ArrayList<>();
    private ArrayAdapter<String> taskAdapter;
    private String currentJobName;
    private static final String PREF_NAME = "JobPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);

        ListView taskListView = findViewById(R.id.task_list_view);
        Button addTaskFab = findViewById(R.id.add_task_fab);
        Button return_tasktojon = findViewById(R.id.btn_return_tasktojob);

        // 获取当前 Job 名称
        currentJobName = getIntent().getStringExtra("jobName");

        // 加载任务列表
        loadTasks();

        // 初始化适配器
        taskAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, taskList);
        taskListView.setAdapter(taskAdapter);

        // 添加任务
        addTaskFab.setOnClickListener(v -> showAddTaskDialog());

        // 点击任务跳转到 ClipListActivity
        taskListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTask = taskList.get(position);
            Intent intent = new Intent(TaskListActivity.this, ClipListActivity.class);
            intent.putExtra("jobName", currentJobName);
            intent.putExtra("taskName", selectedTask);
            startActivity(intent);
        });

        // 返回按钮
        return_tasktojon.setOnClickListener(v -> {
            finish();
        });

        // 长按任务以选择删除或重命名
        taskListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedTask = taskList.get(position);
            showTaskOptionsDialog(selectedTask, position);
            return true; // 表示事件已处理
        });
    }

    // 加载任务列表
    private void loadTasks() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String key = currentJobName + "_tasks";
        Set<String> tasks = prefs.getStringSet(key, new HashSet<>());
        taskList.clear();
        taskList.addAll(tasks);
    }

    // 保存任务列表
    private void saveTasks() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String key = currentJobName + "_tasks";
        prefs.edit().putStringSet(key, new HashSet<>(taskList)).apply();
    }

    // 显示添加任务对话框
    private void showAddTaskDialog() {
        EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Add New Task")
                .setMessage("Enter the name of the new task:")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String taskName = input.getText().toString().trim();
                    if (!taskName.isEmpty()) {
                        if (taskList.contains(taskName)) {
                            Toast.makeText(this, "Task already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            taskList.add(taskName);
                            saveTasks(); // 保存到 SharedPreferences
                            taskAdapter.notifyDataSetChanged(); // 更新列表
                        }
                    } else {
                        Toast.makeText(this, "Task name cannot be empty!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // 显示任务选项对话框（删除或重命名）
    private void showTaskOptionsDialog(String taskName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Task Options")
                .setItems(new CharSequence[]{"Rename", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showRenameTaskDialog(taskName, position);
                    } else if (which == 1) {
                        showDeleteTaskDialog(taskName, position);
                    }
                })
                .show();
    }

    // 显示删除任务对话框
    private void showDeleteTaskDialog(String taskName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task and all associated clips?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTask(taskName, position))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // 删除任务逻辑
    private void deleteTask(String taskName, int position) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 删除与 Task 相关的 Clips 数据
        String clipKey = currentJobName + "_" + taskName + "_clips";
        editor.remove(clipKey);

        // 从列表中移除任务
        taskList.remove(position);
        saveTasks();
        editor.apply();

        // 更新 UI
        runOnUiThread(() -> {
            taskAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Task deleted: " + taskName, Toast.LENGTH_SHORT).show();
        });
    }

    // 显示重命名任务对话框
    private void showRenameTaskDialog(String oldTaskName, int position) {
        EditText input = new EditText(this);
        input.setText(oldTaskName);
        new AlertDialog.Builder(this)
                .setTitle("Rename Task")
                .setMessage("Enter the new name for the task:")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newTaskName = input.getText().toString().trim();
                    if (!newTaskName.isEmpty() && !taskList.contains(newTaskName)) {
                        renameTask(oldTaskName, newTaskName, position);
                    } else {
                        Toast.makeText(this, "Invalid or duplicate task name!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // 重命名任务逻辑
    private void renameTask(String oldTaskName, String newTaskName, int position) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 更新与 Task 相关的 Clips 数据键
        String oldClipKey = currentJobName + "_" + oldTaskName + "_clips";
        String newClipKey = currentJobName + "_" + newTaskName + "_clips";
        editor.putStringSet(newClipKey, prefs.getStringSet(oldClipKey, new HashSet<>()));
        editor.remove(oldClipKey);

        // 更新任务列表
        taskList.set(position, newTaskName);
        saveTasks();
        editor.apply();

        // 更新 UI
        runOnUiThread(() -> {
            taskAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Task renamed to: " + newTaskName, Toast.LENGTH_SHORT).show();
        });
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
//public class TaskListActivity extends AppCompatActivity {
//
//    private List<String> taskList = new ArrayList<>();
//    private ArrayAdapter<String> taskAdapter;
//    private String currentJobName;
//    private static final String PREF_NAME = "JobPrefs";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_task_list);
//
//        ListView taskListView = findViewById(R.id.task_list_view);
//        Button addTaskFab = findViewById(R.id.add_task_fab);
//        Button return_tasktojon = findViewById(R.id.btn_return_tasktojob);
//
//        currentJobName = getIntent().getStringExtra("jobName");
//
//        loadTasks();
//
//        taskAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, taskList);
//        taskListView.setAdapter(taskAdapter);
//
//        addTaskFab.setOnClickListener(v -> showAddTaskDialog());
//
//        taskListView.setOnItemClickListener((parent, view, position, id) -> {
//            String selectedTask = taskList.get(position);
//            Intent intent = new Intent(TaskListActivity.this, ClipListActivity.class);
//            intent.putExtra("jobName", currentJobName);
//            intent.putExtra("taskName", selectedTask);
//            startActivity(intent);
//        });
//
//        return_tasktojon.setOnClickListener(v -> {
//            finish();
//        });
//    }
//
//    private void loadTasks() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        String key = currentJobName + "_tasks";
//        Set<String> tasks = prefs.getStringSet(key, new HashSet<>());
//        taskList.clear();
//        taskList.addAll(tasks);
//    }
//
//    private void saveTasks() {
//        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
//        String key = currentJobName + "_tasks";
//        prefs.edit().putStringSet(key, new HashSet<>(taskList)).apply();
//    }
//
//    private void showAddTaskDialog() {
//        EditText input = new EditText(this);
//        new AlertDialog.Builder(this)
//                .setTitle("Add New Task")
//                .setMessage("Enter the name of the new task:")
//                .setView(input)
//                .setPositiveButton("Add", (dialog, which) -> {
//                    String taskName = input.getText().toString().trim();
//                    if (!taskName.isEmpty()) {
//                        if (taskList.contains(taskName)) {
//                            Toast.makeText(this, "Task already exists!", Toast.LENGTH_SHORT).show();
//                        } else {
//                            taskList.add(taskName);
//                            saveTasks(); // 保存到 SharedPreferences
//                            taskAdapter.notifyDataSetChanged(); // 更新列表
//                        }
//                    } else {
//                        Toast.makeText(this, "Task name cannot be empty!", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
//}

