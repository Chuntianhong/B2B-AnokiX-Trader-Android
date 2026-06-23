package com.anokix.trader.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.model.OrderItem;

import java.util.List;

/**
 * Order cards for the trader (orders placed with distributors), mirroring the
 * Trader Portal /orders cards: distributor logo, status badge, order id/date,
 * items + amount, delivery info and a contextual action.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

    public interface OnOrderClick {
        void onClick(OrderItem item);
    }

    private final List<OrderItem> items;
    private final boolean showActions; // unused for trader; kept for API compatibility
    private OnOrderClick clickListener;

    public OrderAdapter(List<OrderItem> items, boolean showActions) {
        this.items = items;
        this.showActions = showActions;
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OrderItem item = items.get(position);
        holder.traderName.setText(item.traderName);
        holder.orderId.setText(item.orderId);
        holder.subtitle.setText(item.subtitle);
        holder.amount.setText(item.amount);
        holder.initials.setText(item.initials);
        holder.time.setText(item.time);
        holder.productSummary.setText(item.itemsCount + (item.itemsCount == 1 ? " item" : " items"));
        holder.deliveryInfo.setText(item.deliveryInfo);
        holder.action.setText(item.actionLabel + " →");

        try {
            ViewCompat.setBackgroundTintList(holder.initials,
                    ColorStateList.valueOf(Color.parseColor(item.logoColor)));
        } catch (IllegalArgumentException ignored) { }

        int color = statusColor(holder, item.status);
        holder.statusRibbon.setText(humanizeStatus(item.status));
        holder.statusRibbon.setTextColor(color);
        ViewCompat.setBackgroundTintList(holder.statusRibbon,
                ColorStateList.valueOf(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))));
        holder.action.setTextColor(color);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int statusColor(ViewHolder h, String status) {
        switch (status) {
            case "pending":          return Color.parseColor("#D97706");
            case "accepted":         return Color.parseColor("#7C3AED");
            case "picking":          return Color.parseColor("#2563EB");
            case "packing":          return Color.parseColor("#0891B2");
            case "out_for_delivery": return Color.parseColor("#EA580C");
            case "delivered":        return Color.parseColor("#16A34A");
            case "cancelled":        return Color.parseColor("#DC2626");
            default:                 return Color.parseColor("#64748B");
        }
    }

    /** Convert a status key ("out_for_delivery") into a display label ("Out For Delivery"). */
    static String humanizeStatus(String status) {
        String[] parts = status.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView traderName;
        final TextView orderId;
        final TextView subtitle;
        final TextView amount;
        final TextView time;
        final TextView initials;
        final TextView productSummary;
        final TextView statusRibbon;
        final TextView deliveryInfo;
        final TextView action;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            traderName = itemView.findViewById(R.id.traderName);
            orderId = itemView.findViewById(R.id.orderId);
            subtitle = itemView.findViewById(R.id.orderLocation);
            amount = itemView.findViewById(R.id.orderAmount);
            time = itemView.findViewById(R.id.orderTime);
            initials = itemView.findViewById(R.id.avatarText);
            productSummary = itemView.findViewById(R.id.productSummary);
            statusRibbon = itemView.findViewById(R.id.statusRibbon);
            deliveryInfo = itemView.findViewById(R.id.deliveryInfo);
            action = itemView.findViewById(R.id.orderAction);
        }
    }
}
