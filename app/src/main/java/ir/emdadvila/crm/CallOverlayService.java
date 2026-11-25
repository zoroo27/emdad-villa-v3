package ir.emdadvila.crm;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;

public class CallOverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if ("CLOSE".equals(intent.getAction())) {
            removeOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String mode = intent.getStringExtra("mode"); // "RINGING" or "POST_CALL"
        String phone = intent.getStringExtra("phone");
        String custName = intent.getStringExtra("cust_name");
        String custBudget = intent.getStringExtra("cust_budget");
        String custRegion = intent.getStringExtra("cust_region");
        String custStage = intent.getStringExtra("cust_stage");
        String callType = intent.getStringExtra("call_type");
        int duration = intent.getIntExtra("duration", 0);

        showOverlayCard(mode, phone, custName, custBudget, custRegion, custStage, callType, duration);
        return START_NOT_STICKY;
    }

    private void showOverlayCard(String mode, String phone, String custName, String budget,
                                 String region, String stage, String callType, int duration) {
        removeOverlay();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 140;

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(28, 12, 28, 12);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 30, 36, 30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F291B"));
        bg.setCornerRadius(36f);
        bg.setStroke(4, Color.parseColor("#10B981"));
        card.setBackground(bg);

        // Header Row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText("POST_CALL".equals(mode)
                ? "📞 پایان تماس (" + (callType != null ? callType : "ورودی") + (duration > 0 ? " • " + duration + " ثانیه" : "") + ")"
                : "🏛️ کالرآیدی هوشمند امداد ویلا");
        badge.setTextColor(Color.parseColor("#F5D08A"));
        badge.setTextSize(13);
        badge.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(badge, badgeLp);

        Button btnClose = new Button(this);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setTextSize(16);
        btnClose.setOnClickListener(v -> {
            removeOverlay();
            stopSelf();
        });
        header.addView(btnClose);
        card.addView(header);

        // Caller Details
        TextView tvName = new TextView(this);
        boolean isKnown = custName != null && !custName.isEmpty() && !custName.contains("ناشناس");
        tvName.setText((isKnown ? "👤 " + custName : "📱 شماره جدید: " + phone));
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(17);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setPadding(0, 8, 0, 6);
        card.addView(tvName);

        TextView tvMeta = new TextView(this);
        if (isKnown) {
            tvMeta.setText("📞 " + phone + " | 💰 بودجه: " + (budget != null && !budget.isEmpty() ? budget + " میلیارد" : "ثبت نشده")
                    + "\n📍 منطقه: " + (region != null ? region : "نامشخص") + " | وضعیت: " + (stage != null ? stage : "فعال"));
        } else {
            tvMeta.setText("این شماره هنوز در باشگاه مشتریان ثبت نشده است؛ در لیست تماس‌های CRM ذخیره شد.");
        }
        tvMeta.setTextColor(Color.parseColor("#BFE3D0"));
        tvMeta.setTextSize(12);
        tvMeta.setPadding(0, 0, 0, 16);
        card.addView(tvMeta);

        // Post-Call Quick Action Buttons
        if ("POST_CALL".equals(mode)) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setWeightSum(3f);

            actions.addView(makeActionBtn("➕ ثبت مشتری", "#107C41", v -> {
                openAppUrl("https://emdadvila.ir/app?tab=customers&open_cust=1&prefill_phone=" + safeEncode(phone));
            }));
            actions.addView(makeActionBtn("💬 واتساپ", "#047857", v -> {
                String clean = phone != null ? phone.replaceAll("[^0-9]", "") : "";
                if (clean.startsWith("0")) clean = "98" + clean.substring(1);
                String msg = "سلام و احترام 🌱\nسپاس از تماس شما با مجموعه امداد ویلا.\nمشاهده فایل‌های ویژه ما: https://emdadvila.ir";
                Intent wa = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + clean + "?text=" + safeEncode(msg)));
                wa.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(wa);
                removeOverlay();
                stopSelf();
            }));
            actions.addView(makeActionBtn("📅 یادآور", "#B45309", v -> {
                openAppUrl("https://emdadvila.ir/app?tab=planner&open_rem=1&prefill_phone=" + safeEncode(phone));
            }));
            card.addView(actions);
        }

        wrapper.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        overlayView = wrapper;
        windowManager.addView(overlayView, params);
    }

    private Button makeActionBtn(String text, String hexColor, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(hexColor));
        d.setCornerRadius(20f);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(6, 0, 6, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private void openAppUrl(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("open_url", url);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        removeOverlay();
        stopSelf();
    }

    private String safeEncode(String s) {
        try { return URLEncoder.encode(s != null ? s : "", "UTF-8"); } catch (Exception e) { return ""; }
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }
}
