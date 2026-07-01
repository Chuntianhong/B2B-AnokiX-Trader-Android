package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/inventory/analytics?days= — stock ageing buckets,
 * gross-profit summary (net ex-VAT − COGS), and reorder suggestions.
 */
public class InventoryAnalyticsData {

    public int window_days;
    public List<AgeingBucket> ageing;
    public GrossProfit gross_profit;
    public List<Reorder> reorder;

    public static class AgeingBucket {
        public String label;
        public int products;
        public int units;
        public double value;
        public String color;
    }

    public static class GrossProfit {
        public double net_revenue;
        public double cogs;
        public double gross_profit;
        public double margin_pct;
        public int units_sold;
    }

    public static class Reorder {
        public int product_id;
        public String name;
        public String sku;
        public int on_hand;
        public int threshold;
        public int sold_last_30;
        public int suggested;
        public String urgency;   // high | medium | low
    }
}
