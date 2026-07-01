package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/inventory/history?product_id= — one product's stock
 * snapshot plus its full movement ledger.
 */
public class InventoryHistoryData {

    public Product product;
    public int on_hand;
    public double average_cost;
    public double stock_value;
    public int low_stock_threshold;
    public String status;
    public String updated_at;
    public List<InventoryData.Movement> movements;

    public static class Product {
        public int id;
        public String name;
        public String sku;
        public String barcode;
        public String category;
        public String brand;
    }
}
