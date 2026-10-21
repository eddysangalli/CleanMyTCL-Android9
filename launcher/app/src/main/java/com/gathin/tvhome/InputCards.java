package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-TV Home cards for the TV's inputs (HDMI, AV): the owner picks the input, the name the card
 * shows ("Xbox") and its icon, and the card sits with the dock apps and opens that input.
 * Saved per input id in the launcher's settings preferences: input_card.&lt;id&gt;.&lt;field&gt;.
 */
final class InputCards {
    /** Preference key prefix; the field suffix follows the input id. */
    static final String PREFIX = "input_card.";
    static final String ICON_LIVE_TV = "live_tv";
    static final String ICON_GAME = "game";
    static final String ICON_MOVIE = "movie";
    static final String ICON_MUSIC = "music";
    static final String ICON_COMPUTER = "computer";

    /** TvInputInfo id: the stable key. */
    final String inputId;
    /** The owner's name for the card; empty = show the input's own label. */
    final String name;
    /** Icon key: one of the ICON_* constants. */
    final String icon;
    /** Whether the card shows on Home. */
    final boolean shown;
    /** The label on the tile: the name, or the input's label when there is no name. */
    final String displayLabel;
    /** The caption beside the tile's name: the input's label, unless the name already is it. */
    final String caption;

    InputCards(String inputId, String name, String icon, boolean shown) {
        this(inputId, name, icon, shown, "", null);
    }

    private InputCards(String inputId, String name, String icon, boolean shown,
                       String displayLabel, String caption) {
        this.inputId = inputId;
        this.name = name == null ? "" : name;
        this.icon = icon == null || icon.isEmpty() ? ICON_LIVE_TV : icon;
        this.shown = shown;
        this.displayLabel = displayLabel;
        this.caption = caption;
    }

    /** This card ready for the grid: an unnamed card shows the input's own label. */
    InputCards resolved(String portLabel) {
        String base = name.isEmpty() ? portLabel : name;
        return new InputCards(inputId, name, icon, shown, base,
                name.isEmpty() || name.equals(portLabel) ? null : portLabel);
    }

    /** All saved cards, by input id. Missing fields keep their defaults. */
    static Map<String, InputCards> load(SharedPreferences choices) {
        Map<String, InputCards> out = new HashMap<>();
        for (String key : choices.getAll().keySet()) {
            if (!key.startsWith(PREFIX)) continue;
            String rest = key.substring(PREFIX.length());
            String field = null;
            for (String suffix : new String[]{"name", "icon", "shown"}) {
                if (rest.endsWith("." + suffix)) {
                    field = suffix;
                    break;
                }
            }
            if (field == null) continue;
            String id = rest.substring(0, rest.length() - field.length() - 1);
            if (id.isEmpty()) continue;
            InputCards card = out.get(id);
            String name = card == null ? "" : card.name;
            String icon = card == null ? ICON_LIVE_TV : card.icon;
            boolean shown = card != null && card.shown;
            if (field.equals("name")) name = choices.getString(key, "");
            else if (field.equals("icon")) icon = choices.getString(key, ICON_LIVE_TV);
            else shown = choices.getBoolean(key, false);
            out.put(id, new InputCards(id, name, icon, shown));
        }
        return out;
    }

    /** Writes this card's three fields together. */
    static void save(SharedPreferences choices, InputCards card) {
        choices.edit()
                .putString(PREFIX + card.inputId + ".name", card.name)
                .putString(PREFIX + card.inputId + ".icon", card.icon)
                .putBoolean(PREFIX + card.inputId + ".shown", card.shown)
                .apply();
    }

    /** The inputs a card can point at: passthrough ports the TV does not hide. */
    static List<TvInputInfo> cardInputs(Context ctx) {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput() && !info.isHidden(ctx)) out.add(info);
        }
        return out;
    }

    /** The input's own label ("HDMI 1"), falling back to the id's last segment. */
    /**
     * The input's own label ("HDMI 1"), falling back to the id's last segment. The owner's custom name
     * from Settings > Inputs ("Console de jogos") wins: the Inputs menu shows it (MainActivity.inputLabel)
     * and a card that named the same port differently from the menu would be confusing.
     */
    static String portLabel(Context ctx, TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(ctx);
        CharSequence label = custom != null && custom.length() > 0 ? custom : info.loadLabel(ctx);
        String text = label == null ? "" : label.toString().trim();
        if (text.isEmpty()) {
            String id = info.getId();
            text = id.substring(id.lastIndexOf('/') + 1);
        }
        return text;
    }

    /** The tile's icon drawable for an icon key. */
    static int iconRes(String icon) {
        if (ICON_GAME.equals(icon)) return R.drawable.ic_game;
        if (ICON_MOVIE.equals(icon)) return R.drawable.ic_movie;
        if (ICON_MUSIC.equals(icon)) return R.drawable.ic_music;
        if (ICON_COMPUTER.equals(icon)) return R.drawable.ic_computer;
        return R.drawable.ic_livetv;
    }

    /** The icon's name in the settings dialogs for an icon key. */
    static int iconLabelRes(String icon) {
        if (ICON_GAME.equals(icon)) return R.string.icon_game;
        if (ICON_MOVIE.equals(icon)) return R.string.icon_movie;
        if (ICON_MUSIC.equals(icon)) return R.string.icon_music;
        if (ICON_COMPUTER.equals(icon)) return R.string.icon_computer;
        return R.string.live_tv;
    }

    /** The icon keys in the order the picker offers them. */
    static String[] iconKeys() {
        return new String[]{ICON_LIVE_TV, ICON_GAME, ICON_MOVIE, ICON_MUSIC, ICON_COMPUTER};
    }
}
