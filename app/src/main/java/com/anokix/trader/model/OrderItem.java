package com.anokix.trader.model;

public class OrderItem {
    public final String orderId;
    public final String traderName;
    public final String initials;
    public final String location;
    public final String amount;
    public final String payment;
    public final String time;
    public final String status;
    public final String relativeTime;
    public final String productSummary;
    public final boolean isPaid;

    // Trader Portal order fields (order placed by the trader to a distributor)
    public final String subtitle;     // distributor subtitle
    public final int itemsCount;      // number of line items
    public final String deliveryInfo; // est. / live / delivered text
    public final String actionLabel;  // View Details / Track Order / Track Live / View Invoice
    public final String logoColor;    // distributor logo colour (hex)

    public OrderItem(String orderId, String traderName, String initials, String location,
                     String amount, String payment, String time, String status) {
        this(orderId, traderName, initials, location, amount, payment, time, status,
                time, "", false);
    }

    public OrderItem(String orderId, String traderName, String initials, String location,
                     String amount, String payment, String time, String status,
                     String relativeTime, String productSummary, boolean isPaid) {
        this(orderId, traderName, initials, location, amount, payment, time, status,
                relativeTime, productSummary, isPaid, "", 0, "", "View Details", "#7C3AED");
    }

    public OrderItem(String orderId, String traderName, String initials, String location,
                     String amount, String payment, String time, String status,
                     String relativeTime, String productSummary, boolean isPaid,
                     String subtitle, int itemsCount, String deliveryInfo,
                     String actionLabel, String logoColor) {
        this.orderId = orderId;
        this.traderName = traderName;
        this.initials = initials;
        this.location = location;
        this.amount = amount;
        this.payment = payment;
        this.time = time;
        this.status = status;
        this.relativeTime = relativeTime;
        this.productSummary = productSummary;
        this.isPaid = isPaid;
        this.subtitle = subtitle;
        this.itemsCount = itemsCount;
        this.deliveryInfo = deliveryInfo;
        this.actionLabel = actionLabel;
        this.logoColor = logoColor;
    }
}
