package com.anokix.trader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.trader.R;
import com.anokix.trader.model.ReturnReason;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.ReturnsListData;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Goods Returns (GRN) screen. Lists the trader's returns (GET api/trader/returns)
 * with a KPI summary; each card expands to show its line items + the distributor's
 * decision note, and shows the credit note once approved. The "Return Goods" button
 * opens {@link GoodsReturnFormActivity} to file a new return against a received GRV.
 */
public class GoodsReturnsActivity extends AppCompatActivity {

    private final List<ReturnsListData.Grn> returns = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private GrnAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading, emptyView;
    private TextView statTotal, statApproved, statPending, statCredit;
    private boolean reloadOnResume = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods_returns);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statTotal = findViewById(R.id.statTotal);
        statApproved = findViewById(R.id.statApproved);
        statPending = findViewById(R.id.statPending);
        statCredit = findViewById(R.id.statCredit);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);

        list = findViewById(R.id.grnList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GrnAdapter();
        list.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        findViewById(R.id.btnReturnGoods).setOnClickListener(v -> {
            reloadOnResume = true;
            startActivity(new Intent(this, GoodsReturnFormActivity.class));
        });

        load(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reloadOnResume) {
            reloadOnResume = false;
            load(false);
        }
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) {
            loading.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
        ApiClient.get(this).getReturns(new ApiCallback<ReturnsListData>() {
            @Override
            public void onSuccess(ReturnsListData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                returns.clear();
                if (data != null && data.returns != null) {
                    returns.addAll(data.returns);
                }
                bindSummary(data != null ? data.summary : null);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(GoodsReturnsActivity.this, message, Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = returns.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void bindSummary(ReturnsListData.Summary s) {
        int total, approved, pending;
        double credit;
        if (s != null) {
            total = s.total_returns;
            approved = s.approved;
            pending = s.pending;
            credit = s.credit_issued;
        } else {
            total = returns.size();
            approved = 0;
            pending = 0;
            credit = 0;
            for (ReturnsListData.Grn g : returns) {
                if (g.isApproved()) approved++;
                else pending++;
                if (g.credit_note_total != null) credit += g.credit_note_total;
            }
        }
        statTotal.setText(String.valueOf(total));
        statApproved.setText(String.valueOf(approved));
        statPending.setText(String.valueOf(pending));
        statCredit.setText(money(credit));
    }

    // ---- List adapter ----------------------------------------------------

    private class GrnAdapter extends RecyclerView.Adapter<GrnAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grn, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ReturnsListData.Grn g = returns.get(position);
            h.grnNumber.setText(g.grn_number);
            h.grnDistributor.setText(safe(g.distributor_name));
            h.grnMeta.setText(g.lineCount() + " line(s) · " + safe(g.total_quantity)
                    + " × " + ReturnReason.label(g.reason));
            bindStatusBadge(h.grnStatus, g.status);
            h.grnDate.setText(formatDate(g.created_at));

            if (g.hasCreditNote()) {
                h.creditRow.setVisibility(View.VISIBLE);
                String amount = g.credit_note_total != null ? " · " + money(g.credit_note_total) : "";
                h.grnCredit.setText(g.credit_note_number + amount);
            } else {
                h.creditRow.setVisibility(View.GONE);
            }

            boolean isExpanded = expanded.contains(g.id);
            h.expandIcon.setImageResource(isExpanded ? R.drawable.ic_minus : R.drawable.ic_add);
            if (isExpanded) {
                buildExpanded(h.expandContainer, g);
                h.expandContainer.setVisibility(View.VISIBLE);
            } else {
                h.expandContainer.removeAllViews();
                h.expandContainer.setVisibility(View.GONE);
            }

            h.cardRoot.setOnClickListener(v -> {
                if (expanded.contains(g.id)) expanded.remove(g.id);
                else expanded.add(g.id);
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return returns.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final View cardRoot;
            final android.widget.ImageView expandIcon;
            final TextView grnNumber, grnStatus, grnDistributor, grnMeta, grnDate, grnCredit;
            final View creditRow;
            final LinearLayout expandContainer;

            VH(@NonNull View v) {
                super(v);
                cardRoot = v.findViewById(R.id.cardRoot);
                expandIcon = v.findViewById(R.id.expandIcon);
                grnNumber = v.findViewById(R.id.grnNumber);
                grnStatus = v.findViewById(R.id.grnStatus);
                grnDistributor = v.findViewById(R.id.grnDistributor);
                grnMeta = v.findViewById(R.id.grnMeta);
                grnDate = v.findViewById(R.id.grnDate);
                grnCredit = v.findViewById(R.id.grnCredit);
                creditRow = v.findViewById(R.id.creditRow);
                expandContainer = v.findViewById(R.id.expandContainer);
            }
        }
    }

    /** Build the expanded line items + distributor note into the container. */
    private void buildExpanded(LinearLayout container, ReturnsListData.Grn g) {
        container.removeAllViews();

        View divider = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dp.topMargin = dp(12);
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
        container.addView(divider);

        if (g.items != null) {
            for (ReturnsListData.Item item : g.items) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_grn_line, container, false);
                ((TextView) row.findViewById(R.id.lineName)).setText(safe(item.name));
                ((TextView) row.findViewById(R.id.lineQtyReason))
                        .setText(item.quantity + " × " + ReturnReason.label(item.reason));
                container.addView(row);
            }
        }

        if (g.decision_note != null && !g.decision_note.isEmpty()) {
            TextView note = new TextView(this);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = dp(8);
            note.setLayoutParams(np);
            note.setText("Distributor note: " + g.decision_note);
            note.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            note.setTextSize(12);
            container.addView(note);
        }
    }

    private void bindStatusBadge(TextView badge, String status) {
        String label;
        int bg, fg;
        String s = status == null ? "" : status.toLowerCase(Locale.US);
        switch (s) {
            case "approved":
                label = "Approved";
                bg = R.drawable.bg_badge_success;
                fg = R.color.success;
                break;
            case "rejected":
                label = "Rejected";
                bg = R.drawable.bg_badge_danger;
                fg = R.color.danger;
                break;
            case "submitted":
                label = "Submitted";
                bg = R.drawable.bg_badge_warning;
                fg = R.color.warning;
                break;
            default:
                label = status == null || status.isEmpty() ? "—" : status;
                bg = R.drawable.bg_badge_purple;
                fg = R.color.purple_primary;
                break;
        }
        badge.setText(label);
        badge.setBackgroundResource(bg);
        badge.setTextColor(ContextCompat.getColor(this, fg));
    }

    // ---- Helpers ---------------------------------------------------------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** "dd/MM/yyyy HH:mm:ss" → "25 Jun 2026" (falls back to the raw value). */
    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            java.util.Date d = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(raw);
            if (d == null) return raw;
            return new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return raw;
        }
    }
}
