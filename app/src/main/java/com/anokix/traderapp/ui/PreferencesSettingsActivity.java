package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.PreferencesData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings → Preferences. Language / Currency / Timezone / Date format. Timezone
 * options come from base-info. Saves only the regional preferences
 * (api/common/preferences/update). Currency is sent as a numeric id (ZAR=1…).
 */
public class PreferencesSettingsActivity extends AppCompatActivity {

    private ApiClient api;
    private MaterialAutoCompleteTextView ddLanguage, ddCurrency, ddTimezone, ddDateFormat, ddVasPayment;
    private MaterialButton btnSave;

    private String[] tzLabels = new String[0];
    private String[] tzValues = new String[0];

    // Values captured from the preferences load, applied once both calls return.
    private String loadedLanguage, loadedCurrency, loadedTimezone, loadedDateFormat, loadedVasPayment;
    private boolean prefsLoaded, baseLoaded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences_settings);
        api = ApiClient.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ddLanguage = findViewById(R.id.ddLanguage);
        ddCurrency = findViewById(R.id.ddCurrency);
        ddTimezone = findViewById(R.id.ddTimezone);
        ddDateFormat = findViewById(R.id.ddDateFormat);
        ddVasPayment = findViewById(R.id.ddVasPayment);
        btnSave = findViewById(R.id.btnSave);

        bind(ddLanguage, SettingsOptions.LANGUAGE_LABELS);
        bind(ddCurrency, SettingsOptions.CURRENCY_LABELS);
        bind(ddDateFormat, SettingsOptions.DATEFMT_LABELS);
        bind(ddVasPayment, SettingsOptions.VAS_PAYMENT_LABELS);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        load();
    }

    private void load() {
        api.getPreferences(new ApiCallback<PreferencesData>() {
            @Override public void onSuccess(PreferencesData data) {
                PreferencesData.Preferences p = data == null ? null : data.preferences;
                if (p != null) {
                    loadedLanguage = p.language;
                    loadedCurrency = p.currency;
                    loadedTimezone = p.timezone;
                    loadedDateFormat = p.date_format;
                    loadedVasPayment = p.vas_payment_mode;
                }
                prefsLoaded = true;
                applySelections();
            }
            @Override public void onError(String message) { toast(message); }
        });
        api.getBaseInfo(new ApiCallback<BaseInfoData>() {
            @Override public void onSuccess(BaseInfoData data) {
                buildTimezones(data == null ? null : data.timezones);
                baseLoaded = true;
                applySelections();
            }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void buildTimezones(List<BaseInfoData.Timezone> zones) {
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        if (zones != null) {
            for (BaseInfoData.Timezone z : zones) {
                if (z == null || z.time_zone == null) continue;
                values.add(z.time_zone);
                labels.add(z.time_zone + " (" + SettingsOptions.gmtLabel(z.time_offset) + ")");
            }
        }
        tzLabels = labels.toArray(new String[0]);
        tzValues = values.toArray(new String[0]);
        ddTimezone.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tzLabels));
    }

    /** Apply the loaded values once preferences AND base-info (timezones) are both in. */
    private void applySelections() {
        if (prefsLoaded) {
            ddLanguage.setText(SettingsOptions.LANGUAGE_LABELS[
                    SettingsOptions.indexOf(SettingsOptions.LANGUAGE_VALUES, loadedLanguage)], false);
            ddCurrency.setText(SettingsOptions.CURRENCY_LABELS[
                    SettingsOptions.currencyIndex(loadedCurrency)], false);
            ddDateFormat.setText(SettingsOptions.DATEFMT_LABELS[
                    SettingsOptions.indexOf(SettingsOptions.DATEFMT_VALUES, loadedDateFormat)], false);
            ddVasPayment.setText(SettingsOptions.VAS_PAYMENT_LABELS[
                    SettingsOptions.indexOf(SettingsOptions.VAS_PAYMENT_VALUES, loadedVasPayment)], false);
        }
        if (prefsLoaded && baseLoaded && tzValues.length > 0) {
            int idx = SettingsOptions.indexOf(tzValues, loadedTimezone);
            ddTimezone.setText(tzLabels[idx], false);
        }
    }

    private void save() {
        String language = SettingsOptions.LANGUAGE_VALUES[selected(ddLanguage, SettingsOptions.LANGUAGE_LABELS)];
        String currency = SettingsOptions.CURRENCY_VALUES[selected(ddCurrency, SettingsOptions.CURRENCY_LABELS)];
        String dateFormat = SettingsOptions.DATEFMT_VALUES[selected(ddDateFormat, SettingsOptions.DATEFMT_LABELS)];
        String timezone = tzValues.length == 0 ? loadedTimezone
                : tzValues[selected(ddTimezone, tzLabels)];
        String vasPayment = SettingsOptions.VAS_PAYMENT_VALUES[
                selected(ddVasPayment, SettingsOptions.VAS_PAYMENT_LABELS)];

        setBusy(true);
        api.updatePreferenceValues(language, currency, timezone, dateFormat, vasPayment,
                new ApiCallback<Void>() {
            @Override public void onSuccess(Void unused) {
                setBusy(false); toast("Preferences updated successfully.");
            }
            @Override public void onError(String message) { setBusy(false); toast(message); }
        });
    }

    private void bind(MaterialAutoCompleteTextView dd, String[] labels) {
        dd.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        dd.setKeyListener(null); // selection only
    }

    private int selected(MaterialAutoCompleteTextView dd, String[] labels) {
        String shown = dd.getText() == null ? "" : dd.getText().toString();
        for (int i = 0; i < labels.length; i++) if (labels[i].equals(shown)) return i;
        return 0;
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy);
        btnSave.setText(busy ? "Saving…" : "Save Changes");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "Something went wrong." : msg, Toast.LENGTH_SHORT).show();
    }
}
