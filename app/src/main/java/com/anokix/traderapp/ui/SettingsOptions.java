package com.anokix.traderapp.ui;

/**
 * Fixed option sets for the Settings → Preferences & Notifications screens,
 * matching the web portal design. Currency is sent to the API as a numeric id
 * (ZAR=1, USD=2, EUR=3 per requirements); reads accept either the id or the code.
 */
public final class SettingsOptions {

    private SettingsOptions() {}

    // Currency — the API takes the ISO code ("ZAR"), which is also what we display.
    // CURRENCY_IDS are the legacy numeric ids the backend used to store; reads still
    // accept them so an account saved before the switch resolves to the right option.
    public static final String[] CURRENCY_LABELS = {"ZAR", "USD", "EUR"};
    public static final String[] CURRENCY_VALUES = {"ZAR", "USD", "EUR"};
    public static final String[] CURRENCY_IDS    = {"1", "2", "3"};

    // Language
    public static final String[] LANGUAGE_LABELS = {"English", "Afrikaans", "Zulu"};
    public static final String[] LANGUAGE_VALUES = {"en", "af", "zu"};

    // Date format — display sample vs the value the API expects.
    public static final String[] DATEFMT_LABELS = {"19 May 2024", "05/19/2024", "2024-05-19"};
    public static final String[] DATEFMT_VALUES = {"DD MMM YYYY", "MM/DD/YYYY", "YYYY-MM-DD"};

    // Airtime & VAS payment — how VAS purchases are settled.
    public static final String[] VAS_PAYMENT_LABELS = {
            "anokiX wallet (debit on purchase)", "Direct settlement with Limes"
    };
    public static final String[] VAS_PAYMENT_VALUES = {"wallet", "direct"};

    // Notification toggles (key, title, description) + order — the web portal's
    // Notification Preferences screen, plus "lowStockAlerts" so both portals persist the
    // same six keys (see FCM-settings/3-Event-Catalogue.md §7). An absent key is
    // treated as ON, so adding one is backwards compatible with saved preferences.
    public static final String[][] TOGGLES = {
            {"orderAlerts", "Order Alerts", "Get notified about your order status changes."},
            {"deliveryUpdates", "Delivery Updates", "Track delivery status changes and delays."},
            {"lowStockAlerts", "Low Stock Alerts", "Alerts when a product drops below its reorder level."},
            {"promotionAlerts", "Promotion Alerts", "Hear about new deals and promotions from distributors."},
            {"financeReminders", "Finance Reminders", "Reminders for overdue invoices and payments."},
            {"reportReady", "Report Ready", "Notify when scheduled reports are generated."},
    };

    /** Map a stored currency ("ZAR"/"USD"/"EUR" code or a legacy "1"/"2"/"3" id) to its option index. */
    public static int currencyIndex(String stored) {
        if (stored == null) return 0;
        for (int i = 0; i < CURRENCY_VALUES.length; i++) {
            if (CURRENCY_VALUES[i].equalsIgnoreCase(stored) || CURRENCY_IDS[i].equals(stored)) return i;
        }
        return 0;
    }

    /** Index of {@code value} in {@code values}, or 0 if not found. */
    public static int indexOf(String[] values, String value) {
        if (value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equalsIgnoreCase(value)) return i;
            }
        }
        return 0;
    }

    /** Build the "(GMT+2)" suffix from a timezone's seconds offset. */
    public static String gmtLabel(long offsetSeconds) {
        long totalMinutes = offsetSeconds / 60;
        long h = Math.abs(totalMinutes) / 60;
        long m = Math.abs(totalMinutes) % 60;
        String sign = totalMinutes < 0 ? "-" : "+";
        return m == 0 ? "GMT" + sign + h : String.format("GMT%s%d:%02d", sign, h, m);
    }
}
