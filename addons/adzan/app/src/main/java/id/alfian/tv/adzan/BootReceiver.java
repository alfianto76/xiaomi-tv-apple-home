package id.alfian.tv.adzan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot, an update of this app or a clock change: set the next adzan alarm again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.scheduleAsync(ctx, goAsync(), System.currentTimeMillis());
    }
}
