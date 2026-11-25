package ir.emdadvila.crm;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class CallReceiver extends BroadcastReceiver {
    private static String lastState = TelephonyManager.EXTRA_STATE_IDLE;
    private static String savedNumber = "";
    private static boolean isIncoming = false;
    private static long callStartTime = 0L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String outNum = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (outNum != null && !outNum.trim().isEmpty()) {
                savedNumber = outNum.trim();
                isIncoming = false;
            }
            return;
        }

        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String inNum = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        if (inNum != null && !inNum.trim().isEmpty()) {
            savedNumber = inNum.trim();
        }
        if (state == null || state.equals(lastState)) return;

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            isIncoming = true;
            callStartTime = 0L;
            if (!savedNumber.isEmpty()) {
                queryAndHandleCall(context, savedNumber, "ورودی", 0, true);
            }
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            callStartTime = System.currentTimeMillis();
            if (!TelephonyManager.EXTRA_STATE_RINGING.equals(lastState)) {
                isIncoming = false;
            }
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            int durationSec = (callStartTime > 0) ? (int) ((System.currentTimeMillis() - callStartTime) / 1000L) : 0;
            if (!savedNumber.isEmpty()) {
                String finalType;
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(lastState)) {
                    finalType = "بی‌پاسخ";
                    maybeSendAutoSms(context, savedNumber);
                } else if (isIncoming) {
                    finalType = "ورودی";
                } else {
                    finalType = "خروجی";
                }
                queryAndHandleCall(context, savedNumber, finalType, durationSec, false);
            }
            savedNumber = "";
            callStartTime = 0L;
        }
        lastState = state;
    }

    private void queryAndHandleCall(Context context, String phone, String type, int durationSec, boolean isLookupOnly) {
        SharedPreferences prefs = context.getSharedPreferences("ev_crm_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString("webhook_token", "").trim();
        boolean showOverlay = prefs.getBoolean("caller_id_overlay", true);
        if (token.isEmpty()) return;

        new Thread(() -> {
            String custName = "";
            String custBudget = "";
            String custRegion = "";
            String custStage = "";
            try {
                String urlStr = "https://emdadvila.ir/wp-json/emdad-villa/v1/call-webhook"
                        + "?token=" + URLEncoder.encode(token, "UTF-8")
                        + "&phone=" + URLEncoder.encode(phone, "UTF-8")
                        + "&type=" + URLEncoder.encode(type, "UTF-8")
                        + "&duration=" + durationSec
                        + "&mode=" + (isLookupOnly ? "lookup" : "log");

                URL url = new URL(urlStr);
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
                    if (!json.isNull("customer")) {
                        JSONObject c = json.getJSONObject("customer");
                        custName = c.optString("name", "");
                        custBudget = c.optString("budget", "");
                        custRegion = c.optString("region", "");
                        custStage = c.optString("stage", "");
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            if (showOverlay) {
                Intent ov = new Intent(context, CallOverlayService.class);
                ov.putExtra("mode", isLookupOnly ? "RINGING" : "POST_CALL");
                ov.putExtra("phone", phone);
                ov.putExtra("cust_name", custName);
                ov.putExtra("cust_budget", custBudget);
                ov.putExtra("cust_region", custRegion);
                ov.putExtra("cust_stage", custStage);
                ov.putExtra("call_type", type);
                ov.putExtra("duration", durationSec);
                try { context.startService(ov); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void maybeSendAutoSms(Context context, String phone) {
        SharedPreferences prefs = context.getSharedPreferences("ev_crm_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_sms_missed", false)) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return;

        String msg = prefs.getString("auto_sms_text",
                "سلام و احترام 🌱 متاسفانه موفق به پاسخگویی تماس شما نشدم؛ در اولین فرصت با شما تماس می‌گیرم. مشاهده فایل‌ها: https://emdadvila.ir");
        if (msg == null || msg.trim().isEmpty()) return;

        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(msg), null, null);
        } catch (Exception ignored) {}
    }
}
