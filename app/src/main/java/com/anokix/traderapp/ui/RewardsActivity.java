package com.anokix.traderapp.ui;

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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.RewardsOverviewData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * anokiX rewards (Limes) — the whole screen off GET api/common/rewards/overview, laid out
 * in the reading order of the portal's three columns: points balance, benefits, quick
 * redeem, VAS services, promotions, the voucher catalog, the points ledger, tier progress
 * and Invite &amp; Earn.
 *
 * Two things here can be acted on rather than only read:
 * <ul>
 *   <li><b>Airtime and Data</b> hand off to {@link AirtimeActivity}, which is the live VAS
 *       rail. The other service tiles say "coming soon" — there is no endpoint behind
 *       electricity, water or TV yet, and a tile that pretends otherwise is worse than one
 *       that is honest.</li>
 *   <li><b>Redeem</b> spends real points through POST api/common/rewards/redeem, behind a
 *       confirmation sheet that restates the cost and the balance left over.</li>
 * </ul>
 */
public class RewardsActivity extends AppCompatActivity {

    private static final int PER_PAGE = 20;

    /** Quick Redeem grid, in portal order. {@code null} action = not built yet. */
    private static final Tile[] QUICK_REDEEM = {
            new Tile(R.string.rewards_vas_airtime, R.drawable.ic_phone, "#16A34A", Action.AIRTIME),
            new Tile(R.string.rewards_vas_data, R.drawable.ic_wifi, "#2563EB", Action.AIRTIME),
            new Tile(R.string.rewards_vas_electricity, R.drawable.ic_bolt, "#EA580C", Action.SOON),
            new Tile(R.string.rewards_vas_water, R.drawable.ic_droplet, "#0891B2", Action.SOON),
            new Tile(R.string.rewards_quick_loyalty, R.drawable.ic_store, "#7C3AED", Action.SOON),
            new Tile(R.string.rewards_quick_cashback, R.drawable.ic_wallet, "#16A34A", Action.SOON),
            new Tile(R.string.rewards_quick_vouchers, R.drawable.ic_gift_box, "#D97706", Action.VOUCHERS),
            new Tile(R.string.rewards_vas_more, R.drawable.ic_more, "#64748B", Action.SOON),
    };

    /** The descriptive "VAS Services" row. */
    private static final Tile[] VAS_SERVICES = {
            new Tile(R.string.rewards_vas_airtime, R.string.rewards_vas_airtime_sub,
                    R.drawable.ic_phone, "#16A34A", Action.AIRTIME),
            new Tile(R.string.rewards_vas_data, R.string.rewards_vas_data_sub,
                    R.drawable.ic_wifi, "#2563EB", Action.AIRTIME),
            new Tile(R.string.rewards_vas_electricity, R.string.rewards_vas_electricity_sub,
                    R.drawable.ic_bolt, "#EA580C", Action.SOON),
            new Tile(R.string.rewards_vas_water, R.string.rewards_vas_water_sub,
                    R.drawable.ic_droplet, "#0891B2", Action.SOON),
            new Tile(R.string.rewards_vas_tv, R.string.rewards_vas_tv_sub,
                    R.drawable.ic_tv, "#7C3AED", Action.SOON),
            new Tile(R.string.rewards_vas_more, R.string.rewards_vas_more_sub,
                    R.drawable.ic_more, "#64748B", Action.AIRTIME),
    };

    private enum Action { AIRTIME, VOUCHERS, SOON }

    private static final class Tile {
        final int label;
        final int sublabel;
        @DrawableRes final int icon;
        final String hex;
        final Action action;

        Tile(int label, @DrawableRes int icon, String hex, Action action) {
            this(label, 0, icon, hex, action);
        }

        Tile(int label, int sublabel, @DrawableRes int icon, String hex, Action action) {
            this.label = label;
            this.sublabel = sublabel;
            this.icon = icon;
            this.hex = hex;
            this.action = action;
        }
    }

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout benefitsColumn, quickRedeemGrid, vasRow, promoRow, voucherRow, activityColumn;
    private TextView activityEmpty, activityTotal, inviteBody, inviteCode;
    private MaterialButton loadMoreBtn;
    private View voucherScroll, voucherHeading, promoScroll, promoHeading, activityDivider;

