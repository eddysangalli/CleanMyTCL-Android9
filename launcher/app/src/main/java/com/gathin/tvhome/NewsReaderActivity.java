package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

/**
 * The article itself, inside the launcher.
 *
 * The news row opens a link with ACTION_VIEW, but this TV's browser (com.internet.tvbrowser) brings its
 * own task to the front and ignores the URL, so the viewer got the browser home instead of the article.
 * A WebView here shows the page the headline points at, with Back leaving it.
 */
public final class NewsReaderActivity extends Activity {

    static final String EXTRA_URL = "url";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_news_reader);
        String url = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        web = findViewById(R.id.reader);
        final ProgressBar loading = findViewById(R.id.readerLoading);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        // A desktop-ish UA: the mobile layouts of these news sites assume a phone screen held upright.
        settings.setUserAgentString(settings.getUserAgentString() + " EddyTV/1.0");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (loading != null) loading.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loading != null) loading.setVisibility(View.GONE);
                // Two things the desktop site does on a TV: a cookie bar parked over the bottom of the
                // article, and body text sized for a monitor at arm's length. Neither is useful here.
                view.evaluateJavascript(
                        "(function(){var s=document.createElement('style');"
                        + "s.innerHTML='body{font-size:118% !important}"
                        + "*[class*=cookie],*[id*=cookie],*[class*=consent],*[id*=consent],"
                        + "*[class*=lgpd],*[id*=lgpd]{display:none !important}';"
                        + "(document.head||document.documentElement).appendChild(s);})();", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (loading != null) {
                    loading.setProgress(progress);
                    loading.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
                }
            }
        });
        web.loadUrl(url);
        web.requestFocus();
    }

    /** Back leaves the article; with history it goes back a page first, as a browser would. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        if (web != null && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            // A WebView has no focusable links to walk here, so the D-pad scrolls the article: half a
            // screen per press, which is what a reader expects from a remote.
            int screen = web.getHeight();
            int step = Math.max(120, screen / 2);
            web.scrollBy(0, keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? step : -step);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
