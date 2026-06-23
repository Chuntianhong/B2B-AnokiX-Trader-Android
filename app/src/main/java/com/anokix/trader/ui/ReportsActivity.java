package com.anokix.trader.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.TraderReport;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Reports — mirrors the Trader Portal /reports page (verbatim data). */
public class ReportsActivity extends AppCompatActivity {

    private static final String[] CAT_LABELS = {"All Reports", "Sales", "POS", "Inventory", "Marketplace", "Wallet"};

    private List<TraderReport> allReports;
    private LinearLayout reportsList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        allReports = MockData.getTraderReports();
        reportsList = findViewById(R.id.recentReportsList);

        findViewById(R.id.generateReportButton).setOnClickListener(v ->
                Toast.makeText(this, "Generate report coming soon", Toast.LENGTH_SHORT).show());

        buildStats();
        buildTypes();
        buildCategoryChips();
        renderReports("All Reports");
    }

    private void buildStats() {
        LinearLayout grid = findViewById(R.id.reportStatsGrid);
        String[] labels = {"Reports Generated", "Downloads", "Scheduled", "Shared"};
        String[] values = {"186", "142", "8", "22"};
        String[] metas = {"↑ 14.5% vs Last Month", "This month", "Active schedules", "With team members"};
        String[] tones = {"#7c3aed", "#16a34a", "#2563eb", "#ea580c"};

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(10);
                grid.addView(row, lp);
            }
            View card = inflater.inflate(R.layout.item_analytics_kpi, row, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginStart(i % 2 == 1 ? dp(5) : 0);
            clp.setMarginEnd(i % 2 == 0 ? dp(5) : 0);
            card.setLayoutParams(clp);

            tint(card.findViewById(R.id.kpiAccent), tones[i]);
            ((TextView) card.findViewById(R.id.kpiLabel)).setText(labels[i]);
            ((TextView) card.findViewById(R.id.kpiValue)).setText(values[i]);
            ((TextView) card.findViewById(R.id.kpiChange)).setText(metas[i]);
            row.addView(card);
        }
    }

    private void buildTypes() {
        LinearLayout grid = findViewById(R.id.reportTypesGrid);
        String[] titles = {"Sales Reports", "POS Reports", "Inventory Reports",
                "Marketplace Reports", "Wallet Reports", "Rewards Reports"};
        String[] descs = {
                "Daily sales, revenue and category performance",
                "Transactions, tenders and shift summaries",
                "Stock levels, movement and low-stock alerts",
                "Orders, distributors and delivery performance",
                "Settlements, transfers and account activity",
                "Points earned, redeemed and cashback summary"};
        String[] icons = {"📈", "🧾", "📦", "🛒", "💰", "⭐"};
        String[] tones = {"#7c3aed", "#6366f1", "#2563eb", "#16a34a", "#0891b2", "#ea580c"};

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = null;
        for (int i = 0; i < titles.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(10);
                grid.addView(row, lp);
            }
            View card = inflater.inflate(R.layout.item_report_type_card, row, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginStart(i % 2 == 1 ? dp(5) : 0);
            clp.setMarginEnd(i % 2 == 0 ? dp(5) : 0);
            card.setLayoutParams(clp);

            TextView icon = card.findViewById(R.id.typeIcon);
            icon.setText(icons[i]);
            tint(icon, tones[i]);
            ((TextView) card.findViewById(R.id.typeTitle)).setText(titles[i]);
            ((TextView) card.findViewById(R.id.typeDesc)).setText(descs[i]);
            final String title = titles[i];
            card.setOnClickListener(v ->
                    Toast.makeText(this, "Opening " + title, Toast.LENGTH_SHORT).show());
            row.addView(card);
        }
    }

    private void buildCategoryChips() {
        ChipGroup group = findViewById(R.id.reportCategoryChips);
        for (int i = 0; i < CAT_LABELS.length; i++) {
            final String label = CAT_LABELS[i];
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_selector));
            chip.setOnClickListener(v -> renderReports(label));
            group.addView(chip);
        }
    }

    private void renderReports(String category) {
        reportsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<TraderReport> filtered = new ArrayList<>();
        for (TraderReport r : allReports) {
            if (category.equals("All Reports") || category.equals(r.categoryLabel)) {
                filtered.add(r);
            }
        }
        for (TraderReport r : filtered) {
            View row = inflater.inflate(R.layout.item_report_row, reportsList, false);
            tint(row.findViewById(R.id.reportAccent), r.colorHex);
            ((TextView) row.findViewById(R.id.reportName)).setText(r.name);
            ((TextView) row.findViewById(R.id.reportMeta))
                    .setText(r.categoryLabel + " · " + r.period + " · " + r.fileSize);
            ((TextView) row.findViewById(R.id.reportFormat)).setText(r.format);

            TextView status = row.findViewById(R.id.reportStatus);
            ImageView download = row.findViewById(R.id.reportDownload);
            switch (r.status) {
                case "generating":
                    status.setText("Generating");
                    tint(status, "#f59e0b");
                    download.setVisibility(View.GONE);
                    break;
                case "failed":
                    status.setText("Failed");
                    tint(status, "#dc2626");
                    download.setVisibility(View.GONE);
                    break;
                default:
                    status.setText("Ready");
                    tint(status, "#16a34a");
                    download.setVisibility(View.VISIBLE);
                    download.setOnClickListener(v ->
                            Toast.makeText(this, "Downloading " + r.name, Toast.LENGTH_SHORT).show());
                    break;
            }
            reportsList.addView(row);
        }
    }

    private static void tint(View v, String hex) {
        v.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
