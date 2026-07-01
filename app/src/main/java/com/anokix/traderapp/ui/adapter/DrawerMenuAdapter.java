package com.anokix.traderapp.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MenuItem;
import com.anokix.traderapp.ui.views.RobotoTextView;

import java.util.List;

public class DrawerMenuAdapter extends RecyclerView.Adapter<DrawerMenuAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(MenuItem item);
    }

    private final List<MenuItem> items;
    private final OnItemClickListener listener;
    private int selectedPosition = 0;

    public DrawerMenuAdapter(List<MenuItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectedByKey(String key) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(key)) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev != selectedPosition) {
                    notifyItemChanged(prev);
                    notifyItemChanged(selectedPosition);
                }
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drawer_menu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuItem item = items.get(position);
        holder.title.setText(item.title);
        holder.icon.setImageResource(item.iconRes);

        if (item.subtitle != null && !item.subtitle.isEmpty()) {
            holder.subtitle.setText(item.subtitle);
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.subtitle.setVisibility(View.GONE);
        }

        boolean selected = position == selectedPosition;
        int white = ContextCompat.getColor(holder.itemView.getContext(), R.color.drawer_text);
        int muted = ContextCompat.getColor(holder.itemView.getContext(), R.color.drawer_text_muted);

        holder.icon.setColorFilter(selected ? white : muted);
        holder.title.setTextColor(selected ? white : muted);
        holder.subtitle.setTextColor(muted);

        if (selected) {
            holder.itemRoot.setBackgroundResource(R.drawable.bg_drawer_item_selected);
            holder.title.setTypeface(holder.title.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            holder.itemRoot.setBackgroundResource(android.R.color.transparent);
            holder.title.setTypeface(holder.title.getTypeface(), android.graphics.Typeface.NORMAL);
        }

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            int prev = selectedPosition;
            selectedPosition = adapterPosition;
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final RobotoTextView title;
        final RobotoTextView subtitle;
        final ImageView icon;
        final LinearLayout itemRoot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.drawerItemRoot);
            title = itemView.findViewById(R.id.drawerItemTitle);
            subtitle = itemView.findViewById(R.id.drawerItemSubtitle);
            icon = itemView.findViewById(R.id.drawerItemIcon);
        }
    }
}
