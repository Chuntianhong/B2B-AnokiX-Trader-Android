package com.anokix.trader.model;

/** An exclusive marketplace promotion (Trader Portal /marketplace). */
public class MarketPromo {
    public final String brand;
    public final String offer;
    public final String detail;
    public final String expires;
    /** Tone key: green | purple | orange. */
    public final String tone;

    public MarketPromo(String brand, String offer, String detail, String expires, String tone) {
        this.brand = brand;
        this.offer = offer;
        this.detail = detail;
        this.expires = expires;
        this.tone = tone;
    }
}
