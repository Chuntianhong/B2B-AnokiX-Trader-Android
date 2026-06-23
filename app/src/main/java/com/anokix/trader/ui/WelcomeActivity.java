package com.anokix.trader.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;

/**
 * First screen shown after the splash when the user is not logged in.
 *
 * Login / Sign In → {@link LoginActivity}
 * Create Account  → Trader self-registration ({@link RegisterTraderActivity}).
 * Support Centre  → opens the device email app addressed to the support team.
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        View login = findViewById(R.id.loginButton);
        View signIn = findViewById(R.id.signInButton);
        View createAccount = findViewById(R.id.createAccountButton);
        View support = findViewById(R.id.supportButton);

        View.OnClickListener toLogin = v ->
                startActivity(new Intent(this, LoginActivity.class));
        login.setOnClickListener(toLogin);
        signIn.setOnClickListener(toLogin);

        // Create Account opens the public trader registration wizard.
        createAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterTraderActivity.class)));

        support.setOnClickListener(v -> openSupportEmail());
    }

    private void openSupportEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + getString(R.string.support_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_subject));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_LONG).show();
        }
    }
}
