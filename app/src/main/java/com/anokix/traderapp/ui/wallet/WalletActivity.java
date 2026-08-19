package com.anokix.traderapp.ui.wallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.DebitRequestsData;
import com.anokix.traderapp.network.dto.TraderDashboardData;
import com.anokix.traderapp.ui.AirtimeActivity;
import com.anokix.traderapp.ui.FinancesActivity;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The anokiX wallet (powered by IMB): live balance, the settlement account behind it, the
 * recent movements on it, and the two things a trader can actually do with it from a
 * phone — put money in with a card (PayCloud top-up) and stand a debit order against it.
 *
 * A screen of its own rather than a tab inside MainActivity. The wallet is reached from
 * several places (drawer, dashboard card, a returning PayCloud checkout) and a trader who
 * opens it mid-task expects Back to put them where they were, not on the home tab.
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
public class WalletActivity extends AppCompatActivity
        implements TopUpSheetFragment.Host, DebitOrderSheetFragment.Host {

    private SwipeRefreshLayout swipeRefresh;
    private TextView balanceValue, availableValue, pendingValue, asOf, statusPill,
            accountNumber, activatedAt, notice, txnEmpty, debitEmpty, debitHelp;
    private LinearLayout txnContainer, debitContainer;
    private View topUpBtn, hideBtn, copyBtn;

    private TraderDashboardData.Wallet wallet;
    private DebitRequestsData debitOrders;

    /** Balances are hidden with the eye button; not persisted, it is a shoulder-surfing guard. */
    private boolean balancesHidden;
    private boolean walletLoading, debitLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        balanceValue = findViewById(R.id.walletBalance);
        availableValue = findViewById(R.id.walletAvailable);
        pendingValue = findViewById(R.id.walletPending);
        asOf = findViewById(R.id.walletAsOf);
        statusPill = findViewById(R.id.walletStatusPill);
        accountNumber = findViewById(R.id.walletAccountNumber);
        activatedAt = findViewById(R.id.walletActivated);
        notice = findViewById(R.id.walletNotice);
        txnContainer = findViewById(R.id.txnContainer);
        txnEmpty = findViewById(R.id.txnEmpty);
        debitContainer = findViewById(R.id.debitContainer);
        debitEmpty = findViewById(R.id.debitEmpty);
        debitHelp = findViewById(R.id.debitHelp);
        topUpBtn = findViewById(R.id.btnTopUp);
        hideBtn = findViewById(R.id.walletHide);
        copyBtn = findViewById(R.id.btnCopyAccount);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> loadAll(false));

        // A manual refresh bypasses the server's balance cache (refresh=1) — the point of
        // pressing it is to see money that has only just arrived.
        findViewById(R.id.walletRefresh).setOnClickListener(v -> loadWallet(true));
        hideBtn.setOnClickListener(v -> {
            balancesHidden = !balancesHidden;
            bindWallet();
        });
        copyBtn.setOnClickListener(v -> copyAccountNumber());

        topUpBtn.setOnClickListener(v -> openTopUp());
        findViewById(R.id.actionAdd).setOnClickListener(v -> openTopUp());
        findViewById(R.id.actionDebitOrder).setOnClickListener(v -> openDebitOrder());
        findViewById(R.id.actionAirtime).setOnClickListener(v ->
                startActivity(new Intent(this, AirtimeActivity.class)));
        // "Statement" is the wallet ledger, which is exactly what Finances already shows.
        findViewById(R.id.actionStatement).setOnClickListener(v -> openStatement());
        findViewById(R.id.btnViewStatement).setOnClickListener(v -> openStatement());
        findViewById(R.id.btnViewAllTxns).setOnClickListener(v -> openStatement());
        findViewById(R.id.btnNewDebitOrder).setOnClickListener(v -> openDebitOrder());

        bindWallet();
        bindDebitOrders();
        loadAll(true);
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

        ApiClient.get(this).getWallet(bypassCache,
                new ApiCallback<TraderDashboardData.Wallet>() {
                    @Override
                    public void onSuccess(TraderDashboardData.Wallet data) {
                        walletLoading = false;
                        if (isFinishing() || isDestroyed()) return;
                        setRefreshingSpinner(false);
                        settleRefreshing();
                        wallet = data;
                        bindWallet();
                        if (announce) toast(getString(R.string.wallet_refreshed));
                    }

                    @Override
                    public void onError(String message) {
                        walletLoading = false;
                        if (isFinishing() || isDestroyed()) return;
                        setRefreshingSpinner(false);
                        settleRefreshing();
                        toast(message == null ? getString(R.string.wallet_load_failed) : message);
                    }
                });
    }

    private void loadDebitOrders() {
        if (debitLoading) return;
        debitLoading = true;

        ApiClient.get(this).getDebitRequests(new ApiCallback<DebitRequestsData>() {
            @Override
            public void onSuccess(DebitRequestsData data) {
                debitLoading = false;
                if (isFinishing() || isDestroyed()) return;
                settleRefreshing();
                debitOrders = data;
                bindDebitOrders();
            }

            @Override
            public void onError(String message) {
                debitLoading = false;
                if (isFinishing() || isDestroyed()) return;
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
        View button = findViewById(R.id.walletRefresh);
        View progress = findViewById(R.id.walletRefreshProgress);
        button.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        button.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- Balance card ----------------------------------------------------

    private void bindWallet() {
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

        bindSettlementAccount();
        bindTransactions(balance == null ? null : balance.transactions);
    }

    private void bindSettlementAccount() {
        String number = wallet == null ? null : wallet.account_number;
        boolean hasNumber = number != null && !number.isEmpty();
        accountNumber.setText(hasNumber ? spaceOut(number) : getString(R.string.em_dash));
        copyBtn.setVisibility(hasNumber ? View.VISIBLE : View.GONE);

        String opened = wallet == null ? null : wallet.activated_at;
        Date when = parseIso(opened);
        if (when == null) {
            activatedAt.setVisibility(View.GONE);
        } else {
            activatedAt.setVisibility(View.VISIBLE);
            activatedAt.setText(getString(R.string.wallet_activated_at,
                    new SimpleDateFormat("dd MMM yyyy", Locale.US).format(when)));
        }
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

    /**
     * The list arrives flat and newest-first, so it is grouped under date headers as it
     * is laid out — ten bare timestamps in a column is a log, not a statement.
     */
    private void bindTransactions(@Nullable List<TraderDashboardData.WalletTxn> txns) {
        txnContainer.removeAllViews();
        if (txns == null || txns.isEmpty()) {
            txnEmpty.setVisibility(View.VISIBLE);
            return;
        }
        txnEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        String currentDay = null;

        for (int i = 0; i < txns.size(); i++) {
            TraderDashboardData.WalletTxn t = txns.get(i);

            String day = dayLabel(t.date);
            if (!day.equals(currentDay)) {
                TextView header = (TextView) inflater.inflate(
                        R.layout.item_wallet_day_header, txnContainer, false);
                header.setText(day);
                txnContainer.addView(header);
                currentDay = day;
            } else {
                txnContainer.addView(divider());
            }

            View row = inflater.inflate(R.layout.item_wallet_txn, txnContainer, false);

            // The API sends no description, only a signed amount — so the sign is the
            // only thing that says which way the money went.
            double value = parseAmount(t.amount);
            boolean in = value >= 0;
            int tone = ContextCompat.getColor(this, in ? R.color.success : R.color.danger);
            // Pale tile behind a saturated glyph — the same pairing the portal uses. Both
            // tints are opaque, so the icon reads clearly whatever the row sits on.
            int tile = ContextCompat.getColor(this, in ? R.color.success_bg : R.color.danger_bg);

            ImageView icon = row.findViewById(R.id.txnIcon);
            icon.setImageResource(in ? R.drawable.ic_arrow_in : R.drawable.ic_arrow_out);
            icon.setBackgroundTintList(ColorStateList.valueOf(tile));
            icon.setImageTintList(ColorStateList.valueOf(tone));

            ((TextView) row.findViewById(R.id.txnTitle))
                    .setText(in ? R.string.wallet_money_in : R.string.wallet_money_out);
            // The date is already on the group header above, so the row only needs the time.
            ((TextView) row.findViewById(R.id.txnSubtitle)).setText(clockTime(t.date));

            TextView amount = row.findViewById(R.id.txnAmount);
            amount.setTextColor(tone);
            amount.setText(getString(in ? R.string.wallet_amount_in : R.string.wallet_amount_out,
                    OrderFormat.money(Math.abs(value), "R")));

            row.findViewById(R.id.txnStatus).setVisibility(t.pending ? View.VISIBLE : View.GONE);

            txnContainer.addView(row);
        }
    }

    // ---- Debit orders ----------------------------------------------------

    private void bindDebitOrders() {
        debitContainer.removeAllViews();

        // available=false means the IMB payment rail is not configured for this merchant,
        // so say that plainly instead of offering a form that can only fail.
        boolean available = debitOrders == null || debitOrders.available;
        debitHelp.setText(available
                ? getString(R.string.wallet_debit_orders_help)
                : getString(R.string.wallet_debit_unavailable));
        findViewById(R.id.btnNewDebitOrder).setVisibility(available ? View.VISIBLE : View.GONE);

        List<DebitRequestsData.DebitRequest> rows =
                debitOrders == null ? null : debitOrders.debit_requests;
        if (rows == null || rows.isEmpty()) {
            debitEmpty.setVisibility(View.VISIBLE);
            return;
        }
        debitEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
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
        pill.setTextColor(ContextCompat.getColor(this, text));
    }

    // ---- Actions ---------------------------------------------------------

    private void openStatement() {
        startActivity(new Intent(this, FinancesActivity.class));
    }

    private void openTopUp() {
        if (wallet == null || !"active".equalsIgnoreCase(wallet.status == null ? "" : wallet.status)) {
            toast(getString(R.string.wallet_inactive_action));
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag(TopUpSheetFragment.TAG) != null) return;
        new TopUpSheetFragment().show(getSupportFragmentManager(), TopUpSheetFragment.TAG);
    }

    private void openDebitOrder() {
        if (debitOrders != null && !debitOrders.available) {
            toast(getString(R.string.wallet_debit_unavailable));
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag(DebitOrderSheetFragment.TAG) != null) return;

        String account = debitOrders != null && debitOrders.account_number != null
                ? debitOrders.account_number
                : (wallet == null ? null : wallet.account_number);
        DebitOrderSheetFragment.newInstance(account,
                        debitOrders == null ? null : debitOrders.limitsOrDefault())
                .show(getSupportFragmentManager(), DebitOrderSheetFragment.TAG);
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
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.wallet_imb_account), number));
        toast(getString(R.string.wallet_account_copied));
    }

    // ---- Helpers ---------------------------------------------------------

    private View divider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(this, R.color.border_light));
        return v;
    }

    /** Groups an 11-digit account number as 462 103 060 83 so it can be read back aloud. */
    private String spaceOut(String number) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            if (i > 0 && i % 3 == 0) out.append(' ');
            out.append(number.charAt(i));
        }
        return out.toString();
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

    private String clockTime(@Nullable String iso) {
        Date d = parseIso(iso);
        return d == null ? getString(R.string.em_dash)
                : new SimpleDateFormat("HH:mm", Locale.US).format(d);
    }

    /** "Today" / "Yesterday" / "07 Aug 2026" — the header a run of rows sits under. */
    private String dayLabel(@Nullable String iso) {
        Date d = parseIso(iso);
        if (d == null) return getString(R.string.wallet_day_earlier);

        Calendar then = Calendar.getInstance();
        then.setTime(d);
        Calendar today = Calendar.getInstance();
        if (sameDay(then, today)) return getString(R.string.wallet_day_today);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, today)) return getString(R.string.wallet_day_yesterday);

        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
