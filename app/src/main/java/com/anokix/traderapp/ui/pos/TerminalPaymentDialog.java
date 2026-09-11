package com.anokix.traderapp.ui.pos;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.PosIntentData;
import com.anokix.traderapp.pos.WiseCashier;
import com.google.android.material.button.MaterialButton;

/**
 * "Card payment on this device" — takes a POS sale total on the PayCloud terminal the
 * app is running on, through WiseCashier's Intent (PayCloud "Same-terminal Application
 * Integration"). The sibling of {@link CardMachineDialog}, which pushes the amount to a
 * <i>separate</i> machine; this one is used instead when {@link WiseCashier#isInstalled}.
 *
 * <p>The flow, and who decides what:
 * <ol>
 *   <li><b>Prepare</b> — {@code api/common/pos/intent/prepare}. The server creates the
 *       order and returns the Intent to launch, extras included. The app copies it
 *       verbatim (see {@link WiseCashier#buildIntent}), so the amount the machine asks
 *       for is the one the server issued, never one the app composed.</li>
 *   <li><b>Cashier app</b> — the host activity launches the Intent for a result and
 *       hands the answer back through {@link #onCashierResult}.</li>
 *   <li><b>Report</b> — {@code api/common/pos/intent/result}, sent exactly as received.
 *       The backend classifies the code: a decline or cancel is {@code verified} and not
 *       {@code paid}; an approval is checked with PayCloud (amount included) before it is
 *       {@code paid}; a timeout or network code is neither, because the card may still
 *       have been charged.</li>
 *   <li><b>Confirm</b> — only when the answer was not final: poll
 *       {@code api/common/pos/payments/status} until the order is no longer open.</li>
 * </ol>
 *
 * <p>The dialog never says "paid" on the strength of WiseCashier's "00" alone — only the
 * backend's {@code paid} flag (or a closed order reading {@code paid} on the status poll)
 * settles it. And because the same result can be reported twice safely, a failed report
 * keeps the cashier's answer and offers Retry rather than dropping it: an order whose
 * result is never reported would stay pending on the backend.
 *
 * <p>State needed to finish the job survives activity recreation — the cashier app may
 * hold the foreground long enough for the system to reclaim ours — via
 * {@link #saveState}/{@link #restore}, so the result is still reported when we come back.
 */
public final class TerminalPaymentDialog {

    /** How often to re-check an unconfirmed order, and for how long before giving up. */
    private static final long POLL_INTERVAL_MS = 3000;
    private static final long POLL_WINDOW_MS = 90 * 1000;

    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_AMOUNT_TEXT = "amount_text";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_BUSINESS_ORDER_NO = "business_order_no";
    private static final String KEY_MERCHANT_ORDER_NO = "merchant_order_no";
    private static final String KEY_STAGE = "stage";
    private static final String KEY_STATUS_BODY = "status_body";

    public interface Host {
        /**
         * Launch the prepared WiseCashier Intent for a result. May throw
         * {@link ActivityNotFoundException}; the dialog handles it.
         */
        void launchCashier(@NonNull Intent intent);

        /**
         * The backend has confirmed the card payment and recorded the sale. The till is
         * finished with it — clear the cart and step back.
         */
        void onPaid();

        /** The dialog went away without a confirmed payment; the cart is untouched. */
        void onClosed();
    }

    private enum Stage {
        /** Prepare call in flight. */
        PREPARING,
        /** WiseCashier is in front; waiting for its result. */
        AT_CASHIER,
        /** Result call in flight. */
        REPORTING,
        /** Answer was not final; status poll running. */
        CONFIRMING,
        /** Settled: backend says paid. */
        PAID,
        /** Settled: declined / cancelled / not paid. No money moved. */
        FAILED,
        /** Poll window ran out with the order still open. */
        UNCONFIRMED,
        /** The report could not be sent; the answer is kept for Retry. */
        REPORT_FAILED
    }

    private final Activity activity;
    private final ApiClient api;
    private final Host host;
    private final double amount;
    private final String amountText;
    private final String description;
    private final Handler poller = new Handler(Looper.getMainLooper());

