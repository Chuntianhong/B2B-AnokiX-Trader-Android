package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.FinancesData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Finances screen — the trader's anokiX wallet ledger (GET api/trader/finances).
 * Shows a KPI strip (from the API summary + a client-computed Failed/Reversed
 * total) and the transaction list. The endpoint takes no query params, so the
 * search box, Status / Type / Payment-Method dropdowns and the date range all
 * filter the loaded list locally. Each row can be opened (View → detail sheet)
 * or actioned via the ⋮ menu (Download PDF — placeholder, no endpoint yet).
 */
public class FinancesActivity extends AppCompatActivity {

    // Filter option keys/labels ------------------------------------------------
    private static final String[] STATUS_KEYS   = {"all", "paid", "pending", "overdue", "partial"};
    private static final String[] STATUS_LABELS = {"All Statuses", "Paid", "Pending", "Overdue", "Partial"};
    private static final String[] TYPE_KEYS     = {"all", "invoice", "payment", "payout", "credit_note"};
    private static final String[] TYPE_LABELS   = {"All Types", "Invoice", "Payment", "Payout", "Credit Note"};
    private static final String[] METHOD_KEYS   = {"all", "eft", "card", "cash", "credit"};
    private static final String[] METHOD_LABELS = {"All Methods", "EFT", "Card", "Cash", "Credit"};

    private final List<FinancesData.Transaction> all = new ArrayList<>();
    private final List<FinancesData.Transaction> filtered = new ArrayList<>();
    private FinancesData.Summary summary;
    private TxnAdapter adapter;

