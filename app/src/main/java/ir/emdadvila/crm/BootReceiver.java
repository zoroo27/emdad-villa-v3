package ir.emdadvila.crm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(AlertCheckWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "ev_crm_alerts_work", ExistingPeriodicWorkPolicy.KEEP, req);
        }
    }
}