    private AlertDialog dialog;
    private View dismissButton;
    private ProgressBar progress;
    private ImageView icon;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView orderView;
    private MaterialButton primaryButton;
    private MaterialButton closeButton;

    private Stage stage = Stage.PREPARING;
    /** The order the server created; the key the result is reported under. */
    private String businessOrderNo;
    /** The key for the status poll (usually the same number). */
    private String merchantOrderNo;
    /** WiseCashier's answer, kept so a failed report can be re-sent unchanged. */
    private WiseCashier.Response pendingResponse;
    private boolean checking;
    private long pollingUntil;

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            if (stage != Stage.CONFIRMING || !alive()) return;
            if (System.currentTimeMillis() > pollingUntil) {
                showUnconfirmed();
                return;
            }
            checkStatus();
            poller.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private TerminalPaymentDialog(Activity activity, ApiClient api, Host host, double amount,
                                  String amountText, String description) {
        this.activity = activity;
        this.api = api;
        this.host = host;
        this.amount = amount;
        this.amountText = amountText;
        this.description = description;
    }

    /**
     * Start a new payment: show the dialog and prepare the order straight away.
     *
     * @param amountText the already-formatted total, so the dialog and the till agree
     *                   to the cent on what is being charged
     */
    @NonNull
    public static TerminalPaymentDialog start(@NonNull Activity activity, @NonNull ApiClient api,
                                              double amount, @NonNull String amountText,
                                              String description, @NonNull Host host) {
        TerminalPaymentDialog d = new TerminalPaymentDialog(activity, api, host, amount,
                amountText, description);
        d.build();
        d.prepare();
        return d;
    }

    /**
     * Re-create the dialog after the activity was recreated mid-payment. Returns null
     * when the bundle does not describe one. The dialog comes back in the stage it was
     * in: waiting for the cashier app (its result is delivered right after
     * {@code onCreate}), holding an answer whose report had not gone through yet,
     * polling, or already settled — a "paid" verdict in particular must not be lost,
     * because the sale is recorded and Done is what clears the cart.
     */
    @Nullable
    public static TerminalPaymentDialog restore(@NonNull Activity activity, @NonNull ApiClient api,
                                                @NonNull Host host, @Nullable Bundle state) {
        if (state == null || state.getString(KEY_STAGE) == null) {
            return null;
        }
        Stage saved;
        try {
            saved = Stage.valueOf(state.getString(KEY_STAGE));
        } catch (IllegalArgumentException e) {
            return null;
        }
        TerminalPaymentDialog d = new TerminalPaymentDialog(activity, api, host,
                state.getDouble(KEY_AMOUNT, 0), state.getString(KEY_AMOUNT_TEXT, ""),
                state.getString(KEY_DESCRIPTION));
        d.businessOrderNo = state.getString(KEY_BUSINESS_ORDER_NO);
        d.merchantOrderNo = state.getString(KEY_MERCHANT_ORDER_NO);
        d.pendingResponse = WiseCashier.Response.restoreFrom(state);
        d.build();
        d.showOrderNo();

        switch (saved) {
            case AT_CASHIER:
                d.showAtCashier();
                break;
            case REPORTING:
            case REPORT_FAILED:
                if (d.pendingResponse != null) {
                    d.report(d.pendingResponse);
                } else {
                    d.showConfirming();
                    d.startPolling();
                }
                break;
            case CONFIRMING:
            case UNCONFIRMED:
                d.showConfirming();
                d.startPolling();
                break;
            case PAID:
                d.showPaid();
                break;
            case FAILED:
                d.showNotPaid(state.getString(KEY_STATUS_BODY,
                        activity.getString(R.string.terminal_pay_failed_body)));
                break;
            case PREPARING:
            default:
                // The prepare call was cut off: whether the server created an order is
                // unknown, so do not silently create another — let the cashier retry.
                d.showPrepareFailed(activity.getString(R.string.terminal_pay_prepare_error));
                break;
        }
        return d;
    }

    /** True while the dialog is up — i.e. there is a payment to carry across recreation. */
    public boolean needsSaving() {
        return dialog != null && dialog.isShowing();
    }

