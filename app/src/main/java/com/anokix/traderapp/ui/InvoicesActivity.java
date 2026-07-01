package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.InvoiceListData;
import com.anokix.traderapp.network.dto.InvoicePdfData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Invoices screen. Lists the trader's invoices (GET api/trader/invoices) with a
 * KPI summary (total count / total value / average / this month, all computed
 * client-side since the API returns no summary). Each invoice can be exported to
 * PDF (GET api/trader/invoices/{id}/pdf → base64), opened in the in-app viewer.
 */
public class InvoicesActivity extends AppCompatActivity {

    private final List<InvoiceListData.Invoice> invoices = new ArrayList<>();
    private InvoiceAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading;
    private View emptyView;
    private TextView statTotal, statValue, statAverage, statMonth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoices);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statTotal = findViewById(R.id.statTotal);
        statValue = findViewById(R.id.statValue);
        statAverage = findViewById(R.id.statAverage);
        statMonth = findViewById(R.id.statMonth);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);

        list = findViewById(R.id.invoiceList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InvoiceAdapter();
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
        ApiClient.get(this).getInvoices(new ApiCallback<InvoiceListData>() {
            @Override
            public void onSuccess(InvoiceListData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                invoices.clear();
                if (data != null && data.invoices != null) {
                    invoices.addAll(data.invoices);
                }
                bindSummary();
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(InvoicesActivity.this, message, Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = invoices.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Compute the KPI cards from the loaded list (the API sends no summary). */
    private void bindSummary() {
        int total = invoices.size();
        double value = 0;
        int thisMonth = 0;

        Calendar now = Calendar.getInstance();
        int curMonth = now.get(Calendar.MONTH);
        int curYear = now.get(Calendar.YEAR);

        for (InvoiceListData.Invoice inv : invoices) {
            value += inv.total_amount;
            Date created = parseCreatedAt(inv.created_at);
            if (created != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(created);
                if (c.get(Calendar.MONTH) == curMonth && c.get(Calendar.YEAR) == curYear) {
                    thisMonth++;
                }
            }
        }
        double average = total > 0 ? value / total : 0;

        statTotal.setText(String.valueOf(total));
        statValue.setText(money(value));
        statAverage.setText(money(average));
        statMonth.setText(String.valueOf(thisMonth));
    }

    // ---- List adapter ----------------------------------------------------

    private class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_invoice, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            InvoiceListData.Invoice inv = invoices.get(position);
            h.invoiceNumber.setText(safe(inv.invoice_number));
            h.invoiceOrder.setText("Order " + safe(inv.order_number) + " · " + inv.lineCount() + " item(s)");
            h.invoiceDate.setText(formatDate(inv.created_at));
            h.invoiceAmount.setText(money(inv.total_amount));
            bindStatusBadge(h.invoiceStatus, inv.status);
            h.btnPdf.setOnClickListener(v -> openPdf(inv));
        }

        @Override
        public int getItemCount() {
            return invoices.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView invoiceNumber, invoiceStatus, invoiceOrder, invoiceDate, invoiceAmount;
            final MaterialButton btnPdf;

            VH(@NonNull View v) {
                super(v);
                invoiceNumber = v.findViewById(R.id.invoiceNumber);
                invoiceStatus = v.findViewById(R.id.invoiceStatus);
                invoiceOrder = v.findViewById(R.id.invoiceOrder);
                invoiceDate = v.findViewById(R.id.invoiceDate);
                invoiceAmount = v.findViewById(R.id.invoiceAmount);
                btnPdf = v.findViewById(R.id.btnPdf);
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
                label = "Pending";
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

    private void openPdf(InvoiceListData.Invoice inv) {
        Toast.makeText(this, "Generating PDF…", Toast.LENGTH_SHORT).show();
        ApiClient.get(this).getInvoicePdf(inv.id, new ApiCallback<InvoicePdfData>() {
            @Override
            public void onSuccess(InvoicePdfData pdf) {
                if (pdf == null || pdf.data == null || pdf.data.isEmpty()) {
                    Toast.makeText(InvoicesActivity.this, "PDF unavailable.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = Base64.decode(pdf.data, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "invoices");
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    String name = pdf.filename != null && !pdf.filename.isEmpty()
                            ? pdf.filename : inv.invoice_number + ".pdf";
                    File file = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Intent intent = new Intent(InvoicesActivity.this, PdfViewerActivity.class);
                    intent.putExtra(PdfViewerActivity.EXTRA_PATH, file.getAbsolutePath());
                    intent.putExtra(PdfViewerActivity.EXTRA_TITLE, inv.invoice_number);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(InvoicesActivity.this, "Couldn't open PDF.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(InvoicesActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Helpers ---------------------------------------------------------

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    /** Parse the API's "dd/MM/yyyy HH:mm:ss" created_at into a Date (null on failure). */
    private static Date parseCreatedAt(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** "dd/MM/yyyy HH:mm:ss" → "25 Jun 2026" (falls back to the raw value). */
    private static String formatDate(String raw) {
        Date d = parseCreatedAt(raw);
        if (d == null) return raw == null || raw.isEmpty() ? "—" : raw;
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
    }
}
