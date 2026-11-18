package com.thkim.recipe;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
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
import java.io.OutputStream;
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
    static final int REQ_SAVE = 101, REQ_PICK = 102;
    String pendingSave = null;
    ValueCallback<Uri[]> fileCallback = null;

    // 웹 화면에서 부르는 기능 (백업 파일 저장, 요리 모드 화면 켜짐 유지)
    class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String content) {
            runOnUiThread(new Runnable() { public void run() {
                pendingSave = content;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) { notifySaved(false); }
            }});
        }
        // 요리 타이머 예약 (앱 밖에서도 알림)
        @JavascriptInterface
        public void scheduleTimer(int sec, String label, String dish) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() { public void run() {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 5);
                }});
            }
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            long at = System.currentTimeMillis() + sec * 1000L;
            PendingIntent pi = timerIntent(label, dish);
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception e) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
        @JavascriptInterface
        public void cancelTimer() {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            am.cancel(timerIntent(null, null));
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(7);
        }
        // 카톡 등으로 텍스트 공유
        @JavascriptInterface
        public void share(final String text) {
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(i, "레시피 보내기")); } catch (Exception ignored) {}
            }});
        }
        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(new Runnable() { public void run() {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }});
        }
    }

    PendingIntent timerIntent(String label, String dish) {
        Intent i = new Intent(this, TimerReceiver.class);
        if (label != null) i.putExtra("label", label);
        if (dish != null) i.putExtra("dish", dish);
        return PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void notifySaved(boolean ok) {
        web.evaluateJavascript("window.onAndroidSaved && window.onAndroidSaved(" + ok + ")", null);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SAVE) {
            boolean ok = false;
            if (res == RESULT_OK && data != null && data.getData() != null && pendingSave != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt");
                    os.write(pendingSave.getBytes("UTF-8"));
                    os.close();
                    ok = true;
                } catch (Exception ignored) {}
            }
            pendingSave = null;
            notifySaved(ok);
        } else if (req == REQ_PICK) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
                fileCallback = null;
            }
        }
    }

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

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient() { // confirm/alert 창 + 파일 선택(백업 불러오기)
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "백업 파일 선택"), REQ_PICK);
                } catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });
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
