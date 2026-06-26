package com.anokix.trader.model;

import java.util.Locale;

/** Goods-return reasons shared by the GRN list + the Return Goods form. */
public final class ReturnReason {

    public static final String[] KEYS = {
            "damaged", "expired", "incorrect", "overstock", "recall", "other"
    };

    public static final String[] LABELS = {
            "Damaged stock", "Expired", "Incorrect item", "Overstock", "Recall", "Other"
    };

    private ReturnReason() {}

    /** Human label for a reason key (falls back to a capitalised key). */
    public static String label(String key) {
        if (key == null || key.isEmpty()) return "";
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equalsIgnoreCase(key)) return LABELS[i];
        }
        return key.substring(0, 1).toUpperCase(Locale.US) + key.substring(1);
    }
}
