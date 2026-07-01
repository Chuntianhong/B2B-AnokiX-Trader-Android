package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.ui.views.RobotoBoldTextView;
import com.anokix.traderapp.ui.views.RobotoEditText;
import com.anokix.traderapp.ui.views.RobotoTextView;

/**
 * Dedicated "Reset your password" screen. Pre-fills the email typed on the login
 * screen, POSTs it to api/common/forgot-password (portal_type "trader"), then swaps
 * to a "Check your email" confirmation with Resend / Back-to-login actions.
 *
 * The success copy is deliberately neutral ("if that email is registered…") so the
 * screen can't be used to discover which emails have accounts.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    /** Optional intent extra: email to pre-fill from the login screen. */
    public static final String EXTRA_EMAIL = "email";

    private View formState, sentState;
    private RobotoEditText emailInput;
    private RobotoBoldTextView sendButton;
    private RobotoTextView sentMessage;

    private boolean submitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        formState = findViewById(R.id.formState);
        sentState = findViewById(R.id.sentState);
        emailInput = findViewById(R.id.emailInput);
        sendButton = findViewById(R.id.sendButton);
        sentMessage = findViewById(R.id.sentMessage);

        String prefill = getIntent().getStringExtra(EXTRA_EMAIL);
        if (prefill != null && !prefill.isEmpty()) {
            emailInput.setText(prefill);
            emailInput.setSelection(prefill.length());
        }

        sendButton.setOnClickListener(v -> submit(currentEmail()));
        findViewById(R.id.backToLogin).setOnClickListener(v -> finish());
        findViewById(R.id.backToLoginPrimary).setOnClickListener(v -> finish());
        findViewById(R.id.resendButton).setOnClickListener(v -> submit(currentEmail()));
    }

    private String currentEmail() {
        return emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
    }

    private void submit(String email) {
        if (submitting) return;
        if (email.isEmpty()) {
            Toast.makeText(this, R.string.reset_email_empty, Toast.LENGTH_SHORT).show();
            emailInput.requestFocus();
            return;
        }

        submitting = true;
        boolean firstSend = sentState.getVisibility() != View.VISIBLE;
        if (firstSend) {
            sendButton.setEnabled(false);
            sendButton.setText(R.string.sending_ellipsis);
        }

        ApiClient.get(this).forgotPassword(email, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                submitting = false;
                sendButton.setEnabled(true);
                sendButton.setText(R.string.send_reset_link);
                showSentState(email);
                if (!firstSend) {
                    Toast.makeText(ForgotPasswordActivity.this, R.string.resend_link,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                submitting = false;
                sendButton.setEnabled(true);
                sendButton.setText(R.string.send_reset_link);
                Toast.makeText(ForgotPasswordActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSentState(String email) {
        sentMessage.setText(getString(R.string.reset_sent_message, maskEmail(email)));
        formState.setVisibility(View.GONE);
        sentState.setVisibility(View.VISIBLE);
    }

    /** "sipho@business.co.za" → "s•••••@business.co.za" (keeps the first char + domain). */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) return local + domain;
        StringBuilder masked = new StringBuilder().append(local.charAt(0));
        for (int i = 1; i < local.length(); i++) masked.append('•');
        return masked + domain;
    }
}
