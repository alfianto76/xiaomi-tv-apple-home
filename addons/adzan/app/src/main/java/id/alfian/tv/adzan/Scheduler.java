package id.alfian.tv.adzan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;

/** Fetches a month of times from Aladhan and keeps exactly one alarm set for the next adzan. */
final class Scheduler {
    static final String ACTION_ADZAN = "id.alfian.tv.adzan.ADZAN";
    static final String ACTION_REFRESH = "id.alfian.tv.adzan.REFRESH";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_CLOCK = "clock";
    static final String EXTRA_AT = "at";

    private Scheduler() {}

    /** Runs {@link #schedule} off the main thread, then finishes the receiver's pending result. */
    static void scheduleAsync(Context ctx, BroadcastReceiver.PendingResult pending, long afterMillis) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                schedule(app, afterMillis);
            } catch (RuntimeException ignored) {
                // Never crash the receiver; the hourly refresh alarm tries again.
            } finally {
                if (pending != null) pending.finish();
            }
        }, "adzan-schedule").start();
    }

    /** Blocking: fetch what is missing, then set the alarm. Returns the adzan it set, or null. */
    static TimesStore.Next schedule(Context ctx, long afterMillis) {
        TimesStore store = new TimesStore(ctx);
        LocalDate today = LocalDate.now();
        if (!store.has(today)) fetchMonth(ctx, store, YearMonth.from(today));
        LocalDate tomorrow = today.plusDays(1);
        if (!store.has(tomorrow)) fetchMonth(ctx, store, YearMonth.from(tomorrow));

        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        TimesStore.Next next = store.next(Math.max(afterMillis, System.currentTimeMillis()));
        if (next == null) {
            // No times (offline?): try again in an hour.
            Intent retry = new Intent(ctx, AlarmReceiver.class).setAction(ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 2, retry,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 60L * 60L * 1000L, pi);
            return null;
        }
        Intent fire = new Intent(ctx, AlarmReceiver.class).setAction(ACTION_ADZAN)
                .putExtra(EXTRA_NAME, next.name)
                .putExtra(EXTRA_CLOCK, next.clock)
                .putExtra(EXTRA_AT, next.at);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC_WAKEUP, next.at, 60_000L, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi);
        }
        return next;
    }

    /** Fetches one month of times for the configured place. Failures leave the store as it was. */
    static boolean fetchMonth(Context ctx, TimesStore store, YearMonth month) {
        HttpURLConnection conn = null;
        try {
            String url = "https://api.aladhan.com/v1/calendar/" + month.getYear() + "/" + month.getMonthValue()
                    + "?latitude=" + ctx.getString(R.string.latitude)
                    + "&longitude=" + ctx.getString(R.string.longitude)
                    + "&method=" + ctx.getString(R.string.method);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            if (conn.getResponseCode() != 200) return false;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            JSONArray days = new JSONObject(out.toString("UTF-8")).getJSONArray("data");
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                JSONObject timings = day.getJSONObject("timings");
                // "23-10-2026"
                String[] dmy = day.getJSONObject("date").getJSONObject("gregorian").getString("date").split("-");
                LocalDate date = LocalDate.of(Integer.parseInt(dmy[2]), Integer.parseInt(dmy[1]),
                        Integer.parseInt(dmy[0]));
                String[] five = new String[5];
                for (int k = 0; k < 5; k++) {
                    // "04:01 (WIB)" -> "04:01"
                    String raw = timings.getString(TimesStore.API_KEYS[k]).trim();
                    int space = raw.indexOf(' ');
                    five[k] = space > 0 ? raw.substring(0, space) : raw;
                }
                store.put(date, five);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
