package ir.emdadvila.crm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {

    private static final String VERSION_URL = "https://emdadvila.ir/wp-json/emdad-villa/v1/app-version";

    public static void checkForUpdate(Activity activity, boolean showUpToDateToast) {
        new Thread(() -> {
            try {
                PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                long currentCode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        ? pInfo.getLongVersionCode()
                        : pInfo.versionCode;

                URL url = new URL(VERSION_URL);
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
                    int remoteCode = json.optInt("versionCode", 0);
                    String remoteName = json.optString("versionName", "");
                    String apkUrl = json.optString("apkUrl", "").trim();
                    String changelog = json.optString("changelog", "بهبود سرعت و امکانات جدید");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (remoteCode > currentCode && !apkUrl.isEmpty()) {
                            showUpdateDialog(activity, remoteName, changelog, apkUrl);
                        } else if (showUpToDateToast) {
                            Toast.makeText(activity, "✅ اپلیکیشن شما به آخرین نسخه (v" + pInfo.versionName + ") بروز است.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                if (showUpToDateToast) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(activity, "خطا در بررسی نسخه جدید؛ اتصال اینترنت را چک کنید.", Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private static void showUpdateDialog(Activity activity, String versionName, String changelog, String apkUrl) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("🚀 نسخه جدید آماده است (v" + versionName + ")")
                .setMessage("تغییرات نسخه جدید:\n" + changelog + "\n\nآیا مایل به دانلود و نصب خودکار هستید؟")
                .setPositiveButton("📥 دانلود و نصب", (d, w) -> downloadAndInstall(activity, apkUrl))
                .setNegativeButton("بعداً", null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        ProgressDialog pd = new ProgressDialog(activity);
        pd.setTitle("در حال دریافت بروزرسانی...");
        pd.setMessage("لطفاً چند لحظه صبر کنید");
        pd.setIndeterminate(false);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                int totalLen = conn.getContentLength();
                File outFile = new File(activity.getCacheDir(), "emdad-villa-update.apk");
                InputStream in = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(outFile);

                byte[] buf = new byte[8192];
                long downloaded = 0;
                int count;
                while ((count = in.read(buf)) != -1) {
                    downloaded += count;
                    fos.write(buf, 0, count);
                    if (totalLen > 0) {
                        int pct = (int) ((downloaded * 100L) / totalLen);
                        new Handler(Looper.getMainLooper()).post(() -> pd.setProgress(pct));
                    }
                }
                fos.flush();
                fos.close();
                in.close();
                conn.disconnect();

                new Handler(Looper.getMainLooper()).post(() -> {
                    pd.dismiss();
                    installApkFile(activity, outFile);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    pd.dismiss();
                    Toast.makeText(activity, "خطا در دانلود فایل آپدیت.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void installApkFile(Activity activity, File apkFile) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "نصب خودکار ممکن نشد؛ مجوز نصب از منابع ناشناس را فعال کنید.", Toast.LENGTH_LONG).show();
        }
    }
}
