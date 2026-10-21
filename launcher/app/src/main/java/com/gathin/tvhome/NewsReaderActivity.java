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
