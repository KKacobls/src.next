package com.kk.jsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Browser controls and durable user-customizable UI settings for v1.2.0. */
public final class BrowserExtras {
    private final Activity activity;
    private final WebView web;
    private final SharedPreferences prefs;
    private final Runnable refreshTheme;
    private final String mobileUserAgent;
    private int versionTaps;
    private long lastTap;
    private JSONObject previewStyle;
    private AutoScriptManager scripts;
    private java.util.function.Consumer<String> navigate;
    void bindActions(AutoScriptManager scripts, java.util.function.Consumer<String> navigate) {
        this.scripts = scripts; this.navigate = navigate;
    }
    boolean sidebarEnabled() { return developerEnabled() && prefs.getBoolean("developer_sidebar_v130", false); }
    void toggleSidebar() {
        if (!developerEnabled()) return;
        boolean next = !prefs.getBoolean("developer_sidebar_v130", false);
        prefs.edit().putBoolean("developer_sidebar_v130", next).apply();
        toast(next ? "已開啟右側快捷工具" : "已關閉右側快捷工具");
    }
    String startUrl() {return prefs.getString("start_url_v130","");}
    private static final String HISTORY = "browser_history_v120";
    private static final String STYLE = "browser_theme_v120";
    private static final String[] PRESETS = {
        "經典淺色", "經典深色", "極簡白", "AMOLED 黑", "柔和圓角",
        "科技藍", "森林綠", "暖色米白", "透明玻璃感", "緊湊工具型"
    };
    private static final String[] COLORS = {
        "#FFFFFF", "#202329", "#FAFAFA", "#000000", "#F2EFFA",
        "#121D32", "#EAF2E9", "#F8F1E7", "#DCE8F3", "#292D33"
    };
    private static final String[] FOREGROUNDS = {
        "#141614", "#F5F6F8", "#222222", "#FFFFFF", "#342C50",
        "#F7FAFF", "#24462C", "#4E3522", "#264362", "#FFFFFF"
    };
    private static final String[] ACCENTS = {
        "#E5E6E9", "#39404A", "#EEEEEE", "#181818", "#DED8F5",
        "#24446C", "#CEE3D0", "#E8DAC7", "#B3CDDF", "#424B55"
    };

    BrowserExtras(Activity activity, WebView web, Runnable refreshTheme) {
        this.activity = activity;
        this.web = web;
        this.refreshTheme = refreshTheme;
        prefs = activity.getSharedPreferences("browser_settings", Activity.MODE_PRIVATE);
        mobileUserAgent = web.getSettings().getUserAgentString();
    }

    boolean developerEnabled() { return prefs.getBoolean("developer_mode_v120", false); }
    void disableDeveloper() {
        prefs.edit().putBoolean("developer_mode_v120", false).apply();
        toast("已關閉開發者模式");
    }
    void versionTapped() {
        long now = System.currentTimeMillis();
        if (now - lastTap > 4500) versionTaps = 0;
        lastTap = now;
        versionTaps++;
        if (developerEnabled()) return;
        if (versionTaps >= 7) {
            prefs.edit().putBoolean("developer_mode_v120", true).apply();
            versionTaps = 0;
            toast("已啟用開發者模式");
        }
    }

