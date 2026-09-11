package com.anokix.traderapp.network.dto;

import java.util.Map;

import org.json.JSONObject;

/**
 * The three calls behind a card payment taken <b>on the POS terminal the app is running
 * on</b> (PayCloud "Same-terminal Application Integration"), as opposed to pushing the
 * amount to a separate card machine via {@code api/common/pos/payments}.
 *
 * <ol>
 *   <li>{@link Prepare} — POST api/common/pos/intent/prepare. The server creates the
 *       order, fixes the amount, and hands back the Intent to launch WiseCashier with,
 *       extras included. The app copies it verbatim: it never composes an amount or an
 *       order number itself, so it cannot book a figure the server did not issue.</li>
 *   <li>{@link Result} — POST api/common/pos/intent/result. WiseCashier's answer, sent
 *       exactly as {@code onActivityResult} delivered it. The backend classifies the
 *       result code and checks approvals with PayCloud before marking anything paid.</li>
 *   <li>{@link Status} — GET api/common/pos/payments/status?merchant_order_no=…. Polled
 *       while the answer is not final ({@code verified=false}) until the order is no
 *       longer open.</li>
 * </ol>
 */
public class PosIntentData {

    /** {@code data} of POST api/common/pos/intent/prepare. */
    public static class Prepare {
        public String business_order_no;
        public String merchant_order_no;
        public double amount;
        public String currency;
        public Payment payment;
        public LaunchIntent intent;

        /** True when there is an action and extras to launch WiseCashier with. */
        public boolean isLaunchable() {
            return intent != null
                    && intent.action != null && !intent.action.trim().isEmpty()
                    && intent.extras != null && !intent.extras.isEmpty();
        }

        /**
         * The order number the result must be reported under. The server may state it
         * at the top level or on the payment; failing both, it is always inside the
         * {@code transData} JSON it put on the Intent, so read it from there.
         */
        public String businessOrderNo() {
            if (notBlank(business_order_no)) return business_order_no.trim();
            if (payment != null && notBlank(payment.business_order_no)) {
                return payment.business_order_no.trim();
            }
            return intent == null ? null : intent.businessOrderNoFromTransData();
        }

        /**
         * The key for the status poll. Falls back to the business order number, which
         * is what the backend keys intent orders under when no separate one is given.
         */
        public String merchantOrderNo() {
            if (notBlank(merchant_order_no)) return merchant_order_no.trim();
            if (payment != null && notBlank(payment.merchant_order_no)) {
                return payment.merchant_order_no.trim();
            }
            return businessOrderNo();
        }
    }

    /**
     * The Intent the server prepared: {@code action} to launch, the {@code request_code}
     * it suggests, and the {@code extras} (version, appId, transType, transData, …) to
     * copy straight onto it.
     */
    public static class LaunchIntent {
        public String action;
        public int request_code;
        public Map<String, String> extras;

        /** {@code businessOrderNo} out of the {@code transData} extra, or null. */
        public String businessOrderNoFromTransData() {
            if (extras == null) return null;
            String transData = extras.get("transData");
            if (!notBlank(transData)) return null;
            try {
                String no = new JSONObject(transData).optString("businessOrderNo", "");
                return notBlank(no) ? no.trim() : null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * {@code data} of POST api/common/pos/intent/result.
     *
     * <p>{@code paid} — the backend has checked the approval with PayCloud (amount
     * included) and recorded the sale. {@code verified} — the answer was final either
     * way: a decline or cancel is marked failed with no money moved. When {@code verified}
     * is false (timeouts, network errors, "Z000"…) the card may still have been charged,
     * so the order stays pending and the caller polls {@link Status}.
     */
    public static class Result {
        public boolean paid;
        public boolean verified;
        public Payment payment;

        /** The most specific "why not" the backend gave, or null. */
        public String failureMessage() {
            if (payment == null) return null;
            if (notBlank(payment.result_msg)) return payment.result_msg.trim();
            if (notBlank(payment.failure_reason)) return payment.failure_reason.trim();
            return null;
        }

        public String merchantOrderNo() {
            return payment == null || !notBlank(payment.merchant_order_no)
                    ? null : payment.merchant_order_no.trim();
        }
    }

    /**
     * {@code data} of GET api/common/pos/payments/status. The fields may sit at the top
     * level or under {@code payment}, so every reader checks both.
     */
    public static class Status {
        public String merchant_order_no;
        /** "pending" | "paid" | "failed" | "cancelled" | "closed" … */
        public String status;
        public Boolean is_open;
        public Payment payment;

        /** True while the backend has not settled the order one way or the other. */
        public boolean isOpen() {
            if (is_open != null) return is_open;
            if (payment != null && payment.is_open != null) return payment.is_open;
            // No flag at all: infer from the status word, treating only a settled
            // word as closed so an unknown shape errs on the side of "keep checking".
            String s = statusKey();
            return !("paid".equals(s) || "failed".equals(s) || "cancelled".equals(s)
                    || "canceled".equals(s) || "closed".equals(s));
        }

        public boolean isPaid() {
            return "paid".equals(statusKey());
        }

        private String statusKey() {
            String s = notBlank(status) ? status
                    : (payment != null && notBlank(payment.status) ? payment.status : "");
            return s.trim().toLowerCase();
        }
    }

    /** The payment row as the backend exposes it on the calls above. */
    public static class Payment {
        public long id;
        public String merchant_order_no;
        public String business_order_no;
        public String status;
        public double amount;
        public String currency;
        public Boolean is_open;
        public String result_code;
        public String result_msg;
        public String failure_reason;
        public String sale_number;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
