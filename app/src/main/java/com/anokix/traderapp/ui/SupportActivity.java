package com.anokix.traderapp.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.DistributorListData;
import com.anokix.traderapp.network.dto.SupportArticlesData;
import com.anokix.traderapp.network.dto.SupportOverviewData;
import com.anokix.traderapp.network.dto.SupportTicketData;
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
 * Support Centre — GET api/common/support/overview, which returns the channels this
 * merchant may use, the help-article taxonomy and their own tickets.
 *
 * From here they can search help articles, open WhatsApp, ask for a call back or write
 * a ticket; tapping a ticket opens {@link SupportTicketActivity} for the conversation.
 * The System Health and Knowledge Base blocks the web portal shows are deliberately
 * left out — they are not wired to anything yet.
 */
public class SupportActivity extends AppCompatActivity {

    /** Priority codes sent to the API, paired with the labels shown to the trader. */
    private static final String[] PRIORITY_KEYS = {"low", "normal", "high", "urgent"};
    private static final int[] PRIORITY_LABELS = {
            R.string.support_priority_low,
            R.string.support_priority_normal,
            R.string.support_priority_high,
            R.string.support_priority_urgent};
    private static final int DEFAULT_PRIORITY = 1;   // "normal"

    private SwipeRefreshLayout swipeRefresh;
    private View loading;
    private LinearLayout channelList, articleList, ticketList;
    private TextView articlesTitle, articlesEmpty, ticketsEmpty, supportHours;
    private EditText searchInput;

