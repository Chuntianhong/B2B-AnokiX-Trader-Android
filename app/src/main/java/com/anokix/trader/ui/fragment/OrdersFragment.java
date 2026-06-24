package com.anokix.trader.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.OrdersData;
import com.anokix.trader.ui.OrderDetailActivity;
import com.anokix.trader.ui.adapter.TraderOrderAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Trader Orders list — wired to GET /api/trader/orders. Supports keyword search,
 * a distributor filter (populated from the returned orders), a date range, status
 * tabs with live counts, and pagination.
 */
public class OrdersFragment extends Fragment {

    private static final String[] STATUS_LABELS = {
            "All Orders", "Pending", "Accepted", "Picking", "Packing",
            "Out for Delivery", "Delivered", "Cancelled"
    };
    private static final String[] STATUS_KEYS = {
            "all", "pending", "accepted", "picking", "packing",
            "out_for_delivery", "delivered", "cancelled"
    };
    private static final int PER_PAGE = 10;

    private ApiClient api;

    private final List<OrdersData.Order> orders = new ArrayList<>();
    private TraderOrderAdapter adapter;
    private final Chip[] statusChips = new Chip[STATUS_KEYS.length];

    // Filter state
    private String search = "";
    private String distributorId = null;          // null = All Distributors
    private String distributorName = null;
    private String dateFrom, dateTo;
    private String status = "all";
    private int page = 1;
    private int totalPages = 1;
    private int total = 0;

    /** Distributor options discovered from the loaded orders (id → display name). */
    private final Map<String, String> knownDistributors = new LinkedHashMap<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private RecyclerView list;
    private ProgressBar progress;
    private View emptyView, paginationBar, btnPrev, btnNext;
    private TextView distributorLabel, dateRangeLabel, paginationLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = ApiClient.get(requireContext());

        list = view.findViewById(R.id.ordersList);
        progress = view.findViewById(R.id.ordersProgress);
        emptyView = view.findViewById(R.id.ordersEmpty);
        paginationBar = view.findViewById(R.id.paginationBar);
        btnPrev = view.findViewById(R.id.btnPrevPage);
        btnNext = view.findViewById(R.id.btnNextPage);
        distributorLabel = view.findViewById(R.id.distributorLabel);
        dateRangeLabel = view.findViewById(R.id.dateRangeLabel);
        paginationLabel = view.findViewById(R.id.paginationLabel);

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TraderOrderAdapter(orders, "R");
        adapter.setOnOrderClick(this::openOrderDetail);
        list.setAdapter(adapter);

        buildStatusChips(view);
        setupSearch(view);
        setupDistributorDropdown(view);
        setupDateRange(view);

        btnPrev.setOnClickListener(v -> { if (page > 1) { page--; load(); } });
        btnNext.setOnClickListener(v -> { if (page < totalPages) { page++; load(); } });

        // Default range: last 30 days (matches the web portal's first call).
        Calendar cal = Calendar.getInstance();
        dateTo = apiDate(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_MONTH, -30);
        dateFrom = apiDate(cal.getTimeInMillis());
        updateDateLabel();