    @NonNull
    public Bundle saveState() {
        Bundle out = new Bundle();
        out.putString(KEY_STAGE, stage.name());
        out.putDouble(KEY_AMOUNT, amount);
        out.putString(KEY_AMOUNT_TEXT, amountText);
        out.putString(KEY_DESCRIPTION, description);
        out.putString(KEY_BUSINESS_ORDER_NO, businessOrderNo);
        out.putString(KEY_MERCHANT_ORDER_NO, merchantOrderNo);
        if (stage == Stage.FAILED) {
            out.putString(KEY_STATUS_BODY, statusBody.getText().toString());
        }
        if (pendingResponse != null) {
            pendingResponse.saveInto(out);
        }
        return out;
    }

    /** The host is going away: stop the poll and drop the window. */
    public void release() {
        poller.removeCallbacks(pollTick);
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // ---- UI ----------------------------------------------------------------

    private void build() {
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_terminal_payment, null);

        ((TextView) content.findViewById(R.id.terminalPayAmount)).setText(amountText);
        dismissButton = content.findViewById(R.id.terminalPayDismiss);
        progress = content.findViewById(R.id.terminalPayProgress);
        icon = content.findViewById(R.id.terminalPayIcon);
        statusTitle = content.findViewById(R.id.terminalPayStatusTitle);
        statusBody = content.findViewById(R.id.terminalPayStatusBody);
        orderView = content.findViewById(R.id.terminalPayOrder);
        primaryButton = content.findViewById(R.id.terminalPayPrimary);
        closeButton = content.findViewById(R.id.terminalPayClose);

        // Not cancelable: a tap outside must not abandon an order that is mid-flight.
        // Leaving is always through an explicit button, and only once it is safe.
        dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(false)
                .create();

        dismissButton.setOnClickListener(v -> close());
        closeButton.setOnClickListener(v -> close());

        showPreparing();
        dialog.show();
    }

    private void showPreparing() {
        stage = Stage.PREPARING;
        setBusy(R.string.terminal_pay_preparing_title,
                activity.getString(R.string.terminal_pay_preparing_body));
        setActions(null, null, false);
    }

    private void showAtCashier() {
        stage = Stage.AT_CASHIER;
        setBusy(R.string.terminal_pay_cashier_title,
                activity.getString(R.string.terminal_pay_cashier_body));
        setActions(null, null, false);
    }

    private void showReporting() {
        stage = Stage.REPORTING;
        setBusy(R.string.terminal_pay_reporting_title,
                activity.getString(R.string.terminal_pay_reporting_body));
        setActions(null, null, false);
    }

    private void showConfirming() {
        stage = Stage.CONFIRMING;
        setBusy(R.string.terminal_pay_confirming_title,
                activity.getString(R.string.terminal_pay_confirming_body));
        setActions(null, null, false);
    }

    private void showPaid() {
        stage = Stage.PAID;
        poller.removeCallbacks(pollTick);
        setSettled(R.drawable.ic_check_circle, R.color.success,
                R.string.terminal_pay_paid_title,
                activity.getString(R.string.terminal_pay_paid_body, amountText));
        // Done is the only way out: the money has moved, so there is nothing to retry
        // and nothing to keep the cart for.
        setActions(activity.getString(R.string.promo_done), v -> {
            dismiss();
            host.onPaid();
        }, false);
    }

    /** Declined or cancelled at the machine, with the cashier's reason when it gave one. */
    private void showFailed(@Nullable String reason) {
        showNotPaid(reason == null
                ? activity.getString(R.string.terminal_pay_failed_body)
                : activity.getString(R.string.terminal_pay_failed_body_reason,
                        trimTrailingStop(reason)));
    }

    /** Settled as not paid — no money moved. */
    private void showNotPaid(String body) {
        stage = Stage.FAILED;
        poller.removeCallbacks(pollTick);
        setSettled(R.drawable.ic_alert_circle, R.color.danger,
                R.string.terminal_pay_failed_title, body);
        // Try again is a fresh order (new prepare), not a re-launch of the old one:
        // the server has already closed that number as failed.
        setActions(activity.getString(R.string.terminal_pay_try_again), v -> {
            pendingResponse = null;
            businessOrderNo = null;
            merchantOrderNo = null;
            orderView.setVisibility(View.GONE);
            prepare();
        }, true);
    }

