package com.anokix.traderapp.model;

/** A support ticket (Trader Portal /support). */
public class SupportTicket {
    public final String id;
    public final String title;
    public final String description;
    /** open | in_progress | resolved */
    public final String status;
    public final String updated;

    public SupportTicket(String id, String title, String description, String status, String updated) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.updated = updated;
    }
}
