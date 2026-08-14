package com.anokix.traderapp.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.SupportOverviewData;
import com.anokix.traderapp.network.dto.SupportTicketData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One support ticket — GET api/common/support/tickets/{id}. Shows the conversation as
 * a chat thread and, while the ticket is open, lets the trader reply
 * (POST .../tickets/reply) or close it (POST .../tickets/close). Every one of those
 * endpoints answers with the refreshed ticket, so the screen simply rebinds the result.
 */
public class SupportTicketActivity extends AppCompatActivity {

    private static final String EXTRA_TICKET_ID = "ticket_id";

    static void open(Context context, int ticketId) {
        context.startActivity(new Intent(context, SupportTicketActivity.class)
                .putExtra(EXTRA_TICKET_ID, ticketId));
    }

    private final List<SupportOverviewData.Message> messages = new ArrayList<>();
    private MessageAdapter adapter;

    private int ticketId;
    private SupportOverviewData.Ticket ticket;

    private MaterialToolbar toolbar;
    private RecyclerView list;
    private View loading, composer, closedBanner;
    private TextView subjectView, statusView, priorityView, openedView;
    private EditText replyInput;
    private MaterialButton btnSendReply, btnCloseTicket;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_ticket);

        ticketId = getIntent().getIntExtra(EXTRA_TICKET_ID, 0);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        subjectView = findViewById(R.id.ticketSubject);
        statusView = findViewById(R.id.ticketStatus);
        priorityView = findViewById(R.id.ticketPriority);
        openedView = findViewById(R.id.ticketOpened);
        loading = findViewById(R.id.loading);
        composer = findViewById(R.id.composer);
        closedBanner = findViewById(R.id.closedBanner);
        replyInput = findViewById(R.id.inputReply);
        btnSendReply = findViewById(R.id.btnSendReply);
        btnCloseTicket = findViewById(R.id.btnCloseTicket);

        list = findViewById(R.id.messageList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        list.setAdapter(adapter);

        btnSendReply.setOnClickListener(v -> sendReply());
        btnCloseTicket.setOnClickListener(v -> confirmClose());

        load();
    }

    // ---- Data ------------------------------------------------------------

    private void load() {
        loading.setVisibility(View.VISIBLE);
        ApiClient.get(this).getSupportTicket(ticketId, new ApiCallback<SupportTicketData>() {
            @Override
            public void onSuccess(SupportTicketData data) {
                loading.setVisibility(View.GONE);
                bind(data);
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                Toast.makeText(SupportTicketActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bind(SupportTicketData data) {
        if (data == null || data.ticket == null) return;
        ticket = data.ticket;

        toolbar.setTitle(safe(ticket.ticket_number));
        subjectView.setText(safe(ticket.subject));
        SupportActivity.bindTicketStatus(statusView, ticket.is_open);

        // "Opened 13 Aug, 20:39", plus who it went to when it was raised with a
        // distributor rather than anokiX Support.
        String opened = getString(R.string.support_ticket_opened,
                SupportActivity.shortDateTime(ticket.created_at));
        if (ticket.distributor_name != null && !ticket.distributor_name.isEmpty()) {
            opened += " · " + getString(R.string.support_ticket_sent_to, ticket.distributor_name);
        }
        openedView.setText(opened);

        bindPriority();

        messages.clear();
        if (ticket.messages != null) messages.addAll(ticket.messages);
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) list.scrollToPosition(messages.size() - 1);

        // An open ticket gets the composer; a closed one gets the explanation instead.
        composer.setVisibility(ticket.is_open ? View.VISIBLE : View.GONE);
        closedBanner.setVisibility(ticket.is_open ? View.GONE : View.VISIBLE);
    }

    private void bindPriority() {
        String p = safe(ticket.priority).toLowerCase(Locale.US);
        // "normal" is the default and adds no information, so it is not badged.
        if (p.isEmpty() || p.equals("normal")) {
            priorityView.setVisibility(View.GONE);
            return;
        }
        int bg, fg;
        switch (p) {
            case "urgent":
                bg = R.drawable.bg_badge_danger;
                fg = R.color.danger;
                break;
            case "high":
                bg = R.drawable.bg_badge_warning;
                fg = R.color.warning;
                break;
            default:
                bg = R.drawable.bg_badge_muted;
                fg = R.color.text_secondary;
                break;
        }
        priorityView.setVisibility(View.VISIBLE);
        priorityView.setText(p.substring(0, 1).toUpperCase(Locale.US) + p.substring(1));
        priorityView.setBackgroundResource(bg);
        priorityView.setTextColor(ContextCompat.getColor(this, fg));
    }

    // ---- Actions ---------------------------------------------------------

    private void sendReply() {
        String message = replyInput.getText().toString().trim();
        if (message.isEmpty()) {
            replyInput.setError(getString(R.string.support_ticket_reply_required));
            replyInput.requestFocus();
            return;
        }
        setComposerBusy(true, R.string.support_ticket_sending);
        ApiClient.get(this).replySupportTicket(ticketId, message,
                new ApiCallback<SupportTicketData>() {
                    @Override
                    public void onSuccess(SupportTicketData data) {
                        setComposerBusy(false, R.string.support_ticket_send_reply);
                        replyInput.setText("");
                        bind(data);
                    }

                    @Override
                    public void onError(String error) {
                        setComposerBusy(false, R.string.support_ticket_send_reply);
                        Toast.makeText(SupportTicketActivity.this, error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmClose() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.support_ticket_close_confirm_title)
                .setMessage(R.string.support_ticket_close_confirm_body)
                .setPositiveButton(R.string.support_ticket_close, (d, w) -> closeTicket())
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void closeTicket() {
        setComposerBusy(true, R.string.support_ticket_sending);
        ApiClient.get(this).closeSupportTicket(ticketId, new ApiCallback<SupportTicketData>() {
            @Override
            public void onSuccess(SupportTicketData data) {
                onSuccess(data, null);
            }

            @Override
            public void onSuccess(SupportTicketData data, String message) {
                setComposerBusy(false, R.string.support_ticket_send_reply);
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(SupportTicketActivity.this, message, Toast.LENGTH_SHORT).show();
                }
                bind(data);
            }

            @Override
            public void onError(String error) {
                setComposerBusy(false, R.string.support_ticket_send_reply);
                Toast.makeText(SupportTicketActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setComposerBusy(boolean busy, int sendLabel) {
        btnSendReply.setEnabled(!busy);
        btnCloseTicket.setEnabled(!busy);
        btnSendReply.setText(sendLabel);
    }

    // ---- Thread ----------------------------------------------------------

    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ticket_message, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SupportOverviewData.Message m = messages.get(position);
            h.body.setText(safe(m.body));

            if (m.isSystem()) {
                // System notes ("Closed by the merchant.") sit centred, with no author
                // and no timestamp — they read as a divider, not a message.
                h.row.setGravity(Gravity.CENTER_HORIZONTAL);
                h.bubble.setBackgroundResource(R.drawable.bg_bubble_system);
                h.author.setVisibility(View.GONE);
                h.time.setVisibility(View.GONE);
                h.body.setTextColor(ContextCompat.getColor(SupportTicketActivity.this,
                        R.color.text_secondary));
                return;
            }

            boolean mine = m.isMine();
            h.row.setGravity(mine ? Gravity.END : Gravity.START);
            h.bubble.setBackgroundResource(mine
                    ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_theirs);
            h.body.setTextColor(ContextCompat.getColor(SupportTicketActivity.this,
                    R.color.text_primary));

            boolean hasAuthor = !mine && m.author_name != null && !m.author_name.isEmpty();
            h.author.setVisibility(hasAuthor ? View.VISIBLE : View.GONE);
            if (hasAuthor) h.author.setText(m.author_name);

            h.time.setVisibility(View.VISIBLE);
            h.time.setText(timeOnly(m.created_at));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final LinearLayout row, bubble;
            final TextView author, body, time;

            VH(@NonNull View v) {
                super(v);
                row = v.findViewById(R.id.messageRow);
                bubble = v.findViewById(R.id.bubble);
                author = v.findViewById(R.id.messageAuthor);
                body = v.findViewById(R.id.messageBody);
                time = v.findViewById(R.id.messageTime);
            }
        }
    }

    /** "13/08/2026 20:33:41" → "20:33". */
    private static String timeOnly(String raw) {
        Date d = SupportActivity.parseTimestamp(raw);
        if (d == null) return "";
        return new SimpleDateFormat("HH:mm", Locale.US).format(d);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
