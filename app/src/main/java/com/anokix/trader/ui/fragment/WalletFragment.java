package com.anokix.trader.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.Transaction;
import com.anokix.trader.ui.MainActivity;
import com.anokix.trader.ui.NotificationBadge;
import com.anokix.trader.ui.NotificationsActivity;
import com.anokix.trader.ui.adapter.SimpleListAdapter;

/** Trader wallet (IMB): balance, send/receive/withdraw/statement, recent transactions. */
public class WalletFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.hamburgerButton).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        view.findViewById(R.id.actionSend).setOnClickListener(v ->
                amountDialog("Send Money", "Recipient will receive"));
        view.findViewById(R.id.actionAdd).setOnClickListener(v ->
                amountDialog("Add Money", "Top up your wallet with"));
        view.findViewById(R.id.actionPayBills).setOnClickListener(v ->
                info("Pay Bills", "Pay electricity, water and municipal bills directly from your anokiX wallet."));
        view.findViewById(R.id.actionAirtime).setOnClickListener(v ->
                amountDialog("Buy Airtime", "Airtime to be purchased"));
        view.findViewById(R.id.actionStatement).setOnClickListener(v ->
                info("Statement", "Your June 2026 statement is ready.\n\nOpening balance: R12,300.00\nCredits: R8,640.00\nDebits: R5,490.00\nClosing balance: R15,450.00"));
        view.findViewById(R.id.actionSettle).setOnClickListener(v ->
                info("Settle to Bank", "Settle your available balance to:\n\nIMB Business Account ···3345\nNext settlement: 15 May, 12:00pm"));

        RecyclerView list = view.findViewById(R.id.walletTransactions);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new TxnAdapter(MockData.getWalletTransactions()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            NotificationBadge.refresh(getContext(), (TextView) getView().findViewById(R.id.notificationBadge));
        }
    }

    private void amountDialog(String title, String confirmPrefix) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Amount (R)");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(input)
                .setPositiveButton("Confirm", (d, w) -> {
                    double amount;
                    try {
                        amount = Double.parseDouble(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        toast("Enter a valid amount");
                        return;
                    }
                    info(title + " successful",
                            String.format(Locale.US, "%s R%,.2f.", confirmPrefix, amount));
                })
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void info(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.promo_done, null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /** Wallet transaction rows with signed, colour-coded amounts (portal style). */
    private static class TxnAdapter extends RecyclerView.Adapter<TxnAdapter.VH> {
        private final java.util.List<Transaction> items;

        TxnAdapter(java.util.List<Transaction> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wallet_txn, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Transaction t = items.get(position);
            h.title.setText(t.title);
            h.subtitle.setText(t.subtitle + " · " + t.date);
            h.status.setText(t.status);

            int green = androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.success);
            int red = androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.danger);
            int color = t.positive ? green : red;
            h.amount.setTextColor(color);
            h.amount.setText(String.format(Locale.US, "%sR%,.2f", t.positive ? "+" : "−", t.amount));

            int icon;
            switch (t.type) {
                case "received":   icon = R.drawable.ic_finances; break;
                case "settlement": icon = R.drawable.ic_wallet; break;
                case "airtime":    icon = R.drawable.ic_promo_tag; break;
                default:           icon = R.drawable.ic_orders; break;
            }
            h.icon.setImageResource(icon);
            String hex = t.positive ? "#16A34A" : "#7C3AED";
            androidx.core.view.ViewCompat.setBackgroundTintList((View) h.icon.getParent(),
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(hex)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.TextView title, subtitle, amount, status;
            final android.widget.ImageView icon;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.txnTitle);
                subtitle = itemView.findViewById(R.id.txnSubtitle);
                amount = itemView.findViewById(R.id.txnAmount);
                status = itemView.findViewById(R.id.txnStatus);
                icon = itemView.findViewById(R.id.txnIcon);
            }
        }
    }
}
