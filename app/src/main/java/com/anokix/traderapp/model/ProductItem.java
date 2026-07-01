package com.anokix.traderapp.model;

public class ProductItem {
    public final String id;
    public final String name;
    public final String sku;
    public final String category;
    public final String price;
    public final String stockStatus;
    /** Marketplace extras (portal): pack size, optional badge, accent colour. */
    public final String pack;
    public final String badge;
    public final String colorHex;
    /** Remote image URL; null/empty falls back to the placeholder icon. */
    public String imageUrl;

    public ProductItem(String id, String name, String sku, String category,
                       String price, String stockStatus) {
        this(id, name, sku, category, price, stockStatus, "", null, "#7C3AED");
    }

    public ProductItem(String id, String name, String pack, String category,
                       String price, String stockStatus, String badge, String colorHex) {
        this(id, name, "", category, price, stockStatus, pack, badge, colorHex);
    }

    private ProductItem(String id, String name, String sku, String category,
                        String price, String stockStatus, String pack, String badge, String colorHex) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.price = price;
        this.stockStatus = stockStatus;
        this.pack = pack;
        this.badge = badge;
        this.colorHex = colorHex;
    }
}