    /** The poll window ran out with the order still open. */
    private void showUnconfirmed() {
        stage = Stage.UNCONFIRMED;
        poller.removeCallbacks(pollTick);
        setSettled(R.drawable.ic_clock, R.color.warning,
                R.string.terminal_pay_unconfirmed_title,
                activity.getString(R.string.terminal_pay_unconfirmed_body));
        // The cart is deliberately kept: the card may or may not have been charged,
        // and only Sales History (fed by PayCloud's webhook) can say. Close leaves the
        // trader to check there rather than charging twice or losing the sale.
        setActions(activity.getString(R.string.terminal_pay_check_again), v -> {
            showConfirming();
            startPolling();
        }, true);
    }

    /**
     * The result call itself failed; the answer is kept so Retry sends it unchanged.
     * When that answer was an approval the customer's card has most likely been charged,
     * so the wording says so — Close is still offered for a lasting outage, but the
     * cashier must not read it as "nothing happened" and ring the sale up again.
     */
    private void showReportFailed(String message) {
        stage = Stage.REPORT_FAILED;
        boolean approved = pendingResponse != null && pendingResponse.isApproved();
        setSettled(R.drawable.ic_alert_circle, R.color.danger,
                R.string.terminal_pay_report_error_title,
                activity.getString(approved
                                ? R.string.terminal_pay_report_error_approved_body
                                : R.string.terminal_pay_report_error_body,
                        ensureTrailingStop(message)));
        setActions(activity.getString(R.string.terminal_pay_retry),
                v -> report(pendingResponse), true);
    }

    private void showPrepareFailed(String message) {
        stage = Stage.FAILED;
        setSettled(R.drawable.ic_alert_circle, R.color.danger,
                R.string.terminal_pay_failed_title, ensureTrailingStop(message));
        setActions(activity.getString(R.string.terminal_pay_try_again), v -> prepare(), true);
    }

    private void setBusy(int titleRes, String body) {
        progress.setVisibility(View.VISIBLE);
        icon.setVisibility(View.GONE);
        statusTitle.setText(titleRes);
        statusTitle.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        statusBody.setText(body);
    }

    private void setSettled(int iconRes, int colorRes, int titleRes, String body) {
        int color = ContextCompat.getColor(activity, colorRes);
        progress.setVisibility(View.GONE);
        icon.setVisibility(View.VISIBLE);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(titleRes);
        statusTitle.setTextColor(color);
        statusBody.setText(body);
    }

    /**
     * @param primaryLabel the one primary action, or null while nothing can be done
     * @param closable     whether the dialog may be left with the cart intact
     */
    private void setActions(@Nullable String primaryLabel,
                            @Nullable View.OnClickListener primaryAction, boolean closable) {
        if (primaryLabel == null) {
            primaryButton.setVisibility(View.GONE);
        } else {
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setText(primaryLabel);
            primaryButton.setOnClickListener(primaryAction);
        }
        closeButton.setVisibility(closable ? View.VISIBLE : View.GONE);
        dismissButton.setVisibility(closable ? View.VISIBLE : View.INVISIBLE);
    }

    private void showOrderNo() {
        if (businessOrderNo == null) return;
        orderView.setText(activity.getString(R.string.terminal_pay_order, businessOrderNo));
        orderView.setVisibility(View.VISIBLE);
    }

    // ---- Flow --------------------------------------------------------------

    /** Step 1: ask the server to create the order and hand back the Intent. */
    private void prepare() {
        showPreparing();
        api.preparePosIntent(amount, description, new ApiCallback<PosIntentData.Prepare>() {
            @Override
            public void onSuccess(PosIntentData.Prepare data) {
                if (!alive()) return;
                String orderNo = data == null ? null : data.businessOrderNo();
                if (data == null || !data.isLaunchable() || orderNo == null) {
                    showPrepareFailed(activity.getString(R.string.terminal_pay_prepare_error));
                    return;
                }
                businessOrderNo = orderNo;
                merchantOrderNo = data.merchantOrderNo();
                showOrderNo();
                launch(WiseCashier.buildIntent(data.intent));
            }

            @Override
            public void onError(String message) {
                if (!alive()) return;
                showPrepareFailed(message == null || message.isEmpty()
                        ? activity.getString(R.string.terminal_pay_prepare_error) : message);
            }
        });
    }

