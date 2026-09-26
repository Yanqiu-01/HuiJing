from pathlib import Path
p=Path('/workspace/inkbench/app/src/main/java/app/inkbench/studio/MainActivity.java');t=p.read_text()
t=t.replace('    private GalleryStore gallery;','''    private GalleryStore gallery;
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
''')
t=t.replace('        gallery = new GalleryStore(this);','        gallery = new GalleryStore(this);\n        previews = new PreviewLoader();')
t=t.replace('        page.addView(gap(10));\n        LinearLayout composer = card();','''        page.addView(gap(10));
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
        LinearLayout composer = card();''')
t=t.replace('        composer.addView(promptField);','''        composer.addView(promptField);
        LinearLayout historyActions = chipRow();
        undoButton = button("撤回上一步", false);
        originalButton = button("恢复最初", false);
        undoButton.setOnClickListener(v -> restorePrompt(false));
        originalButton.setOnClickListener(v -> restorePrompt(true));
        historyActions.addView(undoButton, weight());
        historyActions.addView(gap(8));
        historyActions.addView(originalButton, weight());
        composer.addView(gap(8));
        composer.addView(historyActions);''')
t=t.replace('        Button save = button("保存", false);','        Button save = button("补存相册", false);')
t=t.replace('        page.addView(galleryGrid);','''        page.addView(galleryGrid);
        page.addView(hint("新图自动保存到系统相册「墨台」。删除这里的图片不删除相册副本。旧图可选中后补存。"));
        page.addView(gap(16));
        LinearLayout displayCard = card();
        refreshSwitch = new android.widget.Switch(this);
        refreshSwitch.setText("优先高刷新率（最高 120Hz）");
        refreshSwitch.setTextColor(0xFF1A1C19);
        refreshSwitch.setTextSize(14);
        displayCard.addView(refreshSwitch);
        refreshLabel = hint("跟随系统调度，不强制切换屏幕分辨率。");
        displayCard.addView(refreshLabel);
        page.addView(displayCard);''')
# fold animation
old='            body.setVisibility(open ? View.VISIBLE : View.GONE);'
t=t.replace(old,'            Motion.toggle(card, body, open);')
# Replace enhance task with shared text tools
start=t.index('    private void enhancePrompt()');end=t.index('    private int readTimeout()',start)
t=t[:start]+'''    private void enhancePrompt() {
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
        final String base=textEndpoint(), key=textApiKey(), note=styleNote();
        if(base.isEmpty() || key.isEmpty()) { toast("先填写文字接口地址和密钥"); openFold(false); return; }
        final int timeout=readTimeout();
        savePrefs(); enhancing=true;
        textTaskName=kind.equals("ideas")?"正在寻找灵感…":kind.equals("summary")?"正在提炼画面…":"正在增强提示词…";
        setBusy(true,textTaskName);
        textTask = new AsyncTask<Void,Void,Result>() {
            @Override protected Result doInBackground(Void... args) {
                try {
                    GatewayClient client=new GatewayClient(base,key,timeout);
                    String reply=kind.equals("ideas")?client.suggestIdeas(input,note)
                            :kind.equals("summary")?client.summarizeForImage(input,note):client.enhancePrompt(input,note);
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
            int first=raw.indexOf('['), last=raw.lastIndexOf(']');
            org.json.JSONArray array=new org.json.JSONArray(first>=0 && last>first?raw.substring(first,last+1):raw);
            for(int i=0;i<Math.min(3,array.length());i++) {
                org.json.JSONObject item=array.optJSONObject(i);
                if(item==null) continue;
                String prompt=item.optString("prompt", "").trim();
                if(prompt.isEmpty()) continue;
                titles.add(item.optString("title", "灵感 "+(i+1))); prompts.add(prompt);
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

'''+t[end:]
# show current result without decoding on main
start=t.index('    private void showEntry(');end=t.index('    private static int sampleOf(',start)
t=t[:start]+'''    private void showEntry(GalleryStore.Entry entry) {
        selectedEntry=entry;
        boolean first=resultBlock.getVisibility()!=View.VISIBLE;
        resultBlock.setVisibility(View.VISIBLE);
        if(first) Motion.appear(resultBlock);
        resultMeta.setText(new SimpleDateFormat("HH:mm",Locale.US).format(new Date(entry.createdAt)));
        previews.load(resultView,gallery.fileOf(entry),1600,true);
    }

    private void renderGallery() {
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
                StringBuilder signature=new StringBuilder().append(galleryLimit).append('|').append(getResources().getDisplayMetrics().widthPixels);
                for(GalleryStore.Entry e:entries) signature.append('|').append(e.id);
                if(signature.toString().equals(gallerySignature)) return;
                gallerySignature=signature.toString();
                bindGallery(entries);
            });
        });
    }
    private void bindGallery(List<GalleryStore.Entry> entries) {
        setGridColumns(galleryGrid,3);
        if(entries.isEmpty()) { galleryGrid.addView(text("还没有图 · 生成后自动存入系统相册「墨台」",13,0xFF6A675F)); return; }
        int side=Math.max(dp(64),(getResources().getDisplayMetrics().widthPixels-dp(64))/3);
        for(int i=0;i<Math.min(entries.size(),galleryLimit);i++) {
            final GalleryStore.Entry entry=entries.get(i);
            ImageView thumb=new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFE7E2D6); bg.setCornerRadius(dp(12));
            thumb.setBackground(bg); thumb.setClipToOutline(true);
            thumb.setContentDescription("查看图片："+entry.prompt);
            thumb.setOnClickListener(v->{
                showEntry(entry);
                Intent intent=new Intent(this,ViewerActivity.class);
                intent.putExtra("path",gallery.fileOf(entry).getAbsolutePath()); intent.putExtra("prompt",entry.prompt);
                startActivity(intent);
            });
            thumb.setOnLongClickListener(v->{confirmDelete(entry);return true;});
            addToGrid(galleryGrid,thumb);
            ViewGroup.LayoutParams lp=thumb.getLayoutParams(); lp.height=side; thumb.setLayoutParams(lp);
            previews.load(thumb,gallery.fileOf(entry),side,false);
        }
        padGrid(galleryGrid);
        if(entries.size()>galleryLimit) {
            Button more=button("再显示24张 · 共"+entries.size()+"张",false);
            more.setOnClickListener(v->{galleryLimit+=24;renderGallery();});galleryGrid.addView(more);
        }
    }

'''+t[end:]
# async gallery sync
old='''            renderGallery();
            for (GalleryStore.Entry entry : gallery.list()) {
                if (id.equals(entry.id)) { showEntry(entry); break; }
            }'''
