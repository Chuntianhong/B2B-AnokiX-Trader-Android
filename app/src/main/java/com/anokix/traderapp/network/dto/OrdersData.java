package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * data block of GET /api/trader/orders (list).
 *
 * Shape: {@code orders[]} (one per order, with a {@code products} name list +
 * {@code item_count}/{@code line_count}), {@code pagination}, and
 * {@code status_counts} (per-status totals for the filter tabs).
 */
public class OrdersData {

    public List<Order> orders;
    public Pagination pagination;
    public StatusCounts status_counts;

    /** A single order. The list endpoint fills {@code products}; the detail endpoint fills {@code items}. */
    public static class Order {
        public long id;
        public String order_number;
        public long distributor_id;
        public String status;
        public String status_key;
        public double subtotal;
        public double total_amount;
        public String delivery_date;
        public String delivery_start_time;
        public String delivery_end_time;
        public String notes;
        public DeliveryAddress delivery_address;
        public Distributor distributor;
        public String created_at;
        public String updated_at;
        public int item_count;
        public int line_count;
        public List<String> products;   // list endpoint
        public List<Item> items;        // detail endpoint

        public String distributorName() {
            if (distributor != null && distributor.display_name != null
                    && !distributor.display_name.trim().isEmpty()) {
                return distributor.display_name;
            }
            return "Distributor";
        }

        public String statusKey() {
            return status_key != null && !status_key.isEmpty() ? status_key
                    : (status != null ? status.toLowerCase() : "pending");
        }
    }

    public static class DeliveryAddress {
        public String business;
        public String address;
        public String latitude;
        public String longitude;
    }

    public static class Distributor {
        public long id;
        public String display_name;
    }

    public static class Item {
        public long id;
        public long order_id;
        public long cart_item_id;
        public long product_id;
        public long distributor_id;
        public int quantity;
        public double unit_price;
        public double line_total;
        public String status;
        public Product product;
    }

    public static class Product {
        public long id;
        public String name;
        public String sku;
        public String barcode;
        public String image_url;
    }

    public static class Pagination {
        public int page;
        public int per_page;
        public int total;
        public int total_pages;
    }

    public static class StatusCounts {
        public int all;
        public int pending;
        public int accepted;
        public int picking;
        public int packing;
        public int out_for_delivery;
        public int delivered;
        public int cancelled;

        public int forKey(String key) {
            if (key == null) return 0;
            switch (key) {
                case "all":              return all;
                case "pending":          return pending;
                case "accepted":         return accepted;
                case "picking":          return picking;
                case "packing":          return packing;
                case "out_for_delivery": return out_for_delivery;
                case "delivered":        return delivered;
                case "cancelled":        return cancelled;
                default:                 return 0;
            }
        }
    }
}
