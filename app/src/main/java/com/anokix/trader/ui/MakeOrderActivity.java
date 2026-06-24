package com.anokix.trader.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.anokix.trader.model.DeliverySlot;
import com.anokix.trader.model.MarketCart;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.LoginData;
import com.anokix.trader.session.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Make Order / Checkout (mirrors Design/MakeOrder). Takes the cart lines selected
 * on the Cart screen and lets the trader confirm the delivery address, pick a
 * delivery window, add notes and a payment method, then places the order via
 * {@code api/trader/orders/create}.
 *
 * Delivery slots are generated locally from the trader's
 * {@code portal_info.preferred_delivery_days} (see {@link DeliverySlot}); the
 * default delivery address comes from {@code portal_info.address}.
 */
public class MakeOrderActivity extends AppCompatActivity {

    public static final String EXTRA_CART_ITEM_IDS = "cart_item_ids";
    public static final String EXTRA_DISTRIBUTOR_ID = "distributor_id";
    public static final String EXTRA_SUBTOTAL = "subtotal";
    public static final String EXTRA_CURRENCY = "currency";

    /** Number of slots shown inline before "View all slots". */
    private static final int INLINE_SLOTS = 5;

    private final ApiClient api = ApiClient.get(this);
    private SessionManager session;

    private ArrayList<String> cartItemIds;
    private String distributorId;
    private double subtotal;
    private String currency = "R";

    private final List<DeliverySlot> slots = new ArrayList<>();
    private int selectedSlotIndex = -1;

    private String selectedPaymentKey;
    private String address = "";

    private LinearLayout slotsContainer, paymentContainer;
    private TextView addressText, noSlots;
    private EditText notesInput;
    private MaterialButton btnPlaceOrder;

