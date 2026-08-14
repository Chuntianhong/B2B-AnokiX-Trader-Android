package com.anokix.traderapp.network.dto;

import java.util.List;
import java.util.Locale;

/**
 * Response for GET api/common/support/overview — the Support Centre landing screen:
 * which contact channels are switched on, the help-article taxonomy and the trader's
 * own tickets.
 */
public class SupportOverviewData {

    public Channels channels;
    /** Help-article topics. Empty while the knowledge base is still being written. */
    public List<Category> categories;
    /** Popular articles. Empty while the knowledge base is still being written. */
    public List<Article> popular;
    public List<Ticket> tickets;
    public int inbox_awaiting;

    /** Which ways of reaching support are enabled for this merchant. */
    public static class Channels {
        public Toggle tickets;
        public Toggle callback;
        public WhatsApp whatsapp;
        public Toggle live_chat;
        public Toggle atlas;
        public Email email;
        public Phone phone;
        /** Free-text opening hours, e.g. "Mon–Fri, 8am–5pm". May be blank. */
        public String hours;

        public boolean ticketsEnabled()  { return tickets  != null && tickets.enabled; }
        public boolean callbackEnabled() { return callback != null && callback.enabled; }
        public boolean whatsappEnabled() { return whatsapp != null && whatsapp.enabled; }
        public boolean emailEnabled() {
            return email != null && email.enabled && email.address != null && !email.address.isEmpty();
        }
        public boolean phoneEnabled() {
            return phone != null && phone.enabled && phone.number != null && !phone.number.isEmpty();
        }
    }

    public static class Toggle {
        public boolean enabled;
    }

    public static class WhatsApp {
        public boolean enabled;
        /** Ready-made wa.me link with the greeting pre-filled. */
        public String link;
        /** Human-readable number, e.g. "+1 5674987536". */
        public String display;

        /** Digits only, for the whatsapp:// deep link. Null when the link has no number. */
        public String msisdn() {
            String source = (display != null && !display.isEmpty()) ? display : link;
            if (source == null) return null;
            String digits = source.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? null : digits;
        }
    }

    public static class Email {
        public boolean enabled;
        public String address;
    }

    public static class Phone {
        public boolean enabled;
        public String number;
    }

    public static class Category {
        public String key;
        public String name;
    }

    public static class Article {
        public int id;
        public String title;
        public String excerpt;
    }

    /** One support ticket. {@link #messages} is only populated by the detail endpoint. */
    public static class Ticket {
        public int id;
        public String ticket_number;
        public String subject;
        public String category;
        /** "web" | "callback". */
        public String channel;
        /** "open" | "closed". */
        public String status;
        /** "low" | "normal" | "high" | "urgent". */
        public String priority;
        public String callback_phone;
        /** "dd/MM/yyyy HH:mm:ss". */
        public String created_at;
        /** "yyyy-MM-dd HH:mm:ss". */
        public String last_reply_at;
        /** "merchant" | "agent" | "system". */
        public String last_reply_by;
        public String resolved_at;
        public boolean is_open;
        public List<Message> messages;

        /** "Open" / "Closed". */
        public String statusLabel() {
            return is_open ? "Open" : "Closed";
        }

        /** Sub-line on the list row: who we are waiting on. */
        public String waitingLabel() {
            if (!is_open) return "Closed";
            String by = last_reply_by == null ? "" : last_reply_by.toLowerCase(Locale.US);
            return "merchant".equals(by) ? "Waiting for support" : "Support replied";
        }
    }

    /** One entry in a ticket conversation. */
    public static class Message {
        public int id;
        /** "merchant" | "agent" | "system". */
        public String author_type;
        public String author_name;
        public String body;
        public boolean is_internal;
        /** "dd/MM/yyyy HH:mm:ss". */
        public String created_at;

        public boolean isMine() {
            return "merchant".equalsIgnoreCase(author_type == null ? "" : author_type);
        }

        public boolean isSystem() {
            return "system".equalsIgnoreCase(author_type == null ? "" : author_type);
        }
    }
}
