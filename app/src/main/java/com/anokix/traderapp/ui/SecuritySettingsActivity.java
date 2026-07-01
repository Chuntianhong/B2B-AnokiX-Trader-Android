package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Settings → Security. Change password (api/common/profile/password). */
public class SecuritySettingsActivity extends AppCompatActivity {

    private ApiClient api;
    private TextInputEditText inCurrentPwd, inNewPwd, inConfirmPwd;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_settings);
        api = ApiClient.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        inCurrentPwd = findViewById(R.id.inCurrentPwd);
        inNewPwd = findViewById(R.id.inNewPwd);
        inConfirmPwd = findViewById(R.id.inConfirmPwd);
        btnSave = findViewById(R.id.btnSave);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        String current = text(inCurrentPwd), next = text(inNewPwd), confirm = text(inConfirmPwd);
        if (current.isEmpty()) { error(inCurrentPwd, "Enter current password"); return; }
        if (next.length() < 8) { error(inNewPwd, "Password must be at least 8 characters."); return; }
        if (!next.equals(confirm)) { error(inConfirmPwd, "Passwords do not match."); return; }
        setBusy(true);
        api.changePassword(current, next, new ApiCallback<Void>() {
            @Override public void onSuccess(Void unused) {
                setBusy(false);
                inCurrentPwd.setText(""); inNewPwd.setText(""); inConfirmPwd.setText("");
                toast("Password updated successfully.");
            }
            @Override public void onError(String message) { setBusy(false); toast(message); }
        });
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnSave.setText(busy ? "Saving…" : "Save Changes");
    }

    private void error(TextInputEditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "Something went wrong." : msg, Toast.LENGTH_SHORT).show();
    }
}
