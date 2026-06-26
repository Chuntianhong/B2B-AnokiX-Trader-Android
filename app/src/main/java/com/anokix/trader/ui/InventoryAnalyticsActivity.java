package com.anokix.trader.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.InventoryAnalyticsData;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/**
 * Inventory Analytics (GET api/trader/inventory/analytics?days=30): stock ageing
 * buckets, a gross-profit summary (net ex-VAT − COGS), and reorder suggestions.
 */
public class InventoryAnalyticsActivity extends AppCompatActivity {

    private static final int DAYS = 30;
    private static final String[] AGE_LABELS = {"0–30 days", "31–60 days", "61–90 days", "90+ days"};

    private View loading;
    private TextView gpTitle, gpValue, gpMargin;
    private LinearLayout ageingContainer, reorderContainer;
    private TextView reorderEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_analytics);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        loading = findViewById(R.id.loading);
        gpTitle = findViewById(R.id.gpTitle);
        gpValue = findViewById(R.id.gpValue);
        gpMargin = findViewById(R.id.gpMargin);
        ageingContainer = findViewById(R.id.ageingContainer);
        reorderContainer = findViewById(R.id.reorderContainer);
        reorderEmpty = findViewById(R.id.reorderEmpty);

        load();
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getInventoryAnalytics(DAYS, new ApiCallback<InventoryAnalyticsData>() {
            @Override
            public void onSuccess(InventoryAnalyticsData data) {
                loading.setVisibility(View.GONE);
                if (data == null) return;
                bindGrossProfit(data);
                bindAgeing(data);
                bindReorder(data);
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                Toast.makeText(InventoryAnalyticsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindGrossProfit(InventoryAnalyticsData data) {
        int days = data.window_days > 0 ? data.window_days : DAYS;
        gpTitle.setText("Gross Profit · Last " + days + " days");
        InventoryAnalyticsData.GrossProfit gp = data.gross_profit;
        if (gp == null) return;
        gpValue.setText(InventoryActivity.money(gp.gross_profit));
        gpMargin.setText(String.format(Locale.US, "%.1f%% margin", gp.margin_pct));
        kv(R.id.kvNet, "Net revenue (ex-VAT)", InventoryActivity.money(gp.net_revenue));
        kv(R.id.kvCogs, "Cost of goods sold", InventoryActivity.money(gp.cogs));
        kv(R.id.kvUnits, "Units sold", String.format(Locale.US, "%,d", gp.units_sold));
    }

    private void bindAgeing(InventoryAnalyticsData data) {
        ageingContainer.removeAllViews();
        if (data.ageing == null) return;
        for (int i = 0; i < data.ageing.size(); i++) {
            InventoryAnalyticsData.AgeingBucket b = data.ageing.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_ageing, ageingContainer, false);
            ViewCompat.setBackgroundTintList(row.findViewById(R.id.ageDot),
                    ColorStateList.valueOf(parseColor(b.color, R.color.text_secondary)));
            String label = i < AGE_LABELS.length ? AGE_LABELS[i] : (b.label == null ? "" : b.label);
            ((TextView) row.findViewById(R.id.ageLabel)).setText(label);
            ((TextView) row.findViewById(R.id.ageSub))
                    .setText(String.format(Locale.US, "%,d products · %,d units", b.products, b.units));
            ((TextView) row.findViewById(R.id.ageValue)).setText(InventoryActivity.money(b.value));
            ageingContainer.addView(row);
        }
    }

    private void bindReorder(InventoryAnalyticsData data) {
        reorderContainer.removeAllViews();
        boolean empty = data.reorder == null || data.reorder.isEmpty();
        reorderEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (InventoryAnalyticsData.Reorder r : data.reorder) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_reorder, reorderContainer, false);
            ((TextView) row.findViewById(R.id.reorderName)).setText(r.name == null ? "" : r.name);
            ((TextView) row.findViewById(R.id.reorderSub)).setText(String.format(Locale.US,
                    "%s · On hand %,d / %,d · sold %,d", r.sku == null ? "" : r.sku, r.on_hand, r.threshold, r.sold_last_30));
            ((TextView) row.findViewById(R.id.reorderSuggest)).setText("Order " + r.suggested);

            TextView urgency = row.findViewById(R.id.reorderUrgency);
            urgency.setText(capitalize(r.urgency));
            int color = urgencyColor(r.urgency);
            urgency.setTextColor(color);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(getResources().getDisplayMetrics().density * 6);
            bg.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
            urgency.setBackground(bg);
            reorderContainer.addView(row);
        }
    }

    private void kv(int includeId, String label, String value) {
        View row = findViewById(includeId);
        ((TextView) row.findViewById(R.id.kvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.kvValue)).setText(value);
    }

    private int urgencyColor(String urgency) {
        switch (urgency == null ? "" : urgency) {
            case "high":   return ContextCompat.getColor(this, R.color.danger);
            case "medium": return ContextCompat.getColor(this, R.color.warning);
            default:       return ContextCompat.getColor(this, R.color.success);
        }
    }

    private int parseColor(String hex, int fallbackRes) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return ContextCompat.getColor(this, fallbackRes);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "—";
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }
}
