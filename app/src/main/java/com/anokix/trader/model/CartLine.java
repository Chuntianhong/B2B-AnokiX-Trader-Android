package com.anokix.trader.model;

/** A single line in the POS sale: a product, its VAT-inclusive unit price, and quantity. */
public class CartLine {
    public final int productId;
    public final String name;
    public final String sku;
    public final String imageUrl;
    public final double unitPrice;
    public final int stock;
    public int qty;

    public CartLine(int productId, String name, String sku, String imageUrl,
                    double unitPrice, int stock, int qty) {
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.stock = stock;
        this.qty = qty;
    }

    public double lineTotal() {
        return unitPrice * qty;
    }
}
