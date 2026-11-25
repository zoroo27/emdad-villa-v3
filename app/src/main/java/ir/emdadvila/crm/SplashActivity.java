package ir.emdadvila.crm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B192C"));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0B192C"));
        layout.setPadding(48, 48, 48, 48);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_ev);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(250, 250);
        lp.bottomMargin = 28;
        layout.addView(logo, lp);

        TextView title = new TextView(this);
        title.setText("امداد ویلا Pro");
        title.setTextColor(Color.parseColor("#E6C27A"));
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("سامانه هوشمند فایلینگ، کالرآیدی و مدیریت تیم املاک • نسخه ۳.۰");
        sub.setTextColor(Color.parseColor("#BFE3D0"));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 14, 0, 0);
        layout.addView(sub);

        AlphaAnimation fade = new AlphaAnimation(0.0f, 1.0f);
        fade.setDuration(700);
        layout.startAnimation(fade);

        setContentView(layout);

        new Handler(Looper.getMainLooper()).postDelayed(this::proceedNext, 850);
    }

    private void proceedNext() {
        SharedPreferences prefs = getSharedPreferences("ev_crm_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("biometric_lock", false)) {
            openMain();
            return;
        }
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            openMain();
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        openMain();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        Toast.makeText(SplashActivity.this, "احراز هویت لغو شد", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("ورود امن به امداد ویلا Pro")
                .setSubtitle("برای محافظت از شماره مالکین، اثر انگشت یا رمز گوشی را وارد کنید")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        if (getIntent() != null && getIntent().hasExtra("open_url")) {
            i.putExtra("open_url", getIntent().getStringExtra("open_url"));
        }
        startActivity(i);
        finish();
    }
}
