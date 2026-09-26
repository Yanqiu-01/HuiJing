from pathlib import Path
root=Path('/workspace/inkbench')
p=root/'app/src/main/java/app/inkbench/studio/MainActivity.java'
t=p.read_text()
t=t.replace('    private BroadcastReceiver jobReceiver;', '''    private GalleryStore.Entry selectedEntry;
    private Button stopButton;
    private boolean enhancing;
    private int galleryLimit = 24;
    private String observedEntry = "";
    private final android.os.Handler jobHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollJob = new Runnable() {
        public void run() { syncJob(); jobHandler.postDelayed(this, 1000); }
    };''')
a=t.index('        int status = 0;'); b=t.index('        LinearLayout top = vertical();',a)
t=t[:a]+'''        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();

'''+t[b:]
t=t.replace('title.setTypeface(Typeface.SERIF);','title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));')
t=t.replace('"墨台", 26','"墨台", 23').replace('"画面", 16','"描述画面", 16')
t=t.replace('        composer.addView(actions);','''        composer.addView(actions);
        stopButton = button("当前张完成后停止", false);
        stopButton.setVisibility(View.GONE);
        stopButton.setOnClickListener(v -> {
            if (JobService.isRunning()) {
                startService(new Intent(this, JobService.class).setAction("STOP_AFTER_CURRENT"));
                stopButton.setEnabled(false);
            }
        });
        composer.addView(gap(8));
        composer.addView(stopButton);''')
t=t.replace('        promptField.setMinLines(4);','        promptField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);\n        promptField.setMinLines(3);')
t=t.replace('            {"1024x1024", "方"}', '            {"1024x1024", "1:1 方形"}').replace('"1024x1536", "竖"','"1024x1536", "2:3 竖图"').replace('"1536x1024", "横"','"1536x1024", "3:2 横图"').replace('"1024x1792", "长竖"','"1024x1792", "4:7 长竖"').replace('"1792x1024", "长横"','"1792x1024", "7:4 长横"')
# service start: don't fall back to invalid calls on modern Android
a=t.index('        if (android.os.Build.VERSION.SDK_INT >= 26) {',t.index('    private void startJob()'))
b=t.index('    private void showEntry(',a)
t=t[:a]+'''        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 71);
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(job);
            else startService(job);
            setBusy(true, "任务正在启动…");
        } catch (Exception e) {
            setBusy(false, "任务启动失败：" + e.getMessage());
        }
    }

    private void syncJob() {
        SharedPreferences job = getSharedPreferences(JobService.STATE, MODE_PRIVATE);
        if (!job.contains("message")) return;
        boolean active = JobService.isRunning();
        if (job.getBoolean("active", false) && !active) {
            job.edit().putBoolean("active", false).putString("message",
                    "上次任务已中断；已保存图片保留，不自动重发以免重复消耗额度").commit();
        }
        String id = job.getString("entry", "");
        if (!id.equals(observedEntry)) {
            observedEntry = id;
            renderGallery();
            for (GalleryStore.Entry entry : gallery.list()) {
                if (id.equals(entry.id)) { showEntry(entry); break; }
            }
        }
        if (!enhancing) {
            String message = job.getString("message", "待命");
            if (active) {
                long seconds = Math.max(0, (System.currentTimeMillis()-job.getLong("started", System.currentTimeMillis()))/1000);
                message += " · " + (seconds/60) + "分" + (seconds%60) + "秒";
            }
            // Do not overwrite an unrelated foreground message with an old completed task.
            if (active || busy || job.getBoolean("active", false) || !message.equals(lastJobMessage)) {
                setBusy(active, message);
                lastJobMessage = message;
            }
        }
    }
    private String lastJobMessage = "";

    @Override protected void onStart() {
        super.onStart();
        jobHandler.removeCallbacks(pollJob);
        jobHandler.post(pollJob);
        renderGallery();
    }
    @Override protected void onStop() {
        jobHandler.removeCallbacks(pollJob);
        savePrefs();
        super.onStop();
    }

'''+t[b:]
t=t.replace('        resultView.setImageBitmap(bitmap);','        selectedEntry = entry;\n        resultView.setImageBitmap(bitmap);')
t=t.replace('        statusView.setText("完成 · " + when);','')
t=t.replace('        for (int i = 0; i < entries.size(); i++) {','        for (int i = 0; i < Math.min(entries.size(), galleryLimit); i++) {')
t=t.replace('            thumb.setBackground(bg);','            thumb.setBackground(bg);\n            thumb.setClipToOutline(true);\n            thumb.setContentDescription("查看图片：" + entry.prompt);')
t=t.replace('        padGrid(galleryGrid);','''        padGrid(galleryGrid);
        if (entries.size() > galleryLimit) {
            Button more = button("再显示 24 张 · 共 " + entries.size() + " 张", false);
            more.setOnClickListener(v -> { galleryLimit += 24; renderGallery(); });
            galleryGrid.addView(more);
        }''')
t=t.replace('                    gallery.delete(entry.id);','''                    gallery.delete(entry.id);
                    if (selectedEntry != null && selectedEntry.id.equals(entry.id)) {
                        selectedEntry = null;
                        resultView.setImageDrawable(null);
                        resultBlock.setVisibility(View.GONE);
                    }''')
