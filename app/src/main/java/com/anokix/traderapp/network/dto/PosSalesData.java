package com.anokix.traderapp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** {@code data} block of {@code GET api/trader/pos/sales} — history + summary. */
public class PosSalesData {
    public List<SaleRow> sales;
    public Summary summary;
    public Pagination pagination;

    public static class SaleRow {
        public int id;
        @SerializedName("sale_number")
        public String saleNumber;
        public double subtotal;
        public double discount;
        @SerializedName("tax_amount")
        public double taxAmount;
        @SerializedName("total_amount")
        public double totalAmount;
        @SerializedName("item_count")
        public int itemCount;
        @SerializedName("payment_method")
        public String paymentMethod;
        @SerializedName("created_at")
        public String createdAt;
    }

    public static class Summary {
        @SerializedName("total_sales")
        public double totalSales;
        public int transactions;
        @SerializedName("average_sale")
        public double averageSale;
        @SerializedName("items_sold")
        public int itemsSold;
    }

    public static class Pagination {
        public int page;
        @SerializedName("per_page")
        public int perPage;
        public int total;
        @SerializedName("total_pages")
        public int totalPages;
    }
}
