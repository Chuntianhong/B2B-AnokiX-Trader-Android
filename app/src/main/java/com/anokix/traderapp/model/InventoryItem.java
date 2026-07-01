package com.anokix.traderapp.model;

/**
 * A trader inventory line, mirroring the Trader Portal inventory model
 * (product / sku / category / brand / warehouse / units / value / status / movement).
 * {@code units} is mutable (adjustable on hand).
 */
public class InventoryItem {
    public final String name;
    public final String sku;
    public final String barcode;
    public final String category;
    public final String brand;
    public final String warehouse;
    public int units;
    public final int capacity;
    public final double stockValue;
    public final String statusKey;   // in_stock | low_stock | out_of_stock | damaged | expired
    public final String movement;    // fast | slow
    public final String updatedAt;
    public final String imageColor;

    public InventoryItem(String name, String sku, String barcode, String category, String brand,
                         String warehouse, int units, int capacity, double stockValue,
                         String statusKey, String movement, String updatedAt, String imageColor) {
        this.name = name;
        this.sku = sku;
        this.barcode = barcode;
        this.category = category;
        this.brand = brand;
        this.warehouse = warehouse;
        this.units = units;
        this.capacity = capacity;
        this.stockValue = stockValue;
        this.statusKey = statusKey;
        this.movement = movement;
        this.updatedAt = updatedAt;
        this.imageColor = imageColor;
    }

    /** Human-readable status label matching the portal badges. */
    public String status() {
        switch (statusKey) {
            case "out_of_stock": return "Out of Stock";
            case "low_stock":    return "Low Stock";
            case "damaged":      return "Damaged";
            case "expired":      return "Expired";
            default:             return "In Stock";
        }
    }

    /** Progress for the stock bar (0-100), units relative to capacity. */
    public int stockPercent() {
        int ceiling = Math.max(1, capacity);
        return Math.max(0, Math.min(100, (int) Math.round(units * 100.0 / ceiling)));
    }
}
