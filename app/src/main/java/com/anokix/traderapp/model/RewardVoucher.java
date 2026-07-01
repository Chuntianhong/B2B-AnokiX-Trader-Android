package com.anokix.traderapp.model;

/** A Quick Redeem voucher (Trader Portal /rewards). */
public class RewardVoucher {
    public final String brand;
    public final String title;
    public final int points;
    public final String colorHex;

    public RewardVoucher(String brand, String title, int points, String colorHex) {
        this.brand = brand;
        this.title = title;
        this.points = points;
        this.colorHex = colorHex;
    }
}
