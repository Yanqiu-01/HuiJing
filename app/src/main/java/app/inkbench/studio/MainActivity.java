package app.inkbench.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Phone image bench for a M365 Copilot2API gateway.
 * Image and text endpoints are separate, collapsible settings.
 * The result pane stays hidden until a picture exists.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "inkbench";
    private static final int[] COUNTS = new int[]{1, 2, 3, 4};

    private static final String[][] SIZES = new String[][]{
            {"1024x1024", "1:1 方形"},
            {"1024x1536", "2:3 竖图"},
            {"1536x1024", "3:2 横图"},
            {"1024x1792", "4:7 长竖"},
            {"1792x1024", "7:4 长横"}
    };

    /** name, short tag appended to the prompt, longer note for the text model */
    private static final String[][] STYLES = new String[][]{
            {"不指定", "", ""},
            {"写实", "写实摄影，自然光，清晰细节", "真实摄影，自然光，材质清楚，不要插画感"},
            {"电影", "电影感宽银幕，体积光，浅景深", "电影画面，有方向的光线，浅景深，克制的色彩"},
            {"水彩", "水彩，纸纹，透亮笔触", "透明水彩，留白，纸纹，边缘柔和"},
            {"水墨", "水墨，留白，枯笔", "中国水墨，大量留白，墨色层次，不要写实照片感"},
            {"扁平", "扁平插画，干净色块，少阴影", "扁平矢量插画，明确色块，轮廓干净"},
            {"方框", "主体置于简洁方框内，方形构图，边框清晰", "方形画框式构图，主体完整落在简洁方框内，边框干净，不要复杂背景"},
            {"海报", "海报构图，大主体，强对比", "海报式构图，主体突出，色彩对比明确，不要乱加文字"},
            {"自定义", "", ""}
    };
    private static final int STYLE_CUSTOM = 8;
    private String customStyleText = "";
    private FrameLayout appShell;
    private ImageView wallpaperView;
    private View wallpaperShade;
    private Button settingsButton;
    private ScrollView mainScroll;
    private int wallpaperLoadToken;
    private String wallpaperUri = "";
    private int shadePercent = 22;
    private int glassPercent = 88;
    private int blurAmount = 8;
    private int scrollBeforeIme;
    private boolean imeWasVisible;
    private final java.util.ArrayList<java.lang.ref.WeakReference<LinearLayout>> glassCards = new java.util.ArrayList<>();

    private EditText imageBaseField;
    private EditText imageKeyField;
    private EditText textBaseField;
    private EditText textKeyField;
    private Spinner textModelSpinner;
    private Button refreshTextModelsButton;
    private TextView textModelStatus;
    private java.util.ArrayList<String> textModels = new java.util.ArrayList<String>();
    private String textModel = GatewayClient.DEFAULT_TEXT_MODEL;
    private EditText timeoutField;
    private EditText promptField;
    private TextView statusView;
    private TextView imageSummary;
    private TextView textSummary;
    private TextView resultMeta;
    private ImageView resultView;
    private ProgressBar progress;
    private Button generateButton;
    private Button enhanceButton;
    private LinearLayout imageBody;
    private LinearLayout textBody;
    private TextView imageCaret;
    private TextView textCaret;
    private LinearLayout resultBlock;
    private Spinner sizeSpinner;
    private Spinner countSpinner;
    private Spinner styleSpinner;
    private Spinner qualitySpinner;
    private SeekBar batchPromptSeek;
    private TextView batchPromptValue;
    private LinearLayout galleryGrid;
    private HorizontalScrollView galleryStrip;
    private Button galleryToggle;
    private boolean galleryExpanded;
    private int blockPercent = 88;
    private GalleryStore.Entry selectedEntry;
    private Button stopButton;
    private boolean enhancing;
    private int galleryLimit = 24;
    private String observedEntry = "";
    private final android.os.Handler jobHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollJob = new Runnable() {
        public void run() { syncJob(); jobHandler.postDelayed(this, 1000); }
    };
    private String size = "1024x1024";
    private String quality = "high";
    private int batchPromptLevel = BatchPromptPolicy.DEFAULT_LEVEL;
    private int count = 1;
    private int style = 0;
    private boolean busy;
    private boolean jobStarting;
    private long jobStartingAt;
    private boolean imageOpen;
    private boolean textOpen;
    private GalleryStore gallery;
    private final PromptHistory promptHistory = new PromptHistory();
    private PreviewLoader previews;
    private final java.util.concurrent.ExecutorService galleryWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.ArrayList<GalleryStore.Entry> visibleEntries = new java.util.ArrayList<>();
    private int galleryVersion;
    private String gallerySignature = "";
    private String pendingEntry = "";
    private boolean destroyed;
    private Button ideaButton, longTextButton, undoButton, originalButton;
    private android.widget.Switch refreshSwitch;
    private TextView refreshLabel;
    private boolean highRefresh = true;
    private String longDraft = "";
    private String wishDraft = "";
    private String textTaskName = "增强中…";
    private AsyncTask<Void, Void, Result> textTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        gallery = new GalleryStore(this);
        previews = new PreviewLoader();
        setContentView(buildUi());
        loadPrefs();
        renderGallery();
        SharedPreferences job = getSharedPreferences(JobService.STATE, MODE_PRIVATE);
        if (!JobService.isRunning() && !job.getBoolean("active", false)) observedEntry = job.getString("entry", "");
    }

    private View buildUi() {
        appShell = new FrameLayout(this);
        appShell.setBackgroundColor(0xFFF3F1EA);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appShell.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        wallpaperShade = new View(this);
        appShell.addView(wallpaperShade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = vertical();
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), 0);
            return insets;
        });
        root.requestApplyInsets();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(12), dp(18), dp(8));
        LinearLayout heading = vertical();
        TextView title = text("绘境", 23, 0xFF1A1C19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView sub = text("把灵感，绘成一方天地", 13, 0xFF6A675F);
        heading.addView(title);
        heading.addView(gap(3));
        heading.addView(sub);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        settingsButton = button("⚙ 设置", false);
        settingsButton.setOnClickListener(v -> showSettings());
        top.addView(settingsButton, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(top);

        final ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setSmoothScrollingEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(16));
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(10), dp(18), dp(32));

        imageSummary = text("", 12, 0xFF6A675F);
        imageBody = endpointBody(true);
        textSummary = text("", 12, 0xFF6A675F);
        textBody = endpointBody(false);

        page.addView(gap(10));
        LinearLayout assistantCard = card();
        TextView welcome = text("今天想生成什么？", 17, 0xFF1A1C19);
        welcome.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        assistantCard.addView(welcome);
        assistantCard.addView(hint("说说心情、用途或主题，让 AI 帮你找到画面。"));
        LinearLayout creativeActions = chipRow();
        ideaButton = button("给我灵感", false);
        longTextButton = button("长文变画面", false);
        ideaButton.setOnClickListener(v -> showIdeaInput());
        longTextButton.setOnClickListener(v -> showLongTextInput());
        creativeActions.addView(ideaButton, weight());
        creativeActions.addView(gap(8));
        creativeActions.addView(longTextButton, weight());
        assistantCard.addView(gap(10));
        assistantCard.addView(creativeActions);
        page.addView(assistantCard);
        page.addView(gap(10));
        LinearLayout composer = card();
        TextView promptTitle = text("描述画面", 16, 0xFF1A1C19);
        promptTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        composer.addView(promptTitle);
        composer.addView(gap(8));
        promptField = field("例如：雨后的青石巷，一盏暖灯", false);
        promptField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptField.setMinLines(3);
        promptField.setGravity(Gravity.TOP);
        composer.addView(promptField);
        LinearLayout historyActions = chipRow();
        undoButton = button("撤回上一步", false);
        originalButton = button("恢复最初", false);
        undoButton.setOnClickListener(v -> restorePrompt(false));
        originalButton.setOnClickListener(v -> restorePrompt(true));
        historyActions.addView(undoButton, weight());
        historyActions.addView(gap(8));
        historyActions.addView(originalButton, weight());
        composer.addView(gap(8));
        composer.addView(historyActions);

        composer.addView(gap(12));
        composer.addView(label("风格"));
        styleSpinner = dropdown();
        composer.addView(gap(6));
        composer.addView(styleSpinner);
        composer.addView(hint("选择“自定义”后输入风格；它会保存在本机，并用于提示词和文字增强。"));

        composer.addView(gap(12));
        composer.addView(label("比例"));
        sizeSpinner = dropdown();
        composer.addView(gap(6));
        composer.addView(sizeSpinner);

        composer.addView(gap(12));
        composer.addView(label("张数"));
        countSpinner = dropdown();
        composer.addView(gap(6));
        composer.addView(countSpinner);

        composer.addView(gap(12));
        composer.addView(label("质量"));
        qualitySpinner = dropdown();
        composer.addView(gap(6));
        composer.addView(qualitySpinner);
        composer.addView(hint("质量通过更严格的画面约束生效；精细和极致会自动提高请求尺寸。网关没有独立的质量参数。"));

        composer.addView(gap(12));
        composer.addView(label("多图取景变化"));
        batchPromptSeek = new SeekBar(this);
        batchPromptSeek.setMax(100);
        composer.addView(gap(4));
        LinearLayout batchPromptLine = new LinearLayout(this);
        batchPromptLine.setOrientation(LinearLayout.HORIZONTAL);
        batchPromptLine.setGravity(Gravity.CENTER_VERTICAL);
        batchPromptLine.addView(batchPromptSeek, new LinearLayout.LayoutParams(0, -2, 1f));
        batchPromptValue = text(BatchPromptPolicy.displayValue(batchPromptLevel), 14, 0xFF1A1C19);
        batchPromptValue.setMinWidth(dp(48));
        batchPromptValue.setGravity(Gravity.END);
        batchPromptLine.addView(batchPromptValue, new LinearLayout.LayoutParams(-2, -2));
        composer.addView(batchPromptLine);
        composer.addView(hint("仅生成第 2 张及以后时生效；0 最稳定，1 变化最大，不改变主体、数量和剧情。"));

        composer.addView(gap(12));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        enhanceButton = button("增强", false);
        generateButton = button("生成 1 张", true);
        enhanceButton.setOnClickListener(v -> enhancePrompt());
        generateButton.setOnClickListener(v -> startJob());
        actions.addView(enhanceButton, weight());
        actions.addView(gap(8));
        actions.addView(generateButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        composer.addView(actions);
        stopButton = button("立即中断当前张", false);
        stopButton.setVisibility(View.GONE);
        stopButton.setOnClickListener(v -> {
            if (JobService.isRunning()) {
                startService(new Intent(this, JobService.class).setAction("STOP_AFTER_CURRENT"));
                stopButton.setEnabled(false);
            }
        });
        composer.addView(gap(8));
        composer.addView(stopButton);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        statusView = text("待命", 13, 0xFF6A675F);
        composer.addView(gap(8));
        composer.addView(statusView);
        composer.addView(progress);
        page.addView(composer);

        resultBlock = card();
        resultBlock.setVisibility(View.GONE);
        LinearLayout resultHead = new LinearLayout(this);
        resultHead.setOrientation(LinearLayout.HORIZONTAL);
        resultHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView resultTitle = text("结果", 16, 0xFF1A1C19);
        resultTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resultMeta = text("", 12, 0xFF6A675F);
        resultHead.addView(resultTitle, weight());
        resultHead.addView(resultMeta);
        resultBlock.addView(resultHead);
        resultBlock.addView(gap(8));
        resultView = new ImageView(this);
        resultView.setAdjustViewBounds(true);
        resultView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(12));
        frame.setColor(0xFF14382A);
        resultView.setBackground(frame);
        resultBlock.addView(resultView);
        LinearLayout resultButtons = new LinearLayout(this);
        resultButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("补存相册", false);
        Button share = button("分享", false);
        Button hide = button("收起", false);
        save.setOnClickListener(v -> exportLatest());
        share.setOnClickListener(v -> shareLatest());
        hide.setOnClickListener(v -> resultBlock.setVisibility(View.GONE));
        resultButtons.addView(save, weight());
        resultButtons.addView(gap(8));
        resultButtons.addView(share, weight());
        resultButtons.addView(gap(8));
        resultButtons.addView(hide, weight());
        resultBlock.addView(gap(10));
        resultBlock.addView(resultButtons);
        page.addView(gap(10));
        page.addView(resultBlock);

        page.addView(gap(16));
        LinearLayout galleryHead = new LinearLayout(this);
        galleryHead.setOrientation(LinearLayout.HORIZONTAL);
        galleryHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView galleryTitle = text("画册", 16, 0xFF1A1C19);
        galleryTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        galleryHead.addView(galleryTitle, weight());
        galleryHead.addView(text("点开 · 长按删除", 12, 0xFF6A675F));
        galleryHead.addView(gap(8));
        galleryToggle = button("展开", false);
        galleryToggle.setMinHeight(dp(36));
        galleryToggle.setTextSize(13);
        galleryToggle.setOnClickListener(v -> {
            galleryExpanded = !galleryExpanded;
            galleryToggle.setText(galleryExpanded ? "收起" : "展开");
            gallerySignature = "";
            renderGallery();
            savePrefs();
        });
        galleryHead.addView(galleryToggle, new LinearLayout.LayoutParams(-2, dp(38)));
        page.addView(galleryHead);
        page.addView(gap(8));
        galleryStrip = new HorizontalScrollView(this);
        galleryStrip.setHorizontalScrollBarEnabled(false);
        galleryStrip.setFillViewport(false);
        galleryGrid = grid(3);
        galleryStrip.addView(galleryGrid, new FrameLayout.LayoutParams(-2, -2));
        page.addView(galleryStrip);
        page.addView(hint("新图自动保存到系统相册「绘境」。删除这里的图片不删除相册副本。旧图可选中后补存。"));

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect visible = new android.graphics.Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int obscured = root.getRootView().getHeight() - visible.bottom;
            boolean imeVisible = obscured > dp(160);
            if (imeVisible) {
                if (!imeWasVisible) scrollBeforeIme = scroll.getScrollY();
                imeWasVisible = true;
            } else if (imeWasVisible) {
                imeWasVisible = false;
                scroll.post(() -> scroll.scrollTo(0, scrollBeforeIme));
            }
        });
        appShell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        paintSizes();
        paintCounts();
        paintStyles();
        paintQuality();
        paintBatchPromptLevel();
        paintTextModels();
        bindDropdowns();
        applyAppearance();
        return appShell;
    }

    private LinearLayout endpointBody(boolean image) {
        LinearLayout body = vertical();
        EditText base = field(image ? "填写电脑局域网地址，例如 192.168.x.x:4141" : "留空则跟随生图地址", false);
        EditText key = field(image ? "生图 API Key" : "留空则用生图的 Key", true);
        if (image) {
            imageBaseField = base;
            imageKeyField = key;
        } else {
            textBaseField = base;
            textKeyField = key;
        }
        body.addView(label(image ? "地址" : "地址"));
        body.addView(gap(4));
        body.addView(base);
        body.addView(gap(8));
        body.addView(label("密钥"));
        body.addView(gap(4));
        body.addView(key);
        if (image) {
            timeoutField = field("超时秒数，默认 360", false);
            timeoutField.setInputType(InputType.TYPE_CLASS_NUMBER);
            body.addView(gap(8));
            body.addView(label("等待"));
            body.addView(gap(4));
            body.addView(timeoutField);
            body.addView(hint("502、503、504 会用相同请求再试一次；401、403、429 不重试。多张是逐次生图。"));
        } else {
            body.addView(gap(8));
            body.addView(label("文字模型"));
            textModelSpinner = dropdown();
            body.addView(gap(4));
            body.addView(textModelSpinner);
            LinearLayout modelActions = chipRow();
            refreshTextModelsButton = button("从 /v1/models 刷新", false);
            refreshTextModelsButton.setTextSize(13);
            refreshTextModelsButton.setOnClickListener(v -> refreshTextModels());
            modelActions.addView(refreshTextModelsButton, weight());
            textModelStatus = hint("选择后立即记住；不会静默切换模型。");
            body.addView(gap(6));
            body.addView(modelActions);
            body.addView(textModelStatus);
            body.addView(hint("走 /v1/chat/completions。地址空着就用上面的生图地址。"));
        }
        return body;
    }

    private View foldCard(final String title, final TextView summary, final View body, final boolean image) {
        final LinearLayout card = card();
        final LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final TextView caret = text("▸", 16, 0xFF1F6B4A);
        if (image) imageCaret = caret;
        else textCaret = caret;
        TextView name = text(title, 16, 0xFF1A1C19);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout titles = vertical();
        titles.addView(name);
        titles.addView(summary);
        head.addView(caret);
        head.addView(gap(8));
        head.addView(titles, weight());
        card.addView(head);
        body.setPadding(0, dp(12), 0, 0);
        card.addView(body);
        body.setVisibility(View.GONE);
        head.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            Motion.toggle(card, body, open);
            caret.setText(open ? "▾" : "▸");
            if (image) imageOpen = open;
            else textOpen = open;
            savePrefs();
        });
        return card;
    }

    private void paintSizes() {
        String[] labels = new String[SIZES.length];
        int selected = 0;
        for (int i = 0; i < SIZES.length; i++) {
            labels[i] = SIZES[i][1];
            if (SIZES[i][0].equals(size)) selected = i;
        }
        setDropdown(sizeSpinner, labels, selected, position -> {
            size = SIZES[position][0];
            savePrefs();
        });
    }

    private void paintCounts() {
        String[] labels = new String[COUNTS.length];
        int selected = 0;
        for (int i = 0; i < COUNTS.length; i++) {
            labels[i] = COUNTS[i] + " 张";
            if (COUNTS[i] == count) selected = i;
        }
        setDropdown(countSpinner, labels, selected, position -> {
            count = COUNTS[position];
            if (!busy) generateButton.setText("生成 " + count + " 张");
            savePrefs();
        });
    }

    private void paintStyles() {
        String[] labels = new String[STYLES.length];
        for (int i = 0; i < STYLES.length; i++) {
            labels[i] = STYLES[i][0];
            if (i == STYLE_CUSTOM && customStyleText.length() > 0) {
                labels[i] = "自定义 · " + shorten(customStyleText, 12);
            }
        }
        int selected = style >= 0 && style < STYLES.length ? style : 0;
        setDropdown(styleSpinner, labels, selected, position -> {
            if (position == STYLE_CUSTOM) {
                // Let the Spinner finish closing its popup before opening an editable dialog.
                styleSpinner.post(this::showCustomStyleDialog);
            } else {
                style = position;
                savePrefs();
            }
        });
    }


    private void paintTextModels() {
        if (textModelSpinner == null) return;
        java.util.ArrayList<String> visible = new java.util.ArrayList<String>();
        if (textModels != null) visible.addAll(textModels);
        if (textModel == null || textModel.trim().isEmpty()) textModel = GatewayClient.DEFAULT_TEXT_MODEL;
        if (!visible.contains(textModel)) visible.add(0, textModel);
        if (visible.isEmpty()) visible.add(GatewayClient.DEFAULT_TEXT_MODEL);
        String[] labels = visible.toArray(new String[0]);
        int selected = Math.max(0, visible.indexOf(textModel));
        setDropdown(textModelSpinner, labels, selected, position -> {
            textModel = labels[position];
            if (textModelStatus != null) textModelStatus.setText("当前模型：" + textModel + " · 点设置里的保存后记住");
        });
    }

    private void refreshTextModels() {
        final String base = textEndpoint();
        final String key = textApiKey();
        if (base.length() == 0 || key.length() == 0) {
            if (textModelStatus != null) textModelStatus.setText("先填写文字接口地址和密钥，再刷新模型");
            return;
        }
        if (refreshTextModelsButton != null) {
            refreshTextModelsButton.setEnabled(false);
            refreshTextModelsButton.setText("正在获取…");
        }
        if (textModelStatus != null) textModelStatus.setText("正在请求 /v1/models…");
        final int timeout = readTimeout();
        new Thread(() -> {
            try {
                java.util.List<String> found = new GatewayClient(base, key, timeout).listModels();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    textModels.clear();
                    textModels.addAll(found);
                    paintTextModels();
                    boolean available = textModels.contains(textModel);
                    if (textModelStatus != null) textModelStatus.setText(available
                            ? "已获取 " + textModels.size() + " 个文字模型 · 当前：" + textModel
                            : "已获取 " + textModels.size() + " 个模型，但当前模型未返回；未自动替换");
                    if (refreshTextModelsButton != null) {
                        refreshTextModelsButton.setEnabled(true);
                        refreshTextModelsButton.setText("从 /v1/models 刷新");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    if (textModelStatus != null) textModelStatus.setText("获取失败：" + shortError(e));
                    if (refreshTextModelsButton != null) {
                        refreshTextModelsButton.setEnabled(true);
                        refreshTextModelsButton.setText("从 /v1/models 刷新");
                    }
                });
            }
        }, "text-models").start();
    }

    private static String shortError(Exception e) {
        String message = e == null || e.getMessage() == null ? "未知错误" : e.getMessage();
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }

    private void paintQuality() {
        final String[] values = new String[]{"standard", "high", "ultra"};
        final String[] labels = new String[]{"标准", "精细", "极致"};
        int selected = 1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(quality)) selected = i;
        setDropdown(qualitySpinner, labels, selected, position -> {
            quality = values[position];
            savePrefs();
        });
    }

    private void paintBatchPromptLevel() {
        if (batchPromptSeek == null) return;
        batchPromptLevel = BatchPromptPolicy.clamp(batchPromptLevel);
        batchPromptSeek.setProgress(batchPromptLevel);
        batchPromptValue.setText(BatchPromptPolicy.displayValue(batchPromptLevel));
        batchPromptSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                batchPromptLevel = BatchPromptPolicy.clamp(value);
                batchPromptValue.setText(BatchPromptPolicy.displayValue(batchPromptLevel));
                savePrefs();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private interface DropdownChoice { void onChoice(int position); }
    private boolean customStyleDialogOpen;
    private void bindDropdowns() { }
    private Spinner dropdown() {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setMinimumHeight(dp(48));
        spinner.setBackground(inputBackground());
        spinner.setPadding(dp(10), dp(4), dp(10), dp(4));
        return spinner;
    }
    private void setDropdown(Spinner spinner, String[] labels, int selected, DropdownChoice choice) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFF1A1C19);
                view.setTextSize(15);
                view.setPadding(dp(6), dp(8), dp(6), dp(8));
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(0xFF1A1C19);
                view.setTextSize(15);
                view.setPadding(dp(14), dp(12), dp(14), dp(12));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Every rebind gets its own token. A delayed callback from an older
        // rebind must not attach an old choice handler to this Spinner.
        final Object bindingToken = new Object();
        spinner.setTag(bindingToken);
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selected, labels.length - 1)), false);
        final boolean[] armed = new boolean[]{false};
        final AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!armed[0]) return;
                choice.onChoice(position);
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        spinner.post(() -> {
            if (spinner.getTag() != bindingToken) return;
            spinner.setOnItemSelectedListener(listener);
            spinner.post(() -> {
                if (spinner.getTag() == bindingToken) armed[0] = true;
            });
        });
    }

    private void showCustomStyleDialog() {
        if (customStyleDialogOpen || isFinishing()) return;
        customStyleDialogOpen = true;
        final int previousStyle = style;
        final String previousText = customStyleText;
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setText(customStyleText);
        input.setSelection(input.length());
        input.setHint("例如：赛博朋克，霓虹雨夜");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        final boolean[] settled = new boolean[]{false};
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义风格")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("使用", null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> customStyleDialogOpen = false);
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            input.requestFocus();
            input.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && input.isShown()) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }, 120);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (settled[0]) return;
                settled[0] = true;
                style = previousStyle;
                customStyleText = previousText;
                paintStyles();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (settled[0]) return;
                String value = input.getText().toString().trim();
                if (value.length() == 0) {
                    input.setError("请输入自定义风格，或点击取消");
                    input.requestFocus();
                    return;
                }
                settled[0] = true;
                customStyleText = value;
                style = STYLE_CUSTOM;
                paintStyles();
                savePrefs();
                dialog.dismiss();
                toast("已使用自定义风格");
            });
        });
        dialog.setOnCancelListener(d -> {
            if (settled[0]) return;
            settled[0] = true;
            style = previousStyle;
            customStyleText = previousText;
            paintStyles();
        });
        dialog.show();
    }

    private String styleTag() {
        if (style == STYLE_CUSTOM) return customStyleText;
        if (style < 0 || style >= STYLES.length) return "";
        return STYLES[style][1];
    }

    private String styleNote() {
        if (style == STYLE_CUSTOM) return customStyleText;
        if (style < 0 || style >= STYLES.length) return "";
        return STYLES[style][2];
    }

    private String composedPrompt() {
        String prompt = promptField.getText().toString().trim();
        String tag = styleTag();
        if (tag.length() == 0 || prompt.length() == 0) return prompt;
        if (prompt.contains(tag)) return prompt;
        return prompt + "。" + tag;
    }

    private void enhancePrompt() {
        String idea = promptField.getText().toString().trim();
        if (idea.isEmpty()) { toast("先写一句想画的内容"); return; }
        runTextTask("enhance", idea);
    }

    private void showIdeaInput() {
        if (busy) return;
        final EditText input = field("例如：做手机壁纸，想要温暖、安静一点", false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3); input.setText(wishDraft);
        new AlertDialog.Builder(this).setTitle("今天想生成什么？")
                .setMessage("可以告诉我主题、心情和用途。留空也能给你三个惊喜建议。只调用文字接口。")
                .setView(input).setNegativeButton("取消", null)
                .setPositiveButton("给我三个建议", (d,w) -> {
                    wishDraft = input.getText().toString();
                    savePrefs(); runTextTask("ideas", wishDraft);
                }).show();
    }

    private void showLongTextInput() {
        if (busy) return;
        LinearLayout body = vertical(); body.setPadding(dp(16),dp(8),dp(16),dp(8));
        EditText input = field("粘贴文章、故事、产品说明或一段笔记…", false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setGravity(Gravity.TOP); input.setMinLines(7); input.setMaxLines(12);
        input.setText(longDraft);
        body.addView(input);
        body.addView(hint("最多 30,000 字符。提交后原文会发到你配置的文字接口；提炼为一个代表性画面，确认后再生图。"));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("长文 → 生图提示词")
                .setView(body).setNegativeButton("保留草稿", (d,w)->{
                    longDraft=input.getText().toString(); savePrefs();
                }).setPositiveButton("提炼画面", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String source=input.getText().toString();
            if(source.trim().isEmpty()) { input.setError("先粘贴原文"); return; }
            if(source.length()>30000) { input.setError("超过 30,000 字符，请先拆分"); return; }
            longDraft=source; savePrefs(); dialog.dismiss(); runTextTask("summary",source);
        }));
        dialog.show();
    }

    private void runTextTask(final String kind, final String input) {
        if (busy) return;
        final String base=textEndpoint(), key=textApiKey(), note=styleNote(), model=textModel;
        if(base.isEmpty() || key.isEmpty()) { toast("先填写文字接口地址和密钥"); openFold(false); return; }
        if(model == null || model.trim().isEmpty()) { toast("先在文字接口设置里选择模型"); openFold(false); return; }
        final int timeout=readTimeout();
        savePrefs(); enhancing=true;
        textTaskName=kind.equals("ideas")?"正在寻找灵感…":kind.equals("summary")?"正在提炼画面…":"正在增强提示词…";
        setBusy(true,textTaskName);
        textTask = new AsyncTask<Void,Void,Result>() {
            @Override protected Result doInBackground(Void... args) {
                try {
                    GatewayClient client=new GatewayClient(base,key,timeout);
                    String reply=kind.equals("ideas")?client.suggestIdeas(input,note,model)
                            :kind.equals("summary")?client.summarizeForImage(input,note,model):client.enhancePrompt(input,note,model);
                    return Result.text(reply);
                } catch(GatewayClient.ApiException e) { return Result.fail(explain(e)); }
                catch(Exception e) { return Result.fail("文字处理失败："+e.getMessage()); }
            }
            @Override protected void onPostExecute(Result result) {
                if (destroyed || isFinishing()) return;
                enhancing=false;
                setBusy(false,result.expanded==null?result.message:"文字处理完成，请确认画面");
                if(result.expanded==null) return;
                if(kind.equals("ideas")) showSuggestions(result.expanded);
                else if(kind.equals("summary")) previewPrompt("提炼后的画面",result.expanded);
                else {
                    replacePrompt(result.expanded);
                    statusView.setText("提示词已增强 · 可撤回上一步或恢复最初");
                }
            }
        };
        textTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showSuggestions(String raw) {
        java.util.ArrayList<String> titles=new java.util.ArrayList<>(), prompts=new java.util.ArrayList<>();
        try {
            String cleaned = raw == null ? "" : raw.trim();
            if (cleaned.startsWith("```")) {
                int firstLine = cleaned.indexOf('\n');
                int lastFence = cleaned.lastIndexOf("```");
                if (firstLine >= 0 && lastFence > firstLine) cleaned = cleaned.substring(firstLine + 1, lastFence).trim();
            }
            int first=cleaned.indexOf('['), last=cleaned.lastIndexOf(']');
            org.json.JSONArray array = null;
            if (first >= 0 && last > first) {
                array = new org.json.JSONArray(cleaned.substring(first,last+1));
            } else if (cleaned.startsWith("{")) {
                org.json.JSONObject wrapper = new org.json.JSONObject(cleaned);
                array = wrapper.optJSONArray("ideas");
                if (array == null) array = wrapper.optJSONArray("suggestions");
            }
            if (array == null) throw new Exception("no ideas array");
            for(int i=0;i<Math.min(3,array.length());i++) {
                org.json.JSONObject item=array.optJSONObject(i);
                if(item==null) continue;
                String prompt=item.optString("prompt", "").trim();
                if(prompt.isEmpty()) continue;
                String title=item.optString("title", "灵感 "+(i+1)).trim();
                if(title.length() > 16) title=title.substring(0,16);
                titles.add(title); prompts.add(prompt);
            }
        } catch(Exception ignored) { }
        if(prompts.isEmpty()) {
            // Do not mistake an unstructured assistant explanation for an image prompt.
            new AlertDialog.Builder(this).setTitle("AI 建议（未按选项格式返回）")
                    .setMessage(raw).setPositiveButton("关闭", null).show();
            statusView.setText("建议格式不规范，未覆盖提示词；可重新请求");
            return;
        }
        new AlertDialog.Builder(this).setTitle("今天试试这几个画面")
                .setItems(titles.toArray(new String[0]),(d,index)->previewPrompt(titles.get(index),prompts.get(index)))
                .setNegativeButton("暂不选择",null).show();
    }

    private void previewPrompt(String title,String value) {
        final EditText preview=field("可在这里调整", false);
        preview.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        preview.setText(value); preview.setMinLines(5); preview.setMaxLines(10);
        new AlertDialog.Builder(this).setTitle(title).setView(preview)
                .setNegativeButton("取消",null).setPositiveButton("用这个画面",(d,w)->{
                    String result=preview.getText().toString().trim();
                    if(result.isEmpty()) { toast("没有替换：内容为空"); return; }
                    replacePrompt(result);
                    statusView.setText("已填入画面，确认后点生成；未自动生图");
                }).show();
    }

    private void replacePrompt(String value) {
        promptHistory.beforeReplace(promptField.getText().toString(),value);
        promptField.setText(value);
        refreshHistoryButtons(); savePrefs(); Motion.appear(promptField);
    }
    private void restorePrompt(boolean original) {
        if(busy || !promptHistory.canUndo()) return;
        new AlertDialog.Builder(this).setTitle(original?"恢复本轮最初内容？":"撤回上一步 AI 替换？")
                .setMessage("将替换当前输入框内容，包括你刚做的手动修改。")
                .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{
                    String value=original?promptHistory.restoreOriginal():promptHistory.undo();
                    if(value!=null) promptField.setText(value);
                    refreshHistoryButtons(); savePrefs();
                    statusView.setText("已恢复提示词"); Motion.appear(promptField);
                }).show();
    }
    private void refreshHistoryButtons() {
        if(undoButton==null) return;
        boolean enabled=!busy && promptHistory.canUndo();
        undoButton.setEnabled(enabled); originalButton.setEnabled(enabled);
        undoButton.setAlpha(enabled?1f:0.4f); originalButton.setAlpha(enabled?1f:0.4f);
    }

    private int readTimeout() {
        try {
            int value = Integer.parseInt(timeoutField.getText().toString().trim());
            if (value < 30) return 30;
            return Math.min(value, 600);
        } catch (Exception ignored) {
            return 360;
        }
    }

    private String textEndpoint() {
        String text = textBaseField.getText().toString().trim();
        if (text.length() > 0) return text;
        return imageBaseField.getText().toString().trim();
    }

    private String textApiKey() {
        String key = textKeyField.getText().toString().trim();
        if (key.length() > 0) return key;
        return imageKeyField.getText().toString().trim();
    }

    private void startJob() {
        if (busy) return;
        final String prompt = composedPrompt();
        if (prompt.length() == 0) {
            toast("先写提示词");
            return;
        }
        final String base = imageBaseField.getText().toString().trim();
        final String key = imageKeyField.getText().toString().trim();
        if (base.length() == 0 || key.length() == 0) {
            toast("先填写生图地址和密钥");
            openFold(true);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT>=23 && android.os.Build.VERSION.SDK_INT<29
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},72);
            toast("允许相册写入后请再点生成；原图仍会保留在应用内");
            return;
        }
        final int timeoutSeconds = readTimeout();
        savePrefs();
        Intent job = new Intent(this, JobService.class);
        job.putExtra(JobService.EXTRA_BASE, base);
        job.putExtra(JobService.EXTRA_KEY, key);
        job.putExtra(JobService.EXTRA_PROMPT, prompt);
        job.putExtra(JobService.EXTRA_SIZE, size);
        job.putExtra(JobService.EXTRA_QUALITY, quality);
        job.putExtra(JobService.EXTRA_BATCH_PROMPT_LEVEL, batchPromptLevel);
        job.putExtra(JobService.EXTRA_COUNT, count);
        job.putExtra(JobService.EXTRA_TIMEOUT, timeoutSeconds);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 71);
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(job);
            else startService(job);
            jobStarting = true;
            jobStartingAt = System.currentTimeMillis();
            setBusy(true, "任务正在启动…");
        } catch (Exception e) {
            setBusy(false, "任务启动失败：" + e.getMessage());
        }
    }

    private void syncJob() {
        SharedPreferences job = getSharedPreferences(JobService.STATE, MODE_PRIVATE);
        if (!job.contains("message")) return;
        boolean active = JobService.isRunning();
        boolean stateActive = job.getBoolean("active", false);
        long stateUpdated = job.getLong("updated", 0L);
        if (jobStarting && (active || stateActive || stateUpdated >= jobStartingAt)) jobStarting = false;
        if (jobStarting && System.currentTimeMillis() - jobStartingAt < 10000L) {
            if (!enhancing) setBusy(true, "任务正在启动…");
            return;
        }
        if (jobStarting && !active && !stateActive) {
            jobStarting = false;
            if (!enhancing) setBusy(false, "任务启动失败，请检查通知权限和后台运行权限");
            return;
        }
        if (stateActive && !active) {
            job.edit().putBoolean("active", false).putString("message",
                    "上次任务已中断；已保存图片保留，不自动重发以免重复消耗额度").apply();
        }
        String id = job.getString("entry", "");
        if (!id.equals(observedEntry)) {
            observedEntry = id;
            pendingEntry = id;
            renderGallery();
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

    @Override protected void onResume() {
        super.onResume();
        if(refreshLabel!=null) refreshLabel.setText(Motion.refresh(this,highRefresh));
    }
    @Override protected void onDestroy() {
        destroyed=true;
        jobHandler.removeCallbacksAndMessages(null);
        if(textTask!=null) textTask.cancel(true);
        galleryWorker.shutdownNow();
        if(previews!=null) previews.close();
        super.onDestroy();
    }

    private void showEntry(GalleryStore.Entry entry) {
        selectedEntry=entry;
        boolean first=resultBlock.getVisibility()!=View.VISIBLE;
        resultBlock.setVisibility(View.VISIBLE);
        if(first) Motion.appear(resultBlock);
        resultMeta.setText(new SimpleDateFormat("HH:mm",Locale.US).format(new Date(entry.createdAt)));
        previews.load(resultView,gallery.fileOf(entry),1600,true);
    }

    private void renderGallery() {
        if(destroyed) return;
        final int version=++galleryVersion;
        galleryWorker.execute(()->{
            List<GalleryStore.Entry> entries=gallery.list();
            runOnUiThread(()->{
                if(destroyed || version!=galleryVersion) return;
                visibleEntries.clear(); visibleEntries.addAll(entries);
                if(!pendingEntry.isEmpty()) {
                    for(GalleryStore.Entry e:entries) if(e.id.equals(pendingEntry)) { showEntry(e); break; }
                    pendingEntry="";
                }
                StringBuilder signature=new StringBuilder().append(galleryLimit).append('|').append(galleryExpanded).append('|').append(getResources().getDisplayMetrics().widthPixels);
                for(GalleryStore.Entry e:entries) signature.append('|').append(e.id);
                if(signature.toString().equals(gallerySignature)) return;
                gallerySignature=signature.toString();
                bindGallery(entries);
            });
        });
    }
    private void bindGallery(List<GalleryStore.Entry> entries) {
        galleryGrid.removeAllViews();
        if (galleryToggle != null) galleryToggle.setText(galleryExpanded ? "收起" : "展开");
        if(entries.isEmpty()) { galleryGrid.addView(text("还没有图 · 生成后自动存入系统相册「绘境」",13,0xFF6A675F)); return; }
        int shown = Math.min(entries.size(), galleryLimit);
        if (!galleryExpanded) {
            LinearLayout row = chipRow();
            galleryGrid.addView(row, new LinearLayout.LayoutParams(-2, -2));
            int side = dp(108);
            for (int i = 0; i < shown; i++) row.addView(galleryThumb(entries.get(i), side), new LinearLayout.LayoutParams(side, side));
            return;
        }
        setGridColumns(galleryGrid,3);
        int side=Math.max(dp(64),(getResources().getDisplayMetrics().widthPixels-dp(64))/3);
        for(int i=0;i<shown;i++) {
            ImageView thumb=galleryThumb(entries.get(i), side);
            addToGrid(galleryGrid,thumb);
            ViewGroup.LayoutParams lp=thumb.getLayoutParams(); lp.height=side; thumb.setLayoutParams(lp);
        }
        padGrid(galleryGrid);
        if(entries.size()>galleryLimit) {
            Button more=button("再显示24张 · 共"+entries.size()+"张",false);
            more.setOnClickListener(v->{galleryLimit+=24;renderGallery();});galleryGrid.addView(more);
        }
    }

    private void openViewer(GalleryStore.Entry entry) {
        showEntry(entry);
        java.util.ArrayList<String> paths = new java.util.ArrayList<String>();
        java.util.ArrayList<String> prompts = new java.util.ArrayList<String>();
        int index = 0;
        for (int i = 0; i < visibleEntries.size(); i++) {
            GalleryStore.Entry item = visibleEntries.get(i);
            if (item.id.equals(entry.id)) index = paths.size();
            paths.add(gallery.fileOf(item).getAbsolutePath());
            prompts.add(item.prompt == null ? "" : item.prompt);
        }
        Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("paths", paths.toArray(new String[0]));
        intent.putExtra("prompts", prompts.toArray(new String[0]));
        intent.putExtra("index", index);
        intent.putExtra("path", gallery.fileOf(entry).getAbsolutePath());
        intent.putExtra("prompt", entry.prompt);
        startActivity(intent);
    }

    private ImageView galleryThumb(GalleryStore.Entry entry, int side) {
        ImageView thumb=new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFE7E2D6); bg.setCornerRadius(dp(12));
        thumb.setBackground(bg); thumb.setClipToOutline(true);
        thumb.setContentDescription("查看图片："+entry.prompt);
        thumb.setOnClickListener(v->openViewer(entry));
        thumb.setOnLongClickListener(v->{confirmDelete(entry);return true;});
        previews.load(thumb,gallery.fileOf(entry),side,false);
        return thumb;
    }

    private static int sampleOf(int width, int target) {
        int sample = 1;
        while (width / (sample * 2) >= target && sample < 16) sample *= 2;
        return sample;
    }

    private void confirmDelete(final GalleryStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle("从应用画册删除？")
                .setMessage("只删除应用内副本，系统相册「绘境」里的图片不会删除。")
                .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                    galleryWorker.execute(()->{
                        try {
                            gallery.delete(entry.id);
                            runOnUiThread(()->{
                                if(destroyed) return;
                                if(selectedEntry!=null && selectedEntry.id.equals(entry.id)) {
                                    selectedEntry=null;resultView.setTag(null);resultView.setImageDrawable(null);resultBlock.setVisibility(View.GONE);
                                }
                                renderGallery(); toast("已从应用画册删除，相册副本保留");
                            });
                        } catch(Exception e) { runOnUiThread(()->toast("删除失败："+e.getMessage())); }
                    });
                }).show();
    }

    private void exportLatest() {
        GalleryStore.Entry entry = selectedEntry;
        if (entry == null) {
            toast("还没有可以保存的图");
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 72);
            toast("授权后请再点一次保存");
            return;
        }
        new Thread(() -> {
            try {
                Album.save(this, gallery.fileOf(entry), "inkbench-" + entry.id + ".png");
                runOnUiThread(() -> toast("已保存到系统相册 · Pictures/绘境"));
            } catch(Exception e) {
                runOnUiThread(() -> toast("保存失败：" + e.getMessage()));
            }
        }, "inkbench-export").start();
    }

    private void shareLatest() {
        GalleryStore.Entry entry = selectedEntry;
        if (entry == null) {
            toast("还没有可以分享的图");
            return;
        }
        try {
            File src = gallery.fileOf(entry);
            File dest = new File(getExternalCacheDir() == null ? getCacheDir() : getExternalCacheDir(), src.getName());
            copyFile(src, dest);
            Uri uri = ShareProvider.uriFor(this, dest);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享图片"));
        } catch (Exception e) {
            toast("分享失败：" + e.getMessage());
        }
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        promptField.setEnabled(!value);
        ideaButton.setEnabled(!value); longTextButton.setEnabled(!value);
        refreshHistoryButtons();
        if (styleSpinner != null) styleSpinner.setEnabled(!value);
        if (sizeSpinner != null) sizeSpinner.setEnabled(!value);
        if (countSpinner != null) countSpinner.setEnabled(!value);
        if (qualitySpinner != null) qualitySpinner.setEnabled(!value);
        if (batchPromptSeek != null) batchPromptSeek.setEnabled(!value);
        generateButton.setEnabled(!value);
        enhanceButton.setEnabled(!value);
        generateButton.setText(value && !enhancing ? "生成中…" : "生成 " + count + " 张");
        enhanceButton.setText(enhancing ? "AI 处理中…" : "增强提示词");
        stopButton.setVisibility(value && !enhancing ? View.VISIBLE : View.GONE);
        if (!value) stopButton.setEnabled(true);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        statusView.setText(message);
    }

    private void setControlsEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) setControlsEnabled(group.getChildAt(i), enabled);
        }
    }

    private void openFold(boolean image) {
        showSettings();
    }

    private void showSettings() {
        // Settings are previewed live, but Cancel must restore the settings baseline.
        final String oldImageBase = imageBaseField.getText().toString();
        final String oldImageKey = imageKeyField.getText().toString();
        final String oldTextBase = textBaseField.getText().toString();
        final String oldTextKey = textKeyField.getText().toString();
        final String oldTextModel = textModel;
        final String oldTimeout = timeoutField.getText().toString();
        final String oldWallpaperUri = wallpaperUri;
        final int oldShade = shadePercent;
        final int oldGlass = glassPercent;
        final int oldBlock = blockPercent;
        final int oldBlur = blurAmount;
        final boolean oldHighRefresh = highRefresh;
        final java.util.ArrayList<String> oldTextModels = new java.util.ArrayList<String>(textModels);
        final LinearLayout content = vertical();
        content.setPadding(dp(16), dp(6), dp(16), dp(8));
        content.addView(text("接口", 17, 0xFF1A1C19));
        content.addView(gap(6));
        content.addView(cardWithContent("生图接口", imageBody));
        content.addView(gap(8));
        content.addView(cardWithContent("文字增强接口", textBody));

        content.addView(gap(16));
        content.addView(text("外观", 17, 0xFF1A1C19));
        content.addView(gap(6));
        LinearLayout appearance = card();
        TextView wallpaperName = text(wallpaperUri.isEmpty() ? "默认山水背景" : "自选背景图片", 14, 0xFF1A1C19);
        appearance.addView(wallpaperName);
        appearance.addView(hint("壁纸仅保存在本机；优先选择柔和、低细节的图片。"));
        LinearLayout wallpaperActions = chipRow();
        Button pick = button("选择背景图", false);
        Button reset = button("恢复默认", false);
        wallpaperActions.addView(pick, weight()); wallpaperActions.addView(gap(8)); wallpaperActions.addView(reset, weight());
        appearance.addView(gap(8)); appearance.addView(wallpaperActions);
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, 84);
        });
        reset.setOnClickListener(v -> {
            wallpaperUri = ""; wallpaperName.setText("默认山水背景");
            loadWallpaper();
        });

        TextView shadeLabel = text("遮罩浓度：" + shadePercent + "%", 13, 0xFF6A675F);
        SeekBar shadeSeek = settingSeek(appearance, shadeLabel, shadePercent, 65, value -> {
            shadePercent = value; applyAppearance();
        });
        TextView glassLabel = text("卡片透明度：" + glassPercent + "%", 13, 0xFF6A675F);
        SeekBar glassSeek = settingSeek(appearance, glassLabel, glassPercent, 100, value -> {
            glassPercent = value; applyGlass();
        });
        TextView blockLabel = text("卡片内方块透明度：" + blockPercent + "%", 13, 0xFF6A675F);
        SeekBar blockSeek = settingSeek(appearance, blockLabel, blockPercent, 100, value -> {
            blockPercent = value; applyGlass();
        });
        TextView blurLabel = text("背景柔焦：" + blurAmount, 13, 0xFF6A675F);
        SeekBar blurSeek = settingSeek(appearance, blurLabel, blurAmount, 18, value -> {
            if (blurAmount != value) {
                blurAmount = value;
                if (Build.VERSION.SDK_INT >= 31) applyWallpaperBlur();
                else scheduleWallpaperReload();
            }
        });

        refreshSwitch = new android.widget.Switch(this);
        refreshSwitch.setText("优先高刷新率（最高 120Hz）");
        refreshSwitch.setTextColor(0xFF1A1C19);
        refreshSwitch.setTextSize(14);
        refreshSwitch.setChecked(highRefresh);
        refreshLabel = hint(Motion.refresh(this, highRefresh));
        appearance.addView(gap(8)); appearance.addView(refreshSwitch); appearance.addView(refreshLabel);
        refreshSwitch.setOnCheckedChangeListener((v, on) -> {
            highRefresh = on;
            refreshLabel.setText(Motion.refresh(this, on));
        });
        content.addView(appearance);
        applyGlass();

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.addView(content);
        final Runnable restoreSettings = () -> {
            imageBaseField.setText(oldImageBase);
            imageKeyField.setText(oldImageKey);
            textBaseField.setText(oldTextBase);
            textKeyField.setText(oldTextKey);
            textModel = oldTextModel;
            textModels.clear();
            textModels.addAll(oldTextModels);
            paintTextModels();
            timeoutField.setText(oldTimeout);
            wallpaperUri = oldWallpaperUri;
            shadePercent = oldShade;
            glassPercent = oldGlass;
            blockPercent = oldBlock;
            blurAmount = oldBlur;
            highRefresh = oldHighRefresh;
            if (refreshSwitch != null) refreshSwitch.setChecked(oldHighRefresh);
            if (refreshLabel != null) refreshLabel.setText(Motion.refresh(this, oldHighRefresh));
            refreshSummaries();
            applyAppearance();
            loadWallpaper();
        };
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("设置")
                .setView(scroller).setNegativeButton("取消", (d,w) -> restoreSettings.run())
                .setPositiveButton("保存", (d,w) -> { savePrefs(); applyAppearance(); toast("设置已保存"); })
                .create();
        dialog.setOnCancelListener(d -> restoreSettings.run());
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) window.setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int)(getResources().getDisplayMetrics().heightPixels * 0.86f));
            if (textEndpoint().length() > 0 && textApiKey().length() > 0 && textModels.isEmpty()) refreshTextModels();
        });
        dialog.show();
    }

    private interface SeekChange { void onChange(int value); }
    private SeekBar settingSeek(LinearLayout parent, TextView label, int progressValue, int max, SeekChange change) {
        parent.addView(gap(10)); parent.addView(label);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max); seek.setProgress(progressValue);
        parent.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int shown = progress;

                label.setText(label.getText().toString().split("：")[0] + "：" + shown + (label.getText().toString().startsWith("背景柔焦") ? "" : "%"));
                if (fromUser) change.onChange(progress);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        return seek;
    }

    private LinearLayout cardWithContent(String title, View body) {
        LinearLayout wrap = card();
        wrap.addView(text(title, 15, 0xFF1A1C19));
        body.setVisibility(View.VISIBLE);
        body.setPadding(0, dp(4), 0, 0);
        if (body.getParent() instanceof ViewGroup) ((ViewGroup) body.getParent()).removeView(body);
        wrap.addView(body);
        return wrap;
    }

    @Override public void onBackPressed() {
        finish();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 84 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
        wallpaperUri = uri.toString();
        loadWallpaper();
        toast("背景图已应用");
    }

    private void scheduleWallpaperReload() {
        if (wallpaperView == null) return;
        wallpaperView.removeCallbacks(wallpaperReloadTask);
        wallpaperView.postDelayed(wallpaperReloadTask, 180);
    }

    private final Runnable wallpaperReloadTask = new Runnable() {
        @Override public void run() { loadWallpaper(); }
    };

    private void loadWallpaper() {
        final int token = ++wallpaperLoadToken;
        final String uriText = wallpaperUri;
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                if (uriText.isEmpty()) {
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_default, opts);
                } else {
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream in = getContentResolver().openInputStream(Uri.parse(uriText))) { BitmapFactory.decodeStream(in, null, bounds); }
                    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                    int sample = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2;
                    opts.inSampleSize = sample;
                    try (InputStream in = getContentResolver().openInputStream(Uri.parse(uriText))) { bitmap = BitmapFactory.decodeStream(in, null, opts); }
                }
                if (bitmap != null && blurAmount > 0) {
                    bitmap = soften(bitmap, blurAmount);
                }
            } catch (Exception ignored) { }
            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (destroyed || token != wallpaperLoadToken || result == null) return;
                wallpaperView.setImageBitmap(result);
                applyWallpaperBlur();
            });
        }, "appearance-wallpaper").start();
    }

    private void applyWallpaperBlur() {
        // Portable API-21 path. Do not reference RenderEffect on a minSdk 21 app.
    }

    private Bitmap soften(Bitmap source, int strength) {
        if (strength <= 0) return source;
        int w = source.getWidth(), h = source.getHeight();
        int[] src = new int[w * h];
        int[] tmp = new int[w * h];
        int[] dst = new int[w * h];
        source.getPixels(src, 0, w, 0, 0, w, h);
        int radius = Math.max(1, Math.min(9, strength / 2));
        int window = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int a=0, r=0, g=0, b=0;
            for (int x=-radius; x<=radius; x++) {
                int c=src[y*w+Math.max(0,Math.min(w-1,x))]; a+=Color.alpha(c); r+=Color.red(c); g+=Color.green(c); b+=Color.blue(c);
            }
            for (int x=0; x<w; x++) {
                tmp[y*w+x]=Color.argb(a/window,r/window,g/window,b/window);
                int remove=src[y*w+Math.max(0,x-radius)];
                int add=src[y*w+Math.min(w-1,x+radius+1)];
                a+=Color.alpha(add)-Color.alpha(remove); r+=Color.red(add)-Color.red(remove);
                g+=Color.green(add)-Color.green(remove); b+=Color.blue(add)-Color.blue(remove);
            }
        }
        for (int x=0; x<w; x++) {
            int a=0, r=0, g=0, b=0;
            for (int y=-radius; y<=radius; y++) {
                int c=tmp[Math.max(0,Math.min(h-1,y))*w+x]; a+=Color.alpha(c); r+=Color.red(c); g+=Color.green(c); b+=Color.blue(c);
            }
            for (int y=0; y<h; y++) {
                dst[y*w+x]=Color.argb(a/window,r/window,g/window,b/window);
                int remove=tmp[Math.max(0,y-radius)*w+x];
                int add=tmp[Math.min(h-1,y+radius+1)*w+x];
                a+=Color.alpha(add)-Color.alpha(remove); r+=Color.red(add)-Color.red(remove);
                g+=Color.green(add)-Color.green(remove); b+=Color.blue(add)-Color.blue(remove);
            }
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        out.setPixels(dst,0,w,0,0,w,h);
        if(source!=out) source.recycle();
        return out;
    }

    private void applyAppearance() {
        if (wallpaperShade != null) wallpaperShade.setBackgroundColor(Color.argb(Math.min(190, shadePercent * 255 / 100), 12, 23, 25));
        applyGlass();
    }

    private void applyGlass() {
        int alpha = Math.max(0, Math.min(255, glassPercent * 255 / 100));
        java.util.Iterator<java.lang.ref.WeakReference<LinearLayout>> it=glassCards.iterator();
        while(it.hasNext()) {
            LinearLayout item=it.next().get();
            if(item==null) { it.remove(); continue; }
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(alpha, 255, 252, 247));
            bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), 0x55FFFFFF); item.setBackground(bg);
        }
        refreshBlockBackgrounds(appShell);
    }

    private void refreshBlockBackgrounds(View view) {
        if (view == null) return;
        if (view instanceof EditText) ((EditText) view).setBackground(inputBackground());
        else if (view instanceof Spinner) ((Spinner) view).setBackground(inputBackground());
        else if (view instanceof Button) {
            Button button = (Button) view;
            boolean primary = button.getCurrentTextColor() == 0xFFF7F4EC;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            int alpha = Math.max(0, Math.min(255, blockPercent * 255 / 100));
            bg.setColor(primary ? Color.argb(alpha, 31, 107, 74) : Color.argb(alpha, 247, 244, 236));
            if (!primary) bg.setStroke(dp(1), Color.argb(Math.min(255, alpha + 30), 217, 211, 199));
            button.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x221F6B4A), bg, null));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) refreshBlockBackgrounds(group.getChildAt(i));
        }
    }

    private void refreshSummaries() {
        imageSummary.setText(shortHost(imageBaseField.getText().toString().trim(), "未填生图地址"));
        String textBase = textBaseField.getText().toString().trim();
        textSummary.setText(textBase.length() == 0 ? "跟随生图地址" : shortHost(textBase, "未填"));
    }

    private static String shortHost(String url, String empty) {
        if (url.length() == 0) return empty;
        String bare = url.replace("https://", "").replace("http://", "");
        return bare.length() > 32 ? bare.substring(0, 32) + "…" : bare;
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String legacyBase = prefs.getString("base", "");
        String legacyKey = prefs.getString("key", "");
        String savedImageBase = prefs.getString("imageBase", legacyBase);
        if ("http://127.0.0.1:4141".equals(savedImageBase)) savedImageBase = "";
        imageBaseField.setText(savedImageBase);
        imageKeyField.setText(prefs.getString("imageKey", legacyKey));
        textBaseField.setText(prefs.getString("textBase", ""));
        textKeyField.setText(prefs.getString("textKey", ""));
        textModel = prefs.getString("textModel", GatewayClient.DEFAULT_TEXT_MODEL);
        timeoutField.setText(String.valueOf(prefs.getInt("timeout", 360)));
        promptField.setText(prefs.getString("draft", ""));
        size = prefs.getString("size", "1024x1024");
        quality = prefs.getString("quality", "high");
        batchPromptLevel = BatchPromptPolicy.clamp(
                prefs.getInt(BatchPromptPolicy.PREF_KEY, BatchPromptPolicy.DEFAULT_LEVEL));
        count = prefs.getInt("count", 1);
        style = prefs.getInt("style", 0);
        customStyleText = prefs.getString("customStyle", "");
        if (style < 0 || style >= STYLES.length) style = 0;
        if (style == STYLE_CUSTOM && customStyleText.length() == 0) style = 0;
        if (count < 1 || count > 4) count = 1;
        imageOpen = false;
        textOpen = false;
        paintSizes();
        paintCounts();
        paintStyles();
        paintTextModels();
        paintBatchPromptLevel();
        refreshSummaries();
        longDraft=prefs.getString("longDraft", "");
        wishDraft=prefs.getString("wishDraft", "");
        java.util.ArrayList<String> history=new java.util.ArrayList<>();
        try {
            org.json.JSONArray array=new org.json.JSONArray(prefs.getString("promptUndo","[]"));
            for(int i=0;i<array.length();i++) history.add(array.getString(i));
        } catch(Exception ignored) { }
        promptHistory.load(prefs.contains("promptOriginal")?prefs.getString("promptOriginal", ""):null,history);
        refreshHistoryButtons();
        highRefresh=prefs.getBoolean("highRefresh",true);
        shadePercent=prefs.getInt("shadePercent",22);
        glassPercent=prefs.getInt("glassPercent",88);
        blockPercent=prefs.getInt("blockPercent",88);
        galleryExpanded=prefs.getBoolean("galleryExpanded",false);
        blurAmount=prefs.getInt("blurAmount",8);
        wallpaperUri=prefs.getString("wallpaperUri","");
        applyAppearance();
        loadWallpaper();
    }

    private void savePrefs() {
        refreshSummaries();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("imageBase", imageBaseField.getText().toString().trim())
                .putString("imageKey", imageKeyField.getText().toString().trim())
                .putString("textBase", textBaseField.getText().toString().trim())
                .putString("textKey", textKeyField.getText().toString().trim())
                .putString("textModel", textModel)
                .putInt("timeout", readTimeout())
                .putString("size", size)
                .putString("quality", quality)
                .putInt(BatchPromptPolicy.PREF_KEY, BatchPromptPolicy.clamp(batchPromptLevel))
                .putString("draft", promptField.getText().toString())
                .putString("promptOriginal",promptHistory.original())
                .putString("promptUndo",new org.json.JSONArray(promptHistory.steps()).toString())
                .putString("longDraft",longDraft).putString("wishDraft",wishDraft)
                .putBoolean("highRefresh",highRefresh)
                .putInt("shadePercent",shadePercent)
                .putInt("glassPercent",glassPercent)
                .putInt("blockPercent",blockPercent)
                .putBoolean("galleryExpanded",galleryExpanded)
                .putInt("blurAmount",blurAmount)
                .putString("wallpaperUri",wallpaperUri)
                .putInt("count", count)
                .putInt("style", style)
                .putString("customStyle", customStyleText)
                .apply();
    }

    private static String explain(GatewayClient.ApiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (e.status == 401 || msg.toLowerCase(Locale.US).contains("api key")) {
            return "密钥无效。到对应网关新建一把再贴过来。";
        }
        if (e.status == 429 || "rate_limit_error".equals(e.type) || msg.toLowerCase(Locale.US).contains("quota")) {
            return "网关限流或额度受限（429）：" + msg;
        }
        if (msg.contains("no image") || msg.contains("upstream returned no image")) {
            return "上游没给出图片。常见原因是提示词被拒，或账号还没登录。";
        }
        if ("origin_bad_gateway".equalsIgnoreCase(e.type)
                || msg.contains("图片上游暂时不可用")
                || (e.status >= 502 && e.status <= 504)) {
            return "图片服务暂时拥堵，已自动退避重试；若仍失败，请稍后再生成。";
        }
        if (e.status == 0) return msg;
        return "网关返回 " + e.status + "：" + msg;
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream in = new java.io.FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout chipRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private HorizontalScrollView wrapScroll(View child) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(child);
        return scroll;
    }

    private LinearLayout grid(int columns) {
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

    private static String shorten(String value, int max) {
        String one = value.replace('\n', ' ').trim();
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 12, 0xFF6A675F);
        return view;
    }

    private TextView hint(String value) {
        TextView view = text(value, 12, 0xFF6A675F);
        view.setPadding(0, dp(6), 0, 0);
        return view;
    }

    private EditText field(String hintText, boolean secret) {
        EditText edit = new EditText(this);
        edit.setOnFocusChangeListener((view, focused) -> {
            if (focused && mainScroll != null && !imeWasVisible) scrollBeforeIme = mainScroll.getScrollY();
        });
        edit.setHint(hintText);
        edit.setTextSize(15);
        edit.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        edit.setTextColor(0xFF1A1C19);
        edit.setHintTextColor(0xFF9A968C);
        edit.setBackground(inputBackground());
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (secret) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return edit;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setMinimumHeight(0);
        button.setMinHeight(0);
        button.setTextSize(15);
        button.setTextColor(primary ? 0xFFF7F4EC : 0xFF1A1C19);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        int buttonAlpha = Math.max(0, Math.min(255, blockPercent * 255 / 100));
        bg.setColor(primary ? Color.argb(buttonAlpha, 31, 107, 74) : Color.argb(buttonAlpha, 247, 244, 236));
        if (!primary) bg.setStroke(dp(1), Color.argb(Math.min(255, buttonAlpha + 30), 217, 211, 199));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x221F6B4A), bg, null));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button chip(String label, boolean on) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setMinimumHeight(0);
        button.setMinHeight(0);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(42));
        button.setPadding(0, 0, 0, 0);
        button.setTextColor(on ? 0xFFF7F4EC : 0xFF1A1C19);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(on ? 0xFF1A1C19 : 0xFFF3F1EA);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x221F6B4A), bg, null));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6FFFCF7);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x66FFFFFF);
        card.setBackground(bg);
        glassCards.add(new java.lang.ref.WeakReference<>(card));
        return card;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable bg = new GradientDrawable();
        int alpha = Math.max(0, Math.min(255, blockPercent * 255 / 100));
        bg.setColor(Color.argb(alpha, 247, 244, 236));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.argb(Math.min(255, alpha + 24), 217, 211, 199));
        return bg;
    }

    private View gap(int value) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(value), dp(value)));
        return view;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Result {
        final GalleryStore.Entry entry;
        final String message;
        final String expanded;

        private Result(GalleryStore.Entry entry, String message, String expanded) {
            this.entry = entry;
            this.message = message;
            this.expanded = expanded;
        }

        static Result ok(GalleryStore.Entry entry, String message) {
            return new Result(entry, message, null);
        }

        static Result partial(GalleryStore.Entry entry, String message) {
            return new Result(entry, message, null);
        }

        static Result fail(String message) {
            return new Result(null, message, null);
        }

        static Result text(String expanded) {
            return new Result(null, "提示词已写回", expanded);
        }
    }
}