    /** Step 2: hand the prepared Intent to the cashier app and wait for its answer. */
    private void launch(Intent intent) {
        showAtCashier();
        try {
            host.launchCashier(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // The order exists server-side but never reached the machine. There is no
            // answer to report; it will stay pending until the backend expires it.
            showPrepareFailed(activity.getString(R.string.terminal_pay_cashier_missing));
        }
    }

    /**
     * The cashier app came back. Called by the host from its activity-result callback,
     * with whatever Intent it delivered — including none.
     */
    public void onCashierResult(@Nullable Intent data) {
        if (!alive() || businessOrderNo == null) return;
        WiseCashier.Response response = WiseCashier.Response.from(data);
        if (response.isEmpty()) {
            // No result code at all (the operator backed out, or the cashier app died
            // before it could answer). Nothing to report; ask the backend instead —
            // it queries PayCloud, which is the only party that knows what happened.
            showConfirming();
            startPolling();
            return;
        }
        report(response);
    }

    /** Step 3: send the answer exactly as received and act on the backend's verdict. */
    private void report(@NonNull WiseCashier.Response response) {
        pendingResponse = response;
        showReporting();
        api.reportPosIntentResult(businessOrderNo, response.result, response.resultMsg,
                response.transData, new ApiCallback<PosIntentData.Result>() {
                    @Override
                    public void onSuccess(PosIntentData.Result data) {
                        if (!alive()) return;
                        pendingResponse = null;
                        if (data == null) {
                            // Accepted, but no verdict to read — fall back to the poll.
                            showConfirming();
                            startPolling();
                            return;
                        }
                        String pollKey = data.merchantOrderNo();
                        if (pollKey != null) merchantOrderNo = pollKey;

                        if (data.paid) {
                            showPaid();
                        } else if (data.verified) {
                            String why = data.failureMessage();
                            showFailed(why != null ? why : response.message());
                        } else {
                            showConfirming();
                            startPolling();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!alive()) return;
                        showReportFailed(message == null || message.isEmpty()
                                ? activity.getString(R.string.terminal_pay_report_error)
                                : message);
                    }
                });
    }

    // ---- Step 4: confirm an order whose answer was not final --------------

    private void startPolling() {
        poller.removeCallbacks(pollTick);
        pollingUntil = System.currentTimeMillis() + POLL_WINDOW_MS;
        poller.post(pollTick);
    }

    private void checkStatus() {
        if (checking) return;
        checking = true;
        String key = merchantOrderNo != null ? merchantOrderNo : businessOrderNo;
        api.getPosPaymentStatus(key, new ApiCallback<PosIntentData.Status>() {
            @Override
            public void onSuccess(PosIntentData.Status status) {
                checking = false;
                if (!alive() || stage != Stage.CONFIRMING || status == null) return;
                if (status.isOpen()) return; // keep ticking
                if (status.isPaid()) {
                    showPaid();
                } else {
                    showNotPaid(activity.getString(R.string.terminal_pay_not_paid_body));
                }
            }

            @Override
            public void onError(String message) {
                // Stay quiet: the tick will ask again, and the window bounds it.
                checking = false;
            }
        });
    }

    // ---- Helpers -----------------------------------------------------------

    private void close() {
        poller.removeCallbacks(pollTick);
        dismiss();
        host.onClosed();
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private boolean alive() {
        return !(activity.isFinishing() || activity.isDestroyed())
                && dialog != null && dialog.isShowing();
    }

    /** "[K026]Manual cancellation." → "[K026]Manual cancellation" for the reason template. */
    private static String trimTrailingStop(String s) {
        String t = s.trim();
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }

    private static String ensureTrailingStop(String s) {
        String t = s.trim();
        return t.isEmpty() || t.endsWith(".") || t.endsWith("!") ? t : t + ".";
    }
}
