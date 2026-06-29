package com.anokix.trader.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.DistributorListData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Distributor detail screen (opened from {@link DistributorsActivity}). Shows the
 * partner's profile, contacts and performance, and lets the trader file a
 * "Request Change Distributor" (POST api/trader/distributor/change-request).
 *
 * The performance figures are not supplied by the API; they mirror the indicative
 * values shown in the web portal design and are rendered statically.
 */
public class DistributorDetailActivity extends AppCompatActivity {

    public static final String EXTRA_DISTRIBUTOR = "distributor";

    private DistributorListData.Distributor distributor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distributor_detail);

        Object extra = getIntent().getSerializableExtra(EXTRA_DISTRIBUTOR);
        if (extra instanceof DistributorListData.Distributor) {
            distributor = (DistributorListData.Distributor) extra;
        }
        if (distributor == null) {
            finish();
            return;
        }

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        bind();

        MaterialButton btnRequest = findViewById(R.id.btnRequestChange);
        btnRequest.setOnClickListener(v -> showChangeRequestDialog());
    }

    private void bind() {
        ((TextView) findViewById(R.id.detailLogo)).setText(distributor.initial());
        ((TextView) findViewById(R.id.detailName)).setText(distributor.displayName());
        ((TextView) findViewById(R.id.detailCoverage)).setText(orDash(distributor.address));
        ((TextView) findViewById(R.id.detailProducts)).setText(distributor.products_available + "+");
        ((TextView) findViewById(R.id.detailSla)).setText("—");
        ((TextView) findViewById(R.id.detailMinOrder)).setText(orDash(distributor.minimum_order));

        ((TextView) findViewById(R.id.detailContactName)).setText(orDash(distributor.contact_name));
        ((TextView) findViewById(R.id.detailContactEmail)).setText(orDash(distributor.contact_email));
        ((TextView) findViewById(R.id.detailContactPhone)).setText(orDash(distributor.contact_phone));

        // "Your Preferred Distributor" only makes sense for an active partner.
        View preferred = findViewById(R.id.detailPreferred);
        boolean active = distributor.status == null || distributor.status.equalsIgnoreCase("active");
        preferred.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void showChangeRequestDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_distributor, null, false);
        TextView message = view.findViewById(R.id.changeMessage);
        TextInputEditText reasonInput = view.findViewById(R.id.reasonInput);
        message.setText("Submit a request to change your distributor from "
                + distributor.displayName() + ". Our team will review it and get back to you.");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Request Change Distributor")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit Request", (dialog, which) -> {
                    String reason = reasonInput.getText() == null ? "" : reasonInput.getText().toString().trim();
                    submitChangeRequest(reason);
                })
                .show();
    }

    private void submitChangeRequest(String reason) {
        Toast.makeText(this, "Submitting request…", Toast.LENGTH_SHORT).show();
        ApiClient.get(this).requestDistributorChange(
                String.valueOf(distributor.id), reason, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Toast.makeText(DistributorDetailActivity.this,
                                "Change request submitted. Our team will review it.",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String messageText) {
                        Toast.makeText(DistributorDetailActivity.this, messageText, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}
