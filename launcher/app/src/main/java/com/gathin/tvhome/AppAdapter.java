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

        Entry(String label, ComponentName component, String key, Bitmap banner, Drawable icon, int ambient) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.key = key;
            this.id = key.hashCode();
            this.banner = banner;
            this.icon = icon;
            this.ambient = ambient;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Ultralight mode: focus changes without animations (set by MainActivity from HomePrefs). */
    static boolean light;
    private static final Interpolator FOCUS_IN = new OvershootInterpolator(1.6f);
    private static final Interpolator FOCUS_OUT = new OvershootInterpolator(0f);

    private final TileListener listener;
    private final int dockSize;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(TileListener listener, int dockSize) {
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
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
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
            final float radius = density * 16f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnClickListener(view -> {
                if (entry == null) return;
                launch(view.getContext(), entry);
                if (listener != null) listener.onTileLaunched(entry);
            });
            card.setOnLongClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (entry == null || listener == null || pos == RecyclerView.NO_POSITION) return false;
                listener.onTileMenu(pos, view);
                return true;
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                if (light) {
                    view.setScaleX(s);
                    view.setScaleY(s);
                    name.setAlpha(focused ? 1f : 0f);
                    name.setTranslationY(focused ? density * 4f : -density * 6f);
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(focused ? FOCUS_IN : FOCUS_OUT)
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
