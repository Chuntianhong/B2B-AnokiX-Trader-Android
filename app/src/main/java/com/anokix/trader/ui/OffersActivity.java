package com.anokix.trader.ui;

import androidx.appcompat.app.AlertDialog;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.ListItem;

import java.util.List;

/** Trader-facing promotions: distributor/supplier offers the trader can claim. */
public class OffersActivity extends BaseListActivity {
    @Override
    protected String getScreenTitle() {
        return "Promotions";
    }

    @Override
    protected List<ListItem> getItems() {
        return MockData.getTraderPromotions();
    }

    @Override
    protected void onItemClick(ListItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.subtitle + "\nStatus: " + item.badge)
                .setNegativeButton("Close", null);
        if ("Active".equals(item.badge)) {
            builder.setPositiveButton("Claim", (d, w) -> new AlertDialog.Builder(this)
                    .setTitle("Promotion claimed")
                    .setMessage("\"" + item.title + "\" is now active on your account.")
                    .setPositiveButton(R.string.promo_done, null)
                    .show());
        }
        builder.show();
    }
}
