package com.anokix.traderapp.network.dto;

/**
 * The two halves of a wallet top-up (PayCloud Hosted Checkout).
 *
 * POST api/common/topup/create starts the checkout and hands back a {@link Create}
 * carrying the URL to open; GET api/common/topup/status then reports where that
 * order got to. The money is only in the wallet once {@code credit_status} reads
 * "credited" — {@code status} "paid" merely means PayCloud took the card, and the
 * credit lands after PayCloud's signed webhook is verified server-side.
 */
public class TopupData {

    /** POST api/common/topup/create. */
    public static class Create {
        /** Our own order reference; the key for polling {@link Status}. */
        public String merchant_order_no;
        /** The PayCloud checkout page to open in the browser. */
        public String pay_url;
        public double amount;
        public String currency;

        public boolean isUsable() {
            return pay_url != null && !pay_url.isEmpty()
                    && merchant_order_no != null && !merchant_order_no.isEmpty();
        }
    }

    /** GET api/common/topup/status?merchant_order_no=… */
    public static class Status {
        public String merchant_order_no;
        /** PayCloud's own reference, once they have one. */
        public String trade_no;
        public double amount;
        public String currency;
        /** "pending" | "paid" | "failed" | "cancelled". */
        public String status;
        /** "none" | "pending" | "credited" | "failed". */
        public String credit_status;
        public String credited_at;
        public String created_at;

        private String creditKey() {
            return credit_status == null ? "" : credit_status.toLowerCase();
        }

        private String statusKey() {
            return status == null ? "" : status.toLowerCase();
        }

        /** The money is in the wallet. */
        public boolean isCredited() {
            return "credited".equals(creditKey());
        }

        /** Card taken but the wallet has not been credited yet — keep waiting. */
        public boolean isPaidNotCredited() {
            return "paid".equals(statusKey()) && !isCredited() && !"failed".equals(creditKey());
        }

        /** Nothing more will happen to this order. */
        public boolean isDead() {
            String s = statusKey();
            return "failed".equals(s) || "cancelled".equals(s) || "failed".equals(creditKey());
        }

        /** True while the trader has not finished the checkout page. */
        public boolean isPending() {
            return !isCredited() && !isDead() && !isPaidNotCredited();
        }
    }
}
