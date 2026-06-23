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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.MarketPromo;
import com.anokix.trader.model.PointsEntry;
import com.anokix.trader.model.RewardVoucher;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/** anokiX rewards (Limes) — mirrors the Trader Portal /rewards page. */
public class RewardsActivity extends AppCompatActivity {

    private static final String[] VAS_LABELS = {"Airtime", "Data", "Electricity", "Water", "TV", "More"};
    private static final String[] VAS_DESC = {
            "Top up instantly", "Buy data bundles", "Prepaid & Postpaid",
            "Pay your bill", "DSTV, GOtv & more", "View all services"};
    private static final String[] VAS_COLORS = {"#22c55e", "#3b82f6", "#f59e0b", "#06b6d4", "#8b5cf6", "#64748b"};
    private static final int[] VAS_ICONS = {
            R.drawable.ic_promo_tag, R.drawable.ic_promo_rocket, R.drawable.ic_finances,
            R.drawable.ic_document, R.drawable.ic_promo_store, R.drawable.ic_dashboard};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.inviteButton).setOnClickListener(v ->
                Toast.makeText(this, "Share your invite link to earn Limes points", Toast.LENGTH_SHORT).show());

        buildVouchers();
        buildVas();
        buildPromos();
        buildPointsActivity();
    }

    private void buildVouchers() {
        LinearLayout row = findViewById(R.id.voucherRow);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (RewardVoucher v : MockData.getRewardVouchers()) {
            View card = inflater.inflate(R.layout.item_reward_voucher, row, false);
            TextView brand = card.findViewById(R.id.voucherBrand);
            brand.setText(v.brand);
            tint(brand, v.colorHex);
            ((TextView) card.findViewById(R.id.voucherTitle)).setText(v.title);
            ((TextView) card.findViewById(R.id.voucherPoints))
                    .setText(String.format(Locale.US, "%,d pts", v.points));
            card.findViewById(R.id.voucherRedeem).setOnClickListener(b -> redeem(v));
            row.addView(card);
        }
    }

    private void redeem(RewardVoucher v) {
        new AlertDialog.Builder(this)
                .setTitle("Redeem with Limes")
                .setMessage(v.brand + " " + v.title + "\n\nRedeem for " +
                        String.format(Locale.US, "%,d", v.points) + " points?")
                .setPositiveButton("Redeem", (d, w) -> new AlertDialog.Builder(this)
                        .setTitle("Redeemed")
                        .setMessage(v.title + " has been added to your account.")
                        .setPositiveButton(R.string.promo_done, null)
                        .show())
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void buildVas() {
        LinearLayout container = findViewById(R.id.vasContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = null;
        for (int i = 0; i < VAS_LABELS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(3);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(12);
                container.addView(row, lp);
            }
            View cell = inflater.inflate(R.layout.item_quick_action, row, false);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cell.setLayoutParams(cellLp);

            View iconBox = cell.findViewById(R.id.quickIconContainer);
            ImageView icon = cell.findViewById(R.id.quickIcon);
            TextView label = cell.findViewById(R.id.quickLabel);
            int color = Color.parseColor(VAS_COLORS[i]);
            iconBox.setBackgroundTintList(ColorStateList.valueOf(withAlpha(color, 28)));
            icon.setImageResource(VAS_ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(color));
            label.setText(VAS_LABELS[i]);

            final String name = VAS_LABELS[i];
            final String desc = VAS_DESC[i];
            cell.setOnClickListener(v ->
                    Toast.makeText(this, name + " — " + desc, Toast.LENGTH_SHORT).show());
            row.addView(cell);
        }
    }

    private void buildPromos() {
        LinearLayout column = findViewById(R.id.rewardsPromoColumn);
        LayoutInflater inflater = LayoutInflater.from(this);
        // title / description / tone (from portal /rewards promos)
        String[][] promos = {
                {"Double Points", "Earn 2X Limes Points on all POS transactions this week", "green"},
                {"R20 Cashback", "Buy airtime or data and get R20 cashback", "lime"},
                {"Utility Week", "Pay any utility bill & get 100 bonus Limes points!", "purple"}
        };
        for (String[] p : promos) {
            View card = inflater.inflate(R.layout.item_market_promo, column, false);
            String hex = toneColor(p[2]);
            tint(card.findViewById(R.id.promoAccent), hex);
            ((TextView) card.findViewById(R.id.promoBrand)).setText(p[0]);
            ((TextView) card.findViewById(R.id.promoDetail)).setText(p[1]);
            card.findViewById(R.id.promoExpires).setVisibility(View.GONE);
            TextView offer = card.findViewById(R.id.promoOffer);
            offer.setText("View");
            tint(offer, hex);
            column.addView(card);
        }
    }

    private void buildPointsActivity() {
        LinearLayout column = findViewById(R.id.pointsColumn);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PointsEntry e : MockData.getPointsActivity()) {
            View row = inflater.inflate(R.layout.item_points_entry, column, false);
            ImageView icon = row.findViewById(R.id.pointsIcon);
            int iconRes;
            String hex;
            switch (e.type) {
                case "cashback": iconRes = R.drawable.ic_wallet;     hex = "#16A34A"; break;
                case "redeemed": iconRes = R.drawable.ic_promo_tag;  hex = "#7C3AED"; break;
                default:         iconRes = R.drawable.ic_promo_gift; hex = "#16A34A"; break;
            }
            icon.setImageResource(iconRes);
            tint((View) icon.getParent(), hex);
            ((TextView) row.findViewById(R.id.pointsTitle)).setText(e.title);
            ((TextView) row.findViewById(R.id.pointsDesc)).setText(e.description + " · " + e.date);
            TextView value = row.findViewById(R.id.pointsValue);
            int color = androidx.core.content.ContextCompat.getColor(this,
                    e.positive ? R.color.success : R.color.danger);
            value.setTextColor(color);
            value.setText(String.format(Locale.US, "%s%,d", e.positive ? "+" : "−", e.points));
            column.addView(row);
        }
    }

    private static String toneColor(String tone) {
        switch (tone) {
            case "green": return "#16A34A";
            case "lime":  return "#65A30D";
            default:      return "#7C3AED";
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void tint(View v, String hex) {
        v.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
