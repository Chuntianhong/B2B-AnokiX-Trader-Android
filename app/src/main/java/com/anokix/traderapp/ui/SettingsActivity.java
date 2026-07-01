package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.BusinessProfileData;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Settings home — a sectioned nav list mirroring the web portal's left rail:
 * Profile, Business Profile, Notifications, Security, Preferences. Each row opens
 * a dedicated editor screen. Header shows the signed-in user + company.
 */
public class SettingsActivity extends AppCompatActivity {

    private TextView avatarInitials, profileName, profileCompany;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        avatarInitials = findViewById(R.id.avatarInitials);
        profileName = findViewById(R.id.profileName);
        profileCompany = findViewById(R.id.profileCompany);

        LinearLayout navList = findViewById(R.id.navList);
        addRow(navList, R.drawable.ic_person, "Profile",
                "Update your personal account information.", ProfileSettingsActivity.class);
        addRow(navList, R.drawable.ic_store, "Business Profile",
                "Company identity, documents and services.", BusinessProfileActivity.class);
        addRow(navList, R.drawable.ic_notifications, "Notifications",
                "Choose which alerts you want to receive.", NotificationSettingsActivity.class);
        addRow(navList, R.drawable.ic_lock, "Security",
                "Update your password and account security.", SecuritySettingsActivity.class);
        addRow(navList, R.drawable.ic_settings, "Preferences",
                "Language, currency and regional settings.", PreferencesSettingsActivity.class);

        loadHeader();
    }

    private void addRow(LinearLayout parent, int icon, String title, String subtitle,
                        Class<?> target) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_nav, parent, false);
        ((ImageView) row.findViewById(R.id.navIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.navTitle)).setText(title);
        ((TextView) row.findViewById(R.id.navSubtitle)).setText(subtitle);
        row.setOnClickListener(v -> startActivity(new Intent(this, target)));
        parent.addView(row);
    }

    private void loadHeader() {
        ApiClient.get(this).getBaseInfo(new ApiCallback<BaseInfoData>() {
            @Override public void onSuccess(BaseInfoData data) {
                BaseInfoData.User u = data == null ? null : data.user;
                if (u == null) return;
                String full = TextUtils.join(" ", new String[]{nz(u.first_name), nz(u.last_name)}).trim();
                profileName.setText(full.isEmpty() ? nz(u.email) : full);
                avatarInitials.setText(initials(u.first_name, u.last_name, u.email));
            }
            @Override public void onError(String message) { }
        });
        // Company subtitle from the business profile (best-effort).
        ApiClient.get(this).getBusinessProfile(new ApiCallback<BusinessProfileData>() {
            @Override public void onSuccess(BusinessProfileData data) {
                BusinessProfileData.Profile p = data == null ? null : data.profile;
                if (p != null && p.business_name != null) profileCompany.setText(p.business_name);
            }
            @Override public void onError(String message) { }
        });
    }

    static String initials(String first, String last, String email) {
        String a = nz(first), b = nz(last);
        if (!a.isEmpty() && !b.isEmpty()) return ("" + a.charAt(0) + b.charAt(0)).toUpperCase();
        if (!a.isEmpty()) return a.substring(0, 1).toUpperCase();
        String e = nz(email);
        return e.isEmpty() ? "?" : e.substring(0, 1).toUpperCase();
    }

    static String nz(String s) { return s == null ? "" : s; }
}
