package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/inventory — the trader's live inventory dashboard:
 * KPI summary, status breakdown chart, fast movers, per-product rows, and the
 * recent stock-movement ledger.
 */
public class InventoryData {

    public Summary summary;
    public List<ChartSlice> chart;
    public List<FastMover> fast_moving;
    public List<Row> rows;
    public List<Movement> movements;

    public static class Summary {
        public double total_stock_value;
        public int total_units;
        public int low_stock;
        public int out_of_stock;
        public int stock_movement;
        public int total_products;
    }

    /** Status breakdown slice (In Stock / Low Stock / Out of Stock), value is a percentage. */
    public static class ChartSlice {
        public String type;
        public double value;
        public String color;
    }

    public static class FastMover {
        public int rank;
        public String name;
        public int units;
    }

    /** One product line on the inventory table. */
    public static class Row {
        public int id;
        public String name;
        public String sku;
        public String barcode;
        public String category;
        public String categoryKey;
        public String brand;
        public String brandKey;
        public String warehouse;
        public String warehouseKey;
        public int units;
        public int capacity;
        public double stockValue;
        public String stockStatus;   // in_stock | low_stock | out_of_stock | damaged | expired
        public String movement;      // fast | slow
        public int soldLast30;
        public String updatedAt;
        public String imageColor;

        public int stockPercent() {
            int ceiling = Math.max(1, capacity);
            return Math.max(0, Math.min(100, (int) Math.round(units * 100.0 / ceiling)));
        }
    }

    /** One stock-ledger entry. */
    public static class Movement {
        public int id;
        public int product_id;
        public String product_name;
        public String movement_type;   // grv | grn | sale | adjustment | ...
        public String reference_number;
        public int quantity_in;
        public int quantity_out;
        public int balance_after;
        public String note;
        public String created_at;
    }
}
