package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.PreferencesData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONObject;

/**
 * Settings → Notifications. 5 toggle cards saved as the nested {@code notifications}
 * object via api/common/preferences/update.
 */
public class NotificationSettingsActivity extends AppCompatActivity {

    private ApiClient api;
    private MaterialButton btnSave;
    private final SwitchMaterial[] switches = new SwitchMaterial[SettingsOptions.TOGGLES.length];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        api = ApiClient.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnSave = findViewById(R.id.btnSave);
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        buildToggles();
        load();
    }

    private void buildToggles() {
        LinearLayout container = findViewById(R.id.togglesContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < SettingsOptions.TOGGLES.length; i++) {
            View card = inflater.inflate(R.layout.item_settings_toggle_card, container, false);
            ((TextView) card.findViewById(R.id.toggleTitle)).setText(SettingsOptions.TOGGLES[i][1]);
            ((TextView) card.findViewById(R.id.toggleDesc)).setText(SettingsOptions.TOGGLES[i][2]);
            switches[i] = card.findViewById(R.id.toggleSwitch);
            switches[i].setChecked(true); // default ON until preferences load
            container.addView(card);
        }
    }

    private void load() {
        api.getPreferences(new ApiCallback<PreferencesData>() {
            @Override public void onSuccess(PreferencesData data) {
                JsonElement n = data != null && data.preferences != null ? data.preferences.notifications : null;
                JsonObject obj = (n != null && n.isJsonObject()) ? n.getAsJsonObject() : null;
                for (int i = 0; i < SettingsOptions.TOGGLES.length; i++) {
                    boolean on = true;
                    String key = SettingsOptions.TOGGLES[i][0];
                    if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
                        try { on = obj.get(key).getAsBoolean(); } catch (Exception ignored) { }
                    }
                    switches[i].setChecked(on);
                }
            }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void save() {
        JSONObject notifications = new JSONObject();
        try {
            for (int i = 0; i < SettingsOptions.TOGGLES.length; i++) {
                notifications.put(SettingsOptions.TOGGLES[i][0], switches[i].isChecked());
            }
        } catch (Exception ignored) { }
        setBusy(true);
        api.updateNotifications(notifications, new ApiCallback<Void>() {
            @Override public void onSuccess(Void unused) {
                setBusy(false); toast("Preferences updated successfully.");
            }
            @Override public void onError(String message) { setBusy(false); toast(message); }
        });
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnSave.setText(busy ? "Saving…" : "Save Changes");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "Something went wrong." : msg, Toast.LENGTH_SHORT).show();
    }
}
