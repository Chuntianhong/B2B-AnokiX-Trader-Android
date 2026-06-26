package com.anokix.trader.model;

import com.google.gson.annotations.SerializedName;

/**
 * A sellable POS product from {@code GET api/trader/pos/products}. Prices are
 * VAT-inclusive rands; {@code units} is the current on-hand stock.
 */
public class PosProduct {
    public int id;
    public String name;
    public String sku;
    public String barcode;
    public String category;
    public String categoryKey;
    public double price;
    public int units;
    @SerializedName("in_stock")
    public boolean inStock;
    @SerializedName("image_url")
    public String imageUrl;

    public boolean sellable() {
        return inStock && units > 0;
    }
}
