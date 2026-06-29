package com.anokix.trader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.NotificationsData;
import com.anokix.trader.model.NotificationItem;
import com.anokix.trader.ui.adapter.NotificationAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Notifications — live feed from GET api/common/notifications (filter all/unread,
 * paged). KPI cards (total/read/unread/important), All/Unread tabs, "Mark all as
 * read", and tap-to-open: tapping a row marks it read (POST .../read) and routes
 * to the related screen (order → order detail, return → GRN, stock → inventory).
 */
public class NotificationsActivity extends AppCompatActivity {

    private static final int PER_PAGE = 8;

    private final ApiClient api = ApiClient.get(this);
    private final List<NotificationsData.Notification> current = new ArrayList<>();
    private NotificationAdapter adapter;

    private String filter = "all";   // "all" | "unread"
    private int page = 1;
    private int totalPages = 1;
    private int total = 0;
    private int unreadCount = 0;

    private TextView statAll, statRead, statUnread, statImportant, lblRead, lblUnread;
    private TextView tabAll, tabUnread, pageInfo;
    private View loading, emptyView, paginationBar, btnPrev, btnNext;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statAll = findViewById(R.id.statAll);
        statRead = findViewById(R.id.statRead);
        statUnread = findViewById(R.id.statUnread);
        statImportant = findViewById(R.id.statImportant);
        lblRead = findViewById(R.id.lblRead);
        lblUnread = findViewById(R.id.lblUnread);
        tabAll = findViewById(R.id.tabAll);
        tabUnread = findViewById(R.id.tabUnread);
        pageInfo = findViewById(R.id.pageInfo);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);
        paginationBar = findViewById(R.id.paginationBar);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        RecyclerView list = findViewById(R.id.notificationsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        adapter.setOnNotificationClick(this::onItemClicked);
        list.setAdapter(adapter);

        tabAll.setOnClickListener(v -> selectFilter("all"));
        tabUnread.setOnClickListener(v -> selectFilter("unread"));
        findViewById(R.id.markAllRead).setOnClickListener(v -> markAllRead());
        btnPrev.setOnClickListener(v -> { if (page > 1) load(page - 1); });
        btnNext.setOnClickListener(v -> { if (page < totalPages) load(page + 1); });

        applyTabStyles();
        load(1);
    }

    // ---- Data ------------------------------------------------------------

    private void selectFilter(String f) {
        if (f.equals(filter)) return;
        filter = f;
        applyTabStyles();
        load(1);
    }

    private void load(int requestedPage) {
        loading.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        api.getNotifications(filter, requestedPage, PER_PAGE, new ApiCallback<NotificationsData>() {
            @Override
            public void onSuccess(NotificationsData data) {
                loading.setVisibility(View.GONE);
                current.clear();
                if (data != null && data.notifications != null) current.addAll(data.notifications);
                if (data != null) {
                    unreadCount = data.unread_count;
                    if (data.pagination != null) {
                        page = data.pagination.page;
                        totalPages = Math.max(1, data.pagination.total_pages);
                        total = data.pagination.total;
                    }
                }
                bindList();
                bindSummary();
                bindPagination();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
                bindList();
            }
        });
    }

    private void bindList() {
        List<NotificationItem> items = new ArrayList<>();
        for (NotificationsData.Notification n : current) items.add(toItem(n));
        adapter.setItems(items);
        emptyView.setVisibility(current.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindSummary() {
        int read = Math.max(0, total - unreadCount);
        int important = 0;
        for (NotificationsData.Notification n : current) if (isImportant(n)) important++;

        statAll.setText(String.valueOf(total));
        statRead.setText(String.valueOf(read));
        statUnread.setText(String.valueOf(unreadCount));
        statImportant.setText(String.valueOf(important));
        lblRead.setText("Read · " + percent(read, total));
        lblUnread.setText("Unread · " + percent(unreadCount, total));

        tabUnread.setText(unreadCount > 0 ? "Unread (" + unreadCount + ")" : "Unread");
    }

    private void bindPagination() {
        if (total <= 0) {
            paginationBar.setVisibility(View.GONE);
            return;
        }
        paginationBar.setVisibility(View.VISIBLE);
        int from = (page - 1) * PER_PAGE + 1;
        int to = Math.min(page * PER_PAGE, total);
        pageInfo.setText("Showing " + from + " to " + to + " of " + total);
        btnPrev.setEnabled(page > 1);
        btnPrev.setAlpha(page > 1 ? 1f : 0.3f);
        btnNext.setEnabled(page < totalPages);
        btnNext.setAlpha(page < totalPages ? 1f : 0.3f);
    }

    private void applyTabStyles() {
        boolean all = "all".equals(filter);
        tabAll.setTextColor(getColor(all ? R.color.purple_primary : R.color.text_secondary));
        tabUnread.setTextColor(getColor(all ? R.color.text_secondary : R.color.purple_primary));
    }

    // ---- Actions ---------------------------------------------------------

    private void onItemClicked(int position) {
        if (position < 0 || position >= current.size()) return;
        NotificationsData.Notification n = current.get(position);

        if (!n.is_read) {
            n.is_read = true;
            if (unreadCount > 0) unreadCount--;
            api.markNotificationRead(n.id, new ApiCallback<Void>() {
                @Override public void onSuccess(Void unused) {}
                @Override public void onError(String message) {}
            });
            // Reflect the change immediately (dot + KPIs).
            if ("unread".equals(filter)) {
                current.remove(position);
                bindList();
            } else {
                adapter.notifyItemChanged(position);
            }
            bindSummary();
        }
        navigate(n);
    }

    private void markAllRead() {
        api.markAllNotificationsRead(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(NotificationsActivity.this, "All notifications marked as read",
                        Toast.LENGTH_SHORT).show();
                load(1);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(NotificationsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Open the screen related to the notification's type/route. */
    private void navigate(NotificationsData.Notification n) {
        String type = n.type == null ? "" : n.type;
        String route = n.data != null ? n.data.route : null;

        if (type.startsWith("order") || "order".equals(route)) {
            if (n.data != null && n.data.order_id > 0) {
                Intent i = new Intent(this, OrderDetailActivity.class);
                i.putExtra(OrderDetailActivity.EXTRA_ID, String.valueOf(n.data.order_id));
                startActivity(i);
            }
        } else if (type.startsWith("return") || "return".equals(route)) {
            startActivity(new Intent(this, GoodsReturnsActivity.class));
        } else if (type.startsWith("stock") || "inventory".equals(route)) {
            startActivity(new Intent(this, InventoryActivity.class));
        }
    }

    // ---- Mapping / helpers ----------------------------------------------

    private NotificationItem toItem(NotificationsData.Notification n) {
        int icon, bg, tint;
        String action;
        String type = n.type == null ? "" : n.type;
        if (type.startsWith("return")) {
            icon = R.drawable.ic_returns; bg = R.drawable.bg_kpi_icon_orange;
            tint = R.color.warning; action = "View Returns";
        } else if (type.startsWith("stock") || (n.data != null && "inventory".equals(n.data.route))) {
            icon = R.drawable.ic_inventory; bg = R.drawable.bg_stat_icon_blue;
            tint = R.color.info; action = "View Inventory";
        } else if (type.startsWith("order")) {
            icon = R.drawable.ic_orders; bg = R.drawable.bg_kpi_icon_purple;
            tint = R.color.purple_primary; action = "View Order";
        } else {
            icon = R.drawable.ic_notifications; bg = R.drawable.bg_kpi_icon_purple;
            tint = R.color.purple_primary; action = "";
        }
        return new NotificationItem(n.id, safe(n.title), safe(n.body), relativeTime(n.created_at),
                type, icon, bg, tint, action, !n.is_read, isImportant(n));
    }

    /**
     * Important = any return decision, or an order whose status is Failed/Cancelled.
     * Mirrors the Trader web portal exactly:
     *   important = type=="return.decision"
     *            || (type=="order.status" && status in {"Failed","Cancelled"})
     */
    private static boolean isImportant(NotificationsData.Notification n) {
        if (n.type == null) return false;
        if ("return.decision".equals(n.type)) return true;
        if ("order.status".equals(n.type) && n.data != null) {
            String s = n.data.status;
            return "Failed".equals(s) || "Cancelled".equals(s);
        }
        return false;
    }

    private static String percent(int part, int whole) {
        if (whole <= 0) return "0%";
        return String.format(Locale.US, "%.1f%%", 100.0 * part / whole);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Recent (< 24h) → "x hours/minutes ago"; older → "dd MMM yyyy, HH:mm". */
    private static String relativeTime(String raw) {
        Date d = parse(raw);
        if (d == null) return raw == null ? "" : raw;
        long diff = System.currentTimeMillis() - d.getTime();
        if (diff >= 0 && diff < 24L * 60 * 60 * 1000) {
            long hours = diff / (60L * 60 * 1000);
            if (hours >= 1) return hours + (hours == 1 ? " hour ago" : " hours ago");
            long mins = diff / (60L * 1000);
            if (mins >= 1) return mins + (mins == 1 ? " minute ago" : " minutes ago");
            return "Just now";
        }
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
    }

    private static Date parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
