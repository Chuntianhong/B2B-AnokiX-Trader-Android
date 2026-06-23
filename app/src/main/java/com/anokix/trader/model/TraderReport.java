package com.anokix.trader.model;

/** A generated report row (Trader Portal /reports). */
public class TraderReport {
    public final String name;
    public final String categoryLabel;
    public final String period;
    public final String format;
    /** ready | generating | failed */
    public final String status;
    public final String fileSize;
    public final String colorHex;

    public TraderReport(String name, String categoryLabel, String period,
                        String format, String status, String fileSize, String colorHex) {
        this.name = name;
        this.categoryLabel = categoryLabel;
        this.period = period;
        this.format = format;
        this.status = status;
        this.fileSize = fileSize;
        this.colorHex = colorHex;
    }
}
