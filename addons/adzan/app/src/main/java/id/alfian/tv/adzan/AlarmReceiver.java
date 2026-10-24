package id.alfian.tv.adzan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** Fires at each adzan (and on the hourly retry): shows the adzan if the TV is on, then sets the next alarm. */
public class AlarmReceiver extends BroadcastReceiver {
    private static final long STALE_MS = 10L * 60L * 1000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long after = System.currentTimeMillis();
        if (Scheduler.ACTION_ADZAN.equals(intent.getAction())) {
            long at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L);
            boolean fresh = Math.abs(System.currentTimeMillis() - at) < STALE_MS;
            PowerManager pm = ctx.getSystemService(PowerManager.class);
            boolean screenOn = pm == null || pm.isInteractive();
            boolean whenOff = ctx.getResources().getBoolean(R.bool.play_when_screen_off);
            if (fresh && (screenOn || whenOff)) {
                Intent show = new Intent(ctx, AdzanActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(Scheduler.EXTRA_NAME, intent.getStringExtra(Scheduler.EXTRA_NAME))
                        .putExtra(Scheduler.EXTRA_CLOCK, intent.getStringExtra(Scheduler.EXTRA_CLOCK));
                try {
                    ctx.startActivity(show);
                } catch (RuntimeException ignored) {
                    // Blocked from the background (overlay permission missing): nothing to show.
                }
            }
            // Never pick the same adzan again.
            after = Math.max(after, at + 60_000L);
        }
        Scheduler.scheduleAsync(ctx, goAsync(), after);
    }
}