        load();
    }

    // ---- Filters ---------------------------------------------------------

    private void buildStatusChips(View view) {
        ChipGroup group = view.findViewById(R.id.statusChips);
        for (int i = 0; i < STATUS_LABELS.length; i++) {
            final String key = STATUS_KEYS[i];
            Chip chip = new Chip(requireContext());
            chip.setText(STATUS_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector));
            chip.setOnClickListener(v -> {
                status = key;
                page = 1;
                load();
            });
            statusChips[i] = chip;
            group.addView(chip);
        }
    }

    private void setupSearch(View view) {
        TextView input = view.findViewById(R.id.searchInput);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    search = s.toString().trim();
                    page = 1;
                    load();
                };
                searchHandler.postDelayed(searchRunnable, 400);
            }
        });
    }

    private void setupDistributorDropdown(View view) {
        view.findViewById(R.id.distributorDropdown).setOnClickListener(this::showDistributorMenu);
    }

    private void showDistributorMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, -1, 0, getString(R.string.all_distributors));
        int i = 0;
        List<String> ids = new ArrayList<>(knownDistributors.keySet());
        for (String id : ids) {
            menu.getMenu().add(0, i, i + 1, knownDistributors.get(id));
            i++;
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == -1) {
                distributorId = null;
                distributorName = null;
                distributorLabel.setText(R.string.all_distributors);
            } else {
                String id = ids.get(item.getItemId());
                distributorId = id;
                distributorName = knownDistributors.get(id);
                distributorLabel.setText(distributorName);
            }
            page = 1;
            load();
            return true;
        });
        menu.show();
    }

    private void setupDateRange(View view) {
        view.findViewById(R.id.dateRangePill).setOnClickListener(v -> showDateRangePicker());
    }

    private void showDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.date_range)
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) return;
            if (selection.first != null) dateFrom = apiDate(selection.first);
            if (selection.second != null) dateTo = apiDate(selection.second);
            updateDateLabel();
            page = 1;
            load();
        });
        picker.show(getParentFragmentManager(), "order_date_range");
    }

    private void updateDateLabel() {
        dateRangeLabel.setText(displayDate(dateFrom) + "  →  " + displayDate(dateTo));
    }

    // ---- Networking ------------------------------------------------------

    private void load() {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        api.getOrders(distributorId, search, dateFrom, dateTo, status, page, PER_PAGE,
                new ApiCallback<OrdersData>() {
                    @Override
                    public void onSuccess(OrdersData data) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        bind(data);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        orders.clear();
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(View.VISIBLE);
                        paginationBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(),
                                message != null ? message : getString(R.string.orders_load_failed),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bind(OrdersData data) {
        orders.clear();
        if (data != null && data.orders != null) {
            orders.addAll(data.orders);
            mergeDistributors(data.orders);
        }
        adapter.notifyDataSetChanged();

        emptyView.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(orders.isEmpty() ? View.GONE : View.VISIBLE);

        updateStatusCounts(data != null ? data.status_counts : null);
        updatePagination(data != null ? data.pagination : null);
        list.scrollToPosition(0);
    }

    private void mergeDistributors(List<OrdersData.Order> list) {
        for (OrdersData.Order o : list) {
            if (o.distributor != null && o.distributor.id != 0) {
                knownDistributors.put(String.valueOf(o.distributor.id), o.distributorName());
            }
        }
    }

    private void updateStatusCounts(OrdersData.StatusCounts counts) {
        for (int i = 0; i < statusChips.length; i++) {
            int count = counts != null ? counts.forKey(STATUS_KEYS[i]) : 0;
            statusChips[i].setText(STATUS_LABELS[i] + "  " + count);
        }
    }

    private void updatePagination(OrdersData.Pagination pagination) {
        if (pagination == null || pagination.total == 0) {
            paginationBar.setVisibility(View.GONE);
            return;
        }
        page = pagination.page;
        totalPages = Math.max(1, pagination.total_pages);
        total = pagination.total;

        int from = (page - 1) * PER_PAGE + 1;
        int to = Math.min(page * PER_PAGE, total);
        paginationLabel.setText(getString(R.string.orders_pagination, from, to, total));

        boolean multi = totalPages > 1;
        paginationBar.setVisibility(View.VISIBLE);
        btnPrev.setEnabled(page > 1);
        btnPrev.setAlpha(page > 1 ? 1f : 0.4f);
        btnNext.setEnabled(page < totalPages);
        btnNext.setAlpha(page < totalPages ? 1f : 0.4f);
        // Keep the "Showing …" line even on a single page; arrows just disable.
        if (!multi && total <= PER_PAGE) {
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
        } else {
            btnPrev.setVisibility(View.VISIBLE);
            btnNext.setVisibility(View.VISIBLE);
        }
    }

    private void openOrderDetail(OrdersData.Order order) {
        Intent intent = new Intent(requireContext(), OrderDetailActivity.class);
        intent.putExtra(OrderDetailActivity.EXTRA_ID, String.valueOf(order.id));
        startActivity(intent);
    }

    // ---- Date helpers ----------------------------------------------------

    /** millis (UTC midnight from the picker) → "yyyy-MM-dd". */
    private static String apiDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(millis);
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
