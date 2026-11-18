package com.thkim.recipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // 앱 화면: GitHub Pages의 최신 버전을 불러옴 (GitHub에 올리면 앱도 자동으로 바뀜)
    static final String BASE_URL = "https://thkim3589-web.github.io/Recipe/";
    static final String APP_URL = BASE_URL + "recipe-finder.html";
    // APK 새 버전 확인용
    static final String VERSION_URL =
            "https://raw.githubusercontent.com/thkim3589-web/Recipe/main/apk/version.json";

    WebView web;
    boolean usedFallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#B23A2E"));

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebChromeClient(new WebChromeClient()); // confirm/alert 창 표시
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith(BASE_URL)) return false;
                // 유튜브 등 외부 링크는 해당 앱/브라우저로 열기
                try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl())); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) loadBundled();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse resp) {
                if (req.isForMainFrame() && resp.getStatusCode() >= 400) loadBundled();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(APP_URL);

        checkForUpdate();
    }

    // 인터넷이 없거나 사이트가 안 열리면 APK에 들어있는 화면을 사용
    void loadBundled() {
        if (usedFallback) return;
        usedFallback = true;
        try {
            InputStream in = getAssets().open("index.html");
            String html = new String(readAll(in), "UTF-8");
            web.loadDataWithBaseURL(APP_URL, html, "text/html", "UTF-8", null);
        } catch (Exception ignored) {}
    }

    void checkForUpdate() {
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                JSONObject j = new JSONObject(new String(readAll(c.getInputStream()), "UTF-8"));
                int latest = j.optInt("versionCode", 0);
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                if (latest <= pi.versionCode) return;
                final String name = j.optString("versionName", "");
                final String notes = j.optString("notes", "");
                final String apkUrl = j.optString("apkUrl", "");
                if (apkUrl.isEmpty()) return;
                runOnUiThread(new Runnable() { public void run() { new AlertDialog.Builder(MainActivity.this)
                        .setTitle("새 버전 " + name + " 업데이트")
                        .setMessage((notes.isEmpty() ? "" : notes + "\n\n") + "새 APK를 내려받아 설치하면 저장한 레시피는 그대로 유지돼요.")
                        .setPositiveButton("업데이트", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))); } catch (Exception ignored) {}
                            }
                        })
                        .setNegativeButton("나중에", null)
                        .show(); }});
            } catch (Exception ignored) {}
        }}).start();
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
