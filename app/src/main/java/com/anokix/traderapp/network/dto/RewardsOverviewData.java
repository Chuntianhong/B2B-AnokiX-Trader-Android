package com.anokix.traderapp.network.dto;

import java.util.List;
import java.util.Locale;

/**
 * Response for GET api/common/rewards/overview — the whole anokiX rewards (Limes) screen
 * in one round trip: points balance, benefits, tier progress, the points ledger, the
 * voucher catalog, running promotions and the trader's invite code.
 *
 * Points live in an append-only ledger, so {@code balance.points} is the sum of the
 * activity feed rather than a stored figure.
 */
public class RewardsOverviewData {

    public Balance balance;
    public Benefits benefits;
    public Tier tier;
    public List<Activity> activity;
    public Pagination pagination;
    public List<Voucher> vouchers;
    public List<Promotion> promotions;
    public Referral referral;

    /** The green hero card: what the trader has, what it is worth, what is about to lapse. */
    public static class Balance {
        public long points;
        /** Rand value of {@link #points}. */
        public double value;
        public long earned_this_month;
        /** Points that lapse on {@link #expiring_date}. Zero when nothing is due to lapse. */
        public long expiring;
        /** Already formatted for display, e.g. "18 Sep 2026". */
        public String expiring_date;
    }

    /** The "Your Benefits" card. */
    public static class Benefits {
        /** Cashback available, in Rand. */
        public double cashback;
        /** Same value as {@link Tier#level}; the card shows it as the monthly bonus level. */
        public String tier;
        /** Points earned from referrals so far. */
        public long referral;
    }

    /** Progress towards the next tier. */
    public static class Tier {
        public String level;
        /** Null once the trader is on the top tier. */
        public String next_level;
        public long points_away;
        public long current;
        public long target;

        /** 0..1 for the progress bar. Guarded so a zero/absent target cannot divide by zero. */
        public float fraction() {
            if (target <= 0) return 0f;
            float f = (float) current / (float) target;
            return Math.max(0f, Math.min(1f, f));
        }

        public boolean hasNextLevel() {
            return next_level != null && !next_level.isEmpty();
        }
    }

    /** One entry in the points ledger. */
    public static class Activity {
        public long id;
        /** "earned" | "cashback" | "redeemed" | "expired" — new kinds may appear. */
        public String type;
        public String title;
        public String description;
        public long points;
        /** False for a debit (a redemption or an expiry). */
        public boolean positive;
        /** "dd/MM/yyyy HH:mm:ss". */
        public String date;

        /** "+20" / "−1 000". */
        public String signedPoints() {
            return (positive ? "+" : "−") + String.format(Locale.US, "%,d", points);
        }
    }

    public static class Pagination {
        public int page;
        public int per_page;
        public int total;
        public int total_pages;

        public boolean hasMore() {
            return page < total_pages;
        }
    }

    /** One item in the redemption catalog. */
    public static class Voucher {
        public int id;
        public String brand;
        public String title;
        /** "airtime" | "data" | "grocery" — drives the icon only. */
        public String kind;
        public long points;
        /** Rand value of the voucher. */
        public double cash_value;
        /** "limes" fulfils live; "manual" is recorded pending. */
        public String provider;
        /** Brand colour, e.g. "#ffcc00". */
        public String color;
        /** True when the API needs a recipient phone number to fulfil (Limes airtime/data). */
        public boolean needs_msisdn;

        /** First letter of the brand, for the coloured tile. */
        public String initial() {
            return brand == null || brand.isEmpty() ? "?" : brand.substring(0, 1).toUpperCase(Locale.US);
        }

        public String pointsLabel() {
            return String.format(Locale.US, "%,d pts", points);
        }
    }

    /** One running promotion. Copy comes from the server; {@code tone} picks the colour. */
    public static class Promotion {
        public long id;
        public String title;
        public String description;
        /** "green" | "lime" | "purple" — anything else falls back to purple. */
        public String tone;
        public String link_label;
    }

    /** Invite &amp; Earn. */
    public static class Referral {
        public String code;
        /** Points earned from referrals so far. */
        public long earnings;
        /** Points paid per successful referral. */
        public long reward;
    }

    // ---- Redeem ----------------------------------------------------------

    /**
     * Response for POST api/common/rewards/redeem: the recorded redemption plus the
     * balance left afterwards, so the screen can settle without re-reading the overview.
     */
    public static class RedeemResult {
        public Redemption redemption;
        public Balance balance;
    }

    public static class Redemption {
        public long id;
        public int voucher_id;
        public String brand;
        public String title;
        public long points;
        /** "pending" | "fulfilled" | "failed". */
        public String status;
        public String msisdn;
        public String created_at;
    }
}
