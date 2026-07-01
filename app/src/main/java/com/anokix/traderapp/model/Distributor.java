package com.anokix.traderapp.model;

/** A marketplace distributor partner (Trader Portal /marketplace). */
public class Distributor {
    public final String name;
    public final String region;
    public final String type;
    public final String rating;
    public final String products;
    public final String colorHex;

    public Distributor(String name, String region, String type,
                       String rating, String products, String colorHex) {
        this.name = name;
        this.region = region;
        this.type = type;
        this.rating = rating;
        this.products = products;
        this.colorHex = colorHex;
    }
}
