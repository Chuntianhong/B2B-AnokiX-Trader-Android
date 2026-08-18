package com.anokix.traderapp.ui.wallet;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.TopupData;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * Top up the anokiX wallet with a card, through PayCloud's Hosted Checkout.
 *
 * <p>The flow, and why it is shaped this way:
 * <ol>
 *   <li>The trader picks an amount and we POST api/common/topup/create, which returns a
 *       {@code pay_url} and our own {@code merchant_order_no}.</li>
 *   <li>{@code pay_url} opens in a <b>Custom Tab</b>, not a WebView. Card details are
 *       being typed, so the trader must be able to see the real address bar and padlock
 *       — and 3-D Secure step-ups and browser autofill only work properly there.</li>
 *   <li>PayCloud redirects back to {@code anokix://topup-return} when it is done, which
 *       {@link TopupReturnActivity} turns into "close the tab, show this sheet again".</li>
 *   <li>The sheet then polls api/common/topup/status. <b>The redirect proves nothing</b>
 *       — the wallet is credited by PayCloud's signed webhook, verified server-side, so
 *       the poll is the only thing that can say the money arrived. That is also why the
 *       sheet keeps polling even if the redirect never happens and the trader closes the
 *       tab by hand.</li>
 * </ol>
 *
 * <p>{@code status=paid} means only that the card was taken. The money is in the wallet
 * when {@code credit_status=credited}, and that is the only state reported as success.
 */
public class TopUpSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "TopUpSheet";

    /** Where PayCloud sends the browser back to (see {@link TopupReturnActivity}). */
    private static final String RETURN_URL = "anokix://topup-return";

    /** Server-side bounds for a top-up (api/common/topup/create). */
    private static final double MIN_AMOUNT = 1;
    private static final double MAX_AMOUNT = 500000;

    /** The quick-pick amounts, matching the trader portal. */
    private static final int[] PRESETS = {50, 100, 200, 500, 1000};

    /** How often to re-check a top-up that has not settled yet, and for how long. */
    private static final long POLL_INTERVAL_MS = 4000;
    private static final long POLL_WINDOW_MS = 10 * 60 * 1000;

    /** Implemented by the host screen so it can reload once money actually lands. */
    public interface Host {
        void onWalletToppedUp();
    }

    private final Handler poller = new Handler(Looper.getMainLooper());

    private LinearLayout formState, waitState, presetRow;
    private EditText amountInput;
    private TextView amountHelp, waitTitle, waitBody, waitOrderNo, sheetTitle;
    private ProgressBar waitSpinner;
    private ImageView waitIcon;
    private MaterialButton proceedBtn, checkBtn, reopenBtn;

    /** Non-null once a checkout has been started — the key for polling its status. */
    private String merchantOrderNo;
    private String payUrl;
    private double startedAmount;
    /** True once the poll has reported the money in the wallet; stops further polling. */
    private boolean settled;
    /** True while a status call is in flight, so taps and ticks cannot stack them up. */
    private boolean checking;
    private long pollingUntil;

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || settled || merchantOrderNo == null) return;
            if (System.currentTimeMillis() > pollingUntil) return;
            checkStatus(false);
            poller.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_wallet_topup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        sheetTitle = v.findViewById(R.id.sheetTitle);
        formState = v.findViewById(R.id.formState);
        waitState = v.findViewById(R.id.waitState);
        presetRow = v.findViewById(R.id.presetRow);
        amountInput = v.findViewById(R.id.inputAmount);
        amountHelp = v.findViewById(R.id.amountHelp);
        waitTitle = v.findViewById(R.id.waitTitle);
        waitBody = v.findViewById(R.id.waitBody);
        waitOrderNo = v.findViewById(R.id.waitOrderNo);
        waitSpinner = v.findViewById(R.id.waitSpinner);
        waitIcon = v.findViewById(R.id.waitIcon);
        proceedBtn = v.findViewById(R.id.btnProceed);
        checkBtn = v.findViewById(R.id.btnCheckStatus);
        reopenBtn = v.findViewById(R.id.btnReopen);

        v.findViewById(R.id.sheetClose).setOnClickListener(x -> dismiss());

        amountHelp.setText(getString(R.string.topup_limits,
                OrderFormat.money(MIN_AMOUNT, "R"), OrderFormat.money(MAX_AMOUNT, "R")));

        buildPresets();
        // Typing an amount by hand clears the chip selection, so the two can never
        // disagree about what is about to be charged.
        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { syncPresetSelection(); }
        });

        proceedBtn.setOnClickListener(x -> startCheckout());
        checkBtn.setOnClickListener(x -> checkStatus(true));
        reopenBtn.setOnClickListener(x -> openCheckout());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the checkout page (or from anywhere else): the status may have moved
        // on while we were in the background, so ask straight away and resume ticking.
        if (merchantOrderNo != null && !settled) {
            checkStatus(false);
            schedulePolling();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        poller.removeCallbacks(pollTick);
    }

    @Override
    public void onDestroyView() {
        poller.removeCallbacks(pollTick);
        super.onDestroyView();
    }

    // ---- Amount form -----------------------------------------------------

    private void buildPresets() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int preset : PRESETS) {
            TextView chip = (TextView) inflater.inflate(
                    R.layout.item_amount_chip, presetRow, false);
            chip.setText(OrderFormat.money(preset, "R"));
            chip.setTag(preset);
            chip.setOnClickListener(v -> {
                amountInput.setText(String.valueOf(preset));
                amountInput.setSelection(amountInput.getText().length());
            });
            presetRow.addView(chip);
        }
    }

    /** Highlights the chip that matches what is typed, or none of them. */
    private void syncPresetSelection() {
        double typed = parseAmount(amountInput.getText().toString());
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            TextView chip = (TextView) presetRow.getChildAt(i);
            boolean on = ((Integer) chip.getTag()) == (int) typed && typed == Math.floor(typed);
            chip.setBackgroundResource(on
                    ? R.drawable.bg_amount_chip_selected : R.drawable.bg_amount_chip);
            chip.setTextColor(ContextCompat.getColor(requireContext(),
                    on ? R.color.white : R.color.text_primary));
        }
    }

    private void startCheckout() {
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) {
            amountInput.setError(getString(R.string.topup_err_amount));
            amountInput.requestFocus();
            return;
        }
        double amount = parseAmount(raw);
        if (amount < MIN_AMOUNT) {
            amountInput.setError(getString(R.string.topup_err_min, OrderFormat.money(MIN_AMOUNT, "R")));
            amountInput.requestFocus();
            return;
        }
        if (amount > MAX_AMOUNT) {
            amountInput.setError(getString(R.string.topup_err_max, OrderFormat.money(MAX_AMOUNT, "R")));
            amountInput.requestFocus();
            return;
        }

        proceedBtn.setEnabled(false);
        proceedBtn.setText(R.string.please_wait);

        // Send the amount as typed, to two decimals, so what the trader sees on the
        // PayCloud page is exactly what they entered here.
        String formatted = String.format(Locale.US, "%.2f", amount);
        ApiClient.get(requireContext()).createTopup(formatted, RETURN_URL,
                new ApiCallback<TopupData.Create>() {
                    @Override
                    public void onSuccess(TopupData.Create data) {
                        if (!isAdded()) return;
                        proceedBtn.setEnabled(true);
                        proceedBtn.setText(R.string.topup_proceed);

                        if (data == null || !data.isUsable()) {
                            toast(getString(R.string.topup_failed));
                            return;
                        }
                        merchantOrderNo = data.merchant_order_no;
                        payUrl = data.pay_url;
                        startedAmount = data.amount > 0 ? data.amount : amount;
                        showWaitState();
                        openCheckout();
                        schedulePolling();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        proceedBtn.setEnabled(true);
                        proceedBtn.setText(R.string.topup_proceed);
                        toast(message == null ? getString(R.string.topup_failed) : message);
                    }
                });
    }

    // ---- Checkout page ---------------------------------------------------

    /**
     * Opens the PayCloud page in a Custom Tab, falling back to whatever browser the
     * phone has if Custom Tabs are not supported. Never a WebView: this is a card entry
     * page and the trader is entitled to see the address bar it belongs to.
     */
    private void openCheckout() {
        if (payUrl == null || payUrl.isEmpty()) return;
        Context context = requireContext();
        Uri uri = Uri.parse(payUrl);
        try {
            new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(false)
                    .build()
                    .launchUrl(context, uri);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                toast(getString(R.string.topup_no_browser));
            }
        }
    }

    // ---- Waiting on the payment -----------------------------------------

    private void schedulePolling() {
        poller.removeCallbacks(pollTick);
        if (settled || merchantOrderNo == null) return;
        pollingUntil = System.currentTimeMillis() + POLL_WINDOW_MS;
        poller.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    private void showWaitState() {
        sheetTitle.setText(R.string.topup_title);
        formState.setVisibility(View.GONE);
        waitState.setVisibility(View.VISIBLE);
        waitOrderNo.setText(getString(R.string.topup_wait_order, merchantOrderNo));
        setWaitLook(true, R.string.topup_wait_title, R.string.topup_wait_body, null);
        reopenBtn.setVisibility(View.VISIBLE);
        checkBtn.setText(R.string.topup_check_status);
    }

    /**
     * @param busy    spinner (still moving) vs. a settled icon
     * @param bodyArg formatted into the body string when it takes an argument
     */
    private void setWaitLook(boolean busy, int titleRes, int bodyRes, String bodyArg) {
        waitSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        waitIcon.setVisibility(busy ? View.GONE : View.VISIBLE);
        waitTitle.setText(titleRes);
        waitBody.setText(bodyArg == null ? getString(bodyRes) : getString(bodyRes, bodyArg));
    }

    private void checkStatus(boolean manual) {
        if (checking || settled || merchantOrderNo == null || !isAdded()) return;
        checking = true;
        if (manual) {
            checkBtn.setEnabled(false);
            checkBtn.setText(R.string.please_wait);
        }

        ApiClient.get(requireContext()).getTopupStatus(merchantOrderNo,
                new ApiCallback<TopupData.Status>() {
                    @Override
                    public void onSuccess(TopupData.Status status) {
                        checking = false;
                        if (!isAdded()) return;
                        restoreCheckButton(manual);
                        if (status != null) applyStatus(status);
                    }

                    @Override
                    public void onError(String message) {
                        checking = false;
                        if (!isAdded()) return;
                        restoreCheckButton(manual);
                        // Only speak up when the trader asked — the background ticks stay
                        // quiet so a blip does not spray toasts over the sheet.
                        if (manual) {
                            toast(message == null ? getString(R.string.topup_status_failed) : message);
                        }
                    }
                });
    }

    private void restoreCheckButton(boolean manual) {
        if (!manual) return;
        checkBtn.setEnabled(true);
        checkBtn.setText(settled ? R.string.topup_done : R.string.topup_check_status);
    }

    private void applyStatus(TopupData.Status status) {
        if (status.isCredited()) {
            settled = true;
            poller.removeCallbacks(pollTick);
            double amount = status.amount > 0 ? status.amount : startedAmount;
            setWaitLook(false, R.string.topup_credited_title, R.string.topup_credited_body,
                    OrderFormat.money(amount, "R"));
            waitIcon.setImageResource(R.drawable.ic_check_circle);
            waitIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.success)));
            reopenBtn.setVisibility(View.GONE);
            checkBtn.setText(R.string.topup_done);
            checkBtn.setOnClickListener(v -> dismiss());
            notifyHost();
            return;
        }

        if (status.isDead()) {
            settled = true;
            poller.removeCallbacks(pollTick);
            setWaitLook(false, R.string.topup_dead_title, R.string.topup_dead_body, null);
            waitIcon.setImageResource(R.drawable.ic_x_circle);
            waitIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.danger)));
            reopenBtn.setVisibility(View.GONE);
            checkBtn.setText(R.string.topup_start_over);
            checkBtn.setOnClickListener(v -> resetToForm());
            return;
        }

        if (status.isPaidNotCredited()) {
            // The card went through but the webhook has not landed yet. Keep the spinner
            // running — the wallet is seconds away, and saying "done" now would be a lie.
            setWaitLook(true, R.string.topup_paid_title, R.string.topup_paid_body, null);
            reopenBtn.setVisibility(View.GONE);
        }
    }

    /** Back to the amount form after a failed or cancelled attempt. */
    private void resetToForm() {
        poller.removeCallbacks(pollTick);
        merchantOrderNo = null;
        payUrl = null;
        settled = false;
        waitState.setVisibility(View.GONE);
        formState.setVisibility(View.VISIBLE);
        checkBtn.setOnClickListener(v -> checkStatus(true));
        checkBtn.setText(R.string.topup_check_status);
        amountInput.setText("");
    }

    private void notifyHost() {
        Host host = null;
        if (getParentFragment() instanceof Host) {
            host = (Host) getParentFragment();
        } else if (getActivity() instanceof Host) {
            host = (Host) getActivity();
        }
        if (host != null) host.onWalletToppedUp();
    }

    // ---- Helpers ---------------------------------------------------------

    private double parseAmount(String raw) {
        if (raw == null) return 0;
        String cleaned = raw.trim().replace(",", "").replace("R", "").trim();
        if (cleaned.isEmpty()) return 0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
