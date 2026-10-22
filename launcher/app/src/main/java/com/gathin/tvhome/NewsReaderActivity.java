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
                hideOverlays(view);
            }

            /**
             * A headline the TV cannot load gets an explanation instead of the WebView's own error
             * page. The usual cause here is a plain http link: this build blocks cleartext, so the
             * engine refuses before any byte leaves the TV.
             */
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (loading != null) loading.setVisibility(View.GONE);
                view.loadDataWithBaseURL(null, failedPage(failingUrl, description), "text/html", "utf-8", null);
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

    /**
     * Hides the consent bar and lifts the body text. It writes to each element's own style instead
     * of injecting a {@code <style>}: news sites ship a CSP that drops injected stylesheets, and a
     * consent manager that mounts late is caught by the short interval, not by a single pass.
     */
    private static void hideOverlays(WebView view) {
        view.evaluateJavascript(
                "(function(){"
                + "function hide(){"
                + "var sel='[class*=cookie],[id*=cookie],[class*=consent],[id*=consent],"
                + "[class*=lgpd],[id*=lgpd]';"
                + "var n=document.querySelectorAll(sel);"
                + "for(var i=0;i<n.length;i++){n[i].style.setProperty('display','none','important');}"
                + "if(document.body){document.body.style.setProperty('font-size','118%','important');}"
                + "}"
                + "hide();"
                + "if(!window.__eddyHide){window.__eddyHide=setInterval(hide,700);"
                + "setTimeout(function(){clearInterval(window.__eddyHide);window.__eddyHide=null;},12000);}"
                + "})();", null);
    }

    /**
     * The page shown when the article cannot load: the same dark stage, and what the viewer can do.
     * A plain http link is refused by this build (cleartext is blocked), and the raw WebView error
     * does not say so in words anyone should have to read on a TV.
     */
    private static String failedPage(String url, String reason) {
        String safeUrl = url == null ? "" : url.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        boolean cleartext = url != null && url.startsWith("http://");
        String why = cleartext
                ? "O endereço desta manchete é http://, e o Android desta TV só abre páginas https://."
                : "A página não respondeu" + (reason == null || reason.isEmpty() ? "." : " (" + reason + ").");
        return "<html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:#0b0c0f;color:#e8eaf0;font:24px/1.55 sans-serif;padding:72px}"
                + "h1{font-size:42px;font-weight:500;margin:0 0 26px}p{color:#b9bfcc;margin:0 0 18px}"
                + "code{color:#8fb4ff;word-break:break-all;font-size:19px}</style></head><body>"
                + "<h1>Não foi possível abrir esta manchete</h1>"
                + "<p>" + why + "</p>"
                + "<p><code>" + safeUrl + "</code></p>"
                + "<p>Volte com o botão Voltar.</p></body></html>";
    }

    /** Back leaves the article; with history it goes back a page first, as a browser would. */    @Override
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
