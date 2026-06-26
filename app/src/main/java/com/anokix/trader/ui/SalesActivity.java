package com.anokix.trader.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.PosSalesData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Sales — review sales performance + receipts ({@code api/trader/pos/sales}). Shows a
 * single date-range filter (defaults to Monday-this-week → today), a KPI summary, the
 * receipts list (with payment-type badges + dividers) and pagination.
 */
public class SalesActivity extends AppCompatActivity {

    private static final int PER_PAGE = 10;
    private static final String[] METHOD_KEYS = {"cash", "wallet", "card", "qr", "other"};
    private static final String[] METHOD_LABELS = {"Cash", "Wallet", "Card", "QR", "Other"};

    private final SimpleDateFormat inFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
    private final SimpleDateFormat outDate = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat outTime = new SimpleDateFormat("HH:mm", Locale.US);

    // Range stored as API strings ("yyyy-MM-dd", UTC) — matches the Orders screen.
    private String dateFrom, dateTo;
    private int page = 1;
    private int totalPages = 1;
    private int totalCount = 0;

    private TextView dateFromView, dateToView;
    private TextView kpiTotal, kpiTxn, kpiAvg, kpiItems;
    private TextView paginationLabel;
    private ImageButton prevBtn, nextBtn;
    private ProgressBar progress;
    private TextView empty;
    private SalesAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Default range: Monday this week → today.
        Calendar cal = Calendar.getInstance();
        dateTo = apiDate(cal.getTimeInMillis());
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int diff = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        cal.add(Calendar.DAY_OF_MONTH, diff);
        dateFrom = apiDate(cal.getTimeInMillis());

        dateFromView = findViewById(R.id.dateFrom);
        dateToView = findViewById(R.id.dateTo);
        paginationLabel = findViewById(R.id.paginationLabel);
        prevBtn = findViewById(R.id.btnPrevPage);
        nextBtn = findViewById(R.id.btnNextPage);
        progress = findViewById(R.id.salesProgress);
        empty = findViewById(R.id.salesEmpty);

        bindKpi(R.id.kpiTotalSales, "💰", R.color.success_bg, R.string.total_sales);
        bindKpi(R.id.kpiTransactions, "🧾", R.color.info_bg, R.string.transactions);
        bindKpi(R.id.kpiAverage, "📈", R.color.warning_bg, R.string.average_sale);
        bindKpi(R.id.kpiItemsSold, "📦", R.color.purple_light, R.string.items_sold);
        kpiTotal = findViewById(R.id.kpiTotalSales).findViewById(R.id.kpiValue);
        kpiTxn = findViewById(R.id.kpiTransactions).findViewById(R.id.kpiValue);
        kpiAvg = findViewById(R.id.kpiAverage).findViewById(R.id.kpiValue);
        kpiItems = findViewById(R.id.kpiItemsSold).findViewById(R.id.kpiValue);

