package com.anokix.trader.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.SupportTicket;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/** Support Centre — mirrors the Trader Portal /support page (verbatim data). */
public class SupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.atlasAsk).setOnClickListener(v ->
                Toast.makeText(this, "Atlas AI is thinking…", Toast.LENGTH_SHORT).show());
        findViewById(R.id.runHealthCheck).setOnClickListener(v ->
                info("Health check", "All systems operational.\n\nPOS, scanner, printer, camera, card reader and internet are all healthy."));
        findViewById(R.id.createTicket).setOnClickListener(v -> logTicket());

        buildContacts();
        buildHealth();
        buildKnowledgeBase();
        buildTopics();
        buildTickets();
    }

    private void buildContacts() {
        LinearLayout row = findViewById(R.id.contactRow);
        String[] titles = {"Live Chat", "WhatsApp", "Call Us", "Email Us", "Submit Ticket"};
        String[] avail = {"Available now", "Available now", "Mon–Fri, 8am–6pm", "24/7", "24/7"};
        String[] detail = {"Avg. response: 2 mins", "+27 80 123 4567", "0800 ANOKIX (266549)", "support@anokix.com", "Track your requests"};
        String[] action = {"Start Chat", "Chat Now", "Call Now", "Send Email", "Create Ticket"};
        int[] icons = {R.drawable.ic_chat, R.drawable.ic_chat, R.drawable.ic_headset, R.drawable.ic_email, R.drawable.ic_document};
        String[] colors = {"#7c3aed", "#16a34a", "#2563eb", "#ea580c", "#6366f1"};

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < titles.length; i++) {
            View card = inflater.inflate(R.layout.item_support_contact, row, false);
            ImageView icon = card.findViewById(R.id.contactIcon);
            icon.setImageResource(icons[i]);
            tint((View) icon.getParent(), colors[i]);
            ((TextView) card.findViewById(R.id.contactTitle)).setText(titles[i]);
            ((TextView) card.findViewById(R.id.contactAvailability)).setText(avail[i]);
            ((TextView) card.findViewById(R.id.contactDetail)).setText(detail[i]);
            MaterialButton btn = card.findViewById(R.id.contactAction);
            btn.setText(action[i]);
            final int idx = i;
            btn.setOnClickListener(v -> onContact(idx));
            row.addView(card);
        }
    }

    private void onContact(int idx) {
        switch (idx) {
            case 2: // Call Us
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:0800266549")));
                break;
            case 3: // Email
                Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@anokix.com"));
                startActivity(Intent.createChooser(email, "Send Email"));
                break;
            case 4: // Ticket
                logTicket();
                break;
            default:
                Toast.makeText(this, "Connecting you to support…", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void buildHealth() {
        LinearLayout list = findViewById(R.id.healthList);
        String[] labels = {"POS Status", "Scanner", "Printer", "Camera", "Card Reader", "Internet"};
        String[] values = {"Online", "Connected", "Connected", "Good", "Connected", "Good"};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < labels.length; i++) {
            View r = inflater.inflate(R.layout.item_health_row, list, false);
            ((TextView) r.findViewById(R.id.healthLabel)).setText(labels[i]);
            TextView tag = r.findViewById(R.id.healthStatus);
            tag.setText(values[i]);
            tint(tag, "#16A34A");
            list.addView(r);
        }
    }

    private void buildKnowledgeBase() {
        LinearLayout grid = findViewById(R.id.kbGrid);
        String[] titles = {"Marketplace Orders", "Wallet & Payments", "Rewards & Cashback",
                "POS Device Support", "Reports & Analytics", "Security & Login"};
        String[] descs = {
                "Track, cancel or manage distributor orders",
                "Settlements, transfers and account issues",
                "Points, redemptions and promotions",
                "Hardware, sync and software updates",
                "Sales data, exports and insights",
                "Password, PIN and account access"};
        int[] articles = {24, 18, 15, 12, 10, 9};
        String[] icons = {"🛒", "💰", "⭐", "🖥️", "📊", "🔒"};
        String[] colors = {"#6366f1", "#2563eb", "#16a34a", "#7c3aed", "#ea580c", "#dc2626"};

        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout rowView = null;
        for (int i = 0; i < titles.length; i++) {
            if (i % 2 == 0) {
                rowView = new LinearLayout(this);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(10);
                grid.addView(rowView, lp);
            }
            View card = inflater.inflate(R.layout.item_report_type_card, rowView, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginStart(i % 2 == 1 ? dp(5) : 0);
            clp.setMarginEnd(i % 2 == 0 ? dp(5) : 0);
            card.setLayoutParams(clp);

            TextView icon = card.findViewById(R.id.typeIcon);
            icon.setText(icons[i]);
            tint(icon, colors[i]);
            ((TextView) card.findViewById(R.id.typeTitle)).setText(titles[i]);
            ((TextView) card.findViewById(R.id.typeDesc)).setText(descs[i] + "\n" + articles[i] + " articles");
            final String title = titles[i];
            card.setOnClickListener(v -> Toast.makeText(this, "Opening " + title, Toast.LENGTH_SHORT).show());
            rowView.addView(card);
        }
    }

    private void buildTopics() {
        LinearLayout column = findViewById(R.id.topicsColumn);
        String[] topics = {
                "How to place an order on Marketplace",
                "How do I redeem Limes points?",
                "Redeeming Limes points for airtime",
                "Managing user roles and access"};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String t : topics) {
            View r = inflater.inflate(R.layout.item_support_topic, column, false);
            ((TextView) r.findViewById(R.id.topicTitle)).setText(t);
            r.setOnClickListener(v -> Toast.makeText(this, t, Toast.LENGTH_SHORT).show());
            column.addView(r);
        }
    }

    private void buildTickets() {
        LinearLayout column = findViewById(R.id.ticketsColumn);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SupportTicket t : MockData.getSupportTickets()) {
            View r = inflater.inflate(R.layout.item_support_ticket, column, false);
            ((TextView) r.findViewById(R.id.ticketTitle)).setText(t.title);
            ((TextView) r.findViewById(R.id.ticketDesc)).setText(t.description);
            ((TextView) r.findViewById(R.id.ticketMeta)).setText(t.id + " · Updated " + t.updated);
            TextView status = r.findViewById(R.id.ticketStatus);
            switch (t.status) {
                case "open":        status.setText("Open");        tint(status, "#f59e0b"); break;
                case "in_progress": status.setText("In Progress"); tint(status, "#2563eb"); break;
                default:            status.setText("Resolved");    tint(status, "#16a34a"); break;
            }
            column.addView(r);
        }
    }

    private void logTicket() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Describe your issue");
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Create Ticket")
                .setView(input)
                .setPositiveButton("Submit", (d, w) ->
                        info("Ticket created", "Reference: #" + (14500 + (int) (Math.random() * 99))
                                + "\n\nOur team will respond within 24 hours."))
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void info(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.promo_done, null)
                .show();
    }

    private static void tint(View v, String hex) {
        v.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
