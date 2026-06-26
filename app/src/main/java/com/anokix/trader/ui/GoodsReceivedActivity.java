package com.anokix.trader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.GrvDetailData;
import com.anokix.trader.network.dto.GrvListData;
import com.anokix.trader.network.dto.GrvPdfData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Goods Received (GRV) screen. Lists the trader's Goods Received Vouchers (from
 * GET api/trader/grvs) with a KPI summary. A GRV is auto-created when an order is
 * delivered; stock is only added to inventory once the trader confirms receipt
 * (full or partial) via the bottom sheet. Each GRV can also be exported to PDF.
 */
public class GoodsReceivedActivity extends AppCompatActivity {

    private final List<GrvListData.Grv> grvs = new ArrayList<>();
    private GrvAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading;
    private View emptyView;
    private TextView statTotal, statAwaiting, statReceived, statLines;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods_received);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statTotal = findViewById(R.id.statTotal);
        statAwaiting = findViewById(R.id.statAwaiting);
        statReceived = findViewById(R.id.statReceived);
        statLines = findViewById(R.id.statLines);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);

        list = findViewById(R.id.grvList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GrvAdapter();
        list.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        load(true);
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) {
            loading.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
        ApiClient.get(this).getGrvs(new ApiCallback<GrvListData>() {
            @Override
            public void onSuccess(GrvListData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                grvs.clear();
                if (data != null && data.grvs != null) {
                    grvs.addAll(data.grvs);
                }
                bindSummary(data != null ? data.summary : null);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(GoodsReceivedActivity.this, message, Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = grvs.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void bindSummary(GrvListData.Summary s) {
        int total, awaiting, received, lines;
        if (s != null) {
            total = s.total_grvs;
            awaiting = s.awaiting;
            received = s.received;
            lines = s.line_items;
        } else {
            // Fall back to computing from the list if the server omits the summary.
            total = grvs.size();
            awaiting = 0;
            received = 0;
            lines = 0;
            for (GrvListData.Grv g : grvs) {
                if (g.isPending()) awaiting++;
                if ("received".equalsIgnoreCase(g.status)) received++;
                lines += g.lineCount();
            }
        }
        statTotal.setText(String.valueOf(total));
        statAwaiting.setText(String.valueOf(awaiting));
        statReceived.setText(String.valueOf(received));
        statLines.setText(String.valueOf(lines));
    }

    // ---- List adapter ----------------------------------------------------

    private class GrvAdapter extends RecyclerView.Adapter<GrvAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_grv, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            GrvListData.Grv g = grvs.get(position);
            h.grvNumber.setText(g.grv_number);
            h.grvOrder.setText("Order " + safe(g.order_number));
            h.grvMeta.setText(g.lineCount() + " line(s) · Delivery " + formatDate(g.delivery_date));
            bindStatusBadge(h.grvStatus, g.status);

            h.btnConfirm.setVisibility(g.isPending() ? View.VISIBLE : View.GONE);
            h.btnConfirm.setOnClickListener(v -> showConfirmSheet(g));
            h.btnPdf.setOnClickListener(v -> openPdf(g));
        }

        @Override
        public int getItemCount() {
            return grvs.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView grvNumber, grvStatus, grvOrder, grvMeta;
            final MaterialButton btnPdf, btnConfirm;

            VH(@NonNull View v) {
                super(v);
                grvNumber = v.findViewById(R.id.grvNumber);
                grvStatus = v.findViewById(R.id.grvStatus);
                grvOrder = v.findViewById(R.id.grvOrder);
                grvMeta = v.findViewById(R.id.grvMeta);
                btnPdf = v.findViewById(R.id.btnPdf);
                btnConfirm = v.findViewById(R.id.btnConfirm);
            }
        }
    }

    private void bindStatusBadge(TextView badge, String status) {
        String label;
        int bg, fg;
        String s = status == null ? "" : status.toLowerCase(Locale.US);
        switch (s) {
            case "received":
                label = "Received";
                bg = R.drawable.bg_badge_success;
                fg = R.color.success;
                break;
            case "partially_received":
                label = "Partially Received";
                bg = R.drawable.bg_badge_info;
                fg = R.color.info;
                break;
            case "pending":
                label = "Awaiting Receipt";
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

    // ---- PDF -------------------------------------------------------------

    private void openPdf(GrvListData.Grv g) {
        Toast.makeText(this, "Generating PDF…", Toast.LENGTH_SHORT).show();
        ApiClient.get(this).getGrvPdf(g.id, new ApiCallback<GrvPdfData>() {
            @Override
            public void onSuccess(GrvPdfData pdf) {
                if (pdf == null || pdf.data == null || pdf.data.isEmpty()) {
                    Toast.makeText(GoodsReceivedActivity.this, "PDF unavailable.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = Base64.decode(pdf.data, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "grv");
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    String name = pdf.filename != null && !pdf.filename.isEmpty()
                            ? pdf.filename : g.grv_number + ".pdf";
                    File file = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Intent intent = new Intent(GoodsReceivedActivity.this, PdfViewerActivity.class);
                    intent.putExtra(PdfViewerActivity.EXTRA_PATH, file.getAbsolutePath());
                    intent.putExtra(PdfViewerActivity.EXTRA_TITLE, g.grv_number);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(GoodsReceivedActivity.this, "Couldn't open PDF.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(GoodsReceivedActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Confirm Receipt sheet ------------------------------------------

    /** Per-line editing state inside the confirm sheet. */
    private static class LineHolder {
        final GrvListData.Item item;
        final EditText received;
        final EditText damaged;
        final TextView missing;
        final TextView accepted;

        LineHolder(GrvListData.Item item, EditText received, EditText damaged,
                   TextView missing, TextView accepted) {
            this.item = item;
            this.received = received;
            this.damaged = damaged;
            this.missing = missing;
            this.accepted = accepted;
        }
    }

    private void showConfirmSheet(GrvListData.Grv g) {
        if (g.items == null || g.items.isEmpty()) {
            Toast.makeText(this, "This GRV has no line items.", Toast.LENGTH_SHORT).show();
            return;
        }

        View sheet = getLayoutInflater().inflate(R.layout.sheet_grv_confirm, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.sheetTitle)).setText("Confirm Receipt · " + g.grv_number);
        ((TextView) sheet.findViewById(R.id.infoGrv)).setText(g.grv_number);
        ((TextView) sheet.findViewById(R.id.infoInvoice)).setText(safe(g.invoice_number));
        ((TextView) sheet.findViewById(R.id.infoDistributor)).setText(safe(g.distributor_name));
        ((TextView) sheet.findViewById(R.id.infoDelivery)).setText(formatDate(g.delivery_date));

        TextView segFull = sheet.findViewById(R.id.segFull);
        TextView segPartial = sheet.findViewById(R.id.segPartial);
        ViewGroup linesContainer = sheet.findViewById(R.id.linesContainer);

        final List<LineHolder> lines = new ArrayList<>();
        final boolean[] fullMode = {true};

        for (GrvListData.Item item : g.items) {
            View row = getLayoutInflater().inflate(R.layout.item_grv_confirm_line, linesContainer, false);
            ((TextView) row.findViewById(R.id.lineName)).setText(safe(item.name));
            ((TextView) row.findViewById(R.id.lineSku)).setText("SKU: " + safe(item.sku));
            ((TextView) row.findViewById(R.id.lineOrdered)).setText(String.valueOf(item.ordered_quantity));
            EditText received = row.findViewById(R.id.lineReceived);
            EditText damaged = row.findViewById(R.id.lineDamaged);
            TextView missing = row.findViewById(R.id.lineMissing);
            TextView accepted = row.findViewById(R.id.lineAccepted);
            received.setText(String.valueOf(item.ordered_quantity));
            damaged.setText("0");

            LineHolder lh = new LineHolder(item, received, damaged, missing, accepted);
            lines.add(lh);

            TextWatcher watcher = new SimpleWatcher(() -> { if (!fullMode[0]) recompute(lh, false); });
            received.addTextChangedListener(watcher);
            damaged.addTextChangedListener(watcher);

            linesContainer.addView(row);
        }

        Runnable applyMode = () -> applyMode(fullMode[0], segFull, segPartial, lines);
        segFull.setOnClickListener(v -> { fullMode[0] = true; applyMode.run(); });
        segPartial.setOnClickListener(v -> { fullMode[0] = false; applyMode.run(); });
        applyMode.run();

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        MaterialButton submit = sheet.findViewById(R.id.btnSubmit);
        submit.setOnClickListener(v -> submitConfirm(g, lines, fullMode[0], submit, dialog));

        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
        dialog.show();
    }

    private void applyMode(boolean full, TextView segFull, TextView segPartial, List<LineHolder> lines) {
        segFull.setBackgroundResource(full ? R.drawable.bg_grv_segment_selected : 0);
        segPartial.setBackgroundResource(full ? 0 : R.drawable.bg_grv_segment_selected);
        segFull.setTextColor(ContextCompat.getColor(this, full ? R.color.text_primary : R.color.text_secondary));
        segPartial.setTextColor(ContextCompat.getColor(this, full ? R.color.text_secondary : R.color.text_primary));

        for (LineHolder lh : lines) {
            lh.received.setEnabled(!full);
            lh.damaged.setEnabled(!full);
            lh.received.setAlpha(full ? 0.5f : 1f);
            lh.damaged.setAlpha(full ? 0.5f : 1f);
            if (full) {
                lh.received.setText(String.valueOf(lh.item.ordered_quantity));
                lh.damaged.setText("0");
            }
            recompute(lh, full);
        }
    }

    /** Recompute Missing + Accepted for one line. Missing = ordered − received − damaged. */
    private void recompute(LineHolder lh, boolean full) {
        int ordered = lh.item.ordered_quantity;
        int received = full ? ordered : parseInt(lh.received);
        int damaged = full ? 0 : parseInt(lh.damaged);
        int missing = ordered - received - damaged;
        int accepted = Math.max(received, 0);

        lh.accepted.setText(String.valueOf(accepted));

        lh.missing.setText(String.valueOf(missing));
        int missingColor;
        if (missing < 0) {
            missingColor = R.color.danger;   // received + damaged exceeds ordered (invalid)
        } else if (missing > 0) {
            missingColor = R.color.warning;
        } else {
            missingColor = R.color.text_secondary;
        }
        lh.missing.setTextColor(ContextCompat.getColor(this, missingColor));
    }

    private void submitConfirm(GrvListData.Grv g, List<LineHolder> lines, boolean full,
                               MaterialButton submit, BottomSheetDialog dialog) {
        JSONArray items = new JSONArray();
        try {
            for (LineHolder lh : lines) {
                int ordered = lh.item.ordered_quantity;
                int received = full ? ordered : parseInt(lh.received);
                int damaged = full ? 0 : parseInt(lh.damaged);
                if (received < 0 || damaged < 0) {
                    Toast.makeText(this, "Quantities can't be negative.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (received + damaged > ordered) {
                    Toast.makeText(this, "Received + damaged can't exceed ordered for "
                            + safe(lh.item.name) + ".", Toast.LENGTH_LONG).show();
                    return;
                }
                JSONObject line = new JSONObject();
                line.put("grv_item_id", lh.item.id);
                line.put("received_quantity", received);
                line.put("damaged_quantity", damaged);
                items.put(line);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't build request.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("items", items);
            body.put("note", "");
        } catch (Exception ignored) {
        }

        submit.setEnabled(false);
        submit.setText("Saving…");
        ApiClient.get(this).confirmGrv(g.id, body.toString(), new ApiCallback<GrvDetailData>() {
            @Override
            public void onSuccess(GrvDetailData data) {
                dialog.dismiss();
                Toast.makeText(GoodsReceivedActivity.this,
                        "Goods received and added to inventory.", Toast.LENGTH_LONG).show();
                load(false);
            }

            @Override
            public void onError(String message) {
                submit.setEnabled(true);
                submit.setText("Confirm & Add to Inventory");
                Toast.makeText(GoodsReceivedActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Helpers ---------------------------------------------------------

    private int parseInt(EditText e) {
        try {
            String s = e.getText().toString().trim();
            return s.isEmpty() ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** "yyyy-MM-dd" → "25 Jun 2026" (falls back to the raw value on parse failure). */
    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
            if (d == null) return raw;
            return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return raw;
        }
    }

    /** A {@link TextWatcher} that just fires a callback after text changes. */
    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }
}
