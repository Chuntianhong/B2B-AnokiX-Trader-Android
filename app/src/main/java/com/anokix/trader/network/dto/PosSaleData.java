package com.anokix.trader.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * {@code data} block of {@code POST api/trader/pos/sale} — the completed sale
 * receipt. VAT is extracted from the inclusive total ({@code tax_amount}).
 */
public class PosSaleData {
    public Sale sale;

    public static class Sale {
        public int id;
        @SerializedName("sale_number")
        public String saleNumber;
        public double subtotal;
        public double discount;
        @SerializedName("tax_amount")
        public double taxAmount;
        @SerializedName("tax_rate")
        public double taxRate;
        @SerializedName("subtotal_excl_tax")
        public double subtotalExclTax;
        @SerializedName("total_amount")
        public double totalAmount;
        @SerializedName("item_count")
        public int itemCount;
        @SerializedName("payment_method")
        public String paymentMethod;
        public List<Item> items;
    }

    public static class Item {
        @SerializedName("product_id")
        public int productId;
        public String name;
        public int quantity;
        @SerializedName("unit_price")
        public double unitPrice;
        @SerializedName("line_total")
        public double lineTotal;
    }
}
