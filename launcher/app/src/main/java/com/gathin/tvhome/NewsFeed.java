package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The news row on Home: RSS headlines from the owner's sources, one card each, mixed round-robin
 * so a small regional feed is not drowned by the big ones. Sources are plain RSS 2.0 / Atom URLs
 * kept in HomePrefs.PREFS_NAME (news_source_&lt;key&gt; on/off, news_custom_url extra feed); every
 * source is on by default, so a fresh install shows the whole mix. The fetched mix is cached to a
 * file for 30 minutes, so Home is instant and a dead network only hides the row instead of
 * blocking the launcher. Fetching happens off the main thread; nothing here touches the UI.
 */
final class NewsFeed {
    interface Listener { void onHeadlines(List<Headline> headlines); }

    /** One headline: what it says, where to read it, which source it came from, when, its lead photo,
     *  the feed's own short summary and its category. Every field is the feed's original wording:
     *  summaries and titles are never rewritten here, only stripped of markup and clipped. */
    static final class Headline {
        final String title;
        final String link;
        final String source;
        final long published;   // epoch ms, 0 when the feed gave no parseable date
        final String image;     // lead photo URL (media:content / enclosure / an <img> in the summary), or ""
        final String summary;   // the feed's description, plain text, or ""
        final String category;  // the feed's first <category>, or ""

        Headline(String title, String link, String source, long published, String image) {
            this(title, link, source, published, image, "", "");
        }

        Headline(String title, String link, String source, long published, String image,
                 String summary, String category) {
            this.title = title;
            this.link = link;
            this.source = source;
            this.published = published;
            this.image = image == null ? "" : image;
            this.summary = summary == null ? "" : summary;
            this.category = category == null ? "" : category;
        }

        /** A short line for the cinematic shelf: the category when the feed gave one, else nothing. */
        String kicker() {
            return category;
        }
    }

    /** One RSS source of the mix; nameRes 0 means a custom feed, whose URL is its own name. */
    static final class Source {
        final String key;       // preference suffix: news_source_<key>
        final int nameRes;
        final String url;

        Source(String key, int nameRes, String url) {
            this.key = key;
            this.nameRes = nameRes;
            this.url = url;
        }

        String name(Context ctx) {
            if (nameRes != 0) return ctx.getString(nameRes);
            String host = Uri.parse(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        }
    }

    /** The built-in mix, all on unless the owner turned one off. */
    static List<Source> catalog() {
        return Arrays.asList(
                new Source("leouve", R.string.news_leouve, "https://leouve.com.br/feed/"),
                new Source("g1", R.string.news_g1, "https://g1.globo.com/rss/g1/"),
                new Source("uol", R.string.news_uol, "https://rss.uol.com.br/feed/noticias.xml"),
                new Source("folha", R.string.news_folha, "https://feeds.folha.uol.com.br/folha/cotidiano/rss091.xml"),
                new Source("sul21", R.string.news_sul21, "https://www.sul21.com.br/feed/"));
    }

    private static final String CACHE_NAME = "news_cache.json";
    private static final int CONNECT_TIMEOUT_MS = 6000, READ_TIMEOUT_MS = 8000;
    private static final int PER_SOURCE = 8, MAX_HEADLINES = 28;     // cards in the row
    /** Cap on the on-disk photo cache: the launcher never grows a news folder without bound. */
    private static final int MAX_IMAGE_FILES = 60;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    private NewsFeed() {}

