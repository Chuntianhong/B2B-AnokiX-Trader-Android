package com.anokix.traderapp.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.DebitRequestsData;
import com.anokix.traderapp.network.dto.TraderDashboardData;
import com.anokix.traderapp.ui.AirtimeActivity;
import com.anokix.traderapp.ui.FinancesActivity;
import com.anokix.traderapp.ui.MainActivity;
import com.anokix.traderapp.ui.NotificationBadge;
import com.anokix.traderapp.ui.NotificationsActivity;
import com.anokix.traderapp.ui.wallet.DebitOrderSheetFragment;
import com.anokix.traderapp.ui.wallet.TopUpSheetFragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The anokiX wallet (powered by IMB): live balance, the recent movements on it, and the
 * two things a trader can actually do with it from a phone — put money in with a card
 * (PayCloud top-up) and stand a debit order against it.
 *
 * Two calls back the screen and they are deliberately kept apart, because they fail
 * apart: GET api/common/wallet for the balance, GET api/common/debit-requests for the
 * standing instructions. A wallet that is not active still has debit orders worth
 * showing, and IMB being down for a balance read must not blank the rest of the page.
 *
 * The web portal's Send Money / Pay Bills / Move to Bank / QR tiles are not here. There
 * is no API behind any of them for a trader, and a wallet screen that offers to move a
 * shopkeeper's money and then cannot is worse than one that does not offer.
 */
