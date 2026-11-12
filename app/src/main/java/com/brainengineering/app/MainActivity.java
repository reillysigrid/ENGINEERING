package com.brainengineering.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Native Android shell with two fully offline educational modules.
 * The existing live website is preserved in its own tab; no demo form is
 * represented as an authenticated or server-backed service.
 */
public final class MainActivity extends Activity {
    private static final String SITE = "https://headless-wxhswlnyfgz-sigridreilly1996-1410.wix-site-host.com/";
    private static final String WIX_HOST = "headless-wxhswlnyfgz-sigridreilly1996-1410.wix-site-host.com";
    private static final int FILE_PICKER_REQUEST = 404;
    static final int NAVY = Color.rgb(16, 42, 54);
    static final int TEAL = Color.rgb(7, 113, 113);
    static final int CREAM = Color.rgb(247, 250, 249);

    private WebView web;
    private FrameLayout content;
    private View plannerView;
    private View practiceView;
    private Button webTab, planTab, practiceTab;
    private StudyTools studyTools;
    private ValueCallback<Uri[]> pendingFiles;
    private int selectedTab = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        // Android 15/16 enforce transparent status bars for API 36 apps.
        // Our page background is light: show dark status icons for contrast.
        if (Build.VERSION.SDK_INT >= 35 && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CREAM);
        root.setFitsSystemWindows(true);
        // Keep the keyboard closed until the learner taps the goal field.
        root.setFocusableInTouchMode(true);
        setContentView(root);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Android 15+ may enforce edge-to-edge. Keep all interactive controls
        // inside the system bars without relying on AndroidX libraries.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left; right = bars.right; top = bars.top; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(9), dp(12), dp(9));
        header.setBackgroundColor(NAVY);
        TextView brand = new TextView(this);
        brand.setText("BrainEngineering");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(20);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setContentDescription("BrainEngineering");
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button contact = headerButton(getString(R.string.contact));
        contact.setOnClickListener(v -> emailContact());
        header.addView(contact, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(43)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        content = new FrameLayout(this);
        content.setBackgroundColor(CREAM);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        web = createWebView(savedInstanceState);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        studyTools = new StudyTools(this);
        plannerView = studyTools.createPlanner();
        practiceView = studyTools.createPractice();
        content.addView(plannerView, new FrameLayout.LayoutParams(-1, -1));
        content.addView(practiceView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(7), dp(5), dp(7), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        webTab = tabButton(getString(R.string.tab_portal));
        planTab = tabButton(getString(R.string.tab_planner));
        practiceTab = tabButton(getString(R.string.tab_practice));
        nav.addView(webTab, weighted()); nav.addView(planTab, weighted()); nav.addView(practiceTab, weighted());
        webTab.setOnClickListener(v -> showTab(0));
        planTab.setOnClickListener(v -> showTab(1));
        practiceTab.setOnClickListener(v -> showTab(2));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(62)));

        // Start with an always-available native learning screen on Samsung and other devices.
        // The Wix portal remains one tap away and loads when connected.
        root.requestFocus();
        showTab(savedInstanceState == null ? 1 : savedInstanceState.getInt("tab", 1));
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(51), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }
    private Button tabButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(dp(2),0,dp(2),0);
        return b;
    }
    private Button headerButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTextSize(13);
        b.setPadding(dp(11),0,dp(11),0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(TEAL); shape.setCornerRadius(dp(10));
        b.setBackground(shape);
        return b;
    }
    private void showTab(int index) {
        selectedTab = index >= 0 && index <= 2 ? index : 0;
        web.setVisibility(selectedTab == 0 ? View.VISIBLE : View.GONE);
        plannerView.setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
        practiceView.setVisibility(selectedTab == 2 ? View.VISIBLE : View.GONE);
        updateTab(webTab, selectedTab == 0);
        updateTab(planTab, selectedTab == 1);
        updateTab(practiceTab, selectedTab == 2);
    }
    private void updateTab(Button button, boolean active) {
        button.setTextColor(active ? Color.WHITE : NAVY);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? TEAL : Color.rgb(236, 245, 243));
        bg.setCornerRadius(dp(13));
        button.setBackground(bg);
    }
    private WebView createWebView(Bundle saved) {
        WebView view = new WebView(this);
        view.setBackgroundColor(CREAM);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);  // required by the existing Wix page
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, String url) {
                return route(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) {
                return !req.isForMainFrame() ? false : route(req.getUrl());
            }
            @Override public void onReceivedError(WebView w, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) offlineScreen(w);
            }
            @Override public void onReceivedHttpError(WebView w, WebResourceRequest req, WebResourceResponse response) {
                if (req.isForMainFrame() && response.getStatusCode() >= 400) offlineScreen(w);
            }
            @Override public void onReceivedSslError(WebView w, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (error.getUrl() != null && error.getUrl().startsWith(SITE)) offlineScreen(w);
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER_REQUEST);
                    return true;
                } catch (Exception ex) {
                    pendingFiles = null;
                    Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        view.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) { openExternal(uri); return; }
            try {
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setMimeType(mime);
                request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                        android.webkit.URLUtil.guessFileName(url, disposition, mime));
                DownloadManager downloads = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (downloads != null) downloads.enqueue(request);
            } catch (Exception ex) { openExternal(uri); }
        });
        if (saved != null) view.restoreState(saved);
        else view.loadUrl(SITE);
        return view;
    }
    private boolean route(Uri url) {
        if ("https".equalsIgnoreCase(url.getScheme()) && (
                WIX_HOST.equalsIgnoreCase(url.getHost()) ||
                "brainengineering.es".equalsIgnoreCase(url.getHost()) ||
                "www.brainengineering.es".equalsIgnoreCase(url.getHost()))) return false;
        openExternal(url);
        return true;
    }
    private void openExternal(Uri url) {
        String scheme = url.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http") ||
                scheme.equalsIgnoreCase("mailto") || scheme.equalsIgnoreCase("tel"))) {
            Toast.makeText(this, R.string.external_error, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(this, R.string.external_error, Toast.LENGTH_SHORT).show();
        }
    }
    private void emailContact() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:sigridreilly1996@gmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_subject));
        try { startActivity(intent); }
        catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.external_error, Toast.LENGTH_SHORT).show();
        }
    }
    private void offlineScreen(WebView view) {
        // Do not invent a server-side fallback. The native study tools remain available offline.
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font:17px Arial;background:#f7faf9;color:#102a36;padding:30px;text-align:center}"
                + "a{display:inline-block;background:#077171;color:white;padding:14px 22px;border-radius:12px;text-decoration:none}</style>"
                + "<h1>BrainEngineering</h1><p>" + getString(R.string.offline) + "</p>"
                + "<p>" + getString(R.string.offline_tools) + "</p>"
                + "<a href='" + SITE + "'>" + getString(R.string.retry) + "</a>";
        view.loadDataWithBaseURL(SITE, html, "text/html", "UTF-8", null);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER_REQUEST && pendingFiles != null) {
            pendingFiles.onReceiveValue(result == RESULT_OK ?
                    WebChromeClient.FileChooserParams.parseResult(result, data) : null);
            pendingFiles = null;
        }
    }
    @Override public void onBackPressed() {
        if (selectedTab != 0) showTab(0);
        else if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putInt("tab", selectedTab);
        if (web != null) web.saveState(saved);
        super.onSaveInstanceState(saved);
    }
    @Override protected void onDestroy() {
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles = null; }
        if (studyTools != null) studyTools.shutdown();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
    int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
