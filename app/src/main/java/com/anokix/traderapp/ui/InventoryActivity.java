package com.anokix.traderapp.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.InventoryData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trader inventory dashboard (live, GET api/trader/inventory): KPI summary, status
 * breakdown, fast movers, recent stock-movement ledger, and a filterable product
 * list. Tapping a product opens a sheet to adjust stock (signed qty + reason →
 * POST api/trader/inventory/adjust) and jump to its Stock History. The toolbar
 * Analytics action opens {@link InventoryAnalyticsActivity}.
 */
public class InventoryActivity extends AppCompatActivity {

    private static final String[] FILTER_LABELS = {
            "All", "Fast Moving", "Slow Moving", "In Stock", "Low Stock", "Out of Stock"
    };
    private static final String[] FILTER_KEYS = {
            "all", "fast", "slow", "in_stock", "low_stock", "out_of_stock"
    };

    static final String[] ADJUST_REASON_KEYS = {
            "stock_count", "damaged", "expired", "theft", "found", "correction", "other"
    };
    static final String[] ADJUST_REASON_LABELS = {
            "Stock count", "Damaged", "Expired", "Theft / loss", "Found", "Correction", "Other"
    };

    private final List<InventoryData.Row> rows = new ArrayList<>();
    private String activeFilter = "all";