    /**
     * Whether the news is on. Off by default: a fresh install fetches nothing, loads no photo and
     * draws no news of its own. A TV that already ran an earlier build (which defaulted to on) keeps
     * it on: the first call migrates that state into an explicit pref, so the owner's choice survives
     * the upgrade instead of the row silently disappearing.
     */
    static boolean enabled(Context ctx) {
        SharedPreferences choices = ctx.getSharedPreferences(HomePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        if (choices.contains("news_enabled")) return choices.getBoolean("news_enabled", false);
        boolean used = new File(ctx.getFilesDir(), CACHE_NAME).exists();
        if (used) choices.edit().putBoolean("news_enabled", true).apply();
        return used;
    }

    /** The prefs keys, all inside the launcher's own file. */
    static boolean enabled(SharedPreferences choices) {
        return choices.getBoolean("news_enabled", false);
    }

    /** How long the cached mix is trusted before Home refetches it (news_refresh_min, default 30). */
    static long refreshMs(SharedPreferences choices) {
        return refreshMinutes(choices) * 60 * 1000L;
    }

    /** How long a downloaded photo is reused before it is fetched again (news_image_hours, default 24). */
    static long imageMaxAgeMs(SharedPreferences choices) {
        return imageHours(choices) * 60 * 60 * 1000L;
    }

    /** The refresh interval in minutes, as the settings screen shows and cycles it. */
    static int refreshMinutes(SharedPreferences choices) {
        return choices.getInt("news_refresh_min", 30);
    }

    /** How long a downloaded photo stays usable, in hours, as the settings screen shows it. */
    static int imageHours(SharedPreferences choices) {
        return choices.getInt("news_image_hours", 24);
    }

    static boolean sourceOn(SharedPreferences choices, Source s) {
        return choices.getBoolean("news_source_" + s.key, true);
    }

    static String customUrl(SharedPreferences choices) {
        return choices.getString("news_custom_url", "");
    }

    /** The cached mix, whatever its age; Home shows it at once while a refresh runs. */
    static List<Headline> cached(Context ctx) {
        return readCache(new File(ctx.getFilesDir(), CACHE_NAME));
    }

    /** True when the cache is gone or over the configured age; the row refetches then. */
    static boolean stale(Context ctx) {
        SharedPreferences choices = ctx.getSharedPreferences(HomePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        File file = new File(ctx.getFilesDir(), CACHE_NAME);
        return !file.exists() || System.currentTimeMillis() - file.lastModified() > refreshMs(choices);
    }

    /** Drops the cached mix, so the next Home fetches the owner's sources again (settings changed). */
    static void clearCache(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getFilesDir(), CACHE_NAME).delete();
    }

    /**
     * Fetches the enabled mix off the main thread; the listener runs on it. One flight at a time.
     * A disabled feature returns at once: turning the news off ends every fetch, including one that
     * was already queued when the owner flipped the switch.
     */
    static void fetch(Context ctx, Listener listener) {
        if (!FETCHING.compareAndSet(false, true)) return;
        Context app = ctx.getApplicationContext();
        if (!enabled(app)) {                        // off: no thread, no socket, no callback
            FETCHING.set(false);
            return;
        }
        new Thread(() -> {
            List<Headline> mix = enabled(app) ? fetchMix(app) : new ArrayList<>();
            File cache = new File(app.getFilesDir(), CACHE_NAME);
            if (!mix.isEmpty()) writeCache(cache, mix);
            else mix = readCache(cache);     // offline: yesterday's headlines beat none
            List<Headline> result = mix;
            MAIN.post(() -> {
                FETCHING.set(false);
                listener.onHeadlines(result);
            });
        }, "news").start();
    }

    /** The enabled sources, fetched separately and interleaved one headline each at a time. */
    private static List<Headline> fetchMix(Context ctx) {
        SharedPreferences choices = ctx.getSharedPreferences(HomePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        List<List<Headline>> perSource = new ArrayList<>();
        for (Source s : catalog()) {
            if (!sourceOn(choices, s)) continue;
            List<Headline> got = fetchSource(ctx, s);
            if (!got.isEmpty()) perSource.add(got);
        }
        String custom = customUrl(choices);
        if (!custom.isEmpty()) {
            List<Headline> got = fetchSource(ctx, new Source("custom", 0, custom));
            if (!got.isEmpty()) perSource.add(got);
        }
        List<Headline> mix = new ArrayList<>();
        Set<String> seen = new HashSet<>();         // the same story in two feeds is one card
        for (int i = 0; mix.size() < MAX_HEADLINES; i++) {
            boolean any = false;
            for (List<Headline> source : perSource) {
                if (i >= source.size()) continue;
                any = true;
                if (seen.add(dedupeKey(source.get(i)))) mix.add(source.get(i));
                if (mix.size() == MAX_HEADLINES) break;
            }
            if (!any) break;
        }
        return mix;
    }

    /** What makes two headlines the same story: the link when it has one, else title plus source. */
    private static String dedupeKey(Headline h) {
        if (!h.link.isEmpty()) return h.link;
        return h.source + "|" + h.title;
    }

    /** One feed's headlines, newest first; an unreachable or broken feed simply brings none. */
    private static List<Headline> fetchSource(Context ctx, Source source) {
        List<Headline> out = new ArrayList<>();
        if (!safeUrl(source.url)) return out;        // only http(s): never file:, javascript:, ...
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(source.url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);   // http to https is fine, Java caps the hops
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NewsRow/1.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return out;
            // A redirect can land anywhere: only an http(s) final URL is read.
            if (!safeUrl(conn.getURL().toString())) return out;
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new java.io.StringReader(readFeedBody(conn, charsetOf(conn.getContentType()))));
            String name = source.name(ctx);
            boolean inItem = false;
            String title = null, link = null, date = null, image = null, summary = null, rawHtml = null, category = null;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < PER_SOURCE; e = p.next()) {
                String tag = p.getName();
                if (e == XmlPullParser.START_TAG) {
                    if ("item".equals(tag) || "entry".equals(tag)) {
                        inItem = true;
                        title = null; link = null; date = null; image = null; summary = null;
                        rawHtml = null; category = null;
                    } else if (inItem && "title".equals(tag)) {
                        title = text(p);
                    } else if (inItem && "link".equals(tag)) {
                        // RSS keeps the URL as text, Atom as the href attribute.
                        String href = p.getAttributeValue(null, "href");
                        link = href != null ? href : text(p);
                    } else if (inItem && date == null
                            && ("pubDate".equals(tag) || "published".equals(tag) || "updated".equals(tag))) {
                        date = text(p);
                    } else if (inItem && category == null && "category".equals(tag)) {
                        category = clean(text(p), 60);
                    } else if (inItem) {
                        // The lead photo the cinematic background shows behind the headline.
                        String found = leadImage(tag, p);
                        if (found != null && image == null) image = found;
                        // The feed's own short summary, kept as the feed wrote it (markup stripped);
                        // the raw HTML is only kept to fish the <img> out of it a few lines below.
                        if (summary == null
                                && ("description".equals(tag) || "summary".equals(tag)
                                || "encoded".equals(tag) || "content".equals(tag))) {
                            String raw = text(p);
                            rawHtml = raw;
                            summary = clean(raw, 240);
                        }
                    }
                } else if (e == XmlPullParser.END_TAG && ("item".equals(tag) || "entry".equals(tag))) {
                    inItem = false;
                    if (title != null && !title.isEmpty()) {
                        title = clean(title, 160);
                        if (!title.isEmpty()) {
                            String lead = image == null ? firstImgInHtml(rawHtml) : image;
                            out.add(new Headline(title, safeLink(link), name, parseDate(date),
                                    orEmpty(safeLink(lead)), summary == null ? "" : summary,
                                    category == null ? "" : category));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // A feed that is down, moved or not RSS at all simply brings nothing.
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    /** Only a real web URL is fetched or shown; anything else (file:, javascript:, data:) is dropped. */
    private static boolean safeUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty() || u.length() > 2048) return false;
        try {
            java.net.URI parsed = java.net.URI.create(u);
            String scheme = parsed.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && parsed.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The URL kept for a headline's link or photo, or null when it is not a usable http(s) URL. */
    private static String safeLink(String url) {
        if (url == null) return null;
        String u = url.trim();
        return safeUrl(u) ? u : null;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Reads the feed body and turns it into text, trusting the bytes over the header.
     *
     * The Folha feed declares ISO-8859-1 in Content-Type and then serves UTF-8, so trusting the header
     * turned every accented letter into a box ("Justi\u25C6a"). The bytes are decoded as UTF-8 first; when
     * they are not valid UTF-8, the declared charset is the right one after all.
     */
    private static String readFeedBody(HttpURLConnection conn, String declared) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
        }
        byte[] bytes = buffer.toByteArray();
        java.nio.charset.CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            return utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException notUtf8) {
            return new String(bytes, declared);
        }
    }

    /** The charset a feed declared in its Content-Type, defaulting to UTF-8 when it did not. */
    static String charsetOf(String contentType) {
        if (contentType == null) return "UTF-8";
        for (String part : contentType.split(";")) {
            String t = part.trim();
            int eq = t.indexOf('=');
            if (eq > 0 && "charset".equalsIgnoreCase(t.substring(0, eq).trim())) {
                String cs = t.substring(eq + 1).trim().replace("\"", "");
                if (!cs.isEmpty()) return cs;
            }
        }
        return "UTF-8";
    }

    /** The image a media tag points at, or null when the tag carries none (only the first one counts). */
    private static String leadImage(String tag, XmlPullParser p) {
        if ("media:content".equals(tag) || "media:thumbnail".equals(tag) || "enclosure".equals(tag)) {
            String url = p.getAttributeValue(null, "url");
            String type = p.getAttributeValue(null, "type");
            // An <enclosure> can be audio or video; only take the ones that are pictures.
            if (url != null && (type == null || type.startsWith("image"))) return url;
        }
        return null;
    }

    /** The first <img src> in an HTML summary, for feeds that hide the photo there. */
    private static String firstImgInHtml(String html) {
        if (html == null) return null;
        int at = html.indexOf("<img");
        if (at < 0) return null;
        int src = html.indexOf("src=\"", at);
        if (src < 0) return null;
        int start = src + 5;
        int end = html.indexOf('"', start);
        return end > start ? html.substring(start, end) : null;
    }

    /** The text of the element the parser is on, empty when it holds markup or nothing. */
    private static String text(XmlPullParser p) {
        try {
            int e = p.next();
            if (e != XmlPullParser.TEXT) return "";
            String value = p.getText();
            p.next();                       // stand on the element's END_TAG again
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    /** One plain line: markup stripped, entities decoded, run together, capped at 160. */
    /** One plain line: markup stripped, entities decoded, run together, capped. */
    private static String clean(String raw, int max) {
        if (raw == null) return "";
        String s = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString();
        // fromHtml turns an <img> into U+FFFC (object replacement); several feeds put the lead photo
        // inside the description, so the summary started with a box glyph and "Foto: ...".
        s = s.replace("\uFFFC", " ");
        // Feeds also prefix the summary with a camera emoji. This TV's font has no glyph for it, so it
        // showed as a tofu box before "Foto: ...". Symbols and emoji carry no meaning here.
        s = s.replaceAll("[\u2600-\u27BF\uFE0F]|[\uD83C-\uD83E][\uDC00-\uDFFF]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        // Cut short and mark the cut, so a long title never eats the whole block.
        if (s.length() > max) s = s.substring(0, Math.max(0, max - 1)).trim() + "â€¦";
        return s;
    }

    /** The date formats the five built-in feeds and most blogs actually use. A feed that uses
     *  another one simply shows no date; the headline itself is unaffected. */
    private static final String[] DATE_PATTERNS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",      // RSS, RFC 822
            "EEE, dd MMM yyyy HH:mm:ss zzz",    // RFC 822 with a named zone (GMT, BRST)
            "yyyy-MM-dd'T'HH:mm:ssXXX",         // Atom, ISO 8601 with offset
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",     // ISO 8601 with milliseconds and offset
            "yyyy-MM-dd'T'HH:mm:ss'Z'",         // ISO 8601, UTC spelled out
            "yyyy-MM-dd'T'HH:mm:ss",            // ISO 8601 without a zone (assumed local)
            "yyyy-MM-dd HH:mm:ss",              // plain datetime, very common in pt-BR feeds
            "dd/MM/yyyy HH:mm:ss",              // the pt-BR convention
            "dd/MM/yyyy HH:mm"};

    private static long parseDate(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        raw = raw.trim();
        for (String pattern : DATE_PATTERNS) {
            try {
                Date d = new SimpleDateFormat(pattern, Locale.ENGLISH).parse(raw);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
                // The next pattern may still match.
            }
        }
        return 0;
    }

    private static void writeCache(File file, List<Headline> headlines) {
        try {
            JSONArray array = new JSONArray();
            for (Headline h : headlines) {
                JSONObject o = new JSONObject();
                o.put("t", h.title);
                o.put("l", h.link);
                o.put("s", h.source);
                o.put("w", h.published);
                o.put("i", h.image);
                o.put("d", h.summary);          // v2.5: the layer-2 summary and category
                o.put("g", h.category);
                array.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("items", array);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // A full disk only costs the next refresh its head start.
        }
    }

    private static List<Headline> readCache(File file) {
        List<Headline> out = new ArrayList<>();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            JSONArray array = new JSONObject(new String(buf, 0, got, StandardCharsets.UTF_8))
                    .optJSONArray("items");
            if (array == null) return out;
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) out.add(new Headline(
                        o.optString("t"), o.optString("l"), o.optString("s"), o.optLong("w"),
                        o.optString("i"), o.optString("d"), o.optString("g")));
            }
        } catch (Exception ignored) {
            // No cache yet, or one this build cannot read: fetch instead.
        }
        return out;
    }

    // ---- lead photos (the cinematic backdrop) -------------------------------------------------

    /** Cap on one photo's bytes: a 20 MP attachment is not worth 8 MB of disk on a TV. */
    private static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;

    /**
     * The headline's photo as a local file, downloading it once and keeping it on disk so Home
     * never refetches a picture it already showed. Runs off the main thread; returns null when
     * there is no photo, the URL is not http(s), or the network is down (the backdrop then falls
     * back to the headline's derived colour). The folder is pruned to MAX_IMAGE_FILES entries, so
     * months of browsing never grow the launcher's data without bound.
     */
    static File imageFile(Context ctx, String url) {
        if (url == null || url.isEmpty() || !safeUrl(url)) return null;
        File dir = new File(ctx.getFilesDir(), "news_images");
        // One file per URL, named by a stable hash of it.
        File file = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
        SharedPreferences choices = ctx.getSharedPreferences(HomePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < imageMaxAgeMs(choices)) {
            return file;                            // already bought, reuse it
        }
        HttpURLConnection conn = null;
        try {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NewsRow/1.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            if (!safeUrl(conn.getURL().toString())) return null;
            long length = conn.getContentLengthLong();
            if (length > MAX_IMAGE_BYTES) return null;   // absurdly large: the headline wins alone
            long written = 0;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    written += n;
                    if (written > MAX_IMAGE_BYTES) throw new java.io.IOException("photo too large");
                    out.write(buf, 0, n);
                }
            }
            pruneImages(dir);
            return file;
        } catch (Exception ignored) {
            // A photo that will not come costs the slide its picture, never its headline.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Keeps the newest MAX_IMAGE_FILES photos: the folder is small, bounded and easy to empty. */
    private static void pruneImages(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_IMAGE_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_IMAGE_FILES; i < files.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
