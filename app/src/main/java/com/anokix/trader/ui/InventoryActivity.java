package com.anokix.trader.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.InventoryItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trader inventory, mirroring the Trader Portal /inventory page: 4 stat cards
 * (Total Stock Value / Total Units / Low Stock / Out of Stock), movement/status
 * filter chips, and a card list with stock bars + a tap-to-adjust flow.
 */
public class InventoryActivity extends AppCompatActivity {

    private static final String[] FILTER_LABELS = {
            "All Inventory", "Fast Moving", "Slow Moving", "Low Stock", "Out of Stock", "Damaged", "Expired"
    };
    private static final String[] FILTER_KEYS = {
            "all", "fast", "slow", "low_stock", "out_of_stock", "damaged", "expired"
    };

    private List<InventoryItem> allItems;
    private final List<InventoryItem> shown = new ArrayList<>();
    private InventoryAdapter adapter;
    private String activeFilter = "all";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        allItems = MockData.getInventoryItems();

        RecyclerView list = findViewById(R.id.inventoryList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter();
        list.setAdapter(adapter);

        bindStats();
        buildFilters();
        applyFilter("all");
    }

    private void bindStats() {
        double totalValue = 0;
        long totalUnits = 0;
        int low = 0, out = 0;
        for (InventoryItem item : allItems) {
            totalValue += item.stockValue;
            totalUnits += item.units;
            if ("low_stock".equals(item.statusKey)) low++;
            else if ("out_of_stock".equals(item.statusKey)) out++;
        }
        ((TextView) findViewById(R.id.statValue)).setText(String.format(Locale.US, "R %,.0f", totalValue));
        ((TextView) findViewById(R.id.statUnits)).setText(String.format(Locale.US, "%,d", totalUnits));
        ((TextView) findViewById(R.id.statLow)).setText(String.valueOf(low));
        ((TextView) findViewById(R.id.statOut)).setText(String.valueOf(out));
    }

    private void buildFilters() {
        ChipGroup group = findViewById(R.id.filterChips);
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final String key = FILTER_KEYS[i];
            Chip chip = new Chip(this);
            chip.setText(FILTER_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_selector));
            chip.setOnClickListener(v -> applyFilter(key));
            group.addView(chip);
        }
    }

    private void applyFilter(String key) {
        activeFilter = key;
        shown.clear();
        for (InventoryItem item : allItems) {
            if (matches(item, key)) shown.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matches(InventoryItem item, String key) {
        switch (key) {
            case "all":      return true;
            case "fast":     return "fast".equals(item.movement);
            case "slow":     return "slow".equals(item.movement);
            default:         return key.equals(item.statusKey);
        }
    }

    private void showItem(InventoryItem item, int position) {
        String details = String.format(Locale.US,
                "SKU: %s\nBarcode: %s\nCategory: %s\nBrand: %s\nWarehouse: %s\n\n"
                        + "Units on hand: %,d  (capacity %,d)\nStock value: R %,.0f\nStatus: %s\nMovement: %s\nUpdated: %s",
                item.sku, item.barcode, item.category, item.brand, item.warehouse,
                item.units, item.capacity, item.stockValue, item.status(),
                "fast".equals(item.movement) ? "Fast moving" : "Slow moving", item.updatedAt);

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage(details)
                .setPositiveButton("Adjust stock", (d, w) -> adjustStock(item, position))
                .setNegativeButton("Close", null)
                .show();
    }

    private void adjustStock(InventoryItem item, int position) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(item.units));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Adjust " + item.name)
                .setMessage("Enter the new quantity on hand:")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        item.units = Math.max(0, Integer.parseInt(input.getText().toString().trim()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindStats();
                    applyFilter(activeFilter);
                    Toast.makeText(this, "Stock updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private int statusColor(String statusKey) {
        switch (statusKey) {
            case "low_stock":    return ContextCompat.getColor(this, R.color.warning);
            case "out_of_stock": return ContextCompat.getColor(this, R.color.danger);
            case "damaged":      return Color.parseColor("#DB2777");
            case "expired":      return Color.parseColor("#78716C");
            default:             return ContextCompat.getColor(this, R.color.success);
        }
    }

    private class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_inventory, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            InventoryItem item = shown.get(position);
            holder.title.setText(item.name);
            holder.sku.setText(item.sku);
            holder.meta.setText(item.category + " · " + item.warehouse + " · " + item.updatedAt);
            holder.units.setText(String.format(Locale.US, "%,d units", item.units));
            holder.value.setText(String.format(Locale.US, "R %,.0f", item.stockValue));

            try {
                ViewCompat.setBackgroundTintList(holder.thumb,
                        ColorStateList.valueOf(Color.parseColor(item.imageColor)));
            } catch (IllegalArgumentException ignored) { }

            int color = statusColor(item.statusKey);
            holder.status.setText(item.status());
            holder.status.setTextColor(color);
            ViewCompat.setBackgroundTintList(holder.status,
                    ColorStateList.valueOf(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))));

            boolean outOfStock = "out_of_stock".equals(item.statusKey);
            holder.progress.setProgress(item.stockPercent());
            holder.progress.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            holder.progress.setVisibility(outOfStock ? View.INVISIBLE : View.VISIBLE);

            holder.itemView.setOnClickListener(v -> showItem(item, position));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, sku, meta, units, value, status;
            final View thumb;
            final ProgressBar progress;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.rowTitle);
                sku = itemView.findViewById(R.id.rowSku);
                meta = itemView.findViewById(R.id.rowMeta);
                units = itemView.findViewById(R.id.rowUnits);
                value = itemView.findViewById(R.id.rowValue);
                status = itemView.findViewById(R.id.rowStatus);
                thumb = itemView.findViewById(R.id.rowThumb);
                progress = itemView.findViewById(R.id.stockProgress);
            }
        }
    }
}
