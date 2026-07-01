package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;

import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.session.SessionManager;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);
        initView();
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
        System.exit(0);
    }

    private void initView() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        new Handler().postDelayed(() -> {
            // Already signed in → straight to the app; otherwise show the welcome screen.
            if (false && SessionManager.get(this).isLoggedIn()) {
                goToMain();
            } else {
                goToWelcome();
            }
        }, 2000);
    }
}