package io.github.whalemiao.windtunnel;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 最小的 WebView 壳：全屏显示 assets/index.html。
 *
 * 关键点（都是踩过的）：
 *   1. DOM storage 要开 —— 页面用 localStorage 记预设
 *   2. 视图层不要设 loadWithOverviewMode —— 否则页面按 980px 桌面宽度渲染，
 *      媒体查询不命中，手机上侧边栏逻辑会走错分支
 *   3. **必须实现 onShowFileChooser** —— 否则 <input type="file"> 点了毫无反应，
 *      这是 WebView 与浏览器的最大差异之一
 *   4. onCreate 整段 try/catch：崩了把堆栈画屏幕上，不连 adb 也能看原因
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    showError("未捕获的异常（线程 " + t.getName() + "）", e);
                }
            });

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            goFullscreen();

            web = new WebView(this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(false);   // 别缩放到 980px 宽，否则按桌面版渲染

            web.setWebViewClient(new WebViewClient());
            web.setWebChromeClient(new WebChromeClient() {
                /** 网页里 <input type="file"> 被点击时走这里 */
                @Override
                public boolean onShowFileChooser(WebView view,
                                                 ValueCallback<Uri[]> callback,
                                                 FileChooserParams params) {
                    // 上次没消费掉的回调要先结束，否则选择器只能弹一次
                    if (fileCallback != null) {
                        fileCallback.onReceiveValue(null);
                        fileCallback = null;
                    }
                    fileCallback = callback;
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/*");
                        startActivityForResult(
                                Intent.createChooser(intent, "选择透明底 PNG"), REQ_FILE);
                        return true;
                    } catch (Throwable e) {
                        fileCallback = null;
                        Toast.makeText(MainActivity.this,
                                "没有可用的文件管理器", Toast.LENGTH_LONG).show();
                        return false;
                    }
                }
            });
            web.setBackgroundColor(0xFF05070D);

            setContentView(web);
            web.loadUrl("file:///android_asset/index.html");

        } catch (Throwable e) {
            showError("启动失败（onCreate）", e);
        }
    }

    /** 文件选择器的结果回到这里，再交还给网页 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                } else if (data.getClipData() != null) {
                    ClipData clip = data.getClipData();
                    result = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        result[i] = clip.getItemAt(i).getUri();
                    }
                }
            }
            if (fileCallback != null) {
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showError(String title, Throwable e) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(title).append("\n\n");
            sb.append("设备: ").append(Build.MANUFACTURER).append(" ")
              .append(Build.MODEL).append("  Android ").append(Build.VERSION.RELEASE)
              .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());

            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(0xFFFFDDDD);
            tv.setBackgroundColor(0xFF1A0E12);
            tv.setPadding(28, 28, 28, 28);
            tv.setTextIsSelectable(true);

            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(0xFF1A0E12);
            sv.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            setContentView(sv);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private void goFullscreen() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Throwable ignored) {
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        try {
            web.evaluateJavascript(
                    "(function(){ if (document.body.classList.contains('panel-open')) {"
                            + "document.body.classList.remove('panel-open'); return 'handled'; }"
                            + "return 'exit'; })()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (value == null || !value.contains("handled")) {
                                MainActivity.super.onBackPressed();
                            }
                        }
                    });
        } catch (Throwable e) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
                fileCallback = null;
            }
            if (web != null) web.destroy();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
