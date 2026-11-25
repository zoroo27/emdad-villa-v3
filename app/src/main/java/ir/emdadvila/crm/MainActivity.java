package ir.emdadvila.crm;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.Switch;
import android.graphics.drawable.ColorDrawable;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String CRM_URL = "https://emdadvila.ir/app";
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView statusBadge;
    private ValueCallback<Uri[]> filePathCallback;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B132B"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B132B"));

        // 1. Native Luxury Top App Bar
        root.addView(buildNativeTopBar());

        // 2. Smooth Native Progress Bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8);
        root.addView(progressBar, pbLp);

        // 3. SwipeRefresh + WebView
        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setEnabled(false);
        swipeRefresh.setColorSchemeColors(Color.parseColor("#107C41"), Color.parseColor("#0B192C"));
        webView = new WebView(this);
        swipeRefresh.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefresh.setOnRefreshListener(() -> {
            updateNetworkBadge();
            updateCacheMode();
            webView.reload();
        });
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(swipeRefresh, webLp);

        // 4. Native Bottom Navigation Dock
        root.addView(buildNativeBottomBar());

        setContentView(root);

        requestNeededPermissions();
        setupWebView();
        scheduleAlertWorker();
        AppUpdater.checkForUpdate(this, false);
        updateNetworkBadge();

        String startUrl = getIntent().getStringExtra("open_url");
        loadCrmUrl((startUrl != null && !startUrl.isEmpty()) ? startUrl : CRM_URL);
    }

    private LinearLayout buildNativeTopBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.parseColor("#0B132B"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(18, 10, 18, 10);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_3d_brand);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(68, 68);
        logoLp.setMarginEnd(12);
        bar.addView(logo, logoLp);

        TextView brand = new TextView(this);
        brand.setText("امداد ویلا Pro");
        brand.setTextColor(Color.parseColor("#F3E5AB"));
        brand.setTextSize(14f);
        brand.setSingleLine(true);
        brand.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(brand, titleLp);

        statusBadge = new TextView(this);
        statusBadge.setText("● آنلاین");
        statusBadge.setTextColor(Color.parseColor("#34D399"));
        statusBadge.setTextSize(10f);
        statusBadge.setSingleLine(true);
        statusBadge.setPadding(10, 0, 10, 0);
        bar.addView(statusBadge);

        bar.addView(makeTop3dBtn(R.drawable.ic_3d_phone, "تماس‌ها", v -> CallLogHelper.showRecentCallsDialog(this)));
        bar.addView(makeTop3dBtn(R.drawable.ic_3d_settings, null, v -> showAdvancedSettingsDialog()));

        wrapper.addView(bar);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#44D4AF37"));
        wrapper.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));

        return wrapper;
    }

    private LinearLayout makeTop3dBtn(int iconRes, String label, View.OnClickListener listener) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(label != null ? 18 : 14, 10, label != null ? 18 : 14, 10);
        btn.setElevation(8f);

        GradientDrawable pill = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#21345C"), Color.parseColor("#111D38")});
        pill.setCornerRadius(26f);
        pill.setStroke(2, Color.parseColor("#D4AF37"));
        btn.setBackground(pill);

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(44, 44);
        btn.addView(iv, ivLp);

        if (label != null) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextColor(Color.parseColor("#F3E5AB"));
            tv.setTextSize(11.5f);
            tv.setSingleLine(true);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setPadding(10, 0, 0, 0);
            btn.addView(tv);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(10);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            listener.onClick(v);
        });
        return btn;
    }

    private LinearLayout buildNativeBottomBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.parseColor("#0B132B"));

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#44D4AF37"));
        wrapper.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(6, 8, 6, 10);
        nav.setWeightSum(5f);

        nav.addView(make3dNavTab(R.drawable.ic_3d_home, "میز کار", false, v -> loadCrmUrl(CRM_URL + "?tab=home")));
        nav.addView(make3dNavTab(R.drawable.ic_3d_files, "فایلینگ", false, v -> loadCrmUrl(CRM_URL + "?tab=files")));
        nav.addView(make3dNavTab(R.drawable.ic_3d_add, "ثبت ملک", true, v -> loadCrmUrl(CRM_URL + "?open_new=1")));
        nav.addView(make3dNavTab(R.drawable.ic_3d_customers, "مشتریان", false, v -> loadCrmUrl(CRM_URL + "?tab=customers")));
        nav.addView(make3dNavTab(R.drawable.ic_3d_tools, "ابزارها", false, v -> loadCrmUrl(CRM_URL + "?tab=tools")));
        wrapper.addView(nav);
        return wrapper;
    }

    private LinearLayout make3dNavTab(int iconRes, String label, boolean isCenterFab, View.OnClickListener listener) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(4, 4, 4, 4);

        LinearLayout iconHolder = new LinearLayout(this);
        iconHolder.setGravity(Gravity.CENTER);
        int boxSize = isCenterFab ? 88 : 68;
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(boxSize, boxSize);

        if (isCenterFab) {
            GradientDrawable fabBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.parseColor("#FFF0B3"), Color.parseColor("#D4AF37"), Color.parseColor("#9A7214")});
            fabBg.setCornerRadius(44f);
            fabBg.setStroke(2, Color.parseColor("#FFF8DC"));
            iconHolder.setBackground(fabBg);
            iconHolder.setElevation(14f);
        } else {
            GradientDrawable tileBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.parseColor("#1D2E54"), Color.parseColor("#101B34")});
            tileBg.setCornerRadius(20f);
            tileBg.setStroke(1, Color.parseColor("#38D4AF37"));
            iconHolder.setBackground(tileBg);
            iconHolder.setElevation(6f);
        }

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        int imgSize = isCenterFab ? 60 : 46;
        iconHolder.addView(iv, new LinearLayout.LayoutParams(imgSize, imgSize));
        tab.addView(iconHolder, holderLp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(isCenterFab ? Color.parseColor("#F3E5AB") : Color.parseColor("#E2E8F0"));
        tv.setTextSize(10.5f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 6, 0, 0);
        tab.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tab.setLayoutParams(lp);
        tab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            listener.onClick(v);
        });
        return tab;
    }

    public void loadCrmUrl(String url) {
        if (webView != null) {
            updateCacheMode();
            webView.loadUrl(url);
        }
    }

    private void updateNetworkBadge() {
        if (statusBadge == null) return;
        if (isOnline()) {
            statusBadge.setText("● آنلاین");
            statusBadge.setTextColor(Color.parseColor("#34D399"));
        } else {
            statusBadge.setText("● آفلاین");
            statusBadge.setTextColor(Color.parseColor("#FB7185"));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = intent.getStringExtra("open_url");
        if (url != null && !url.isEmpty()) loadCrmUrl(url);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    private void scheduleAlertWorker() {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(AlertCheckWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("ev_crm_alerts_work", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        String[] perms = new String[]{
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 301);
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }

    private void updateCacheMode() {
        if (webView == null) return;
        webView.getSettings().setCacheMode(isOnline() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
    }

    private void setupWebView() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setGeolocationEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        updateCacheMode();

        // Native File Download Support (Catalogs, CSV exports, PDFs)
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimeType);
                    req.addRequestHeader("User-Agent", userAgent);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(this, "📥 در حال دانلود فایل: " + fileName, Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "خطا در شروع دانلود فایل", Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                updateNetworkBadge();
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                updateNetworkBadge();
                if (request.isForMainFrame() && !isOnline()) {
                    String offlineHtml = "<html dir='rtl'><body style='font-family:sans-serif;background:#0B192C;color:#fff;text-align:center;padding:60px 24px;'>"
                            + "<h2 style='color:#E6C27A;'>📡 اتصال اینترنت قطع است</h2>"
                            + "<p style='color:#BFE3D0;font-size:14px;'>می‌توانید از دکمه «📞 تماس‌ها» در نوار بالا تاریخچه تماس‌های گوشی را ببینید یا پس از وصل اینترنت دوباره تلاش کنید.</p>"
                            + "<a href='" + CRM_URL + "' style='display:inline-block;margin-top:20px;padding:12px 28px;background:#107C41;color:#fff;border-radius:14px;text-decoration:none;font-weight:bold;'>🔄 تلاش مجدد</a>"
                            + "</body></html>";
                    view.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:")
                        || url.contains("wa.me") || url.contains("eitaa.com") || url.contains("ble.ir")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar == null) return;
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                fileChooserLauncher.launch(Intent.createChooser(intent, "انتخاب فایل یا تصویر"));
                return true;
            }
        });
    }

    private void showAdvancedSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences("ev_crm_prefs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(36, 36, 36, 32);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111C36"));
        bg.setCornerRadius(44f);
        bg.setStroke(3, Color.parseColor("#D4AF37"));
        root.setBackground(bg);

        // Header
        TextView title = new TextView(this);
        title.setText("⚙️ تنظیمات امداد ویلا Pro");
        title.setTextColor(Color.parseColor("#F3E5AB"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 6);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("نسخه ۳.۳ — آیکون‌های سه‌بعدی و اتصال هوشمند");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(11.5f);
        sub.setPadding(0, 0, 0, 22);
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#0B132B"));
        inputBg.setCornerRadius(24f);
        inputBg.setStroke(2, Color.parseColor("#475569"));

        TextView lblTok = new TextView(this);
        lblTok.setText("🔑 توکن اختصاصی مشاور:");
        lblTok.setTextColor(Color.parseColor("#E2E8F0"));
        lblTok.setTextSize(12.5f);
        lblTok.setPadding(0, 0, 0, 8);
        box.addView(lblTok);

        EditText edtToken = new EditText(this);
        edtToken.setHint("adv1_xxxxxxxxxxxx");
        edtToken.setHintTextColor(Color.parseColor("#64748B"));
        edtToken.setTextColor(Color.parseColor("#F8FAFC"));
        edtToken.setTextSize(13f);
        edtToken.setSingleLine(true);
        edtToken.setPadding(24, 20, 24, 20);
        edtToken.setBackground(inputBg);
        edtToken.setText(prefs.getString("webhook_token", ""));
        box.addView(edtToken);

        Switch chkOverlay = new Switch(this);
        chkOverlay.setText("📞 پنجره شناور کالرآیدی و پایان تماس");
        chkOverlay.setTextColor(Color.parseColor("#F8FAFC"));
        chkOverlay.setTextSize(13f);
        chkOverlay.setPadding(0, 24, 0, 14);
        chkOverlay.setChecked(prefs.getBoolean("caller_id_overlay", true));
        box.addView(chkOverlay);

        Switch chkAutoSms = new Switch(this);
        chkAutoSms.setText("💬 پیامک خودکار در تماس بی‌پاسخ");
        chkAutoSms.setTextColor(Color.parseColor("#F8FAFC"));
        chkAutoSms.setTextSize(13f);
        chkAutoSms.setPadding(0, 10, 0, 14);
        chkAutoSms.setChecked(prefs.getBoolean("auto_sms_missed", false));
        box.addView(chkAutoSms);

        GradientDrawable smsBg = new GradientDrawable();
        smsBg.setColor(Color.parseColor("#0B132B"));
        smsBg.setCornerRadius(24f);
        smsBg.setStroke(2, Color.parseColor("#475569"));

        EditText edtSms = new EditText(this);
        edtSms.setHint("متن پیامک خودکار...");
        edtSms.setHintTextColor(Color.parseColor("#64748B"));
        edtSms.setTextColor(Color.parseColor("#F8FAFC"));
        edtSms.setTextSize(12.5f);
        edtSms.setPadding(24, 18, 24, 18);
        edtSms.setBackground(smsBg);
        edtSms.setText(prefs.getString("auto_sms_text",
                "سلام و احترام 🌱 متاسفانه موفق به پاسخگویی تماس شما نشدم؛ در اولین فرصت تماس می‌گیرم. https://emdadvila.ir"));
        box.addView(edtSms);

        Switch chkBio = new Switch(this);
        chkBio.setText("🔒 قفل اثر انگشت / رمز ورود");
        chkBio.setTextColor(Color.parseColor("#F8FAFC"));
        chkBio.setTextSize(13f);
        chkBio.setPadding(0, 20, 0, 12);
        chkBio.setChecked(prefs.getBoolean("biometric_lock", false));
        box.addView(chkBio);

        Switch chkPush = new Switch(this);
        chkPush.setText("🔔 اعلان هوشمند یادآورها و مشتریان");
        chkPush.setTextColor(Color.parseColor("#F8FAFC"));
        chkPush.setTextSize(13f);
        chkPush.setPadding(0, 10, 0, 18);
        chkPush.setChecked(prefs.getBoolean("push_alerts", true));
        box.addView(chkPush);

        scroll.addView(box);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, sp);

        // Action Buttons Row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 18, 0, 0);

        TextView btnSave = new TextView(this);
        btnSave.setText("💾 ذخیره تنظیمات");
        btnSave.setTextColor(Color.parseColor("#0B132B"));
        btnSave.setTextSize(13.5f);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setGravity(android.view.Gravity.CENTER);
        btnSave.setPadding(20, 22, 20, 22);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(Color.parseColor("#D4AF37"));
        saveBg.setCornerRadius(28f);
        btnSave.setBackground(saveBg);

        TextView btnUpdate = new TextView(this);
        btnUpdate.setText("🔄 آپدیت");
        btnUpdate.setTextColor(Color.parseColor("#F3E5AB"));
        btnUpdate.setTextSize(12.5f);
        btnUpdate.setGravity(android.view.Gravity.CENTER);
        btnUpdate.setPadding(18, 22, 18, 22);
        GradientDrawable updBg = new GradientDrawable();
        updBg.setColor(Color.parseColor("#1C2A4A"));
        updBg.setCornerRadius(28f);
        updBg.setStroke(2, Color.parseColor("#D4AF37"));
        btnUpdate.setBackground(updBg);

        LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        lpSave.setMargins(10, 0, 0, 0);
        LinearLayout.LayoutParams lpUpd = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f);
        btnRow.addView(btnSave, lpSave);
        btnRow.addView(btnUpdate, lpUpd);
        root.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString("webhook_token", edtToken.getText().toString().trim())
                    .putBoolean("caller_id_overlay", chkOverlay.isChecked())
                    .putBoolean("auto_sms_missed", chkAutoSms.isChecked())
                    .putString("auto_sms_text", edtSms.getText().toString().trim())
                    .putBoolean("biometric_lock", chkBio.isChecked())
                    .putBoolean("push_alerts", chkPush.isChecked())
                    .apply();
            dialog.dismiss();
            if (chkOverlay.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "لطفاً مجوز نمایش روی سایر برنامه‌ها را فعال کنید", Toast.LENGTH_LONG).show();
                Intent ovPerm = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(ovPerm);
            } else {
                Toast.makeText(this, "✅ تمام تنظیمات ذخیره شد!", Toast.LENGTH_SHORT).show();
            }
        });
        btnUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            AppUpdater.checkForUpdate(this, true);
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