    private RewardsOverviewData rewards;
    /** The ledger accumulates across pages; everything else is replaced on each load. */
    private final List<RewardsOverviewData.Activity> activity = new ArrayList<>();
    private int page = 1;
    private boolean loading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        benefitsColumn = findViewById(R.id.benefitsColumn);
        quickRedeemGrid = findViewById(R.id.quickRedeemGrid);
        vasRow = findViewById(R.id.vasRow);
        promoRow = findViewById(R.id.promoRow);
        voucherRow = findViewById(R.id.voucherRow);
        activityColumn = findViewById(R.id.activityColumn);
        activityEmpty = findViewById(R.id.activityEmpty);
        activityTotal = findViewById(R.id.activityTotal);
        activityDivider = findViewById(R.id.activityDivider);
        inviteBody = findViewById(R.id.inviteBody);
        inviteCode = findViewById(R.id.inviteCode);
        loadMoreBtn = findViewById(R.id.btnLoadMore);
        voucherScroll = findViewById(R.id.voucherScroll);
        voucherHeading = findViewById(R.id.voucherHeading);
        promoScroll = findViewById(R.id.promoScroll);
        promoHeading = findViewById(R.id.promoHeading);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.success);
        swipeRefresh.setOnRefreshListener(this::reload);

        loadMoreBtn.setOnClickListener(v -> loadPage(page + 1));
        findViewById(R.id.btnVasViewAll).setOnClickListener(v -> openAirtime());
        findViewById(R.id.btnCopyInvite).setOnClickListener(v -> copyInviteCode());
        findViewById(R.id.btnCopyInviteCode).setOnClickListener(v -> copyInviteCode());
        findViewById(R.id.btnShareInvite).setOnClickListener(v -> shareInviteCode());

        // These two never change, so they are built once rather than on every load.
        buildQuickRedeem();
        buildVasServices();

        swipeRefresh.setRefreshing(true);
        loadPage(1);
    }

    // ---- Loading ---------------------------------------------------------

    private void reload() {
        activity.clear();
        loadPage(1);
    }

    /**
     * Page 1 replaces the ledger; later pages append to it. Everything outside the ledger
     * is re-read on every page, which costs nothing extra — the endpoint returns the whole
     * screen — and keeps the balance honest after a redemption.
     */
    private void loadPage(int target) {
        if (loading) return;
        loading = true;
        loadMoreBtn.setEnabled(false);

        ApiClient.get(this).getRewardsOverview(target, PER_PAGE,
                new ApiCallback<RewardsOverviewData>() {
                    @Override
                    public void onSuccess(RewardsOverviewData data) {
                        loading = false;
                        if (isFinishing() || isDestroyed()) return;
                        swipeRefresh.setRefreshing(false);
                        loadMoreBtn.setEnabled(true);
                        if (data == null) return;

                        rewards = data;
                        page = data.pagination == null ? target : data.pagination.page;
                        if (target <= 1) activity.clear();
                        if (data.activity != null) activity.addAll(data.activity);

                        bindAll();
                    }

                    @Override
                    public void onError(String message) {
                        loading = false;
                        if (isFinishing() || isDestroyed()) return;
                        swipeRefresh.setRefreshing(false);
                        loadMoreBtn.setEnabled(true);
                        toast(message == null ? getString(R.string.rewards_load_failed) : message);
                    }
                });
    }

    private void bindAll() {
        bindBalance();
        bindBenefits();
        bindPromotions();
        bindVouchers();
        bindActivity();
        bindTier();
        bindReferral();
    }

    // ---- Points balance hero ---------------------------------------------

    private void bindBalance() {
        RewardsOverviewData.Balance b = rewards == null ? null : rewards.balance;

        ((TextView) findViewById(R.id.pointsBalance))
                .setText(getString(R.string.rewards_points_fmt, points(b == null ? 0 : b.points)));
        ((TextView) findViewById(R.id.pointsValue)).setText(getString(R.string.rewards_value_fmt,
                OrderFormat.money(b == null ? 0 : b.value, "R")));

        // The portal labels this "Points Earned (Aug)" — the month is the current one, so
        // it is read off the clock rather than parsed out of anything.
        ((TextView) findViewById(R.id.earnedLabel)).setText(getString(
                R.string.rewards_earned_label_month,
                new SimpleDateFormat("MMM", Locale.US).format(new Date())));
        ((TextView) findViewById(R.id.earnedValue)).setText(getString(
                R.string.rewards_points_plus, points(b == null ? 0 : b.earned_this_month)));

        TextView expiring = findViewById(R.id.expiringValue);
        if (b == null || b.expiring_date == null || b.expiring_date.isEmpty()) {
            expiring.setText(b == null || b.expiring == 0
                    ? getString(R.string.rewards_expiring_none)
                    : getString(R.string.rewards_points_fmt, points(b.expiring)));
        } else {
            expiring.setText(getString(R.string.rewards_expiring_on,
                    points(b.expiring), b.expiring_date));
        }
    }

    // ---- Benefits --------------------------------------------------------

    private void bindBenefits() {
        RewardsOverviewData.Benefits benefits = rewards == null ? null : rewards.benefits;
        benefitsColumn.removeAllViews();

        addBenefit(R.string.rewards_benefit_cashback, R.drawable.ic_wallet, "#16A34A",
                OrderFormat.money(benefits == null ? 0 : benefits.cashback, "R"));
        addBenefit(R.string.rewards_benefit_tier, R.drawable.ic_promo_crown, "#7C3AED",
                benefits == null || benefits.tier == null || benefits.tier.isEmpty()
                        ? getString(R.string.em_dash) : benefits.tier);
        addBenefit(R.string.rewards_benefit_referral, R.drawable.ic_person_add, "#2563EB",
                getString(R.string.rewards_points_fmt, points(benefits == null ? 0 : benefits.referral)));
    }

    private void addBenefit(int label, @DrawableRes int icon, String hex, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_reward_benefit, benefitsColumn, false);
        paintIcon(row.findViewById(R.id.benefitIcon), icon, hex);
        ((TextView) row.findViewById(R.id.benefitLabel)).setText(label);
        ((TextView) row.findViewById(R.id.benefitValue)).setText(value);
        benefitsColumn.addView(row);
    }

    // ---- Quick Redeem + VAS Services -------------------------------------

    private void buildQuickRedeem() {
        LayoutInflater inflater = getLayoutInflater();
        LinearLayout row = null;
        for (int i = 0; i < QUICK_REDEEM.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = i == 0 ? 0 : dp(10);
                quickRedeemGrid.addView(row, lp);
            }

            Tile tile = QUICK_REDEEM[i];
            View cell = inflater.inflate(R.layout.item_quick_action, row, false);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            View box = cell.findViewById(R.id.quickIconContainer);
            ImageView icon = cell.findViewById(R.id.quickIcon);
            int color = Color.parseColor(tile.hex);
            box.setBackgroundTintList(ColorStateList.valueOf(pale(color)));
            icon.setImageResource(tile.icon);
            icon.setImageTintList(ColorStateList.valueOf(color));
            ((TextView) cell.findViewById(R.id.quickLabel)).setText(tile.label);

            cell.setOnClickListener(v -> runAction(tile));
            row.addView(cell);
        }
    }

    private void buildVasServices() {
        LayoutInflater inflater = getLayoutInflater();
        for (Tile tile : VAS_SERVICES) {
            View card = inflater.inflate(R.layout.item_vas_service, vasRow, false);
            paintIcon(card.findViewById(R.id.vasIcon), tile.icon, tile.hex);
            ((TextView) card.findViewById(R.id.vasTitle)).setText(tile.label);
            ((TextView) card.findViewById(R.id.vasSubtitle)).setText(tile.sublabel);
            card.setOnClickListener(v -> runAction(tile));
            vasRow.addView(card);
        }
    }

    private void runAction(Tile tile) {
        switch (tile.action) {
            case AIRTIME:
                openAirtime();
                break;
            case VOUCHERS:
                // Everything redeemable is already on this screen, so scroll to it rather
                // than opening a second list of the same six vouchers.
                scrollTo(voucherHeading);
                break;
            default:
                toast(getString(R.string.rewards_coming_soon, getString(tile.label)));
                break;
        }
    }

    private void openAirtime() {
        startActivity(new Intent(this, AirtimeActivity.class));
    }

    /** Scrolls the page so {@code target} sits just under the toolbar. */
    private void scrollTo(View target) {
        if (target.getVisibility() != View.VISIBLE) return;
        androidx.core.widget.NestedScrollView scroll = findViewById(R.id.rewardsScroll);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, (int) target.getY() - dp(8))));
    }

    // ---- Promotions ------------------------------------------------------

    private void bindPromotions() {
        List<RewardsOverviewData.Promotion> promos = rewards == null ? null : rewards.promotions;
        promoRow.removeAllViews();

        boolean any = promos != null && !promos.isEmpty();
        promoHeading.setVisibility(any ? View.VISIBLE : View.GONE);
        promoScroll.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) return;

        LayoutInflater inflater = getLayoutInflater();
        for (RewardsOverviewData.Promotion p : promos) {
            View card = inflater.inflate(R.layout.item_reward_promo, promoRow, false);
            ((TextView) card.findViewById(R.id.promoTitle)).setText(p.title);
            ((TextView) card.findViewById(R.id.promoBody)).setText(p.description);
            paintIcon(card.findViewById(R.id.promoIcon), R.drawable.ic_promo_gift, toneColor(p.tone));
            promoRow.addView(card);
        }
    }

    /** The API's promo tone keys. Anything unrecognised falls back to the brand purple. */
    private static String toneColor(String tone) {
        if (tone == null) return "#7C3AED";
        switch (tone.toLowerCase(Locale.US)) {
            case "green": return "#16A34A";
            case "lime":  return "#65A30D";
            case "blue":  return "#2563EB";
            case "orange": return "#EA580C";
            default:      return "#7C3AED";
        }
    }

    // ---- Voucher catalog -------------------------------------------------

    private void bindVouchers() {
        List<RewardsOverviewData.Voucher> vouchers = rewards == null ? null : rewards.vouchers;
        voucherRow.removeAllViews();

        boolean any = vouchers != null && !vouchers.isEmpty();
        voucherHeading.setVisibility(any ? View.VISIBLE : View.GONE);
        voucherScroll.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) return;

        long balance = balancePoints();
        LayoutInflater inflater = getLayoutInflater();
        for (RewardsOverviewData.Voucher v : vouchers) {
            View card = inflater.inflate(R.layout.item_reward_voucher, voucherRow, false);

            TextView initial = card.findViewById(R.id.voucherInitial);
            initial.setText(v.initial());
            initial.setBackgroundTintList(ColorStateList.valueOf(safeColor(v.color, "#7C3AED")));

            ((TextView) card.findViewById(R.id.voucherBrand)).setText(v.brand);
            ((TextView) card.findViewById(R.id.voucherTitle)).setText(v.title);
            ((TextView) card.findViewById(R.id.voucherPoints)).setText(v.pointsLabel());

            // Affordability is decided here rather than at the API: a Redeem button that
            // can only come back with "insufficient balance" should not look live.
            MaterialButton redeem = card.findViewById(R.id.voucherRedeem);
            boolean affordable = balance >= v.points;
            redeem.setEnabled(affordable);
            int tone = ContextCompat.getColor(this, affordable ? R.color.success : R.color.text_tertiary);
            redeem.setTextColor(tone);
            redeem.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, affordable ? R.color.success : R.color.border)));
            redeem.setOnClickListener(b -> showRedeemSheet(v));

            voucherRow.addView(card);
        }
    }

    private long balancePoints() {
        return rewards == null || rewards.balance == null ? 0 : rewards.balance.points;
    }

    // ---- Redeem ----------------------------------------------------------

    private void showRedeemSheet(RewardsOverviewData.Voucher v) {
        long balance = balancePoints();
        if (balance < v.points) {
            toast(getString(R.string.rewards_redeem_short, points(v.points - balance)));
            return;
        }

        View sheet = getLayoutInflater().inflate(R.layout.sheet_reward_redeem, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        TextView initial = sheet.findViewById(R.id.redeemInitial);
        initial.setText(v.initial());
        initial.setBackgroundTintList(ColorStateList.valueOf(safeColor(v.color, "#7C3AED")));

        ((TextView) sheet.findViewById(R.id.redeemBrand)).setText(v.brand);
        ((TextView) sheet.findViewById(R.id.redeemTitle)).setText(v.title);
        ((TextView) sheet.findViewById(R.id.redeemCost))
                .setText(getString(R.string.rewards_points_fmt, points(v.points)));
        ((TextView) sheet.findViewById(R.id.redeemAfter))
                .setText(getString(R.string.rewards_points_fmt, points(balance - v.points)));

        // Only Limes-fulfilled airtime/data need a number; asking for one otherwise would
        // be a field the API ignores.
        View msisdnBlock = sheet.findViewById(R.id.msisdnBlock);
        EditText msisdnInput = sheet.findViewById(R.id.inputMsisdn);
        msisdnBlock.setVisibility(v.needs_msisdn ? View.VISIBLE : View.GONE);

        ((TextView) sheet.findViewById(R.id.redeemFootnote)).setText(
                "limes".equalsIgnoreCase(v.provider == null ? "" : v.provider)
                        ? R.string.rewards_redeem_note_live
                        : R.string.rewards_redeem_note_pending);

        MaterialButton confirm = sheet.findViewById(R.id.btnConfirmRedeem);
        sheet.findViewById(R.id.sheetClose).setOnClickListener(x -> dialog.dismiss());

        confirm.setOnClickListener(x -> {
            String msisdn = null;
            if (v.needs_msisdn) {
                msisdn = msisdnInput.getText().toString().trim();
                if (msisdn.isEmpty()) {
                    msisdnInput.setError(getString(R.string.rewards_redeem_err_msisdn));
                    msisdnInput.requestFocus();
                    return;
                }
                String digits = msisdn.replaceAll("[^0-9]", "");
                if (digits.length() < 9 || digits.length() > 13) {
                    msisdnInput.setError(getString(R.string.rewards_redeem_err_msisdn_length));
                    msisdnInput.requestFocus();
                    return;
                }
                msisdn = digits;
            }

            confirm.setEnabled(false);
            confirm.setText(R.string.please_wait);
            ApiClient.get(this).redeemReward(v.id, msisdn,
                    new ApiCallback<RewardsOverviewData.RedeemResult>() {
                        @Override
                        public void onSuccess(RewardsOverviewData.RedeemResult result) {
                            onSuccess(result, null);
                        }

                        /** The server's own wording is worth showing verbatim. */
                        @Override
                        public void onSuccess(RewardsOverviewData.RedeemResult result, String message) {
                            if (isFinishing() || isDestroyed()) return;
                            dialog.dismiss();
                            toast(message == null || message.isEmpty()
                                    ? getString(R.string.rewards_redeemed, v.title) : message);

                            // The response carries the balance left over, so the hero and
                            // the affordability of every other voucher settle immediately;
                            // the reload behind it then picks up the new ledger entry.
                            if (result != null && result.balance != null && rewards != null) {
                                rewards.balance = result.balance;
                                bindBalance();
                                bindVouchers();
                            }
                            reload();
                        }

                        @Override
                        public void onError(String message) {
                            if (isFinishing() || isDestroyed()) return;
                            confirm.setEnabled(true);
                            confirm.setText(R.string.rewards_redeem_confirm);
                            toast(message == null ? getString(R.string.rewards_redeem_failed) : message);
                        }
                    });
        });

        expand(dialog, sheet);
    }

    private void expand(BottomSheetDialog dialog, View sheet) {
        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
        dialog.show();
    }

    // ---- Points ledger ---------------------------------------------------

    private void bindActivity() {
        activityColumn.removeAllViews();

        if (activity.isEmpty()) {
            activityEmpty.setVisibility(View.VISIBLE);
            activityDivider.setVisibility(View.GONE);
            loadMoreBtn.setVisibility(View.GONE);
        } else {
            activityEmpty.setVisibility(View.GONE);
            activityDivider.setVisibility(View.VISIBLE);

            LayoutInflater inflater = getLayoutInflater();
            for (int i = 0; i < activity.size(); i++) {
                RewardsOverviewData.Activity e = activity.get(i);
                View row = inflater.inflate(R.layout.item_points_entry, activityColumn, false);

                paintIcon(row.findViewById(R.id.pointsIcon), activityIcon(e.type),
                        e.positive ? "#16A34A" : "#7C3AED");
                ((TextView) row.findViewById(R.id.pointsTitle)).setText(e.title);
                ((TextView) row.findViewById(R.id.pointsDesc)).setText(e.description);
                ((TextView) row.findViewById(R.id.pointsDate))
                        .setText(OrderFormat.createdAt(e.date));

                TextView value = row.findViewById(R.id.pointsValue);
                value.setText(getString(R.string.rewards_points_fmt, e.signedPoints()));
                value.setTextColor(ContextCompat.getColor(this,
                        e.positive ? R.color.success : R.color.danger));

                activityColumn.addView(row);
                if (i < activity.size() - 1) activityColumn.addView(divider());
            }

            boolean more = rewards != null && rewards.pagination != null && rewards.pagination.hasMore();
            loadMoreBtn.setVisibility(more ? View.VISIBLE : View.GONE);
        }

        long earned = rewards == null || rewards.balance == null ? 0 : rewards.balance.earned_this_month;
        activityTotal.setText(getString(R.string.rewards_total_earned,
                new SimpleDateFormat("MMM", Locale.US).format(new Date()),
                getString(R.string.rewards_points_plus, points(earned))));
    }

    @DrawableRes
    private static int activityIcon(String type) {
        if (type == null) return R.drawable.ic_promo_gift;
        switch (type.toLowerCase(Locale.US)) {
            case "cashback": return R.drawable.ic_wallet;
            case "redeemed": return R.drawable.ic_gift_box;
            case "expired":  return R.drawable.ic_clock;
            default:         return R.drawable.ic_star;
        }
    }

    // ---- Tier ------------------------------------------------------------

    private void bindTier() {
        RewardsOverviewData.Tier tier = rewards == null ? null : rewards.tier;

        String level = tier == null || tier.level == null || tier.level.isEmpty() ? null : tier.level;
        ((TextView) findViewById(R.id.tierLevel)).setText(level == null
                ? getString(R.string.em_dash) : getString(R.string.rewards_tier_level, level));

        TextView away = findViewById(R.id.tierAway);
        away.setText(tier != null && tier.hasNextLevel()
                ? getString(R.string.rewards_tier_away, points(tier.points_away), tier.next_level)
                : getString(R.string.rewards_tier_top));

        ProgressBar bar = findViewById(R.id.tierProgress);
        bar.setProgress(tier == null ? 0 : Math.round(tier.fraction() * 100));

        ((TextView) findViewById(R.id.tierCounts)).setText(getString(R.string.rewards_tier_counts,
                points(tier == null ? 0 : tier.current), points(tier == null ? 0 : tier.target)));
    }

    // ---- Invite & Earn ---------------------------------------------------

    private void bindReferral() {
        RewardsOverviewData.Referral r = rewards == null ? null : rewards.referral;

        long reward = r == null ? 0 : r.reward;
        long earned = r == null ? 0 : r.earnings;
        inviteBody.setText(earned > 0
                ? getString(R.string.rewards_invite_body_earned, points(reward), points(earned))
                : getString(R.string.rewards_invite_body, points(reward)));

        String code = r == null ? null : r.code;
        boolean hasCode = code != null && !code.isEmpty();
        inviteCode.setText(hasCode ? code : getString(R.string.em_dash));
        findViewById(R.id.btnCopyInvite).setVisibility(hasCode ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCopyInviteCode).setEnabled(hasCode);
        findViewById(R.id.btnShareInvite).setEnabled(hasCode);
    }

    private void copyInviteCode() {
        String code = inviteCodeOrNull();
        if (code == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.rewards_invite_title), code));
        toast(getString(R.string.rewards_invite_copied));
    }

    private void shareInviteCode() {
        String code = inviteCodeOrNull();
        if (code == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, getString(R.string.rewards_invite_share_text, code));
        startActivity(Intent.createChooser(share, getString(R.string.rewards_share_invite)));
    }

    @Nullable
    private String inviteCodeOrNull() {
        RewardsOverviewData.Referral r = rewards == null ? null : rewards.referral;
        if (r == null || r.code == null || r.code.isEmpty()) return null;
        return r.code;
    }

    // ---- Helpers ---------------------------------------------------------

    /**
     * Paints an icon tile: a pale wash of the colour behind, the full colour on the glyph.
     * Keeping the two apart is the whole point — a saturated icon on a saturated tile is
     * unreadable.
     */
    private void paintIcon(ImageView view, @DrawableRes int icon, String hex) {
        int color = safeColor(hex, "#7C3AED");
        view.setImageResource(icon);
        view.setImageTintList(ColorStateList.valueOf(color));
        view.setBackgroundTintList(ColorStateList.valueOf(pale(color)));
    }

    /** A light, opaque version of {@code color} — the colour mixed 12% into white. */
    @ColorInt
    private static int pale(@ColorInt int color) {
        float f = 0.12f;
        return Color.rgb(
                Math.round(255 * (1 - f) + Color.red(color) * f),
                Math.round(255 * (1 - f) + Color.green(color) * f),
                Math.round(255 * (1 - f) + Color.blue(color) * f));
    }

    /** Brand colours come from the API, so a malformed one must not crash the screen. */
    @ColorInt
    private static int safeColor(String hex, String fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(fallback);
        }
    }

    private static String points(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private View divider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(this, R.color.border_light));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