        RecyclerView list = findViewById(R.id.salesList);
        list.setLayoutManager(new LinearLayoutManager(this));
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        divider.setDividerColor(ContextCompat.getColor(this, R.color.divider));
        int inset = Math.round(16 * getResources().getDisplayMetrics().density);
        divider.setDividerInsetStart(inset);
        divider.setDividerInsetEnd(inset);
        divider.setLastItemDecorated(false);
        list.addItemDecoration(divider);
        adapter = new SalesAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.dateRangePill).setOnClickListener(v -> showDateRangePicker());
        prevBtn.setOnClickListener(v -> { if (page > 1) { page--; load(); } });
        nextBtn.setOnClickListener(v -> { if (page < totalPages) { page++; load(); } });

        updateDateLabels();
        load();
    }

    private void bindKpi(int includeId, String emoji, int tintColor, int labelRes) {
        View card = findViewById(includeId);
        TextView icon = card.findViewById(R.id.kpiIcon);
        icon.setText(emoji);
        icon.setBackgroundTintList(ContextCompat.getColorStateList(this, tintColor));
        ((TextView) card.findViewById(R.id.kpiLabel)).setText(labelRes);
    }

    /** Single picker for both start + end dates (matches the Orders screen). */
    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.date_range)
                        .setSelection(new Pair<>(millisOf(dateFrom), millisOf(dateTo)))
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            if (selection.first != null) dateFrom = apiDate(selection.first);
            if (selection.second != null) dateTo = apiDate(selection.second);
            updateDateLabels();
            page = 1;
            load();
        });
        picker.show(getSupportFragmentManager(), "sales_date_range");
    }

    private void updateDateLabels() {
        dateFromView.setText(displayDate(dateFrom));
        dateToView.setText(displayDate(dateTo));
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        ApiClient.get(this).getPosSales(dateFrom, dateTo, page, PER_PAGE, new ApiCallback<PosSalesData>() {
            @Override
            public void onSuccess(PosSalesData data) {
                progress.setVisibility(View.GONE);
                if (data != null && data.summary != null) {
                    kpiTotal.setText(money(data.summary.totalSales));
                    kpiTxn.setText(String.valueOf(data.summary.transactions));
                    kpiAvg.setText(money(data.summary.averageSale));
                    kpiItems.setText(String.valueOf(data.summary.itemsSold));
                }
                if (data != null && data.pagination != null) {
                    page = data.pagination.page;
                    totalPages = Math.max(1, data.pagination.totalPages);
                    totalCount = data.pagination.total;
                } else {
                    totalPages = 1;
                    totalCount = 0;
                }
                List<PosSalesData.SaleRow> rows = data != null && data.sales != null
                        ? data.sales : new ArrayList<>();
                adapter.setItems(rows);
                empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                updatePagination(rows.size());
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                adapter.setItems(new ArrayList<>());
                empty.setVisibility(View.VISIBLE);
                updatePagination(0);
                Toast.makeText(SalesActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePagination(int shownCount) {
        if (totalCount == 0) {
            paginationLabel.setText(getString(R.string.sales_showing_none));
        } else {
            int start = (page - 1) * PER_PAGE + 1;
            int end = start + shownCount - 1;
            paginationLabel.setText(getString(R.string.sales_showing, start, end, totalCount));
        }
        prevBtn.setEnabled(page > 1);
        prevBtn.setAlpha(page > 1 ? 1f : 0.4f);
        nextBtn.setEnabled(page < totalPages);
        nextBtn.setAlpha(page < totalPages ? 1f : 0.4f);
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    private String labelForKey(String key) {
        if (key != null) {
            for (int i = 0; i < METHOD_KEYS.length; i++) {
                if (METHOD_KEYS[i].equalsIgnoreCase(key)) {
                    return METHOD_LABELS[i];
                }
            }
        }
        return key == null ? "" : key;
    }

    private class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.VH> {
        private List<PosSalesData.SaleRow> items = new ArrayList<>();

        void setItems(List<PosSalesData.SaleRow> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sale_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PosSalesData.SaleRow row = items.get(position);
            h.number.setText(row.saleNumber);
            h.dateTime.setText(formatDateTime(row.createdAt));
            h.amount.setText(money(row.totalAmount));

            h.payment.setText(labelForKey(row.paymentMethod));
            String key = row.paymentMethod == null ? "" : row.paymentMethod.toLowerCase(Locale.US);
            int badgeBg, badgeColor;
            if ("cash".equals(key)) {
                badgeBg = R.drawable.bg_badge_outline_success;
                badgeColor = R.color.success;
            } else if ("wallet".equals(key)) {
                badgeBg = R.drawable.bg_badge_outline_warning;
                badgeColor = R.color.warning;
            } else {
                badgeBg = R.drawable.bg_badge_outline_info;
                badgeColor = R.color.info;
            }
            h.payment.setBackgroundResource(badgeBg);
            h.payment.setTextColor(ContextCompat.getColor(SalesActivity.this, badgeColor));

            String unit = row.itemCount == 1 ? "item" : "items";
            h.items.setText(row.itemCount + " " + unit);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView number, dateTime, amount, payment, items;

            VH(@NonNull View v) {
                super(v);
                number = v.findViewById(R.id.saleNumber);
                dateTime = v.findViewById(R.id.saleDateTime);
                amount = v.findViewById(R.id.saleAmount);
                payment = v.findViewById(R.id.salePayment);
                items = v.findViewById(R.id.saleItems);
            }
        }
    }

    /** "26/06/2026 11:22:23" → "26 Jun 2026 · 11:22"; falls back to the raw value. */
    private String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Date d = inFmt.parse(raw);
            if (d != null) {
                return outDate.format(d) + " · " + outTime.format(d);
            }
        } catch (ParseException ignored) {
        }
        return raw;
    }

    // ---- Date helpers (UTC, matches the Orders screen) -------------------

    private static String apiDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(millis);
    }

    private static long millisOf(String raw) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(raw);
            if (d != null) return d.getTime();
        } catch (Exception ignored) {
        }
        return MaterialDatePicker.todayInUtcMilliseconds();
    }

    /** "yyyy-MM-dd" → "28 May 2026". */
    private static String displayDate(String raw) {
        if (raw == null) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat out = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            out.setTimeZone(TimeZone.getTimeZone("UTC"));
            return out.format(in.parse(raw));
        } catch (Exception e) {
            return raw;
        }
    }
}
