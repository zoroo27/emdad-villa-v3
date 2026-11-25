package ir.emdadvila.crm;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.CallLog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallLogHelper {

    public static void showRecentCallsDialog(MainActivity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "مجوز خواندن لیست تماس‌ها فعال نیست.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(32, 32, 32, 26);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#111C36"));
        rootBg.setCornerRadius(44f);
        rootBg.setStroke(3, Color.parseColor("#D4AF37"));
        root.setBackground(rootBg);

        TextView header = new TextView(activity);
        header.setText("📞 تاریخچه تماس‌های اخیر");
        header.setTextColor(Color.parseColor("#F3E5AB"));
        header.setTextSize(16f);
        header.setTypeface(null, Typeface.BOLD);
        root.addView(header);

        TextView sub = new TextView(activity);
        sub.setText("برای ثبت سریع مشتری یا یادآور روی هر تماس بزنید");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(11.5f);
        sub.setPadding(0, 4, 0, 18);
        root.addView(sub);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout listBox = new LinearLayout(activity);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int count = 0;
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    null, null, CallLog.Calls.DATE + " DESC");

            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd  HH:mm", Locale.US);
            while (cursor != null && cursor.moveToNext() && count < 25) {
                final String num = cursor.getString(0) != null ? cursor.getString(0) : "";
                final String name = cursor.getString(1);
                int type = cursor.getInt(2);
                long dateMs = cursor.getLong(3);
                int dur = cursor.getInt(4);

                String typeText = (type == CallLog.Calls.MISSED_TYPE) ? "🔴 بی‌پاسخ"
                        : (type == CallLog.Calls.OUTGOING_TYPE ? "🔵 خروجی" : "🟢 ورودی");

                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(24, 18, 24, 18);
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(Color.parseColor("#18274A"));
                cardBg.setCornerRadius(26f);
                cardBg.setStroke(1, Color.parseColor("#334155"));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cp.setMargins(0, 0, 0, 14);

                TextView tvName = new TextView(activity);
                tvName.setText((name != null && !name.isEmpty()) ? name + "  (" + num + ")" : num);
                tvName.setTextColor(Color.parseColor("#F8FAFC"));
                tvName.setTextSize(13.5f);
                tvName.setTypeface(null, Typeface.BOLD);
                card.addView(tvName);

                TextView tvMeta = new TextView(activity);
                tvMeta.setText(typeText + "   •   " + sdf.format(new Date(dateMs)) + "   •   " + dur + " ثانیه");
                tvMeta.setTextColor(Color.parseColor("#CBD5E1"));
                tvMeta.setTextSize(11.5f);
                tvMeta.setPadding(0, 6, 0, 0);
                card.addView(tvMeta);

                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    showCallActionSheet(activity, num, name);
                });

                listBox.addView(card, cp);
                count++;
            }
        } catch (Exception e) {
            Toast.makeText(activity, "خطا در خواندن تاریخچه تماس", Toast.LENGTH_SHORT).show();
        } finally {
            if (cursor != null) cursor.close();
        }

        if (count == 0) {
            TextView empty = new TextView(activity);
            empty.setText("تماس اخیری یافت نشد.");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setPadding(0, 20, 0, 20);
            listBox.addView(empty);
        }

        scroll.addView(listBox);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 900);
        root.addView(scroll, sp);

        TextView btnClose = new TextView(activity);
        btnClose.setText("بستن");
        btnClose.setTextColor(Color.parseColor("#0B132B"));
        btnClose.setTextSize(13.5f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setPadding(20, 20, 20, 20);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#D4AF37"));
        closeBg.setCornerRadius(28f);
        btnClose.setBackground(closeBg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 16, 0, 0);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose, bp);

        dialog.show();
    }

    private static void showCallActionSheet(MainActivity activity, String phone, String name) {
        String cleanPhone = phone.replaceAll("[^0-9+]", "");
        String[] actions = {"👤 ثبت به عنوان مشتری جدید در CRM", "📅 ثبت یادآور پیگیری تماس", "💬 ارسال پیام در واتساپ", "📞 تماس مجدد"};
        new AlertDialog.Builder(activity)
                .setTitle((name != null && !name.isEmpty() ? name + " — " : "") + cleanPhone)
                .setItems(actions, (d, which) -> {
                    try {
                        String enc = URLEncoder.encode(cleanPhone, "UTF-8");
                        if (which == 0) {
                            activity.loadCrmUrl("https://emdadvila.ir/app?tab=customers&open_cust=1&prefill_phone=" + enc);
                        } else if (which == 1) {
                            activity.loadCrmUrl("https://emdadvila.ir/app?tab=planner&open_rem=1&prefill_phone=" + enc);
                        } else if (which == 2) {
                            String wa = cleanPhone.startsWith("0") ? "98" + cleanPhone.substring(1) : cleanPhone.replace("+", "");
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + wa)));
                        } else if (which == 3) {
                            activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanPhone)));
                        }
                    } catch (Exception ignored) {}
                })
                .show();
    }
}
