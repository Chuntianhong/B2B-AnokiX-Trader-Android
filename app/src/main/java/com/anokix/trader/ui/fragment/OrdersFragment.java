package com.anokix.trader.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.OrderItem;
import com.anokix.trader.ui.OrderDetailActivity;
import com.anokix.trader.ui.adapter.OrderAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class OrdersFragment extends Fragment {

    private static final String[] FILTER_LABELS = {
            "All Orders", "Pending", "Accepted", "Picking", "Packing",
            "Out for Delivery", "Delivered", "Cancelled"
    };
    private static final String[] FILTER_KEYS = {
            "all", "pending", "accepted", "picking", "packing",
            "out_for_delivery", "delivered", "cancelled"
    };

    private final List<OrderItem> allOrders = new ArrayList<>();
    private final List<OrderItem> shown = new ArrayList<>();
    private OrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        allOrders.clear();
        allOrders.addAll(MockData.getOrders());

        RecyclerView list = view.findViewById(R.id.ordersList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrderAdapter(shown, false);
        adapter.setOnOrderClick(this::openOrderDetail);
        list.setAdapter(adapter);

        buildFilters(view);
        applyFilter("all");

        view.findViewById(R.id.scanBarcodeBtn).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Barcode scanner coming soon", Toast.LENGTH_SHORT).show());
    }

    private void buildFilters(View view) {
        ChipGroup group = view.findViewById(R.id.statusChips);
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final String key = FILTER_KEYS[i];
            Chip chip = new Chip(requireContext());
            chip.setText(FILTER_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector));
            chip.setOnClickListener(v -> applyFilter(key));
            group.addView(chip);
        }
    }

    private void applyFilter(String key) {
        shown.clear();
        for (OrderItem o : allOrders) {
            if ("all".equals(key) || key.equals(o.status)) {
                shown.add(o);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void openOrderDetail(OrderItem item) {
        Intent intent = new Intent(requireContext(), OrderDetailActivity.class);
        intent.putExtra(OrderDetailActivity.EXTRA_ID, item.orderId);
        intent.putExtra(OrderDetailActivity.EXTRA_DISTRIBUTOR, item.traderName);
        intent.putExtra(OrderDetailActivity.EXTRA_AMOUNT, item.amount);
        intent.putExtra(OrderDetailActivity.EXTRA_PAYMENT, item.payment);
        intent.putExtra(OrderDetailActivity.EXTRA_DATE, item.time);
        intent.putExtra(OrderDetailActivity.EXTRA_STATUS, item.status);
        intent.putExtra(OrderDetailActivity.EXTRA_PRODUCTS, item.productSummary);
        startActivity(intent);
    }
}
