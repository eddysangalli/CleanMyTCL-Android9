package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The news sources (NewsFeed): turn the row on or off, pick the built-in feeds of the mix, and add
 * any other RSS / Atom URL. Every change saves at once and drops the cache, so the Home behind this
 * screen fetches the owner's mix again; RESULT_OK on the way out makes MainActivity recreate().
 */
public final class NewsSettingsActivity extends Activity {
    /** The intervals the owner can pick, cycled in order by the refresh row. */
    private static final int[] REFRESH_CHOICES = {10, 15, 30, 60, 180};
    /** How long a downloaded photo is kept, in hours, cycled in order by the photo row. */
    private static final int[] IMAGE_CHOICES = {6, 12, 24, 72};

    private SharedPreferences choices;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_news_settings);
        choices = getSharedPreferences(HomePrefs.PREFS_NAME, MODE_PRIVATE);
        list = findViewById(R.id.list);
        fill();
    }

    private void fill() {
        int focused = focusedIndex();
        list.removeAllViews();
        row(getString(R.string.news_enabled,
                getString(NewsFeed.enabled(choices) ? R.string.on : R.string.off)), () -> {
            put("news_enabled", !NewsFeed.enabled(choices));
        });
        boolean idle = choices.getBoolean("news_idle", true);
        row(getString(R.string.news_idle_enabled,
                getString(idle ? R.string.on : R.string.off)), () -> {
            choices.edit().putBoolean("news_idle", !idle).apply();
            setResult(RESULT_OK);
            fill();
        });
        // The cinematic Home (news_cinema): the focused headline's photo behind the whole screen.
        // Reversible, so an owner who prefers the plain row underneath can switch it back off.
        boolean cinema = choices.getBoolean("news_cinema", true);
        row(getString(R.string.news_cinema,
                getString(cinema ? R.string.on : R.string.off)), () -> {
            choices.edit().putBoolean("news_cinema", !cinema).apply();
            setResult(RESULT_OK);
            fill();
        });
        boolean rotate = choices.getBoolean("news_rotate", true);
        row(getString(R.string.news_rotate,
                getString(rotate ? R.string.on : R.string.off)), () -> {
            choices.edit().putBoolean("news_rotate", !rotate).apply();
            setResult(RESULT_OK);
            fill();
        });
        row(getString(R.string.news_refresh, NewsFeed.refreshMinutes(choices)), () -> {
            choices.edit().putInt("news_refresh_min",
                    next(NewsFeed.refreshMinutes(choices), REFRESH_CHOICES)).apply();
            repaint();
        });
        row(getString(R.string.news_image_age, NewsFeed.imageHours(choices)), () -> {
            choices.edit().putInt("news_image_hours",
                    next(NewsFeed.imageHours(choices), IMAGE_CHOICES)).apply();
            repaint();
        });
        for (NewsFeed.Source s : NewsFeed.catalog()) {
            boolean on = NewsFeed.sourceOn(choices, s);
            row(getString(s.nameRes) + ": " + getString(on ? R.string.on : R.string.off), () -> {
                put("news_source_" + s.key, !on);
            });
        }
        String custom = NewsFeed.customUrl(choices);
        row(getString(R.string.news_custom,
                custom.isEmpty() ? getString(R.string.news_custom_none) : custom), this::editCustom);
        // Clears the cached headlines and photos and asks Home to fetch again. It is also the way out of
        // a headline cached before a parser fix: the cache lives for the refresh interval.
        row(getString(R.string.news_clear), () -> {
            NewsFeed.clearCache(this);
            setResult(RESULT_OK);
            Toast.makeText(this, getString(R.string.news_empty), Toast.LENGTH_SHORT).show();
            fill();
        });
        // Back to the row the viewer was on; the first one only on the first build.
        if (focused >= 0 && focused < list.getChildCount()) list.getChildAt(focused).requestFocus();
        else if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    /**
     * Index of the focused row, or -1. Every toggle rebuilds the list, and without this the focus fell
     * back to the first row: moving down and pressing OK then flipped a setting two rows away.
     */
    private int focusedIndex() {
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocused()) return i;
        }
        return -1;
    }

    /** One custom feed URL on top of the built-in mix; empty removes it. */
    private void editCustom() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String current = NewsFeed.customUrl(choices);
        input.setText(current);
        input.setSelection(current.length());
        input.setHint(R.string.news_custom_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.news_custom_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    choices.edit().putString("news_custom_url", input.getText().toString().trim()).apply();
                    saved();
                    fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Writes one key and drops the cached mix, so the next Home refetches with the new choices. */
    private void put(String key, boolean value) {
        choices.edit().putBoolean(key, value).apply();
        // The master switch changes nothing about what the feeds contain: keep the cached mix, so
        // turning the row back on restores it at once instead of an empty Home waiting on the network.
        if ("news_enabled".equals(key)) setResult(RESULT_OK);
        else saved();
        fill();
    }

    /**
     * For a choice that only changes when things happen, never what the feeds contain: repaint the
     * rows so the new value is visible and tell Home to re-read it, but keep the owner's cache —
     * an interval is no reason to empty a screenful of headlines.
     */
    private void repaint() {
        setResult(RESULT_OK);
        fill();
    }

    /** The next value in a cycling list, wrapping to the start; values off the list restart it. */
    private static int next(int current, int[] choices) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) return choices[(i + 1) % choices.length];
        }
        return choices[0];
    }

    /** Every change tells the Home behind this screen to rebuild when it closes. */
    private void saved() {
        NewsFeed.clearCache(this);
        setResult(RESULT_OK);
    }

    /** A settings row that runs this choice's editor. */
    private void row(String text, Runnable action) {
        TextView line = new TextView(this, null, 0, R.style.SettingsRow);
        line.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        line.setLayoutParams(lp);
        line.setOnClickListener(v -> action.run());
        list.addView(line);
    }
}
