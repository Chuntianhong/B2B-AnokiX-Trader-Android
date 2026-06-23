package com.anokix.trader.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.data.Cart;
import com.anokix.trader.model.CartLine;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

/**
 * POS checkout: review cart lines (with quantity steppers), see VAT-inclusive totals,
 * choose a payment method, and confirm the sale (mock receipt). Powered by Pagamio in
 * production.
 */
public class CheckoutActivity extends AppCompatActivity {

    /** Intent extra (String) selecting which cart to check out: {@link Cart#POS} or {@link Cart#ORDER}. */
    public static final String EXTRA_CART = "cart";

    private static final String[] PAYMENT_METHODS = {"Cash", "Card", "QR", "Wallet", "Split Payment"};

    private Cart cart;
    private boolean orderMode;
    private CartAdapter adapter;

    private RecyclerView cartList;
    private TextView emptyView;
    private TextView subtotalValue;
    private TextView vatValue;
    private TextView totalValue;
    private MaterialButton chargeButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        String cartKey = getIntent().getStringExtra(EXTRA_CART);
        cart = Cart.byKey(cartKey);
        orderMode = Cart.ORDER.equals(cartKey);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(orderMode ? "Place Order" : getString(R.string.checkout));
        toolbar.setNavigationOnClickListener(v -> finish());

        cartList = findViewById(R.id.cartList);
        emptyView = findViewById(R.id.emptyView);
        subtotalValue = findViewById(R.id.subtotalValue);
        vatValue = findViewById(R.id.vatValue);
        totalValue = findViewById(R.id.totalValue);
        chargeButton = findViewById(R.id.chargeButton);

        cartList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter();
        cartList.setAdapter(adapter);

        chargeButton.setOnClickListener(v -> choosePaymentMethod());

        refresh();
    }

    private void refresh() {
        List<CartLine> lines = cart.lines();
        boolean empty = lines.isEmpty();
        cartList.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        chargeButton.setEnabled(!empty);

        double total = cart.subtotal();          // prices are VAT-inclusive
        double subtotal = total / 1.15;
        double vat = total - subtotal;

        subtotalValue.setText(money(subtotal));
        vatValue.setText(money(vat));
        totalValue.setText(money(total));
        String action = orderMode ? "Place Order" : getString(R.string.pay);
        chargeButton.setText(empty ? action : action + "  ·  " + money(total));
        adapter.notifyDataSetChanged();
    }

    private void choosePaymentMethod() {
        if (cart.isEmpty()) {
            return;
        }
        final int[] selected = {0};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_payment_method)
                .setSingleChoiceItems(PAYMENT_METHODS, 0, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.pay, (d, w) -> completeSale(PAYMENT_METHODS[selected[0]]))
                .setNegativeButton(R.string.cancel_btn, null)
                .show();
    }

    private void completeSale(String method) {
        String total = money(cart.subtotal());
        int count = cart.itemCount();
        cart.clear();
        refresh();
        String title;
        String receipt;
        if (orderMode) {
            title = "Order placed";
            receipt = "Order submitted to distributor\n\n"
                    + count + " item(s)\n"
                    + "Total: " + total + "\n"
                    + "Payment: " + method + "\n\n"
                    + "You'll be notified when the distributor accepts your order.";
        } else {
            title = "Payment successful";
            receipt = "Sale complete\n\n"
                    + count + " item(s)\n"
                    + "Total: " + total + "\n"
                    + "Paid by: " + method + "\n\n"
                    + "Receipt sent. Inventory updated.";
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(receipt)
                .setPositiveButton(R.string.promo_done, (d, w) -> finish())
                .setCancelable(false)
                .show();
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

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.lineName);
                unit = itemView.findViewById(R.id.lineUnit);
                qty = itemView.findViewById(R.id.lineQty);
                total = itemView.findViewById(R.id.lineTotal);
                minus = itemView.findViewById(R.id.btnMinus);
                plus = itemView.findViewById(R.id.btnPlus);
            }
        }
    }
}
