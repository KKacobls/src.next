package com.kk.geckomonkey;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal, independent browser using Mozilla GeckoView and the REAL, Mozilla-signed
 * Tampermonkey Firefox extension. No imitation userscript interpreter.
 *
 * The Tampermonkey XPI is never redistributed or modified in the APK.
 * The user explicitly installs it from its official addons.mozilla.org listing.
 */
public final class MainActivity extends Activity {
    private static final String HOME = "https://example.com/";
    private static final String AMO_API =
        "https://addons.mozilla.org/api/v5/addons/addon/tampermonkey/";
    private static final int BAR = Color.rgb(247, 249, 253);
    private static final int TEXT = Color.rgb(34, 47, 64);
    private static final int BLUE = Color.rgb(24, 94, 208);

    private GeckoRuntime runtime;
    private final ArrayList<GeckoSession> tabs = new ArrayList<>();
    private GeckoSession current;
    private GeckoView browserView;
    private EditText address;
    private TextView info;
    private ProgressBar progress;
    private Button monkeyButton;
    private WebExtensionController controller;
    private WebExtension tampermonkey;
    private WebExtension.Action browserAction;
    private Dialog popupDialog;
    private GeckoSession popupSession;
    private GeckoView popupView;
    private boolean canGoBack;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(24, 43, 79));
        getWindow().setNavigationBarColor(Color.rgb(24, 43, 79));

        runtime = ((BrowserApp) getApplication()).runtime();
        controller = runtime.getWebExtensionController();
        buildUI();
        registerInstallPrompts();
        openNewTab(HOME);
        checkInstalled();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        LinearLayout row1 = new LinearLayout(this);
        row1.setPadding(dp(8), dp(7), dp(8), dp(6));
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setBackgroundColor(BAR);
        root.addView(row1, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView globe = new TextView(this);
        globe.setText("🌐");
        globe.setTextSize(21);
        globe.setGravity(Gravity.CENTER);
        row1.addView(globe, new LinearLayout.LayoutParams(dp(38), -1));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(TEXT);
        address.setHint("輸入網址或搜尋");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setPadding(dp(10), 0, dp(10), 0);
        address.setBackground(round(Color.WHITE, 14));
        row1.addView(address, new LinearLayout.LayoutParams(0, dp(42), 1));
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });

        Button go = smallButton("前往");
        LinearLayout.LayoutParams goParam = new LinearLayout.LayoutParams(dp(57), dp(42));
        goParam.leftMargin = dp(6);
        row1.addView(go, goParam);
        go.setOnClickListener(v -> navigate(address.getText().toString()));

        LinearLayout row2 = new LinearLayout(this);
        row2.setPadding(dp(8), 0, dp(8), dp(4));
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setBackgroundColor(BAR);
        root.addView(row2, new LinearLayout.LayoutParams(-1, dp(54)));

        Button back = smallButton("‹");
        back.setContentDescription("上一頁");
        row2.addView(back, new LinearLayout.LayoutParams(dp(48), dp(46)));
        back.setOnClickListener(v -> {
            if (canGoBack && current != null) current.goBack();
            else if (tabs.size() > 1) closeCurrentTab();
            else toast("沒有上一頁");
        });

        Button refresh = smallButton("↻");
        refresh.setContentDescription("重新整理");
        row2.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(46)));
        refresh.setOnClickListener(v -> {
            if (current != null) current.reload();
        });

        monkeyButton = smallButton("🐵  安裝油猴");
        monkeyButton.setTextColor(BLUE);
        LinearLayout.LayoutParams mParam = new LinearLayout.LayoutParams(0, dp(46), 1);
        mParam.leftMargin = dp(5);
        row2.addView(monkeyButton, mParam);
        monkeyButton.setOnClickListener(v -> showMonkeyMenu());

        Button menu = smallButton("⋮");
        LinearLayout.LayoutParams menuParam = new LinearLayout.LayoutParams(dp(48), dp(46));
        menuParam.leftMargin = dp(5);
        row2.addView(menu, menuParam);
        menu.setOnClickListener(v -> showToolsMenu());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        browserView = new GeckoView(this);
        browserView.setBackgroundColor(Color.WHITE);
        content.addView(browserView, new FrameLayout.LayoutParams(-1, -1));

        info = new TextView(this);
        info.setText("GeckoView 引擎 · 官方 Firefox 油猴擴充套件");
        info.setTextSize(11);
        info.setTextColor(Color.rgb(103, 119, 144));
        info.setPadding(dp(12), dp(3), dp(12), dp(3));
        root.addView(info, new LinearLayout.LayoutParams(-1, dp(25)));
    }

    private Button smallButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackground(round(Color.WHITE, 12));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void navigate(String text) {
        String value = text.trim();
        if (value.isEmpty() || current == null) return;
        if (value.startsWith("about:") || value.startsWith("moz-extension://")) {
            current.loadUri(value);
        } else if (value.startsWith("https://") || value.startsWith("http://")) {
            current.loadUri(value);
        } else if (!value.contains(" ") && value.contains(".")) {
            current.loadUri("https://" + value);
        } else {
            try {
                current.loadUri("https://www.google.com/search?q="
                    + java.net.URLEncoder.encode(value, "UTF-8"));
            } catch (Exception ignored) {}
        }
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
        browserView.requestFocus();
    }

    private GeckoSession newConfiguredSession() {
        GeckoSession session = new GeckoSession();
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession s, String url,
                  List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                  Boolean hasUserGesture) {
                if (s == current && url != null) {
                    address.setText(url);
                    info.setText(url.startsWith("moz-extension://")
                        ? "Tampermonkey · 擴充套件頁面" : "Mozilla GeckoView");
                }
            }

            @Override
            public void onCanGoBack(GeckoSession s, boolean back) {
                if (s == current) canGoBack = back;
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String url) {
                GeckoSession next = newConfiguredSession();
                next.open(runtime);
                tabs.add(next);
                showTab(next);
                return GeckoResult.fromValue(next);
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onProgressChange(GeckoSession s, int percent) {
                if (s == current) progress.setProgress(percent);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                if (s == current) {
                    progress.setProgress(100);
                    progress.postDelayed(() -> progress.setProgress(0), 450);
                    if (!success) info.setText("網頁載入失敗，請檢查網址和網路");
                }
            }
        });
        // The real extension needs tab APIs for every browser tab, including
        // tabs opened later by its own dashboard or userscripts.
        if (tampermonkey != null) {
            configureSessionForExtension(session, tampermonkey);
        }
        return session;
    }

    private void openNewTab(String uri) {
        GeckoSession next = newConfiguredSession();
        next.open(runtime);
        tabs.add(next);
        showTab(next);
        if (uri != null && !uri.isEmpty()) next.loadUri(uri);
    }

    private void showTab(GeckoSession next) {
        if (current == next) return;
        if (browserView.getSession() != null) {
            browserView.releaseSession();
        }
        current = next;
        canGoBack = false;
        browserView.setSession(next);
        controller.setTabActive(next, true);
        next.setActive(true);
    }

    private void closeCurrentTab() {
        if (current == null || tabs.size() <= 1) return;
        GeckoSession closed = current;
        tabs.remove(closed);
        GeckoSession next = tabs.get(tabs.size() - 1);
        showTab(next);
        closed.close();
    }

    private void showToolsMenu() {
        new AlertDialog.Builder(this)
            .setTitle("KK瀏覽器 · GeckoView")
            .setItems(new String[] {
                "開啟 Tampermonkey 管理頁",
                "油猴安裝狀態",
                "新增分頁",
                "切換分頁",
                "開啟測試網頁",
                "官方 Tampermonkey 頁面"
            }, (d, n) -> {
                switch (n) {
                    case 0: openOptionsPage(); break;
                    case 1: checkInstalled(); break;
                    case 2: openNewTab(HOME); break;
                    case 3: switchTabs(); break;
                    case 4: if (current != null) current.loadUri(HOME); break;
                    case 5: if (current != null) current.loadUri(
                        "https://addons.mozilla.org/zh-TW/android/addon/tampermonkey/"); break;
                }
            })
            .setNegativeButton("關閉", null).show();
    }

    private void switchTabs() {
        if (tabs.isEmpty()) return;
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = tabs.get(i) == current ? "● 分頁 " + (i + 1) : "○ 分頁 " + (i + 1);
        }
        new AlertDialog.Builder(this).setTitle("選擇分頁").setItems(labels, (d, which) ->
            showTab(tabs.get(which))).show();
    }

    private void checkInstalled() {
        controller.list().accept(extensions -> {
            WebExtension installed = null;
            for (WebExtension e : extensions) {
                if (e.metaData != null && e.metaData.name != null &&
                    e.metaData.name.toLowerCase(Locale.ROOT).contains("tampermonkey")) {
                    installed = e;
                    break;
                }
            }
            tampermonkey = installed;
            browserAction = null;
            if (installed != null) {
                configureExtension(installed);
                monkeyButton.setText("🐵  油猴已安裝");
                info.setText("Tampermonkey " + installed.metaData.version + " · 已安裝");
            } else {
                monkeyButton.setText("🐵  安裝油猴");
                info.setText("尚未安裝 Tampermonkey，點上方按鈕安裝官方 XPI");
            }
        }, error -> {
            info.setText("檢查油猴狀態失敗");
            toast("無法列出擴充套件：" + error.getMessage());
        });
    }

    private void showMonkeyMenu() {
        if (tampermonkey == null) {
            new AlertDialog.Builder(this)
                .setTitle("安裝官方 Tampermonkey")
                .setMessage("KK瀏覽器使用 Firefox GeckoView 引擎，將直接從 Mozilla 官方擴充套件商店下載原版已簽署 XPI。\n\n"
                    + "Tampermonkey 需要讀取並修改網站內容的權限；不會把它打包或修改後轉售。")
                .setPositiveButton("從 Mozilla 安裝", (d, w) -> installFromMozilla())
                .setNeutralButton("重新檢查", (d, w) -> checkInstalled())
                .setNegativeButton("取消", null).show();
            return;
        }
        String version = tampermonkey.metaData == null ? "" : tampermonkey.metaData.version;
        new AlertDialog.Builder(this)
            .setTitle("Tampermonkey " + version)
            .setItems(new String[] {
                "🐵 開啟油猴選單",
                "📝 開啟腳本管理介面",
                "🔄 檢查擴充套件更新",
                "📋 已安裝資訊",
                "🗑 解除安裝油猴"
            }, (d, n) -> {
                switch (n) {
                    case 0: openMonkeyPopup(); break;
                    case 1: openOptionsPage(); break;
                    case 2: updateMonkey(); break;
                    case 3: showInstalledInfo(); break;
                    case 4: uninstallMonkey(); break;
                }
            }).setNegativeButton("關閉", null).show();
    }

    /** Download only the official AMO metadata, not a third-party unverified XPI. */
    private void installFromMozilla() {
        monkeyButton.setEnabled(false);
        info.setText("正在向 Mozilla 取得官方 Tampermonkey 安裝網址…");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL api = new URL(AMO_API);
                conn = (HttpURLConnection) api.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200)
                    throw new IllegalStateException("Mozilla AMO HTTP " + conn.getResponseCode());
                StringBuilder json = new StringBuilder();
                try (InputStream input = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null && json.length() < 400000)
                        json.append(line);
                }
                JSONObject addon = new JSONObject(json.toString());
                if (!"tampermonkey".equals(addon.optString("slug")))
                    throw new IllegalStateException("AMO 回傳的套件不是 Tampermonkey");
                String url = addon.getJSONObject("current_version")
                    .getJSONObject("file").getString("url");
                URL xpi = new URL(url);
                if (!"https".equalsIgnoreCase(xpi.getProtocol()) ||
                        !"addons.mozilla.org".equalsIgnoreCase(xpi.getHost()) ||
                        !xpi.getPath().endsWith(".xpi")) {
                    throw new SecurityException("拒絕非 Mozilla 官方來源的 XPI");
                }
                runOnUiThread(() -> {
                    info.setText("正在下載並驗證官方 XPI 的 Mozilla 簽章…");
                    controller.install(url,
                        WebExtensionController.INSTALLATION_METHOD_MANAGER)
                    .accept(extension -> {
                        monkeyButton.setEnabled(true);
                        tampermonkey = extension;
                        configureExtension(extension);
                        monkeyButton.setText("🐵  油猴已安裝");
                        info.setText("Tampermonkey 安裝成功！按油猴按鈕進入管理畫面");
                        new AlertDialog.Builder(this).setTitle("已安裝 Tampermonkey")
                            .setMessage("這是真正的 Firefox Tampermonkey 擴充套件。你可以使用它管理 .user.js 腳本。")
                            .setPositiveButton("開啟腳本管理", (d,w) -> openOptionsPage())
                            .setNegativeButton("關閉", null).show();
                    }, error -> {
                        monkeyButton.setEnabled(true);
                        info.setText("油猴安裝失敗");
                        errorDialog("Tampermonkey 安裝失敗", error);
                    });
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    monkeyButton.setEnabled(true);
                    info.setText("無法取得官方油猴 XPI");
                    errorDialog("Mozilla AMO 連線／驗證失敗", e);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "AMO-Metadata").start();
    }

    private void registerInstallPrompts() {
        controller.setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                    WebExtension extension, String[] permissions,
                    String[] origins, String[] dataCollectionPermissions) {
                GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
                String name = extension.metaData != null && extension.metaData.name != null
                    ? extension.metaData.name : "擴充套件";
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("授權安裝 " + name)
                    .setMessage("此擴充套件要求以下權限：\n"
                        + summarize(permissions, 8) + "\n\n網站權限：\n"
                        + summarize(origins, 6)
                        + "\n\n油猴可能讀取、修改符合權限的網站內容。確定安裝嗎？")
                    .setPositiveButton("同意安裝", (d,w) ->
                        result.complete(new WebExtension.PermissionPromptResponse(
                            true, false, false)))
                    .setNegativeButton("拒絕", (d,w) ->
                        result.complete(new WebExtension.PermissionPromptResponse(
                            false, false, false)))
                    .setOnCancelListener(d ->
                        result.complete(new WebExtension.PermissionPromptResponse(
                            false, false, false))).show();
                return result;
            }

            @Override
            public GeckoResult<AllowOrDeny> onOptionalPrompt(
                    WebExtension extension, String[] permissions,
                    String[] origins, String[] dataCollectionPermissions) {
                return askExtensionPermission("油猴要求額外權限",
                    permissions, origins);
            }

            @Override
            public GeckoResult<AllowOrDeny> onUpdatePrompt(
                    WebExtension extension, String[] permissions,
                    String[] origins, String[] dataCollectionPermissions) {
                return askExtensionPermission("油猴更新需要額外權限",
                    permissions, origins);
            }
        });
    }

    private GeckoResult<AllowOrDeny> askExtensionPermission(
            String title, String[] permissions, String[] origins) {
        GeckoResult<AllowOrDeny> result = new GeckoResult<>();
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage("權限：\n" + summarize(permissions, 8)
                + "\n\n網站：\n" + summarize(origins, 6))
            .setPositiveButton("允許", (d,w) -> result.complete(AllowOrDeny.ALLOW))
            .setNegativeButton("拒絕", (d,w) -> result.complete(AllowOrDeny.DENY))
            .setOnCancelListener(d -> result.complete(AllowOrDeny.DENY))
            .show();
        return result;
    }

    private String summarize(String[] values, int limit) {
        if (values == null || values.length == 0) return "（無）";
        StringBuilder b = new StringBuilder();
        for (int i=0; i<values.length && i<limit; i++) b.append("• ").append(values[i]).append("\n");
        if (values.length > limit) b.append("…還有 ").append(values.length-limit).append(" 項");
        return b.toString();
    }

    private void configureExtension(WebExtension extension) {
        extension.setActionDelegate(new WebExtension.ActionDelegate() {
            @Override
            public void onBrowserAction(WebExtension source, GeckoSession session,
                                        WebExtension.Action action) {
                if (session == null || session == current) browserAction = action;
            }

            @Override
            public void onPageAction(WebExtension source, GeckoSession session,
                                     WebExtension.Action action) {
                if (browserAction == null) browserAction = action;
            }

            @Override
            public GeckoResult<GeckoSession> onOpenPopup(
                    WebExtension source, WebExtension.Action action) {
                return displayActionPopup();
            }

            @Override
            public GeckoResult<GeckoSession> onTogglePopup(
                    WebExtension source, WebExtension.Action action) {
                if (popupDialog != null && popupDialog.isShowing()) {
                    popupDialog.dismiss();
                    return GeckoResult.fromValue(null);
                }
                return displayActionPopup();
            }
        });
        extension.setTabDelegate(new WebExtension.TabDelegate() {
            @Override
            public GeckoResult<GeckoSession> onNewTab(
                    WebExtension source, WebExtension.CreateTabDetails details) {
                GeckoSession next = newConfiguredSession();
                next.open(runtime);
                tabs.add(next);
                showTab(next);
                return GeckoResult.fromValue(next);
            }

            @Override
            public void onOpenOptionsPage(WebExtension source) {
                openOptionsPage();
            }
        });
        for (GeckoSession s : tabs) configureSessionForExtension(s, extension);
    }

    private void configureSessionForExtension(GeckoSession session, WebExtension extension) {
        session.getWebExtensionController().setTabDelegate(extension,
            new WebExtension.SessionTabDelegate() {
                @Override
                public GeckoResult<AllowOrDeny> onUpdateTab(
                        WebExtension e, GeckoSession changed,
                        WebExtension.UpdateTabDetails details) {
                    if (tabs.contains(changed)) showTab(changed);
                    return GeckoResult.allow();
                }

                @Override
                public GeckoResult<AllowOrDeny> onCloseTab(
                        WebExtension e, GeckoSession closing) {
                    if (tabs.size() <= 1 || !tabs.contains(closing))
                        return GeckoResult.deny();
                    if (closing == current) closeCurrentTab();
                    else { tabs.remove(closing); closing.close(); }
                    return GeckoResult.allow();
                }
            });
    }

    private GeckoResult<GeckoSession> displayActionPopup() {
        if (popupDialog != null && popupDialog.isShowing()) popupDialog.dismiss();
        final GeckoSession popup = new GeckoSession();
        popup.open(runtime);
        final GeckoView popupSurface = new GeckoView(this);
        popupSurface.setSession(popup);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(popupSurface);
        dialog.setOnDismissListener(d -> {
            if (popupSurface.getSession() != null) popupSurface.releaseSession();
            popup.close();
            if (popupDialog == dialog) {
                popupDialog = null;
                popupSession = null;
                popupView = null;
            }
        });
        popupDialog = dialog;
        popupSession = popup;
        popupView = popupSurface;
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(Math.min(dp(390), getResources().getDisplayMetrics().widthPixels-dp(16)),
                Math.min(dp(570), (int)(getResources().getDisplayMetrics().heightPixels*.76)));
            w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        }
        return GeckoResult.fromValue(popup);
    }

    private void openMonkeyPopup() {
        if (tampermonkey == null) { showMonkeyMenu(); return; }
        if (browserAction != null) {
            browserAction.click();
        } else {
            // Some versions only expose their options page, not a browser action.
            openOptionsPage();
        }
    }

    private void openOptionsPage() {
        if (tampermonkey == null) {
            toast("請先安裝 Tampermonkey");
            showMonkeyMenu();
            return;
        }
        String options = tampermonkey.metaData != null
            ? tampermonkey.metaData.optionsPageUrl : null;
        if (options == null || !options.startsWith("moz-extension://")) {
            toast("此版本未提供 optionsPageUrl，改用油猴選單");
            if (browserAction != null) browserAction.click();
            return;
        }
        openNewTab(options);
    }

    private void showInstalledInfo() {
        if (tampermonkey == null) return;
        String name = tampermonkey.metaData == null ? "" : tampermonkey.metaData.name;
        String version = tampermonkey.metaData == null ? "" : tampermonkey.metaData.version;
        String description = "套件：" + name + "\n版本：" + version
            + "\nID：" + tampermonkey.id
            + "\n已透過 Mozilla GeckoView 驗證簽章並安裝";
        new AlertDialog.Builder(this).setTitle("油猴安裝資訊")
            .setMessage(description)
            .setPositiveButton("確定", null)
            .setNeutralButton("複製", (d,w) -> {
                ClipboardManager clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clip.setPrimaryClip(ClipData.newPlainText("extension", description));
            }).show();
    }

    private void updateMonkey() {
        if (tampermonkey == null) return;
        info.setText("正在向 Mozilla 檢查 Tampermonkey 更新…");
        controller.update(tampermonkey).accept(extension -> {
            if (extension != null) {
                tampermonkey = extension;
                configureExtension(extension);
            }
            checkInstalled();
            toast("已完成油猴更新檢查");
        }, error -> errorDialog("油猴更新失敗", error));
    }

    private void uninstallMonkey() {
        if (tampermonkey == null) return;
        WebExtension extension = tampermonkey;
        new AlertDialog.Builder(this).setTitle("移除 Tampermonkey")
            .setMessage("解除安裝會清除油猴腳本及擴充套件資料，確定嗎？")
            .setPositiveButton("解除安裝", (d,w) ->
                controller.uninstall(extension).accept(v -> {
                    tampermonkey = null;
                    browserAction = null;
                    checkInstalled();
                }, error -> errorDialog("解除安裝失敗", error)))
            .setNegativeButton("取消", null).show();
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private void errorDialog(String title, Throwable throwable) {
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(String.valueOf(throwable.getMessage()))
            .setPositiveButton("確定", null).show();
    }

    @Override
    public void onBackPressed() {
        if (popupDialog != null && popupDialog.isShowing()) {
            popupDialog.dismiss();
        } else if (current != null && canGoBack) {
            current.goBack();
        } else if (tabs.size() > 1) {
            closeCurrentTab();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (popupDialog != null) popupDialog.dismiss();
        if (browserView != null && browserView.getSession() != null)
            browserView.releaseSession();
        for (GeckoSession s : tabs) if (s.isOpen()) s.close();
        tabs.clear();
        super.onDestroy();
    }
}
