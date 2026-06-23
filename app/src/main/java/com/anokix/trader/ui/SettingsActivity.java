package com.anokix.trader.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.anokix.trader.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/** Settings — mirrors the Trader Portal /settings page (verbatim data). */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.exportData).setOnClickListener(v ->
                Toast.makeText(this, "Preparing your data export…", Toast.LENGTH_SHORT).show());
        findViewById(R.id.resetSettings).setOnClickListener(v -> confirm("Reset App Settings",
                "Restore all settings to defaults? Your data will not be deleted.", "Reset"));
        findViewById(R.id.deactivateStore).setOnClickListener(v -> confirm("Deactivate Store",
                "Deactivate store requires confirmation. This will suspend your store.", "Deactivate"));

        buildStatusCards();
        buildSections();
    }

    private void buildStatusCards() {
        LinearLayout row = findViewById(R.id.statusRow);
        String[] titles = {"Store Status", "Wallet Status", "Rewards Status", "POS Status"};
        String[] values = {"Active", "Active", "Active", "Online"};
        String[] subs = {"Everything is running smoothly.", "Powered by IMB", "Powered by Limes", "Powered by Pagamio"};
        String[] tones = {"#6366f1", "#2563eb", "#16a34a", "#7c3aed"};

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < titles.length; i++) {
            View card = inflater.inflate(R.layout.item_settings_status, row, false);
            tint(card.findViewById(R.id.statusAccent), tones[i]);
            ((TextView) card.findViewById(R.id.statusTitle)).setText(titles[i]);
            TextView value = card.findViewById(R.id.statusValue);
            value.setText(values[i]);
            value.setTextColor(Color.parseColor(tones[i]));
            ((TextView) card.findViewById(R.id.statusSubtitle)).setText(subs[i]);
            row.addView(card);
        }
    }

    private void buildSections() {
        LinearLayout container = findViewById(R.id.sectionsContainer);
        String[] titles = {"Store Profile", "Business Settings", "anokiX Wallet Settings",
                "anokiX Rewards Settings", "POS Device Management", "User Management",
                "Notifications", "Security & Access"};
        String[] icons = {"🏪", "🧾", "💰", "⭐", "🖥️", "👥", "🔔", "🔒"};
        String[] actions = {"Edit Profile", "Update Business Details", "Wallet Settings",
                "Rewards Settings", "Device Settings", "Manage Users",
                "Notification Settings", "Security Settings"};

        String[][][] fields = {
                { {"Store Name", "Sipho Spaza Store", ""}, {"Owner", "Sipho Dlamini", ""},
                  {"Mobile", "082 123 4567", ""}, {"Email", "sipho@email.com", ""},
                  {"Address", "Umlazi, Durban, 4031", ""}, {"Business Type", "Spaza Shop", ""} },
                { {"VAT Registered", "Yes", "green"}, {"VAT Number", "4123456789", ""},
                  {"Business Registration", "CK2025/12345", ""}, {"Industry", "Retail & FMCG", ""} },
                { {"Wallet Status", "Active", "green"}, {"Settlement Account", "Linked", "green"},
                  {"Daily Settlement", "Enabled", "green"}, {"Notifications", "Enabled", "green"},
                  {"Auto Top-up", "Disabled", "gray"} },
                { {"Rewards Active", "Yes", "green"}, {"Cashback Active", "Yes", "green"},
                  {"Promotions Active", "Yes", "green"}, {"Points Expiry", "12 Months", ""},
                  {"Auto Enrol New Customers", "Enabled", "green"} },
                { {"Device ID", "POS-10234", ""}, {"Status", "Online", "green"},
                  {"Software Version", "1.4.2", ""}, {"Last Sync", "2 mins ago", ""},
                  {"Battery Level", "87%", ""} },
                { {"Total Users", "3", ""}, {"Active Users", "3", ""}, {"Roles", "3", ""},
                  {"Last Login", "2 mins ago", ""}, {"Access Control", "Configured", ""} },
                { {"Order Updates", "Enabled", "green"}, {"Promotions", "Enabled", "green"},
                  {"Wallet Alerts", "Enabled", "green"}, {"Low Stock Alerts", "Enabled", "green"},
                  {"System Updates", "Enabled", "green"} },
                { {"PIN Login", "Enabled", "green"}, {"Biometric Login", "Enabled", "green"},
                  {"Two Factor Auth", "Enabled", "green"}, {"Password Changed", "5 days ago", ""},
                  {"Active Sessions", "2", ""} }
        };

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int s = 0; s < titles.length; s++) {
            View card = inflater.inflate(R.layout.item_settings_section, container, false);
            ((TextView) card.findViewById(R.id.sectionIcon)).setText(icons[s]);
            ((TextView) card.findViewById(R.id.sectionTitle)).setText(titles[s]);

            LinearLayout fieldList = card.findViewById(R.id.sectionFields);
            for (String[] f : fields[s]) {
                View row = inflater.inflate(R.layout.item_settings_field, fieldList, false);
                ((TextView) row.findViewById(R.id.fieldLabel)).setText(f[0]);
                TextView value = row.findViewById(R.id.fieldValue);
                value.setText(f[1]);
                applyBadge(value, f[2]);
                fieldList.addView(row);
            }

            MaterialButton action = card.findViewById(R.id.sectionAction);
            action.setText(actions[s]);
            final String label = actions[s];
            action.setOnClickListener(v -> Toast.makeText(this, label, Toast.LENGTH_SHORT).show());
            container.addView(card);
        }
    }

    private void applyBadge(TextView value, String tone) {
        if (tone == null || tone.isEmpty()) {
            value.setBackground(null);
            value.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            value.setPadding(0, 0, 0, 0);
            return;
        }
        int pad = dp(8);
        value.setPadding(pad, dp(2), pad, dp(2));
        value.setTextColor(Color.WHITE);
        value.setBackgroundResource("gray".equals(tone)
                ? R.drawable.bg_badge_info : R.drawable.bg_badge_success);
        value.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor("gray".equals(tone) ? "#94A3B8" : "#16A34A")));
    }

    private void confirm(String title, String message, String positive) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (d, w) ->
                        Toast.makeText(this, title + " — done", Toast.LENGTH_SHORT).show())
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private static void tint(View v, String hex) {
        v.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
