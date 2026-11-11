package com.brainengineering.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.ValueCallback;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String SITE = "https://headless-wxhswlnyfgz-sigridreilly1996-1410.wix-site-host.com/";
    private static final String HOST = "headless-wxhswlnyfgz-sigridreilly1996-1410.wix-site-host.com";
    private static final int REQUEST_FILE = 42;
    private WebView web;
    private ValueCallback<Uri[]> files;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 42, 54));
        getWindow().setNavigationBarColor(Color.rgb(16, 42, 54));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(248, 250, 248));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web, new ViewGroup.LayoutParams(-1,-1));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            web.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets sizes =
                        insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(sizes.left, sizes.top, sizes.right, sizes.bottom);
                return insets;
            });
        }

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri url = req.getUrl();
                if ("https".equalsIgnoreCase(url.getScheme()) && HOST.equalsIgnoreCase(url.getHost())) {
                    return false;
                }
                external(url);
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) offline();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse response) {
                if (req.isForMainFrame() && response.getStatusCode() >= 400) offline();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                offline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (files != null) files.onReceiveValue(null);
                files = callback;
                try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE);
                    return true;
                } catch (Exception ex) {
                    files = null;
                    Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                external(uri);
                return;
            }
            try {
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setMimeType(mime);
                request.addRequestHeader("User-Agent", agent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String name = android.webkit.URLUtil.guessFileName(url, disposition, mime);
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager != null) manager.enqueue(request);
            } catch (Exception ex) {
                external(uri);
            }
        });
        if (state != null) web.restoreState(state);
        else web.loadUrl(SITE);
    }

    private void external(Uri url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (Exception ex) {
            Toast.makeText(this, R.string.external_error, Toast.LENGTH_SHORT).show();
        }
    }
    private void offline() {
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font:17px Arial;background:#f8faf8;color:#102a36;"
                + "display:grid;place-items:center;height:90vh;text-align:center}"
                + "a{background:#006e75;color:white;padding:14px 24px;border-radius:12px;text-decoration:none}</style>"
                + "<main><h1>BrainEngineering</h1><p>" + getString(R.string.offline) + "</p>"
                + "<a href='" + SITE + "'>" + getString(R.string.retry) + "</a></main>";
        web.loadDataWithBaseURL(SITE, html, "text/html", "UTF-8", null);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_FILE && files != null) {
            files.onReceiveValue(result == RESULT_OK
                    ? WebChromeClient.FileChooserParams.parseResult(result, data) : null);
            files = null;
        }
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if (files != null) files.onReceiveValue(null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}