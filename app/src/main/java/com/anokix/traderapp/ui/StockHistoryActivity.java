package com.anokix.traderapp.ui;

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

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.InventoryData;
import com.anokix.traderapp.network.dto.InventoryHistoryData;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/**
 * Stock History for one product (GET api/trader/inventory/history?product_id=):
 * a stock snapshot (on hand, average cost, value, threshold, status) plus the
 * full movement ledger. Reuses {@link InventoryActivity}'s formatting helpers.
 */
public class StockHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";
    public static final String EXTRA_PRODUCT_NAME = "product_name";

    private MaterialToolbar toolbar;
    private View loading;
    private TextView histSku, histStatus, ledgerEmpty;
    private LinearLayout movementsContainer, snapshotRows;
    private int productId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_history);

        productId = getIntent().getIntExtra(EXTRA_PRODUCT_ID, 0);
        String name = getIntent().getStringExtra(EXTRA_PRODUCT_NAME);

        toolbar = findViewById(R.id.toolbar);
        if (name != null && !name.isEmpty()) toolbar.setTitle(name);
        toolbar.setNavigationOnClickListener(v -> finish());

        histSku = findViewById(R.id.histSku);
        histStatus = findViewById(R.id.histStatus);
        movementsContainer = findViewById(R.id.movementsContainer);
        snapshotRows = findViewById(R.id.snapshotRows);
        ledgerEmpty = findViewById(R.id.ledgerEmpty);
        loading = findViewById(R.id.loading);

        load();
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getInventoryHistory(productId, new ApiCallback<InventoryHistoryData>() {
            @Override
            public void onSuccess(InventoryHistoryData data) {
                loading.setVisibility(View.GONE);
                if (data == null) return;
                bind(data);
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                Toast.makeText(StockHistoryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bind(InventoryHistoryData data) {
        if (data.product != null) {
            if (toolbar.getTitle() == null || toolbar.getTitle().length() == 0) {
                toolbar.setTitle(data.product.name);
            }
            histSku.setText("SKU: " + (data.product.sku == null ? "—" : data.product.sku));
        }

        histStatus.setText(InventoryActivity.statusLabel(data.status));
        int color = statusColor(data.status);
        histStatus.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(getResources().getDisplayMetrics().density * 6);
        bg.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        histStatus.setBackground(bg);

        snapshotRows.removeAllViews();
        addKv("On hand", String.format(Locale.US, "%,d units", data.on_hand));
        addKv("Stock value", InventoryActivity.money(data.stock_value));
        addKv("Average cost", InventoryActivity.money(data.average_cost));
        addKv("Low-stock at", String.valueOf(data.low_stock_threshold));
        addKv("Updated", InventoryActivity.formatDateTime(data.updated_at));

        movementsContainer.removeAllViews();
        boolean empty = data.movements == null || data.movements.isEmpty();
        ledgerEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) {
            for (InventoryData.Movement m : data.movements) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_inventory_movement, movementsContainer, false);
                bindMovement(row, m);
                movementsContainer.addView(row);
            }
        }
    }

    /** Inflate a fresh key/value row into the snapshot container (avoids include-id clashes). */
    private void addKv(String label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_kv, snapshotRows, false);
        ((TextView) row.findViewById(R.id.kvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.kvValue)).setText(value);
        snapshotRows.addView(row);
    }

    private void bindMovement(View row, InventoryData.Movement m) {
        String ref = m.reference_number == null ? "" : m.reference_number;
        String title = !ref.isEmpty() ? ref : InventoryActivity.movementTypeLabel(m.movement_type);
        String sub = (m.note != null && !m.note.isEmpty() ? m.note : InventoryActivity.movementTypeLabel(m.movement_type))
                + " · " + InventoryActivity.formatDateTime(m.created_at);
        ((TextView) row.findViewById(R.id.moveTitle)).setText(title);
        ((TextView) row.findViewById(R.id.moveSub)).setText(sub);

        int net = m.quantity_in - m.quantity_out;
        TextView qty = row.findViewById(R.id.moveQty);
        qty.setText((net > 0 ? "+" : "") + net);
        int c = net > 0 ? R.color.success : (net < 0 ? R.color.danger : R.color.text_secondary);
        qty.setTextColor(androidx.core.content.ContextCompat.getColor(this, c));
        ((TextView) row.findViewById(R.id.moveBal)).setText("Bal " + m.balance_after);
    }

    private int statusColor(String statusKey) {
        switch (statusKey == null ? "" : statusKey) {
            case "low_stock":    return androidx.core.content.ContextCompat.getColor(this, R.color.warning);
            case "out_of_stock": return androidx.core.content.ContextCompat.getColor(this, R.color.danger);
            case "damaged":      return Color.parseColor("#DB2777");
            case "expired":      return Color.parseColor("#78716C");
            default:             return androidx.core.content.ContextCompat.getColor(this, R.color.success);
        }
    }
}
