package com.anokix.trader.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/grvs — the trader's Goods Received Vouchers plus a
 * KPI summary. A GRV is auto-created (status "pending") when an order is delivered;
 * stock is only added to inventory once the trader confirms the receipt.
 */
public class GrvListData {

    public List<Grv> grvs;
    public Summary summary;

    /** One Goods Received Voucher (header + line items). */
    public static class Grv {
        public String id;
        public String grv_number;
        public String order_id;
        public String invoice_id;
        public String trader_id;
        public String distributor_id;
        /** "pending" | "partially_received" | "received". */
        public String status;
        public String note;
        public String confirmed_by;
        public String confirmed_at;
        public String created_at;
        public String updated_at;
        public String order_number;
        /** "yyyy-MM-dd". */
        public String delivery_date;
        public String invoice_number;
        public String distributor_name;
        public List<Item> items;

        public boolean isPending() {
            return "pending".equalsIgnoreCase(status);
        }

        public int lineCount() {
            return items == null ? 0 : items.size();
        }
    }

    /** One line on a GRV. {@code unit_price} is in Rand. */
    public static class Item {
        public int id;
        public int product_id;
        public String name;
        public String sku;
        public int ordered_quantity;
        public int received_quantity;
        public int damaged_quantity;
        public double unit_price;
    }

    /** KPI cards shown at the top of the GRV screen. */
    public static class Summary {
        public int total_grvs;
        public int awaiting;
        public int received;
        public int line_items;
    }
}
