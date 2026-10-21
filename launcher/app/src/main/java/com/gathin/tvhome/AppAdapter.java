package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * then the apps opened most recently, then the rest alphabetically (order set by {@link AppLoader}).
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** OK held on a tile: the app's menu (dock, hide, app info). */
        void onTileMenu(int position, View card);

        /** An app was opened from its tile. */
        void onTileLaunched(Entry entry);

        /** Opens the input an INPUT tile points at (an HDMI/AV card, see {@link InputCards}). */
        void onInputRequested(String inputId);
    }

    /** One app, ready to bind: built once by {@link AppLoader} and reused until the app changes. */
    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        /** Stable key (the component), and its hash as the RecyclerView item id. */
        final String key;
        final long id;
        /** Banner pre-rendered at tile size, or null when the app only has an icon. */
        final Bitmap banner;
        final Drawable icon;
        final int ambient;
        /** TvInputInfo id for an INPUT tile (an HDMI/AV card), else null. */
        final String inputId;

        Entry(String label, ComponentName component, String key, Bitmap banner, Drawable icon, int ambient) {
            this(label, component, key, banner, icon, ambient, null);
        }

        Entry(String label, ComponentName component, String key, Bitmap banner, Drawable icon, int ambient,
              String inputId) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.key = key;
            this.id = key.hashCode();
            this.banner = banner;
            this.icon = icon;
            this.ambient = ambient;
            this.inputId = inputId;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    /** Ultralight mode: focus changes without animations (set by MainActivity from HomePrefs). */
    static boolean light;
    private static final Interpolator FOCUS_IN = new OvershootInterpolator(1.6f);
    private static final Interpolator FOCUS_OUT = new OvershootInterpolator(0f);

    private final TileListener listener;
    private final Context ctx;
    /** Input cards (HDMI/AV) resolved by the Home; empty = none shown. */
    private List<InputCards> inputCards = Collections.emptyList();
    private final int dockSize;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        // Tiles keep their identity across setEntries(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /** Dock first, then recents, then alphabetical; see {@link AppLoader}. */
    void setEntries(List<Entry> entries) {
        items.clear();
        items.addAll(entries);
        // The owner's input cards (HDMI/AV) sit with the tiles, after the apps. An input id can hold a
        // slash, so the key uses a sanitised copy to stay a valid, unique RecyclerView id.
        for (InputCards card : inputCards) {
            items.add(new Entry(card.displayLabel,
                    new ComponentName("com.gathin.tvhome.input", card.inputId.replace('/', '.')),
                    "input:" + card.inputId, null,
                    ctx.getDrawable(InputCards.iconRes(card.icon)), 0, card.inputId));
        }
        notifyDataSetChanged();
    }

    /** Input cards resolved by the Home; empty = none shown. */
    void setInputCards(List<InputCards> cards) {
        this.inputCards = cards == null ? Collections.emptyList() : cards;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        // Tiles shrink to fit when the configured columns do not fit at the design size (e.g. 5 or 6).
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density);
        int h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.entry = e;
        h.name.setText(e.label);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.banner != null) {
            h.banner.setImageBitmap(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
    }

    private static void launch(Context ctx, Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (KeyRemapService.SOURCE.equals(e.component.getPackageName())) KeyRemapService.allowSource();
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).key : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(key)) return i;
        }
        return -1;
    }

    /** Position of the first tile of this package, or -1. */
    int positionOfPkg(String pkg) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pkg.equals(pkg)) return i;
        }
        return -1;
    }

    String pkgAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : null;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon). */
    int ambientColorAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).ambient : 0xFF444444;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        /** The app this view currently shows. */
        Entry entry;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnClickListener(view -> {
                if (entry == null) return;
                if (entry.inputId != null) {
                    // An input card switches to that HDMI/AV port (same path as the Inputs pill).
                    if (listener != null) listener.onInputRequested(entry.inputId);
                    return;
                }
                launch(view.getContext(), entry);
                if (listener != null) listener.onTileLaunched(entry);
            });
            card.setOnLongClickListener(view -> {
                // An input card has no app page behind it; holding OK does nothing.
                if (entry != null && entry.inputId != null) return true;
                int pos = getBindingAdapterPosition();
                if (entry == null || listener == null || pos == RecyclerView.NO_POSITION) return false;
                listener.onTileMenu(pos, view);
                return true;
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                // The remote's menu/options key opens the same menu as holding OK.
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0) {
                    int pos = getBindingAdapterPosition();
                    if (listener == null || pos == RecyclerView.NO_POSITION) return false;
                    listener.onTileMenu(pos, view);
                    return true;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().cancel();
                name.animate().cancel();
                if (light) {
                    view.setScaleX(s);
                    view.setScaleY(s);
                    name.setAlpha(focused ? 1f : 0f);
                    name.setTranslationY(focused ? density * 4f : -density * 6f);
                } else {
                    // A hardware layer keeps the banner as a texture while it scales, instead of
                    // re-rasterising the tile on every frame of the animation.
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(focused ? FOCUS_IN : FOCUS_OUT)
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
