package com.anokix.traderapp.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.anokix.traderapp.data.Cart;
import com.anokix.traderapp.model.CartLine;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.PosSaleData;
import com.anokix.traderapp.network.dto.PosTerminalsData;
import com.anokix.traderapp.ui.pos.CardMachineDialog;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * POS "Current Sale" / Complete Sale (Pagamio). Review the sale lines (qty steppers +
 * remove), apply a discount, pick a payment method, and complete the sale. VAT (15%) is
 * extracted from the inclusive total.
 *
 * <p>The four payment methods split into two flows:
 * <ul>
 *   <li><b>Cash, Wallet, QR Payment</b> — recorded straight away through
 *       {@code api/trader/pos/sale} with the matching {@code payment_method}; the
 *       receipt dialog mirrors the portal's Sale Recorded modal.</li>
 *   <li><b>Card</b> — the money is taken on a physical card machine, so nothing is
 *       recorded here. The trader's terminals are read from
 *       {@code api/common/pos/terminals}, they pick one, and the amount is pushed to it
 *       via {@code api/common/pos/payments} ({@link CardMachineDialog}). The backend
 *       records that sale when the gateway reports the card payment.</li>
 * </ul>
 */
public class CheckoutActivity extends AppCompatActivity {

    private static final String[] METHOD_KEYS = {"cash", "wallet", "card", "qr"};
    private static final String[] METHOD_LABELS = {"Cash", "Wallet", "Card", "QR Payment"};
    /** The one method that is taken on a terminal instead of being recorded here. */
    private static final String METHOD_CARD = "card";
    private static final double VAT_RATE = 0.15;

    private Cart cart;
    private CartAdapter adapter;
    private ApiClient api;

    private RecyclerView cartList;
    private TextView emptyView;
    private EditText discountInput;
    private TextView subtotalValue;
    private TextView vatValue;
    private TextView totalValue;
    private TextView paymentMethodValue;
    private MaterialButton chargeButton;

    private int methodIndex = 1; // default Wallet (matches portal)
    private boolean submitting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        cart = Cart.get();
        api = ApiClient.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cartList = findViewById(R.id.cartList);
        emptyView = findViewById(R.id.emptyView);
        discountInput = findViewById(R.id.discountInput);
        subtotalValue = findViewById(R.id.subtotalValue);
        vatValue = findViewById(R.id.vatValue);
        totalValue = findViewById(R.id.totalValue);
        paymentMethodValue = findViewById(R.id.paymentMethodValue);
        chargeButton = findViewById(R.id.chargeButton);

        cartList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter();
        cartList.setAdapter(adapter);

        paymentMethodValue.setText(METHOD_LABELS[methodIndex]);
        findViewById(R.id.paymentMethodSelector).setOnClickListener(v -> choosePaymentMethod());

        discountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refreshTotals(); }
        });

        chargeButton.setOnClickListener(v -> completeSale());

        refresh();
    }

    private double discount() {
        try {
            String raw = discountInput.getText().toString().trim();
            return raw.isEmpty() ? 0 : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void refresh() {
        boolean empty = cart.isEmpty();
        cartList.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
        refreshTotals();
    }

    /** What the customer actually pays: VAT-inclusive lines less the discount. */
    private double payableTotal() {
        double inclusive = cart.subtotal();
        return Math.max(0, inclusive - Math.min(discount(), inclusive));
    }

    private void refreshTotals() {
        double total = payableTotal();
        double vat = total * VAT_RATE / (1 + VAT_RATE); // VAT = total × 15/115
        double subtotalExcl = total - vat;

        subtotalValue.setText(money(subtotalExcl));
        vatValue.setText(money(vat));
        totalValue.setText(money(total));

        boolean canSell = !cart.isEmpty() && !submitting;
        chargeButton.setEnabled(canSell);
        chargeButton.setText(cart.isEmpty()
                ? getString(R.string.complete_sale)
                : getString(R.string.complete_sale) + "  ·  " + money(total));
    }

    private void choosePaymentMethod() {
        final int[] selected = {methodIndex};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_payment_method)
                .setSingleChoiceItems(METHOD_LABELS, methodIndex, (d, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    methodIndex = selected[0];
                    paymentMethodValue.setText(METHOD_LABELS[methodIndex]);
                })
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void completeSale() {
        if (cart.isEmpty() || submitting) {
            return;
        }
        if (METHOD_CARD.equals(METHOD_KEYS[methodIndex])) {
            startCardMachinePayment();
            return;
        }
        recordSale();
    }

    // ---- Card: pay on a card machine -------------------------------------

    /**
     * Load the trader's card machines, then offer them. The list is fetched per sale
     * rather than cached, so a machine switched on (or off) since the last sale is
     * reflected without the cashier having to restart anything.
     */
    private void startCardMachinePayment() {
        submitting = true;
        refreshTotals();
        chargeButton.setText(R.string.processing);

        api.getPosTerminals(new ApiCallback<PosTerminalsData>() {
            @Override
            public void onSuccess(PosTerminalsData data) {
                submitting = false;
                refreshTotals();
                if (isFinishing() || isDestroyed()) return;

                List<PosTerminalsData.Terminal> machines =
                        data == null ? null : data.activeTerminals();
                if (machines == null || machines.isEmpty()) {
                    // "None set up" and "all switched off" are different problems, and
                    // only the trader can tell them apart from the wording.
                    boolean configured = data != null && data.configured;
                    new AlertDialog.Builder(CheckoutActivity.this)
                            .setTitle(R.string.card_machine_title)
                            .setMessage(configured
                                    ? R.string.card_machine_none_active
                                    : R.string.card_machine_none)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                showCardMachineDialog(machines);
            }

            @Override
            public void onError(String message) {
                submitting = false;
                refreshTotals();
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(CheckoutActivity.this,
                        message == null || message.isEmpty()
                                ? getString(R.string.card_machine_load_error) : message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCardMachineDialog(List<PosTerminalsData.Terminal> machines) {
        double total = payableTotal();
        CardMachineDialog.show(this, api, total, money(total), saleDescription(), machines,
                terminalName -> {
                    // The amount is on the machine and the backend owns the rest of that
                    // sale, so the till is finished with it — clear and step back.
                    cart.clear();
                    finish();
                });
    }

    /** What the payment is for, worded as the portal does it: "Sale · 3 items". */
    private String saleDescription() {
        int count = cart.itemCount();
        return "Sale · " + count + (count == 1 ? " item" : " items");
    }

    // ---- Cash / Wallet / QR: record the sale ------------------------------

    private void recordSale() {
        JSONArray items = new JSONArray();
        try {
            for (CartLine line : cart.lines()) {
                JSONObject o = new JSONObject();
                o.put("product_id", line.productId);
                o.put("quantity", line.qty);
                o.put("unit_price", line.unitPrice);
                items.put(o);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.generic_error, Toast.LENGTH_SHORT).show();
            return;
        }

        submitting = true;
        refreshTotals();
        chargeButton.setText(R.string.processing);

        api.createPosSale(items.toString(), METHOD_KEYS[methodIndex], discount(),
                new ApiCallback<PosSaleData>() {
                    @Override
                    public void onSuccess(PosSaleData data) {
                        submitting = false;
                        if (data != null && data.sale != null) {
                            showReceipt(data.sale);
                        } else {
                            refreshTotals();
                            Toast.makeText(CheckoutActivity.this,
                                    R.string.generic_error, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        submitting = false;
                        refreshTotals();
                        Toast.makeText(CheckoutActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showReceipt(PosSaleData.Sale sale) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_pos_receipt, null);
        ((TextView) content.findViewById(R.id.receiptSaleNumber)).setText(sale.saleNumber);

        LinearLayout itemsContainer = content.findViewById(R.id.receiptItems);
        if (sale.items != null) {
            for (PosSaleData.Item item : sale.items) {
                addRow(itemsContainer, item.quantity + " × " + item.name, money(item.lineTotal), false);
            }
        }

        LinearLayout totals = content.findViewById(R.id.receiptTotals);
        if (sale.discount > 0) {
            addRow(totals, getString(R.string.discount), "-" + money(sale.discount), false);
        }
        addRow(totals, getString(R.string.subtotal_excl_vat), money(sale.subtotalExclTax), false);
        addRow(totals, getString(R.string.vat_15), money(sale.taxAmount), false);
        String methodLabel = labelForKey(sale.paymentMethod);
        addRow(totals, getString(R.string.total_with_method, methodLabel), money(sale.totalAmount), true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(false)
                .create();

        Runnable done = () -> {
            cart.clear();
            dialog.dismiss();
            finish();
        };
        content.findViewById(R.id.receiptDone).setOnClickListener(v -> done.run());
        content.findViewById(R.id.receiptClose).setOnClickListener(v -> done.run());
        dialog.show();
    }

    private void addRow(LinearLayout parent, String label, String value, boolean emphasised) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_receipt, parent, false);
        TextView l = row.findViewById(R.id.receiptLabel);
        TextView v = row.findViewById(R.id.receiptValue);
        l.setText(label);
        v.setText(value);
        if (emphasised) {
            l.setTypeface(Typeface.DEFAULT_BOLD);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            int purple = ContextCompat.getColor(this, R.color.purple_primary);
            l.setTextColor(purple);
            v.setTextColor(purple);
            l.setTextSize(16);
            v.setTextSize(16);
        }
        parent.addView(row);
    }

    private String labelForKey(String key) {
        if (key != null) {
            for (int i = 0; i < METHOD_KEYS.length; i++) {
                if (METHOD_KEYS[i].equalsIgnoreCase(key)) {
                    return METHOD_LABELS[i];
                }
            }
        }
        return key == null ? "" : key;
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    // ---- Cart line adapter -----------------------------------------------

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cart_line, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CartLine line = cart.lines().get(position);
            holder.name.setText(line.name);
            holder.unit.setText(money(line.unitPrice) + " each");
            holder.qty.setText(String.valueOf(line.qty));
            holder.total.setText(money(line.lineTotal()));
            holder.minus.setOnClickListener(v -> {
                cart.decrement(line.productId);
                refresh();
            });
            holder.plus.setOnClickListener(v -> {
                cart.increment(line.productId);
                refresh();
            });
            holder.remove.setOnClickListener(v -> {
                cart.remove(line.productId);
                refresh();
            });
        }

        @Override
        public int getItemCount() {
            return cart.lines().size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView unit;
            final TextView qty;
            final TextView total;
            final View minus;
            final View plus;
            final View remove;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.lineName);
                unit = itemView.findViewById(R.id.lineUnit);
                qty = itemView.findViewById(R.id.lineQty);
                total = itemView.findViewById(R.id.lineTotal);
                minus = itemView.findViewById(R.id.btnMinus);
                plus = itemView.findViewById(R.id.btnPlus);
                remove = itemView.findViewById(R.id.btnRemove);
            }
        }
    }
}
