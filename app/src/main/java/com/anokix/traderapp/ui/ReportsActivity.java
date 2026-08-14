package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.Http;
import com.anokix.traderapp.network.dto.ReportMutationData;
import com.anokix.traderapp.network.dto.ReportsData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Reports — GET api/trader/reports, one call for the KPI summary, the catalogue of
 * report types the trader may build and the reports already generated.
 *
 * From here a report can be generated (POST .../generate), downloaded (the download
 * service on its own port), regenerated for the same type, or deleted (POST .../delete).
 * Searching, the category tabs and the period filter all run on the device — the
 * endpoint takes no query params.
 */
public class ReportsActivity extends AppCompatActivity {

    /**
     * Category tabs, in the order the trader portal shows them. Each tab matches a set
     * of server {@code category} values: the API groups Best Sellers under "products"
     * (a till/POS report) and Purchases under "orders" (bought on the marketplace).
     */
    private static final String[] TAB_LABELS = {
            "All Reports", "Sales", "POS", "Inventory", "MarketPlace", "Wallet", "Rewards"};
    private static final String[][] TAB_CATEGORIES = {
            {},                                 // All Reports
            {"sales"},
            {"products", "pos"},
            {"inventory"},
            {"orders", "marketplace"},
            {"wallet"},
            {"rewards"}};

    private final List<ReportsData.Report> all = new ArrayList<>();
    private final List<ReportsData.Report> filtered = new ArrayList<>();
    private final List<ReportsData.Available> available = new ArrayList<>();

    private ReportAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading;
    private View emptyView;
    private TextView emptyTitle, emptyBody, resultCount;
    private TextView statGenerated, statDownloads, statThisMonth, statFailed;
    private TextView dateRangeLabel, dateClear;

    private String search = "";
    private int tabIndex = 0;
    /** Period filter, "yyyy-MM-dd"; null when the trader has not narrowed the list. */
    private String filterFrom, filterTo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statGenerated = findViewById(R.id.statGenerated);
        statDownloads = findViewById(R.id.statDownloads);
        statThisMonth = findViewById(R.id.statThisMonth);
        statFailed = findViewById(R.id.statFailed);
        resultCount = findViewById(R.id.resultCount);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyBody = findViewById(R.id.emptyBody);
        dateRangeLabel = findViewById(R.id.dateRangeLabel);
        dateClear = findViewById(R.id.dateClear);

        list = findViewById(R.id.reportList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter();
        list.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        findViewById(R.id.generateReportButton).setOnClickListener(v -> showGenerateSheet(null));

        setupTabs();
        setupSearch();
        setupPeriodFilter();

        load(true);
    }

    // ---- Filters ---------------------------------------------------------

