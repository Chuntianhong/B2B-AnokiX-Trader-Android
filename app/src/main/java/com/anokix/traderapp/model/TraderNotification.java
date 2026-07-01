package com.anokix.traderapp.model;

/** A notification (Trader Portal /notifications). */
public class TraderNotification {
    public final String type;
    public final String title;
    public final String message;
    public final String time;
    public final boolean read;
    public final boolean important;
    public final String route;

    public TraderNotification(String type, String title, String message, String time,
                              boolean read, boolean important, String route) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.time = time;
        this.read = read;
        this.important = important;
        this.route = route;
    }
}
