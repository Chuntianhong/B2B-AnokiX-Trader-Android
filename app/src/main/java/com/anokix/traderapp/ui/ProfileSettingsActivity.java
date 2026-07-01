package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.BusinessProfileData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.hbb20.CountryCodePicker;

import android.widget.EditText;

/** Settings → Profile. Edits the signed-in user (api/common/profile/update). */
public class ProfileSettingsActivity extends AppCompatActivity {

    private ApiClient api;
    private TextInputEditText inFirstName, inLastName, inEmail;
    private EditText etPhone;
    private CountryCodePicker ccpPhone;
    private TextView avatarInitials, headerName, headerCompany;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_settings);
        api = ApiClient.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        inFirstName = findViewById(R.id.inFirstName);
        inLastName = findViewById(R.id.inLastName);
        inEmail = findViewById(R.id.inEmail);
        etPhone = findViewById(R.id.etPhone);
        ccpPhone = findViewById(R.id.ccpPhone);
        ccpPhone.registerCarrierNumberEditText(etPhone);
        avatarInitials = findViewById(R.id.avatarInitials);
        headerName = findViewById(R.id.headerName);
        headerCompany = findViewById(R.id.headerCompany);
        btnSave = findViewById(R.id.btnSave);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        load();
    }

    private void load() {
        api.getBaseInfo(new ApiCallback<BaseInfoData>() {
            @Override public void onSuccess(BaseInfoData data) {
                BaseInfoData.User u = data == null ? null : data.user;
                if (u == null) return;
                inFirstName.setText(u.first_name);
                inLastName.setText(u.last_name);
                inEmail.setText(u.email);
                if (u.phone_number != null && !u.phone_number.trim().isEmpty()) {
                    try { ccpPhone.setFullNumber(u.phone_number.trim()); }
                    catch (Exception ignored) { etPhone.setText(u.phone_number.trim()); }
                }
                refreshHeader(u.first_name, u.last_name, u.email);
            }
            @Override public void onError(String message) { toast(message); }
        });
        api.getBusinessProfile(new ApiCallback<BusinessProfileData>() {
            @Override public void onSuccess(BusinessProfileData data) {
                if (data != null && data.profile != null && data.profile.business_name != null) {
                    headerCompany.setText(data.profile.business_name);
                }
            }
            @Override public void onError(String message) { }
        });
    }

    private void save() {
        String first = text(inFirstName), last = text(inLastName), email = text(inEmail);
        if (first.isEmpty()) { error(inFirstName, "First name is required."); return; }
        if (email.isEmpty()) { error(inEmail, "Email is required."); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            error(inEmail, "Enter a valid email."); return;
        }
        String phone = text(etPhone).isEmpty() ? "" : ccpPhone.getFullNumberWithPlus();
        setBusy(true);
        api.updateProfile(first, last, email, phone, new ApiCallback<Void>() {
            @Override public void onSuccess(Void unused) {
                setBusy(false);
                refreshHeader(first, last, email);
                toast("Profile updated successfully.");
            }
            @Override public void onError(String message) { setBusy(false); toast(message); }
        });
    }

    private void refreshHeader(String first, String last, String email) {
        String full = ((SettingsActivity.nz(first) + " " + SettingsActivity.nz(last)).trim());
        headerName.setText(full.isEmpty() ? SettingsActivity.nz(email) : full);
        avatarInitials.setText(SettingsActivity.initials(first, last, email));
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnSave.setText(busy ? "Saving…" : "Save Changes");
    }

    private void error(TextInputEditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private String text(TextView e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "Something went wrong." : msg, Toast.LENGTH_SHORT).show();
    }
}