public class WalletFragment extends Fragment
        implements TopUpSheetFragment.Host, DebitOrderSheetFragment.Host {

    private SwipeRefreshLayout swipeRefresh;
    private TextView balanceValue, availableValue, pendingValue, asOf, statusPill,
            accountNumber, notice, txnEmpty, debitEmpty, debitHelp;
    private LinearLayout txnContainer, debitContainer;
    private View topUpBtn, hideBtn, copyBtn;

    private TraderDashboardData.Wallet wallet;
    private DebitRequestsData debitOrders;

    /** Balances are hidden with the eye button; not persisted, it is a shoulder-surfing guard. */
    private boolean balancesHidden;
    private boolean walletLoading, debitLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        balanceValue = view.findViewById(R.id.walletBalance);
        availableValue = view.findViewById(R.id.walletAvailable);
        pendingValue = view.findViewById(R.id.walletPending);
        asOf = view.findViewById(R.id.walletAsOf);
        statusPill = view.findViewById(R.id.walletStatusPill);
        accountNumber = view.findViewById(R.id.walletAccountNumber);
        notice = view.findViewById(R.id.walletNotice);
        txnContainer = view.findViewById(R.id.txnContainer);
        txnEmpty = view.findViewById(R.id.txnEmpty);
        debitContainer = view.findViewById(R.id.debitContainer);
        debitEmpty = view.findViewById(R.id.debitEmpty);
        debitHelp = view.findViewById(R.id.debitHelp);
        topUpBtn = view.findViewById(R.id.btnTopUp);
        hideBtn = view.findViewById(R.id.walletHide);
        copyBtn = view.findViewById(R.id.btnCopyAccount);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> loadAll(false));

        view.findViewById(R.id.hamburgerButton).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        // A manual refresh bypasses the server's balance cache (refresh=1) — the point of
        // pressing it is to see money that has only just arrived.
        view.findViewById(R.id.walletRefresh).setOnClickListener(v -> loadWallet(true));
        hideBtn.setOnClickListener(v -> {
            balancesHidden = !balancesHidden;
            bindWallet();
        });
        copyBtn.setOnClickListener(v -> copyAccountNumber());

        topUpBtn.setOnClickListener(v -> openTopUp());
        view.findViewById(R.id.actionAdd).setOnClickListener(v -> openTopUp());
        view.findViewById(R.id.actionDebitOrder).setOnClickListener(v -> openDebitOrder());
        view.findViewById(R.id.actionAirtime).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AirtimeActivity.class)));
        // "Statement" is the wallet ledger, which is exactly what Finances already shows.
        view.findViewById(R.id.actionStatement).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FinancesActivity.class)));
        view.findViewById(R.id.btnViewAllTxns).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FinancesActivity.class)));
        view.findViewById(R.id.btnNewDebitOrder).setOnClickListener(v -> openDebitOrder());

        bindWallet();
        bindDebitOrders();
        loadAll(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            NotificationBadge.refresh(getContext(), (TextView) getView().findViewById(R.id.notificationBadge));
        }
    }

    // ---- Loading ---------------------------------------------------------

    private void loadAll(boolean firstLoad) {
        if (firstLoad) swipeRefresh.setRefreshing(true);
        loadWallet(false);
        loadDebitOrders();
    }

    private void loadWallet(boolean bypassCache) {
        loadWallet(bypassCache, bypassCache);
    }

    /**
     * @param bypassCache send {@code refresh=1} so the server re-reads the balance from IMB
     * @param announce    confirm with a toast. Off after a top-up, where the sheet has
     *                    already said the money landed — a toast on top is just noise.
     */
    private void loadWallet(boolean bypassCache, boolean announce) {
        if (walletLoading) return;
        walletLoading = true;
        setRefreshingSpinner(true);

        ApiClient.get(requireContext()).getWallet(bypassCache,
                new ApiCallback<TraderDashboardData.Wallet>() {
                    @Override
                    public void onSuccess(TraderDashboardData.Wallet data) {
                        walletLoading = false;
                        if (!isAdded()) return;
                        setRefreshingSpinner(false);
                        settleRefreshing();
                        wallet = data;
                        bindWallet();
                        if (announce) toast(getString(R.string.wallet_refreshed));
                    }

                    @Override
                    public void onError(String message) {
                        walletLoading = false;
                        if (!isAdded()) return;
                        setRefreshingSpinner(false);
                        settleRefreshing();
                        toast(message == null ? getString(R.string.wallet_load_failed) : message);
                    }
                });
    }

    private void loadDebitOrders() {
        if (debitLoading) return;
        debitLoading = true;

        ApiClient.get(requireContext()).getDebitRequests(new ApiCallback<DebitRequestsData>() {
            @Override
            public void onSuccess(DebitRequestsData data) {
                debitLoading = false;
                if (!isAdded()) return;
                settleRefreshing();
                debitOrders = data;
                bindDebitOrders();
            }

            @Override
            public void onError(String message) {
                debitLoading = false;
                if (!isAdded()) return;
                settleRefreshing();
                // Quiet: the balance above is the point of the screen, and a failed
                // debit-order read leaves that perfectly readable.
                bindDebitOrders();
            }
        });
    }

    /** The pull-to-refresh spinner only stops once both calls are done. */
    private void settleRefreshing() {
        if (!walletLoading && !debitLoading) swipeRefresh.setRefreshing(false);
    }

    private void setRefreshingSpinner(boolean busy) {
        View root = getView();
        if (root == null) return;
        View button = root.findViewById(R.id.walletRefresh);
        View progress = root.findViewById(R.id.walletRefreshProgress);
        button.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        button.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- Balance card ----------------------------------------------------

    private void bindWallet() {
        if (getView() == null) return;

        TraderDashboardData.Balance balance = wallet == null ? null : wallet.balance;
        boolean active = wallet != null && "active".equalsIgnoreCase(
                wallet.status == null ? "" : wallet.status);

        balanceValue.setText(money(balance == null ? 0 : balance.current));
        availableValue.setText(money(balance == null ? 0 : balance.available));
        pendingValue.setText(money(balance == null ? 0 : balance.pending));

        ((ImageView) hideBtn).setImageResource(balancesHidden
                ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        hideBtn.setContentDescription(getString(balancesHidden
                ? R.string.wallet_show_balance : R.string.wallet_hide_balance));

        // "as of" doubles as the staleness warning: a degraded balance read still returns
        // a number, and the trader needs to know that number may have moved on.
        String time = balance == null ? null : shortTime(balance.as_of);
        if (time == null) {
            asOf.setVisibility(View.GONE);
        } else {
            asOf.setVisibility(View.VISIBLE);
            asOf.setText(balance.stale
                    ? getString(R.string.wallet_as_of, time) + " · " + getString(R.string.wallet_stale)
                    : getString(R.string.wallet_as_of, time));
        }

        String status = wallet == null ? null : wallet.status;
        statusPill.setText(status == null || status.isEmpty()
                ? getString(R.string.wallet_status_active) : OrderFormat.humanize(status));
        statusPill.setVisibility(wallet == null ? View.INVISIBLE : View.VISIBLE);

        // Top-ups need a live wallet to land in, so the button goes away and says why.
        topUpBtn.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            notice.setVisibility(View.GONE);
        } else {
            notice.setVisibility(View.VISIBLE);
            String reason = wallet == null ? null : wallet.last_error;
            notice.setText(reason == null || reason.isEmpty()
                    ? getString(R.string.wallet_not_configured) : reason);
        }

        String number = wallet == null ? null : wallet.account_number;
        boolean hasNumber = number != null && !number.isEmpty();
        accountNumber.setText(hasNumber ? number : getString(R.string.em_dash));
        copyBtn.setVisibility(hasNumber ? View.VISIBLE : View.GONE);

        bindTransactions(balance == null ? null : balance.transactions);
    }

    /**
     * Money on the card, or a mask when balances are hidden. Everything on the purple
     * card goes through here, so the eye button cannot leave one figure showing.
     */
    private String money(double value) {
        return balancesHidden ? getString(R.string.wallet_amount_masked)
                : OrderFormat.money(value, "R");
    }

    // ---- Recent transactions ---------------------------------------------

    private void bindTransactions(@Nullable List<TraderDashboardData.WalletTxn> txns) {
        txnContainer.removeAllViews();
        if (txns == null || txns.isEmpty()) {
            txnEmpty.setVisibility(View.VISIBLE);
            return;
        }
        txnEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < txns.size(); i++) {
            TraderDashboardData.WalletTxn t = txns.get(i);
            View row = inflater.inflate(R.layout.item_wallet_txn, txnContainer, false);

            // The API sends no description, only a signed amount — so the sign is the
            // only thing that says which way the money went.
            double value = parseAmount(t.amount);
            boolean in = value >= 0;
            int tone = ContextCompat.getColor(requireContext(), in ? R.color.success : R.color.danger);

            ImageView icon = row.findViewById(R.id.txnIcon);
            icon.setImageResource(in ? R.drawable.ic_arrow_in : R.drawable.ic_arrow_out);
            icon.setBackgroundTintList(ColorStateList.valueOf(
                    Color.argb(28, Color.red(tone), Color.green(tone), Color.blue(tone))));
            icon.setImageTintList(ColorStateList.valueOf(tone));

            ((TextView) row.findViewById(R.id.txnTitle))
                    .setText(in ? R.string.wallet_money_in : R.string.wallet_money_out);
            ((TextView) row.findViewById(R.id.txnSubtitle)).setText(txnDate(t.date));

            TextView amount = row.findViewById(R.id.txnAmount);
            amount.setTextColor(tone);
            amount.setText((in ? "+" : "−") + OrderFormat.money(Math.abs(value), "R"));

            row.findViewById(R.id.txnStatus).setVisibility(t.pending ? View.VISIBLE : View.GONE);

            txnContainer.addView(row);
            if (i < txns.size() - 1) txnContainer.addView(divider());
        }
    }

    // ---- Debit orders ----------------------------------------------------

    private void bindDebitOrders() {
        if (getView() == null) return;
        debitContainer.removeAllViews();

        // available=false means the IMB payment rail is not configured for this merchant,
        // so say that plainly instead of offering a form that can only fail.
        boolean available = debitOrders == null || debitOrders.available;
        debitHelp.setText(available
                ? getString(R.string.wallet_debit_orders_help)
                : getString(R.string.wallet_debit_unavailable));
        getView().findViewById(R.id.btnNewDebitOrder)
                .setVisibility(available ? View.VISIBLE : View.GONE);

        List<DebitRequestsData.DebitRequest> rows =
                debitOrders == null ? null : debitOrders.debit_requests;
        if (rows == null || rows.isEmpty()) {
            debitEmpty.setVisibility(View.VISIBLE);
            return;
        }
        debitEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < rows.size(); i++) {
            DebitRequestsData.DebitRequest r = rows.get(i);
            View row = inflater.inflate(R.layout.item_wallet_debit_order, debitContainer, false);

            ((TextView) row.findViewById(R.id.debitReference)).setText(
                    r.reference == null || r.reference.isEmpty()
                            ? getString(R.string.em_dash) : r.reference);
            ((TextView) row.findViewById(R.id.debitSchedule)).setText(scheduleLabel(r));
            ((TextView) row.findViewById(R.id.debitAmount))
                    .setText(OrderFormat.money(r.amount, symbolFor(r.currency)));

            TextView status = row.findViewById(R.id.debitStatus);
            status.setText(OrderFormat.humanize(r.status == null ? "" : r.status));
            paintStatus(status, r.status);

            // A failed instruction is only useful if it says why IMB refused it.
            TextView error = row.findViewById(R.id.debitError);
            if (r.isFailed() && r.last_error != null && !r.last_error.isEmpty()) {
                error.setVisibility(View.VISIBLE);
                error.setText(r.last_error);
            } else {
                error.setVisibility(View.GONE);
            }

            debitContainer.addView(row);
            if (i < rows.size() - 1) debitContainer.addView(divider());
        }
    }

    private String scheduleLabel(DebitRequestsData.DebitRequest r) {
        String schedule = r.schedule == null ? "" : r.schedule.toLowerCase(Locale.US);
        if ("completed".equals(schedule)) return getString(R.string.debit_schedule_completed);

        if (r.once_off || "once-off".equals(schedule)) {
            String raised = OrderFormat.createdAt(r.created_at);
            return raised.isEmpty()
                    ? getString(R.string.debit_schedule_once_off)
                    : getString(R.string.debit_schedule_once_off) + " · "
                            + getString(R.string.debit_raised_on, raised);
        }
        return getString(R.string.debit_schedule_monthly,
                OrderFormat.deliveryDate(r.next_debit_date),
                OrderFormat.deliveryDate(r.final_debit_date));
    }

    private void paintStatus(TextView pill, @Nullable String status) {
        int background, text;
        switch (status == null ? "" : status.toLowerCase(Locale.US)) {
            case "active":
                background = R.drawable.bg_badge_success;
                text = R.color.success;
                break;
            case "failed":
                background = R.drawable.bg_badge_danger;
                text = R.color.danger;
                break;
            default:   // "pending" and anything new the API grows
                background = R.drawable.bg_badge_warning;
                text = R.color.warning;
                break;
        }
        pill.setBackgroundResource(background);
        pill.setTextColor(ContextCompat.getColor(requireContext(), text));
    }

    // ---- Actions ---------------------------------------------------------

    private void openTopUp() {
        if (wallet == null || !"active".equalsIgnoreCase(wallet.status == null ? "" : wallet.status)) {
            toast(getString(R.string.wallet_inactive_action));
            return;
        }
        if (getChildFragmentManager().findFragmentByTag(TopUpSheetFragment.TAG) != null) return;
        new TopUpSheetFragment().show(getChildFragmentManager(), TopUpSheetFragment.TAG);
    }

    private void openDebitOrder() {
        if (debitOrders != null && !debitOrders.available) {
            toast(getString(R.string.wallet_debit_unavailable));
            return;
        }
        if (getChildFragmentManager().findFragmentByTag(DebitOrderSheetFragment.TAG) != null) return;

        String account = debitOrders != null && debitOrders.account_number != null
                ? debitOrders.account_number
                : (wallet == null ? null : wallet.account_number);
        DebitOrderSheetFragment.newInstance(account,
                        debitOrders == null ? null : debitOrders.limitsOrDefault())
                .show(getChildFragmentManager(), DebitOrderSheetFragment.TAG);
    }

    @Override
    public void onWalletToppedUp() {
        // Money has actually landed, so read past the balance cache to prove it.
        loadWallet(true, false);
    }

    @Override
    public void onDebitOrderCreated() {
        loadDebitOrders();
    }

    private void copyAccountNumber() {
        String number = wallet == null ? null : wallet.account_number;
        if (number == null || number.isEmpty()) return;
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.wallet_imb_account), number));
        toast(getString(R.string.wallet_account_copied));
    }

    // ---- Helpers ---------------------------------------------------------

    private View divider() {
        View v = new View(requireContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.border_light));
        return v;
    }

    /** Debit orders carry an ISO code ("ZAR"); everything else on this screen shows "R". */
    private String symbolFor(@Nullable String currency) {
        return currency == null || currency.isEmpty() || "ZAR".equalsIgnoreCase(currency)
                ? "R" : currency + " ";
    }

    private double parseAmount(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The wallet's timestamps are ISO-8601 with an offset; the phone shows local time. */
    @Nullable
    private Date parseIso(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.US).parse(raw);
            } catch (Exception ignored) {
                // try the next shape
            }
        }
        return null;
    }

    @Nullable
    private String shortTime(@Nullable String iso) {
        Date d = parseIso(iso);
        return d == null ? null : new SimpleDateFormat("HH:mm", Locale.US).format(d);
    }

    private String txnDate(@Nullable String iso) {
        Date d = parseIso(iso);
        if (d == null) return iso == null ? "" : iso;
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
