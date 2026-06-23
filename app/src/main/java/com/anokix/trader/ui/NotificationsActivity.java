package com.anokix.trader.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.NotificationItem;
import com.anokix.trader.model.TraderNotification;
import com.anokix.trader.ui.adapter.NotificationAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Notifications — mirrors the Trader Portal /notifications page (verbatim data). */
public class NotificationsActivity extends AppCompatActivity {

    private static final String[] FILTER_LABELS = {
            "All", "Unread", "Important", "Orders", "Inventory", "Wallet", "Rewards", "System"};
    private static final String[] FILTER_KEYS = {
            "all", "unread", "important", "orders", "inventory", "wallet", "rewards", "system"};

    private List<TraderNotification> data;
    private boolean[] read;
    private NotificationAdapter adapter;
    private TextView unreadCount;
    private String currentFilter = "all";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        data = MockData.getTraderNotifications();
        read = new boolean[data.size()];
        for (int i = 0; i < data.size(); i++) read[i] = data.get(i).read;

        unreadCount = findViewById(R.id.unreadCount);

        RecyclerView list = findViewById(R.id.notificationsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.markAllRead).setOnClickListener(v -> {
            for (int i = 0; i < read.length; i++) read[i] = true;
            render();
            Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show();
        });

        buildChips();
        render();
    }

    private void buildChips() {
        ChipGroup group = findViewById(R.id.notificationChips);
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            final String key = FILTER_KEYS[i];
            Chip chip = new Chip(this);
            chip.setText(FILTER_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_selector));
            chip.setOnClickListener(v -> {
                currentFilter = key;
                render();
            });
            group.addView(chip);
        }
    }

    private void render() {
        List<NotificationItem> items = new ArrayList<>();
        int unread = 0;
        for (int i = 0; i < data.size(); i++) {
            TraderNotification n = data.get(i);
            if (!read[i]) unread++;
            if (!matches(n, i)) continue;
            items.add(toItem(n, i));
        }
        adapter.setItems(items);
        unreadCount.setText(unread + " unread notification" + (unread == 1 ? "" : "s"));
    }

    private boolean matches(TraderNotification n, int index) {
        switch (currentFilter) {
            case "all":       return true;
            case "unread":    return !read[index];
            case "important": return n.important;
            default:          return currentFilter.equals(n.type);
        }
    }

    private NotificationItem toItem(TraderNotification n, int index) {
        int icon, bg, tint;
        String action;
        switch (n.type) {
            case "wallet":
                icon = R.drawable.ic_wallet; bg = R.drawable.bg_kpi_icon_green;
                tint = R.color.success; action = "View Wallet"; break;
            case "inventory":
                icon = R.drawable.ic_inventory; bg = R.drawable.bg_stat_icon_blue;
                tint = R.color.info; action = "View Inventory"; break;
            case "rewards":
                icon = R.drawable.ic_promo_gift; bg = R.drawable.bg_kpi_icon_orange;
                tint = R.color.warning; action = "View Rewards"; break;
            case "system":
                icon = R.drawable.ic_settings; bg = R.drawable.bg_kpi_icon_red;
                tint = R.color.danger; action = "Update"; break;
            default: // orders
                icon = R.drawable.ic_orders; bg = R.drawable.bg_kpi_icon_purple;
                tint = R.color.purple_primary; action = "View Order"; break;
        }
        String title = n.important ? "★ " + n.title : n.title;
        return new NotificationItem(String.valueOf(index), title, n.message, n.time,
                n.type, icon, bg, tint, action, !read[index]);
    }
}
