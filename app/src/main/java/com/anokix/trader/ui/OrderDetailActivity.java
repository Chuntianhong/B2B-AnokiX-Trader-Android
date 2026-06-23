package com.anokix.trader.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Order tracking detail: order summary + a vertical status timeline
 * (Pending → Accepted → Picking → Packing → Ready → Out for Delivery → Delivered → Completed).
 */
public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_DISTRIBUTOR = "distributor";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_PAYMENT = "payment";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PRODUCTS = "products";

    private static final String[] STAGE_KEYS = {
            "pending", "accepted", "picking", "packing", "ready",
            "out_for_delivery", "delivered", "completed"
    };
    private static final String[] STAGE_LABELS = {
            "Pending", "Accepted", "Picking", "Packing", "Ready for Shipment",
            "Out for Delivery", "Delivered", "Completed"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        String id = getString(getIntent(), EXTRA_ID, "Order");
        String distributor = getString(getIntent(), EXTRA_DISTRIBUTOR, "Distributor");
        String amount = getString(getIntent(), EXTRA_AMOUNT, "R0.00");
        String payment = getString(getIntent(), EXTRA_PAYMENT, "—");
        String date = getString(getIntent(), EXTRA_DATE, "");
        String status = getString(getIntent(), EXTRA_STATUS, "pending");
        String products = getString(getIntent(), EXTRA_PRODUCTS, "");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(id);
        toolbar.setNavigationOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.detailDistributor)).setText(distributor);
        ((TextView) findViewById(R.id.detailDate)).setText(date);
        ((TextView) findViewById(R.id.detailAmount)).setText(amount);
        ((TextView) findViewById(R.id.detailPayment)).setText(payment);
        ((TextView) findViewById(R.id.detailStatus)).setText(status.replace('_', ' ').toUpperCase());

        TextView productsView = findViewById(R.id.detailProducts);
        if (products.isEmpty()) {
            productsView.setVisibility(android.view.View.GONE);
        } else {
            productsView.setText(products);
        }

        buildTimeline(status);
    }

    private void buildTimeline(String status) {
        LinearLayout timeline = findViewById(R.id.timeline);
        int currentIndex = indexOf(status);
        boolean cancelled = "cancelled".equalsIgnoreCase(status);
        int density = (int) getResources().getDisplayMetrics().density;

        for (int i = 0; i < STAGE_LABELS.length; i++) {
            boolean done = !cancelled && i < currentIndex;
            boolean current = !cancelled && i == currentIndex;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 6 * density, 0, 6 * density);

            TextView dot = new TextView(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(16 * density, 16 * density);
            dotLp.setMarginEnd(14 * density);
            dot.setLayoutParams(dotLp);
            int color;
            if (done) {
                color = 0xFF16A34A;        // green = completed stage
            } else if (current) {
                color = 0xFF7C3AED;        // purple = current stage
            } else {
                color = 0xFFCBD5E1;        // grey = future stage
            }
            dot.setBackground(circle(color));

            TextView label = new TextView(this);
            label.setText(STAGE_LABELS[i] + (current ? "  · current" : ""));
            label.setTextSize(14);
            if (done || current) {
                label.setTextColor(Color.parseColor("#0F172A"));
            } else {
                label.setTextColor(Color.parseColor("#94A3B8"));
            }
            if (current) {
                label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            }

            row.addView(dot);
            row.addView(label);
            timeline.addView(row);
        }

        if (cancelled) {
            TextView cancel = new TextView(this);
            cancel.setText("This order was cancelled.");
            cancel.setTextColor(0xFFDC2626);
            cancel.setTextSize(14);
            cancel.setPadding(0, 8 * density, 0, 0);
            timeline.addView(cancel);
        }
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int indexOf(String status) {
        for (int i = 0; i < STAGE_KEYS.length; i++) {
            if (STAGE_KEYS[i].equalsIgnoreCase(status)) {
                return i;
            }
        }
        return 0;
    }

    private static String getString(android.content.Intent intent, String key, String fallback) {
        String value = intent.getStringExtra(key);
        return value == null ? fallback : value;
    }
}