    private static final int REQ_PICK_ADDRESS = 2002;
    private Double pickedLat, pickedLng;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_make_order);
        session = SessionManager.get(this);

        cartItemIds = getIntent().getStringArrayListExtra(EXTRA_CART_ITEM_IDS);
        if (cartItemIds == null) cartItemIds = new ArrayList<>();
        distributorId = getIntent().getStringExtra(EXTRA_DISTRIBUTOR_ID);
        subtotal = getIntent().getDoubleExtra(EXTRA_SUBTOTAL, 0);
        String cur = getIntent().getStringExtra(EXTRA_CURRENCY);
        if (cur != null && !cur.isEmpty()) currency = cur;

        slotsContainer = findViewById(R.id.slotsContainer);
        paymentContainer = findViewById(R.id.paymentContainer);
        addressText = findViewById(R.id.addressText);
        noSlots = findViewById(R.id.noSlots);
        notesInput = findViewById(R.id.notesInput);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChangeAddress).setOnClickListener(v -> openAddressPicker());
        findViewById(R.id.btnViewAllSlots).setOnClickListener(v -> showAllSlotsDialog());
        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        LoginData.PortalInfo info = session.getPortalInfo();
        address = info != null && info.address != null ? info.address : "";
        bindAddress();

        // Delivery slots come from the SELECTED DISTRIBUTOR's preferred_delivery_days
        // (marketplace response); fall back to the trader's own login value if absent.
        String deliveryDays = MarketCart.get().getDistributorDeliveryDays();
        if (deliveryDays == null || deliveryDays.trim().isEmpty()) {
            deliveryDays = info != null ? info.preferred_delivery_days : null;
        }
        slots.addAll(DeliverySlot.generate(deliveryDays));
        if (!slots.isEmpty()) selectedSlotIndex = 0;
        renderSlots();

        buildPaymentOptions();
        updateSummary();
    }

    // ---- Delivery address ------------------------------------------------

    private void bindAddress() {
        addressText.setText(address.isEmpty() ? getString(R.string.no_address_set) : address);
    }

    /** Open the map-based address picker (search + Google Map), prefilled with the current address. */
    private void openAddressPicker() {
        Intent i = new Intent(this, AddressPickerActivity.class);
        i.putExtra(AddressPickerActivity.EXTRA_ADDRESS, address);
        if (pickedLat != null && pickedLng != null) {
            i.putExtra(AddressPickerActivity.EXTRA_LAT, pickedLat);
            i.putExtra(AddressPickerActivity.EXTRA_LNG, pickedLng);
        }
        startActivityForResult(i, REQ_PICK_ADDRESS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ADDRESS && resultCode == RESULT_OK && data != null) {
            String picked = data.getStringExtra(AddressPickerActivity.EXTRA_ADDRESS);
            if (picked != null && !picked.trim().isEmpty()) {
                address = picked;
                bindAddress();
            }
            if (data.hasExtra(AddressPickerActivity.EXTRA_LAT)
                    && data.hasExtra(AddressPickerActivity.EXTRA_LNG)) {
                pickedLat = data.getDoubleExtra(AddressPickerActivity.EXTRA_LAT, 0);
                pickedLng = data.getDoubleExtra(AddressPickerActivity.EXTRA_LNG, 0);
            }
        }
    }

    // ---- Delivery window -------------------------------------------------

    private void renderSlots() {
        slotsContainer.removeAllViews();
        if (slots.isEmpty()) {
            noSlots.setVisibility(View.VISIBLE);
            findViewById(R.id.btnViewAllSlots).setVisibility(View.GONE);
            return;
        }
        noSlots.setVisibility(View.GONE);

        // Show the selected slot first, then fill up to INLINE_SLOTS with the earliest others.
        List<Integer> inline = new ArrayList<>();
        if (selectedSlotIndex >= 0) inline.add(selectedSlotIndex);
        for (int i = 0; i < slots.size() && inline.size() < INLINE_SLOTS; i++) {
            if (!inline.contains(i)) inline.add(i);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int idx : inline) {
            slotsContainer.addView(buildSlotRow(inflater, slotsContainer, idx));
        }
        findViewById(R.id.btnViewAllSlots).setVisibility(
                slots.size() > INLINE_SLOTS ? View.VISIBLE : View.GONE);
    }

    private View buildSlotRow(LayoutInflater inflater, ViewGroup parent, int index) {
        View row = inflater.inflate(R.layout.item_order_option, parent, false);
        TextView title = row.findViewById(R.id.optionTitle);
        title.setText(slots.get(index).label());
        applyOptionState(row, index == selectedSlotIndex);
        row.setOnClickListener(v -> {
            selectedSlotIndex = index;
            renderSlots();
        });
        return row;
    }

    private void showAllSlotsDialog() {
        if (slots.isEmpty()) return;
        ViewGroup view = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.dialog_address_search, null);
        // Reuse the simple list dialog shell: hide the search field + its label, show only the list.
        view.findViewById(R.id.addressSearchInput).setVisibility(View.GONE);
        ((TextView) view.getChildAt(0)).setText(R.string.all_delivery_slots);
        view.getChildAt(1).setVisibility(View.GONE);
        ListView list = view.findViewById(R.id.addressResults);
        list.getLayoutParams().height = getResources().getDisplayMetrics().heightPixels / 2;

        List<String> labels = new ArrayList<>();
        for (DeliverySlot s : slots) labels.add(s.label());
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .create();

        list.setOnItemClickListener((parent, v, position, id) -> {
            selectedSlotIndex = position;
            renderSlots();
            dialog.dismiss();
        });
        dialog.show();
    }

    // ---- Payment method --------------------------------------------------

    private void buildPaymentOptions() {
        String[][] options = {
                {"wallet", getString(R.string.pm_wallet), getString(R.string.pm_wallet_sub)},
                {"credit", getString(R.string.pm_credit), getString(R.string.pm_credit_sub)},
                {"card", getString(R.string.pm_card), getString(R.string.pm_card_sub)},
                {"bank", getString(R.string.pm_bank), getString(R.string.pm_bank_sub)},
        };
        selectedPaymentKey = options[0][0];

        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] opt : options) {
            View row = inflater.inflate(R.layout.item_order_option, paymentContainer, false);
            ((TextView) row.findViewById(R.id.optionTitle)).setText(opt[1]);
            TextView sub = row.findViewById(R.id.optionSubtitle);
            sub.setText(opt[2]);
            sub.setVisibility(View.VISIBLE);
            applyOptionState(row, opt[0].equals(selectedPaymentKey));
            row.setOnClickListener(v -> {
                selectedPaymentKey = opt[0];
                refreshPaymentStates();
            });
            row.setTag(opt[0]);
            paymentContainer.addView(row);
        }
    }

    private void refreshPaymentStates() {
        for (int i = 0; i < paymentContainer.getChildCount(); i++) {
            View row = paymentContainer.getChildAt(i);
            applyOptionState(row, row.getTag() != null && row.getTag().equals(selectedPaymentKey));
        }
    }

    /** Toggle the radio icon + bordered background for a selectable option row. */
    private void applyOptionState(View row, boolean selected) {
        ImageView radio = row.findViewById(R.id.optionRadio);
        radio.setImageResource(selected ? R.drawable.ic_radio_on_purple : R.drawable.ic_radio_unselected);
        row.setBackgroundResource(selected
                ? R.drawable.bg_fulfillment_selected : R.drawable.bg_fulfillment_unselected);
    }

    // ---- Summary + place order ------------------------------------------

    private void updateSummary() {
        ((TextView) findViewById(R.id.summarySubtotal)).setText(money(subtotal));
        ((TextView) findViewById(R.id.summaryTotal)).setText(money(subtotal));
        btnPlaceOrder.setText(getString(R.string.order_amount, money(subtotal)));
    }

    private void placeOrder() {
        if (cartItemIds.isEmpty()) {
            Toast.makeText(this, R.string.select_items_to_order, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedSlotIndex < 0 || selectedSlotIndex >= slots.size()) {
            Toast.makeText(this, R.string.choose_delivery_window, Toast.LENGTH_SHORT).show();
            return;
        }
        if (address.trim().isEmpty()) {
            Toast.makeText(this, R.string.no_address_set, Toast.LENGTH_SHORT).show();
            return;
        }

        DeliverySlot slot = slots.get(selectedSlotIndex);
        String notes = notesInput.getText().toString().trim();

        btnPlaceOrder.setEnabled(false);
        btnPlaceOrder.setText(R.string.placing_order);

        api.createOrder(distributorId, toJsonArray(cartItemIds), slot.deliveryDate,
                slot.startApi, slot.endApi, notes, address, selectedPaymentKey,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(MakeOrderActivity.this,
                                R.string.order_placed_success, Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        btnPlaceOrder.setEnabled(true);
                        updateSummary();
                        Toast.makeText(MakeOrderActivity.this,
                                message != null ? message : getString(R.string.order_failed),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Build a JSON array string like {@code [10,11]} from numeric id strings. */
    private static String toJsonArray(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.append(']').toString();
    }

    private String money(double v) {
        return currency + String.format(Locale.US, "%,.2f", v);
    }
}
