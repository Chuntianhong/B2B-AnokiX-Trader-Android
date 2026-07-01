package com.anokix.traderapp.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.ReturnReason;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.GrvListData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Return Goods form. Files a new return against a received GRV: pick a GRV that has
 * received stock, choose a return quantity per line, pick a reason + note, then POST
 * to api/trader/returns (which reduces stock immediately, pending distributor review).
 */
public class GoodsReturnFormActivity extends AppCompatActivity {

    /** GRVs that still have received stock to return against. */
    private final List<GrvListData.Grv> returnableGrvs = new ArrayList<>();
    private GrvListData.Grv selectedGrv;
    /** Returnable line items of the selected GRV (received_quantity > 0). */
    private final List<GrvListData.Item> currentItems = new ArrayList<>();
    private int[] qtys = new int[0];
    private final List<TextView> qtyViews = new ArrayList<>();
    private String reasonKey;

    private TextView grvField, grvHint, reasonField;
    private View itemsSection;
    private LinearLayout itemsContainer;
    private EditText noteInput;
    private MaterialButton btnSubmit;
    private boolean submitting = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods_return_form);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        grvField = findViewById(R.id.grvField);
        grvHint = findViewById(R.id.grvHint);
        reasonField = findViewById(R.id.reasonField);
        itemsSection = findViewById(R.id.itemsSection);
        itemsContainer = findViewById(R.id.itemsContainer);
        noteInput = findViewById(R.id.noteInput);
        btnSubmit = findViewById(R.id.btnSubmit);

        grvField.setOnClickListener(v -> showGrvPicker());
        reasonField.setOnClickListener(v -> showReasonPicker());
        btnSubmit.setOnClickListener(v -> submit());

        loadGrvs();
    }

    // ---- Load source GRVs ------------------------------------------------

    private void loadGrvs() {
        grvField.setText("Loading deliveries…");
        ApiClient.get(this).getGrvs(new ApiCallback<GrvListData>() {
            @Override
            public void onSuccess(GrvListData data) {
                returnableGrvs.clear();
                if (data != null && data.grvs != null) {
                    for (GrvListData.Grv g : data.grvs) {
                        if (hasReturnableItems(g)) returnableGrvs.add(g);
                    }
                }
                if (returnableGrvs.isEmpty()) {
                    grvField.setText("No received goods to return");
                    grvHint.setText("Confirm receipt of a delivery (GRV) before you can return goods.");
                    grvHint.setVisibility(View.VISIBLE);
                } else {
                    grvField.setText("Select a received GRV");
                    grvHint.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                grvField.setText("Select a received GRV");
                Toast.makeText(GoodsReturnFormActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean hasReturnableItems(GrvListData.Grv g) {
        if (g.items == null) return false;
        for (GrvListData.Item item : g.items) {
            if (item.received_quantity > 0) return true;
        }
        return false;
    }

    // ---- Pickers ---------------------------------------------------------

    private void showGrvPicker() {
        if (returnableGrvs.isEmpty()) {
            Toast.makeText(this, "No received goods to return.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[returnableGrvs.size()];
        for (int i = 0; i < returnableGrvs.size(); i++) {
            GrvListData.Grv g = returnableGrvs.get(i);
            String dist = g.distributor_name != null && !g.distributor_name.isEmpty()
                    ? " · " + g.distributor_name : "";
            labels[i] = g.grv_number + dist;
        }
        new AlertDialog.Builder(this)
                .setTitle("Select a received GRV")
                .setItems(labels, (d, which) -> selectGrv(returnableGrvs.get(which)))
                .show();
    }

    private void selectGrv(GrvListData.Grv g) {
        selectedGrv = g;
        String dist = g.distributor_name != null && !g.distributor_name.isEmpty()
                ? " · " + g.distributor_name : "";
        grvField.setText(g.grv_number + dist);

        currentItems.clear();
        for (GrvListData.Item item : g.items) {
            if (item.received_quantity > 0) currentItems.add(item);
        }
        qtys = new int[currentItems.size()];
        buildItemRows();
        itemsSection.setVisibility(View.VISIBLE);
    }

    private void buildItemRows() {
        itemsContainer.removeAllViews();
        qtyViews.clear();
        for (int i = 0; i < currentItems.size(); i++) {
            final int index = i;
            GrvListData.Item item = currentItems.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_return_line, itemsContainer, false);
            ((TextView) row.findViewById(R.id.lineName)).setText(safe(item.name));
            ((TextView) row.findViewById(R.id.lineAvail))
                    .setText(item.received_quantity + " available");
            TextView qtyValue = row.findViewById(R.id.qtyValue);
            qtyViews.add(qtyValue);
            ImageView minus = row.findViewById(R.id.btnMinus);
            ImageView plus = row.findViewById(R.id.btnPlus);
            minus.setOnClickListener(v -> changeQty(index, -1));
            plus.setOnClickListener(v -> changeQty(index, +1));
            itemsContainer.addView(row);
        }
    }

    private void changeQty(int index, int delta) {
        int max = currentItems.get(index).received_quantity;
        int next = Math.max(0, Math.min(max, qtys[index] + delta));
        qtys[index] = next;
        qtyViews.get(index).setText(String.valueOf(next));
    }

    private void showReasonPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Reason for return")
                .setItems(ReturnReason.LABELS, (d, which) -> {
                    reasonKey = ReturnReason.KEYS[which];
                    reasonField.setText(ReturnReason.LABELS[which]);
                })
                .show();
    }

    // ---- Submit ----------------------------------------------------------

    private void submit() {
        if (submitting) return;
        if (selectedGrv == null) {
            Toast.makeText(this, "Select a delivery (GRV) to return against.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reasonKey == null) {
            Toast.makeText(this, "Choose a reason for the return.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONArray items = new JSONArray();
        try {
            for (int i = 0; i < currentItems.size(); i++) {
                if (qtys[i] <= 0) continue;
                JSONObject line = new JSONObject();
                line.put("product_id", currentItems.get(i).product_id);
                line.put("quantity", qtys[i]);
                line.put("reason", reasonKey);
                items.put(line);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't build request.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (items.length() == 0) {
            Toast.makeText(this, "Set a return quantity for at least one item.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("items", items);
            body.put("reason", reasonKey);
            body.put("note", noteInput.getText().toString().trim());
            body.put("source_grv_id", selectedGrv.id);
        } catch (Exception ignored) {
        }

        submitting = true;
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting…");
        ApiClient.get(this).createReturn(body.toString(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(GoodsReturnFormActivity.this,
                        "Return submitted. Stock updated; awaiting distributor review.",
                        Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                submitting = false;
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Return");
                Toast.makeText(GoodsReturnFormActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
