package com.anokix.trader.model;

/** A single line in the POS cart: a product, its unit price, and the quantity. */
public class CartLine {
    public final String productId;
    public final String name;
    public final double unitPrice;
    public int qty;

    public CartLine(String productId, String name, double unitPrice, int qty) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.qty = qty;
    }

    public double lineTotal() {
        return unitPrice * qty;
    }
}