    private SwipeRefreshLayout swipeRefresh;
    private View loading;
    private TextView statValue, statUnits, statLow, statOut, statMovement, emptyText;
    private com.anokix.traderapp.ui.views.DonutChartView statusDonut;
    private LinearLayout statusLegend, fastMovingContainer, movementsContainer, productsContainer;
    private View fastMovingCard, movementsCard;
    private ChipGroup filterChips;
    private InventoryData.Summary summary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_inventory);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_analytics) {
                startActivity(new Intent(this, InventoryAnalyticsActivity.class));
                return true;
            }
            return false;
        });

        statValue = findViewById(R.id.statValue);
        statUnits = findViewById(R.id.statUnits);
        statLow = findViewById(R.id.statLow);
        statOut = findViewById(R.id.statOut);
        statMovement = findViewById(R.id.statMovement);
        emptyText = findViewById(R.id.emptyText);
        statusDonut = findViewById(R.id.statusDonut);
        statusLegend = findViewById(R.id.statusLegend);
        fastMovingCard = findViewById(R.id.fastMovingCard);
        fastMovingContainer = findViewById(R.id.fastMovingContainer);
        movementsCard = findViewById(R.id.movementsCard);
        movementsContainer = findViewById(R.id.movementsContainer);
        productsContainer = findViewById(R.id.productsContainer);
        filterChips = findViewById(R.id.filterChips);
        loading = findViewById(R.id.loading);

        findViewById(R.id.btnAdjustStock).setOnClickListener(v -> showAdjustPicker());
        findViewById(R.id.btnReturnStock).setOnClickListener(v ->
                startActivity(new Intent(this, GoodsReturnFormActivity.class)));
        findViewById(R.id.btnAdjustStock).setActivated(true);
        findViewById(R.id.btnReturnStock).setActivated(true);

        buildFilters();

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        load(true);
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getInventory(new ApiCallback<InventoryData>() {
            @Override
            public void onSuccess(InventoryData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                bindSummary(data != null ? data.summary : null);
                bindStatusBreakdown(data != null ? data.chart : null);
                bindFastMoving(data != null ? data.fast_moving : null);
                bindMovements(data != null ? data.movements : null);
                rows.clear();
                if (data != null && data.rows != null) rows.addAll(data.rows);
                renderProducts();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(InventoryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindSummary(InventoryData.Summary s) {
        this.summary = s;
        if (s == null) return;
        statValue.setText(money(s.total_stock_value));
        statUnits.setText(String.format(Locale.US, "%,d", s.total_units));
        statLow.setText(String.valueOf(s.low_stock));
        statOut.setText(String.valueOf(s.out_of_stock));
        statMovement.setText(String.format(Locale.US, "%,d", s.stock_movement));
    }

    private void bindStatusBreakdown(List<InventoryData.ChartSlice> chart) {
        statusLegend.removeAllViews();
        if (chart == null || chart.isEmpty()) {
            statusDonut.setVisibility(View.GONE);
            statusLegend.setVisibility(View.GONE);
            return;
        }
        statusDonut.setVisibility(View.VISIBLE);
        statusLegend.setVisibility(View.VISIBLE);

        // Ring chart from the status breakdown slices.
        float[] values = new float[chart.size()];
        int[] colors = new int[chart.size()];
        for (int i = 0; i < chart.size(); i++) {
            values[i] = (float) Math.max(0, chart.get(i).value);
            colors[i] = parseColor(chart.get(i).color, R.color.divider);
        }
        statusDonut.setData(values, colors);
        int totalProducts = summary != null ? summary.total_products : 0;
        statusDonut.setCenterText(String.valueOf(totalProducts), "Products");

        for (InventoryData.ChartSlice s : chart) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_status_legend, statusLegend, false);
            View dot = row.findViewById(R.id.legendDot);
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(parseColor(s.color, R.color.text_secondary)));
            ((TextView) row.findViewById(R.id.legendLabel)).setText(s.type);
            ((TextView) row.findViewById(R.id.legendValue)).setText(String.format(Locale.US, "%.0f%%", s.value));
            statusLegend.addView(row);
        }
    }

    private void bindFastMoving(List<InventoryData.FastMover> movers) {
        fastMovingContainer.removeAllViews();
        if (movers == null || movers.isEmpty()) {
            fastMovingCard.setVisibility(View.GONE);
            return;
        }
        fastMovingCard.setVisibility(View.VISIBLE);
        for (InventoryData.FastMover m : movers) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_fast_mover, fastMovingContainer, false);
            ((TextView) row.findViewById(R.id.fastRank)).setText(String.valueOf(m.rank));
            ((TextView) row.findViewById(R.id.fastName)).setText(safe(m.name));
            ((TextView) row.findViewById(R.id.fastUnits)).setText(String.format(Locale.US, "%,d units", m.units));
            fastMovingContainer.addView(row);
        }
    }

    private void bindMovements(List<InventoryData.Movement> movements) {
        movementsContainer.removeAllViews();
        if (movements == null || movements.isEmpty()) {
            movementsCard.setVisibility(View.GONE);
            return;
        }
        movementsCard.setVisibility(View.VISIBLE);
        int max = Math.min(movements.size(), 6);
        for (int i = 0; i < max; i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_inventory_movement, movementsContainer, false);
            bindMovementRow(row, movements.get(i), true);
            movementsContainer.addView(row);
        }
    }

    /** Binds one ledger row. {@code showProduct} controls whether the title is the product or the reference. */
    void bindMovementRow(View row, InventoryData.Movement m, boolean showProduct) {
        String typeLabel = movementTypeLabel(m.movement_type);
        String ref = m.reference_number == null ? "" : m.reference_number;
        String title = showProduct ? safe(m.product_name) : (!ref.isEmpty() ? ref : typeLabel);
        String subLead = showProduct
                ? (typeLabel + (ref.isEmpty() ? "" : " · " + ref))
                : (m.note != null && !m.note.isEmpty() ? m.note : typeLabel);
        ((TextView) row.findViewById(R.id.moveTitle)).setText(title);
        ((TextView) row.findViewById(R.id.moveSub)).setText(subLead + " · " + formatDateTime(m.created_at));

        int net = m.quantity_in - m.quantity_out;
        TextView qty = row.findViewById(R.id.moveQty);
        qty.setText((net > 0 ? "+" : "") + net);
        int color = net > 0 ? R.color.success : (net < 0 ? R.color.danger : R.color.text_secondary);
        qty.setTextColor(ContextCompat.getColor(this, color));
        ((TextView) row.findViewById(R.id.moveBal)).setText("Bal " + m.balance_after);
    }

    // ---- Products + filters ----------------------------------------------

    private void buildFilters() {
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final String key = FILTER_KEYS[i];
            Chip chip = new Chip(this);
            chip.setText(FILTER_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_selector));
            chip.setOnClickListener(v -> { activeFilter = key; renderProducts(); });
            filterChips.addView(chip);
        }
    }

    private void renderProducts() {
        productsContainer.removeAllViews();
        int shown = 0;
        for (InventoryData.Row item : rows) {
            if (!matches(item, activeFilter)) continue;
            View v = LayoutInflater.from(this).inflate(R.layout.item_inventory, productsContainer, false);
            bindProduct(v, item);
            productsContainer.addView(v);
            shown++;
        }
        emptyText.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matches(InventoryData.Row item, String key) {
        switch (key) {
            case "all":  return true;
            case "fast": return "fast".equals(item.movement);
            case "slow": return "slow".equals(item.movement);
            default:     return key.equals(item.stockStatus);
        }
    }

    private void bindProduct(View v, InventoryData.Row item) {
        ((TextView) v.findViewById(R.id.rowTitle)).setText(safe(item.name));
        ((TextView) v.findViewById(R.id.rowSku)).setText(safe(item.sku));
        ((TextView) v.findViewById(R.id.rowMeta))
                .setText(item.category + " · " + item.warehouse + " · " + item.updatedAt);
        ((TextView) v.findViewById(R.id.rowUnits))
                .setText(String.format(Locale.US, "%,d / %,d units", item.units, item.capacity));
        ((TextView) v.findViewById(R.id.rowValue)).setText(money(item.stockValue));

        View thumb = v.findViewById(R.id.rowThumb);
        ViewCompat.setBackgroundTintList(thumb, ColorStateList.valueOf(parseColor(item.imageColor, R.color.purple_primary)));

        int color = statusColor(item.stockStatus);
        TextView status = v.findViewById(R.id.rowStatus);
        status.setText(statusLabel(item.stockStatus));
        status.setTextColor(color);
        ViewCompat.setBackgroundTintList(status,
                ColorStateList.valueOf(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))));

        boolean out = "out_of_stock".equals(item.stockStatus);
        ProgressBar progress = v.findViewById(R.id.stockProgress);
        progress.setProgress(item.stockPercent());
        // Tint only the progress layer (not the track) so the bar shows units/capacity.
        progress.setProgressTintList(ColorStateList.valueOf(color));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.border)));
        progress.setVisibility(out ? View.INVISIBLE : View.VISIBLE);

        v.setOnClickListener(view -> showAdjustSheet(item));
    }

    // ---- Adjust sheet ----------------------------------------------------

    /** Top "Adjust Stock" action — pick a product, then open its adjust sheet. */
    private void showAdjustPicker() {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No products to adjust.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) names[i] = safe(rows.get(i).name);
        new AlertDialog.Builder(this)
                .setTitle("Adjust which product?")
                .setItems(names, (d, w) -> showAdjustSheet(rows.get(w)))
                .show();
    }

    private void showAdjustSheet(InventoryData.Row item) {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_inventory_adjust, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.adjName)).setText(safe(item.name));
        ((TextView) sheet.findViewById(R.id.adjSku)).setText(safe(item.sku));
        ((TextView) sheet.findViewById(R.id.adjOnHand))
                .setText(String.format(Locale.US, "On hand: %,d · Value %s", item.units, money(item.stockValue)));
        ((TextView) sheet.findViewById(R.id.adjMeta))
                .setText(item.brand + " · " + item.category + " · " + item.warehouse);
        ViewCompat.setBackgroundTintList(sheet.findViewById(R.id.adjThumb),
                ColorStateList.valueOf(parseColor(item.imageColor, R.color.purple_primary)));
        int sColor = statusColor(item.stockStatus);
        TextView adjStatus = sheet.findViewById(R.id.adjStatus);
        adjStatus.setText(statusLabel(item.stockStatus));
        adjStatus.setTextColor(sColor);
        ViewCompat.setBackgroundTintList(adjStatus,
                ColorStateList.valueOf(Color.argb(28, Color.red(sColor), Color.green(sColor), Color.blue(sColor))));

        TextView segAdd = sheet.findViewById(R.id.segAdd);
        TextView segRemove = sheet.findViewById(R.id.segRemove);
        TextView amountValue = sheet.findViewById(R.id.amountValue);
        TextView reasonField = sheet.findViewById(R.id.reasonField);
        TextView previewText = sheet.findViewById(R.id.previewText);

        final boolean[] isAdd = {true};
        final int[] amount = {1};
        final int[] reasonIdx = {0};

        Runnable refresh = () -> {
            segAdd.setBackgroundResource(isAdd[0] ? R.drawable.bg_grv_segment_selected : 0);
            segRemove.setBackgroundResource(isAdd[0] ? 0 : R.drawable.bg_grv_segment_selected);
            segAdd.setTextColor(ContextCompat.getColor(this, isAdd[0] ? R.color.text_primary : R.color.text_secondary));
            segRemove.setTextColor(ContextCompat.getColor(this, isAdd[0] ? R.color.text_secondary : R.color.text_primary));
            amountValue.setText(String.valueOf(amount[0]));
            reasonField.setText("Reason: " + ADJUST_REASON_LABELS[reasonIdx[0]]);
            int signed = isAdd[0] ? amount[0] : -amount[0];
            previewText.setText("New on hand: " + Math.max(0, item.units + signed));
        };

        segAdd.setOnClickListener(v -> { isAdd[0] = true; refresh.run(); });
        segRemove.setOnClickListener(v -> { isAdd[0] = false; refresh.run(); });
        sheet.findViewById(R.id.btnMinus).setOnClickListener(v -> { amount[0] = Math.max(1, amount[0] - 1); refresh.run(); });
        sheet.findViewById(R.id.btnPlus).setOnClickListener(v -> { amount[0] = amount[0] + 1; refresh.run(); });
        reasonField.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reason for adjustment")
                .setItems(ADJUST_REASON_LABELS, (d, which) -> { reasonIdx[0] = which; refresh.run(); })
                .show());

        sheet.findViewById(R.id.btnHistory).setOnClickListener(v -> {
            dialog.dismiss();
            Intent i = new Intent(this, StockHistoryActivity.class);
            i.putExtra(StockHistoryActivity.EXTRA_PRODUCT_ID, item.id);
            i.putExtra(StockHistoryActivity.EXTRA_PRODUCT_NAME, item.name);
            startActivity(i);
        });

        MaterialButton apply = sheet.findViewById(R.id.btnApply);
        apply.setOnClickListener(v -> {
            int signed = isAdd[0] ? amount[0] : -amount[0];
            apply.setEnabled(false);
            apply.setText("Applying…");
            ApiClient.get(this).adjustInventory(item.id, signed, ADJUST_REASON_KEYS[reasonIdx[0]],
                    new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            dialog.dismiss();
                            Toast.makeText(InventoryActivity.this, "Stock adjusted.", Toast.LENGTH_SHORT).show();
                            load(false);
                        }

                        @Override
                        public void onError(String message) {
                            apply.setEnabled(true);
                            apply.setText("Apply Adjustment");
                            Toast.makeText(InventoryActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        refresh.run();
        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            BottomSheetBehavior<View> b = BottomSheetBehavior.from(parent);
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
            b.setSkipCollapsed(true);
        });
        dialog.show();
    }

    // ---- Shared helpers (also used by history/analytics) -----------------

    static String movementTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "grv":        return "Goods received";
            case "grn":        return "Return";
            case "sale":       return "Sale";
            case "adjustment": return "Adjustment";
            case "order":      return "Order";
            default:           return type.substring(0, 1).toUpperCase(Locale.US) + type.substring(1);
        }
    }

    private int statusColor(String statusKey) {
        switch (statusKey == null ? "" : statusKey) {
            case "low_stock":    return ContextCompat.getColor(this, R.color.warning);
            case "out_of_stock": return ContextCompat.getColor(this, R.color.danger);
            case "damaged":      return Color.parseColor("#DB2777");
            case "expired":      return Color.parseColor("#78716C");
            default:             return ContextCompat.getColor(this, R.color.success);
        }
    }

    static String statusLabel(String statusKey) {
        switch (statusKey == null ? "" : statusKey) {
            case "out_of_stock": return "Out of Stock";
            case "low_stock":    return "Low Stock";
            case "damaged":      return "Damaged";
            case "expired":      return "Expired";
            default:             return "In Stock";
        }
    }

    private int parseColor(String hex, int fallbackRes) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return ContextCompat.getColor(this, fallbackRes);
        }
    }

    static String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** "dd/MM/yyyy HH:mm:ss" → "25 Jun 2026, 21:31". */
    static String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(raw);
            if (d == null) return raw;
            return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
        } catch (Exception e) {
            return raw;
        }
    }
}
