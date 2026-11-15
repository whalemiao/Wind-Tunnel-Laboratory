package io.github.whalemiao.windtunnel;

import android.annotation.SuppressLint;
import android.app.Activity;
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

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 最小的 WebView 壳：全屏显示 assets/index.html。
 *
 * 这一版刻意写得"笨"：
 *   · 不碰 WindowInsetsController（那是 API 30+ 的类，旧机上可能触发类校验失败）
 *   · 不用 lambda（避免脱糖器在旧 ART 上出问题）
 *   · onCreate 整段 try/catch —— 崩了就把堆栈画在屏幕上，不用连 adb 也能看原因
 */
public class MainActivity extends Activity {

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // 线程级兜底：万一还有没抓到的异常，也画到屏幕上
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
            s.setLoadWithOverviewMode(true);

            web.setWebViewClient(new WebViewClient());
            web.setWebChromeClient(new WebChromeClient());
            web.setBackgroundColor(0xFF05070D);

            setContentView(web);
            web.loadUrl("file:///android_asset/index.html");

        } catch (Throwable e) {
            showError("启动失败（onCreate）", e);
        }
    }

    /** 把异常堆栈直接画到屏幕上（不依赖 adb） */
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
            // 连错误界面都画不出来的话，只能交给系统了
        }
    }

    /** 全屏：只用老的 systemUiVisibility，所有版本都能跑（30+ 已废弃但依然生效） */
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

    /** 返回键先让网页处理（收起侧栏），网页没处理就退出 */
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
            if (web != null) web.destroy();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
