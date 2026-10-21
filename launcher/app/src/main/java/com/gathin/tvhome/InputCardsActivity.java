package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/**
 * The owner's input cards, one block per TV input (HDMI, AV): rename it ("Xbox"), pick its icon
 * and choose whether the card shows on Home. Every change saves at once; RESULT_OK on the way out
 * makes MainActivity recreate() so the grid picks the cards up right away.
 */
public final class InputCardsActivity extends Activity {
    private SharedPreferences choices;
    private Map<String, InputCards> saved;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_input_cards);
        choices = getSharedPreferences(HomePrefs.PREFS_NAME, MODE_PRIVATE);
        saved = InputCards.load(choices);
        list = findViewById(R.id.list);
        fill();
    }

    private void fill() {
        // Renaming, picking an icon or toggling "show" rebuilds this list; without this the focus fell
        // back to the first row and the viewer lost the input he was editing (the defect the original
        // commit had). The row count does not change between rebuilds, so the index is stable.
        int focused = focusedIndex();
        list.removeAllViews();
        List<TvInputInfo> inputs = InputCards.cardInputs(this);
        if (inputs.isEmpty()) {
            plain(getString(R.string.no_inputs));
            return;
        }
        for (TvInputInfo info : inputs) {
            String id = info.getId();
            String port = InputCards.portLabel(this, info);
            InputCards card = saved.get(id);
            String name = card == null ? "" : card.name;
            String icon = card == null ? InputCards.ICON_LIVE_TV : card.icon;
            boolean shown = card != null && card.shown;

            plain(port);   // the input's own name heads its block
            row(getString(R.string.input_card_name,
                    name.isEmpty() ? getString(R.string.input_card_name_default) : name),
                    () -> rename(id, port, name, icon, shown));
            row(getString(R.string.input_card_icon, getString(InputCards.iconLabelRes(icon))),
                    () -> pickIcon(id, name, icon, shown));
            row(getString(R.string.input_card_shown, getString(shown ? R.string.on : R.string.off)), () -> {
                save(id, name, icon, !shown);
                fill();
            });
        }
        // Put the focus back where it was; on the first build, on the first row under the first header.
        if (focused >= 0 && focused < list.getChildCount()) list.getChildAt(focused).requestFocus();
        else if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    /** Index of the focused row in the list, or -1 when nothing there has the focus. */
    private int focusedIndex() {
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocused()) return i;
        }
        return -1;
    }

    private void rename(String id, String port, String name, String icon, boolean shown) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(name);
        input.setSelection(name.length());
        input.setHint(port);
        new AlertDialog.Builder(this)
                .setTitle(R.string.card_name_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    save(id, input.getText().toString().trim(), icon, shown);
                    fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickIcon(String id, String name, String icon, boolean shown) {
        String[] keys = InputCards.iconKeys();
        String[] labels = new String[keys.length];
        int checked = 0;
        for (int i = 0; i < keys.length; i++) {
            labels[i] = getString(InputCards.iconLabelRes(keys[i]));
            if (keys[i].equals(icon)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.input_card_icon_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    save(id, name, keys[which], shown);
                    fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Writes the card and tells the Home behind this screen to rebuild when it closes. */
    private void save(String id, String name, String icon, boolean shown) {
        InputCards card = new InputCards(id, name, icon, shown);
        saved.put(id, card);
        InputCards.save(choices, card);
        setResult(RESULT_OK);
    }

    /** A plain text line: the block header or the empty-inputs note. Never takes the remote's focus. */
    private void plain(String text) {
        TextView line = new TextView(this, null, 0, R.style.SettingsRow);
        line.setText(text);
        line.setAllCaps(true);
        // The style is the focusable one used by the rows; a header has no action, so it must not stop
        // the D-pad between two inputs (the same defect the original commit had).
        line.setFocusable(false);
        line.setClickable(false);
        line.setBackground(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (14 * getResources().getDisplayMetrics().density);
        line.setLayoutParams(lp);
        list.addView(line);
    }

    /** A settings row that opens this input's editor. */
    private void row(String text, Runnable action) {
        TextView line = new TextView(this, null, 0, R.style.SettingsRow);
        line.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        line.setLayoutParams(lp);
        line.setOnClickListener(v -> {
            action.run();
        });
        list.addView(line);
    }
}
