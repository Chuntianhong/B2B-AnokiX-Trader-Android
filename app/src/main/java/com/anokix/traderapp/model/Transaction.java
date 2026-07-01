package com.anokix.traderapp.model;

/** A wallet transaction line, mirroring the Trader Portal /wallet transactions. */
public class Transaction {
    public final String type;      // received | sent | settlement | airtime
    public final String title;
    public final String subtitle;
    public final double amount;
    public final boolean positive; // true = money in (green +), false = money out (red −)
    public final String date;
    public final String status;

    public Transaction(String type, String title, String subtitle, double amount,
                       boolean positive, String date, String status) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.amount = amount;
        this.positive = positive;
        this.date = date;
        this.status = status;
    }
}
