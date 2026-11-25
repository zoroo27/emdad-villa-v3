package ir.emdadvila.crm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class AlertCheckWorker extends Worker {

    private static final String CHANNEL_ID = "ev_crm_alerts";

    public AlertCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("ev_crm_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("push_alerts", true)) return Result.success();
        String token = prefs.getString("webhook_token", "").trim();
        if (token.isEmpty()) return Result.success();

        try {
            URL url = new URL("https://emdadvila.ir/wp-json/emdad-villa/v1/alerts-check?token=" + URLEncoder.encode(token, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                int pending = json.optInt("pending_reminders", 0);
                String nextTitle = json.optString("next_reminder", "");
                int hot = json.optInt("hot_customers", 0);

                int lastNotified = prefs.getInt("last_pending_count", -1);
                if (pending > 0 && pending != lastNotified) {
                    prefs.edit().putInt("last_pending_count", pending).apply();
                    showNotification(ctx, "📅 یادآورهای امداد ویلا CRM",
                            "شما " + pending + " پیگیری معلق و " + hot + " مشتری داغ دارید."
                                    + (!nextTitle.isEmpty() ? " اولویت بعدی: " + nextTitle : ""));
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return Result.success();
    }

    private void showNotification(Context ctx, String title, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "اعلان‌های امداد ویلا CRM", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent openIntent = new Intent(ctx, MainActivity.class);
        openIntent.putExtra("open_url", "https://emdadvila.ir/app?tab=planner");
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        nm.notify(2026, b.build());
    }
}