t=t.replace(old,'''            pendingEntry = id;
            renderGallery();''')
# remove bitmap invalidation tag on delete
start=t.index('    private void confirmDelete(');end=t.index('    private void exportLatest()',start)
t=t[:start]+'''    private void confirmDelete(final GalleryStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle("从应用画册删除？")
                .setMessage("只删除应用内副本，系统相册「墨台」里的图片不会删除。")
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

'''+t[end:]
t=t.replace('Pictures/Inkbench','Pictures/墨台')
t=t.replace('        promptField.setEnabled(!value);','''        promptField.setEnabled(!value);
        ideaButton.setEnabled(!value); longTextButton.setEnabled(!value);
        refreshHistoryButtons();''')
t=t.replace('enhanceButton.setText(enhancing ? "增强中…" : "增强提示词");','enhanceButton.setText(enhancing ? "AI 处理中…" : "增强提示词");')
# load prefs history and refresh controls
t=t.replace('        refreshSummaries();\n    }\n\n    private void savePrefs()', '''        refreshSummaries();
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
        refreshSwitch.setChecked(highRefresh);
        refreshLabel.setText(Motion.refresh(this,highRefresh));
        refreshSwitch.setOnCheckedChangeListener((v,on)->{
            highRefresh=on;refreshLabel.setText(Motion.refresh(this,on));savePrefs();
        });
    }

    private void savePrefs()''')
t=t.replace('                .putString("draft", promptField.getText().toString())','''                .putString("draft", promptField.getText().toString())
                .putString("promptOriginal",promptHistory.original())
                .putString("promptUndo",new org.json.JSONArray(promptHistory.steps()).toString())
                .putString("longDraft",longDraft).putString("wishDraft",wishDraft)
                .putBoolean("highRefresh",highRefresh)''')
# request legacy permissions only before job; media store no broad photo permission modern
needle='        final int timeoutSeconds = readTimeout();\n        savePrefs();'
t=t.replace(needle,'''        if (android.os.Build.VERSION.SDK_INT>=23 && android.os.Build.VERSION.SDK_INT<29
                && checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},72);
            toast("允许相册写入后请再点生成；原图仍会保留在应用内");
            return;
        }
        final int timeoutSeconds = readTimeout();
        savePrefs();''')
# high-refresh request upon visible resumes; lifecycle cleanup
a=t.index('    private void showEntry(')
t=t[:a]+'''    @Override protected void onResume() {
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

'''+t[a:]
# ripple rather than no touch feedback
t=t.replace('        button.setBackground(bg);','''        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x221F6B4A), bg, null));''')
p.write_text(t)
print('MainActivity 2.4 wired')
