package com.anokix.trader.ui;

import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.anokix.trader.data.MockData;
import com.anokix.trader.model.ListItem;

import java.util.List;

/** Payments (IMB): card/QR/wallet payments, settlement, refunds. Tap for detail + refund. */
public class PaymentsActivity extends BaseListActivity {
    @Override
    protected String getScreenTitle() {
        return "Payments";
    }

    @Override
    protected List<ListItem> getItems() {
        return MockData.getPayments();
    }

    @Override
    protected void onItemClick(ListItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.subtitle + "\nStatus: " + item.badge)
                .setNegativeButton("Close", null);
        if ("Settled".equals(item.badge)) {
            builder.setPositiveButton("Refund", (d, w) ->
                    Toast.makeText(this, "Refund requested for " + item.title, Toast.LENGTH_SHORT).show());
        }
        builder.show();
    }
}
