package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/common/debit-requests — the debit orders standing against the
 * trader's own IMB wallet, newest first, plus the rules the create form must respect.
 *
 * A debit order authorises IMB to pull money INTO the wallet from the merchant's bank
 * account. The account debited is resolved server-side from the caller's active wallet,
 * so a merchant can only ever debit themselves.
 */
public class DebitRequestsData {

    public List<DebitRequest> debit_requests;
    /** False when the IMB payment rail is not configured — the create form is then closed. */
    public boolean available;
    /** Null until the wallet is active. */
    public String account_number;
    public Limits limits;

    /** Amount / reference bounds the create form validates against. */
    public static class Limits {
        public double min_amount;
        public double max_amount;
        public int max_reference;

        public double min() { return min_amount > 0 ? min_amount : 1; }
        public double max() { return max_amount > 0 ? max_amount : 500000; }
        public int maxReference() { return max_reference > 0 ? max_reference : 20; }
    }

    /** Fallback bounds for when the API sends no {@code limits} block. */
    public Limits limitsOrDefault() {
        return limits != null ? limits : new Limits();
    }

    public static class DebitRequest {
        public int id;
        /** Shows on the debit itself. Max 20 characters. */
        public String reference;
        public String account_number;
        public double amount;
        public String currency;
        /** True = collected once; false = collected monthly between the two dates. */
        public boolean once_off;
        /** "once-off" | "monthly" | "completed". */
        public String schedule;
        /** "yyyy-MM-dd". Null on a once-off. */
        public String next_debit_date;
        /** "yyyy-MM-dd". Null on a once-off. */
        public String final_debit_date;
        /** "pending" | "active" | "failed" — "active" means IMB accepted the instruction. */
        public String status;
        public String imb_request_id;
        public String imb_status;
        /** Why IMB declined, on a failed row. */
        public String last_error;
        /** "dd/MM/yyyy HH:mm:ss". */
        public String created_at;

        public boolean isFailed() {
            return "failed".equalsIgnoreCase(status == null ? "" : status);
        }
    }
}
