package id.alfian.tv.adzan;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;

/** Today's times, the next adzan, what is set up and what is missing, a test button and a refresh button. */
public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView nextLine;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(64), dp(40), dp(64), dp(32));

        root.addView(text("Adzan", 36, Color.WHITE));
        root.addView(text(getString(R.string.place) + "  ·  Kemenag RI", 18, Color.argb(170, 255, 255, 255)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.HORIZONTAL);
        list.setGravity(Gravity.CENTER);
        list.setPadding(0, dp(28), 0, dp(20));
        root.addView(list);

        nextLine = text("", 22, Color.rgb(43, 182, 115));
        root.addView(nextLine);

        status = text("", 15, Color.argb(170, 255, 255, 255));
        status.setPadding(0, dp(16), 0, dp(20));
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        Button test = button("Tes Adzan");
        test.setOnClickListener(v -> startActivity(new Intent(this, AdzanActivity.class)
                .putExtra(Scheduler.EXTRA_NAME, "Tes")
                .putExtra(Scheduler.EXTRA_CLOCK, "")));
        Button reload = button("Muat ulang jadwal");
        reload.setOnClickListener(v -> {
            new TimesStore(this).clear();
            refresh();
        });
        buttons.addView(test);
        buttons.addView(reload);
        root.addView(buttons);

        setContentView(root);
        test.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        nextLine.setText("Memuat jadwal…");
        new Thread(() -> {
            TimesStore.Next found;
            try {
                found = Scheduler.schedule(getApplicationContext(), System.currentTimeMillis());
            } catch (RuntimeException e) {
                found = null;
            }
            TimesStore.Next next = found;
            runOnUiThread(() -> {
                if (!isFinishing()) render(next);
            });
        }, "adzan-refresh").start();
    }

    private void render(TimesStore.Next next) {
        list.removeAllViews();
        String[] times = new TimesStore(this).get(LocalDate.now());
        for (int i = 0; i < 5; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(22), dp(14), dp(22), dp(14));
            boolean isNext = next != null && times != null && next.name.equals(TimesStore.NAMES[i])
                    && next.clock.equals(times[i]);
            if (isNext) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.argb(60, 43, 182, 115));
                bg.setCornerRadius(dp(18));
                cell.setBackground(bg);
            }
            cell.addView(text(TimesStore.NAMES[i], 16, Color.argb(190, 255, 255, 255)));
            cell.addView(text(times == null ? "--:--" : times[i], 30, Color.WHITE));
            list.addView(cell);
        }
        nextLine.setText(next == null
                ? "Jadwal belum termuat. Cek koneksi internet TV."
                : "Adzan berikutnya: " + next.name + "  " + next.clock);

        StringBuilder s = new StringBuilder();
        boolean overlay = Settings.canDrawOverlays(this);
        s.append(overlay ? "✓ Boleh tampil di atas aplikasi lain"
                : "✗ Belum boleh tampil di atas aplikasi lain (adzan tidak akan muncul)");
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            s.append('\n').append(am.canScheduleExactAlarms() ? "✓ Alarm tepat waktu aktif"
                    : "✗ Alarm tepat waktu belum aktif (adzan bisa telat sampai 1 menit)");
        }
        File f = AdzanActivity.audioFile("adzan");
        s.append('\n').append(f != null ? "✓ Suara adzan: " + f.getPath()
                : "✗ File adzan belum ada (sementara lonceng). Taruh di /sdcard/Adzan/adzan.mp3 atau .ogg");
        status.setText(s);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.argb(40, 255, 255, 255));
        normal.setCornerRadius(dp(24));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.rgb(43, 182, 115));
        focused.setCornerRadius(dp(24));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        b.setBackground(states);
        b.setPadding(dp(28), dp(10), dp(28), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
