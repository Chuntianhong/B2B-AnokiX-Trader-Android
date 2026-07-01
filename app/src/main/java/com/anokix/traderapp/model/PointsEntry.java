package com.anokix.traderapp.model;

/** A Points Activity row (Trader Portal /rewards). */
public class PointsEntry {
    public final String type;
    public final String title;
    public final String description;
    public final int points;
    public final boolean positive;
    public final String date;

    public PointsEntry(String type, String title, String description,
                       int points, boolean positive, String date) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.points = points;
        this.positive = positive;
        this.date = date;
    }
}
