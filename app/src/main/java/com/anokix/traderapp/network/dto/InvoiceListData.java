package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/invoices — the trader's invoices. An invoice is
 * generated automatically when an order is delivered. The API returns no KPI
 * summary, so the screen computes the cards (total / total value / average /
 * this month) from the list.
 */
public class InvoiceListData {

    public List<Invoice> invoices;

    /** One invoice (header + line items). All money fields are in Rand. */
    public static class Invoice {
        public String id;
        public String invoice_number;
        public String order_id;
        public String trader_id;
        public String distributor_id;
        public double subtotal;
        public double total_amount;
        /** "pending" | "partially_received" | "received". */
        public String status;
        /** "dd/MM/yyyy HH:mm:ss". */
        public String created_at;
        public String updated_at;
        public String order_number;
        public List<Item> items;

        public int lineCount() {
            return items == null ? 0 : items.size();
        }
    }

    /** One line on an invoice. {@code unit_price}/{@code line_total} are in Rand. */
    public static class Item {
        public int product_id;
        public String name;
        public String sku;
        public int quantity;
        public double unit_price;
        public double line_total;
    }
}
