package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
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
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /**
     * Intent action: open the Inputs dropdown (sent by {@link InputKeyService} when the remote's
     * Input/Source key is pressed - KEYCODE_TV, KEYCODE_TV_INPUT or KEYCODE_MEDIA_RECORD).
     */
    public static final String ACTION_OPEN_INPUTS = "com.gathin.tvhome.action.OPEN_INPUTS";

    /** InputCardsActivity returns OK when a card changed; the grid is rebuilt in place, no recreate(). */
    private static final int INPUT_CARDS_REQUEST = 2015;

    /**
     * The Inputs popup, when one is on screen. Set by {@link #showInputs()} so a second press of the
     * remote's Input key (delivered via {@link InputKeyService} and the {@link #ACTION_OPEN_INPUTS}
     * intent) can close it again.
     */
    private PopupWindow inputsPopup;

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    /** TVs with no TvInputManager (TCL Android TV 9): component (service or activity) and action of the
     *  TV's own source-selection panel. Empty by default; the fields only matter when their XML keys are
     *  filled in. Set in onCreate. */
    private String sourcePanelService;
    private String sourcePanelAction;

    private HomePrefs prefs;
    /** Ultralight mode (HomePrefs): apps only, no glow, title, weather or springs. */
    private boolean light;
    private static final ExecutorService WEATHER = Executors.newSingleThreadExecutor();
    /** One static wallpaper decode at a time; the bitmap is released when the Home is destroyed or turned black. */
    private static final ExecutorService WALLPAPER = Executors.newSingleThreadExecutor();
    /** After a failed weather fetch, wait this long before trying again. */
    private static final long WEATHER_RETRY_MS = 10 * 60_000L;
    private static long lastWeatherTry;
    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private ImageView wallpaperView;
    private Bitmap wallpaperBitmap;
    /** Incremented whenever the wallpaper is hidden or reloaded, so a late decode is discarded. */
    private int wallpaperRequest;
    private int wallpaperRes, wallpaperLoadingRes;   // image shown / being decoded, 0 = none
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
    private float glassTargetX, glassTargetY;   // X in root coordinates, Y in grid content coordinates
    private int gridScrollY;   // how far the grid content has scrolled, summed from onScrolled
    private int glassCardW;   // width of the tile the glass was last fitted to
    private boolean glassShown;
    /** Vendor keycodes that mean the remote's Settings button; empty unless configured in config.xml. */
    private int[] settingsKeycodes = new int[0];
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
        settingsKeycodes = getResources().getIntArray(R.array.settings_key_keycodes);
        COLUMNS = prefs.columns();
        liveTvPkg = getString(R.string.live_tv_package).trim();
        sourcePanelService = getString(R.string.source_panel_service).trim();
        sourcePanelAction = getString(R.string.source_panel_action).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        wallpaperView = findViewById(R.id.wallpaper);
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
            // The grid's top padding is applied here, before the children are laid out, so the first
            // layout already has the right padding. Doing it from the layout listener (the older way)
            // left the first pass with the XML padding (10dp), which made the grid inflate rows from
            // the top of the screen and then pushed them back out with a second pass
            // (upstream PR #446, idea taken from there; measured on this TV, see docs/).
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                applyGridPadding();
                super.onLayoutChildren(recycler, state);
            }

            // RecyclerView smooth-scrolls a newly focused tile into view on its own; in ultralight mode
            // that animation (~250 ms) is skipped so a move finishes in one frame.
            //
            // A row that is already fully on screen must not be nudged into the bottom padding. That
            // padding only keeps the last row clear of the screen edge; treating it as the visible
            // bottom made Down on the last row scroll the whole grid up (#289, upstream Samreay).
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
                if (getPosition(child) < Grid.dockSize(COLUMNS)) return false;
                int top = child.getTop() + rect.top;
                int bottom = top + rect.height();
                int limitBottom = getHeight() - getPaddingBottom();
                int dy;
                if (top < 0) dy = top;
                else if (bottom <= getHeight()) dy = 0;
                else dy = Math.min(top, bottom - limitBottom);
                if (dy == 0) return false;
                if (immediate || light) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(Grid.dockSize(COLUMNS), density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        // After the rise-in animation, refit the ring once more: a cold start can focus a tile before
        // the grid has its final width, and otherwise the ring would stay at the design size.
        grid.setLayoutAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
                focusTile(lastFocused);
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }
        });
        setupNews();
        adapter = new AppAdapter(this, this, Grid.dockSize(COLUMNS));
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        // The padding itself is set from the layout manager, before the children are laid out; the listener
        // keeps it in step with a size that changed after the fact, and still refits the glass.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            applyGridPadding();
            // On a slow cold start a tile can take focus while the grid has no width yet, at the design size;
            // the relaid-out tile is smaller, so refit the glass instead of waiting for the next focus change.
            // Do this even before the first successful focus: the glass starts at the design size and would
            // otherwise show a wide ring until the viewer presses a key.
            grid.post(this::refitGlass);
        });

        // Keep the glass glued to its tile while the grid scrolls. A running Y spring works in content
        // coordinates, so it keeps the same feel as the X one instead of chasing a moving target.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                gridScrollY += dy;
                if (glassShown) glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        // Material 3 Expressive "spatial default" spring (m3e_core M3EMotion.expressiveSpatialDefault:
        // stiffness 380, damping ratio 0.8, mass 1), as on Gateway Ops Dashboard's navigation rail.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("contentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() + gridScrollY;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value - gridScrollY);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(380f).setDampingRatio(0.8f));
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
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                focusTile(lastFocused % COLUMNS);
                return true;
            }
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
        updateWallpaper();
        debugKeys = Settings.Global.getInt(getContentResolver(), "eddy_debug_keys", 0) == 1;
        updateGreeting();
        updateWifiLabel();
        refreshWeather();
        refreshNews();
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
        newsRotate.removeCallbacks(newsRotateStep);
        AppLoader.stop();
    }

    private void showApps(List<AppAdapter.Entry> entries) {
        if (isDestroyed()) return;
        boolean first = adapter.getItemCount() == 0;
        // Read these before rebinding: it can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.setInputCards(resolveInputCards());
        adapter.setEntries(entries);
        if (first && !light) grid.scheduleLayoutAnimation();   // rise-in on launch only, not on every Home press
        restoreFocus(returnKey, returnSlot);
        if (first) {
            // Cold start: the first focus can happen before the grid has its final width, leaving the
            // ring at the 4-column design size until a key is pressed. Retry after the layout settles;
            // a single delayed pass can still land before AppLoader has bound the first holder.
            for (int delay : new int[]{300, 800, 1600, 3000}) grid.postDelayed(this::refitGlass, delay);
        }
    }

    /**
     * The owner's shown input cards, resolved against the inputs the TV reports right now. Read only
     * when the grid rebinds, so no input query runs while the remote moves.
     */
    private List<InputCards> resolveInputCards() {
        Map<String, InputCards> saved = InputCards.load(
                getSharedPreferences(HomePrefs.PREFS_NAME, MODE_PRIVATE));
        if (saved.isEmpty()) return Collections.emptyList();
        List<InputCards> out = new ArrayList<>();
        for (TvInputInfo info : InputCards.cardInputs(this)) {
            InputCards card = saved.get(info.getId());
            if (card != null && card.shown) out.add(card.resolved(InputCards.portLabel(this, info)));
        }
        return out;
    }

    /** An input card was chosen: switch to that HDMI/AV input, the same path as the Inputs pill. */
    @Override
    public void onInputRequested(String inputId) {
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                TvContract.buildChannelUriForPassthroughInput(inputId), "vnd.android.cursor.item/channel"));
    }

    /** Return to the app we left from, or the same slot if that app is gone. Posted so it runs after layout. */
    private void restoreFocus(String returnKey, int returnSlot) {
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != INPUT_CARDS_REQUEST || result != RESULT_OK) return;
        // Rebuild only the grid: recreate() would drop the focus and re-query every app. The list of
        // apps is already cached, and showApps() puts the focus back on the same tile.
        List<AppAdapter.Entry> cached = AppLoader.cached();
        if (cached != null) showApps(cached);
    }

    @Override
    protected void onDestroy() {
        wallpaperRequest++;
        releaseWallpaper();
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
        glassCardW = card.getWidth();
        float pad = 6f * density;        // GlassView's inset, also the reveal margin below
        float x = r.exactCenterX() - glass.getLayoutParams().width / 2f;
        float y = r.exactCenterY() - glass.getLayoutParams().height / 2f;
        glassTargetX = x;
        glassTargetY = y + gridScrollY;   // content coordinates, like springY
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
            springY.animateToFinalPosition(glassTargetY);
        }
        View item = (View) card.getParent();
        if (Grid.inDock(position, COLUMNS)) {
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
                if (!Grid.dockHasRoom(dock.size(), COLUMNS)) {
                    Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
                    return;
                }
                dock.add(pkg);
                saveDock(dock);
            }));
            items.add(new MenuItem(R.drawable.ic_hide, R.string.menu_hide, () -> {
                prefs.hide(pkg);
                AppLoader.reload(this);
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

    /** Hold OK on Settings: experience preset, motion, weather, hidden apps and the default dock. */
    private void showHomeOptions(View anchor) {
        showHomeOptions(anchor, 0);
    }

    /**
     * Settings with values (accent, motion, columns, wallpaper, Globoplay key) change in place with
     * OK/Left/Right and keep the menu open; name and message reopen it on the same row after typing.
     */
    private void showHomeOptions(View anchor, int focusIndex) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(R.drawable.ic_person, getString(R.string.opt_name, prefs.userName()), () ->
                askText(R.string.name_title, prefs.userName(), 0, text -> {
                    prefs.setUserName(text);
                    updateGreeting();
                }, () -> showHomeOptions(anchor, 0))));
        items.add(new MenuItem(R.drawable.ic_input, R.string.input_cards, () ->
                startActivityForResult(new Intent(this, InputCardsActivity.class), INPUT_CARDS_REQUEST)));
        items.add(new MenuItem(R.drawable.ic_info, R.string.news_settings_title, () ->
                startActivity(new Intent(this, NewsSettingsActivity.class))));
        String message = prefs.message();
        items.add(new MenuItem(R.drawable.ic_edit, getString(R.string.opt_message,
                message.isEmpty() ? getString(R.string.opt_message_auto) : message), () ->
                askText(R.string.message_title, prefs.message(), R.string.message_hint, text -> {
                    prefs.setMessage(text);
                    updateGreeting();
                }, () -> showHomeOptions(anchor, 1))));
        String[] accentNames = getResources().getStringArray(R.array.accent_names);
        items.add(new MenuItem(R.drawable.ic_circle,
                () -> getString(R.string.opt_accent, accentNames[prefs.accent() % accentNames.length]), dir -> {
            prefs.setAccent((prefs.accent() + dir + ACCENTS.length) % ACCENTS.length);
            applyAccent();
        }));
        items.add(new MenuItem(R.drawable.ic_bolt,
                getString(R.string.opt_experience, experienceLabel()), this::askExperience));
        items.add(new MenuItem(R.drawable.ic_bolt,
                () -> getString(light ? R.string.opt_motion_off : R.string.opt_motion_on), dir -> toggleLight()));
        items.add(new MenuItem(R.drawable.ic_person, getString(R.string.opt_profiles, prefs.profiles().length()), this::showProfiles));
        // Changing the columns rebuilds the Home, so the new count is applied when the menu closes.
        items.add(new MenuItem(R.drawable.ic_grid, () -> getString(R.string.opt_columns, prefs.columns()),
                dir -> prefs.setColumns(Grid.columns(prefs.columns() + dir))));
        String[] wallpaperNames = getResources().getStringArray(R.array.wallpaper_names);
        items.add(new MenuItem(R.drawable.ic_wallpaper, () -> {
            int i = backgroundIndex(prefs.wallpaper());
            // A translation that lags behind BG_IDS shows the id instead of crashing.
            return getString(R.string.opt_wallpaper, i < wallpaperNames.length ? wallpaperNames[i] : BG_IDS[i]);
        }, this::stepWallpaper));
        if (!remapServiceEnabled()) {
            items.add(new MenuItem(R.drawable.ic_star, R.string.opt_remap_service, () ->
                    open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        } else {
            items.add(new MenuItem(R.drawable.ic_star, () -> {
                String target = prefs.remapTarget();
                return target.isEmpty() ? getString(R.string.opt_remap_off)
                        : getString(R.string.opt_remap_on, appLabel(target));
            }, dir -> prefs.setRemapTarget(prefs.remapTarget().isEmpty() ? HomePrefs.DEFAULT_REMAP : "")));
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
                AppLoader.reload(this);
            }));
        }
        items.add(new MenuItem(R.drawable.ic_refresh, R.string.opt_reset_dock, () -> {
            prefs.resetDock();
            AppLoader.reorder(this);
        }));
        showMenu(null, items, focusIndex, () -> {
            if (prefs.columns() != COLUMNS) recreate();
        });
    }

    private void toggleLight() {
        light = !light;
        prefs.setLight(light);
        applyExperienceState();
    }

    /** Label for the current preset; Custom means the viewer changed one option individually. */
    private String experienceLabel() {
        switch (prefs.experience()) {
            case PERFORMANCE:
                return getString(R.string.experience_performance_short);
            case BALANCED:
                return getString(R.string.experience_balanced_short);
            case VISUAL:
                return getString(R.string.experience_visual_short);
            default:
                return getString(R.string.experience_custom_short);
        }
    }

    /** Three presets, same app and same components. Choosing one sets motion and wallpaper together. */
    private void askExperience() {
        final HomePrefs.Experience[] choices = {
                HomePrefs.Experience.PERFORMANCE,
                HomePrefs.Experience.BALANCED,
                HomePrefs.Experience.VISUAL
        };
        final String[] labels = {
                getString(R.string.experience_performance),
                getString(R.string.experience_balanced),
                getString(R.string.experience_visual)
        };
        int checked = -1;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == prefs.experience()) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.experience_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyExperiencePreset(choices[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyExperiencePreset(HomePrefs.Experience experience) {
        prefs.applyExperience(experience);
        applyExperienceState();
    }

    /** Applies the saved light/wallpaper values to the views after a preset or an individual change. */
    private void applyExperienceState() {
        light = prefs.light();
        AppAdapter.light = light;
        adapter.notifyDataSetChanged();
        updateGreeting();
        updateWallpaper();
        if (light) {
            hideGlass();
        } else {
            grid.post(() -> focusTile(lastFocused));
        }
    }

    /**
     * Cycles the background with Left/Right. Solid colours switch immediately; an image or BlurHash
     * background shows the measured Aurora cost or an explicit unmeasured notice before it is enabled.
     * Ultralight always draws black.
     */
    private void stepWallpaper(int dir) {
        int n = BG_IDS.length;
        String current = prefs.wallpaper();
        int currentIndex = backgroundIndex(current);
        final String next = BG_IDS[(currentIndex + dir + n) % n];
        int nextIndex = backgroundIndex(next);
        boolean image = BG_IMAGES[nextIndex] != 0 || BG_HASHES[nextIndex] != null;
        boolean alreadyImage = BG_IMAGES[currentIndex] != 0 || BG_HASHES[currentIndex] != null;
        boolean enteringUnmeasured = image && !"aurora".equals(next) && "aurora".equals(current);
        if (image && (!alreadyImage || enteringUnmeasured) && !light) {
            String[] names = getResources().getStringArray(R.array.wallpaper_names);
            String label = nextIndex < names.length ? names[nextIndex] : BG_IDS[nextIndex];
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(getString(R.string.opt_wallpaper, label))
                    .setMessage("aurora".equals(next) ? R.string.wallpaper_cost
                            : R.string.wallpaper_cost_unmeasured)
                    .setPositiveButton(R.string.wallpaper_enable, (dialog, which) -> applyBackground(next))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            applyBackground(next);
        }
    }

    private void applyBackground(String id) {
        prefs.setWallpaper(id);
        updateWallpaper();
        if (light && !"black".equals(id)) {
            Toast.makeText(this, R.string.wallpaper_light, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- columns and family profiles -------------------------------------------------------------

    /** Lists the saved profiles; tapping one offers load/delete, and the button saves the current look. */
    private void showProfiles() {
        JSONObject all = prefs.profiles();
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) keys.add(it.next());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.profiles_title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.profile_save, (dialog, which) -> askProfileName());
        if (keys.isEmpty()) {
            builder.setMessage(R.string.profile_empty);
        } else {
            String[] labels = new String[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                JSONObject profile = all.optJSONObject(keys.get(i));
                String savedName = profile == null ? "" : profile.optString("name", "");
                labels[i] = savedName.isEmpty() || savedName.equals(keys.get(i))
                        ? keys.get(i) : keys.get(i) + " — " + savedName;
            }
            builder.setItems(labels, (dialog, which) -> showProfileActions(keys.get(which)));
        }
        builder.show();
    }

    private void askProfileName() {
        askText(R.string.profile_name_title, prefs.userName(), 0, text -> {
            String label = text.trim();
            if (label.isEmpty()) return;
            prefs.saveProfile(label);
            Toast.makeText(this, getString(R.string.profile_saved, label), Toast.LENGTH_SHORT).show();
        });
    }

    private void showProfileActions(String label) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(label)
                .setPositiveButton(R.string.profile_load, (dialog, which) -> {
                    prefs.applyProfile(label);
                    updateGreeting();
                    applyAccent();
                    updateWallpaper();
                })
                .setNeutralButton(R.string.profile_delete, (dialog, which) -> {
                    prefs.deleteProfile(label);
                    Toast.makeText(this, getString(R.string.profile_deleted, label), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
                    String now = Weather.current(getApplicationContext(), p.lat, p.lon);
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
                // Only weather_set contains a format placeholder; other messages take no argument.
                String notice = (msg == R.string.weather_set && name != null)
                        ? getString(R.string.weather_set, name) : getString(msg);
                Toast.makeText(this, notice, Toast.LENGTH_SHORT).show();
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
                prefs.setWeatherText(Weather.current(getApplicationContext(), lat, lon));
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
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            glass.setLayoutParams(lp);
        }
        // Also apply it now: focus restored inside a layout pass would otherwise keep the old size until
        // the next one. Reapply even when the params already match: the parent can lay the glass out with
        // its previous measured size right after a cold-start focus, leaving the params and the view out of
        // sync until the viewer presses a key.
        glass.layout(glass.getLeft(), glass.getTop(), glass.getLeft() + w, glass.getTop() + h);
        glass.setRadius(AppAdapter.cornerRadius((int) cardW, density) * AppAdapter.FOCUS_SCALE + pad);
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
            if (!Grid.atRowStart(position, COLUMNS)) return false;
            nudgeGlass(-1, 0);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (!Grid.atRowEnd(position, count, COLUMNS)) return false;
            nudgeGlass(1, 0);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (Grid.inDock(position, COLUMNS)) {
                // Dock row. With the news row above it, Up goes to the carousel; without it, the key
                // falls through to the focus search and reaches the top bar, as before. The grid's
                // nextFocusUp does not help here: the focused view is the tile inside it.
                if (newsCarousel != null && newsCarousel.getVisibility() == View.VISIBLE) {
                    newsCarousel.requestFocus();
                    return true;
                }
                return false;
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = Grid.lastRowStart(count, COLUMNS);
            if (position >= lastRowStart) {                // already on the last row: stay put
                nudgeGlass(0, 1);
                return true;
            }
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    // ---- news (RSS) ------------------------------------------------------------------------------

    private View newsCarousel;
    private NewsBackdropView newsBackdrop;
    private TextView newsKicker, newsHeadline, newsSummary, newsMeta;
    private LinearLayout newsDots;
    private List<NewsFeed.Headline> newsHeadlines = Collections.emptyList();
    private int newsIndex = -1;
    /** Advances the carousel while the viewer is not on it, like the upstream row (news_rotate). */
    private final Handler newsRotate = new Handler(Looper.getMainLooper());
    private final Runnable newsRotateStep = this::rotateNews;
    private static final long NEWS_ROTATE_MS = 20_000;

    private void rotateNews() {
        if (newsHeadlines.size() < 2 || newsIndex < 0 || !newsRotateAllowed()) return;
        showScene(newsIndex + 1);
        newsRotate.postDelayed(newsRotateStep, NEWS_ROTATE_MS);
    }

    /** Rotation stops while the viewer is on the carousel (reading or moving through it). */
    private boolean newsRotateAllowed() {
        if (newsCarousel == null || newsCarousel.getVisibility() != View.VISIBLE) return false;
        if (!NewsFeed.enabled(this)) return false;
        return !newsCarousel.hasFocus();
    }

    private void scheduleNewsRotation() {
        newsRotate.removeCallbacks(newsRotateStep);
        if (newsHeadlines.size() > 1) newsRotate.postDelayed(newsRotateStep, NEWS_ROTATE_MS);
    }

    /** Wires the carousel. Gone unless headlines arrive, so an RSS-off Home draws none of it. */
    private void setupNews() {
        newsCarousel = findViewById(R.id.newsCarousel);
        if (newsCarousel == null) return;
        newsBackdrop = findViewById(R.id.newsBackdrop);
        newsKicker = findViewById(R.id.newsKicker);
        newsHeadline = findViewById(R.id.newsHeadline);
        newsSummary = findViewById(R.id.newsSummary);
        newsMeta = findViewById(R.id.newsMeta);
        newsDots = findViewById(R.id.newsDots);
        newsCarousel.setOnKeyListener(this::onCarouselKey);
        newsCarousel.setOnClickListener(v -> openCurrentNews());
        newsCarousel.setOnFocusChangeListener((v, focused) -> {
            if (newsBackdrop != null) newsBackdrop.setFocused(focused);
            // On the carousel the viewer is choosing: hold the headline still.
            if (focused) newsRotate.removeCallbacks(newsRotateStep);
            else scheduleNewsRotation();
        });
        // Without this the focus search skipped the carousel and landed on the top bar's buttons.
        // Down from the carousel goes into the grid; up from the grid comes back here.
        newsCarousel.setNextFocusDownId(R.id.grid);
        grid.setNextFocusUpId(R.id.newsCarousel);
    }

    /**
     * Reads the cache and, when it is older than the refresh interval, fetches in the background.
     * Everything here is off when the row is disabled: no request, no view, no work.
     */
    private void refreshNews() {
        if (newsCarousel == null || light) { hideNewsScene(); return; }
        if (!NewsFeed.enabled(this)) { hideNewsScene(); return; }
        List<NewsFeed.Headline> cached = NewsFeed.cached(this);
        if (!cached.isEmpty()) showNews(cached);
        if (NewsFeed.stale(this)) NewsFeed.fetch(this, this::showNews);
    }

    private void showNews(List<NewsFeed.Headline> headlines) {
        if (headlines == null || headlines.isEmpty() || newsCarousel == null) {
            hideNewsScene();
            return;
        }
        newsHeadlines = headlines;
        if (newsIndex < 0 || newsIndex >= headlines.size()) newsIndex = 0;
        if (newsCarousel.getVisibility() != View.VISIBLE) {
            newsCarousel.setVisibility(View.VISIBLE);
            // The carousel takes a fixed slice under the header; the grid keeps its own padding.
            ViewGroup.LayoutParams lp = newsCarousel.getLayoutParams();
            lp.height = (int) (250 * density);
            newsCarousel.setLayoutParams(lp);
        }
        showScene(newsIndex);
        scheduleNewsRotation();
    }

    private void showScene(int index) {
        if (newsHeadlines.isEmpty()) return;
        newsIndex = ((index % newsHeadlines.size()) + newsHeadlines.size()) % newsHeadlines.size();
        NewsFeed.Headline h = newsHeadlines.get(newsIndex);
        if (newsKicker != null) newsKicker.setText(h.category.isEmpty() ? h.source : h.source + " · " + h.category);
        if (newsHeadline != null) newsHeadline.setText(h.title);
        if (newsSummary != null) {
            newsSummary.setText(h.summary);
            newsSummary.setVisibility(h.summary.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (newsMeta != null) newsMeta.setText(newsMeta(h));
        rebuildNewsDots();
        if (newsBackdrop != null) newsBackdrop.show(h, true);
    }

    /** "21/10 · 08:15" when the feed gave a date, the source alone when it did not. */
    private String newsMeta(NewsFeed.Headline h) {
        if (h.published <= 0) return h.source;
        String when = new java.text.SimpleDateFormat("dd/MM · HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(h.published));
        return h.source.isEmpty() ? when : when + " · " + h.source;
    }

    private void rebuildNewsDots() {
        if (newsDots == null) return;
        if (newsDots.getChildCount() != newsHeadlines.size()) {
            newsDots.removeAllViews();
            for (int i = 0; i < newsHeadlines.size(); i++) {
                TextView dot = new TextView(this);
                dot.setText("•");
                dot.setTextSize(13);
                dot.setIncludeFontPadding(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart((int) (6 * density));
                dot.setLayoutParams(lp);
                newsDots.addView(dot);
            }
        }
        for (int i = 0; i < newsDots.getChildCount(); i++) {
            newsDots.getChildAt(i).setAlpha(i == newsIndex ? 1f : 0.35f);
        }
    }

    private boolean onCarouselKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (newsHeadlines.size() > 1) showScene(newsIndex - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (newsHeadlines.size() > 1) showScene(newsIndex + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focusTile(lastFocused % COLUMNS);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                return false;                     // the focus search takes it to the top bar
            default:
                return false;
        }
    }

    private void openCurrentNews() {
        if (newsIndex < 0 || newsIndex >= newsHeadlines.size()) return;
        NewsFeed.Headline h = newsHeadlines.get(newsIndex);
        if (h.link.isEmpty()) return;
        // Inside the launcher: this TV's browser ignores ACTION_VIEW and opens its own home, so the
        // article never showed. If the reader cannot be opened, the browser is still the fallback.
        try {
            open(new Intent(this, NewsReaderActivity.class).putExtra(NewsReaderActivity.EXTRA_URL, h.link));
        } catch (Exception e) {
            try {
                open(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(h.link)));
            } catch (Exception again) {
                Toast.makeText(this, getString(R.string.cant_open, h.source), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideNewsScene() {
        newsRotate.removeCallbacks(newsRotateStep);
        newsHeadlines = Collections.emptyList();
        newsIndex = -1;
        if (newsCarousel != null && newsCarousel.getVisibility() == View.VISIBLE) {
            newsCarousel.setVisibility(View.GONE);
            // The grid's own focus search goes back to the top bar when there is no carousel.
            if (grid != null) grid.setNextFocusUpId(View.NO_ID);
        }
        if (newsBackdrop != null) {
            newsBackdrop.setVisibility(View.GONE);
            newsBackdrop.release();
        }
    }

    /**
     * The grid's top padding, so the dock sits on the bottom edge of the screen and the rows below it
     * stay hidden until the viewer presses Down.
     *
     * The layout manager calls this before it lays the children out, so the first pass already has the
     * right padding and the grid does not inflate rows from the top of the screen only to push them back
     * out (upstream PR #446). The layout listener calls it too, as a safety net for a size that changed
     * after the fact. It only writes when the padding really changes, so it cannot loop the layout.
     */
    private void applyGridPadding() {
        int h = grid.getHeight();
        int w = grid.getWidth();
        if (h <= 0 || w <= 0) return;
        // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
        int inner = w - grid.getPaddingLeft() - grid.getPaddingRight();
        int itemH = AppAdapter.itemHeightPx(
                AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
        int want = Grid.topPadding(h, itemH, density);
        if (grid.getPaddingTop() != want) {
            grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
        }
    }

    /** Refit the glass to the currently focused holder; used after cold-start layout settles. */
    private void refitGlass() {
        if (isDestroyed() || adapter.getItemCount() == 0) return;
        int pos = Math.max(0, Math.min(lastFocused, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() <= 0) return;
        float pad = 6f * density;
        int w = (int) (card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad);
        int h = (int) (card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad);
        if (!glassShown || card.getWidth() != glassCardW
                || glass.getWidth() != w || glass.getHeight() != h) {
            onTileFocused(pos, card);
        }
    }

    /** At a grid edge, a key with nowhere to go: the highlight leans that way and springs back. */
    private void nudgeGlass(int dirX, int dirY) {
        if (light || !glassShown || springX.isRunning() || springY.isRunning()) return;
        // An impulse at rest peaks at ~0.022 s × velocity with this spring: ~6dp, subtle on purpose.
        float v = 280f * density;
        if (dirX != 0) springX.setStartVelocity(dirX * v).animateToFinalPosition(glassTargetX);
        if (dirY != 0) springY.setStartVelocity(dirY * v).animateToFinalPosition(glassTargetY);
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

    /**
     * Home backgrounds in menu order: saved id, then a solid colour (0 = none, the window's black) or a
     * bundled image or a BlurHash of a photo that is not bundled. Names are R.array.wallpaper_names in the same
     * order. Colours keep at least 13:1 contrast with the white header text; the photos (and the hashes,
     * taken from them) have a dark scrim baked in behind it. Hash sources: launcher/NOTICE.md.
     */
    static final String[] BG_IDS = {"black", "midnight", "forest", "plum", "wine", "graphite", "petrol",
            "aurora", "iss", "nebula_blur", "milkyway_blur", "trails_blur"};
    private static final int[] BG_COLORS = {0, 0xFF0B1A2E, 0xFF0E2A1C, 0xFF2A1033, 0xFF3A0D16, 0xFF1C1E22, 0xFF062F33,
            0, 0, 0, 0, 0};
    private static final int[] BG_IMAGES = {0, 0, 0, 0, 0, 0, 0, R.drawable.wallpaper_aurora, R.drawable.wallpaper_iss,
            0, 0, 0};
    private static final String[] BG_HASHES = {null, null, null, null, null, null, null, null, null,
            "LJC$i-E1WXRj0e%2oJj]M{bcoLjs", "L47nOID*IAs:E4%LRiWA00xuozRj", "LB9%|]?cD%o#4WRV-poeH;W9S%WC"};

    private static int backgroundIndex(String id) {
        return Math.max(0, Arrays.asList(BG_IDS).indexOf(id));
    }

    /** Shows the chosen colour or image (decoded off the main thread), or black in ultralight mode. */
    private void updateWallpaper() {
        if (wallpaperView == null) return;
        int i = backgroundIndex(prefs.wallpaper());
        if (!light && BG_HASHES[i] != null) {
            if (wallpaperBitmap == null || wallpaperRes != -1 - i) {
                wallpaperRequest++;
                wallpaperLoadingRes = 0;
                releaseWallpaper();
                wallpaperBitmap = BlurHash.decode(BG_HASHES[i], 32, 18);   // ~600 pixels: fine on the main thread
                wallpaperRes = -1 - i;
            }
            wallpaperView.setBackgroundColor(0);
            wallpaperView.setImageBitmap(wallpaperBitmap);
            wallpaperView.setVisibility(View.VISIBLE);
            return;
        }
        int image = light ? 0 : BG_IMAGES[i];
        if (image == 0) {
            int color = light ? 0 : BG_COLORS[i];
            wallpaperRequest++;
            wallpaperLoadingRes = 0;
            releaseWallpaper();
            wallpaperView.setBackgroundColor(color);
            wallpaperView.setVisibility(color == 0 ? View.GONE : View.VISIBLE);
            return;
        }
        wallpaperView.setBackgroundColor(0);
        wallpaperView.setVisibility(View.VISIBLE);
        if (wallpaperBitmap != null && wallpaperRes == image) {
            wallpaperView.setImageBitmap(wallpaperBitmap);
            return;
        }
        if (wallpaperLoadingRes == image) return;
        wallpaperLoadingRes = image;
        final int request = ++wallpaperRequest;
        final Resources resources = getResources();
        WALLPAPER.execute(() -> {
            Bitmap bitmap = decodeWallpaper(resources, image);
            runOnUiThread(() -> {
                if (request != wallpaperRequest || isFinishing() || isDestroyed()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                wallpaperLoadingRes = 0;
                if (bitmap == null) return;
                releaseWallpaper();
                wallpaperBitmap = bitmap;
                wallpaperRes = image;
                wallpaperView.setImageBitmap(bitmap);
                wallpaperView.setVisibility(View.VISIBLE);
            });
        });
    }

    /**
     * Decodes a wallpaper at the size the screen actually shows it, so it costs a screen-sized bitmap
     * and no more (#277, from upstream #166). Two passes over the JPEG: the first reads the size for
     * nothing, the second decodes. A picture over about 4K is refused and the caller keeps the plain
     * colour: decoding one would ask for hundreds of MB on a TV with 1 GB of RAM.
     *
     * The bundled wallpapers are 1920x1080 and the BeyondTV2 is 1920x1080, so on this TV both passes
     * return the same options the decode had before and the picture costs 4.1 MB as it always did; the
     * sampling is what keeps a larger screen, or a larger picture, from costing more than that.
     */
    private static Bitmap decodeWallpaper(Resources resources, int image) {
        DisplayMetrics dm = resources.getDisplayMetrics();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        bounds.inScaled = false;
        BitmapFactory.decodeResource(resources, image, bounds);
        if (!WallpaperSize.accepted(bounds.outWidth, bounds.outHeight)) return null;

        int sample = WallpaperSize.sampleSize(bounds.outWidth, bounds.outHeight, dm.widthPixels, dm.heightPixels);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inScaled = false;
        if (sample > 1) options.inSampleSize = sample;
        // A picture between two sample sizes still decodes at full size; inDensity/inTargetDensity scale
        // it while the decoder has it open, down to the side that limits what centerCrop shows.
        int[] scale = WallpaperSize.coverScale(bounds.outWidth / sample, bounds.outHeight / sample,
                dm.widthPixels, dm.heightPixels);
        if (scale != null) {
            options.inScaled = true;
            options.inDensity = scale[0];
            options.inTargetDensity = scale[1];
        }
        return BitmapFactory.decodeResource(resources, image, options);
    }

    /** Drops the only wallpaper bitmap: black mode, destroy, or just before a new decode is shown. */
    private void releaseWallpaper() {
        if (wallpaperView != null) wallpaperView.setImageDrawable(null);
        if (wallpaperBitmap != null) {
            wallpaperBitmap.recycle();
            wallpaperBitmap = null;
            wallpaperRes = 0;
        }
    }

    private interface TextResult {
        void accept(String text);
    }

    private void askText(int title, String current, int hint, TextResult result) {
        askText(title, current, hint, result, null);
    }

    /** {@code after} runs when the dialog closes, OK or Cancel (after {@code result}). */
    private void askText(int title, String current, int hint, TextResult result, Runnable after) {
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
                .setOnDismissListener(d -> {
                    if (after != null && !isDestroyed()) after.run();
                })
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
        if (isSettingsKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /**
     * The Settings button on some TCL remotes is a vendor keycode rather than KEYCODE_SETTINGS, and it
     * only reaches HomeKeyService when that service is running. Consume it here too, so the button
     * still opens Settings with the stock Home disabled. Vendor codes are opt-in in config.xml.
     */
    private boolean isSettingsKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean match = code == KeyEvent.KEYCODE_SETTINGS;
        for (int vendor : settingsKeycodes) {
            if (vendor == code) {
                match = true;
                break;
            }
        }
        if (!match) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            open(new Intent(Settings.ACTION_SETTINGS));
        }
        return true;
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
            if (!info.isHidden(this) && info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(info);
        }
        Collections.sort(hdmi, (a, b) -> inputLabel(a).compareToIgnoreCase(inputLabel(b)));
        if (index >= hdmi.size()) return false;
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                TvContract.buildChannelUriForPassthroughInput(hdmi.get(index).getId()), "vnd.android.cursor.item/channel"));
        return true;
    }

    /** Settings > Inputs custom name when one exists, otherwise the standard input label. */
    private String inputLabel(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        CharSequence standard = info.loadLabel(this);
        String label = custom != null && custom.length() > 0
                ? custom.toString()
                : standard != null ? standard.toString() : info.getId();
        if (info.getType() == TvInputInfo.TYPE_HDMI && !label.toUpperCase(Locale.ROOT).contains("HDMI")) {
            label = "HDMI " + label;
        }
        return label;
    }

    /** Home pressed three times within 10 s: recovery menu (original launcher, Settings, defaults). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && ACTION_OPEN_INPUTS.equals(intent.getAction())) {
            // The remote's Input/Source button (via InputKeyService) toggles the Inputs dropdown,
            // opening it from any app and closing it again on the next press (#244).
            handler.post(() -> {
                if (inputsPopup != null) {
                    inputsPopup.dismiss();
                } else {
                    showInputs();
                }
            });
            return;
        }
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
            applyAccent();
            applyExperienceState();
            AppLoader.reload(this);
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

    private interface Label {
        String get();
    }

    private interface Step {
        void step(int direction);   // -1 = left, +1 = right or OK
    }

    private final class MenuItem {
        final int icon;
        final String title;
        final Runnable action;
        // A setting with values: OK and Right step forward, Left steps back, and the menu stays open.
        final Label label;
        final Step step;

        MenuItem(int icon, int title, Runnable action) {
            this(icon, getString(title), action);
        }

        MenuItem(int icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
            this.label = null;
            this.step = null;
        }

        MenuItem(int icon, Label label, Step step) {
            this.icon = icon;
            this.title = label.get();
            this.action = null;
            this.label = label;
            this.step = step;
        }
    }

    private void showMenu(View anchor, List<MenuItem> items) {
        showMenu(anchor, items, 0, null);
    }

    /**
     * "Label: value" rows (from the opt_* strings, in any language that uses a colon) show the label
     * fixed and the value in the scrolling view; anything else goes whole into the scrolling view.
     */
    private void setRowText(TextView label, TextView value, String text) {
        int colon = text.indexOf(':');
        boolean split = colon > 0 && colon < text.length() - 1;
        label.setVisibility(split ? View.VISIBLE : View.GONE);
        if (split) label.setText(text.substring(0, colon + 1));
        value.setText(split ? text.substring(colon + 1).trim() : text);
        ((ViewGroup.MarginLayoutParams) value.getLayoutParams()).setMarginStart((int) ((split ? 5 : 10) * density));
    }

    /**
     * A dropdown of actions in the Inputs menu's style, below the anchor (or above it near the bottom).
     * With no anchor it is a wider panel fixed at the right edge, vertically centred (the Home options).
     */
    private void showMenu(View anchor, List<MenuItem> items, int focusIndex, Runnable onDismiss) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int popupWidth = (int) ((anchor == null ? 380 : 300) * density);
        final PopupWindow popup = new PopupWindow(panel, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        for (MenuItem item : items) {
            View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(item.icon);
            TextView label = row.findViewById(R.id.label);
            TextView title = row.findViewById(R.id.title);
            setRowText(label, title, item.title);
            // A value too long for the row scrolls once into view while the row has focus.
            title.setMarqueeRepeatLimit(1);
            row.setOnFocusChangeListener((v, focused) -> {
                title.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
                title.setSelected(focused);
            });
            TextView status = row.findViewById(R.id.status);
            row.findViewById(R.id.dot).setVisibility(View.GONE);
            if (item.step == null) {
                status.setVisibility(View.GONE);
                row.setOnClickListener(v -> {
                    popup.dismiss();
                    item.action.run();
                });
            } else {
                status.setText(R.string.menu_stepper_hint);
                status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                status.setTextColor(title.getTextColors());
                row.setOnClickListener(v -> {
                    item.step.step(1);
                    setRowText(label, title, item.label.get());
                });
                row.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        item.step.step(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                        setRowText(label, title, item.label.get());
                    }
                    return true;
                });
            }
            rows.addView(row);
        }
        if (onDismiss != null) popup.setOnDismissListener(onDismiss::run);
        if (anchor == null) {
            popup.showAtLocation(root, Gravity.END | Gravity.CENTER_VERTICAL, (int) (40 * density), 0);
            rows.getChildAt(Math.max(0, Math.min(focusIndex, rows.getChildCount() - 1))).requestFocus();
            return;
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
        rows.getChildAt(Math.max(0, Math.min(focusIndex, rows.getChildCount() - 1))).requestFocus();
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        // Remember this popup so a second press of the remote's Input button (delivered as
        // ACTION_OPEN_INPUTS via InputKeyService) can dismiss it again.
        if (inputsPopup != null) inputsPopup.dismiss();
        inputsPopup = popup;
        popup.setOnDismissListener(() -> inputsPopup = null);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>();
            for (TvInputInfo info : tim.getTvInputList()) {
                // Inputs hidden in Settings > Inputs should not come back through this menu or the
                // 1-4 shortcuts (#291, upstream mwamodo).
                if (!info.isHidden(this)) inputs.add(info);
            }
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return inputLabel(a).compareToIgnoreCase(inputLabel(b));
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
                String label = inputLabel(info);
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                final String id = info.getId();
                // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
                // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
                int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        // A configured source panel and no Live TV row to offer next to it: the panel is the whole menu.
        // The BYINTEK U4 needs this, as its TvInputManager lists three HDMI ports and an AV input it doesn't have.
        if (!sourcePanelService.isEmpty() && liveTv == null) {
            openSourcePanel();
            popup.dismiss();
            return;
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvLaunch = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvLaunch));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row, up to 360dp: a long custom input name (#291) must not make the popup
        // wider than the useful part of a TV screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.min(Math.max(width, panel.getMeasuredWidth()), (int) (360 * density)));

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

    /** The Live TV app's launch intent, or null when no Live TV row should be added to the menu. */
    private Intent liveTvIntent() {
        return liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a
     *  service, or an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            // The component may be an activity (BYINTEK U4) or a service (the older TCL sets);
            // resolveActivity distinguishes the two and the IF is harmless when the config is empty.
            if (getPackageManager().resolveActivity(i, 0) != null) {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
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
