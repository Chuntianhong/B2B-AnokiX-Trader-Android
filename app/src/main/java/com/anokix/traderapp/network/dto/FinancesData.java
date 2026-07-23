package com.anokix.traderapp.network.dto;

import java.util.List;
import java.util.Locale;

/**
 * Response for GET api/trader/finances — the trader's anokiX wallet ledger.
 * Returns the list of money movements (payments made for orders, payouts,
 * credit notes …) plus a pre-computed KPI {@link Summary}. The list is filtered
 * and searched locally on the device (the endpoint takes no query params).
 */
public class FinancesData {

    public List<Transaction> transactions;
    public Summary summary;

    /** One ledger entry. {@code amount} is signed (negative = money out). */
    public static class Transaction {
        public int id;
        public String reference;
        /** Raw type, e.g. "order_payment", "invoice", "payout", "credit_note". */
        public String type;
        public String counterparty;
        public String counterparty_phone;
        public String order_number;
        public String description;
        public double amount;
        public String currency;
        /** "paid" | "pending" | "overdue" | "partial" | "failed" | "reversed". */
        public String status;
        /** Optional — the API has no method field yet (see requirements). */
        public String payment_method;
        /** "yyyy-MM-dd HH:mm:ss". */
        public String created_at;

        /** Coarse type bucket used by the Type filter (Invoice/Payment/Payout/Credit Note). */
        public String typeGroup() {
            String t = type == null ? "" : type.toLowerCase(Locale.US);
            if (t.contains("invoice")) return "invoice";
            if (t.contains("payout")) return "payout";
            if (t.contains("credit")) return "credit_note";
            return "payment"; // order_payment, payment, …
        }

        /** Human label for the type badge. */
        public String typeLabel() {
            switch (typeGroup()) {
                case "invoice":     return "Invoice";
                case "payout":      return "Payout";
                case "credit_note": return "Credit Note";
                default:            return "Payment";
            }
        }

        /** Method key for the (future) Payment Method filter; null until the API sends one. */
        public String methodKey() {
            if (payment_method == null || payment_method.isEmpty()) return null;
            return payment_method.toLowerCase(Locale.US);
        }
    }

    /** Pre-computed KPI totals returned alongside the list. */
    public static class Summary {
        public double spent;
        public double spent_month;
        public double pending;
        public double commission_month;
        public int count;
    }
}
