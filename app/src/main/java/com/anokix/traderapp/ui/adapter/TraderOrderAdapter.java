package com.anokix.traderapp.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.dto.OrdersData;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * Order cards for the trader Orders screen, bound to the real
 * {@link OrdersData.Order}: distributor logo/name, order number + placed date,
 * the product name chips, total amount, status badge, est. delivery, and a
 * "View Detail" action.
 */
public class TraderOrderAdapter extends RecyclerView.Adapter<TraderOrderAdapter.ViewHolder> {

    public interface OnOrderClick {
        void onClick(OrdersData.Order order);
    }

    /** Deterministic logo palette keyed by distributor id (mirrors the portal tints). */
    private static final String[] LOGO_COLORS = {
            "#EC4899", "#7C3AED", "#2563EB", "#0891B2", "#16A34A", "#EA580C", "#D97706"
    };

    private final List<OrdersData.Order> items;
    private final String currency;
    private OnOrderClick clickListener;

    public TraderOrderAdapter(List<OrdersData.Order> items, String currency) {
        this.items = items;
        this.currency = currency;
    }

    public void setOnOrderClick(OnOrderClick listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        OrdersData.Order o = items.get(position);
        String name = o.distributorName();

        h.traderName.setText(name);
        h.orderId.setText(o.order_number);
        h.orderTime.setText(OrderFormat.createdAt(o.created_at));
        h.amount.setText(OrderFormat.money(o.total_amount, currency));
        h.initials.setText(initials(name));

        ViewCompat.setBackgroundTintList(h.initials,
                ColorStateList.valueOf(Color.parseColor(logoColor(o.distributor_id))));

        int color = OrderFormat.statusColor(o.statusKey());
        h.statusRibbon.setText(OrderFormat.humanize(o.statusKey()));
        h.statusRibbon.setTextColor(color);
        ViewCompat.setBackgroundTintList(h.statusRibbon,
                ColorStateList.valueOf(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))));

        // Highlight the card border for pending orders (mirrors the web portal).
        boolean pending = "pending".equals(o.statusKey());
        h.card.setStrokeColor(pending ? color
                : ContextColor(h.itemView, R.color.divider));
        h.card.setStrokeWidth(dp(h.itemView, pending ? 1 : 1));

        int productCount = o.products != null ? o.products.size() : 0;
        h.productsLabel.setText(h.itemView.getContext().getString(R.string.products_count, productCount));
        bindProductChips(h.productChips, o.products);

        h.deliveryInfo.setText(deliveryInfo(o));

        h.viewDetail.setOnClickListener(v -> fire(o));
        //h.itemView.setOnClickListener(v -> fire(o));
    }

    private void fire(OrdersData.Order o) {
        if (clickListener != null) clickListener.onClick(o);
    }

    private void bindProductChips(ChipGroup group, List<String> products) {
        group.removeAllViews();
        if (products == null) return;
        for (String p : products) {
            Chip chip = new Chip(group.getContext());
            chip.setText(p);
            chip.setTextSize(11);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(dp(group, 26));
            chip.setChipBackgroundColorResource(R.color.surface_variant);
            chip.setTextColor(ContextColor(group, R.color.text_primary));
            chip.setChipStrokeColorResource(R.color.divider);
            chip.setChipStrokeWidth(dp(group, 1));
            group.addView(chip);
        }
    }

    /** "Est. delivery 26 Jun 2026" while open, "Delivered 26 Jun 2026" once done. */
    private String deliveryInfo(OrdersData.Order o) {
        String date = OrderFormat.deliveryDate(o.delivery_date);
        if (date.isEmpty()) return "";
        String prefix = "delivered".equals(o.statusKey()) ? "Delivered " : "Est. delivery ";
        return prefix + date;
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() >= 2) break;
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String logoColor(long distributorId) {
        int idx = (int) (Math.abs(distributorId) % LOGO_COLORS.length);
        return LOGO_COLORS[idx];
    }

    private static int dp(View v, int value) {
        return (int) (value * v.getResources().getDisplayMetrics().density);
    }

    private static int ContextColor(View v, int res) {
        return androidx.core.content.ContextCompat.getColor(v.getContext(), res);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView traderName, orderId, orderTime, amount, initials, statusRibbon,
                productsLabel, deliveryInfo;
        final ChipGroup productChips;
        final View viewDetail;
        final MaterialCardView card;

        ViewHolder(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            traderName = v.findViewById(R.id.traderName);
            orderId = v.findViewById(R.id.orderId);
            orderTime = v.findViewById(R.id.orderTime);
            amount = v.findViewById(R.id.orderAmount);
            initials = v.findViewById(R.id.avatarText);
            statusRibbon = v.findViewById(R.id.statusRibbon);
            productsLabel = v.findViewById(R.id.productsLabel);
            deliveryInfo = v.findViewById(R.id.deliveryInfo);
            productChips = v.findViewById(R.id.productChips);
            viewDetail = v.findViewById(R.id.btnViewDetail);
        }
    }
}
