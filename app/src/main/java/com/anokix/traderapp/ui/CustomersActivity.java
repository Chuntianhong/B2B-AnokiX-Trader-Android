package com.anokix.traderapp.ui;

import androidx.appcompat.app.AlertDialog;

import com.anokix.traderapp.data.MockData;
import com.anokix.traderapp.model.ListItem;

import java.util.List;

/** Customers: profiles, purchase history, loyalty tracking. Tap a customer for details. */
public class CustomersActivity extends BaseListActivity {
    @Override
    protected String getScreenTitle() {
        return "Customers";
    }

    @Override
    protected List<ListItem> getItems() {
        return MockData.getCustomers();
    }

    @Override
    protected void onItemClick(ListItem item) {
        String message = item.subtitle + "\nSegment: " + item.badge + "\n\n"
                + "Recent purchases:\n"
                + "· Maize Meal 10kg — R112.50\n"
                + "· Airtime R50 — R50.00\n"
                + "· Coca-Cola 2L — R24.00\n\n"
                + "Loyalty points: 1,240";
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .setNeutralButton("Add note", null)
                .show();
    }
}
