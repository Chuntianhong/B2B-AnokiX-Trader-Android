package com.anokix.trader.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.anokix.trader.R;
import com.anokix.trader.ui.views.DonutChartView;
import com.anokix.trader.ui.views.LineChartView;
import com.google.android.material.appbar.MaterialToolbar;

/** Analytics — mirrors the Trader Portal /analytics page (verbatim data). */
public class AnalyticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        buildKpis();

        LineChartView revenueChart = findViewById(R.id.revenueChart);
        revenueChart.setValues(new float[]{288f, 312f, 345f, 360f, 402f, 430f, 456f});

        bindCategories();
        bindHealth();
        bindTopProducts();
        bindInventoryInsights();
        bindModules();
    }

    private void buildKpis() {
        LinearLayout grid = findViewById(R.id.kpiGrid);
        String[] labels = {"Sales Today", "Monthly Revenue", "Wallet Balance", "Rewards Value"};
        String[] values = {"R18,450", "R456,200", "R8,750", "R1,250"};
        String[] changes = {"+18% vs Yesterday", "+24% vs Last Month", "+12% vs Last Week", "+32% vs Last Week"};
        String[] tones = {"#16a34a", "#7c3aed", "#2563eb", "#ea580c"};

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
            ((TextView) card.findViewById(R.id.kpiChange)).setText(changes[i]);
            row.addView(card);
        }
    }

    private void bindCategories() {
        DonutChartView donut = findViewById(R.id.categoryDonut);
        LinearLayout legend = findViewById(R.id.categoryLegend);

        String[] names = {"Beverages", "Groceries", "Household", "Airtime & VAS"};
        String[] amounts = {"42%", "31%", "18%", "9%"};
        float[] values = {42, 31, 18, 9};
        int[] colors = {0xFF7C3AED, 0xFF16A34A, 0xFF2563EB, 0xFFEA580C};

        donut.setStrokeWidthDp(15);
        donut.setData(values, colors);
        donut.setCenterText("100%", "Sales");

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < names.length; i++) {
            View row = inflater.inflate(R.layout.item_category_legend, legend, false);
            View dot = row.findViewById(R.id.legendDot);
            GradientDrawable dotBg = (GradientDrawable) dot.getBackground().mutate();
            dotBg.setColor(colors[i]);
            ((TextView) row.findViewById(R.id.legendName)).setText(names[i]);
            ((TextView) row.findViewById(R.id.legendAmount)).setText(amounts[i]);
            legend.addView(row);
        }
    }

    private void bindHealth() {
        LinearLayout list = findViewById(R.id.healthList);
        String[] labels = {"Sales Growth", "Stock Management", "Customer Retention", "Rewards Engagement"};
        String[] status = {"Excellent", "Good", "Excellent", "Excellent"};
        String[] tones = {"green", "blue", "green", "green"};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < labels.length; i++) {
            View row = inflater.inflate(R.layout.item_health_row, list, false);
            ((TextView) row.findViewById(R.id.healthLabel)).setText(labels[i]);
            TextView tag = row.findViewById(R.id.healthStatus);
            tag.setText(status[i]);
            tint(tag, "blue".equals(tones[i]) ? "#2563EB" : "#16A34A");
            list.addView(row);
        }
    }

    private void bindTopProducts() {
        LinearLayout container = findViewById(R.id.topProductsList);
        String[] names = {"Coca-Cola 2L", "White Bread Loaf", "Sunlight Liquid 2L", "Simba Chips 150g", "Fresh Milk 2L"};
        int[] units = {420, 380, 210, 185, 160};
        String[] revenue = {"R9,030", "R3,040", "R3,990", "R2,775", "R2,560"};
        String[] profit = {"R1,680", "R760", "R840", "R555", "R480"};
        String[] colors = {"#e53935", "#ff6f00", "#f9a825", "#e85d04", "#42a5f5"};

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < names.length; i++) {
            View row = inflater.inflate(R.layout.item_top_seller, container, false);
            TextView rank = row.findViewById(R.id.sellerRank);
            rank.setText(String.valueOf(i + 1));
            tint(rank, colors[i]);
            ((TextView) row.findViewById(R.id.sellerName)).setText(names[i]);
            ((TextView) row.findViewById(R.id.sellerUnits)).setText(units[i] + " units sold");
            ((TextView) row.findViewById(R.id.sellerRevenue)).setText(revenue[i]);
            ((TextView) row.findViewById(R.id.sellerProfit)).setText(profit[i] + " profit");
            container.addView(row);
        }
    }

    private void bindInventoryInsights() {
        LinearLayout grid = findViewById(R.id.inventoryGrid);
        String[] labels = {"Fast Movers", "Slow Movers", "Low Stock", "Out of Stock"};
        String[] values = {"24", "6", "8", "3"};
        String[] tones = {"#16a34a", "#f59e0b", "#f59e0b", "#dc2626"};

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
            View card = inflater.inflate(R.layout.item_inv_insight, row, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginStart(i % 2 == 1 ? dp(5) : 0);
            clp.setMarginEnd(i % 2 == 0 ? dp(5) : 0);
            card.setLayoutParams(clp);

            tint(card.findViewById(R.id.insightDot), tones[i]);
            TextView value = card.findViewById(R.id.insightValue);
            value.setText(values[i]);
            value.setTextColor(Color.parseColor(tones[i]));
            ((TextView) card.findViewById(R.id.insightLabel)).setText(labels[i]);
            row.addView(card);
        }
    }

    private void bindModules() {
        LinearLayout container = findViewById(R.id.moduleContainer);
        String[] titles = {"anokiX Wallet Analytics", "anokiX Rewards Analytics", "Marketplace Analytics"};
        String[][] keys = {
                {"Wallet Inflows", "Wallet Outflows", "Settlements", "Active Loan"},
                {"Points Earned", "Cashback Earned", "Points Redeemed", "Active Promotions"},
                {"Orders Placed", "Distributor Spend", "Avg. Basket Size", "Top Distributor"}
        };
        String[][] vals = {
                {"R85,400", "R62,300", "R120,000", "R15,000"},
                {"12,500", "R450", "8,200", "14"},
                {"156", "R245,600", "R1,575", "Tiger Brands"}
        };
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int m = 0; m < titles.length; m++) {
            View card = inflater.inflate(R.layout.item_module_analytics, container, false);
            ((TextView) card.findViewById(R.id.moduleTitle)).setText(titles[m]);
            LinearLayout stats = card.findViewById(R.id.moduleStats);
            for (int i = 0; i < keys[m].length; i++) {
                View kv = inflater.inflate(R.layout.item_kv_row, stats, false);
                ((TextView) kv.findViewById(R.id.kvLabel)).setText(keys[m][i]);
                ((TextView) kv.findViewById(R.id.kvValue)).setText(vals[m][i]);
                stats.addView(kv);
            }
            container.addView(card);
        }
    }

    private static void tint(View v, String hex) {
        v.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