    private SupportOverviewData overview;
    private final List<SupportOverviewData.Article> articles = new ArrayList<>();
    /**
     * The trader's distributors, for the "Who should answer this?" picker. Loaded
     * alongside the overview so the Create Ticket sheet opens with the list ready.
     */
    private final List<DistributorListData.Distributor> distributors = new ArrayList<>();
    /** Non-null while showing search results rather than the popular topics. */
    private String activeQuery;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        loading = findViewById(R.id.loading);
        channelList = findViewById(R.id.channelList);
        articleList = findViewById(R.id.articleList);
        ticketList = findViewById(R.id.ticketList);
        articlesTitle = findViewById(R.id.articlesTitle);
        articlesEmpty = findViewById(R.id.articlesEmpty);
        ticketsEmpty = findViewById(R.id.ticketsEmpty);
        supportHours = findViewById(R.id.supportHours);
        searchInput = findViewById(R.id.searchInput);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        findViewById(R.id.btnSearch).setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnNewTicket).setOnClickListener(v -> showCreateTicketSheet());

        load(true);
        loadDistributors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from a ticket the status or last reply has usually moved on.
        if (overview != null) load(false);
    }

    // ---- Data ------------------------------------------------------------

    private void load(boolean showSpinner) {
        if (showSpinner) loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getSupportOverview(new ApiCallback<SupportOverviewData>() {
            @Override
            public void onSuccess(SupportOverviewData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                overview = data;
                bindChannels();
                bindTickets();
                if (activeQuery == null) bindPopularArticles();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(SupportActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * The trader's distributors, so a ticket can be raised with one of them rather than
     * with anokiX. A failure here is silent — the picker simply offers anokiX Support
     * only, which is the default anyway.
     */
    private void loadDistributors() {
        ApiClient.get(this).getDistributors(new ApiCallback<DistributorListData>() {
            @Override
            public void onSuccess(DistributorListData data) {
                distributors.clear();
                if (data != null && data.distributors != null) distributors.addAll(data.distributors);
            }

            @Override
            public void onError(String message) {
                // Leave the list empty; the sheet falls back to anokiX Support only.
            }
        });
    }

    // ---- Channels --------------------------------------------------------

    private void bindChannels() {
        channelList.removeAllViews();
        SupportOverviewData.Channels c = overview == null ? null : overview.channels;
        if (c == null) {
            addChannelPlaceholder();
            return;
        }

        boolean any = false;
        if (c.whatsappEnabled()) {
            String number = c.whatsapp.display;
            addChannel(R.drawable.ic_whatsapp, R.color.success, R.color.success_bg,
                    getString(R.string.support_channel_whatsapp),
                    TextUtils.isEmpty(number) ? getString(R.string.support_channel_whatsapp_sub) : number,
                    v -> openWhatsApp(c.whatsapp));
            any = true;
        }
        if (c.callbackEnabled()) {
            addChannel(R.drawable.ic_phone, R.color.info, R.color.info_bg,
                    getString(R.string.support_channel_callback),
                    getString(R.string.support_channel_callback_sub),
                    v -> showCallbackSheet());
            any = true;
        }
        if (c.ticketsEnabled()) {
            addChannel(R.drawable.ic_document, R.color.purple_primary, R.color.purple_light,
                    getString(R.string.support_channel_ticket),
                    getString(R.string.support_channel_ticket_sub),
                    v -> showCreateTicketSheet());
            any = true;
        }
        if (c.phoneEnabled()) {
            addChannel(R.drawable.ic_headset, R.color.warning, R.color.warning_bg,
                    getString(R.string.support_channel_phone), c.phone.number,
                    v -> dial(c.phone.number));
            any = true;
        }
        if (c.emailEnabled()) {
            addChannel(R.drawable.ic_email, R.color.text_secondary, R.color.border_light,
                    getString(R.string.support_channel_email), c.email.address,
                    v -> email(c.email.address));
            any = true;
        }
        if (!any) addChannelPlaceholder();

        boolean hasHours = c.hours != null && !c.hours.trim().isEmpty();
        supportHours.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        if (hasHours) supportHours.setText(c.hours);
    }

    private void addChannel(@DrawableRes int icon, int tintColor, int tileColor,
                            String title, String subtitle, View.OnClickListener onClick) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_support_channel, channelList, false);

        FrameLayout tile = card.findViewById(R.id.channelIconTile);
        tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, tileColor)));

        ImageView iconView = card.findViewById(R.id.channelIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, tintColor)));

        ((TextView) card.findViewById(R.id.channelTitle)).setText(title);

        TextView sub = card.findViewById(R.id.channelSubtitle);
        boolean hasSub = subtitle != null && !subtitle.isEmpty();
        sub.setVisibility(hasSub ? View.VISIBLE : View.GONE);
        if (hasSub) sub.setText(subtitle);

        card.setOnClickListener(onClick);
        channelList.addView(card);
    }

    private void addChannelPlaceholder() {
        TextView t = new TextView(this);
        t.setText(R.string.support_no_channels);
        t.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        t.setTextSize(12);
        t.setBackgroundResource(R.drawable.bg_card_white);
        int pad = dp(16);
        t.setPadding(pad, dp(24), pad, dp(24));
        t.setGravity(android.view.Gravity.CENTER);
        channelList.addView(t);
    }

    // ---- Help articles ---------------------------------------------------

    private void runSearch() {
        String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            activeQuery = null;
            bindPopularArticles();
            return;
        }
        ApiClient.get(this).searchSupportArticles(q, new ApiCallback<SupportArticlesData>() {
            @Override
            public void onSuccess(SupportArticlesData data) {
                activeQuery = q;
                articles.clear();
                if (data != null && data.articles != null) articles.addAll(data.articles);
                renderArticles(getString(R.string.support_no_articles_found, q));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SupportActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindPopularArticles() {
        articles.clear();
        if (overview != null && overview.popular != null) articles.addAll(overview.popular);
        renderArticles(getString(R.string.support_articles_empty));
    }

    private void renderArticles(String emptyMessage) {
        articlesTitle.setText(activeQuery == null
                ? getString(R.string.support_popular_topics)
                : getString(R.string.support_search));

        articleList.removeAllViews();
        for (SupportOverviewData.Article a : articles) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_support_article, articleList, false);
            ((TextView) row.findViewById(R.id.articleTitle)).setText(safe(a.title));
            TextView excerpt = row.findViewById(R.id.articleExcerpt);
            boolean hasExcerpt = a.excerpt != null && !a.excerpt.isEmpty();
            excerpt.setVisibility(hasExcerpt ? View.VISIBLE : View.GONE);
            if (hasExcerpt) excerpt.setText(a.excerpt);
            articleList.addView(row);
        }

        boolean empty = articles.isEmpty();
        articlesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) articlesEmpty.setText(emptyMessage);
    }

    // ---- Tickets ---------------------------------------------------------

    private void bindTickets() {
        ticketList.removeAllViews();
        List<SupportOverviewData.Ticket> tickets =
                overview == null || overview.tickets == null ? new ArrayList<>() : overview.tickets;

        for (SupportOverviewData.Ticket t : tickets) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_support_ticket, ticketList, false);
            ((TextView) row.findViewById(R.id.ticketNumber)).setText(safe(t.ticket_number));
            ((TextView) row.findViewById(R.id.ticketSubject)).setText(safe(t.subject));
            ((TextView) row.findViewById(R.id.ticketMeta)).setText(t.waitingLabel());
            ((TextView) row.findViewById(R.id.ticketDate)).setText(shortDateTime(t.created_at));
            bindTicketStatus(row.findViewById(R.id.ticketStatus), t.is_open);
            row.setOnClickListener(v -> SupportTicketActivity.open(this, t.id));
            ticketList.addView(row);
        }

        ticketsEmpty.setVisibility(tickets.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Shared with {@link SupportTicketActivity} so both screens badge a ticket alike. */
    static void bindTicketStatus(TextView badge, boolean open) {
        badge.setText(open ? R.string.support_status_open : R.string.support_status_closed);
        badge.setBackgroundResource(open ? R.drawable.bg_badge_danger : R.drawable.bg_badge_success);
        badge.setTextColor(ContextCompat.getColor(badge.getContext(),
                open ? R.color.danger : R.color.success));
    }

    // ---- Call back sheet -------------------------------------------------

    private void showCallbackSheet() {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_support_callback, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        EditText phone = sheet.findViewById(R.id.inputPhone);
        EditText subject = sheet.findViewById(R.id.inputSubject);
        EditText message = sheet.findViewById(R.id.inputMessage);
        MaterialButton submit = sheet.findViewById(R.id.btnSubmit);

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        submit.setOnClickListener(v -> {
            String number = phone.getText().toString().trim();
            if (number.isEmpty()) {
                phone.setError(getString(R.string.support_callback_number_required));
                phone.requestFocus();
                return;
            }
            // Both fields are optional on the form, but the API wants a subject and a
            // body — fall back to a line that says what was asked for.
            String subjectText = subject.getText().toString().trim();
            if (subjectText.isEmpty()) subjectText = getString(R.string.support_callback_default_subject);
            String messageText = message.getText().toString().trim();
            if (messageText.isEmpty()) {
                messageText = getString(R.string.support_callback_default_message, number);
            }

            // A call-back always goes to anokiX Support — there is no audience picker
            // on that form, so no distributor is attached.
            submitTicket(submit, dialog, subjectText, messageText, "callback",
                    null, number, null, null);
        });

        expand(dialog, sheet);
    }

    // ---- Create ticket sheet ---------------------------------------------

    private void showCreateTicketSheet() {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_support_ticket, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        EditText subject = sheet.findViewById(R.id.inputSubject);
        EditText message = sheet.findViewById(R.id.inputMessage);
        TextView priorityLabel = sheet.findViewById(R.id.priorityLabel);
        TextView topicLabel = sheet.findViewById(R.id.topicLabel);
        TextView audienceLabel = sheet.findViewById(R.id.audienceLabel);
        MaterialButton submit = sheet.findViewById(R.id.btnSubmit);

        // Who should answer this? Index -1 is anokiX Support (no distributor_id sent);
        // anything else points at the trader's distributor at that position.
        final int[] audience = {-1};
        sheet.findViewById(R.id.audiencePicker).setOnClickListener(v -> {
            CharSequence[] labels = new CharSequence[distributors.size() + 1];
            labels[0] = getString(R.string.support_ticket_audience_anokix);
            for (int i = 0; i < distributors.size(); i++) {
                labels[i + 1] = distributors.get(i).displayName();
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.support_ticket_audience)
                    .setSingleChoiceItems(labels, audience[0] + 1, (d, which) -> {
                        audience[0] = which - 1;
                        audienceLabel.setText(labels[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        final int[] priority = {DEFAULT_PRIORITY};
        priorityLabel.setText(PRIORITY_LABELS[priority[0]]);
        sheet.findViewById(R.id.priorityPicker).setOnClickListener(v -> {
            CharSequence[] labels = new CharSequence[PRIORITY_LABELS.length];
            for (int i = 0; i < PRIORITY_LABELS.length; i++) labels[i] = getString(PRIORITY_LABELS[i]);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.support_ticket_priority)
                    .setSingleChoiceItems(labels, priority[0], (d, which) -> {
                        priority[0] = which;
                        priorityLabel.setText(PRIORITY_LABELS[which]);
                        d.dismiss();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        // Topics only exist once the knowledge base is populated; hide the row until then.
        final List<SupportOverviewData.Category> topics =
                overview == null || overview.categories == null
                        ? new ArrayList<>() : overview.categories;
        final int[] topic = {-1};
        if (!topics.isEmpty()) {
            sheet.findViewById(R.id.topicBlock).setVisibility(View.VISIBLE);
            sheet.findViewById(R.id.topicPicker).setOnClickListener(v -> {
                CharSequence[] labels = new CharSequence[topics.size()];
                for (int i = 0; i < topics.size(); i++) labels[i] = safe(topics.get(i).name);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.support_ticket_topic)
                        .setSingleChoiceItems(labels, topic[0], (d, which) -> {
                            topic[0] = which;
                            topicLabel.setText(safe(topics.get(which).name));
                            topicLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                            d.dismiss();
                        })
                        .setNegativeButton(R.string.cancel_btn, null)
                        .show();
            });
        }

        sheet.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        submit.setOnClickListener(v -> {
            String subjectText = subject.getText().toString().trim();
            if (subjectText.isEmpty()) {
                subject.setError(getString(R.string.support_ticket_subject_required));
                subject.requestFocus();
                return;
            }
            String messageText = message.getText().toString().trim();
            if (messageText.isEmpty()) {
                message.setError(getString(R.string.support_ticket_message_required));
                message.requestFocus();
                return;
            }
            String category = topic[0] < 0 ? null : topics.get(topic[0]).key;
            String distributorId = audience[0] < 0
                    ? null : String.valueOf(distributors.get(audience[0]).id);
            submitTicket(submit, dialog, subjectText, messageText, "web",
                    PRIORITY_KEYS[priority[0]], null, category, distributorId);
        });

        expand(dialog, sheet);
    }

    /** Shared POST for both sheets — the only difference is the channel and its extras. */
    private void submitTicket(MaterialButton submit, BottomSheetDialog dialog,
                              String subject, String message, String channel,
                              @Nullable String priority, @Nullable String callbackPhone,
                              @Nullable String category, @Nullable String distributorId) {
        CharSequence original = submit.getText();
        submit.setEnabled(false);
        submit.setText(R.string.support_ticket_sending);

        ApiClient.get(this).createSupportTicket(subject, message, channel, priority,
                callbackPhone, category, distributorId, new ApiCallback<SupportTicketData>() {
                    @Override
                    public void onSuccess(SupportTicketData data) {
                        onSuccess(data, null);
                    }

                    @Override
                    public void onSuccess(SupportTicketData data, String serverMessage) {
                        dialog.dismiss();
                        Toast.makeText(SupportActivity.this,
                                serverMessage == null || serverMessage.isEmpty()
                                        ? getString(R.string.support_request_logged) : serverMessage,
                                Toast.LENGTH_LONG).show();
                        // The new ticket has to show up in the list and the counts.
                        load(false);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        submit.setEnabled(true);
                        submit.setText(original);
                        Toast.makeText(SupportActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
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

    // ---- External apps ---------------------------------------------------

    /**
     * Open the chat directly in WhatsApp. The server sends a ready-made wa.me link with
     * the greeting pre-filled; we aim it at the WhatsApp package first so the app opens
     * instead of the browser, and fall back to the plain link if it is not installed.
     */
    private void openWhatsApp(SupportOverviewData.WhatsApp whatsapp) {
        String link = whatsapp == null ? null : whatsapp.link;
        if (link == null || link.isEmpty()) {
            String msisdn = whatsapp == null ? null : whatsapp.msisdn();
            if (msisdn == null) {
                Toast.makeText(this, R.string.support_no_whatsapp, Toast.LENGTH_SHORT).show();
                return;
            }
            link = "https://wa.me/" + msisdn;
        }

        Uri uri = Uri.parse(link);
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg));
                return;
            } catch (ActivityNotFoundException ignored) {
                // try the next package, then the browser
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.support_no_whatsapp, Toast.LENGTH_SHORT).show();
        }
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException ignored) {
            // no dialler on this device
        }
    }

    private void email(String address) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address))
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_centre));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.support_channel_email)));
        } catch (ActivityNotFoundException ignored) {
            // no mail client on this device
        }
    }

    // ---- Helpers ---------------------------------------------------------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Ticket timestamps arrive as "dd/MM/yyyy HH:mm:ss" on the list and
     * "yyyy-MM-dd HH:mm:ss" on reply fields, so both shapes are tried.
     */
    static Date parseTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        for (String pattern : new String[]{"dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"}) {
            try {
                return new SimpleDateFormat(pattern, Locale.US).parse(raw);
            } catch (Exception ignored) {
                // try the next pattern
            }
        }
        return null;
    }

    /** "13/08/2026 20:33:41" → "13 Aug, 20:33". */
    static String shortDateTime(String raw) {
        Date d = parseTimestamp(raw);
        if (d == null) return raw == null ? "" : raw;
        return new SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(d);
    }
}
