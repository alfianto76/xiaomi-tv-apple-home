package id.alfian.tv.adzan;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Weather (Open-Meteo, no key) and each configured league's next match (TheSportsDB, free key "123"), with the
 * two club badges, for the launcher's info panel. Kept in SharedPreferences "info" and files under cache/badges.
 */
final class InfoFetcher {
    static final long WEATHER_EVERY_MS = 30L * 60L * 1000L;
    static final long MATCHES_EVERY_MS = 3L * 60L * 60L * 1000L;
    private static volatile boolean running;

    private InfoFetcher() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("info", Context.MODE_PRIVATE);
    }

    static File badgeDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "badges");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** Starts a background refresh of whatever is stale; does nothing if one is already running. */
    static void refreshIfStale(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        boolean weather = now - p.getLong("weather_at", 0) > WEATHER_EVERY_MS;
        boolean matches = now - p.getLong("matches_at_team", 0) > MATCHES_EVERY_MS;
        if (!weather && !matches) return;
        if (running) return;
        running = true;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                if (weather) fetchWeather(app);
                if (matches) fetchMatches(app);
            } catch (RuntimeException ignored) {
                // Try again on the next query.
            } finally {
                running = false;
            }
        }, "info-refresh").start();
    }

    private static void fetchWeather(Context ctx) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + ctx.getString(R.string.latitude)
                    + "&longitude=" + ctx.getString(R.string.longitude)
                    + "&current=temperature_2m,weather_code,is_day&timezone=auto";
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            prefs(ctx).edit()
                    .putString("weather_temp", Math.round(cur.getDouble("temperature_2m")) + "°C")
                    .putInt("weather_code", cur.getInt("weather_code"))
                    .putBoolean("weather_day", cur.optInt("is_day", 1) == 1)
                    .putLong("weather_at", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
            // Keep the last reading.
        }
    }

    private static void fetchMatches(Context ctx) {
        String[] teams = ctx.getResources().getStringArray(R.array.team_ids);
        SharedPreferences.Editor e = prefs(ctx).edit();
        int n = 0;
        for (String team : teams) {
            try {
                JSONObject root = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id="
                        + team));
                JSONArray events = root.optJSONArray("events");
                if (events == null || events.length() == 0) continue;
                JSONObject ev = events.getJSONObject(0);
                String home = ev.optString("strHomeTeam", "");
                String away = ev.optString("strAwayTeam", "");
                String ts = ev.optString("strTimestamp", "");
                if (home.isEmpty() || away.isEmpty() || ts.isEmpty()) continue;
                e.putString("m" + n + "_home", home)
                        .putString("m" + n + "_away", away)
                        .putString("m" + n + "_league", leagueName(ev.optString("strLeague", "")))
                        .putString("m" + n + "_ts", ts)
                        .putString("m" + n + "_home_badge", saveBadge(ctx, ev.optString("strHomeTeamBadge", "")))
                        .putString("m" + n + "_away_badge", saveBadge(ctx, ev.optString("strAwayTeamBadge", "")));
                n++;
            } catch (Exception ignored) {
                // Skip this team this time.
            }
        }
        e.putInt("match_count", n).putLong("matches_at_team", System.currentTimeMillis());
        e.apply();
    }

    private static String leagueName(String league) {
        if ("Indonesian Super League".equals(league)) return "BRI Super League";
        if ("English Premier League".equals(league)) return "Premier League";
        return league;
    }

    /** Downloads a badge (the "/small" variant) into cache/badges once; returns its file name or "". */
    private static String saveBadge(Context ctx, String url) {
        if (url == null || !url.startsWith("https://")) return "";
        String name = Integer.toHexString(url.hashCode()) + ".png";
        File f = new File(badgeDir(ctx), name);
        if (f.length() > 0) return name;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url + "/small").openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            if (conn.getResponseCode() != 200) return "";
            File tmp = new File(f.getPath() + ".part");
            try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            return tmp.renameTo(f) ? name : "";
        } catch (Exception e) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** Open-Meteo WMO code to Indonesian words and an emoji. */
    static String[] describe(int code, boolean day) {
        if (code == 0) return new String[]{"cerah", day ? "☀️" : "🌙"};
        if (code == 1) return new String[]{"cerah berawan", day ? "🌤️" : "🌙"};
        if (code == 2) return new String[]{"berawan sebagian", "⛅"};
        if (code == 3) return new String[]{"berawan", "☁️"};
        if (code == 45 || code == 48) return new String[]{"berkabut", "🌫️"};
        if (code >= 51 && code <= 57) return new String[]{"gerimis", "🌦️"};
        if (code >= 61 && code <= 67) return new String[]{"hujan", "🌧️"};
        if (code >= 71 && code <= 77) return new String[]{"salju", "❄️"};
        if (code >= 80 && code <= 82) return new String[]{"hujan lokal", "🌦️"};
        if (code >= 95) return new String[]{"badai petir", "⛈️"};
        return new String[]{"", "🌡️"};
    }
}
