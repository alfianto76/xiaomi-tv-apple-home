package id.alfian.tv.adzan;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * A small card in the corner over whatever is playing, with the adzan audio. Asking for transient audio
 * focus makes most players (Netflix, SmartTube, YouTube) pause until the adzan ends. OK or Back stops it.
 */
public class AdzanActivity extends Activity {
    private static final long MAX_MS = 6L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String name = getIntent().getStringExtra(Scheduler.EXTRA_NAME);
        String clock = getIntent().getStringExtra(Scheduler.EXTRA_CLOCK);
        if (name == null) name = "Tes";
        if (clock == null) clock = "";
        setContentView(buildCard(name, clock));

        handler.postDelayed(close, MAX_MS);
        play(name);
    }

    private FrameLayout buildCard(String name, String clock) {
        FrameLayout root = new FrameLayout(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        card.setPadding(pad, dp(22), pad, dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 16, 20, 26));
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        card.setBackground(bg);
        card.setElevation(dp(12));

        TextView label = text("WAKTU SOLAT", 13, Color.rgb(43, 182, 115));
        label.setLetterSpacing(0.2f);
        card.addView(label);

        TextView title = text(name, 40, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        card.addView(title);

        String place = getString(R.string.place);
        card.addView(text(clock.isEmpty() ? place : clock + "  ·  " + place, 20, Color.argb(220, 255, 255, 255)));

        TextView hint = text("Tekan OK atau Back untuk menghentikan", 14, Color.argb(150, 255, 255, 255));
        hint.setPadding(0, dp(12), 0, 0);
        card.addView(hint);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(dp(48), dp(48), dp(56), dp(48));
        root.addView(card, lp);
        return root;
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

    private static final String[] EXTENSIONS = {".mp3", ".ogg", ".oga", ".m4a", ".wav"};

    /** /sdcard/Adzan/subuh.mp3 (or .ogg, .m4a...) for that prayer, else /sdcard/Adzan/adzan.*, else null. */
    static File audioFile(String name) {
        File dir = new File(Environment.getExternalStorageDirectory(), "Adzan");
        File own = firstReadable(dir, name.toLowerCase(Locale.ROOT));
        return own != null ? own : firstReadable(dir, "adzan");
    }

    private static File firstReadable(File dir, String base) {
        for (String ext : EXTENSIONS) {
            File f = new File(dir, base + ext);
            if (f.canRead()) return f;
        }
        return null;
    }

    private void play(String name) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build();
        if (audio != null) audio.requestAudioFocus(focus);

        player = new MediaPlayer();
        player.setAudioAttributes(attrs);
        try {
            File f = audioFile(name);
            if (f != null) {
                player.setDataSource(f.getPath());
            } else {
                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.chime);
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }
            player.setOnCompletionListener(mp -> {
                handler.removeCallbacks(close);
                handler.postDelayed(close, 4000);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                handler.removeCallbacks(close);
                handler.postDelayed(close, 15000);
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            // No sound: keep the card up for a short while so the time is still seen.
            handler.removeCallbacks(close);
            handler.postDelayed(close, 15000);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // Never started.
            }
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        super.onDestroy();
    }
}
