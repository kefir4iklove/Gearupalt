package com.floathub.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 1;

    private TextView status;
    private Button btnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        btnToggle = findViewById(R.id.btn_toggle);

        findViewById(R.id.btn_overlay).setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_storage).setOnClickListener(v -> requestNeededPerms());

        btnToggle.setOnClickListener(v -> {
            Intent i = new Intent(this, FloatingService.class);
            if (FloatingService.running) {
                stopService(i);
            } else {
                startForegroundService(i);
            }
        });
    }

    private void requestNeededPerms() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                    "android.permission.POST_NOTIFICATIONS"}, REQ_PERMS);
        } else if (Build.VERSION.SDK_INT == 33) {
            requestPermissions(new String[]{
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.POST_NOTIFICATIONS"}, REQ_PERMS);
        } else {
            requestPermissions(new String[]{
                    "android.permission.READ_EXTERNAL_STORAGE"}, REQ_PERMS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private boolean hasMediaPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        status.setText("Поверх окон: " + (overlay ? "✔ разрешено" : "✕ не разрешено")
                + "\nДоступ к фото: " + (hasMediaPermission() ? "✔ есть" : "✕ нет (галерея будет пуста)")
                + "\nПанель: " + (FloatingService.running ? "запущена" : "остановлена"));
        btnToggle.setText(FloatingService.running ? R.string.btn_stop : R.string.btn_start);
    }
}
