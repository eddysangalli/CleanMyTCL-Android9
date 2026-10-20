package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Open-Meteo (free, no API key). Blocking calls: run them off the main thread. The home screen calls
 * {@link #current} at most once an hour while it is on screen, and the screensaver only reads the cache.
 */
final class Weather {

    static final class Place {
        final String name;
        final double lat, lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private Weather() {
    }

    /** First match for a city name, or null. Place names come back in the TV's language. */
    static Place find(String query) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language="
                + Locale.getDefault().getLanguage() + "&name=" + URLEncoder.encode(query.trim(), "UTF-8");
        JSONArray results = new JSONObject(get(url)).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject r = results.getJSONObject(0);
        String name = r.getString("name");
        String region = r.optString("admin1", "");
        return new Place(region.isEmpty() || region.equals(name) ? name : name + ", " + region,
                r.getDouble("latitude"), r.getDouble("longitude"));
    }

    /** Current temperature and condition, e.g. "23° NUBLADO", in the TV's language. */
    static String current(Context ctx, double lat, double lon) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
        JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
        long temp = Math.round(cur.getDouble("temperature_2m"));
        int desc = describe(cur.getInt("weather_code"));
        return desc == 0 ? temp + "°" : temp + "° " + ctx.getString(desc);
    }

    /** WMO weather code to a short description (string resource), or 0 when there is none. */
    static int describe(int code) {
        if (code == 0) return R.string.wx_clear;
        if (code <= 2) return R.string.wx_few_clouds;
        if (code == 3) return R.string.wx_overcast;
        if (code == 45 || code == 48) return R.string.wx_fog;
        if (code >= 51 && code <= 57) return R.string.wx_drizzle;
        if (code >= 61 && code <= 67) return R.string.wx_rain;
        if (code >= 71 && code <= 77) return R.string.wx_snow;
        if (code >= 80 && code <= 82) return R.string.wx_showers;
        if (code >= 95) return R.string.wx_thunderstorm;
        return 0;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
