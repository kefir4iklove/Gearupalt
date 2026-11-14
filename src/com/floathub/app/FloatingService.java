package com.floathub.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloatingService extends Service {

    public static volatile boolean running = false;

    private static final String CHANNEL_ID = "floathub";
    private static final String DISCORD_URL = "https://discord.com/app";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WindowManager wm;
    private Handler ui;
    private View bubble;
    private WindowManager.LayoutParams bubbleParams;
    private View menu;
    private WindowManager.LayoutParams menuParams;
    private final Map<String, FloatWindow> windows = new HashMap<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ui = new Handler(Looper.getMainLooper());
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "FloatHub",
                NotificationManager.IMPORTANCE_MIN);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("FloatHub работает")
                .setContentText("Плавающие окна активны")
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        removeAllViews();
        super.onDestroy();
    }

    private void removeAllViews() {
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        hideMenu();
        for (FloatWindow w : new ArrayList<>(windows.values())) {
            w.close();
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBg(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void showBubble() {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.bubble);
        int size = dp(52);
        bubbleParams = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(12);
        bubbleParams.y = dp(180);
        bubble = iv;

        iv.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = bubbleParams.x;
                        startY = bubbleParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        bubbleParams.x = startX + dx;
                        bubbleParams.y = startY + dy;
                        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggleMenu();
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(bubble, bubbleParams);
        } catch (Exception e) {
            Toast.makeText(this, "Нужно разрешение «поверх других окон»", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void toggleMenu() {
        if (menu != null) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void hideMenu() {
        if (menu != null) {
            try { wm.removeView(menu); } catch (Exception ignored) {}
            menu = null;
        }
    }

    private void showMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedBg(0xF220242E, 18));
        box.setPadding(dp(14), dp(10), dp(14), dp(14));
        box.setElevation(dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("FloatHub");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(16);
        title.setPadding(0, dp(4), 0, dp(8));
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView quit = new TextView(this);
        quit.setText("⏻");
        quit.setTextColor(0xFFFF6B6B);
        quit.setTextSize(20);
        quit.setPadding(dp(12), dp(4), dp(4), dp(8));
        quit.setOnClickListener(v -> {
            hideMenu();
            stopSelf();
        });
        head.addView(quit);
        box.addView(head);

        String[][] items = {
                {"🖼", "Галерея", "gallery"},
                {"🌐", "Браузер", "browser"},
                {"💬", "Discord", "discord"},
                {"📝", "Заметки", "notes"},
                {"📱", "Приложения", "apps"},
        };
        for (String[] it : items) {
            TextView row = new TextView(this);
            row.setText(it[0] + "  " + it[1]);
            row.setTextColor(Color.WHITE);
            row.setTextSize(16);
            row.setPadding(dp(10), dp(12), dp(10), dp(12));
            row.setBackground(roundedBg(0xFF2B3140, 12));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(8);
            String id = it[2];
            row.setOnClickListener(v -> {
                hideMenu();
                openWindow(id);
            });
            box.addView(row, rp);
        }

        menuParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x = bubbleParams.x;
        menuParams.y = bubbleParams.y + dp(60);
        menu = box;
        try {
            wm.addView(menu, menuParams);
        } catch (Exception ignored) {
            menu = null;
        }
    }

    private void openWindow(String id) {
        String title;
        View content;
        int w, h;
        switch (id) {
            case "gallery":
                title = "🖼 Галерея";
                content = new GalleryContent(this);
                w = 320; h = 440;
                break;
            case "browser":
                title = "🌐 Браузер";
                content = new BrowserContent(this, "https://www.google.com", false);
                w = 340; h = 480;
                break;
            case "discord":
                title = "💬 Discord";
                content = new BrowserContent(this, DISCORD_URL, true);
                w = 340; h = 480;
                break;
            case "notes":
                title = "📝 Заметки";
                content = new NotesContent(this);
                w = 300; h = 380;
                break;
            case "apps":
            default:
                title = "📱 Приложения";
                content = new AppsContent(this);
                w = 320; h = 440;
                break;
        }
        FloatWindow win = new FloatWindow(id, title, w, h, content);
        if (win.root != null) {
            windows.put(id, win);
        }
    }

    private class FloatWindow {
        final String id;
        LinearLayout root;
        final FrameLayout content;
        final WindowManager.LayoutParams params;

        private FloatWindow(String id, String title, int wDp, int hDp, View contentView) {
            this.id = id;
            root = new LinearLayout(FloatingService.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(roundedBg(0xF51B1F28, 14));
            root.setElevation(dp(10));

            LinearLayout header = new LinearLayout(FloatingService.this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(12), dp(6), dp(8), dp(6));

            TextView tv = new TextView(FloatingService.this);
            tv.setText(title);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextSize(14);
            header.addView(tv, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView close = new TextView(FloatingService.this);
            close.setText("✕");
            close.setTextColor(0xFFFF6B6B);
            close.setTextSize(16);
            close.setPadding(dp(10), dp(4), dp(6), dp(4));
            close.setOnClickListener(v -> close());
            header.addView(close);

            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            content = new FrameLayout(FloatingService.this);
            content.setBackground(roundedBg(0xFF232833, 10));
            content.addView(contentView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            params = new WindowManager.LayoutParams(dp(wDp), dp(hDp),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = dp(40 + windows.size() * 24);
            params.y = dp(120 + windows.size() * 24);

            header.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY;
                int startX, startY;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX();
                            downY = e.getRawY();
                            startX = params.x;
                            startY = params.y;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = startX + (int) (e.getRawX() - downX);
                            params.y = startY + (int) (e.getRawY() - downY);
                            try { wm.updateViewLayout(root, params); } catch (Exception ignored) {}
                            return true;
                    }
                    return false;
                }
            });

            try {
                wm.addView(root, params);
            } catch (Exception e) {
                Toast.makeText(FloatingService.this, "Не удалось открыть окно", Toast.LENGTH_SHORT).show();
                root = null;
            }
        }

        void close() {
            if (root != null) {
                try { wm.removeView(root); } catch (Exception ignored) {}
                root = null;
            }
            windows.remove(id);
        }
    }

    private class GalleryContent extends FrameLayout {
        private final GridView grid;
        private final TextView empty;
        private final List<Bitmap> thumbs = new ArrayList<>();
        private final List<Uri> uris = new ArrayList<>();

        GalleryContent(Context ctx) {
            super(ctx);
            grid = new GridView(ctx);
            grid.setNumColumns(3);
            grid.setHorizontalSpacing(dp(4));
            grid.setVerticalSpacing(dp(4));
            grid.setPadding(dp(6), dp(6), dp(6), dp(6));
            grid.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() { return thumbs.size(); }
                @Override
                public Object getItem(int position) { return uris.get(position); }
                @Override
                public long getItemId(int position) { return position; }
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    ImageView iv = (convertView instanceof ImageView)
                            ? (ImageView) convertView : new ImageView(ctx);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setLayoutParams(new GridView.LayoutParams(dp(92), dp(92)));
                    iv.setImageBitmap(thumbs.get(position));
                    return iv;
                }
            });
            grid.setOnItemClickListener((parent, view, position, id) -> showFull(uris.get(position)));
            addView(grid, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            empty = new TextView(ctx);
            empty.setText("Нет доступа к фото или изображений не найдено.\nВыдайте доступ в главном окне приложения.");
            empty.setTextColor(0xFF9AA3B2);
            empty.setTextSize(13);
            empty.setPadding(dp(16), dp(24), dp(16), dp(16));
            addView(empty, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            loadImages();
        }

        private void loadImages() {
            new Thread(() -> {
                List<Bitmap> t = new ArrayList<>();
                List<Uri> u = new ArrayList<>();
                try {
                    String[] proj = {MediaStore.Images.Media._ID};
                    android.database.Cursor c = getContentResolver().query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                            null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                    if (c != null) {
                        while (c.moveToNext() && t.size() < 120) {
                            long id = c.getLong(0);
                            Uri uri = Uri.withAppendedPath(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                            Bitmap b = decode(uri, 256);
                            if (b != null) {
                                t.add(b);
                                u.add(uri);
                            }
                        }
                        c.close();
                    }
                } catch (Exception ignored) {}
                ui.post(() -> {
                    thumbs.clear();
                    uris.clear();
                    thumbs.addAll(t);
                    uris.addAll(u);
                    ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
                    empty.setVisibility(thumbs.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }).start();
        }

        private void showFull(Uri uri) {
            new Thread(() -> {
                Bitmap full = decode(uri, 1024);
                ui.post(() -> {
                    if (full == null) return;
                    LinearLayout box = new LinearLayout(FloatingService.this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    TextView back = new TextView(FloatingService.this);
                    back.setText("←  назад");
                    back.setTextColor(0xFF8FB7FF);
                    back.setTextSize(14);
                    back.setPadding(dp(12), dp(8), dp(12), dp(8));
                    back.setOnClickListener(v -> {
                        box.setVisibility(View.GONE);
                        grid.setVisibility(View.VISIBLE);
                    });
                    ImageView iv = new ImageView(FloatingService.this);
                    iv.setImageBitmap(full);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    box.addView(back, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    box.addView(iv, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                    grid.setVisibility(View.GONE);
                    addView(box, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                });
            }).start();
        }

        private Bitmap decode(Uri uri, int target) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream is = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(is, null, o);
                if (is != null) is.close();
                int sample = 1;
                while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) {
                    sample *= 2;
                }
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                is = getContentResolver().openInputStream(uri);
                Bitmap b = BitmapFactory.decodeStream(is, null, o2);
                if (is != null) is.close();
                return b;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class BrowserContent extends LinearLayout {
        private final EditText urlBar;
        private final WebView web;
        private final String homeUrl;

        BrowserContent(Context ctx, String startUrl, boolean desktopUa) {
            super(ctx);
            setOrientation(VERTICAL);
            homeUrl = startUrl;

            LinearLayout bar = new LinearLayout(ctx);
            bar.setOrientation(HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(8), dp(6), dp(8), dp(6));

            urlBar = new EditText(ctx);
            urlBar.setSingleLine(true);
            urlBar.setTextSize(13);
            urlBar.setTextColor(Color.WHITE);
            urlBar.setHintTextColor(0xFF77808F);
            urlBar.setHint("Введите адрес");
            urlBar.setBackground(roundedBg(0xFF323949, 8));
            urlBar.setPadding(dp(10), dp(8), dp(10), dp(8));
            bar.addView(urlBar, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView go = new TextView(ctx);
            go.setText("→");
            go.setTextColor(0xFF8FB7FF);
            go.setTextSize(18);
            go.setPadding(dp(12), dp(4), dp(8), dp(4));
            go.setOnClickListener(v -> load(urlBar.getText().toString()));
            bar.addView(go);

            addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            web = new WebView(new android.view.ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault));
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.getSettings().setBuiltInZoomControls(true);
            web.getSettings().setDisplayZoomControls(false);
            if (desktopUa) {
                web.getSettings().setUserAgentString(DESKTOP_UA);
            }
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    urlBar.setText(url);
                }
            });
            web.setWebChromeClient(new WebChromeClient());
            addView(web, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout nav = new LinearLayout(ctx);
            nav.setOrientation(HORIZONTAL);
            nav.setGravity(Gravity.CENTER);
            String[] labels = {"⟨ назад", "домой", "открыть в приложении"};
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                TextView b = new TextView(ctx);
                b.setText(labels[i]);
                b.setTextColor(0xFFB9C2D0);
                b.setTextSize(13);
                b.setPadding(dp(14), dp(10), dp(14), dp(10));
                b.setOnClickListener(v -> {
                    if (idx == 0) {
                        web.goBack();
                    } else if (idx == 1) {
                        load(homeUrl);
                    } else {
                        try {
                            Intent i2 = new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl()));
                            i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i2);
                        } catch (Exception ignored) {}
                    }
                });
                nav.addView(b);
            }
            addView(nav, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            load(startUrl);
        }

        private void load(String raw) {
            String url = raw == null ? "" : raw.trim();
            if (url.isEmpty()) return;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (url.contains(".") && !url.contains(" ")) {
                    url = "https://" + url;
                } else {
                    url = "https://www.google.com/search?q=" + Uri.encode(raw);
                }
            }
            urlBar.setText(url);
            web.loadUrl(url);
        }
    }

    private class AppsContent extends FrameLayout {
        private final List<ResolveInfo> apps = new ArrayList<>();
        private final PackageManager pm;

        AppsContent(Context ctx) {
            super(ctx);
            pm = getPackageManager();
            ListView list = new ListView(ctx);
            list.setDivider(null);
            list.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() { return apps.size(); }
                @Override
                public Object getItem(int position) { return apps.get(position); }
                @Override
                public long getItemId(int position) { return position; }
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    LinearLayout row = (convertView instanceof LinearLayout)
                            ? (LinearLayout) convertView : null;
                    if (row == null) {
                        row = new LinearLayout(ctx);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(12), dp(8), dp(12), dp(8));
                        ImageView icon = new ImageView(ctx);
                        icon.setId(0x1001);
                        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
                        row.addView(icon);
                        TextView label = new TextView(ctx);
                        label.setId(0x1002);
                        label.setTextColor(Color.WHITE);
                        label.setTextSize(14);
                        label.setPadding(dp(12), 0, 0, 0);
                        row.addView(label, new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    }
                    ResolveInfo ri = apps.get(position);
                    ImageView icon = row.findViewById(0x1001);
                    TextView label = row.findViewById(0x1002);
                    icon.setImageDrawable(ri.loadIcon(pm));
                    label.setText(ri.loadLabel(pm));
                    return row;
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> {
                ResolveInfo ri = apps.get(position);
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (Exception ignored) {}
            });
            addView(list, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            apps.clear();
            for (ResolveInfo ri : ris) {
                if (!getPackageName().equals(ri.activityInfo.packageName)) {
                    apps.add(ri);
                }
            }
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        }
    }

    private class NotesContent extends LinearLayout {
        NotesContent(Context ctx) {
            super(ctx);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(10), dp(10), dp(10));

            final SharedPreferences sp = getSharedPreferences("floathub_notes", MODE_PRIVATE);
            final EditText edit = new EditText(ctx);
            edit.setGravity(Gravity.TOP | Gravity.START);
            edit.setTextSize(14);
            edit.setTextColor(Color.WHITE);
            edit.setHintTextColor(0xFF77808F);
            edit.setHint("Пишите здесь…");
            edit.setText(sp.getString("text", ""));
            edit.setBackground(roundedBg(0xFF323949, 8));
            edit.setPadding(dp(10), dp(10), dp(10), dp(10));
            edit.setMinLines(6);
            addView(edit, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            Button save = new Button(ctx);
            save.setText("Сохранить");
            save.setTextColor(Color.WHITE);
            save.setBackground(roundedBg(0xFF4F7CFF, 10));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(10);
            save.setOnClickListener(v -> {
                sp.edit().putString("text", edit.getText().toString()).apply();
                Toast.makeText(FloatingService.this, "Сохранено", Toast.LENGTH_SHORT).show();
            });
            addView(save, bp);
        }
    }
}
