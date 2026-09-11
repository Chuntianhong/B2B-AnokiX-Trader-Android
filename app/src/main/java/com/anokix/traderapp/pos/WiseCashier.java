package com.anokix.traderapp.pos;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anokix.traderapp.network.dto.PosIntentData;

import java.util.Map;

/**
 * The PayCloud cashier app ("WiseCashier") that lives on a PayCloud POS terminal, and the
 * Intent contract it exposes to other apps on the same device ("Same-terminal Application
 * Integration"): launch {@link #ACTION} with the prepared extras via
 * {@code startActivityForResult}, and read {@code result} / {@code resultMsg} /
 * {@code transData} back out of the result Intent.
 *
 * <p>Nothing here decides what a result <i>means</i> — that is the backend's job
 * ({@code api/common/pos/intent/result}), which classifies the code and checks approvals
 * with PayCloud. This class only detects the cashier app, builds the Intent from what the
 * server prepared, and carries its answer.
 */
public final class WiseCashier {

    /** The action WiseCashier registers for (PayCloud docs, section "Invocation"). */
    public static final String ACTION = "com.wiseasy.transaction.call";

    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_RESULT_MSG = "resultMsg";
    public static final String EXTRA_TRANS_DATA = "transData";

    /** WiseCashier's own "approved" code. Informational only — the backend verifies. */
    public static final String RESULT_APPROVED = "00";

    private WiseCashier() {}

    /**
     * True when this device can take the card payment itself, i.e. the cashier app is
     * installed and answers the action. That is what "the app is running on the POS
     * terminal" means in practice; on an ordinary phone this is false and the sale is
     * pushed to a separate card machine instead.
     *
     * <p>Needs the {@code <queries>} entry for {@link #ACTION} in the manifest on
     * Android 11+, otherwise the cashier app is invisible to us and this is always false.
     */
    public static boolean isInstalled(@NonNull Context context) {
        try {
            return new Intent(ACTION).resolveActivity(context.getPackageManager()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The Intent to launch, built from what the server prepared: its action, and every
     * extra copied across as a string exactly as received. The amount and order number
     * are inside {@code transData}, so the app never composes them.
     */
    @NonNull
    public static Intent buildIntent(@NonNull PosIntentData.LaunchIntent prepared) {
        Intent intent = new Intent(prepared.action);
        if (prepared.extras != null) {
            for (Map.Entry<String, String> e : prepared.extras.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    intent.putExtra(e.getKey(), e.getValue());
                }
            }
        }
        return intent;
    }

    /**
     * WiseCashier's answer, as delivered to {@code onActivityResult}. Kept verbatim so it
     * can be reported (and re-reported — the endpoint is idempotent) without the app
     * reinterpreting any of it.
     */
    public static final class Response {

        private static final String KEY_RESULT = "wisecashier_result";
        private static final String KEY_RESULT_MSG = "wisecashier_result_msg";
        private static final String KEY_TRANS_DATA = "wisecashier_trans_data";

        @Nullable public final String result;
        @Nullable public final String resultMsg;
        @Nullable public final String transData;

        private Response(@Nullable String result, @Nullable String resultMsg,
                         @Nullable String transData) {
            this.result = result;
            this.resultMsg = resultMsg;
            this.transData = transData;
        }

        /** Read the three extras off the result Intent; a null Intent yields an empty answer. */
        @NonNull
        public static Response from(@Nullable Intent data) {
            if (data == null) {
                return new Response(null, null, null);
            }
            return new Response(
                    data.getStringExtra(EXTRA_RESULT),
                    data.getStringExtra(EXTRA_RESULT_MSG),
                    data.getStringExtra(EXTRA_TRANS_DATA));
        }

        /**
         * The cashier app came back without a result code at all — typically the
         * operator backed out before a transaction was attempted, or the app was killed.
         * There is nothing to report, only something to check.
         */
        public boolean isEmpty() {
            return result == null || result.trim().isEmpty();
        }

        public boolean isApproved() {
            return result != null && RESULT_APPROVED.equals(result.trim());
        }

        /** The cashier's wording for a failure, or null when it gave none. */
        @Nullable
        public String message() {
            return resultMsg == null || resultMsg.trim().isEmpty() ? null : resultMsg.trim();
        }

        // -- Survives activity recreation while a report is pending --------------

        public void saveInto(@NonNull Bundle out) {
            out.putString(KEY_RESULT, result);
            out.putString(KEY_RESULT_MSG, resultMsg);
            out.putString(KEY_TRANS_DATA, transData);
        }

        @Nullable
        public static Response restoreFrom(@Nullable Bundle in) {
            if (in == null || !in.containsKey(KEY_RESULT)) {
                return null;
            }
            return new Response(in.getString(KEY_RESULT), in.getString(KEY_RESULT_MSG),
                    in.getString(KEY_TRANS_DATA));
        }
    }
}
