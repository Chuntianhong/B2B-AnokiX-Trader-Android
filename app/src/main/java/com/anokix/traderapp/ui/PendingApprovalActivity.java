package com.anokix.traderapp.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.dto.LoginData;
import com.anokix.traderapp.ui.views.RobotoBoldTextView;
import com.anokix.traderapp.ui.views.RobotoTextView;

/**
 * "Application Under Review" screen shown when login returns a still-pending
 * account (status=true, pending_approval=true, no token). Mirrors the web
 * portal layout in the dark auth chrome: a details card, the wallet state, a
 * "what happens next" list, and a Back to Login action.
 */
public class PendingApprovalActivity extends AppCompatActivity {

    private static final String EXTRA_FIRST_NAME = "first_name";
    private static final String EXTRA_STATUS = "status";
    private static final String EXTRA_BUSINESS = "business";
    private static final String EXTRA_REFERENCE = "reference";
    private static final String EXTRA_SUBMITTED = "submitted";
    private static final String EXTRA_WALLET_ACTIVATED = "wallet_activated";
    private static final String EXTRA_WALLET_MESSAGE = "wallet_message";

    private static final int[] NEXT_STEPS = {
            R.string.pending_next_1, R.string.pending_next_2, R.string.pending_next_3};

    /** Builds the launch intent, flattening the pending payload into extras. */
    public static Intent newIntent(Context context, LoginData data) {
        Intent i = new Intent(context, PendingApprovalActivity.class);
        if (data.user != null) i.putExtra(EXTRA_FIRST_NAME, data.user.first_name);
        i.putExtra(EXTRA_STATUS, data.registration_status);
        i.putExtra(EXTRA_BUSINESS, data.business_name);
        i.putExtra(EXTRA_REFERENCE, data.reference);
        i.putExtra(EXTRA_SUBMITTED, data.submitted_at);

        boolean activated = data.wallet != null && data.wallet.isActivated();
        i.putExtra(EXTRA_WALLET_ACTIVATED, activated);
        if (data.wallet != null && !TextUtils.isEmpty(data.wallet.last_error)) {
            i.putExtra(EXTRA_WALLET_MESSAGE, data.wallet.last_error);
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approval);

        bindHeader();
        bindDetails();
        bindWallet();
        buildNextSteps();

        findViewById(R.id.pendingBackLink).setOnClickListener(v -> backToLogin());
        findViewById(R.id.btnBackToLogin).setOnClickListener(v -> backToLogin());
        findViewById(R.id.supportEmail).setOnClickListener(v -> emailSupport());
    }

    private void bindHeader() {
        String firstName = getIntent().getStringExtra(EXTRA_FIRST_NAME);
        RobotoTextView subtitle = findViewById(R.id.pendingSubtitle);
        if (!TextUtils.isEmpty(firstName)) {
            subtitle.setText(getString(R.string.pending_subtitle, firstName.trim()));
        } else {
            subtitle.setText(R.string.pending_subtitle_generic);
        }
    }

    private void bindDetails() {
        String status = getIntent().getStringExtra(EXTRA_STATUS);
        if (!TextUtils.isEmpty(status)) {
            ((RobotoBoldTextView) findViewById(R.id.statusBadge)).setText(status);
        }
        setValue(R.id.businessValue, getIntent().getStringExtra(EXTRA_BUSINESS));
        setValue(R.id.referenceValue, getIntent().getStringExtra(EXTRA_REFERENCE));
        setValue(R.id.submittedValue, getIntent().getStringExtra(EXTRA_SUBMITTED));
    }

    private void bindWallet() {
        boolean activated = getIntent().getBooleanExtra(EXTRA_WALLET_ACTIVATED, false);
        RobotoBoldTextView badge = findViewById(R.id.walletBadge);
        LinearLayout infoBox = findViewById(R.id.infoBox);

        if (activated) {
            badge.setText(R.string.pending_wallet_activated);
            badge.setBackgroundResource(R.drawable.bg_badge_success);
            badge.setTextColor(0xFF4ADE80);
            infoBox.setVisibility(ViewGroup.GONE);
        } else {
            badge.setText(R.string.pending_wallet_not_activated);
            badge.setBackgroundResource(R.drawable.bg_badge_muted);
            badge.setTextColor(0xFFB4C3D8);

            String message = getIntent().getStringExtra(EXTRA_WALLET_MESSAGE);
            RobotoTextView infoText = findViewById(R.id.infoText);
            infoText.setText(TextUtils.isEmpty(message)
                    ? getString(R.string.pending_wallet_later) : message);
        }
    }

    private void setValue(int viewId, String value) {
        RobotoBoldTextView tv = findViewById(viewId);
        tv.setText(TextUtils.isEmpty(value) ? getString(R.string.pending_dash) : value);
    }

    private void buildNextSteps() {
        LinearLayout container = findViewById(R.id.nextStepsContainer);
        for (int i = 0; i < NEXT_STEPS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            RobotoBoldTextView num = new RobotoBoldTextView(this);
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            num.setLayoutParams(numLp);
            num.setGravity(Gravity.CENTER);
            num.setText(String.valueOf(i + 1));
            num.setTextSize(12);
            num.setTextColor(0xFFFFFFFF);
            num.setBackgroundResource(R.drawable.bg_step_circle_active);
            row.addView(num);

            RobotoTextView label = new RobotoTextView(this);
            label.setText(NEXT_STEPS[i]);
            label.setTextSize(13);
            label.setLineSpacing(dp(3), 1f);
            label.setTextColor(0xFFC7D0E4);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(14);
            label.setLayoutParams(lp);
            row.addView(label);

            container.addView(row);
        }
    }

    private void emailSupport() {
        String email = getString(R.string.pending_support_email);
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void backToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        backToLogin();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
