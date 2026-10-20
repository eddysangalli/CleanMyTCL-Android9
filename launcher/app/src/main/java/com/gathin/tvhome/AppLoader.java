package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds the launchable apps and prepares their tiles on a background thread. Tiles are kept for the life of the
 * process, so returning Home costs one package query instead of decoding every banner again; an app is only
 * rebuilt when the system reports it installed, updated, enabled, disabled or removed.
 */
final class AppLoader {

    interface Callback {
        void onLoaded(List<AppAdapter.Entry> entries);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Packages changed since their tiles were built. Added on the main thread, drained on the worker. */
    private static final Set<String> STALE = Collections.synchronizedSet(new HashSet<>());
    /** Worker thread only: built tiles by component key. */
    private static final Map<String, AppAdapter.Entry> CACHE = new HashMap<>();
    /** Worker thread only: labels are localised, so a language change drops the cache. */
    private static String cacheLocale;
    /** The list last handed out; written on the worker, read anywhere. */
    private static volatile List<AppAdapter.Entry> last;
    /** Main thread only: who to tell when a package changes while Home is on screen. */
    private static Callback active;
    private static boolean watching;

    private AppLoader() {
    }

    /** Tiles from the last load, or null before the first one finishes. */
    static List<AppAdapter.Entry> cached() {
        return last;
    }

    /** Keeps the list fresh while Home is resumed: refreshes now and again whenever a package changes. */
    static void start(Context ctx, Callback callback) {
        Context app = ctx.getApplicationContext();
        watch(app);
        active = callback;
        refresh(app, callback);
    }

    static void stop() {
        active = null;
    }

    /** Re-sorts now (after the dock, hidden apps or recents changed); tiles are reused from the cache. */
    static void reorder(Context ctx) {
        if (active != null) refresh(ctx.getApplicationContext(), active);
    }

    /** Rebuilds the list on the worker; calls back on the main thread only when it differs from the last one. */
    private static void refresh(Context app, Callback callback) {
        WORKER.execute(() -> {
            List<AppAdapter.Entry> fresh = load(app);
            if (sameTiles(last, fresh)) return;
            last = fresh;
            MAIN.post(() -> callback.onLoaded(fresh));
        });
    }

    private static void watch(Context app) {
        if (watching) return;
        watching = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Uri data = intent.getData();
                if (data != null) STALE.add(data.getSchemeSpecificPart());
                if (active != null) refresh(app, active);
            }
        }, filter);
    }

    private static boolean sameTiles(List<AppAdapter.Entry> a, List<AppAdapter.Entry> b) {
        if (a == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;   // cached tiles are reused, so identity is enough
        }
        return true;
    }

    private static List<AppAdapter.Entry> load(Context app) {
        Resources res = app.getResources();
        String locale = res.getConfiguration().getLocales().toLanguageTags();
        if (!locale.equals(cacheLocale)) {
            CACHE.clear();
            cacheLocale = locale;
        }
        HomePrefs prefs = new HomePrefs(app);
        List<String> favourites = prefs.dock();
        Set<String> hidden = prefs.hidden();
        List<String> recents = prefs.recents();
        // Banners are drawn at the focused size so the zoomed tile stays sharp.
        int w = Math.round(res.getDimension(R.dimen.tile_width) * AppAdapter.FOCUS_SCALE);
        int h = Math.round(res.getDimension(R.dimen.tile_height) * AppAdapter.FOCUS_SCALE);

        Set<String> stale;
        synchronized (STALE) {
            stale = new HashSet<>(STALE);
            STALE.clear();
        }
        PackageManager pm = app.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<AppAdapter.Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(app.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            String key = component.flattenToShortString();
            seen.add(key);
            AppAdapter.Entry e = stale.contains(pkg) ? null : CACHE.get(key);
            if (e == null) {
                e = build(pm, ri, component, key, w, h);
                CACHE.put(key, e);
            }
            // An app can declare two leanback activities with the same visible name; one tile is
            // enough (#298, upstream anandhakrishnanaji). Different labels are still kept.
            if (!labels.add(e.pkg + '\n' + e.label)) continue;
            fresh.add(e);
        }
        CACHE.keySet().retainAll(seen);
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            int ra = recents.indexOf(a.pkg), rb = recents.indexOf(b.pkg);
            if (ra >= 0 || rb >= 0) {
                if (ra < 0) return 1;
                if (rb < 0) return -1;
                return Integer.compare(ra, rb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        return fresh;
    }

    private static AppAdapter.Entry build(PackageManager pm, ResolveInfo ri, ComponentName component, String key,
                                          int w, int h) {
        String label = String.valueOf(ri.loadLabel(pm));
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        if (banner != null) {
            Bitmap tile = render(banner, w, h, true);
            int ambient = vividColor(tile);
            return new AppAdapter.Entry(label, component, key, toHardware(tile), null, ambient);
        }
        Drawable icon = ri.loadIcon(pm);
        int ambient = icon != null ? vividColor(render(icon, 12, 12, false)) : 0xFF444444;
        return new AppAdapter.Entry(label, component, key, null, icon, ambient);
    }

    /** Draws a drawable into a bitmap of exactly this size, centre-cropped like the tile's ImageView. */
    private static Bitmap render(Drawable d, int w, int h, boolean crop) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (crop && dw > 0 && dh > 0) {
            float s = Math.max((float) w / dw, (float) h / dh);
            int sw = Math.round(dw * s), sh = Math.round(dh * s);
            int l = (w - sw) / 2, t = (h - sh) / 2;
            d.setBounds(l, t, l + sw, t + sh);
        } else {
            d.setBounds(0, 0, w, h);
        }
        d.draw(new Canvas(bmp));
        return bmp;
    }

    /** GPU-only copy: no second copy in the app's memory and no upload on first draw. */
    private static Bitmap toHardware(Bitmap bmp) {
        Bitmap hw = bmp.copy(Bitmap.Config.HARDWARE, false);
        if (hw == null) return bmp;
        bmp.recycle();
        return hw;
    }

    /** A vivid colour sampled from the banner (or icon), for the glow behind the top shelf. */
    private static int vividColor(Bitmap src) {
        int n = 12;
        Bitmap bmp = src.getWidth() == n && src.getHeight() == n ? src : Bitmap.createScaledBitmap(src, n, n, true);
        int[] px = new int[n * n];
        bmp.getPixels(px, 0, n, 0, 0, n, n);
        if (bmp != src) bmp.recycle();
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int p : px) {
            r += Color.red(p);
            g += Color.green(p);
            b += Color.blue(p);
            Color.colorToHSV(p, hsv);
            float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / px.length), (int) (g / px.length), (int) (b / px.length));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }
}
