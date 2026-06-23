package com.anokix.trader.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.Random;

/**
 * Airtime & VAS (Limes): pick a service + provider, enter number/amount, and buy.
 * Mock-first — issues a fake voucher/token on confirm.
 */
public class AirtimeActivity extends AppCompatActivity {

    private static final String[] MOBILE_PROVIDERS = {"Vodacom", "MTN", "Cell C", "Telkom"};
    private static final String[] ELEC_PROVIDERS = {"Eskom", "City Power", "Municipal"};
    private static final String[] GIFT_PROVIDERS = {"Takealot", "Steam", "Google Play", "Netflix"};

    private RadioGroup serviceGroup;
    private Spinner providerSpinner;
    private EditText numberInput;
    private EditText amountInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_airtime);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        serviceGroup = findViewById(R.id.serviceGroup);
        providerSpinner = findViewById(R.id.providerSpinner);
        numberInput = findViewById(R.id.numberInput);
        amountInput = findViewById(R.id.amountInput);

        serviceGroup.setOnCheckedChangeListener((g, id) -> updateProviders());
        updateProviders();

        ((MaterialButton) findViewById(R.id.buyButton)).setOnClickListener(v -> buy());
    }

    private void updateProviders() {
        String[] providers;
        int checked = serviceGroup.getCheckedRadioButtonId();
        if (checked == R.id.svcElectricity) {
            providers = ELEC_PROVIDERS;
        } else if (checked == R.id.svcGift) {
            providers = GIFT_PROVIDERS;
        } else {
            providers = MOBILE_PROVIDERS;
        }
        providerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, providers));
    }

    private void buy() {
        String number = numberInput.getText().toString().trim();
        String amountStr = amountInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        String service = selectedServiceLabel();
        String provider = providerSpinner.getSelectedItem() != null
                ? providerSpinner.getSelectedItem().toString() : "";
        String voucher = generateVoucher();

        String message = String.format(Locale.US,
                "%s · %s\nFor: %s\nAmount: R%,.2f\n\nVoucher / Token:\n%s\n\nReceipt printed.",
                service, provider, number, amount, voucher);

        new AlertDialog.Builder(this)
                .setTitle("Purchase successful")
                .setMessage(message)
                .setPositiveButton(R.string.promo_done, (d, w) -> {
                    numberInput.setText("");
                    amountInput.setText("");
                })
                .setCancelable(false)
                .show();
    }

    private String selectedServiceLabel() {
        int checked = serviceGroup.getCheckedRadioButtonId();
        if (checked == R.id.svcData) {
            return "Data Bundle";
        } else if (checked == R.id.svcElectricity) {
            return "Electricity";
        } else if (checked == R.id.svcGift) {
            return "Gift Card";
        }
        return "Airtime";
    }

    private String generateVoucher() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(String.format(Locale.US, "%04d", r.nextInt(10000)));
        }
        return sb.toString();
    }
}
