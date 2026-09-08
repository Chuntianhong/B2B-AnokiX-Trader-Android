package com.anokix.traderapp.ui.pos;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.PosTerminalsData;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * "Card machine payment" — pushes a POS sale total to one of the trader's PayCloud
 * terminals via {@code api/common/pos/payments}, mirroring the trader portal's dialog.
 *
 * <p>Card is the one payment method that does not go through {@code api/trader/pos/sale}:
 * the amount is sent to the terminal, the customer taps or inserts their card there, and
 * the backend records the sale once the gateway reports it. So this dialog's job ends at
 * "the amount is on the machine" — it never claims the customer has paid.
 *
 * <p>Failures are shown in the dialog rather than as a toast, because the gateway's
 * wording is the actionable part ("[E07507]The device is not turned on, please start the
 * device and cashier application") and it is usually fixable on the spot — so Send stays
 * enabled for a retry once the trader has switched the terminal on.
 */
public final class CardMachineDialog {

    /** Reference type recorded against the payment, so the backend can tie it to a sale. */
    private static final String REFERENCE_TYPE = "pos_sale";

    public interface Listener {
        /**
         * The amount reached the card machine. The sale is now the terminal's (and the
         * backend's) to finish, so the caller should close the till for this sale.
         */
        void onPaymentSent(String terminalName);
    }

    private final Activity activity;
    private final ApiClient api;
    private final double amount;
    private final String amountText;
    private final String description;
    private final List<PosTerminalsData.Terminal> terminals;
    private final Listener listener;

    private AlertDialog dialog;
    // Bound as plain TextViews: the layout uses the Roboto* subclasses, which are
    // siblings rather than a hierarchy, so only their common parent casts safely.
    private TextView terminalValue;
    private View statusRow;
    private ImageView statusIcon;
    private TextView statusText;
    private MaterialButton sendButton;
    private MaterialButton closeButton;

    private int selectedIndex;
    private boolean sending;
    /** True once the amount is on a machine — Send must not fire a second payment. */
    private boolean sent;

    private CardMachineDialog(Activity activity, ApiClient api, double amount, String amountText,
                              String description, List<PosTerminalsData.Terminal> terminals,
                              Listener listener) {
        this.activity = activity;
        this.api = api;
        this.amount = amount;
        this.amountText = amountText;
        this.description = description;
        this.terminals = terminals;
        this.listener = listener;
    }

    /**
     * @param amountText the already-formatted total, so the dialog and the till agree
     *                   to the cent on what is being charged
     * @param terminals  active machines only — never empty (the caller checks first)
     */
    public static void show(@NonNull Activity activity, @NonNull ApiClient api,
                            double amount, @NonNull String amountText, String description,
                            @NonNull List<PosTerminalsData.Terminal> terminals,
                            Listener listener) {
        new CardMachineDialog(activity, api, amount, amountText, description, terminals, listener)
                .build();
    }

    private void build() {
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_card_machine_payment, null);

        ((TextView) content.findViewById(R.id.cardMachineAmount)).setText(amountText);
        terminalValue = content.findViewById(R.id.cardMachineValue);
        statusRow = content.findViewById(R.id.cardMachineStatus);
        statusIcon = content.findViewById(R.id.cardMachineStatusIcon);
        statusText = content.findViewById(R.id.cardMachineStatusText);
        sendButton = content.findViewById(R.id.cardMachineSend);
        closeButton = content.findViewById(R.id.cardMachineClose);

        selectedIndex = 0;
        terminalValue.setText(terminals.get(selectedIndex).displayName());
        sendButton.setText(activity.getString(R.string.card_machine_send, amountText));

        dialog = new AlertDialog.Builder(activity)
                .setView(content)
                .setCancelable(true)
                .create();

        content.findViewById(R.id.cardMachineSelector).setOnClickListener(v -> chooseTerminal());
        content.findViewById(R.id.cardMachineDismiss).setOnClickListener(v -> dismiss());
        closeButton.setOnClickListener(v -> dismiss());
        sendButton.setOnClickListener(v -> send());

        dialog.show();
    }

    /** Single-choice picker, matching how the till already picks a payment method. */
    private void chooseTerminal() {
        if (sending || sent) {
            return;
        }
        String[] names = new String[terminals.size()];
        for (int i = 0; i < terminals.size(); i++) {
            names[i] = terminals.get(i).displayName();
        }
        final int[] picked = {selectedIndex};
        new AlertDialog.Builder(activity)
                .setTitle(R.string.card_machine_choose)
                .setSingleChoiceItems(names, selectedIndex, (d, which) -> picked[0] = which)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    selectedIndex = picked[0];
                    terminalValue.setText(terminals.get(selectedIndex).displayName());
                    // A different machine deserves a clean slate — the previous machine's
                    // error says nothing about this one.
                    hideStatus();
                })
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void send() {
        if (sending || sent) {
            return;
        }
        PosTerminalsData.Terminal terminal = terminals.get(selectedIndex);

        sending = true;
        hideStatus();
        sendButton.setEnabled(false);
        sendButton.setText(R.string.card_machine_sending);

        api.sendPosPayment(amount, terminal.id, description, REFERENCE_TYPE,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        sending = false;
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        showSent(terminal.displayName());
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        sendButton.setEnabled(true);
                        sendButton.setText(activity.getString(R.string.card_machine_send, amountText));
                        showError(message == null || message.isEmpty()
                                ? activity.getString(R.string.card_machine_send_error)
                                : message);
                    }
                });
    }

    /**
     * The amount is on the machine. The customer still has to complete it there, so the
     * dialog says exactly that — and Done, not another Send, is the only way out.
     *
     * <p>Done takes over the primary button rather than the outlined one: it is now the
     * only action, and leaving it as the quieter of the two would read as the lesser of
     * a choice that no longer exists.
     */
    private void showSent(String terminalName) {
        sent = true;
        closeButton.setVisibility(View.GONE);
        showStatus(activity.getString(R.string.card_machine_sent, terminalName),
                R.drawable.ic_check_circle, R.color.success);

        sendButton.setEnabled(true);
        sendButton.setIcon(null);
        sendButton.setText(R.string.promo_done);
        sendButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onPaymentSent(terminalName);
            }
        });
    }

    private void showError(String message) {
        showStatus(message, R.drawable.ic_alert_circle, R.color.danger);
    }

    private void showStatus(String message, int iconRes, int colorRes) {
        int color = ContextCompat.getColor(activity, colorRes);
        statusRow.setVisibility(View.VISIBLE);
        statusIcon.setImageResource(iconRes);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusText.setText(message);
        statusText.setTextColor(color);
    }

    private void hideStatus() {
        statusRow.setVisibility(View.GONE);
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