    void recordPage(String url, String title) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        try {
            JSONArray old = history();
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("url", url);
            item.put("title", title == null || title.isEmpty() ? url : title);
            item.put("time", System.currentTimeMillis());
            next.put(item);
            for (int i=0; i<old.length() && next.length()<300; i++) {
                JSONObject row=old.optJSONObject(i);
                if (row != null && !url.equals(row.optString("url"))) next.put(row);
            }
            prefs.edit().putString(HISTORY,next.toString()).apply();
        } catch (Exception ignored) {}
    }
    private JSONArray history() {
        try { return new JSONArray(prefs.getString(HISTORY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    void showHistory() {
        JSONArray arr=history();
        if (arr.length()==0) { toast("尚無瀏覽紀錄"); return; }
        List<String> labels = new ArrayList<>();
        for (int i=0; i<arr.length(); i++) {
            JSONObject j=arr.optJSONObject(i);
            if (j==null) continue;
            String stamp=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.TAIWAN)
                .format(new Date(j.optLong("time",0)));
            labels.add(j.optString("title") + "\n" + j.optString("url") + "\n" + stamp);
        }
        new AlertDialog.Builder(activity).setTitle("瀏覽紀錄")
          .setItems(labels.toArray(new String[0]),(d,index)->{
              JSONObject j=arr.optJSONObject(index);
              if (j != null) web.loadUrl(j.optString("url"));
          }).setNeutralButton("清除紀錄",(d,w)->new AlertDialog.Builder(activity)
             .setMessage("確定清除所有瀏覽紀錄？")
             .setPositiveButton("清除",(x,y)->prefs.edit().remove(HISTORY).apply())
             .setNegativeButton("取消",null).show())
          .setNegativeButton("關閉",null).show();
    }
    void rememberClosed() {
        String url=web.getUrl();
        if (url!=null && (url.startsWith("https://") || url.startsWith("http://"))) {
            prefs.edit().putString("last_closed_url_v120",url).apply();
        }
    }
    void reopenClosed() {
        String url=prefs.getString("last_closed_url_v120","");
        if (url.isEmpty()) { toast("沒有最近關閉的分頁"); return; }
        web.loadUrl(url);
    }
    void searchInPage() {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("輸入網頁搜尋文字");
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(20),dp(8),dp(20),0);
        row.addView(input);
        TextView count=new TextView(activity);
        count.setPadding(0,dp(12),0,dp(10));
        count.setText("輸入關鍵字搜尋");
        row.addView(count);
        web.setFindListener((active,total,done)->activity.runOnUiThread(()->
          count.setText(total==0?"找不到符合項目":"第 "+(active+1)+" 個，共 "+total+" 個")));
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count) {
                if (s.length()==0) { web.clearMatches(); return; }
                web.findAllAsync(s.toString());
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("在網頁中搜尋").setView(row)
          .setPositiveButton("下一個",null).setNeutralButton("上一個",null)
          .setNegativeButton("完成",(d,w)->web.clearMatches()).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->web.findNext(true));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->web.findNext(false));
        });
        dialog.setOnCancelListener(d->web.clearMatches());
        dialog.show();
    }
    void zoom(boolean increase) {
        int value=prefs.getInt("zoom_v120",100);
        value=Math.max(50,Math.min(200,value+(increase?10:-10)));
        prefs.edit().putInt("zoom_v120",value).apply();
        web.setInitialScale(value);
        web.getSettings().setTextZoom(value);
        toast("網頁縮放："+value+"%");
        web.reload();
    }
    void desktopToggle() {
        boolean next=!prefs.getBoolean("desktop_v120",false);
        prefs.edit().putBoolean("desktop_v120",next).apply();
        applyDesktop();
        web.reload();
        toast(next?"已切換電腦版":"已切換手機版");
    }
    void applyDesktop() {
        boolean desktop=prefs.getBoolean("desktop_v120",false);
        WebSettings settings=web.getSettings();
        if (desktop) {
            settings.setUserAgentString(mobileUserAgent.replace("Android", "X11; Linux x86_64")
                .replace("Mobile ", ""));
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        } else {
            settings.setUserAgentString(mobileUserAgent);
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
        }
        int zoom=prefs.getInt("zoom_v120",100);
        settings.setTextZoom(zoom);
    }
    void viewSource() {
        web.evaluateJavascript("(function(){return document.documentElement.outerHTML})()",result->{
            String html=result;
            try { Object parsed=new org.json.JSONTokener(result).nextValue();html=String.valueOf(parsed); }
            catch (Exception ignored) {}
            EditText text=new EditText(activity);
            text.setText(html);
            text.setTypeface(Typeface.MONOSPACE);
            text.setTextSize(11);
            text.setTextIsSelectable(true);
            text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            ScrollView scroll=new ScrollView(activity);
            scroll.addView(text);
            new AlertDialog.Builder(activity).setTitle("網頁 HTML（目前 DOM）")
                .setView(scroll).setPositiveButton("關閉",null).show();
        });
    }

    private JSONObject defaults() {
        JSONObject j=new JSONObject();
        try {
            j.put("preset",0);j.put("backgroundColor","#FFFFFF");
            j.put("textColor","#141614");j.put("toolbarColor","#E5E6E9");
            j.put("fontSize",16);j.put("fontWeight",600);j.put("lineHeight",1.2);
            j.put("borderRadius",18);j.put("spacing",8);j.put("borderWidth",0);
            j.put("shadow",8);j.put("toolbarHeight",62);j.put("iconSize",25);
            j.put("toolbarPosition","top");j.put("headerAlignment","left");
            j.put("buttonColor","#F3F3F3");j.put("buttonTextColor","#333333");
            j.put("accentColor","#1764CF");j.put("borderColor","#D5D5D5");
            j.put("dialogBackgroundColor","#FFFFFF");
        } catch(Exception ignored) {}
        return j;
    }
    JSONObject style() {
        if (previewStyle != null) return previewStyle;
        try { return new JSONObject(prefs.getString(STYLE, defaults().toString())); }
        catch(Exception e) {return defaults();}
    }
    private void saveStyle(JSONObject j) {
        previewStyle = null;
        prefs.edit().putString(STYLE,j.toString()).apply();
        refreshTheme.run();
    }
    private int parseColor(String value,int fallback) {
        try {return Color.parseColor(value);}
        catch(Exception e){return fallback;}
    }
    int color(String key,int fallback) {return parseColor(style().optString(key),fallback);}
    int size(String key,int fallback) {return style().optInt(key,fallback); }
    boolean bottomToolbar() {return "bottom".equals(style().optString("toolbarPosition"));}
    void applyTheme(View container, TextView title, TextView subtitle, View header, TextView menu) {
        JSONObject s=style();
        int background=color("backgroundColor",Color.WHITE);
        int foreground=color("textColor",Color.BLACK);
        int toolbar=color("toolbarColor",Color.WHITE);
        container.setBackgroundColor(background);
        android.graphics.drawable.GradientDrawable headerBg = new android.graphics.drawable.GradientDrawable();
        headerBg.setColor(toolbar);
        headerBg.setCornerRadius(dp(Math.max(0,Math.min(80,s.optInt("borderRadius",18)))));
        int border=Math.max(0,Math.min(12,s.optInt("borderWidth",0)));
        if(border>0)headerBg.setStroke(dp(border),color("borderColor",foreground));
        header.setBackground(headerBg);
        header.setElevation(dp(Math.max(0,Math.min(35,s.optInt("shadow",8)))));
        int spacing=Math.max(0,Math.min(35,s.optInt("spacing",8)));
        header.setPadding(dp(spacing/2),0,dp(spacing/2),0);
        title.setTextColor(foreground);
        subtitle.setTextColor(foreground);
        menu.setTextColor(foreground);
        title.setTextSize(s.optInt("fontSize",16));
        title.setLineSpacing(0,(float)s.optDouble("lineHeight",1.2));
        title.setTypeface(Typeface.DEFAULT,s.optInt("fontWeight",600)>=600?Typeface.BOLD:Typeface.NORMAL);
        menu.setTextSize(s.optInt("iconSize",25));
        ViewGroup.LayoutParams layout=header.getLayoutParams();
        if (layout!=null) {layout.height=dp(s.optInt("toolbarHeight",62));header.setLayoutParams(layout);}
        if(header.getParent() instanceof LinearLayout) {
            LinearLayout parent=(LinearLayout)header.getParent();
            int index=parent.indexOfChild(header);
            if(bottomToolbar() && index != parent.getChildCount()-1) {
                parent.removeView(header);parent.addView(header);
            } else if(!bottomToolbar() && index != 0) {
                parent.removeView(header);parent.addView(header,0);
            }
        }
    }
    void choosePreset() {
        new ThemeStudio(activity,prefs,this::saveStyle).showGallery(style());
    }
    void chooseToolbarPosition() {
        String[] choices={"頂部工具列","底部工具列"};
        new AlertDialog.Builder(activity).setTitle("工具列位置")
            .setSingleChoiceItems(choices,bottomToolbar()?1:0,(d,which)->{
                JSONObject s=style();
                try{s.put("toolbarPosition",which==1?"bottom":"top");}catch(Exception ignored){}
                saveStyle(s);
                d.dismiss();
            }).setNegativeButton("完成",null).show();
    }
    private static final String[] PROPERTY_NAMES = {
        "背景顏色","文字顏色","工具列顏色","按鈕背景","按鈕文字","強調色","邊框顏色","對話框背景","字體大小","字重","行高","圓角",
        "間距","邊框寬度","陰影","工具列高度","圖示大小"
    };
    private static final String[] PROPERTY_KEYS = {
        "backgroundColor","textColor","toolbarColor","buttonColor","buttonTextColor","accentColor","borderColor","dialogBackgroundColor","fontSize","fontWeight","lineHeight",
        "borderRadius","spacing","borderWidth","shadow","toolbarHeight","iconSize"
    };
    void editStyle() {
        String[] options=new String[PROPERTY_NAMES.length+2];
        System.arraycopy(PROPERTY_NAMES,0,options,0,PROPERTY_NAMES.length);
        options[PROPERTY_NAMES.length]="選擇預設風格";
        options[PROPERTY_NAMES.length+1]="頂部／底部位置";
        new AlertDialog.Builder(activity).setTitle("CSS／介面屬性").setItems(options,(d,which)->{
            if(which==PROPERTY_NAMES.length) {choosePreset();return;}
            if(which==PROPERTY_NAMES.length+1) {chooseToolbarPosition();return;}
            JSONObject s=style();
            String key=PROPERTY_KEYS[which];
            if (which < 8) {
                ThemeStudio studio=new ThemeStudio(activity,prefs,this::saveStyle);
                int initial=color(key,Color.WHITE);
                studio.pickColor(PROPERTY_NAMES[which],initial,
                    c->{ try {
                             JSONObject stage = new JSONObject(s.toString());
                             stage.put(key,String.format(java.util.Locale.US,"#%06X",c&0xffffff));
                             previewStyle=stage;
                             refreshTheme.run();
                         } catch(Exception ignored){}
                    },
                    c->{try{s.put(key,String.format(java.util.Locale.US,"#%06X",c&0xffffff));saveStyle(s);}
                         catch(Exception ignored){previewStyle=null;refreshTheme.run();}});
                return;
            }
            double initial=s.optDouble(key,16);
            double min=0,max=100,step=1;
            if(key.equals("fontSize")){min=8;max=36;step=1;}
            if(key.equals("fontWeight")){min=100;max=900;step=100;}
            if(key.equals("lineHeight")){min=0.5;max=3;step=.05;}
            if(key.equals("borderRadius")){min=0;max=80;step=1;}
            if(key.equals("spacing")){min=0;max=40;step=1;}
            if(key.equals("borderWidth")){min=0;max=12;step=1;}
            if(key.equals("shadow")){min=0;max=35;step=1;}
            if(key.equals("toolbarHeight")){min=42;max=100;step=1;}
            if(key.equals("iconSize")){min=12;max=40;step=1;}
            new ThemeStudio(activity,prefs,this::saveStyle)
                .pickNumber(PROPERTY_NAMES[which],initial,min,max,step,
                    n->{
                        try{
                            JSONObject stage=new JSONObject(s.toString());
                            stage.put(key,n);
                            previewStyle=stage;
                            refreshTheme.run();
                        }catch(Exception ignored){}
                    },
                    n->{
                        try{
                            s.put(key,n);
                            saveStyle(s);
                        }catch(Exception ignored){previewStyle=null;refreshTheme.run();}
                    });
        }).setNegativeButton("關閉",null).show();
    }
    private JSONObject exportConfig() throws org.json.JSONException {
        JSONObject cfg = new JSONObject();
        cfg.put("schemaVersion", 1);
        cfg.put("developerMode", developerEnabled());
        cfg.put("appearance", style());
        JSONObject browser = new JSONObject();
        browser.put("startUrl", startUrl());
        browser.put("defaultZoom", prefs.getInt("zoom_v120",100));
        browser.put("desktopMode", prefs.getBoolean("desktop_v120",false));
        browser.put("restoreLastClosedTab", true);
        browser.put("historyEnabled", true);
        cfg.put("browser", browser);
        JSONObject developer = new JSONObject();
        developer.put("autoScriptsEnabled", prefs.getBoolean("autoScriptsEnabled_v120",true));
        developer.put("sidebarEnabled", prefs.getBoolean("developer_sidebar_v130",false));
        if (scripts != null) developer.put("scripts", scripts.exportScripts());
        cfg.put("developer", developer);
        return cfg;
    }

    /** Fully validate before writing persistent settings or changing active scripts. */
    private void importConfig(String source) throws Exception {
        JSONObject cfg = new JSONObject(source);
        if (cfg.has("schemaVersion") && cfg.getInt("schemaVersion") != 1)
            throw new IllegalArgumentException("目前支援 schemaVersion 1");
        JSONObject appearance = cfg.optJSONObject("appearance");
        JSONObject browser = cfg.optJSONObject("browser");
        JSONObject developer = cfg.optJSONObject("developer");
        JSONObject next = new JSONObject(style().toString());
        JSONArray validatedScripts = null;
        String target = null;
        if (appearance != null) {
            JSONArray keys = appearance.names();
            if (keys != null) for (int i=0; i<keys.length(); i++) {
                String key = keys.getString(i);
                Object value = appearance.get(key);
                if (key.endsWith("Color")) Color.parseColor(String.valueOf(value));
                if (key.equals("toolbarPosition") && !("top".equals(value) || "bottom".equals(value)))
                    throw new IllegalArgumentException("工具列位置僅接受 top 或 bottom");
                if (key.equals("preset") && (appearance.getInt(key)<0 || appearance.getInt(key)>9))
                    throw new IllegalArgumentException("預設樣式編號無效");
                next.put(key,value);
            }
        }
        if (browser != null) {
            if (browser.has("defaultZoom")) {
                int zoom = browser.getInt("defaultZoom");
                if (zoom < 50 || zoom > 200) throw new IllegalArgumentException("縮放範圍需 50～200");
            }
            if (browser.has("startUrl")) {
                target = browser.getString("startUrl").trim();
                if (!target.isEmpty()) {
                    android.net.Uri uri = android.net.Uri.parse(target);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost()==null || uri.getHost().isEmpty())
                        throw new IllegalArgumentException("startUrl 必須是完整 http/https 網址");
                }
            }
        }
        if (developer != null && developer.has("scripts")) {
            if (scripts == null) throw new IllegalArgumentException("腳本管理器尚未準備好");
            validatedScripts = scripts.prepareImport(developer.getJSONArray("scripts"));
        }
        Boolean developerMode = cfg.has("developerMode") ? cfg.getBoolean("developerMode") : null;
        Boolean enabled = developer != null && developer.has("autoScriptsEnabled")
            ? developer.getBoolean("autoScriptsEnabled") : null;
        Boolean sidebar = developer != null && developer.has("sidebarEnabled")
            ? developer.getBoolean("sidebarEnabled") : null;
        Boolean desktop = browser != null && browser.has("desktopMode")
            ? browser.getBoolean("desktopMode") : null;
        Integer zoom = browser != null && browser.has("defaultZoom")
            ? browser.getInt("defaultZoom") : null;
        // All user-provided fields passed validation; persistent write happens once.
        SharedPreferences.Editor editor = prefs.edit().putString(STYLE, next.toString());
        if (developerMode != null) editor.putBoolean("developer_mode_v120",developerMode);
        if (enabled != null) editor.putBoolean("autoScriptsEnabled_v120",enabled);
        if (sidebar != null) editor.putBoolean("developer_sidebar_v130",sidebar);
        if (desktop != null) editor.putBoolean("desktop_v120",desktop);
        if (zoom != null) editor.putInt("zoom_v120",zoom);
        if (target != null) editor.putString("start_url_v130",target);
        if (!editor.commit()) throw new IllegalStateException("無法寫入設定");
        if (validatedScripts != null) scripts.applyPreparedImport(validatedScripts);
        applyDesktop();
        refreshTheme.run();
        if (target != null && !target.isEmpty() && navigate != null) navigate.accept(target);
    }

    void jsonSettings() {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(9);
        input.setMaxLines(18);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(12);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),0,dp(18),0);
        panel.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("JSON 設定")
            .setView(panel).setPositiveButton("匯入",null)
            .setNeutralButton("匯出",null)
            .setNegativeButton("取消",(d,w)->{}).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    importConfig(input.getText().toString());
                    toast("已套用設定與腳本");
                    dialog.dismiss();
                } catch(Exception e) {
                    new AlertDialog.Builder(activity).setTitle("匯入失敗")
                        .setMessage(e.getMessage()).setPositiveButton("確定",null).show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    String json = exportConfig().toString(2);
                    input.setText(json);
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("browser-settings.json",json));
                    toast("設定 JSON 已複製");
                } catch(Exception e) { toast("匯出失敗"); }
            });
        });
        dialog.show();
    }
    private void toast(String s) {Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
    private int dp(int value) {return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
}