    // Filter state
    private String search = "";
    private String statusFilter = "all";
    private String typeFilter = "all";
    private String methodFilter = "all";
    private String dateFrom, dateTo; // "yyyy-MM-dd", null = no range

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading, emptyView;
    private TextView statusLabel, typeLabel, methodLabel, dateRangeLabel, dateClear, resultCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finances);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);
        statusLabel = findViewById(R.id.statusLabel);
        typeLabel = findViewById(R.id.typeLabel);
        methodLabel = findViewById(R.id.methodLabel);
        dateRangeLabel = findViewById(R.id.dateRangeLabel);
        dateClear = findViewById(R.id.dateClear);
        resultCount = findViewById(R.id.resultCount);

        list = findViewById(R.id.txnList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TxnAdapter();
        list.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        setupKpiIcons();
        setupSearch();
        setupFilters();

        load(true);
    }

    // ---- KPI cards -----------------------------------------------------------

    /** Fix the static icon/tint of each KPI card once (values are bound after load). */
    private void setupKpiIcons() {
        styleKpi(R.id.kpiSpent,      R.drawable.ic_finances,        R.color.purple_primary, R.color.purple_light);
        styleKpi(R.id.kpiCommission, R.drawable.ic_finance_report,  R.color.warning,        R.color.warning_bg);
        styleKpi(R.id.kpiTotalPaid,  R.drawable.ic_grv_total,       R.color.success,        R.color.success_bg);
        styleKpi(R.id.kpiPending,    R.drawable.ic_clock,           R.color.info,           R.color.info_bg);
        styleKpi(R.id.kpiFailed,     R.drawable.ic_warning_box,     R.color.danger,         R.color.danger_bg);
    }

    private void styleKpi(int includeId, @DrawableRes int icon, @ColorRes int tint, @ColorRes int bg) {
        View card = findViewById(includeId);
        ImageView iv = card.findViewById(R.id.kpiIcon);
        iv.setImageResource(icon);
        iv.setImageTintList(ContextCompat.getColorStateList(this, tint));
        card.findViewById(R.id.kpiIconTile)
                .setBackgroundTintList(ContextCompat.getColorStateList(this, bg));
    }

    private void bindKpi(int includeId, String value, String label, String sub) {
        View card = findViewById(includeId);
        ((TextView) card.findViewById(R.id.kpiValue)).setText(value);
        ((TextView) card.findViewById(R.id.kpiLabel)).setText(label);
        ((TextView) card.findViewById(R.id.kpiSub)).setText(sub);
    }

    private void bindSummary() {
        double spentMonth = summary != null ? summary.spent_month : 0;
        double commission = summary != null ? summary.commission_month : 0;
        double totalPaid = summary != null ? summary.spent : 0;
        double pending = summary != null ? summary.pending : 0;
        int count = summary != null ? summary.count : all.size();

        // Failed/Reversed isn't in the summary — compute it from the list.
        double failed = 0;
        int failedCount = 0;
        for (FinancesData.Transaction t : all) {
            String s = t.status == null ? "" : t.status.toLowerCase(Locale.US);
            if (s.equals("failed") || s.equals("reversed")) {
                failed += Math.abs(t.amount);
                failedCount++;
            }
        }

        bindKpi(R.id.kpiSpent, money(spentMonth), "Spent (This Month)", "Paid this month");
        bindKpi(R.id.kpiCommission, money(commission), "Commission (This Month)", "Platform fee this month");
        bindKpi(R.id.kpiTotalPaid, money(totalPaid), "Total Paid",
                count + (count == 1 ? " transaction" : " transactions"));
        bindKpi(R.id.kpiPending, money(pending), "Pending", "Awaiting settlement");
        bindKpi(R.id.kpiFailed, money(failed), "Failed / Reversed",
                failedCount == 0 ? "None" : failedCount + (failedCount == 1 ? " item" : " items"));
    }

    // ---- Filters -------------------------------------------------------------

    private void setupSearch() {
        TextView input = findViewById(R.id.searchInput);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                search = s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
        });
    }

    private void setupFilters() {
        findViewById(R.id.statusDropdown).setOnClickListener(v ->
                showFilterMenu(v, STATUS_KEYS, STATUS_LABELS, statusFilter, key -> {
                    statusFilter = key;
                    statusLabel.setText(labelFor(key, STATUS_KEYS, STATUS_LABELS, "Status"));
                    setPillActive(R.id.statusDropdown, R.id.statusLabel, R.id.statusChevron, !"all".equals(key));
                    applyFilters();
                }));
        findViewById(R.id.typeDropdown).setOnClickListener(v ->
                showFilterMenu(v, TYPE_KEYS, TYPE_LABELS, typeFilter, key -> {
                    typeFilter = key;
                    typeLabel.setText(labelFor(key, TYPE_KEYS, TYPE_LABELS, "Type"));
                    setPillActive(R.id.typeDropdown, R.id.typeLabel, R.id.typeChevron, !"all".equals(key));
                    applyFilters();
                }));
        findViewById(R.id.methodDropdown).setOnClickListener(v ->
                showFilterMenu(v, METHOD_KEYS, METHOD_LABELS, methodFilter, key -> {
                    methodFilter = key;
                    methodLabel.setText(labelFor(key, METHOD_KEYS, METHOD_LABELS, "Method"));
                    setPillActive(R.id.methodDropdown, R.id.methodLabel, R.id.methodChevron, !"all".equals(key));
                    applyFilters();
                }));

        findViewById(R.id.dateRangePill).setOnClickListener(v -> showDateRangePicker());
        dateClear.setOnClickListener(v -> {
            dateFrom = null;
            dateTo = null;
            dateRangeLabel.setText("Date range");
            dateClear.setVisibility(View.GONE);
            findViewById(R.id.dateRangePill).setBackgroundResource(R.drawable.bg_filter_pill);
            applyFilters();
        });
    }

    /** Toggle a filter pill between the neutral (white) and active (purple) look. */
    private void setPillActive(int pillId, int labelId, int chevronId, boolean active) {
        findViewById(pillId).setBackgroundResource(
                active ? R.drawable.bg_filter_pill_active : R.drawable.bg_filter_pill);
        ((TextView) findViewById(labelId)).setTextColor(ContextCompat.getColor(this,
                active ? R.color.purple_primary : R.color.text_primary));
        ((ImageView) findViewById(chevronId)).setImageTintList(ContextCompat.getColorStateList(this,
                active ? R.color.purple_primary : R.color.text_secondary));
    }

    private interface FilterPick { void onPick(String key); }

    private void showFilterMenu(View anchor, String[] keys, String[] labels, String selected, FilterPick cb) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int i = 0; i < keys.length; i++) {
            menu.getMenu().add(0, i, i, labels[i]).setCheckable(true).setChecked(keys[i].equals(selected));
        }
        menu.setOnMenuItemClickListener(item -> {
            cb.onPick(keys[item.getItemId()]);
            return true;
        });
        menu.show();
    }

    /** For a non-"all" key, show its short label on the pill; otherwise the default header. */
    private static String labelFor(String key, String[] keys, String[] labels, String fallback) {
        if ("all".equals(key)) return fallback;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) return labels[i];
        }
        return fallback;
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> b =
                MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select date range");
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = b.build();
        picker.addOnPositiveButtonClickListener(sel -> {
            if (sel == null) return;
            if (sel.first != null) dateFrom = apiDate(sel.first);
            if (sel.second != null) dateTo = apiDate(sel.second);
            dateRangeLabel.setText(displayDate(dateFrom) + "  →  " + displayDate(dateTo));
            dateClear.setVisibility(View.VISIBLE);
            findViewById(R.id.dateRangePill).setBackgroundResource(R.drawable.bg_filter_pill_active);
            applyFilters();
        });
        picker.show(getSupportFragmentManager(), "finance_date_range");
    }

    /** Rebuild {@link #filtered} from {@link #all} using the current filter state. */
    private void applyFilters() {
        filtered.clear();
        for (FinancesData.Transaction t : all) {
            if (!matchesSearch(t)) continue;
            if (!"all".equals(statusFilter) && !statusFilter.equalsIgnoreCase(nz(t.status))) continue;
            if (!"all".equals(typeFilter) && !typeFilter.equals(t.typeGroup())) continue;
            // Payment method has no field in the API yet — a specific pick only matches
            // once the backend supplies payment_method (methodKey() is null until then).
            if (!"all".equals(methodFilter) && !methodFilter.equals(t.methodKey())) continue;
            if (!withinDateRange(t)) continue;
            filtered.add(t);
        }
        adapter.notifyDataSetChanged();
        int n = filtered.size();
        resultCount.setText("Showing " + n + (n == 1 ? " transaction" : " transactions"));
        boolean empty = filtered.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean matchesSearch(FinancesData.Transaction t) {
        if (search.isEmpty()) return true;
        return contains(t.reference) || contains(t.order_number)
                || contains(t.counterparty) || contains(t.description)
                || contains(t.counterparty_phone);
    }

    private boolean contains(String field) {
        return field != null && field.toLowerCase(Locale.US).contains(search);
    }

    private boolean withinDateRange(FinancesData.Transaction t) {
        if (dateFrom == null && dateTo == null) return true;
        String day = dayOf(t.created_at);
        if (day == null) return false;
        if (dateFrom != null && day.compareTo(dateFrom) < 0) return false;
        if (dateTo != null && day.compareTo(dateTo) > 0) return false;
        return true;
    }

    // ---- Data ----------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) {
            loading.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
        ApiClient.get(this).getFinances(new ApiCallback<FinancesData>() {
            @Override
            public void onSuccess(FinancesData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                all.clear();
                if (data != null && data.transactions != null) all.addAll(data.transactions);
                summary = data != null ? data.summary : null;
                bindSummary();
                applyFilters();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(FinancesActivity.this,
                        message != null ? message : "Failed to load finances.", Toast.LENGTH_SHORT).show();
                bindSummary();
                applyFilters();
            }
        });
    }

    // ---- List adapter --------------------------------------------------------

    private class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_finance_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            FinancesData.Transaction t = filtered.get(position);

            bindTypeBadge(h.type, t);
            bindStatusBadge(h.status, t.status);

            String name = nzDash(t.counterparty);
            h.avatar.setText(initial(name));
            h.counterparty.setText(name);
            h.phone.setText(nzDash(t.counterparty_phone));
            h.phone.setVisibility(t.counterparty_phone == null || t.counterparty_phone.isEmpty()
                    ? View.GONE : View.VISIBLE);

            h.amount.setText(signedMoney(t.amount));
            h.amount.setTextColor(ContextCompat.getColor(FinancesActivity.this,
                    t.amount < 0 ? R.color.danger : R.color.success));

            h.description.setText(nzDash(t.description));
            h.meta.setText(shortRef(t.reference) + " · " + formatDate(t.created_at));

            h.btnView.setOnClickListener(v -> showDetail(t));
            h.btnMore.setOnClickListener(v -> showRowMenu(v, t));
            h.itemView.setOnClickListener(v -> showDetail(t));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView type, status, avatar, counterparty, phone, amount, description, meta;
            final MaterialButton btnView;
            final ImageButton btnMore;

            VH(@NonNull View v) {
                super(v);
                type = v.findViewById(R.id.txnType);
                status = v.findViewById(R.id.txnStatus);
                avatar = v.findViewById(R.id.txnAvatar);
                counterparty = v.findViewById(R.id.txnCounterparty);
                phone = v.findViewById(R.id.txnPhone);
                amount = v.findViewById(R.id.txnAmount);
                description = v.findViewById(R.id.txnDescription);
                meta = v.findViewById(R.id.txnMeta);
                btnView = v.findViewById(R.id.btnView);
                btnMore = v.findViewById(R.id.btnMore);
            }
        }
    }

    private void showRowMenu(View anchor, FinancesData.Transaction t) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, "View details");
        menu.getMenu().add(0, 1, 1, "Download PDF");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                showDetail(t);
            } else {
                Toast.makeText(this, "PDF download coming soon.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }

    // ---- Detail bottom sheet -------------------------------------------------

    private void showDetail(FinancesData.Transaction t) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.sheet_finance_detail, null, false);

        bindTypeBadge(content.findViewById(R.id.detailType), t);
        bindStatusBadge(content.findViewById(R.id.detailStatus), t.status);

        TextView amount = content.findViewById(R.id.detailAmount);
        amount.setText(signedMoney(t.amount));
        amount.setTextColor(ContextCompat.getColor(this, t.amount < 0 ? R.color.danger : R.color.success));

        ((TextView) content.findViewById(R.id.detailDescription)).setText(nzDash(t.description));

        ViewGroup rows = content.findViewById(R.id.detailRows);
        addRow(rows, "Reference", nzDash(t.reference));
        addRow(rows, "Order", nzDash(t.order_number));
        addRow(rows, "Paid to", nzDash(t.counterparty));
        if (t.counterparty_phone != null && !t.counterparty_phone.isEmpty()) {
            addRow(rows, "Phone", t.counterparty_phone);
        }
        addRow(rows, "Method", methodDisplay(t));
        addRow(rows, "Date", formatDateTime(t.created_at));

        content.findViewById(R.id.btnDownloadPdf).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "PDF download coming soon.", Toast.LENGTH_SHORT).show();
        });

        dialog.setContentView(content);
        dialog.show();
    }

    private void addRow(ViewGroup parent, String label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.sheet_finance_detail_row, parent, false);
        ((TextView) row.findViewById(R.id.rowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.rowValue)).setText(value);
        parent.addView(row);
    }

    // ---- Badges --------------------------------------------------------------

    private void bindTypeBadge(TextView badge, FinancesData.Transaction t) {
        String group = t.typeGroup();
        int bg, fg;
        switch (group) {
            case "invoice":     bg = R.drawable.bg_badge_info;    fg = R.color.info;    break;
            case "payout":      bg = R.drawable.bg_badge_purple;  fg = R.color.purple_primary; break;
            case "credit_note": bg = R.drawable.bg_badge_warning; fg = R.color.warning; break;
            default:            bg = R.drawable.bg_badge_success; fg = R.color.success; break;
        }
        badge.setText(t.typeLabel());
        badge.setBackgroundResource(bg);
        badge.setTextColor(ContextCompat.getColor(this, fg));
    }

    private void bindStatusBadge(TextView badge, String status) {
        String s = status == null ? "" : status.toLowerCase(Locale.US);
        String label;
        int bg, fg;
        switch (s) {
            case "paid":     label = "Paid";     bg = R.drawable.bg_badge_success; fg = R.color.success; break;
            case "pending":  label = "Pending";  bg = R.drawable.bg_badge_warning; fg = R.color.warning; break;
            case "overdue":  label = "Overdue";  bg = R.drawable.bg_badge_danger;  fg = R.color.danger;  break;
            case "partial":  label = "Partial";  bg = R.drawable.bg_badge_info;    fg = R.color.info;    break;
            case "failed":   label = "Failed";   bg = R.drawable.bg_badge_danger;  fg = R.color.danger;  break;
            case "reversed": label = "Reversed"; bg = R.drawable.bg_badge_muted;   fg = R.color.text_secondary; break;
            default:         label = s.isEmpty() ? "—" : capitalize(s); bg = R.drawable.bg_badge_muted; fg = R.color.text_secondary; break;
        }
        badge.setText(label);
        badge.setBackgroundResource(bg);
        badge.setTextColor(ContextCompat.getColor(this, fg));
    }

    private String methodDisplay(FinancesData.Transaction t) {
        if (t.payment_method != null && !t.payment_method.isEmpty()) return t.payment_method;
        return "anokiX Wallet (IMB)";
    }

    // ---- Helpers -------------------------------------------------------------

    private static String nz(String s) { return s == null ? "" : s; }

    private static String nzDash(String s) { return s == null || s.isEmpty() ? "—" : s; }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    private static String initial(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.US);
    }

    /** First 8 chars of a UUID reference (matches the web portal's truncation). */
    private static String shortRef(String ref) {
        if (ref == null || ref.isEmpty()) return "—";
        return ref.length() > 10 ? ref.substring(0, 8) + "…" : ref;
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    /** Signed money for amounts (leading "-" before the R for negatives). */
    private String signedMoney(double value) {
        return (value < 0 ? "-" : "") + String.format(Locale.US, "R%,.2f", Math.abs(value));
    }

    // Dates — the API sends "yyyy-MM-dd HH:mm:ss".

    private static Date parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** created_at → "yyyy-MM-dd" for range comparison (null on failure). */
    private static String dayOf(String raw) {
        Date d = parse(raw);
        if (d == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    /** created_at → "15 Jul 2026" (falls back to the raw value). */
    private static String formatDate(String raw) {
        Date d = parse(raw);
        if (d == null) return raw == null || raw.isEmpty() ? "—" : raw;
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
    }

    /** created_at → "15 Jul 2026, 13:58". */
    private static String formatDateTime(String raw) {
        Date d = parse(raw);
        if (d == null) return raw == null || raw.isEmpty() ? "—" : raw;
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
    }

    /** picker millis (UTC midnight) → "yyyy-MM-dd". */
    private static String apiDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(millis);
    }

    /** "yyyy-MM-dd" → "15 Jul 2026". */
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
