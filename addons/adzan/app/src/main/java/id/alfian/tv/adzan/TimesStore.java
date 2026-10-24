package id.alfian.tv.adzan;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Prayer times per day, kept in SharedPreferences as "HH:mm,HH:mm,HH:mm,HH:mm,HH:mm" (Subuh..Isya). */
final class TimesStore {
    static final String[] NAMES = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    static final String[] API_KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    /** One upcoming adzan. */
    static final class Next {
        final String name;
        final String clock;
        final long at;

        Next(String name, String clock, long at) {
            this.name = name;
            this.clock = clock;
            this.at = at;
        }
    }

    private final SharedPreferences prefs;

    TimesStore(Context ctx) {
        prefs = ctx.getSharedPreferences("adzan_times", Context.MODE_PRIVATE);
    }

    void put(LocalDate day, String[] five) {
        prefs.edit().putString("d:" + day, String.join(",", five)).apply();
    }

    /** The five "HH:mm" times of that day, or null when that day was never fetched. */
    String[] get(LocalDate day) {
        String s = prefs.getString("d:" + day, null);
        if (s == null) return null;
        String[] parts = s.split(",");
        return parts.length == 5 ? parts : null;
    }

    boolean has(LocalDate day) {
        return get(day) != null;
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    /** The first adzan strictly after {@code afterMillis}, looking at today and the next two days. */
    Next next(long afterMillis) {
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        for (int d = 0; d < 3; d++) {
            LocalDate day = today.plusDays(d);
            String[] times = get(day);
            if (times == null) continue;
            for (int i = 0; i < 5; i++) {
                try {
                    LocalTime t = LocalTime.parse(times[i]);
                    long at = day.atTime(t).atZone(zone).toInstant().toEpochMilli();
                    if (at > afterMillis) return new Next(NAMES[i], times[i], at);
                } catch (RuntimeException ignored) {
                    // A malformed entry: skip it rather than stop scheduling.
                }
            }
        }
        return null;
    }
}
