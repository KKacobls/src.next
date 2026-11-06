package com.kk.jsbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-page Android WebView browser with manual JS console.
 * No tabs or remote JS bridge. User-enabled scripts run only on matching pages.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 41;
    private static final int MEDIA_PERMISSIONS = 42;
    private static final int LOCATION_PERMISSIONS = 43;
    private static final String PREFS = "browser_settings";

    private WebView webView;
    private LinearLayout browserRoot;
    private FrameLayout browserBar;
    private BrowserExtras extras;
    private DownloadPage downloads;
    private View sidebarOverlay;
    private float edgeTouchStartX, edgeTouchStartY;
    private TextView pageTitle;
    private TextView pageHost;
    private LinearLayout headerInfo;
    private PopupWindow overflowPopup;
    private TextView overflowButton;
    private AutoScriptManager autoScripts;
    private View homeView;
    private ProgressBar loading;
    private Dialog consoleDialog;
    private TextView consoleOutput;
    private ScrollView consoleScroll;
    private final StringBuilder consoleHistory = new StringBuilder();
    private ValueCallback<Uri[]> filePickerCallback;
    private PermissionRequest mediaRequest;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences prefs = base.getSharedPreferences(PREFS, MODE_PRIVATE);
        // One-time repair for v1.0-v1.1.2 installs that kept an old system/English setting.
        // After this migration users can choose English or follow-system normally.
        if (!prefs.getBoolean("language_migrated_v113", false)) {
            prefs.edit().putString("language", "zh-Hant-TW")
                    .putBoolean("language_migrated_v113", true).commit();
        }
        String language = prefs.getString("language", "zh-Hant-TW");
        if ("system".equals(language)) {
            Locale.setDefault(base.getResources().getConfiguration().getLocales().get(0));
        } else {
            Locale locale = Locale.forLanguageTag(language);
            Locale.setDefault(locale);
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            base = base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(229, 230, 233));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        browserRoot = root;
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setStatusBarColor(Color.rgb(229, 230, 233));
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        // LINE-like header: iPhone mode has centered title; Android mode is left-aligned.
        FrameLayout bar = new FrameLayout(this);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(62)));
        browserBar = bar;

        headerInfo = new LinearLayout(this);
        headerInfo.setOrientation(LinearLayout.VERTICAL);
        bar.addView(headerInfo, new FrameLayout.LayoutParams(-1, -1));

        pageTitle = new TextView(this);
        pageTitle.setTextColor(Color.rgb(20, 22, 20));
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        pageTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        pageTitle.setSingleLine(true);
        pageTitle.setEllipsize(TextUtils.TruncateAt.END);
        headerInfo.addView(pageTitle);

        pageHost = new TextView(this);
        pageHost.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pageHost.setTextColor(Color.rgb(164, 166, 168));
        pageHost.setSingleLine(true);
        pageHost.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        headerInfo.addView(pageHost);

        // The first layer stays minimal; editing the URL and all tools live under ⋮.
        headerInfo.setOnClickListener(v -> showUrlDialog());
        headerInfo.setOnLongClickListener(v -> { showUrlDialog(); return true; });
        bar.setOnLongClickListener(v -> { showOverflowMenu(overflowButton); return true; });

        overflowButton = new TextView(this);
        overflowButton.setText("⋮");
        overflowButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        overflowButton.setTextColor(Color.rgb(23, 25, 23));
        overflowButton.setGravity(Gravity.CENTER);
        overflowButton.setContentDescription(getString(R.string.tools));
        FrameLayout.LayoutParams overflowParams = new FrameLayout.LayoutParams(dp(42), -1, Gravity.END);
        overflowParams.rightMargin = dp(50);
        bar.addView(overflowButton, overflowParams);
        overflowButton.setOnClickListener(v -> showOverflowMenu(overflowButton));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        close.setTextColor(Color.BLACK);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(R.string.close));
        close.setOnClickListener(v -> { if (extras != null) extras.rememberClosed(); finish(); });
        bar.addView(close, new FrameLayout.LayoutParams(dp(50), -1, Gravity.END));

        applyHeaderStyle();

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setVisibility(View.GONE);
        root.addView(loading, new LinearLayout.LayoutParams(-1, dp(2)));

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        webView = new WebView(this);
        frame.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        webView.setBackgroundColor(Color.WHITE);

        homeView = buildHome();
        frame.addView(homeView, new FrameLayout.LayoutParams(-1, -1));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        webView.setWebViewClient(createWebViewClient());
        webView.setWebChromeClient(createChromeClient());
        autoScripts = new AutoScriptManager(this, webView,
                getSharedPreferences(PREFS, MODE_PRIVATE), this::appendLog, () -> {});
        configureDownloads();
        extras = new BrowserExtras(this, webView, this::applyCustomTheme);
        extras.bindActions(autoScripts, this::navigate);
        extras.applyDesktop();
        downloads = new DownloadPage(this);
        applyCustomTheme();
        setupDeveloperSwipe(frame);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            String restoredUrl = webView.getUrl();
            homeView.setVisibility(restoredUrl == null || restoredUrl.equals("about:blank") ? View.VISIBLE : View.GONE);
            refreshHeader();
        } else if (!openExternalIntent(getIntent())) {
            String configured = extras.startUrl();
            if (!configured.isEmpty()) navigate(configured);
            else showHome();
        }
        askDefaultBrowserOnce();
    }

    private View buildHome() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.WHITE);
        view.setPadding(dp(25), dp(20), dp(25), dp(60));

        TextView logo = new TextView(this);
        logo.setText("{ JS }");
        logo.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        logo.setTextColor(Color.rgb(52, 60, 52));
        logo.setGravity(Gravity.CENTER);
        view.addView(logo, new LinearLayout.LayoutParams(-1, dp(70)));

        TextView headline = new TextView(this);
        headline.setText(R.string.home_main);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setTextColor(Color.rgb(32, 36, 32));
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(-1, -2);
        headlineParams.topMargin = dp(10);
        view.addView(headline, headlineParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.home_subtitle);
        subtitle.setTextColor(Color.rgb(124, 130, 124));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(14);
        view.addView(subtitle, subtitleParams);

        Button open = button(getString(R.string.open_url), true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonParams.topMargin = dp(27);
        view.addView(open, buttonParams);
        open.setOnClickListener(v -> showUrlDialog());
        return view;
    }

    private WebViewClient createWebViewClient() {
        return new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                String scheme = request.getUrl().getScheme();
                if (scheme == null || scheme.equals("http") || scheme.equals("https") ||
                        scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") ||
                        scheme.equals("javascript")) return false;
                openExternalLink(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                homeView.setVisibility("about:blank".equals(url) ? View.VISIBLE : View.GONE);
                loading.setVisibility("about:blank".equals(url) ? View.GONE : View.VISIBLE);
                autoScripts.onPageStarted();
                refreshHeader();
            }
            @Override public void onPageFinished(WebView view, String url) {
                refreshHeader();
                autoScripts.onPageFinished(url);
                if (extras != null) extras.recordPage(url, view.getTitle());
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                refreshHeader();
                autoScripts.onHistoryChanged(url);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) appendLog("[Page error] " + error.getDescription());
            }
        };
    }

    private WebChromeClient createChromeClient() {
        return new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                showPageJsDialog(url, message, result, false);
                return true;
            }
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                showPageJsDialog(url, message, result, true);
                return true;
            }
            @Override public void onReceivedTitle(WebView view, String title) { refreshHeader(); }
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
                if (progress >= 100) loading.setVisibility(View.GONE);
            }
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String prefix = message.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? "[ERROR] " : "[LOG] ";
                appendLog(prefix + message.message() + "  (line " + message.lineNumber() + ")");
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (extras != null) extras.rememberClosed();
        closeSidebar();
        if (filePickerCallback != null) filePickerCallback.onReceiveValue(null);
                filePickerCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePickerCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                runOnUiThread(() -> askForGeolocation(origin, callback));
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> askForMedia(request));
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (mediaRequest == request) mediaRequest = null;
            }
        };
    }

    private void refreshHeader() {
        if (webView == null) return;
        String url = webView.getUrl();
        if (url == null || url.equals("about:blank")) {
            pageTitle.setText(R.string.home_title);
            pageHost.setText(R.string.home_hint);
            return;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String title = webView.getTitle();
        pageTitle.setText(title == null || title.trim().isEmpty() || title.equals(url)
                ? (host == null ? url : host) : title);
        pageHost.setText(host == null ? url : host);
    }

    private void showUrlDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.url_hint);
        String current = webView.getUrl();
        if (current != null && !current.equals("about:blank")) {
            input.setText(current);
            input.selectAll();
        }
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(22), dp(4), dp(22), dp(4));
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));

        // Version is visible here as requested, and also at the bottom of the ⋮ menu.
        TextView version = new TextView(this);
        version.setText(getString(R.string.app_name) + " · v" + BuildConfig.VERSION_NAME);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        version.setTextColor(Color.rgb(153, 156, 158));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, dp(32));
        versionParams.topMargin = dp(8);
        wrapper.addView(version, versionParams);
        version.setOnClickListener(v -> extras.versionTapped());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.open_url)
                .setView(wrapper)
                .setPositiveButton(R.string.open, (d, which) -> navigate(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        styleDialogButtons(dialog);
        input.requestFocus();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private boolean isCenteredHeader() {
        return "ios".equals(getSharedPreferences(PREFS, MODE_PRIVATE).getString("header_style", "android"));
    }

    private void applyHeaderStyle() {
        if (headerInfo == null) return;
        boolean centered = isCenteredHeader();
        headerInfo.setGravity(centered ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        pageTitle.setGravity(centered ? Gravity.CENTER : Gravity.START);
        pageHost.setGravity(centered ? Gravity.CENTER : Gravity.START);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) headerInfo.getLayoutParams();
        params.leftMargin = dp(centered ? 108 : 18);
        params.rightMargin = dp(108);
        headerInfo.setLayoutParams(params);
    }

    private void chooseHeaderStyle(Button selector) {
        String[] labels = {getString(R.string.style_ios), getString(R.string.style_android)};
        int current = isCenteredHeader() ? 0 : 1;
        new AlertDialog.Builder(this).setTitle(R.string.header_style)
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString("header_style", which == 0 ? "ios" : "android").apply();
                    applyHeaderStyle();
                    if (selector != null) selector.setText(which == 0 ? "iPhone" : "Android");
                    dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void navigate(String typed) {
        String value = typed.trim();
        if (value.isEmpty()) return;
        if (value.regionMatches(true, 0, "javascript:", 0, 11)) {
            executeJs(value.substring(11));
            Toast.makeText(this, R.string.js_success, Toast.LENGTH_SHORT).show();
            return;
        }
        if (value.equalsIgnoreCase("about:blank")) { showHome(); return; }
        if (value.matches("(?i)^https?://.*")) {
            webView.loadUrl(value);
        } else if (value.contains(" ") || !(value.contains(".") || value.startsWith("localhost") || value.contains(":"))) {
            webView.loadUrl("https://www.google.com/search?q=" + Uri.encode(value));
        } else {
            boolean local = value.startsWith("localhost") || value.startsWith("127.") ||
                    value.startsWith("192.168.") || value.startsWith("10.") ||
                    value.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
            webView.loadUrl((local ? "http://" : "https://") + value);
        }
        homeView.setVisibility(View.GONE);
    }

    private void showHome() {
        if (extras != null) extras.rememberClosed();
        webView.loadUrl("about:blank");
        homeView.setVisibility(View.VISIBLE);
        pageTitle.setText(R.string.home_title);
        pageHost.setText(R.string.home_hint);
    }

    // Native, rounded overflow menu inspired by the Android in-app browser screenshot.
    // No tab manager: this browser intentionally keeps only one WebView.
    private void showOverflowMenu(View anchor) {
        if (anchor == null) return;
        if (overflowPopup != null && overflowPopup.isShowing()) {
            overflowPopup.dismiss();
            return;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(8));
        panel.setBackground(rounded(Color.WHITE, 19));

        String[][] actions = {
            {"⌕","網址"}, {"↻","重新整理"}, {"‹","返回"},
            {"⌂","首頁"}, {"↗","分享"}, {"◷","紀錄"},
            {"−","縮小"}, {"+","放大"},
            {"▣", getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("desktop_v120",false) ? "手機" : "電腦"}
        };
        Runnable[] commands = {
            this::showUrlDialog, () -> webView.reload(),
            () -> {if(webView.canGoBack())webView.goBack();else Toast.makeText(this,R.string.no_history,Toast.LENGTH_SHORT).show();},
            this::showHome, this::sharePage, extras::showHistory,
            () -> extras.zoom(false), () -> extras.zoom(true), extras::desktopToggle
        };
        for(int row=0;row<3;row++) {
            LinearLayout gridRow = new LinearLayout(this);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            panel.addView(gridRow,new LinearLayout.LayoutParams(-1,dp(73)));
            for(int col=0;col<3;col++) {
                int idx=row*3+col;
                addGridShortcut(gridRow,actions[idx][0],actions[idx][1],commands[idx]);
            }
        }
        addMenuDivider(panel);
        addMenuItemLabel(panel,"↶","重新開啟最近關閉頁面",()->{
            extras.reopenClosed();homeView.setVisibility(View.GONE);
        });
        addMenuItemLabel(panel,"⌕","在網頁中搜尋",extras::searchInPage);
        addMenuItemLabel(panel,"↓","下載紀錄",downloads::show);
        addMenuItemLabel(panel,"⚑","設定預設瀏覽器",this::requestDefaultBrowser);
        addMenuItemLabel(panel,"{}","JSON 設定匯入／匯出",extras::jsonSettings);
        addMenuItemLabel(panel,"文","語言設定",this::chooseLanguage);
        addMenuItem(panel,"⚙",R.string.app_permissions,this::openAppPermissions);
        if (extras.developerEnabled()) {
            addMenuDivider(panel);
            addMenuItemLabel(panel,"⌘","開發者工具",this::showDeveloperTools);
            addMenuItemLabel(panel,"◇","關閉開發者模式",()->{extras.disableDeveloper();closeSidebar();});
        }
        addMenuDivider(panel);

        TextView version = new TextView(this);
        version.setText(getString(R.string.app_name) + "  ·  v" + BuildConfig.VERSION_NAME);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        version.setTextColor(Color.rgb(157, 159, 161));
        version.setGravity(Gravity.CENTER);
        panel.addView(version, new LinearLayout.LayoutParams(-1, dp(43)));
        version.setOnClickListener(v -> extras.versionTapped());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(rounded(Color.WHITE, 19));
        scroll.addView(panel);
        int width = Math.min(dp(310), getResources().getDisplayMetrics().widthPixels - dp(24));
        int[] pos = new int[2];
        anchor.getLocationOnScreen(pos);
        int top = pos[1] + anchor.getHeight() - dp(3);
        int available = getResources().getDisplayMetrics().heightPixels - top - dp(16);
        int height = Math.min(dp(690), Math.max(dp(225), available));
        PopupWindow popup = new PopupWindow(scroll, width, height, true);
        popup.setBackgroundDrawable(rounded(Color.WHITE, 19));
        popup.setElevation(dp(13));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        popup.setOnDismissListener(() -> {
            if (overflowPopup == popup) overflowPopup = null;
        });
        overflowPopup = popup;
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.RIGHT, dp(10), top);
    }

    private void addGridShortcut(LinearLayout parent,String glyph,String title,Runnable command) {
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setContentDescription(title);
        parent.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
        TextView symbol=new TextView(this);
        symbol.setText(glyph);
        symbol.setTextColor(Color.rgb(28,32,32));
        symbol.setTextSize(TypedValue.COMPLEX_UNIT_SP,25);
        symbol.setGravity(Gravity.CENTER);
        cell.addView(symbol,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView text=new TextView(this);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
        text.setTextColor(Color.rgb(75,76,76));
        text.setGravity(Gravity.CENTER);
        cell.addView(text);
        cell.setOnClickListener(v->{dismissOverflow();command.run();});
    }

    private void addMenuShortcut(LinearLayout parent, String glyph, int title, Runnable command) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        parent.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextColor(Color.rgb(25, 26, 25));
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        icon.setGravity(Gravity.CENTER);
        cell.addView(icon, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.rgb(40, 41, 40));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setGravity(Gravity.CENTER);
        cell.addView(label);
        cell.setOnClickListener(v -> {
            dismissOverflow();
            command.run();
        });
    }

    private void addMenuItem(LinearLayout panel, String glyph, int title, Runnable command) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), 0, dp(8), 0);
        panel.addView(item, new LinearLayout.LayoutParams(-1, dp(50)));
        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, glyph.length() > 1 ? 16 : 23);
        icon.setTextColor(Color.rgb(36, 38, 36));
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(38), -1));
        TextView label = new TextView(this);
        label.setText(title);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setTextColor(Color.rgb(39, 40, 39));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.leftMargin = dp(8);
        item.addView(label, textParams);
        item.setOnClickListener(v -> {
            dismissOverflow();
            command.run();
        });
    }


    private void addMenuItemLabel(LinearLayout parent, String glyph, String title, Runnable action) {
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10),0,dp(8),0);
        parent.addView(item,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView symbol=new TextView(this);
        symbol.setText(glyph);
        symbol.setTextSize(TypedValue.COMPLEX_UNIT_SP, glyph.length()>1?16:23);
        symbol.setGravity(Gravity.CENTER);
        symbol.setTextColor(Color.rgb(36,38,36));
        item.addView(symbol,new LinearLayout.LayoutParams(dp(38),-1));
        TextView text=new TextView(this);
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP,15);
        text.setTextColor(Color.rgb(39,40,39));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.leftMargin=dp(8);
        item.addView(text,p);
        item.setOnClickListener(v->{dismissOverflow();action.run();});
    }

    private void showDeveloperTools() {
        if(!extras.developerEnabled()) return;
        String toggle = extras.sidebarEnabled() ? "停用右側快捷欄" : "啟用右側快捷欄";
        String[] options={"JS 主控台","自動 JS 腳本","檢視網頁原始碼","CSS／介面屬性","介面風格","工具列位置",toggle,"JSON 設定"};
        new AlertDialog.Builder(this).setTitle("開發者工具")
            .setItems(options,(d,which)->{
                switch(which){
                    case 0: showConsole();break;
                    case 1: autoScripts.showManager();break;
                    case 2: extras.viewSource();break;
                    case 3: extras.editStyle();break;
                    case 4: extras.choosePreset();break;
                    case 5: extras.chooseToolbarPosition();break;
                    case 6: extras.toggleSidebar();break;
                    case 7: extras.jsonSettings();break;
                }
            }).setNegativeButton("關閉",null).show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDeveloperSwipe(FrameLayout frame) {
        View edge=new View(this);
        FrameLayout.LayoutParams edgeParams=new FrameLayout.LayoutParams(dp(16),-1,Gravity.RIGHT);
        frame.addView(edge,edgeParams);
        edge.setOnTouchListener((v,event)->{
            if(extras==null || !extras.sidebarEnabled())return false;
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                edgeTouchStartX=event.getRawX();edgeTouchStartY=event.getRawY();return true;
            }
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                if(edgeTouchStartX-event.getRawX()>dp(65) && Math.abs(edgeTouchStartY-event.getRawY())<dp(160)){
                    openSidebar();return true;
                }
            }
            return true;
        });
    }

    private void openSidebar() {
        if(extras==null || !extras.sidebarEnabled() || sidebarOverlay!=null)return;
        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(0x55000000);
        sidebarOverlay=overlay;
        addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
        overlay.setOnClickListener(v->closeSidebar());
        LinearLayout drawer=new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(14),dp(28),dp(14),dp(12));
        drawer.setBackground(rounded(Color.WHITE,18));
        FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(
            Math.min(dp(294),(int)(getResources().getDisplayMetrics().widthPixels*.82f)),-1,Gravity.RIGHT);
        overlay.addView(drawer,placement);
        drawer.setOnClickListener(v->{});
        TextView title=new TextView(this);
        title.setText("開發者快捷工具");
        title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);title.setPadding(dp(8),0,0,dp(15));
        drawer.addView(title);
        String[] labels={"JS 主控台","自動 JS 腳本","View Source","CSS 樣式","介面風格","JSON 設定"};
        Runnable[] actions={this::showConsole,autoScripts::showManager,extras::viewSource,
            extras::editStyle,extras::choosePreset,extras::jsonSettings};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            Button button=new Button(this);
            button.setAllCaps(false);button.setText(labels[i]);
            drawer.addView(button,new LinearLayout.LayoutParams(-1,dp(53)));
            button.setOnClickListener(v->{closeSidebar();actions[index].run();});
        }
        drawer.setTranslationX(dp(300));
        drawer.animate().translationX(0).setDuration(185).start();
    }
    private void closeSidebar(){
        if(sidebarOverlay==null)return;
        View view=sidebarOverlay;sidebarOverlay=null;
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
    }

    private void applyCustomTheme() {
        if(extras==null || browserRoot==null || browserBar==null || pageTitle==null) return;
        extras.applyTheme(browserRoot,pageTitle,pageHost,browserBar,overflowButton);
        applyHeaderStyle();
    }

    private void addMenuDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(237, 238, 238));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.setMargins(dp(9), dp(6), dp(9), dp(6));
        parent.addView(line, p);
    }

    private void dismissOverflow() {
        if (overflowPopup != null) overflowPopup.dismiss();
    }

    private void sharePage() {
        String url = webView.getUrl();
        if (url == null || url.equals("about:blank")) {
            Toast.makeText(this, R.string.no_webpage, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(share, getString(R.string.share)));
    }

    private void openAppPermissions() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (Exception ignored) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void styleDialogButtons(AlertDialog dialog) {
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) positive.setTextColor(Color.rgb(132, 190, 124));
        if (negative != null) negative.setTextColor(Color.rgb(38, 38, 38));
    }

    // The browser owns these dialogs; the website CSS is not changed.
    // The native dialog is drawn on a transparent window so its corners are truly rounded.
    private void showPageJsDialog(String url, String message, JsResult result, boolean alert) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> result.cancel());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(rounded(Color.WHITE, 18));
        body.setClipToOutline(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));

        TextView host = new TextView(this);
        String domain = null;
        try { if (url != null) domain = Uri.parse(url).getHost(); }
        catch (Exception ignored) { }
        host.setText(domain == null ? getString(R.string.app_name) : domain);
        host.setTextColor(Color.rgb(24, 24, 24));
        host.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        host.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        host.setGravity(Gravity.CENTER);
        content.addView(host, new LinearLayout.LayoutParams(-1, -2));

        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(Color.rgb(121, 124, 123));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(14);
        messageParams.bottomMargin = 0;
        content.addView(text, messageParams);

        // Explicit equal weights make Cancel and Confirm occupy exactly half each.
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));
        if (!alert) {
            TextView cancel = jsDialogAction(getString(R.string.cancel), Color.rgb(36, 36, 36));
            actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1f));
            cancel.setOnClickListener(v -> {
                dialog.dismiss();
                result.cancel();
            });
        }
        TextView confirm = jsDialogAction(getString(R.string.confirm), Color.rgb(132, 190, 124));
        actions.addView(confirm, new LinearLayout.LayoutParams(0, -1, 1f));
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            result.confirm();
        });
        // An alert has only one action, occupying the full width.

        dialog.setContentView(body);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(340));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.48f;
            window.setAttributes(params);
        }
    }

    private TextView jsDialogAction(String label, int color) {
        TextView action = new TextView(this);
        action.setText(label);
        action.setTextColor(color);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    /** In-page JavaScript (not a Linux shell). No unsafe remote @JavascriptInterface. */
    private void executeJs(String code) {
        if (code.trim().isEmpty()) return;
        if (code.regionMatches(true, 0, "javascript:", 0, 11)) code = code.substring(11);
        String snippet = code;
        appendLog("> " + snippet);
        webView.evaluateJavascript(snippet, result -> {
            if (result != null && !result.equals("null") && !result.equals("\"undefined\"")) {
                appendLog("< " + result);
            }
        });
    }

    private void showConsole() {
        if (consoleDialog != null && consoleDialog.isShowing()) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(12));
        panel.setBackgroundColor(Color.rgb(17, 22, 28));

        TextView label = new TextView(this);
        label.setText("›_  " + getString(R.string.console));
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setTextColor(Color.rgb(133, 201, 255));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        panel.addView(label, new LinearLayout.LayoutParams(-1, dp(45)));

        consoleScroll = new ScrollView(this);
        consoleScroll.setFillViewport(true);
        consoleOutput = new TextView(this);
        consoleOutput.setText(consoleHistory);
        consoleOutput.setTextColor(Color.rgb(190, 224, 193));
        consoleOutput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        consoleOutput.setTypeface(Typeface.MONOSPACE);
        consoleOutput.setTextIsSelectable(true);
        consoleOutput.setPadding(dp(4), dp(6), dp(4), dp(8));
        consoleScroll.addView(consoleOutput);
        panel.addView(consoleScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        EditText input = new EditText(this);
        input.setHint(R.string.console_hint);
        input.setHintTextColor(Color.rgb(130, 148, 165));
        input.setTextColor(Color.WHITE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackground(rounded(Color.rgb(32, 42, 55), 8));
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(10);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(53));
        actionsParams.topMargin = dp(8);
        panel.addView(actions, actionsParams);

        Button clear = button(getString(R.string.clear), false);
        Button close = button(getString(R.string.close), false);
        Button run = button("▶ " + getString(R.string.run), true);
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(close, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(run, new LinearLayout.LayoutParams(0, dp(44), 1.25f));
        clear.setOnClickListener(v -> {
            consoleHistory.setLength(0);
            consoleOutput.setText("");
        });
        close.setOnClickListener(v -> dialog.dismiss());
        run.setOnClickListener(v -> {
            String code = input.getText().toString();
            if (code.trim().isEmpty()) return;
            executeJs(code);
            input.setText("");
            ScrollView scroll = consoleScroll;
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });

        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> {
            consoleDialog = null;
            consoleOutput = null;
            consoleScroll = null;
        });
        consoleDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * .80f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setGravity(Gravity.BOTTOM);
        }
        consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            consoleHistory.append(message).append('\n');
            if (consoleHistory.length() > 25000) consoleHistory.delete(0, consoleHistory.length() - 20000);
            if (consoleOutput != null) consoleOutput.setText(consoleHistory);
            ScrollView scroll = consoleScroll;
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void chooseLanguage() {
        String[] choices = { getString(R.string.system_language), getString(R.string.traditional_chinese), getString(R.string.english) };
        String[] keys = { "system", "zh-Hant-TW", "en" };
        String current = getSharedPreferences(PREFS, MODE_PRIVATE).getString("language", "zh-Hant-TW");
        int selected = current.startsWith("zh") ? 1 : current.startsWith("en") ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.language)
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("language", keys[which]).apply();
                    dialog.dismiss();
                    recreate();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void clearBrowsingData() {
        new AlertDialog.Builder(this).setTitle(R.string.clear_data)
                .setMessage(R.string.clear_data_question)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    CookieManager manager = CookieManager.getInstance();
                    manager.removeAllCookies(null);
                    manager.flush();
                    WebStorage.getInstance().deleteAllData();
                    webView.clearCache(true);
                    webView.clearHistory();
                    consoleHistory.setLength(0);
                    showHome();
                    Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void configureDownloads() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            try {
                if (!url.startsWith("http://") && !url.startsWith("https://")) throw new IllegalArgumentException("Download URL");
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        .replace('/', '_').replace('\\', '_');
                request.setTitle(fileName);
                if (mimeType != null) request.setMimeType(mimeType);
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if (Build.VERSION.SDK_INT >= 29) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                } else {
                    request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
                }
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager == null) throw new IllegalStateException("No DownloadManager");
                long downloadId=manager.enqueue(request);
                downloads.record(downloadId,fileName,url);
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                appendLog("[Download error] " + e.getMessage());
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void askForMedia(PermissionRequest request) {
        new AlertDialog.Builder(this).setTitle(R.string.allow)
                .setMessage(getString(R.string.permission_request, request.getOrigin().toString()))
                .setPositiveButton(R.string.allow, (dialog, which) -> {
                    mediaRequest = request;
                    List<String> needed = new ArrayList<>();
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                            needed.add(Manifest.permission.CAMERA);
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                            needed.add(Manifest.permission.RECORD_AUDIO);
                    }
                    if (needed.isEmpty()) grantMediaIfAllowed();
                    else requestPermissions(needed.toArray(new String[0]), MEDIA_PERMISSIONS);
                })
                .setNegativeButton(R.string.deny, (dialog, which) -> request.deny())
                .setOnCancelListener(dialog -> request.deny())
                .show();
    }

    private void grantMediaIfAllowed() {
        if (mediaRequest == null) return;
        List<String> allowed = new ArrayList<>();
        for (String resource : mediaRequest.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                allowed.add(resource);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                allowed.add(resource);
        }
        if (allowed.isEmpty()) mediaRequest.deny();
        else mediaRequest.grant(allowed.toArray(new String[0]));
        mediaRequest = null;
    }

    private void askForGeolocation(String origin, GeolocationPermissions.Callback callback) {
        new AlertDialog.Builder(this).setTitle(R.string.allow)
                .setMessage(getString(R.string.location_request, origin))
                .setPositiveButton(R.string.allow, (dialog, which) -> {
                    if (hasLocationPermission()) callback.invoke(origin, true, false);
                    else {
                        geolocationCallback = callback;
                        geolocationOrigin = origin;
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSIONS);
                    }
                }).setNegativeButton(R.string.deny, (dialog, which) -> callback.invoke(origin, false, false))
                .setOnCancelListener(dialog -> callback.invoke(origin, false, false)).show();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSIONS) grantMediaIfAllowed();
        if (requestCode == LOCATION_PERMISSIONS && geolocationCallback != null) {
            geolocationCallback.invoke(geolocationOrigin, hasLocationPermission(), false);
            geolocationCallback = null;
            geolocationOrigin = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && filePickerCallback != null) {
            filePickerCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePickerCallback = null;
        }
    }

    private boolean openExternalIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri uri = intent.getData();
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                navigate(uri.toString());
                return true;
            }
        }
        return false;
    }

    /** Always show origin, destination and target application before external navigation. */
    private void openExternalLink(String url) {
        try {
            Intent external;
            if (url.startsWith("intent://")) {
                external=Intent.parseUri(url,Intent.URI_INTENT_SCHEME);
                external.addCategory(Intent.CATEGORY_BROWSABLE);
                external.setComponent(null);
                external.setSelector(null);
            } else {
                external=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
                external.addCategory(Intent.CATEGORY_BROWSABLE);
            }
            String source=webView==null?"":webView.getUrl();
            android.content.pm.ResolveInfo resolved=getPackageManager().resolveActivity(external,0);
            String packageId=external.getPackage();
            if(packageId==null && resolved!=null && resolved.activityInfo!=null)
                packageId=resolved.activityInfo.packageName;
            if(packageId==null)packageId="未知";
            String appName=resolved==null?"外部應用程式":String.valueOf(resolved.loadLabel(getPackageManager()));
            final String targetPackage=packageId;
            final String rememberKey="external_approval_"+targetPackage;
            SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
            if(p.getBoolean(rememberKey,false)){
                try{startActivity(external);return;}catch(Exception ignored){}
            }
            LinearLayout details=new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(20),dp(8),dp(20),dp(5));
            TextView label=new TextView(this);
            label.setText("要使用 "+appName+" 開啟這個連結嗎？");
            label.setTextSize(18);
            details.addView(label);
            if(resolved!=null){
                try {
                    android.widget.ImageView appIcon=new android.widget.ImageView(this);
                    appIcon.setImageDrawable(resolved.loadIcon(getPackageManager()));
                    LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(54),dp(54));
                    iconParams.topMargin=dp(8);
                    iconParams.bottomMargin=dp(5);
                    details.addView(appIcon,iconParams);
                } catch(Exception ignored){}
            }
            android.widget.CheckBox remember=new android.widget.CheckBox(this);
            remember.setText("記住此 App 的開啟選擇");
            TextView info=new TextView(this);
            info.setText("來源網址：\n"+source+"\n\n目的地網址：\n"+url+
                "\n\n應用程式：\n"+appName+"\n\n套件識別碼：\n"+targetPackage);
            info.setTextIsSelectable(true);
            info.setPadding(0,dp(10),0,dp(12));
            info.setVisibility(View.GONE);
            Button expand=new Button(this);
            expand.setAllCaps(false);expand.setText("顯示詳細資訊 ▾");
            expand.setOnClickListener(v->{
                boolean show=info.getVisibility()!=View.VISIBLE;
                info.setVisibility(show?View.VISIBLE:View.GONE);
                expand.setText(show?"隱藏詳細資訊 ▴":"顯示詳細資訊 ▾");
            });
            details.addView(expand);
            details.addView(info);
            details.addView(remember);
            final Intent launchIntent=external;
            new AlertDialog.Builder(this).setView(details)
                .setPositiveButton("在應用程式中開啟",(d,w)->{
                    try {
                        startActivity(launchIntent);
                        if(remember.isChecked())p.edit().putBoolean(rememberKey,true).apply();
                    }catch(Exception e){
                        String fallback=launchIntent.getStringExtra("browser_fallback_url");
                        if(fallback!=null && (fallback.startsWith("https://")||fallback.startsWith("http://")))navigate(fallback);
                        else Toast.makeText(this,"無法開啟此應用程式",Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("留在瀏覽器",null).show();
        } catch(Exception e){
            Toast.makeText(this,"無法辨識外部連結",Toast.LENGTH_SHORT).show();
        }
    }

    private void requestDefaultBrowser(){
        try{
            if(android.os.Build.VERSION.SDK_INT>=29){
                android.app.role.RoleManager role=(android.app.role.RoleManager)getSystemService(ROLE_SERVICE);
                if(role!=null && role.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)){
                    if(role.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)){
                        Toast.makeText(this,"已設定為預設瀏覽器",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivityForResult(role.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),54);
                    return;
                }
            }
            startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
        }catch(Exception e){openAppPermissions();}
    }

    private void askDefaultBrowserOnce(){
        SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        if(p.getBoolean("default_browser_asked_v130",false))return;
        p.edit().putBoolean("default_browser_asked_v130",true).apply();
        new AlertDialog.Builder(this).setTitle("預設瀏覽器")
            .setMessage("要將 JS Lite Browser 設為預設瀏覽器嗎？")
            .setPositiveButton("設定", (d,w)->requestDefaultBrowser())
            .setNegativeButton("稍後",null).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openExternalIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (sidebarOverlay != null) closeSidebar();
        else if (consoleDialog != null && consoleDialog.isShowing()) consoleDialog.dismiss();
        else if (overflowPopup != null && overflowPopup.isShowing()) overflowPopup.dismiss();
        else if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (filePickerCallback != null) filePickerCallback.onReceiveValue(null);
        if (mediaRequest != null) mediaRequest.deny();
        if (consoleDialog != null) consoleDialog.dismiss();
        if (overflowPopup != null) overflowPopup.dismiss();
        if (webView != null) {
            webView.stopLoading();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private Button button(String text, boolean prominent) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setAllCaps(false);
        button.setTextColor(prominent ? Color.WHITE : Color.rgb(174, 186, 198));
        button.setBackground(rounded(prominent ? Color.rgb(52, 60, 52) : Color.rgb(38, 47, 57), 8));
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private int dp(int dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }
}