    private void setupTabs() {
        ChipGroup group = findViewById(R.id.reportCategoryChips);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int index = i;
            Chip chip = new Chip(this);
            chip.setText(TAB_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setCheckedIconVisible(false);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_selector));
            chip.setOnClickListener(v -> {
                chip.setChecked(true);      // keep a tab always selected
                tabIndex = index;
                applyFilters();
            });
            group.addView(chip);
        }
    }

    private void setupSearch() {
        EditText input = findViewById(R.id.searchInput);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                search = s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
        });
    }

    private void setupPeriodFilter() {
        findViewById(R.id.dateRangePill).setOnClickListener(v -> showPeriodFilterPicker());
        dateClear.setOnClickListener(v -> {
            filterFrom = null;
            filterTo = null;
            dateRangeLabel.setText(R.string.reports_period_filter);
            dateClear.setVisibility(View.GONE);
            findViewById(R.id.dateRangePill).setBackgroundResource(R.drawable.bg_filter_pill);
            applyFilters();
        });
    }

    private void showPeriodFilterPicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.reports_period_filter)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            if (selection.first != null) filterFrom = apiDate(selection.first);
            if (selection.second != null) filterTo = apiDate(selection.second);
            dateRangeLabel.setText(rangeLabel(filterFrom, filterTo));
            dateClear.setVisibility(View.VISIBLE);
            findViewById(R.id.dateRangePill).setBackgroundResource(R.drawable.bg_filter_pill_active);
            applyFilters();
        });
        picker.show(getSupportFragmentManager(), "reports_period_filter");
    }

    /** Rebuild {@link #filtered} from {@link #all} using the tab, search and period. */
    private void applyFilters() {
        filtered.clear();
        for (ReportsData.Report r : all) {
            if (!matchesTab(r)) continue;
            if (!matchesSearch(r)) continue;
            if (!matchesPeriod(r)) continue;
            filtered.add(r);
        }
        adapter.notifyDataSetChanged();

        int n = filtered.size();
        resultCount.setText(getResources().getQuantityString(R.plurals.reports_showing, n, n));

        boolean empty = filtered.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            boolean unfiltered = all.isEmpty();
            emptyTitle.setText(unfiltered ? R.string.reports_empty_title : R.string.reports_no_matches_title);
            emptyBody.setText(unfiltered ? R.string.reports_empty_body : R.string.reports_no_matches_body);
        }
    }

    private boolean matchesTab(ReportsData.Report r) {
        String[] categories = TAB_CATEGORIES[tabIndex];
        if (categories.length == 0) return true;
        String actual = r.category == null ? "" : r.category.toLowerCase(Locale.US);
        for (String c : categories) {
            if (c.equals(actual)) return true;
        }
        return false;
    }

    private boolean matchesSearch(ReportsData.Report r) {
        if (search.isEmpty()) return true;
        return contains(r.name) || contains(r.category) || contains(r.period_from) || contains(r.period_to);
    }

    private boolean contains(String field) {
        return field != null && field.toLowerCase(Locale.US).contains(search);
    }

    /** A report matches when its period overlaps the filter window. */
    private boolean matchesPeriod(ReportsData.Report r) {
        if (filterFrom == null && filterTo == null) return true;
        String from = r.period_from, to = r.period_to;
        if (from == null || from.isEmpty()) from = to;
        if (to == null || to.isEmpty()) to = from;
        if (from == null || to == null) return false;
        if (filterFrom != null && to.compareTo(filterFrom) < 0) return false;
        if (filterTo != null && from.compareTo(filterTo) > 0) return false;
        return true;
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) {
            loading.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
        ApiClient.get(this).getReports(new ApiCallback<ReportsData>() {
            @Override
            public void onSuccess(ReportsData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                all.clear();
                available.clear();
                if (data != null) {
                    if (data.reports != null) all.addAll(data.reports);
                    if (data.available != null) available.addAll(data.available);
                    bindSummary(data.summary);
                }
                applyFilters();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                applyFilters();
            }
        });
    }

    private void bindSummary(ReportsData.Summary s) {
        statGenerated.setText(String.valueOf(s == null ? 0 : s.generated));
        statDownloads.setText(String.valueOf(s == null ? 0 : s.downloads));
        statThisMonth.setText(String.valueOf(s == null ? 0 : s.this_month));
        statFailed.setText(String.valueOf(s == null ? 0 : s.failed));
    }

    // ---- List ------------------------------------------------------------

    private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ReportsData.Report r = filtered.get(position);

            h.name.setText(safe(r.name));
            h.category.setText(safe(r.category));
            h.meta.setText(metaLine(r));
            h.period.setText(periodLabel(r));
            h.generated.setText(getString(R.string.report_generated_at, dateTime(r.created_at)));

            bindStatusBadge(h.status, r.status);

            boolean failed = "failed".equalsIgnoreCase(safe(r.status));
            boolean hasError = failed && r.error != null && !r.error.isEmpty();
            h.error.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) h.error.setText(r.error);

            h.downloads.setText(r.downloads > 0
                    ? getResources().getQuantityString(R.plurals.report_downloads, r.downloads, r.downloads)
                    : getString(R.string.report_not_downloaded));

            // Only a finished report has a file behind it.
            h.btnDownload.setEnabled(r.isReady());
            h.btnDownload.setAlpha(r.isReady() ? 1f : 0.4f);
            h.btnDownload.setOnClickListener(v -> download(r));
            h.btnMore.setOnClickListener(v -> showRowMenu(v, r));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name, status, category, meta, period, generated, error, downloads;
            final MaterialButton btnDownload;
            final ImageView btnMore;

            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.reportName);
                status = v.findViewById(R.id.reportStatus);
                category = v.findViewById(R.id.reportCategory);
                meta = v.findViewById(R.id.reportMeta);
                period = v.findViewById(R.id.reportPeriod);
                generated = v.findViewById(R.id.reportGenerated);
                error = v.findViewById(R.id.reportError);
                downloads = v.findViewById(R.id.reportDownloads);
                btnDownload = v.findViewById(R.id.btnDownload);
                btnMore = v.findViewById(R.id.btnMore);
            }
        }
    }

    private String metaLine(ReportsData.Report r) {
        String format = r.format == null || r.format.isEmpty() ? "CSV" : r.format.toUpperCase(Locale.US);
        String rows = getResources().getQuantityString(R.plurals.report_rows, r.row_count, r.row_count);
        return format + " · " + rows + " · " + r.sizeLabel();
    }

    private void bindStatusBadge(TextView badge, String status) {
        String label;
        int bg, fg;
        switch (safe(status).toLowerCase(Locale.US)) {
            case "ready":
                label = getString(R.string.report_status_ready);
                bg = R.drawable.bg_badge_success;
                fg = R.color.success;
                break;
            case "generating":
            case "pending":
            case "queued":
                label = getString(R.string.report_status_generating);
                bg = R.drawable.bg_badge_warning;
                fg = R.color.warning;
                break;
            case "failed":
                label = getString(R.string.report_status_failed);
                bg = R.drawable.bg_badge_danger;
                fg = R.color.danger;
                break;
            default:
                label = safe(status).isEmpty() ? "—" : status;
                bg = R.drawable.bg_badge_purple;
                fg = R.color.purple_primary;
                break;
        }
        badge.setText(label);
        badge.setBackgroundResource(bg);
        badge.setTextColor(ContextCompat.getColor(this, fg));
    }

    /** Row overflow: Generate again / Delete. */
    private void showRowMenu(View anchor, ReportsData.Report r) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.report_generate_again);
        menu.getMenu().add(0, 2, 1, R.string.delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showGenerateSheet(r);
            } else {
                confirmDelete(r);
            }
            return true;
        });
        menu.show();
    }

    // ---- Download --------------------------------------------------------

    private void download(ReportsData.Report r) {
        Toast.makeText(this, R.string.report_downloading, Toast.LENGTH_SHORT).show();
        ApiClient.get(this).downloadReport(r.id, new ApiCallback<Http.BinaryResult>() {
            @Override
            public void onSuccess(Http.BinaryResult download) {
                ReportFiles.saveAndOffer(ReportsActivity.this, download, r.fileName());
                // The server counts the download, so pull the fresh numbers back in.
                load(false);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Delete ----------------------------------------------------------

    private void confirmDelete(ReportsData.Report r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.report_delete_title)
                .setMessage(getString(R.string.report_delete_body, safe(r.name)))
                .setPositiveButton(R.string.delete, (d, w) -> delete(r))
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void delete(ReportsData.Report r) {
        ApiClient.get(this).deleteReport(r.id, new ApiCallback<ReportMutationData>() {
            @Override
            public void onSuccess(ReportMutationData data) {
                onSuccess(data, null);
            }

            @Override
            public void onSuccess(ReportMutationData data, String message) {
                Toast.makeText(ReportsActivity.this,
                        message == null || message.isEmpty()
                                ? getString(R.string.report_deleted) : message,
                        Toast.LENGTH_SHORT).show();
                all.remove(r);
                if (data != null && data.summary != null) {
                    bindSummary(data.summary);
                }
                applyFilters();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Generate sheet --------------------------------------------------

    /**
     * @param repeat when non-null the sheet opens pre-filled with that report's type and
     *               period — the "Generate again" row action.
     */
    private void showGenerateSheet(@Nullable ReportsData.Report repeat) {
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.reports_no_types, Toast.LENGTH_SHORT).show();
            return;
        }

        View sheet = getLayoutInflater().inflate(R.layout.sheet_generate_report, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        TextView pickerLabel = sheet.findViewById(R.id.reportPickerLabel);
        TextView about = sheet.findViewById(R.id.reportAbout);
        TextView periodLabel = sheet.findViewById(R.id.periodLabel);
        MaterialButton generate = sheet.findViewById(R.id.btnGenerate);

        // Selection state. Index -1 = nothing chosen yet.
        final int[] selected = {repeat == null ? -1 : indexOf(repeat)};
        // Default period: the last 30 days, matching the portal.
        final String[] period = defaultPeriod();
        if (repeat != null && repeat.period_from != null && repeat.period_to != null) {
            period[0] = repeat.period_from;
            period[1] = repeat.period_to;
        }

        Runnable renderReport = () -> {
            if (selected[0] < 0) {
                pickerLabel.setText(R.string.choose_a_report);
                pickerLabel.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
                about.setVisibility(View.GONE);
            } else {
                ReportsData.Available a = available.get(selected[0]);
                pickerLabel.setText(safe(a.name));
                pickerLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                boolean hasAbout = a.about != null && !a.about.isEmpty();
                about.setVisibility(hasAbout ? View.VISIBLE : View.GONE);
                about.setText(safe(a.about));
            }
        };
        Runnable renderPeriod = () -> periodLabel.setText(rangeLabel(period[0], period[1]));

        renderReport.run();
        renderPeriod.run();

        sheet.findViewById(R.id.reportPicker).setOnClickListener(v -> {
            CharSequence[] names = new CharSequence[available.size()];
            for (int i = 0; i < available.size(); i++) {
                names[i] = safe(available.get(i).name);
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.choose_a_report)
                    .setSingleChoiceItems(names, selected[0], (d, which) -> {
                        selected[0] = which;
                        renderReport.run();
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        sheet.findViewById(R.id.periodPicker).setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText(R.string.period_label)
                    .setSelection(new Pair<>(utcMillis(period[0]), utcMillis(period[1])))
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection == null) return;
                if (selection.first != null) period[0] = apiDate(selection.first);
                if (selection.second != null) period[1] = apiDate(selection.second);
                renderPeriod.run();
            });
            picker.show(getSupportFragmentManager(), "generate_report_period");
        });

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        generate.setOnClickListener(v -> {
            if (selected[0] < 0) {
                Toast.makeText(this, R.string.report_pick_type, Toast.LENGTH_SHORT).show();
                return;
            }
            submitGenerate(available.get(selected[0]).key, period[0], period[1], generate, dialog);
        });

        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
        dialog.show();
    }

    private void submitGenerate(String reportKey, String from, String to,
                                MaterialButton generate, BottomSheetDialog dialog) {
        generate.setEnabled(false);
        generate.setText(R.string.report_generating);
        ApiClient.get(this).generateReport(reportKey, from, to,
                new ApiCallback<ReportMutationData>() {
                    @Override
                    public void onSuccess(ReportMutationData data) {
                        onSuccess(data, null);
                    }

                    @Override
                    public void onSuccess(ReportMutationData data, String message) {
                        dialog.dismiss();
                        Toast.makeText(ReportsActivity.this,
                                message == null || message.isEmpty()
                                        ? getString(R.string.report_ready) : message,
                                Toast.LENGTH_LONG).show();
                        load(false);
                    }

                    @Override
                    public void onError(String message) {
                        generate.setEnabled(true);
                        generate.setText(R.string.generate);
                        Toast.makeText(ReportsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Position of a generated report's type in {@link #available}, or -1. */
    private int indexOf(ReportsData.Report r) {
        for (int i = 0; i < available.size(); i++) {
            ReportsData.Available a = available.get(i);
            if (a.name != null && a.name.equalsIgnoreCase(safe(r.name))) return i;
        }
        // Fall back to the category when the display name has since been reworded.
        for (int i = 0; i < available.size(); i++) {
            ReportsData.Available a = available.get(i);
            if (a.category != null && a.category.equalsIgnoreCase(safe(r.category))) return i;
        }
        return -1;
    }

    // ---- Dates -----------------------------------------------------------

    /** The last 30 days, inclusive — the period the portal opens with. */
    private static String[] defaultPeriod() {
        Calendar c = utcCalendar();
        String to = API.format(c.getTime());
        c.add(Calendar.DAY_OF_MONTH, -30);
        String from = API.format(c.getTime());
        return new String[]{from, to};
    }

    private static Calendar utcCalendar() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** MaterialDatePicker hands back UTC midnight millis; the API wants "yyyy-MM-dd". */
    private static String apiDate(long utcMillis) {
        return API.format(new Date(utcMillis));
    }

    /** Inverse of {@link #apiDate}, used to pre-select the picker. */
    private static long utcMillis(String apiDate) {
        try {
            Date d = API.parse(apiDate);
            if (d != null) return d.getTime();
        } catch (Exception ignored) {
            // fall through
        }
        return MaterialDatePicker.todayInUtcMilliseconds();
    }

    /** "2026-07-15" + "2026-08-14" → "15 Jul – 14 Aug 2026". */
    private static String rangeLabel(String from, String to) {
        Date a = parseQuietly(from);
        Date b = parseQuietly(to);
        if (a == null && b == null) return "—";
        if (a == null) return DISPLAY_FULL.format(b);
        if (b == null) return DISPLAY_FULL.format(a);

        Calendar ca = utcCalendar();
        ca.setTime(a);
        Calendar cb = utcCalendar();
        cb.setTime(b);
        String left = ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                ? DISPLAY_SHORT.format(a) : DISPLAY_FULL.format(a);
        return left + " – " + DISPLAY_FULL.format(b);
    }

    /** The period line on a report card. */
    private static String periodLabel(ReportsData.Report r) {
        return rangeLabel(r.period_from, r.period_to);
    }

    /** "2026-08-13 22:46:59" → "13 Aug 2026, 22:46". */
    private static String dateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw);
            if (d != null) return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
        } catch (Exception ignored) {
            // fall through to the raw value
        }
        return raw;
    }

    private static Date parseQuietly(String apiDate) {
        try {
            return API.parse(apiDate);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final SimpleDateFormat API = utcFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DISPLAY_SHORT = utcFormat("dd MMM");
    private static final SimpleDateFormat DISPLAY_FULL = utcFormat("dd MMM yyyy");

    private static SimpleDateFormat utcFormat(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    }
}