t=t.replace('        GalleryStore.Entry entry = latest();','        GalleryStore.Entry entry = selectedEntry;')
a=t.index('        try {\n            Album.save(');b=t.index('\n    private void shareLatest()',a)
t=t[:a]+'''        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 72);
            toast("授权后请再点一次保存");
            return;
        }
        new Thread(() -> {
            try {
                Album.save(this, gallery.fileOf(entry), "inkbench-" + entry.id + ".png");
                runOnUiThread(() -> toast("已保存到系统相册 · Pictures/Inkbench"));
            } catch(Exception e) {
                runOnUiThread(() -> toast("保存失败：" + e.getMessage()));
            }
        }, "inkbench-export").start();
    }
'''+t[b:]
t=t.replace('        generateButton.setText(value ? "生成中" : "生成");\n        enhanceButton.setText(value ? "…" : "增强");','''        generateButton.setText(value && !enhancing ? "生成中…" : "生成 " + count + " 张");
        enhanceButton.setText(enhancing ? "增强中…" : "增强提示词");
        stopButton.setVisibility(value && !enhancing ? View.VISIBLE : View.GONE);
        if (!value) stopButton.setEnabled(true);''')
t=t.replace('        setBusy(true, "正在扩写…");','        enhancing = true;\n        setBusy(true, "正在扩写…");')
t=t.replace('                setBusy(false, result.expanded == null ?', '                enhancing = false;\n                setBusy(false, result.expanded == null ?')
t=t.replace('                if (result.expanded != null) promptField.setText(result.expanded);','''                if (result.expanded != null) {
                    promptField.setText(result.expanded);
                    savePrefs();
                }''')
t=t.replace('        body.setVisibility(View.VISIBLE);','        body.setVisibility(View.VISIBLE);\n        (image ? imageCaret : textCaret).setText("▾");')
t=t.replace('        String legacyBase = prefs.getString("base", "");','        String legacyBase = prefs.getString("base", "http://127.0.0.1:4141");')
t=t.replace('        timeoutField.setText(String.valueOf(prefs.getInt("timeout", 360)));','        timeoutField.setText(String.valueOf(prefs.getInt("timeout", 360)));\n        promptField.setText(prefs.getString("draft", ""));')
t=t.replace('                .putInt("count", count)','                .putString("draft", promptField.getText().toString())\n                .putInt("count", count)')
t=t.replace('field(image ? "http://电脑地址:4141"','field(image ? "http://127.0.0.1:4141 或远程网关地址"')
# Replace broken layout engine
a=t.index('    private LinearLayout grid(int columns)');b=t.index('    private static String shorten(',a)
t=t[:a]+'''    private LinearLayout grid(int columns) {
        LinearLayout wrap = vertical();
        wrap.setTag(columns);
        return wrap;
    }
    private void setGridColumns(LinearLayout wrap, int columns) {
        wrap.removeAllViews();
        wrap.setTag(columns);
    }
    private LinearLayout.LayoutParams gridCellLp() {
        int h = Math.max(48, Math.round(40 * getResources().getConfiguration().fontScale));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(h), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }
    private void addToGrid(LinearLayout wrap, View child) {
        int columns = (Integer) wrap.getTag();
        LinearLayout row = wrap.getChildCount() == 0 ? null : (LinearLayout) wrap.getChildAt(wrap.getChildCount()-1);
        if (row == null || row.getChildCount() == columns) {
            row = chipRow();
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(child, gridCellLp());
    }
    private void padGrid(LinearLayout wrap) {
        if (wrap.getChildCount()==0) return;
        int columns = (Integer)wrap.getTag();
        LinearLayout row = (LinearLayout)wrap.getChildAt(wrap.getChildCount()-1);
        while(row.getChildCount()<columns) row.addView(new View(this), gridCellLp());
    }

'''+t[b:]
# unified explicit sans + disable framework button elevation
for name in ['title','promptTitle','resultTitle','galleryTitle','name']:
 t=t.replace(name+'.setTypeface(Typeface.DEFAULT_BOLD);',name+'.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));')
t=t.replace('        view.setTextSize(sp);','        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));\n        view.setIncludeFontPadding(false);\n        view.setTextSize(sp);')
t=t.replace('        edit.setTextSize(15);','        edit.setTextSize(15);\n        edit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));')
t=t.replace('        button.setAllCaps(false);','''        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setMinimumHeight(0);
        button.setMinHeight(0);''')
t=t.replace('button.setTextSize(14);','button.setTextSize(13);')
t=t.replace('        card.addView(gap(8));\n        card.addView(body);','        body.setPadding(0, dp(12), 0, 0);\n        card.addView(body);')
t=t.replace('                paintCounts();\n                savePrefs();','                paintCounts();\n                if (!busy) generateButton.setText("生成 " + count + " 张");\n                savePrefs();')
p.write_text(t)
# modern SDK, manifest & theme
p=root/'tools/build.sh'; t=p.read_text().replace('tools/android-23.jar','tools/android-35.jar'); p.write_text(t)
p=root/'app/src/main/AndroidManifest.xml'; t=p.read_text().replace('versionCode="12"','versionCode="13"').replace('versionName="2.1"','versionName="2.2"')
t=t.replace('    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />','    <uses-permission android:name="android.permission.WAKE_LOCK" />')
t=t.replace('android:name=".JobService"\n            android:exported="false"','android:name=".JobService"\n            android:foregroundServiceType="dataSync"\n            android:exported="false"')
t=t.replace('android:name=".MainActivity"','android:name=".MainActivity"\n            android:launchMode="singleTop"')
p.write_text(t)
p=root/'app/src/main/res/values/styles.xml'; t=p.read_text().replace('Theme.DeviceDefault.Light.NoActionBar','Theme.Material.Light.NoActionBar').replace('<item name="android:statusBarColor">@color/ink_strong</item>','<item name="android:statusBarColor">@color/paper</item>')
t=t.replace('    </style>','        <item name="android:fontFamily">sans-serif</item>\n        <item name="android:colorAccent">@color/jade</item>\n        <item name="android:windowLightNavigationBar">true</item>\n    </style>');p.write_text(t)
print('Applied 2.2 corrections')
