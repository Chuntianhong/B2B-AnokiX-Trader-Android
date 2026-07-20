package com.anokix.traderapp.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.Http;
import com.anokix.traderapp.network.dto.AutocompleteData;
import com.anokix.traderapp.network.dto.BusinessProfileData;
import com.anokix.traderapp.network.dto.TraderDashboardData;
import com.anokix.traderapp.network.dto.CoordinateData;
import com.anokix.traderapp.network.dto.ReferenceData;
import com.anokix.traderapp.ui.views.FlowLayout;
import com.anokix.traderapp.ui.views.LogoCropView;
import com.anokix.traderapp.ui.views.RobotoBoldTextView;
import com.anokix.traderapp.ui.views.RobotoTextView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.hbb20.CountryCodePicker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings → Business Profile editor. Seven Trader sections matching the design
 * (Owner · Business · Type · Location · Verify · Distributor · Wallet) with a
 * tappable stepper and per-step "Save Changes". Each step posts only its own
 * fields to api/trader/business/profile/update (partial multipart). Param names /
 * formats follow the real web payload.
 */
public class BusinessProfileActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int OWNER = 0, BUSINESS = 1, TYPE = 2, LOCATION = 3,
            VERIFY = 4, DISTRIBUTOR = 5, WALLET = 6, STEP_COUNT = 7;
    private static final String[] STEP_LABELS =
            {"Owner", "Business", "Type", "Location", "Verify", "Distributor", "Wallet"};

    private static final long MAX_DOC_BYTES = 5L * 1024 * 1024;
    private static final String LOGO_TARGET = "__logo__";

    // Delivery grid rows in payload-index order: 0=Sun .. 6=Sat.
    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String DEFAULT_HOURS = "8.00-18.00";

    private static final String[] PAYMENT_LABELS = {"Wallet", "EFT", "Cash", "Card"};
    private static final String[] PAYMENT_CODES = {"wallet", "eft", "cash", "card"};
    private static final String[] FALLBACK_TRADER_TYPES = {
            "Spaza Shop", "Mini Market", "Superette", "General Dealer",
            "Wholesaler", "Pharmacy", "Butchery", "Restaurant"};

    private ApiClient api;
    private ViewFlipper flipper;
    private LinearLayout stepperContainer;
    private TextView btnSave, btnPrev;
    private final TextView[] stepCircles = new TextView[STEP_COUNT];
    private final TextView[] stepLabels = new TextView[STEP_COUNT];
    private int currentStep = 0;

    private CountryCodePicker ccpBusinessPhone;
    private boolean walletLoading;

    // Trader type
    private final List<ReferenceData.TraderType> traderTypes = new ArrayList<>();
    private long selectedTraderTypeId = -1;

    // Categories / distributors
    private final List<ReferenceData.ProductCategory> primaryCategories = new ArrayList<>();
    private final List<Long> selectedCategoryIds = new ArrayList<>();
    private final List<ReferenceData.Distributor> distributors = new ArrayList<>();
    private final List<Long> selectedDistributorIds = new ArrayList<>();

    // Delivery grid + payment
    private final List<View> dayRows = new ArrayList<>();
    private String paymentCode;

    // Logo
    private LinearLayout logoPlaceholder;
    private ImageView logoPreview;
    private Bitmap logoBitmap;

    // Documents — POST field key, title, GET key for prefill.
    private static final class DocDef {
        final String key, title, getKey;
        DocDef(String key, String title, String getKey) { this.key = key; this.title = title; this.getKey = getKey; }
    }
    private final List<DocDef> docDefs = new ArrayList<>();
    private final Map<String, Uri> docUris = new LinkedHashMap<>();
    private final Map<String, TextView> docSubtitleViews = new LinkedHashMap<>();
    private final Map<String, TextView> docButtons = new LinkedHashMap<>();

    // Pickers
    private String pendingTarget;
    private Uri pendingCameraUri;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> fileLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // Map / address
    private MapView businessMap;
    private GoogleMap googleMap;
    private Marker marker;
    private Double pickedLat, pickedLng;
    private final Handler autocompleteHandler = new Handler(Looper.getMainLooper());
    private Runnable autocompleteRunnable;
    private boolean suppressAddressWatcher;
    private android.widget.ListPopupWindow addressPopup;
    private final List<AutocompleteData.Prediction> predictions = new ArrayList<>();

    // Prefill bookkeeping
    private boolean profileLoaded, referenceLoaded;
    private final List<Long> pendingCategoryIds = new ArrayList<>();
    private final List<Long> pendingDistributorIds = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_profile);
        api = ApiClient.get(this);

        flipper = findViewById(R.id.stepFlipper);
        stepperContainer = findViewById(R.id.stepperContainer);
        btnSave = findViewById(R.id.btnSave);
        btnPrev = findViewById(R.id.btnPrev);
        logoPlaceholder = findViewById(R.id.logoPlaceholder);
        logoPreview = findViewById(R.id.logoPreview);
        ccpBusinessPhone = findViewById(R.id.ccpBusinessPhone);
        ccpBusinessPhone.registerCarrierNumberEditText(findViewById(R.id.etBusinessPhone));

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        btnPrev.setOnClickListener(v -> { if (currentStep == 0) finish(); else showStep(currentStep - 1); });
        btnSave.setOnClickListener(v -> saveStep(currentStep));

        buildStepper();
        buildDocumentRows();
        buildDeliveryGrid();
        setupWalletStep();
        setupLogoUpload();
        setupTraderTypeField();
        setupCategoryField();
        setupDistributorField();
        setupPaymentField();
        setupPickers();
        setupAddressAutocomplete();
        setupMap(savedInstanceState);

        showStep(0);
        loadReference();
        loadProfile();
    }

    // ---- Stepper ---------------------------------------------------------

    private void buildStepper() {
        for (int i = 0; i < STEP_COUNT; i++) {
            if (i > 0) {
                View connector = new View(this);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(18), dp(1));
                clp.setMargins(dp(6), 0, dp(6), 0);
                connector.setLayoutParams(clp);
                connector.setBackgroundColor(ContextCompat.getColor(this, R.color.border));
                stepperContainer.addView(connector);
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            final int index = i;
            item.setOnClickListener(v -> showStep(index));

            RobotoBoldTextView circle = new RobotoBoldTextView(this);
            circle.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
            circle.setGravity(Gravity.CENTER);
            circle.setTextSize(12);
            circle.setText(String.valueOf(i + 1));
            item.addView(circle);

            RobotoTextView label = new RobotoTextView(this);
            label.setText(STEP_LABELS[i]);
            label.setTextSize(13);
            label.setPadding(dp(6), 0, 0, 0);
            item.addView(label);

            stepperContainer.addView(item);
            stepCircles[i] = circle;
            stepLabels[i] = label;
        }
    }

    private void renderStepper() {
        for (int i = 0; i < STEP_COUNT; i++) {
            boolean active = i == currentStep;
            stepCircles[i].setBackgroundResource(active
                    ? R.drawable.bg_step_circle_active : R.drawable.bg_step_circle_inactive);
            stepCircles[i].setTextColor(active ? 0xFFFFFFFF
                    : ContextCompat.getColor(this, R.color.text_secondary));
            stepLabels[i].setTextColor(ContextCompat.getColor(this,
                    active ? R.color.purple_primary : R.color.text_secondary));
            stepLabels[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void showStep(int index) {
        currentStep = index;
        flipper.setDisplayedChild(index);
        btnPrev.setText(index == 0 ? R.string.cancel_btn : R.string.back);
        btnSave.setVisibility(index == WALLET ? View.GONE : View.VISIBLE);
        renderStepper();
        stepperContainer.post(() -> {
            View item = (View) stepLabels[index].getParent();
            if (item != null) {
                ((android.widget.HorizontalScrollView) stepperContainer.getParent())
                        .smoothScrollTo(Math.max(0, item.getLeft() - dp(40)), 0);
            }
        });
    }

    // ---- Trader type -----------------------------------------------------

    private void setupTraderTypeField() {
        findViewById(R.id.fieldTraderType).setOnClickListener(v -> {
            List<String> names = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            if (!traderTypes.isEmpty()) {
                for (ReferenceData.TraderType t : traderTypes) { names.add(t.name); ids.add(t.id); }
            } else {
                for (int i = 0; i < FALLBACK_TRADER_TYPES.length; i++) { names.add(FALLBACK_TRADER_TYPES[i]); ids.add((long) (i + 1)); }
            }
            new AlertDialog.Builder(this)
                    .setTitle("Trader Type")
                    .setItems(names.toArray(new String[0]), (d, w) -> {
                        selectedTraderTypeId = ids.get(w);
                        setTraderTypeLabel(names.get(w));
                    })
                    .show();
        });
    }

    private void setTraderTypeLabel(String name) {
        TextView l = findViewById(R.id.lblTraderType);
        l.setText(name);
        l.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void applyTraderTypePrefill() {
        if (selectedTraderTypeId < 0) return;
        String name = null;
        for (ReferenceData.TraderType t : traderTypes) if (t.id == selectedTraderTypeId) name = t.name;
        if (name == null && selectedTraderTypeId >= 1 && selectedTraderTypeId <= FALLBACK_TRADER_TYPES.length) {
            name = FALLBACK_TRADER_TYPES[(int) selectedTraderTypeId - 1];
        }
        if (name != null) setTraderTypeLabel(name);
    }

    // ---- Categories / distributors --------------------------------------

    private void setupCategoryField() {
        findViewById(R.id.categoryField).setOnClickListener(v -> showCategoryDialog());
    }

    private void showCategoryDialog() {
        if (primaryCategories.isEmpty()) { toast(getString(R.string.coming_soon)); return; }
        String[] names = new String[primaryCategories.size()];
        boolean[] checkedItems = new boolean[primaryCategories.size()];
        for (int i = 0; i < primaryCategories.size(); i++) {
            names[i] = primaryCategories.get(i).title;
            checkedItems[i] = selectedCategoryIds.contains(primaryCategories.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.primary_product_categories)
                .setMultiChoiceItems(names, checkedItems, (d, which, isChecked) -> {
                    long id = primaryCategories.get(which).id;
                    if (isChecked) { if (!selectedCategoryIds.contains(id)) selectedCategoryIds.add(id); }
                    else selectedCategoryIds.remove(id);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> renderCategoryChips())
                .show();
    }

    private void renderCategoryChips() {
        FlowLayout chips = findViewById(R.id.categoryChips);
        chips.removeAllViews();
        findViewById(R.id.categoryHint).setVisibility(selectedCategoryIds.isEmpty() ? View.VISIBLE : View.GONE);
        for (ReferenceData.ProductCategory cat : primaryCategories) {
            if (!selectedCategoryIds.contains(cat.id)) continue;
            chips.addView(buildChip(chips, cat.title, () -> { selectedCategoryIds.remove(cat.id); renderCategoryChips(); }));
        }
    }

    private void setupDistributorField() {
        findViewById(R.id.distributorField).setOnClickListener(v -> showDistributorDialog());
    }

    private void showDistributorDialog() {
        if (distributors.isEmpty()) { toast(getString(R.string.coming_soon)); return; }
        String[] names = new String[distributors.size()];
        boolean[] checkedItems = new boolean[distributors.size()];
        for (int i = 0; i < distributors.size(); i++) {
            names[i] = distributors.get(i).displayName();
            checkedItems[i] = selectedDistributorIds.contains(distributors.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.preferred_distributors)
                .setMultiChoiceItems(names, checkedItems, (d, which, isChecked) -> {
                    long id = distributors.get(which).id;
                    if (isChecked) { if (!selectedDistributorIds.contains(id)) selectedDistributorIds.add(id); }
                    else selectedDistributorIds.remove(id);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> renderDistributorChips())
                .show();
    }

    private void renderDistributorChips() {
        FlowLayout chips = findViewById(R.id.distributorChips);
        chips.removeAllViews();
        findViewById(R.id.distributorHint).setVisibility(selectedDistributorIds.isEmpty() ? View.VISIBLE : View.GONE);
        for (ReferenceData.Distributor dist : distributors) {
            if (!selectedDistributorIds.contains(dist.id)) continue;
            chips.addView(buildChip(chips, dist.displayName(), () -> { selectedDistributorIds.remove(dist.id); renderDistributorChips(); }));
        }
    }

    private View buildChip(ViewGroup parent, String label, Runnable onRemove) {
        View chip = LayoutInflater.from(this).inflate(R.layout.item_tag_chip, parent, false);
        ((TextView) chip.findViewById(R.id.tagText)).setText(label);
        chip.findViewById(R.id.tagRemove).setOnClickListener(v -> onRemove.run());
        return chip;
    }

    // ---- Payment ---------------------------------------------------------

    private void setupPaymentField() {
        findViewById(R.id.fieldPayment).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.select_payment_method)
                        .setItems(PAYMENT_LABELS, (d, w) -> {
                            paymentCode = PAYMENT_CODES[w];
                            setPaymentLabel(PAYMENT_LABELS[w]);
                        })
                        .show());
    }

    private void setPaymentLabel(String label) {
        TextView l = findViewById(R.id.lblPayment);
        l.setText(label);
        l.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void applyPaymentPrefill(String code) {
        if (code == null) return;
        for (int i = 0; i < PAYMENT_CODES.length; i++) {
            if (PAYMENT_CODES[i].equalsIgnoreCase(code)) { paymentCode = PAYMENT_CODES[i]; setPaymentLabel(PAYMENT_LABELS[i]); return; }
        }
    }

    // ---- Delivery grid ---------------------------------------------------

    private void buildDeliveryGrid() {
        LinearLayout container = findViewById(R.id.deliveryGridContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String day : DAY_NAMES) {
            View row = inflater.inflate(R.layout.item_delivery_day, container, false);
            ((CheckBox) row.findViewById(R.id.cbDay)).setText(day);
            container.addView(row);
            dayRows.add(row);
        }
    }

    /**
     * "1:8.00-18.00/3:8.00-18.00/5:8.00-18.00" — ONLY the ticked days, keyed by ISO day
     * (1=Mon…7=Sun) and emitted in Mon→Sun order. This is the shape the server stores and
     * that {@link com.anokix.traderapp.model.DeliverySlot} reads when building order slots;
     * it drops the old leading abbreviation CSV and no longer writes hours for unticked
     * days (which used to make every weekday a delivery day, and lost Sunday entirely
     * because DeliverySlot rejects day 0).
     */
    private String deliveryDaysString() {
        List<String> entries = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            int i = rowForDay(day);
            View row = dayRows.get(i);
            if (!((CheckBox) row.findViewById(R.id.cbDay)).isChecked()) continue;
            String o1 = etText(row, R.id.etOpen1), c1 = etText(row, R.id.etClose1);
            String o2 = etText(row, R.id.etOpen2), c2 = etText(row, R.id.etClose2);
            String slot = (o1.isEmpty() && c1.isEmpty()) ? DEFAULT_HOURS : fmtHour(o1) + "-" + fmtHour(c1);
            if (!o2.isEmpty() || !c2.isEmpty()) slot += "," + fmtHour(o2) + "-" + fmtHour(c2);
            entries.add(day + ":" + slot);
        }
        return TextUtils.join("/", entries);
    }

    private String fmtHour(String v) {
        if (v.isEmpty()) return "0.00";
        return v.contains(".") ? v : v + ".00";
    }

    /**
     * Prefill the grid from {@code preferred_delivery_days}. Handles both shapes:
     *
     *  - canonical (what the server stores, and what {@link com.anokix.traderapp.model.DeliverySlot}
     *    reads): {@code "1:8.00-18.00/3:8.00-18.00/5:8.00-18.00"} — ONLY the delivery days are
     *    listed, so a day that carries hours IS a selected day and must be ticked.
     *  - legacy (what this screen used to write): a leading {@code "Mon,Wed,Fri"} CSV followed by
     *    hours for ALL seven days. There the CSV is the only reliable source of selection, since
     *    unchecked days still carry default hours.
     */
    private void prefillDeliveryDays(String raw) {
        if (raw == null || raw.isEmpty()) return;

        String[] parts = raw.split("/");
        // A first segment without a ':' is the legacy day-abbreviation CSV.
        boolean hasLegacyCsv = parts.length > 0 && !parts[0].trim().isEmpty()
                && parts[0].indexOf(':') < 0;

        if (hasLegacyCsv) {
            List<String> abbrs = new ArrayList<>();
            for (String p : parts[0].split(",")) abbrs.add(p.trim());
            for (int i = 0; i < dayRows.size(); i++) {
                if (abbrs.contains(DAY_NAMES[i])) setDayChecked(i, true);
            }
        }

        for (String seg : parts) {
            String s = seg.trim();
            int colon = s.indexOf(':');
            if (colon <= 0) continue;                   // the legacy CSV, or junk
            int day;
            try {
                day = Integer.parseInt(s.substring(0, colon).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            int idx = rowForDay(day);
            if (idx < 0) continue;

            View row = dayRows.get(idx);
            String[] slots = s.substring(colon + 1).split(",");
            boolean filled = fillSlot(row, R.id.etOpen1, R.id.etClose1, slots.length > 0 ? slots[0] : "");
            filled |= fillSlot(row, R.id.etOpen2, R.id.etClose2, slots.length > 1 ? slots[1] : "");
            // Canonical strings list delivery days only, so real hours imply a ticked day.
            if (filled && !hasLegacyCsv) setDayChecked(idx, true);
        }
    }

    /**
     * Payload day number → grid row. The payload is ISO (1=Mon…7=Sun) while the rows run
     * 0=Sun…6=Sat, so 1–6 line up and only Sunday moves. A legacy {@code 0} also means
     * Sunday. Returns -1 for anything out of range.
     */
    private int rowForDay(int day) {
        if (day == 0 || day == 7) return 0;             // Sunday (legacy 0 / ISO 7)
        return (day >= 1 && day <= 6) ? day : -1;
    }

    private void setDayChecked(int idx, boolean checked) {
        ((CheckBox) dayRows.get(idx).findViewById(R.id.cbDay)).setChecked(checked);
    }

    /** @return true when the slot carried a real time range (so the day counts as selected). */
    private boolean fillSlot(View row, int openId, int closeId, String slot) {
        if (slot == null) return false;
        String s = slot.trim();
        if (s.isEmpty() || "-".equals(s)) return false;
        String[] oc = s.split("-");
        if (oc.length != 2) return false;
        ((EditText) row.findViewById(openId)).setText(oc[0].trim());
        ((EditText) row.findViewById(closeId)).setText(oc[1].trim());
        return true;
    }

    // ---- Wallet step (live, GET api/common/wallet) ------------------------

    /**
     * The Wallet step is read-only (the Save button is hidden for it), so it just loads
     * the live balance once the editor opens and offers a manual Refresh.
     */
    private void setupWalletStep() {
        findViewById(R.id.walletRefreshBtn).setOnClickListener(v -> loadWallet());
        loadWallet();
    }

    private void loadWallet() {
        if (walletLoading) return;
        walletLoading = true;
        setWalletRefreshing(true);
        api.getWallet(new ApiCallback<TraderDashboardData.Wallet>() {
            @Override public void onSuccess(TraderDashboardData.Wallet wallet) {
                walletLoading = false;
                if (isFinishing() || isDestroyed()) return;
                setWalletRefreshing(false);
                bindWallet(wallet);
            }
            @Override public void onError(String message) {
                walletLoading = false;
                if (isFinishing() || isDestroyed()) return;
                setWalletRefreshing(false);
                toast(message == null ? getString(R.string.wallet_refresh_failed) : message);
            }
        });
    }

    private void setWalletRefreshing(boolean busy) {
        View btn = findViewById(R.id.walletRefreshBtn);
        btn.setEnabled(!busy);
        btn.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.walletRefreshSpinner).setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void bindWallet(TraderDashboardData.Wallet wallet) {
        TextView pill = findViewById(R.id.walletStatusPill);
        TextView account = findViewById(R.id.walletAccountNumber);
        TextView activated = findViewById(R.id.walletActivatedAt);

        String status = wallet == null ? null : wallet.status;
        boolean active = "active".equalsIgnoreCase(status);
        pill.setText(status == null || status.isEmpty()
                ? getString(R.string.wallet_not_configured)
                : OrderFormat.humanize(status));
        int statusColor = ContextCompat.getColor(this, active ? R.color.success : R.color.warning);
        pill.setTextColor(statusColor);
        pill.setBackgroundTintList(ColorStateList.valueOf(
                Color.argb(28, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))));

        String number = wallet == null ? null : wallet.account_number;
        account.setText(number == null || number.isEmpty() ? "—" : number);

        String since = wallet == null ? null : wallet.activated_at;
        if (since == null || since.isEmpty()) {
            activated.setVisibility(View.GONE);
        } else {
            activated.setVisibility(View.VISIBLE);
            activated.setText(getString(R.string.wallet_activated_at, since));
        }

        TraderDashboardData.Balance balance = wallet == null ? null : wallet.balance;
        ((TextView) findViewById(R.id.walletAvailableValue))
                .setText(OrderFormat.money(balance == null ? 0 : balance.available, "R"));
        ((TextView) findViewById(R.id.walletCurrentValue))
                .setText(OrderFormat.money(balance == null ? 0 : balance.current, "R"));
        ((TextView) findViewById(R.id.walletPendingValue))
                .setText(OrderFormat.money(balance == null ? 0 : balance.pending, "R"));

        bindWalletTransactions(balance == null ? null : balance.transactions);
    }

    private void bindWalletTransactions(List<TraderDashboardData.WalletTxn> txns) {
        LinearLayout container = findViewById(R.id.walletTxnContainer);
        View empty = findViewById(R.id.walletTxnEmpty);
        container.removeAllViews();

        if (txns == null || txns.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < txns.size(); i++) {
            TraderDashboardData.WalletTxn t = txns.get(i);
            View row = inflater.inflate(R.layout.item_wallet_txn_row, container, false);
            ((TextView) row.findViewById(R.id.txnDate)).setText(walletTxnDate(t.date));

            // The API sends the amount as a string ("-60.00"); keep the sign in the label.
            double value = parseAmount(t.amount);
            TextView amount = row.findViewById(R.id.txnAmount);
            amount.setText((value < 0 ? "-" : "") + OrderFormat.money(Math.abs(value), "R"));
            amount.setTextColor(ContextCompat.getColor(this,
                    value < 0 ? R.color.text_primary : R.color.success));

            container.addView(row);
            if (i < txns.size() - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
                sep.setBackgroundColor(ContextCompat.getColor(this, R.color.border));
                container.addView(sep);
            }
        }
    }

    private double parseAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "2026-07-15T15:58:07+02:00" → "15 Jul 2026, 15:58". Falls back to the raw value. */
    private String walletTxnDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                java.util.Date d = new java.text.SimpleDateFormat(pattern, Locale.US).parse(iso);
                if (d != null) {
                    return new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(d);
                }
            } catch (java.text.ParseException ignored) {
                // try the next pattern
            }
        }
        return iso;
    }

    // ---- Documents -------------------------------------------------------

    private void buildDocumentRows() {
        docDefs.add(new DocDef("business_registration_document", "Business Registration Document *", "registration_document"));
        docDefs.add(new DocDef("owner_id_document", "Owner ID Document *", "id_document"));
        docDefs.add(new DocDef("proof_of_address", "Proof of Address *", "proof_of_address"));
        docDefs.add(new DocDef("store_front_photo", "Store Front Photo *", "store_front_photo"));
        docDefs.add(new DocDef("store_interior_photo", "Store Interior Photo *", "store_interior_photo"));

        LinearLayout container = findViewById(R.id.documentContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (DocDef def : docDefs) {
            View row = inflater.inflate(R.layout.item_document_row, container, false);
            ((TextView) row.findViewById(R.id.docTitle)).setText(def.title);
            TextView sub = row.findViewById(R.id.docSubtitle);
            TextView upload = row.findViewById(R.id.docUploadButton);
            sub.setVisibility(View.GONE);
            upload.setOnClickListener(v -> chooseImageSource(def.key, true));
            docSubtitleViews.put(def.key, sub);
            docButtons.put(def.key, upload);
            container.addView(row);
        }
    }

    private void setupLogoUpload() {
        findViewById(R.id.logoUploadBox).setOnClickListener(v -> chooseImageSource(LOGO_TARGET, false));
    }

    // ---- Load + prefill --------------------------------------------------

    private void loadProfile() {
        api.getBusinessProfile(new ApiCallback<BusinessProfileData>() {
            @Override public void onSuccess(BusinessProfileData data) {
                BusinessProfileData.Profile p = data == null ? null : data.profile;
                if (p == null) return;
                setText(R.id.etIdNumber, p.id_number);
                setText(R.id.etPassportNumber, p.passport_number);

                setText(R.id.etBusinessName, p.business_name);
                setText(R.id.etTradingName, p.trading_name);
                setText(R.id.etRegistrationNumber, p.business_registration_number);
                setText(R.id.etBusinessEmail, p.business_email);
                if (p.business_phone_number != null && !p.business_phone_number.isEmpty()) {
                    try { ccpBusinessPhone.setFullNumber(p.business_phone_number); }
                    catch (Exception ignored) { setText(R.id.etBusinessPhone, p.business_phone_number); }
                }

                selectedTraderTypeId = parseLong(p.trader_type, -1);
                applyTraderTypePrefill();

                setAddressText(p.address);
                if (p.latitude != null && p.longitude != null) {
                    pickedLat = p.latitude; pickedLng = p.longitude;
                    moveMapTo(new LatLng(p.latitude, p.longitude), true);
                }
                prefillDocuments(p.documents);

                applyPaymentPrefill(p.payment_method);
                prefillDeliveryDays(p.preferred_delivery_days);

                pendingCategoryIds.clear();
                pendingCategoryIds.addAll(parseIds(p.primary_product_category_ids));
                pendingDistributorIds.clear();
                pendingDistributorIds.addAll(parseIds(p.preferred_distributor_ids));
                profileLoaded = true;
                applyPrefillSelections();
            }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void loadReference() {
        api.getReference(new ApiCallback<ReferenceData>() {
            @Override public void onSuccess(ReferenceData data) {
                if (data != null) {
                    if (data.trader_types != null) { traderTypes.clear(); traderTypes.addAll(data.trader_types); applyTraderTypePrefill(); }
                    if (data.product_categories != null) {
                        primaryCategories.clear();
                        for (ReferenceData.ProductCategory c : data.product_categories) if (c.parent_id == null) primaryCategories.add(c);
                    }
                    if (data.distributors != null) { distributors.clear(); distributors.addAll(data.distributors); }
                }
                referenceLoaded = true;
                applyPrefillSelections();
            }
            @Override public void onError(String message) { }
        });
    }

    private void applyPrefillSelections() {
        if (!(profileLoaded && referenceLoaded)) return;
        selectedCategoryIds.clear();
        selectedCategoryIds.addAll(pendingCategoryIds);
        renderCategoryChips();
        selectedDistributorIds.clear();
        selectedDistributorIds.addAll(pendingDistributorIds);
        renderDistributorChips();
    }

    private void prefillDocuments(BusinessProfileData.Documents docs) {
        if (docs == null) return;
        Map<String, List<BusinessProfileData.Doc>> byKey = new HashMap<>();
        byKey.put("registration_document", docs.registration_document);
        byKey.put("id_document", docs.id_document);
        byKey.put("proof_of_address", docs.proof_of_address);
        byKey.put("store_front_photo", docs.store_front_photo);
        byKey.put("store_interior_photo", docs.store_interior_photo);
        for (DocDef def : docDefs) {
            List<BusinessProfileData.Doc> list = byKey.get(def.getKey);
            if (list != null && !list.isEmpty()) {
                TextView sub = docSubtitleViews.get(def.key);
                TextView btn = docButtons.get(def.key);
                String name = list.get(0).original_name;
                if (sub != null) {
                    sub.setVisibility(View.VISIBLE);
                    sub.setText("Uploaded: " + (name == null ? "file" : name));
                    sub.setTextColor(ContextCompat.getColor(this, R.color.success));
                }
                if (btn != null) btn.setText(R.string.replace);
            }
        }
    }

    // ---- Save per step ---------------------------------------------------

    private void saveStep(int step) {
        Map<String, String> form = new TreeMap<>();
        List<Http.FilePart> files = new ArrayList<>();
        switch (step) {
            case OWNER:
                form.put("id_number", text(R.id.etIdNumber));
                form.put("passport_number", text(R.id.etPassportNumber));
                break;
            case BUSINESS:
                if (text(R.id.etBusinessName).isEmpty()) { toast("Business name is required."); return; }
                form.put("business_name", text(R.id.etBusinessName));
                form.put("trading_name", text(R.id.etTradingName));
                form.put("business_registration_number", text(R.id.etRegistrationNumber));
                form.put("business_email", text(R.id.etBusinessEmail));
                form.put("business_phone_number",
                        text(R.id.etBusinessPhone).isEmpty() ? "" : "+" + ccpBusinessPhone.getFullNumber());
                if (logoBitmap != null) files.add(logoFilePart());
                break;
            case TYPE:
                if (selectedTraderTypeId < 0) { toast("Select a trader type."); return; }
                form.put("trader_type", String.valueOf(selectedTraderTypeId));
                form.put("primary_product_category_ids", joinLongs(selectedCategoryIds));
                break;
            case LOCATION:
                form.put("address", text(R.id.etAddressSearch));
                if (pickedLat != null && pickedLng != null) {
                    form.put("latitude", String.valueOf(pickedLat));
                    form.put("longitude", String.valueOf(pickedLng));
                }
                break;
            case VERIFY:
                for (Map.Entry<String, Uri> e : docUris.entrySet()) {
                    if (fileSize(e.getValue()) > MAX_DOC_BYTES) { toast(getString(R.string.err_doc_too_large)); return; }
                    Http.FilePart part = readFilePart(e.getKey(), e.getValue());
                    if (part != null) files.add(part);
                }
                if (files.isEmpty()) { toast("Pick a document to upload first."); return; }
                break;
            case DISTRIBUTOR:
                form.put("preferred_distributor_ids", joinLongs(selectedDistributorIds));
                form.put("preferred_delivery_days", deliveryDaysString());
                form.put("payment_method", paymentCode == null ? "" : paymentCode);
                break;
            default: return;
        }
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");
        api.updateBusinessProfile(form, files, new ApiCallback<Void>() {
            @Override public void onSuccess(Void unused) {
                btnSave.setEnabled(true); btnSave.setText("Save Changes");
                toast("Business profile updated.");
            }
            @Override public void onError(String message) {
                btnSave.setEnabled(true); btnSave.setText("Save Changes");
                toast(message);
            }
        });
    }

    // ---- Pickers ---------------------------------------------------------

    private void setupPickers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), success -> {
                    if (success && pendingCameraUri != null) onImagePicked(pendingCameraUri);
                });
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> { if (uri != null) onImagePicked(uri); });
        fileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> { if (uri != null) onImagePicked(uri); });
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) launchCamera();
                    else Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                });
    }

    private void chooseImageSource(String target, boolean allowFile) {
        pendingTarget = target;
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.source_take_photo));
        labels.add(getString(R.string.source_choose_gallery));
        if (allowFile) labels.add(getString(R.string.source_choose_file));
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_image_source)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == 0) requestCamera();
                    else if (which == 1) galleryLauncher.launch("image/*");
                    else fileLauncher.launch("*/*");
                })
                .show();
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) launchCamera();
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "captures");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onImagePicked(Uri uri) {
        if (LOGO_TARGET.equals(pendingTarget)) showCropDialog(uri);
        else applyDocument(pendingTarget, uri);
    }

    private void applyDocument(String key, Uri uri) {
        if (key == null) return;
        docUris.put(key, uri);
        TextView sub = docSubtitleViews.get(key);
        TextView btn = docButtons.get(key);
        String name = queryFileName(uri);
        if (sub != null && name != null) {
            sub.setVisibility(View.VISIBLE);
            sub.setText(name);
            sub.setTextColor(ContextCompat.getColor(this, R.color.success));
        }
        if (btn != null) btn.setText(R.string.replace);
    }

    private void showCropDialog(Uri uri) {
        Bitmap bitmap = decodeBitmap(uri, 1500);
        if (bitmap == null) { toast(getString(R.string.err_documents)); return; }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crop_logo, null);
        LogoCropView cropView = view.findViewById(R.id.cropView);
        cropView.setBitmap(bitmap);
        android.widget.SeekBar scale = view.findViewById(R.id.cropScale);
        android.widget.SeekBar rotation = view.findViewById(R.id.cropRotation);
        scale.setProgress(50);
        rotation.setProgress(0);
        scale.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean u) { cropView.setUserScale(0.5f + p / 100f); }
        });
        rotation.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean u) { cropView.setRotationDegrees(p); }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        view.findViewById(R.id.cropClose).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.cropCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.cropUploadNew).setOnClickListener(v -> { dialog.dismiss(); chooseImageSource(LOGO_TARGET, false); });
        view.findViewById(R.id.cropOk).setOnClickListener(v -> {
            Bitmap cropped = cropView.getCroppedBitmap();
            if (cropped != null) {
                logoBitmap = cropped;
                logoPreview.setImageBitmap(cropped);
                logoPreview.setVisibility(View.VISIBLE);
                logoPlaceholder.setVisibility(View.GONE);
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private abstract static class SimpleSeekBarListener
            implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
    }

    @Nullable
    private Bitmap decodeBitmap(Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / sample > maxDim) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) { return null; }
    }

    // ---- Address autocomplete + map -------------------------------------

    private void setupAddressAutocomplete() {
        EditText field = findViewById(R.id.etAddressSearch);
        addressPopup = new android.widget.ListPopupWindow(this);
        addressPopup.setAnchorView(field);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (suppressAddressWatcher) return;
                String keyword = s.toString().trim();
                if (autocompleteRunnable != null) autocompleteHandler.removeCallbacks(autocompleteRunnable);
                if (keyword.length() < 3) { addressPopup.dismiss(); return; }
                autocompleteRunnable = () -> queryAutocomplete(keyword);
                autocompleteHandler.postDelayed(autocompleteRunnable, 350);
            }
        });
    }

    private void queryAutocomplete(String keyword) {
        api.getAddressAutocomplete(keyword, new ApiCallback<AutocompleteData>() {
            @Override public void onSuccess(AutocompleteData data) {
                predictions.clear();
                if (data != null && data.predictions != null) predictions.addAll(data.predictions);
                showPredictions();
            }
            @Override public void onError(String message) { }
        });
    }

    private void showPredictions() {
        if (predictions.isEmpty()) { addressPopup.dismiss(); return; }
        List<String> labels = new ArrayList<>();
        for (AutocompleteData.Prediction p : predictions) labels.add(p.description);
        addressPopup.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));
        addressPopup.setOnItemClickListener((parent, view, position, id) -> {
            addressPopup.dismiss();
            AutocompleteData.Prediction p = predictions.get(position);
            setAddressText(p.description);
            resolvePlace(p.place_id);
        });
        addressPopup.show();
    }

    private void resolvePlace(String placeId) {
        api.getCoordinateFromPlaceId(placeId, new ApiCallback<CoordinateData>() {
            @Override public void onSuccess(CoordinateData data) {
                if (data == null) return;
                pickedLat = data.latitude; pickedLng = data.longitude;
                if (data.formatted_address != null && !data.formatted_address.isEmpty()) setAddressText(data.formatted_address);
                moveMapTo(new LatLng(data.latitude, data.longitude), true);
            }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void setAddressText(String value) {
        if (value == null) return;
        suppressAddressWatcher = true;
        EditText field = findViewById(R.id.etAddressSearch);
        field.setText(value);
        field.setSelection(value.length());
        suppressAddressWatcher = false;
    }

    private void setupMap(Bundle savedInstanceState) {
        businessMap = findViewById(R.id.businessMap);
        businessMap.onCreate(savedInstanceState);
        businessMap.getMapAsync(this);
        final NestedScrollView scroll = findViewById(R.id.locationScroll);
        businessMap.setOnTouchListener((v, ev) -> {
            int action = ev.getActionMasked();
            boolean holding = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
            scroll.requestDisallowInterceptTouchEvent(holding);
            return false;
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setOnMapClickListener(latLng -> {
            pickedLat = latLng.latitude; pickedLng = latLng.longitude;
            placeMarker(latLng);
            reverseGeocode(latLng);
        });
        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker m) { }
            @Override public void onMarkerDrag(Marker m) { }
            @Override public void onMarkerDragEnd(Marker m) {
                pickedLat = m.getPosition().latitude; pickedLng = m.getPosition().longitude;
                reverseGeocode(m.getPosition());
            }
        });
        if (pickedLat != null && pickedLng != null) moveMapTo(new LatLng(pickedLat, pickedLng), true);
        else googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-26.2041, 28.0473), 9f));
    }

    private void moveMapTo(LatLng latLng, boolean placePin) {
        if (googleMap == null) return;
        if (placePin) placeMarker(latLng);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    private void placeMarker(LatLng latLng) {
        if (googleMap == null) return;
        if (marker == null) marker = googleMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
        else marker.setPosition(latLng);
    }

    private void reverseGeocode(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (results != null && !results.isEmpty() && results.get(0).getMaxAddressLineIndex() >= 0) {
                    final String line = results.get(0).getAddressLine(0);
                    runOnUiThread(() -> setAddressText(line));
                }
            } catch (Exception ignored) { }
        }).start();
    }

    // ---- File helpers ----------------------------------------------------

    private Http.FilePart logoFilePart() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return new Http.FilePart("company_logo", "company_logo.png", "image/png", out.toByteArray());
    }

    @Nullable
    private Http.FilePart readFilePart(String field, Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new Http.FilePart(field, queryFileName(uri) != null ? queryFileName(uri) : field,
                    getContentResolver().getType(uri), out.toByteArray());
        } catch (Exception e) { return null; }
    }

    private long fileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    @Nullable
    private String queryFileName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    // ---- Small helpers ---------------------------------------------------

    private String text(int id) {
        TextView tv = findViewById(id);
        return tv.getText() == null ? "" : tv.getText().toString().trim();
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value == null ? "" : value);
    }

    private String etText(View parent, int id) {
        EditText e = parent.findViewById(id);
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private List<Long> parseIds(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) { try { out.add(Long.parseLong(t)); } catch (NumberFormatException ignored) { } }
            }
        }
        return out;
    }

    private long parseLong(String s, long fallback) {
        try { return s == null ? fallback : Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String joinLongs(List<Long> values) {
        StringBuilder sb = new StringBuilder();
        for (Long v : values) { if (sb.length() > 0) sb.append(','); sb.append(v); }
        return sb.toString();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String msg) {
        Toast.makeText(this, msg == null ? "Something went wrong." : msg, Toast.LENGTH_SHORT).show();
    }

    // ---- MapView lifecycle ----------------------------------------------

    @Override protected void onStart() { super.onStart(); if (businessMap != null) businessMap.onStart(); }
    @Override protected void onResume() { super.onResume(); if (businessMap != null) businessMap.onResume(); }
    @Override protected void onPause() { if (businessMap != null) businessMap.onPause(); super.onPause(); }
    @Override protected void onStop() { if (businessMap != null) businessMap.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (businessMap != null) businessMap.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (businessMap != null) businessMap.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (businessMap != null && outState != null) businessMap.onSaveInstanceState(outState);
    }
}
