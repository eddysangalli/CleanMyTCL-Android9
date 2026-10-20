package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the viewer changes from the remote: dock order, hidden apps, recently opened apps, the ultralight
 * mode and the weather place. Defaults come from res/values/config.xml.
 */
final class HomePrefs {

    private static final int MAX_RECENTS = 8;
    /** Weather older than this is fetched again, and only while the home screen is on screen. */
    static final long WEATHER_MAX_AGE_MS = 60 * 60_000L;

    private final SharedPreferences sp;
    private final Context ctx;

    HomePrefs(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.sp = this.ctx.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    // ---- dock -----------------------------------------------------------------------------------

    List<String> dock() {
        String saved = sp.getString("dock", null);
        if (saved == null) return new ArrayList<>(Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        return split(saved);
    }

    void setDock(List<String> pkgs) {
        sp.edit().putString("dock", TextUtils.join(",", pkgs)).apply();
    }

    void resetDock() {
        sp.edit().remove("dock").apply();
    }

    // ---- hidden apps ----------------------------------------------------------------------------

    /** Hidden by config.xml plus hidden by the viewer. */
    Set<String> hidden() {
        Set<String> all = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        all.addAll(userHidden());
        return all;
    }

    Set<String> userHidden() {
        return new HashSet<>(split(sp.getString("hidden", "")));
    }

    void hide(String pkg) {
        Set<String> h = userHidden();
        h.add(pkg);
        sp.edit().putString("hidden", TextUtils.join(",", h)).apply();
    }

    void unhideAll() {
        sp.edit().remove("hidden").apply();
    }

    // ---- recently opened ------------------------------------------------------------------------

    /** Packages opened from this launcher, most recent first. */
    List<String> recents() {
        return split(sp.getString("recents", ""));
    }

    void touchRecent(String pkg) {
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > MAX_RECENTS) r.remove(r.size() - 1);
        sp.edit().putString("recents", TextUtils.join(",", r)).apply();
    }

    // ---- ultralight mode ------------------------------------------------------------------------

    /** Apps only: no ambient glow, no app title, no weather, no springs or tile animations. */
    boolean light() {
        return sp.getBoolean("light", true);   // Eddy TV default: instant focus, no motion
    }

    void setLight(boolean on) {
        sp.edit().putBoolean("light", on).apply();
    }

    // ---- columns ---------------------------------------------------------------------------------

    /** Tiles per row chosen on the TV (4-6), or res/values/config.xml when nothing valid is saved. */
    int columns() {
        int saved = sp.getInt("columns", 0);
        return saved >= 4 && saved <= 6 ? saved : ctx.getResources().getInteger(R.integer.columns);
    }

    void setColumns(int n) {
        sp.edit().putInt("columns", n).apply();
    }

    // ---- wallpaper -------------------------------------------------------------------------------

    /** Static aurora wallpaper in smooth motion; ultralight always stays black. */
    boolean wallpaper() {
        return sp.getBoolean("wallpaper", true);
    }

    void setWallpaper(boolean on) {
        sp.edit().putBoolean("wallpaper", on).apply();
    }

    // ---- family profiles -------------------------------------------------------------------------

    /** Saved profiles by label: {label: {name, message, accent, wallpaper}}. No image data is stored. */
    JSONObject profiles() {
        try {
            return new JSONObject(sp.getString("profiles", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** Saves the current name, message, accent and wallpaper flag under a label; an existing label is replaced. */
    void saveProfile(String label) {
        String key = label.trim();
        if (key.isEmpty()) return;
        try {
            JSONObject all = profiles();
            JSONObject profile = new JSONObject();
            profile.put("name", userName());
            profile.put("message", message());
            profile.put("accent", accent());
            profile.put("wallpaper", wallpaper());
            all.put(key, profile);
            sp.edit().putString("profiles", all.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    void applyProfile(String label) {
        JSONObject profile = profiles().optJSONObject(label);
        if (profile == null) return;
        sp.edit()
                .putString("name", profile.optString("name", "").trim())
                .putString("message", profile.optString("message", "").trim())
                .putInt("accent", profile.optInt("accent", 0))
                .putBoolean("wallpaper", profile.optBoolean("wallpaper", true))
                .apply();
    }

    void deleteProfile(String label) {
        JSONObject all = profiles();
        all.remove(label);
        sp.edit().putString("profiles", all.toString()).apply();
    }

    // ---- identity (Eddy TV) ----------------------------------------------------------------------

    String userName() {
        String n = sp.getString("name", "").trim();
        return n.isEmpty() ? ctx.getString(R.string.default_name) : n;
    }

    void setUserName(String name) {
        sp.edit().putString("name", name.trim()).apply();
    }

    /** Custom line under the greeting, or "" for the automatic one per time of day. */
    String message() {
        return sp.getString("message", "").trim();
    }

    void setMessage(String message) {
        sp.edit().putString("message", message.trim()).apply();
    }

    int accent() {
        return sp.getInt("accent", 0);
    }

    void setAccent(int index) {
        sp.edit().putInt("accent", index).apply();
    }

    // ---- Globoplay button remap (KeyRemapService) ---------------------------------------------

    static final String KEY_REMAP = "remap";
    static final String DEFAULT_REMAP = "com.unitvnet.tvod";

    /** App opened by the remote's Globoplay button, or "" to leave the button alone. */
    String remapTarget() {
        return sp.getString(KEY_REMAP, DEFAULT_REMAP);
    }

    void setRemapTarget(String pkg) {
        sp.edit().putString(KEY_REMAP, pkg).apply();
    }

    void listen(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sp.registerOnSharedPreferenceChangeListener(listener);
    }

    /** Recovery: name, message, colour, motion, columns, wallpaper, profiles, dock and hidden apps back to the defaults. */
    void resetAll() {
        sp.edit().remove("name").remove("message").remove("accent").remove("light")
                .remove("columns").remove("wallpaper").remove("profiles").remove("dock").remove("hidden").apply();
    }

    // ---- weather --------------------------------------------------------------------------------

    boolean hasWeatherPlace() {
        return sp.contains("wx_lat");
    }

    String weatherPlace() {
        return sp.getString("wx_place", "");
    }

    double weatherLat() {
        return Double.longBitsToDouble(sp.getLong("wx_lat", 0));
    }

    double weatherLon() {
        return Double.longBitsToDouble(sp.getLong("wx_lon", 0));
    }

    void setWeatherPlace(String place, double lat, double lon) {
        sp.edit().putString("wx_place", place)
                .putLong("wx_lat", Double.doubleToRawLongBits(lat))
                .putLong("wx_lon", Double.doubleToRawLongBits(lon))
                .remove("wx_text").remove("wx_at").apply();
    }

    void clearWeather() {
        sp.edit().remove("wx_place").remove("wx_lat").remove("wx_lon").remove("wx_text").remove("wx_at").apply();
    }

    /** Last fetched weather, e.g. "23° NUBLADO", or "" when none. */
    String weatherText() {
        return sp.getString("wx_text", "");
    }

    boolean weatherStale() {
        return System.currentTimeMillis() - sp.getLong("wx_at", 0) > WEATHER_MAX_AGE_MS;
    }

    void setWeatherText(String text) {
        sp.edit().putString("wx_text", text).putLong("wx_at", System.currentTimeMillis()).apply();
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }
}
