package id.alfian.tv.adzan;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Read-only data for the launcher's home screen:
 *   content://id.alfian.tv.adzan.times/today    rows (name, time, next): today's five prayer times
 *   content://id.alfian.tv.adzan.times/info     one row (place, temp, weather, icon, next_name, next_time)
 *   content://id.alfian.tv.adzan.times/matches  rows (home, away, league, when, home_badge, away_badge)
 *   content://id.alfian.tv.adzan.times/badge/x  a club badge PNG
 * Queries also start a background refresh of stale weather and matches.
 */
public class TimesProvider extends ContentProvider {
    private static final String AUTHORITY = "content://id.alfian.tv.adzan.times/badge/";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        String path = uri.getLastPathSegment();
        if (ctx == null) return new MatrixCursor(new String[]{"empty"});
        if ("info".equals(path)) return info(ctx);
        if ("matches".equals(path)) return matches(ctx);
        return today(ctx);
    }

    private Cursor today(Context ctx) {
        MatrixCursor out = new MatrixCursor(new String[]{"name", "time", "next"});
        TimesStore store = new TimesStore(ctx);
        String[] times = store.get(LocalDate.now());
        if (times == null) return out;
        TimesStore.Next next = store.next(System.currentTimeMillis());
        for (int i = 0; i < 5; i++) {
            boolean isNext = next != null && next.name.equals(TimesStore.NAMES[i]) && next.clock.equals(times[i]);
            out.addRow(new Object[]{TimesStore.NAMES[i], times[i], isNext ? 1 : 0});
        }
        return out;
    }

    private Cursor info(Context ctx) {
        InfoFetcher.refreshIfStale(ctx);
        MatrixCursor out = new MatrixCursor(new String[]{"place", "temp", "weather", "icon", "next_name", "next_time"});
        SharedPreferences p = InfoFetcher.prefs(ctx);
        String temp = p.getString("weather_temp", "");
        String[] w = temp.isEmpty() ? new String[]{"", ""}
                : InfoFetcher.describe(p.getInt("weather_code", -1), p.getBoolean("weather_day", true));
        TimesStore.Next next = new TimesStore(ctx).next(System.currentTimeMillis());
        out.addRow(new Object[]{ctx.getString(R.string.place), temp, w[0], w[1],
                next == null ? "" : next.name, next == null ? "" : next.clock});
        return out;
    }

    private Cursor matches(Context ctx) {
        InfoFetcher.refreshIfStale(ctx);
        MatrixCursor out = new MatrixCursor(new String[]{"home", "away", "league", "when", "home_badge", "away_badge"});
        SharedPreferences p = InfoFetcher.prefs(ctx);
        int n = p.getInt("match_count", 0);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM HH.mm", new Locale("in", "ID"));
        for (int i = 0; i < n; i++) {
            String ts = p.getString("m" + i + "_ts", "");
            String when;
            try {
                // TheSportsDB timestamps are UTC: "2026-10-24T08:30:00".
                ZonedDateTime local = LocalDateTime.parse(ts).atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.systemDefault());
                if (local.isBefore(ZonedDateTime.now().minusHours(3))) continue;   // long over
                when = local.format(fmt);
            } catch (RuntimeException e) {
                continue;
            }
            out.addRow(new Object[]{p.getString("m" + i + "_home", ""), p.getString("m" + i + "_away", ""),
                    p.getString("m" + i + "_league", ""), when,
                    badgeUri(p.getString("m" + i + "_home_badge", "")),
                    badgeUri(p.getString("m" + i + "_away_badge", ""))});
        }
        return out;
    }

    private static String badgeUri(String name) {
        return name.isEmpty() ? "" : AUTHORITY + name;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getContext();
        List<String> segs = uri.getPathSegments();
        if (ctx == null || segs.size() != 2 || !"badge".equals(segs.get(0))) throw new FileNotFoundException();
        String name = segs.get(1);
        if (!name.matches("[0-9a-f-]+\\.png")) throw new FileNotFoundException();
        File f = new File(InfoFetcher.badgeDir(ctx), name);
        if (!f.isFile()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
