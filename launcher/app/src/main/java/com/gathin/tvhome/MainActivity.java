package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private HomePrefs prefs;
    /** Ultralight mode (HomePrefs): apps only, no glow, title, weather or springs. */
    private boolean light;
    private static final ExecutorService WEATHER = Executors.newSingleThreadExecutor();
    /** After a failed weather fetch, wait this long before trying again. */
    private static final long WEATHER_RETRY_MS = 10 * 60_000L;
    private static long lastWeatherTry;
    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private TextView brand;
    private TextView greeting;
    private TextView subtitle;
    private TextView weather;
    /** Eddy TV accent colours, in the order of R.array.accent_names. */
    private static final int[] ACCENTS = {0xFF4C8DFF, 0xFF3DDC97, 0xFFFF9F43, 0xFFFF5C8A, 0xFFA779FF, 0xFFFFFFFF};
    private static final int FOCUS_RING = 0xF2FFFFFF;
    /** Last three Home presses (elapsedRealtime), for the recovery shortcut: three within 10 s. */
    private static final long[] homePresses = new long[3];
    private static int homePressIndex;
    /** Benchmark aid: `settings put global eddy_debug_keys 1` logs how long each key took to reach us. */
    private boolean debugKeys;
    private TextView btnWifi;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Scratch rects for onTileFocused, which runs on every D-pad move. */
    private final Rect tileRect = new Rect();
    private final Rect revealRect = new Rect();

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();   // the network pill follows networkCallback, no need to poll it
            refreshWeather();   // no-op unless the cached weather is over an hour old
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new HomePrefs(this);
        light = prefs.light();
        AppAdapter.light = light;
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        // Sized for a focused tile before the first frame: left at the 10dp placeholder it showed as a dot.
        sizeGlass(getResources().getDimension(R.dimen.tile_width), getResources().getDimension(R.dimen.tile_height));
        brand = findViewById(R.id.brand);
        greeting = findViewById(R.id.greeting);
        subtitle = findViewById(R.id.subtitle);
        weather = findViewById(R.id.weather);
        applyAccent();
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            // RecyclerView smooth-scrolls a newly focused tile into view on its own; in ultralight mode
            // that animation (~250 ms) is skipped so a move finishes in one frame.
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
                return super.requestChildRectangleOnScreen(parent, child, rect, immediate || light, focusedChildVisible);
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((145 + 6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        // Hold OK on Settings: this home screen's own options.
        btnSettings.setOnLongClickListener(v -> {
            showHomeOptions(v);
            return true;
        });
        View btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> open(new Intent(Intent.ACTION_ASSIST)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0) {
                showHomeOptions(v);   // menu key on the top bar: this home screen's options
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnVoice.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyRemapService.homeResumed();
        glassShown = false;
        glass.setAlpha(0f);
        light = prefs.light();
        AppAdapter.light = light;
        debugKeys = Settings.Global.getInt(getContentResolver(), "eddy_debug_keys", 0) == 1;
        updateGreeting();
        updateWifiLabel();
        refreshWeather();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Tiles are cached for the life of the process: show them right away, then let the loader check
        // for installed or removed apps in the background (it only calls back when something changed).
        List<AppAdapter.Entry> cached = AppLoader.cached();
        if (adapter.getItemCount() == 0 && cached != null) showApps(cached);
        else if (adapter.getItemCount() > 0) restoreFocus(lastFocusedKey, lastFocused);
        AppLoader.start(this, this::showApps);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        AppLoader.stop();
    }

    private void showApps(List<AppAdapter.Entry> entries) {
        if (isDestroyed()) return;
        boolean first = adapter.getItemCount() == 0;
        // Read these before rebinding: it can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.setEntries(entries);
        if (first && !light) grid.scheduleLayoutAnimation();   // tiles rise in on launch only, not on every Home press
        restoreFocus(returnKey, returnSlot);
    }

    /** Return to the app we left from, or the same slot if that app is gone. Posted so it runs after layout. */
    private void restoreFocus(String returnKey, int returnSlot) {
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Focus restored before the first layout (cold start): the tile has no size yet, and the ring
        // would be drawn as a dot. Run again once it is laid out.
        if (card.getWidth() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        sizeGlass(card.getWidth(), card.getHeight());
        float pad = 6f * density;        // GlassView's inset, also the reveal margin below
        float x = r.exactCenterX() - glass.getLayoutParams().width / 2f;
        float y = r.exactCenterY() - glass.getLayoutParams().height / 2f;
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown || light) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (light) glass.setAlpha(1f);
            else if (!glassShown) glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) {
                if (light) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
        } else {
            // Scroll just enough so the whole glass (not only the tile) is visible.
            revealRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            // Ultralight: jump instead of animating the scroll, so a move finishes in one frame.
            grid.requestChildRectangleOnScreen(item, revealRect, light);
        }
    }

    @Override
    public void onTileLaunched(AppAdapter.Entry entry) {
        prefs.touchRecent(entry.pkg);
    }

    /** Hold OK on a tile: move it in the dock, pin or unpin it, hide it, or open its app info. */
    @Override
    public void onTileMenu(int position, View card) {
        String pkg = adapter.pkgAt(position);
        if (pkg == null) return;
        List<String> dock = prefs.dock();
        // Forget dock entries that are not on screen (uninstalled or hidden), so they don't take a slot.
        dock.removeIf(p -> adapter.positionOfPkg(p) < 0);
        int idx = dock.indexOf(pkg);
        List<MenuItem> items = new ArrayList<>();
        if (idx >= 0) {
            if (idx > 0) items.add(new MenuItem(R.drawable.ic_left, R.string.menu_move_left, () -> moveInDock(dock, idx, idx - 1)));
            if (idx < dock.size() - 1) items.add(new MenuItem(R.drawable.ic_right, R.string.menu_move_right, () -> moveInDock(dock, idx, idx + 1)));
            items.add(new MenuItem(R.drawable.ic_hide, R.string.menu_unpin, () -> {
                dock.remove(pkg);
                saveDock(dock);
            }));
        } else {
            items.add(new MenuItem(R.drawable.ic_star, R.string.menu_pin, () -> {
                if (dock.size() >= COLUMNS) {
                    Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
                    return;
                }
                dock.add(pkg);
                saveDock(dock);
            }));
            items.add(new MenuItem(R.drawable.ic_hide, R.string.menu_hide, () -> {
                prefs.hide(pkg);
                AppLoader.reorder(this);
            }));
        }
        items.add(new MenuItem(R.drawable.ic_info, R.string.menu_info, () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)))));
        showMenu(card, items);
    }

    private void moveInDock(List<String> dock, int from, int to) {
        dock.add(to, dock.remove(from));
        saveDock(dock);
    }

    private void saveDock(List<String> dock) {
        prefs.setDock(dock);
        AppLoader.reorder(this);
    }

    /** Hold OK on Settings: ultralight mode, weather place, hidden apps and the default dock. */
    private void showHomeOptions(View anchor) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(R.drawable.ic_person, getString(R.string.opt_name, prefs.userName()), () ->
                askText(R.string.name_title, prefs.userName(), 0, text -> {
                    prefs.setUserName(text);
                    updateGreeting();
                })));
        String message = prefs.message();
        items.add(new MenuItem(R.drawable.ic_edit, getString(R.string.opt_message,
                message.isEmpty() ? getString(R.string.opt_message_auto) : message), () ->
                askText(R.string.message_title, prefs.message(), R.string.message_hint, text -> {
                    prefs.setMessage(text);
                    updateGreeting();
                })));
        String[] accentNames = getResources().getStringArray(R.array.accent_names);
        items.add(new MenuItem(R.drawable.ic_circle, getString(R.string.opt_accent,
                accentNames[prefs.accent() % accentNames.length]), () -> {
            prefs.setAccent((prefs.accent() + 1) % ACCENTS.length);
            applyAccent();
        }));
        items.add(new MenuItem(R.drawable.ic_bolt, light ? R.string.opt_motion_off : R.string.opt_motion_on, this::toggleLight));
        if (!remapServiceEnabled()) {
            items.add(new MenuItem(R.drawable.ic_star, R.string.opt_remap_service, () ->
                    open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        } else {
            String target = prefs.remapTarget();
            items.add(new MenuItem(R.drawable.ic_star, target.isEmpty() ? getString(R.string.opt_remap_off)
                    : getString(R.string.opt_remap_on, appLabel(target)), () ->
                    prefs.setRemapTarget(target.isEmpty() ? HomePrefs.DEFAULT_REMAP : "")));
        }
        if (prefs.hasWeatherPlace()) {
            items.add(new MenuItem(R.drawable.ic_cloud, getString(R.string.opt_weather, prefs.weatherPlace()), this::askWeatherPlace));
            items.add(new MenuItem(R.drawable.ic_hide, R.string.opt_weather_disable, () -> {
                prefs.clearWeather();
                updateGreeting();
            }));
        } else {
            items.add(new MenuItem(R.drawable.ic_cloud, R.string.opt_weather_off, this::askWeatherPlace));
        }
        int hidden = prefs.userHidden().size();
        if (hidden > 0) {
            items.add(new MenuItem(R.drawable.ic_info, getString(R.string.opt_unhide, hidden), () -> {
                prefs.unhideAll();
                AppLoader.reorder(this);
            }));
        }
        items.add(new MenuItem(R.drawable.ic_refresh, R.string.opt_reset_dock, () -> {
            prefs.resetDock();
            AppLoader.reorder(this);
        }));
        showMenu(anchor, items);
    }

    private void toggleLight() {
        light = !light;
        prefs.setLight(light);
        AppAdapter.light = light;
        updateGreeting();
    }

    // ---- weather ---------------------------------------------------------------------------------

    private void askWeatherPlace() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.weather_hint);
        input.setText(prefs.weatherPlace());
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.weather_title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> findWeatherPlace(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void findWeatherPlace(String query) {
        if (query.trim().isEmpty()) return;
        WEATHER.execute(() -> {
            int message;
            String place = null;
            try {
                Weather.Place p = Weather.find(query);
                if (p == null) {
                    message = R.string.weather_not_found;
                } else {
                    String now = Weather.current(p.lat, p.lon);
                    prefs.setWeatherPlace(p.name, p.lat, p.lon);
                    prefs.setWeatherText(now);
                    place = p.name;
                    message = R.string.weather_set;
                }
            } catch (Exception e) {
                message = R.string.weather_failed;
            }
            final int msg = message;
            final String name = place;
            handler.post(() -> {
                if (isDestroyed()) return;
                updateGreeting();
                Toast.makeText(this, name != null ? getString(msg, name) : getString(msg), Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** Fetches the weather in the background when the cached one is over an hour old; never in ultralight mode. */
    private void refreshWeather() {
        if (!prefs.hasWeatherPlace() || !prefs.weatherStale()) return;
        long now = SystemClock.elapsedRealtime();
        if (lastWeatherTry != 0 && now - lastWeatherTry < WEATHER_RETRY_MS) return;
        lastWeatherTry = now;
        final double lat = prefs.weatherLat(), lon = prefs.weatherLon();
        WEATHER.execute(() -> {
            try {
                prefs.setWeatherText(Weather.current(lat, lon));
                handler.post(() -> {
                    if (!isDestroyed()) updateGreeting();
                });
            } catch (Exception ignored) {
                // Keep showing the last value; tried again after WEATHER_RETRY_MS.
            }
        });
    }

    /** Highlight size for a tile of cardW x cardH at focus scale, plus GlassView's inset on each side. */
    private void sizeGlass(float cardW, float cardH) {
        float pad = 6f * density;        // matches GlassView's inset
        int w = (int) (cardW * AppAdapter.FOCUS_SCALE + 2 * pad);
        int h = (int) (cardH * AppAdapter.FOCUS_SCALE + 2 * pad);
        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width == w && lp.height == h) return;
        lp.width = w;
        lp.height = h;
        glass.setLayoutParams(lp);
        // Also apply it now: focus restored inside a layout pass would otherwise keep the old size until the next one.
        glass.layout(glass.getLeft(), glass.getTop(), glass.getLeft() + w, glass.getTop() + h);
        glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        if (light) glass.setAlpha(0f);
        else glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (light) {
            grid.scrollToPosition(pos);   // ultralight: jump, then focus after the next layout
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
            });
            return;
        }
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /**
     * "Bom dia, Eddy." from the TV's local time, no network. Called on resume and once a minute;
     * the views are only touched when their text actually changes (at most at the turn of a period).
     */
    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int period = hour >= 5 && hour < 12 ? 0 : hour >= 12 && hour < 18 ? 1 : 2;
        String name = prefs.userName();
        setIfChanged(brand, getString(R.string.brand_format, name));
        setIfChanged(greeting, getString(new int[]{R.string.greet_morning, R.string.greet_afternoon, R.string.greet_night}[period], name));
        String message = prefs.message();
        setIfChanged(subtitle, message.isEmpty()
                ? getString(new int[]{R.string.sub_morning, R.string.sub_afternoon, R.string.sub_night}[period]) : message);
        String w = prefs.hasWeatherPlace() ? prefs.weatherText() : "";
        setIfChanged(weather, w);
        int visibility = w.isEmpty() ? View.GONE : View.VISIBLE;
        if (weather.getVisibility() != visibility) weather.setVisibility(visibility);
    }

    private static void setIfChanged(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    /** Accent colour on the brand. The focus ring stays white: a coloured ring read as heavy on the TV. */
    private void applyAccent() {
        int color = ACCENTS[prefs.accent() % ACCENTS.length];
        brand.setTextColor(color);
        glass.setRing(FOCUS_RING);
    }

    private interface TextResult {
        void accept(String text);
    }

    private void askText(int title, String current, int hint, TextResult result) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        if (hint != 0) input.setHint(hint);
        input.setText(current);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> result.accept(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- remote shortcuts and recovery -----------------------------------------------------------

    private boolean remapServiceEnabled() {
        String on = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(getPackageName() + "/" + KeyRemapService.class.getName());
    }

    private String appLabel(String pkg) {
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (debugKeys && event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i("EddyKey", "key=" + event.getKeyCode() + " delay=" + (SystemClock.uptimeMillis() - event.getEventTime()));
        }
        return super.dispatchKeyEvent(event);
    }

    /** Number keys 1-4 jump straight to HDMI 1-4 (same order as the Inputs menu). */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_4 && event.getRepeatCount() == 0
                && openHdmi(keyCode - KeyEvent.KEYCODE_1)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean openHdmi(int index) {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return false;
        List<TvInputInfo> hdmi = new ArrayList<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(info);
        }
        Collections.sort(hdmi, (a, b) -> String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this))));
        if (index >= hdmi.size()) return false;
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                TvContract.buildChannelUriForPassthroughInput(hdmi.get(index).getId()), "vnd.android.cursor.item/channel"));
        return true;
    }

    /** Home pressed three times within 10 s: recovery menu (original launcher, Settings, defaults). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        long now = SystemClock.elapsedRealtime();
        homePresses[homePressIndex++ % homePresses.length] = now;
        long oldest = Long.MAX_VALUE;
        for (long t : homePresses) oldest = Math.min(oldest, t);
        if (oldest > 0 && now - oldest <= 10_000) {
            java.util.Arrays.fill(homePresses, 0);
            handler.post(this::showRecovery);
        }
    }

    private void showRecovery() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(R.drawable.ic_refresh, R.string.rec_tvlauncher, () -> open(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.tvlauncher")))));
        items.add(new MenuItem(R.drawable.ic_settings, R.string.rec_settings, () -> open(new Intent(Settings.ACTION_SETTINGS))));
        items.add(new MenuItem(R.drawable.ic_hide, R.string.rec_reset, () -> {
            prefs.resetAll();
            light = prefs.light();
            AppAdapter.light = light;
            applyAccent();
            updateGreeting();
            AppLoader.reorder(this);
        }));
        items.add(new MenuItem(R.drawable.ic_left, R.string.rec_close, () -> { }));
        showMenu(greeting, items);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private final class MenuItem {
        final int icon;
        final String title;
        final Runnable action;

        MenuItem(int icon, int title, Runnable action) {
            this(icon, getString(title), action);
        }

        MenuItem(int icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }

    /** A dropdown of actions in the Inputs menu's style, below the anchor (or above it near the bottom). */
    private void showMenu(View anchor, List<MenuItem> items) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = new PopupWindow(panel, (int) (300 * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        for (MenuItem item : items) {
            View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(item.icon);
            ((TextView) row.findViewById(R.id.title)).setText(item.title);
            row.findViewById(R.id.status).setVisibility(View.GONE);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            row.setOnClickListener(v -> {
                popup.dismiss();
                item.action.run();
            });
            rows.addView(row);
        }
        // PopupWindow only flips above the anchor when it knows its height, and a WRAP_CONTENT one never does:
        // near the bottom the menu was cut off by the screen edge. Measure it and place it above by hand.
        int width = popup.getWidth();
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = panel.getMeasuredHeight();
        int[] at = new int[2];
        anchor.getLocationInWindow(at);   // includes the focus scale
        int below = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int gap = (int) (8 * density);
        if (below + gap + height > root.getHeight()) {
            int ringGap = (int) (6 * density);   // clear the focus ring drawn just outside the tile
            popup.showAtLocation(root, Gravity.START | Gravity.TOP, at[0], Math.max(0, at[1] - gap - ringGap - height));
        } else {
            popup.showAsDropDown(anchor, 0, gap, Gravity.START);
        }
        rows.getChildAt(0).requestFocus();
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
