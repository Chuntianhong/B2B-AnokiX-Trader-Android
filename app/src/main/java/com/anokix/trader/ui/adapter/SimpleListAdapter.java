package com.anokix.trader.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.model.ListItem;

import java.util.List;

public class SimpleListAdapter extends RecyclerView.Adapter<SimpleListAdapter.ViewHolder> {

    /** Optional tap callback for a row. */
    public interface OnItemClick {
        void onClick(ListItem item);
    }

    private final List<ListItem> items;
    private OnItemClick clickListener;

    public SimpleListAdapter(List<ListItem> items) {
        this.items = items;
    }

    public void setOnItemClick(OnItemClick listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListItem item = items.get(position);
        holder.title.setText(item.title);
        holder.subtitle.setText(item.subtitle);
        if (holder.badge != null) {
            if (item.badge != null && !item.badge.isEmpty()) {
                holder.badge.setText(item.badge);
                holder.badge.setVisibility(android.view.View.VISIBLE);
            } else {
                holder.badge.setVisibility(android.view.View.GONE);
            }
        }
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView badge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.rowTitle);
            subtitle = itemView.findViewById(R.id.rowSubtitle);
            badge = itemView.findViewById(R.id.rowBadge);
        }
    }
}
