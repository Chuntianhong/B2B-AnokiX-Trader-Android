package com.anokix.traderapp.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.Http;
import com.anokix.traderapp.network.dto.AutocompleteData;
import com.anokix.traderapp.network.dto.CoordinateData;
import com.anokix.traderapp.network.dto.ReferenceData;
import com.anokix.traderapp.network.dto.RegisterTraderData;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eight-step public trader self-registration ("Create Account") wired to
 * POST /api/trader/register.
 *
 * Steps: 1 Owner · 2 Business · 3 Type · 4 Location · 5 Verify ·
 *        6 Distributor · 7 Wallet · 8 Terms.
 *
 * Every step lives inside a {@link ViewFlipper}, so field values survive
 * forward/back navigation. Trader types, product categories and distributors
 * are loaded from the public {@link ApiClient#getReference} endpoint. On success
 * the user lands on {@link RegisterSubmittedActivity}.
 *
 * Field names follow the real web payload (Chrome multipart dump) where it
 * differs from the Postman collection: {@code email}, numeric {@code trader_type},
 * comma-joined id lists and {@code preferred_delivery_days} as "Mon,Wed,Fri".
 */
public class RegisterTraderActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int STEP_COUNT = 8;
    private static final int STEP_OWNER = 0;
    private static final int STEP_BUSINESS = 1;
    private static final int STEP_TYPE = 2;
    private static final int STEP_LOCATION = 3;
    private static final int STEP_VERIFY = 4;
    private static final int STEP_DISTRIBUTOR = 5;
    private static final int STEP_WALLET = 6;
    private static final int STEP_TERMS = 7;

    private static final int[] STEP_LABELS = {
            R.string.step_owner, R.string.step_business, R.string.step_trader,
            R.string.step_location, R.string.step_verify, R.string.step_distributor,
            R.string.step_wallet, R.string.step_terms};

    private static final String LOGO_TARGET = "__logo__";

    private static final long MAX_DOC_BYTES = 5L * 1024 * 1024; // 5MB per Accepted formats note

    // Delivery days in the order shown / submitted (Mon-first). Defaults follow the design.
    // Delivery grid rows in payload-index order: 0=Sun..6=Sat (matches the Business editor).
    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final boolean[] DAYS_DEFAULT_ON = {false, true, false, true, false, true, false}; // Mon/Wed/Fri on

    private static final String[] PAYMENT_LABELS = {"Wallet", "EFT", "Cash", "Card"};
    private static final String[] PAYMENT_CODES = {"wallet", "eft", "cash", "card"};

    // Fallback trader classifications (design order) if the reference API is unavailable.
    private static final String[] FALLBACK_TRADER_TYPES = {
            "Spaza Shop", "Mini Market", "Superette", "General Dealer",
            "Wholesaler", "Pharmacy", "Butchery", "Restaurant"};

    private ViewFlipper flipper;
    private LinearLayout stepperContainer;
    private TextView btnContinue;
    private TextView btnBack;

    private final TextView[] stepCircles = new TextView[STEP_COUNT];
    private final TextView[] stepLabels = new TextView[STEP_COUNT];
    private int currentStep = 0;

    private boolean passwordVisible;
    private boolean confirmVisible;

    private CountryCodePicker ccpMobile;
    private CountryCodePicker ccpBusinessPhone;

    // Trader types
    private final List<ReferenceData.TraderType> traderTypes = new ArrayList<>();
    private long selectedTraderTypeId = -1;
    private final List<View> traderTypeCards = new ArrayList<>();
    private final List<Long> traderTypeCardIds = new ArrayList<>();

    // Reference data / categories / distributors
    private final List<ReferenceData.ProductCategory> primaryCategories = new ArrayList<>();
    private final List<Long> selectedCategoryIds = new ArrayList<>();
    private final List<ReferenceData.Distributor> distributors = new ArrayList<>();
    private final List<Long> selectedDistributorIds = new ArrayList<>();

    // Delivery days
    private final List<View> dayRows = new ArrayList<>();

    // Payment method
    private String paymentCode = PAYMENT_CODES[0];

    // Logo
    private LinearLayout logoPlaceholder;
    private ImageView logoPreview;
    private Bitmap logoBitmap;

    // Documents
    private static final class DocDef {
        final String key, title, sub;
        final boolean required;
        DocDef(String key, String title, String sub, boolean required) {
            this.key = key; this.title = title; this.sub = sub; this.required = required;
        }
    }
    private final List<DocDef> docDefs = new ArrayList<>();
    private final Map<String, Uri> docUris = new LinkedHashMap<>();
    private final Map<String, TextView> docSubtitleViews = new LinkedHashMap<>();
    private final Map<String, TextView> docButtons = new LinkedHashMap<>();

    // Wallet
    private boolean createWalletNow = true;

    // Wallet › Owner identity + residential address (only sent when "Create Wallet Now")
    private static final String[] ID_TYPE_LABELS = {"SA ID Number", "Passport", "Asylum Document"};
    private static final String[] ID_TYPE_CODES = {"ID", "passport", "asylum"};
    private static final String[] GENDER_VALUES = {"Male", "Female", "Other"};
    // Province display names + ISO 3166-2:ZA codes used by the payload (e.g. "ZA-FS").
    private static final String[] PROVINCE_LABELS = {
            "Eastern Cape", "Free State", "Gauteng", "Limpopo", "Mpumalanga",
            "Northern Cape", "Kwa-Zulu Natal", "North West", "Western Cape"};
    private static final String[] PROVINCE_CODES = {
            "ZA-EC", "ZA-FS", "ZA-GP", "ZA-LP", "ZA-MP",
            "ZA-NC", "ZA-KZN", "ZA-NW", "ZA-WC"};

    private String walletIdTypeCode;   // "ID" / "passport" / "asylum"
    private String walletGender;       // "Male" / "Female" / "Other"
    private String walletProvinceCode; // e.g. "ZA-FS"
    private String walletDobIso;       // yyyy-MM-dd
    private String walletPassportExpIso; // yyyy-MM-dd
    private boolean walletUssd = true;
    private boolean walletInternet = true;
    private boolean walletCrossBorder = false;
    private boolean walletSaIdValid;   // true once a valid 13-digit SA ID is parsed

    private final SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat readableFmt = new SimpleDateFormat("MM/dd/yyyy", Locale.US);

    // Image / file pickers
    private String pendingTarget;          // LOGO_TARGET or a document key
    private Uri pendingCameraUri;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> fileLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // Map
    private MapView registerMap;
    private GoogleMap googleMap;
    private Marker marker;
    private Double pickedLat, pickedLng;

    // Address autocomplete
    private final Handler autocompleteHandler = new Handler(Looper.getMainLooper());
    private Runnable autocompleteRunnable;
    private boolean suppressAddressWatcher;
    private android.widget.ListPopupWindow addressPopup;
    private final List<AutocompleteData.Prediction> predictions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_trader);

        flipper = findViewById(R.id.stepFlipper);
        stepperContainer = findViewById(R.id.stepperContainer);
        btnContinue = findViewById(R.id.btnContinue);
        btnBack = findViewById(R.id.btnBack);
        logoPlaceholder = findViewById(R.id.logoPlaceholder);
        logoPreview = findViewById(R.id.logoPreview);

        buildStepper();
        buildBenefits();
        buildDocumentRows();
        buildDeliveryGrid();
        buildTraderTypeCards(); // fallback cards until reference data arrives
        setupCountryPickers();
        setupPasswordToggles();
        setupLogoUpload();
        setupCategoryField();
        setupDistributorField();
        setupPaymentMethodField();
        setupWalletOptions();
        setupWalletFields();
        setupNavigation();
        setupPickers();
        setupAddressAutocomplete();
        setupMap(savedInstanceState);

        showStep(0);
        loadReference();
    }

    // ---- Stepper ---------------------------------------------------------

    private void buildStepper() {
        for (int i = 0; i < STEP_COUNT; i++) {
            if (i > 0) {
                View connector = new View(this);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(20), dp(1));
                clp.setMargins(dp(6), 0, dp(6), 0);
                connector.setLayoutParams(clp);
                connector.setBackgroundColor(ContextCompat.getColor(this, R.color.border));
                stepperContainer.addView(connector);
            }

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);

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
            TextView circle = stepCircles[i];
            TextView label = stepLabels[i];
            if (i < currentStep) {
                circle.setBackgroundResource(R.drawable.bg_step_circle_active);
                circle.setText("✓");
                circle.setTextColor(0xFFFFFFFF);
                label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            } else if (i == currentStep) {
                circle.setBackgroundResource(R.drawable.bg_step_circle_active);
                circle.setText(String.valueOf(i + 1));
                circle.setTextColor(0xFFFFFFFF);
                label.setTextColor(ContextCompat.getColor(this, R.color.purple_primary));
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                circle.setBackgroundResource(R.drawable.bg_step_circle_inactive);
                circle.setText(String.valueOf(i + 1));
                circle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                label.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    // ---- Navigation ------------------------------------------------------

    private void setupNavigation() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> {
            if (currentStep == 0) {
                finish();
            } else {
                showStep(currentStep - 1);
            }
        });
        btnContinue.setOnClickListener(v -> {
            if (!validateStep(currentStep)) {
                return;
            }
            if (currentStep == STEP_TERMS) {
                submit();
            } else {
                showStep(currentStep + 1);
            }
        });
    }

    private void showStep(int index) {
        currentStep = index;
        flipper.setDisplayedChild(index);
        btnContinue.setText(index == STEP_TERMS ? R.string.register_btn : R.string.continue_btn);
        renderStepper();
        stepperContainer.post(() -> {
            View item = stepLabels[index];
            if (item != null && item.getParent() instanceof View) {
                View parent = (View) item.getParent();
                ((android.widget.HorizontalScrollView) stepperContainer.getParent())
                        .smoothScrollTo(Math.max(0, parent.getLeft() - dp(40)), 0);
            }
        });
    }

    // ---- Validation ------------------------------------------------------

    private boolean validateStep(int step) {
        switch (step) {
            case STEP_OWNER:
                if (text(R.id.etFirstName).isEmpty()) return fail(R.string.err_first_name);
                if (text(R.id.etLastName).isEmpty()) return fail(R.string.err_last_name);
                if (text(R.id.etIdNumber).isEmpty() && text(R.id.etPassportNumber).isEmpty()) {
                    return fail(R.string.err_id_or_passport);
                }
                if (text(R.id.etMobile).isEmpty()) return fail(R.string.err_mobile);
                if (!isValidEmail(text(R.id.etEmail))) return fail(R.string.err_email);
                if (text(R.id.etPassword).length() < 8) return fail(R.string.err_password_length);
                if (!text(R.id.etPassword).equals(text(R.id.etConfirmPassword))) {
                    return fail(R.string.err_password_match);
                }
                return true;
            case STEP_BUSINESS:
                if (text(R.id.etBusinessName).isEmpty()) return fail(R.string.err_business_name);
                if (!isValidEmail(text(R.id.etBusinessEmail))) return fail(R.string.err_business_email);
                return true;
            case STEP_TYPE:
                if (selectedTraderTypeId < 0) return fail(R.string.err_trader_type);
                if (selectedCategoryIds.isEmpty()) return fail(R.string.err_category);
                return true;
            case STEP_LOCATION:
                if (pickedLat == null || pickedLng == null) return fail(R.string.err_location);
                return true;
            case STEP_VERIFY:
                for (DocDef d : docDefs) {
                    if (d.required && !docUris.containsKey(d.key)) {
                        return fail(R.string.err_documents);
                    }
                }
                for (Map.Entry<String, Uri> e : docUris.entrySet()) {
                    if (fileSize(e.getValue()) > MAX_DOC_BYTES) {
                        return fail(R.string.err_doc_too_large);
                    }
                }
                return true;
            case STEP_DISTRIBUTOR:
                if (selectedDistributorIds.isEmpty()) return fail(R.string.err_distributors);
                if (!anyDaySelected()) return fail(R.string.err_delivery_days);
                if (paymentCode == null) return fail(R.string.err_payment_method);
                return true;
            case STEP_WALLET:
                return validateWallet();
            case STEP_TERMS:
                if (!checked(R.id.cbAccurate) || !checked(R.id.cbTos)
                        || !checked(R.id.cbKyc) || !checked(R.id.cbComms)) {
                    return fail(R.string.err_terms);
                }
                return true;
            default:
                return true;
        }
    }

    private boolean fail(int msgRes) {
        Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
        return false;
    }

    /** Wallet step: nothing required when "Activate Later"; full identity + address otherwise. */
    private boolean validateWallet() {
        if (!createWalletNow) {
            return true;
        }
        if (walletIdTypeCode == null) return fail(R.string.err_wallet_id_type);
        String idNumber = text(R.id.etWalletIdNumber);
        if (idNumber.isEmpty()) return fail(R.string.err_wallet_id_number);
        if ("ID".equals(walletIdTypeCode) && !walletSaIdValid) {
            return fail(R.string.err_wallet_sa_id);
        }
        if ("passport".equals(walletIdTypeCode)) {
            if (text(R.id.etWalletPassportCountry).isEmpty()) {
                return fail(R.string.err_wallet_passport_country);
            }
            if (walletPassportExpIso == null) return fail(R.string.err_wallet_passport_exp);
        }
        if (walletDobIso == null) return fail(R.string.err_wallet_dob);
        if (walletGender == null) return fail(R.string.err_wallet_gender);
        if (text(R.id.etWalletStreet).isEmpty()) return fail(R.string.err_wallet_street);
        if (text(R.id.etWalletSuburb).isEmpty()) return fail(R.string.err_wallet_suburb);
        if (text(R.id.etWalletCity).isEmpty()) return fail(R.string.err_wallet_city);
        if (walletProvinceCode == null) return fail(R.string.err_wallet_province);
        if (text(R.id.etWalletPostCode).isEmpty()) return fail(R.string.err_wallet_post_code);
        return true;
    }

    // ---- Phone pickers / password ---------------------------------------

    private void setupCountryPickers() {
        ccpMobile = findViewById(R.id.ccpMobile);
        ccpBusinessPhone = findViewById(R.id.ccpBusinessPhone);
        ccpMobile.registerCarrierNumberEditText(findViewById(R.id.etMobile));
        ccpBusinessPhone.registerCarrierNumberEditText(findViewById(R.id.etBusinessPhone));
    }

    /** Country code + national number, no leading '+' — matches the web payload ("27133133133"). */
    private String fullPhone(CountryCodePicker ccp, int numberFieldId) {
        return text(numberFieldId).isEmpty() ? "" : ccp.getFullNumber();
    }

    private void setupPasswordToggles() {
        EditText pwd = findViewById(R.id.etPassword);
        EditText confirm = findViewById(R.id.etConfirmPassword);
        pwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
        confirm.setTransformationMethod(PasswordTransformationMethod.getInstance());

        ImageButton togglePwd = findViewById(R.id.togglePassword);
        ImageButton toggleConfirm = findViewById(R.id.toggleConfirmPassword);
        togglePwd.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            pwd.setTransformationMethod(passwordVisible
                    ? null : PasswordTransformationMethod.getInstance());
            togglePwd.setImageResource(passwordVisible
                    ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            pwd.setSelection(pwd.getText() != null ? pwd.getText().length() : 0);
        });
        toggleConfirm.setOnClickListener(v -> {
            confirmVisible = !confirmVisible;
            confirm.setTransformationMethod(confirmVisible
                    ? null : PasswordTransformationMethod.getInstance());
            toggleConfirm.setImageResource(confirmVisible
                    ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            confirm.setSelection(confirm.getText() != null ? confirm.getText().length() : 0);
        });
    }

    // ---- Trader type cards ----------------------------------------------

    private void buildTraderTypeCards() {
        LinearLayout container = findViewById(R.id.traderTypeContainer);
        container.removeAllViews();
        traderTypeCards.clear();
        traderTypeCardIds.clear();

        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (!traderTypes.isEmpty()) {
            for (ReferenceData.TraderType t : traderTypes) {
                names.add(t.name);
                ids.add(t.id);
            }
        } else {
            for (int i = 0; i < FALLBACK_TRADER_TYPES.length; i++) {
                names.add(FALLBACK_TRADER_TYPES[i]);
                ids.add((long) (i + 1));
            }
        }

        final int columns = 3;
        LinearLayout row = null;
        for (int i = 0; i < names.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(10);
                row.setLayoutParams(rlp);
                container.addView(row);
            }
            row.addView(buildTraderTypeCard(names.get(i), ids.get(i)));
        }
        // Pad the final row so cards keep a consistent width.
        if (row != null) {
            int remainder = names.size() % columns;
            if (remainder != 0) {
                for (int j = remainder; j < columns; j++) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                            0, 1, 1f);
                    sp.setMargins(dp(4), 0, dp(4), 0);
                    spacer.setLayoutParams(sp);
                    row.addView(spacer);
                }
            }
        }
        refreshTraderTypeSelection();
    }

    private View buildTraderTypeCard(String name, long id) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);
        card.setMinimumHeight(dp(72));
        card.setPadding(dp(8), dp(14), dp(8), dp(14));
        card.setBackgroundResource(R.drawable.bg_trader_type_card);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        icon.setImageResource(R.drawable.ic_store);
        card.addView(icon);

        RobotoTextView label = new RobotoTextView(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(6);
        label.setLayoutParams(llp);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12);
        card.addView(label);

        card.setOnClickListener(v -> {
            selectedTraderTypeId = id;
            refreshTraderTypeSelection();
        });

        traderTypeCards.add(card);
        traderTypeCardIds.add(id);
        return card;
    }

    private void refreshTraderTypeSelection() {
        for (int i = 0; i < traderTypeCards.size(); i++) {
            boolean selected = traderTypeCardIds.get(i) == selectedTraderTypeId;
            View card = traderTypeCards.get(i);
            card.setBackgroundResource(selected
                    ? R.drawable.bg_trader_type_card_selected : R.drawable.bg_trader_type_card);
            int color = ContextCompat.getColor(this,
                    selected ? R.color.purple_primary : R.color.text_secondary);
            ((ImageView) ((LinearLayout) card).getChildAt(0)).setColorFilter(color);
            ((TextView) ((LinearLayout) card).getChildAt(1)).setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.purple_primary : R.color.text_primary));
        }
    }

    // ---- Categories ------------------------------------------------------

    private void setupCategoryField() {
        findViewById(R.id.categoryField).setOnClickListener(v -> showCategoryDialog());
    }

    private void showCategoryDialog() {
        if (primaryCategories.isEmpty()) {
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            return;
        }
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
                    if (isChecked) {
                        if (!selectedCategoryIds.contains(id)) selectedCategoryIds.add(id);
                    } else {
                        selectedCategoryIds.remove(id);
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> renderCategoryChips())
                .show();
    }

    private void renderCategoryChips() {
        FlowLayout chips = findViewById(R.id.categoryChips);
        chips.removeAllViews();
        findViewById(R.id.categoryHint).setVisibility(
                selectedCategoryIds.isEmpty() ? View.VISIBLE : View.GONE);
        for (ReferenceData.ProductCategory cat : primaryCategories) {
            if (!selectedCategoryIds.contains(cat.id)) {
                continue;
            }
            chips.addView(buildChip(chips, cat.title, () -> {
                selectedCategoryIds.remove(cat.id);
                renderCategoryChips();
            }));
        }
    }

    // ---- Distributors ----------------------------------------------------

    private void setupDistributorField() {
        findViewById(R.id.distributorField).setOnClickListener(v -> showDistributorDialog());
    }

    private void showDistributorDialog() {
        if (distributors.isEmpty()) {
            Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            return;
        }
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
                    if (isChecked) {
                        if (!selectedDistributorIds.contains(id)) selectedDistributorIds.add(id);
                    } else {
                        selectedDistributorIds.remove(id);
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> renderDistributorChips())
                .show();
    }

    private void renderDistributorChips() {
        FlowLayout chips = findViewById(R.id.distributorChips);
        chips.removeAllViews();
        findViewById(R.id.distributorHint).setVisibility(
                selectedDistributorIds.isEmpty() ? View.VISIBLE : View.GONE);
        for (ReferenceData.Distributor dist : distributors) {
            if (!selectedDistributorIds.contains(dist.id)) {
                continue;
            }
            chips.addView(buildChip(chips, dist.displayName(), () -> {
                selectedDistributorIds.remove(dist.id);
                renderDistributorChips();
            }));
        }
    }

    private View buildChip(ViewGroup parent, String label, Runnable onRemove) {
        View chip = LayoutInflater.from(this).inflate(R.layout.item_tag_chip, parent, false);
        ((TextView) chip.findViewById(R.id.tagText)).setText(label);
        chip.findViewById(R.id.tagRemove).setOnClickListener(v -> onRemove.run());
        return chip;
    }

    // ---- Delivery days ---------------------------------------------------

    /** Build the per-day delivery-hours grid (checkbox + two open/close windows),
     *  matching the Business editor. Mon/Wed/Fri are ticked by default. */
    private void buildDeliveryGrid() {
        LinearLayout container = findViewById(R.id.deliveryGridContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < DAY_NAMES.length; i++) {
            View row = inflater.inflate(R.layout.item_delivery_day, container, false);
            CheckBox cb = row.findViewById(R.id.cbDay);
            cb.setText(DAY_NAMES[i]);
            cb.setChecked(DAYS_DEFAULT_ON[i]);
            container.addView(row);
            dayRows.add(row);
        }
    }

    /** Selected-day abbreviations only, e.g. "Mon,Wed,Fri" (no hours): the captured
     *  api/trader/register payload sends plain CSV, so registration submits days only
     *  even though the hours grid is shown. (The Business editor still sends full hours.) */
    private String selectedDaysCsv() {
        List<String> sel = new ArrayList<>();
        for (int i = 0; i < dayRows.size(); i++) {
            if (((CheckBox) dayRows.get(i).findViewById(R.id.cbDay)).isChecked()) sel.add(DAY_NAMES[i]);
        }
        return TextUtils.join(",", sel);
    }

    private boolean anyDaySelected() {
        for (View row : dayRows) {
            if (((CheckBox) row.findViewById(R.id.cbDay)).isChecked()) return true;
        }
        return false;
    }

    // ---- Payment method --------------------------------------------------

    private void setupPaymentMethodField() {
        TextView label = findViewById(R.id.paymentMethodLabel);
        findViewById(R.id.paymentMethodField).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.select_payment_method)
                        .setItems(PAYMENT_LABELS, (d, which) -> {
                            paymentCode = PAYMENT_CODES[which];
                            label.setText(PAYMENT_LABELS[which]);
                            label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                        })
                        .show());
    }

    // ---- Wallet ----------------------------------------------------------

    private void setupWalletOptions() {
        findViewById(R.id.optionCreateWallet).setOnClickListener(v -> setWallet(true));
        findViewById(R.id.optionActivateLater).setOnClickListener(v -> setWallet(false));
        setWallet(true);
    }

    private void setWallet(boolean now) {
        createWalletNow = now;
        ((ImageView) findViewById(R.id.radioCreateWallet)).setImageResource(
                now ? R.drawable.ic_radio_selected : R.drawable.ic_radio_unselected);
        ((ImageView) findViewById(R.id.radioActivateLater)).setImageResource(
                now ? R.drawable.ic_radio_unselected : R.drawable.ic_radio_selected);
        // "Activate Later" hides the whole identity/address panel (design Register7-new4).
        findViewById(R.id.walletInfoPanel).setVisibility(now ? View.VISIBLE : View.GONE);
    }

    // ---- Wallet identity + residential address --------------------------

    private void setupWalletFields() {
        // ID Type picker.
        findViewById(R.id.walletIdTypeField).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.wallet_id_type)
                        .setItems(ID_TYPE_LABELS, (d, which) -> {
                            walletIdTypeCode = ID_TYPE_CODES[which];
                            TextView label = findViewById(R.id.walletIdTypeLabel);
                            label.setText(ID_TYPE_LABELS[which]);
                            label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                            applyIdType();
                        })
                        .show());

        // Live SA-ID validation + auto DOB/gender derivation.
        ((EditText) findViewById(R.id.etWalletIdNumber)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if ("ID".equals(walletIdTypeCode)) {
                    validateSaId(e.toString().trim());
                }
            }
        });

        // Date pickers (guarded so SA-ID DOB stays read-only/auto).
        findViewById(R.id.walletDobField).setOnClickListener(v -> {
            if ("ID".equals(walletIdTypeCode)) return; // auto-filled from the ID number
            pickDate(walletDobIso, iso -> {
                walletDobIso = iso;
                setFieldValue(R.id.walletDobLabel, readable(iso));
            });
        });
        findViewById(R.id.walletPassportExpField).setOnClickListener(v ->
                pickDate(walletPassportExpIso, iso -> {
                    walletPassportExpIso = iso;
                    setFieldValue(R.id.walletPassportExpLabel, readable(iso));
                }));

        // Gender picker (guarded so SA-ID gender stays read-only/auto).
        findViewById(R.id.walletGenderField).setOnClickListener(v -> {
            if ("ID".equals(walletIdTypeCode)) return;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.wallet_gender)
                    .setItems(GENDER_VALUES, (d, which) -> {
                        walletGender = GENDER_VALUES[which];
                        setFieldValue(R.id.walletGenderLabel, walletGender);
                    })
                    .show();
        });

        // Province picker.
        findViewById(R.id.walletProvinceField).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.wallet_province)
                        .setItems(PROVINCE_LABELS, (d, which) -> {
                            walletProvinceCode = PROVINCE_CODES[which];
                            setFieldValue(R.id.walletProvinceLabel, PROVINCE_LABELS[which]);
                        })
                        .show());

        // Wallet-access toggles.
        findViewById(R.id.walletUssdTile).setOnClickListener(v -> {
            walletUssd = !walletUssd;
            renderAccessTile(R.id.walletUssdTile, R.id.walletUssdCheck, walletUssd);
        });
        findViewById(R.id.walletInternetTile).setOnClickListener(v -> {
            walletInternet = !walletInternet;
            renderAccessTile(R.id.walletInternetTile, R.id.walletInternetCheck, walletInternet);
        });
        findViewById(R.id.walletCrossBorderTile).setOnClickListener(v -> {
            walletCrossBorder = !walletCrossBorder;
            renderAccessTile(R.id.walletCrossBorderTile, R.id.walletCrossBorderCheck, walletCrossBorder);
        });
    }

    /** Reconfigures the identity fields when the ID type changes. */
    private void applyIdType() {
        boolean saId = "ID".equals(walletIdTypeCode);
        boolean passport = "passport".equals(walletIdTypeCode);

        EditText idInput = findViewById(R.id.etWalletIdNumber);
        TextView idLabel = findViewById(R.id.walletIdNumberLabel);
        if (saId) {
            idLabel.setText(R.string.wallet_sa_id_number);
            idInput.setHint(R.string.wallet_hint_sa_id);
            idInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            idInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(13)});
            // Pre-fill from the owner ID captured in step 1 (requirement).
            if (idInput.getText().toString().trim().isEmpty()) {
                String ownerId = text(R.id.etIdNumber);
                if (!ownerId.isEmpty()) idInput.setText(ownerId);
            }
        } else {
            idLabel.setText(passport ? R.string.wallet_passport_number : R.string.wallet_asylum_number);
            idInput.setHint(R.string.wallet_hint_document_number);
            idInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            idInput.setFilters(new android.text.InputFilter[0]);
        }

        findViewById(R.id.walletPassportGroup).setVisibility(passport ? View.VISIBLE : View.GONE);

        // DOB + gender are derived (read-only) for SA ID, manual otherwise.
        setAutoField(R.id.walletDobField, R.id.walletDobLabel, !saId);
        setAutoField(R.id.walletGenderField, R.id.walletGenderLabel, !saId);

        if (saId) {
            validateSaId(idInput.getText().toString().trim());
        } else {
            // Leaving SA ID clears the derived values + validation UI.
            walletSaIdValid = false;
            findViewById(R.id.walletIdError).setVisibility(View.GONE);
            findViewById(R.id.walletIdHint).setVisibility(View.GONE);
            walletDobIso = null;
            walletGender = null;
            resetFieldValue(R.id.walletDobLabel, R.string.wallet_select_date);
            resetFieldValue(R.id.walletGenderLabel, R.string.wallet_select_gender);
        }
    }

    /** Validates a South African ID and, when valid, fills DOB + gender. */
    private void validateSaId(String id) {
        TextView error = findViewById(R.id.walletIdError);
        TextView hint = findViewById(R.id.walletIdHint);
        walletSaIdValid = false;
        walletDobIso = null;
        walletGender = null;

        if (id.isEmpty()) {
            error.setVisibility(View.GONE);
            hint.setVisibility(View.GONE);
            resetFieldValue(R.id.walletDobLabel, R.string.wallet_select_date);
            resetFieldValue(R.id.walletGenderLabel, R.string.wallet_select_gender);
            return;
        }

        if (id.length() != 13 || !id.matches("\\d{13}")) {
            showIdError(R.string.wallet_err_id_length);
            return;
        }
        String dobIso = saIdDob(id);
        if (dobIso == null) {
            showIdError(R.string.wallet_err_id_date);
            return;
        }
        if (!luhnValid(id)) {
            showIdError(R.string.wallet_err_id_checksum);
            return;
        }

        // Valid: derive DOB + gender.
        walletSaIdValid = true;
        walletDobIso = dobIso;
        int seq = Integer.parseInt(id.substring(6, 10));
        walletGender = seq >= 5000 ? "Male" : "Female";
        error.setVisibility(View.GONE);
        hint.setVisibility(View.VISIBLE);
        setFieldValue(R.id.walletDobLabel, readable(dobIso));
        setFieldValue(R.id.walletGenderLabel, walletGender);
    }

    private void showIdError(int msgRes) {
        TextView error = findViewById(R.id.walletIdError);
        error.setText(msgRes);
        error.setVisibility(View.VISIBLE);
        findViewById(R.id.walletIdHint).setVisibility(View.GONE);
        resetFieldValue(R.id.walletDobLabel, R.string.wallet_select_date);
        resetFieldValue(R.id.walletGenderLabel, R.string.wallet_select_gender);
    }

    /** Returns yyyy-MM-dd for a valid YYMMDD prefix, or null if the date is invalid. */
    @Nullable
    private String saIdDob(String id) {
        int yy = Integer.parseInt(id.substring(0, 2));
        int mm = Integer.parseInt(id.substring(2, 4));
        int dd = Integer.parseInt(id.substring(4, 6));
        if (mm < 1 || mm > 12 || dd < 1 || dd > 31) return null;
        int currentYy = Calendar.getInstance().get(Calendar.YEAR) % 100;
        int year = yy > currentYy ? 1900 + yy : 2000 + yy;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.setLenient(false);
        c.set(year, mm - 1, dd);
        try {
            c.getTime(); // triggers validation (e.g. rejects 31 Feb)
        } catch (Exception e) {
            return null;
        }
        return isoFmt.format(c.getTime());
    }

    private boolean luhnValid(String num) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = num.length() - 1; i >= 0; i--) {
            int d = num.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private interface DatePicked { void onPicked(String iso); }

    private void pickDate(@Nullable String currentIso, DatePicked cb) {
        Calendar c = Calendar.getInstance();
        if (currentIso != null) {
            try {
                c.setTime(isoFmt.parse(currentIso));
            } catch (Exception ignored) {
            }
        }
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(year, month, day);
            cb.onPicked(isoFmt.format(picked.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String readable(String iso) {
        try {
            return readableFmt.format(isoFmt.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private void setFieldValue(int labelId, String value) {
        TextView label = findViewById(labelId);
        label.setText(value);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void resetFieldValue(int labelId, int hintRes) {
        TextView label = findViewById(labelId);
        label.setText(hintRes);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    /** Toggles a dropdown-style field between editable and derived/read-only. */
    private void setAutoField(int fieldId, int labelId, boolean editable) {
        View field = findViewById(fieldId);
        field.setClickable(editable);
        field.setAlpha(editable ? 1f : 0.6f);
    }

    private void renderAccessTile(int tileId, int checkId, boolean on) {
        findViewById(tileId).setBackgroundResource(
                on ? R.drawable.bg_wallet_access_on : R.drawable.bg_wallet_access_off);
        ((ImageView) findViewById(checkId)).setImageResource(
                on ? R.drawable.ic_checkbox_on : R.drawable.ic_checkbox_off);
    }

    private void buildBenefits() {
        LinearLayout container = findViewById(R.id.benefitsContainer);
        int[] benefits = {R.string.benefit_faster_payments, R.string.benefit_access_credit,
                R.string.benefit_insurance, R.string.benefit_rewards, R.string.benefit_growth_tools};
        for (int res : benefits) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setPadding(0, dp(6), 0, dp(6));

            ImageView check = new ImageView(this);
            check.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
            check.setImageResource(R.drawable.ic_check_green);
            rowView.addView(check);

            RobotoTextView label = new RobotoTextView(this);
            label.setText(res);
            label.setTextSize(14);
            label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(10);
            label.setLayoutParams(lp);
            rowView.addView(label);

            container.addView(rowView);
        }
    }

    // ---- Documents -------------------------------------------------------

    private void buildDocumentRows() {
        docDefs.add(new DocDef("business_registration_document",
                getString(R.string.doc_business_reg), getString(R.string.doc_business_reg_sub), true));
        docDefs.add(new DocDef("owner_id_document",
                getString(R.string.doc_owner_id), getString(R.string.doc_owner_id_sub), true));
        docDefs.add(new DocDef("proof_of_address",
                getString(R.string.doc_proof_address), getString(R.string.doc_proof_address_sub), true));
        docDefs.add(new DocDef("store_front_photo",
                getString(R.string.doc_store_front), getString(R.string.doc_store_front_sub), true));
        docDefs.add(new DocDef("store_interior_photo",
                getString(R.string.doc_store_interior), getString(R.string.doc_store_interior_sub), true));
        docDefs.add(new DocDef("additional_documents",
                getString(R.string.doc_additional), getString(R.string.doc_additional_sub), false));

        LinearLayout container = findViewById(R.id.documentContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (DocDef def : docDefs) {
            View row = inflater.inflate(R.layout.item_document_row, container, false);
            TextView title = row.findViewById(R.id.docTitle);
            TextView sub = row.findViewById(R.id.docSubtitle);
            TextView upload = row.findViewById(R.id.docUploadButton);
            title.setText(def.required ? def.title + " *" : def.title);
            if (def.sub == null) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setText(def.sub);
            }
            upload.setOnClickListener(v -> chooseImageSource(def.key, true));
            docSubtitleViews.put(def.key, sub);
            docButtons.put(def.key, upload);
            container.addView(row);
        }
    }

    // ---- Logo ------------------------------------------------------------

    private void setupLogoUpload() {
        findViewById(R.id.logoUploadBox).setOnClickListener(v ->
                chooseImageSource(LOGO_TARGET, false));
    }

    // ---- Image / file source chooser ------------------------------------

    private void setupPickers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), success -> {
                    if (success && pendingCameraUri != null) {
                        onImagePicked(pendingCameraUri);
                    }
                });
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) onImagePicked(uri);
                });
        fileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) onImagePicked(uri);
                });
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, R.string.camera_permission_denied,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** allowFile = true for documents (PDF allowed); logo is image-only. */
    private void chooseImageSource(String target, boolean allowFile) {
        pendingTarget = target;
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.source_take_photo));
        labels.add(getString(R.string.source_choose_gallery));
        if (allowFile) {
            labels.add(getString(R.string.source_choose_file));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_image_source)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        requestCamera();
                    } else if (which == 1) {
                        galleryLauncher.launch("image/*");
                    } else {
                        fileLauncher.launch("*/*");
                    }
                })
                .show();
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "captures");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onImagePicked(Uri uri) {
        if (LOGO_TARGET.equals(pendingTarget)) {
            showCropDialog(uri);
        } else {
            applyDocument(pendingTarget, uri);
        }
    }

    private void applyDocument(String key, Uri uri) {
        if (key == null) {
            return;
        }
        docUris.put(key, uri);
        TextView sub = docSubtitleViews.get(key);
        TextView btn = docButtons.get(key);
        String name = queryFileName(uri);
        if (sub != null && name != null) {
            sub.setVisibility(View.VISIBLE);
            sub.setText(name);
            sub.setTextColor(ContextCompat.getColor(this, R.color.success));
        }
        if (btn != null) {
            btn.setText(R.string.replace);
        }
    }

    // ---- Logo crop dialog ------------------------------------------------

    private void showCropDialog(Uri uri) {
        Bitmap bitmap = decodeBitmap(uri, 1500);
        if (bitmap == null) {
            Toast.makeText(this, R.string.err_documents, Toast.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_crop_logo, null);
        LogoCropView cropView = view.findViewById(R.id.cropView);
        cropView.setBitmap(bitmap);

        android.widget.SeekBar scale = view.findViewById(R.id.cropScale);
        android.widget.SeekBar rotation = view.findViewById(R.id.cropRotation);
        scale.setProgress(50); // 0.5 + 50/100 = 1.0
        rotation.setProgress(0);
        scale.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean u) {
                cropView.setUserScale(0.5f + p / 100f);
            }
        });
        rotation.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean u) {
                cropView.setRotationDegrees(p);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();

        view.findViewById(R.id.cropClose).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.cropCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.cropUploadNew).setOnClickListener(v -> {
            dialog.dismiss();
            chooseImageSource(LOGO_TARGET, false);
        });
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
            while (largest / sample > maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Address autocomplete -------------------------------------------

    private void setupAddressAutocomplete() {
        TextView field = findViewById(R.id.etAddressSearch);
        addressPopup = new android.widget.ListPopupWindow(this);
        addressPopup.setAnchorView(field);

        ((EditText) field).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (suppressAddressWatcher) {
                    return;
                }
                String keyword = s.toString().trim();
                if (autocompleteRunnable != null) {
                    autocompleteHandler.removeCallbacks(autocompleteRunnable);
                }
                if (keyword.length() < 3) {
                    addressPopup.dismiss();
                    return;
                }
                autocompleteRunnable = () -> queryAutocomplete(keyword);
                autocompleteHandler.postDelayed(autocompleteRunnable, 350);
            }
        });
    }

    private void queryAutocomplete(String keyword) {
        ApiClient.get(this).getAddressAutocomplete(keyword, new ApiCallback<AutocompleteData>() {
            @Override
            public void onSuccess(AutocompleteData data) {
                predictions.clear();
                if (data != null && data.predictions != null) {
                    predictions.addAll(data.predictions);
                }
                showPredictions();
            }

            @Override
            public void onError(String message) {
                // Best-effort; the user can still drop a pin on the map.
            }
        });
    }

    private void showPredictions() {
        if (predictions.isEmpty()) {
            addressPopup.dismiss();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (AutocompleteData.Prediction p : predictions) {
            labels.add(p.description);
        }
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
        ApiClient.get(this).getCoordinateFromPlaceId(placeId, new ApiCallback<CoordinateData>() {
            @Override
            public void onSuccess(CoordinateData data) {
                if (data == null) {
                    return;
                }
                pickedLat = data.latitude;
                pickedLng = data.longitude;
                if (data.formatted_address != null && !data.formatted_address.isEmpty()) {
                    setAddressText(data.formatted_address);
                }
                moveMapTo(new LatLng(data.latitude, data.longitude), true);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RegisterTraderActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setAddressText(String value) {
        suppressAddressWatcher = true;
        EditText field = findViewById(R.id.etAddressSearch);
        field.setText(value);
        field.setSelection(value.length());
        suppressAddressWatcher = false;
    }

    // ---- Map -------------------------------------------------------------

    private void setupMap(Bundle savedInstanceState) {
        registerMap = findViewById(R.id.registerMap);
        registerMap.onCreate(savedInstanceState);
        registerMap.getMapAsync(this);

        final NestedScrollView scroll = findViewById(R.id.locationScroll);
        registerMap.setOnTouchListener((v, ev) -> {
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
            pickedLat = latLng.latitude;
            pickedLng = latLng.longitude;
            placeMarker(latLng);
            reverseGeocode(latLng);
        });
        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker m) { }
            @Override public void onMarkerDrag(Marker m) { }
            @Override public void onMarkerDragEnd(Marker m) {
                pickedLat = m.getPosition().latitude;
                pickedLng = m.getPosition().longitude;
                reverseGeocode(m.getPosition());
            }
        });

        if (pickedLat != null && pickedLng != null) {
            moveMapTo(new LatLng(pickedLat, pickedLng), true);
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(-26.2041, 28.0473), 9f)); // Johannesburg, South Africa
        }
    }

    private void moveMapTo(LatLng latLng, boolean placePin) {
        if (googleMap == null) {
            return;
        }
        if (placePin) {
            placeMarker(latLng);
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    private void placeMarker(LatLng latLng) {
        if (googleMap == null) {
            return;
        }
        if (marker == null) {
            marker = googleMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
        } else {
            marker.setPosition(latLng);
        }
    }

    private void reverseGeocode(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(
                        latLng.latitude, latLng.longitude, 1);
                if (results != null && !results.isEmpty()
                        && results.get(0).getMaxAddressLineIndex() >= 0) {
                    final String line = results.get(0).getAddressLine(0);
                    runOnUiThread(() -> setAddressText(line));
                }
            } catch (Exception ignored) {
                // Offline / unavailable geocoder: the pin lat/lng is still captured.
            }
        }).start();
    }

    // ---- Reference data --------------------------------------------------

    private void loadReference() {
        ApiClient.get(this).getReference(new ApiCallback<ReferenceData>() {
            @Override
            public void onSuccess(ReferenceData data) {
                if (data == null) {
                    return;
                }
                if (data.trader_types != null && !data.trader_types.isEmpty()) {
                    traderTypes.clear();
                    traderTypes.addAll(data.trader_types);
                    buildTraderTypeCards();
                }
                if (data.product_categories != null) {
                    primaryCategories.clear();
                    for (ReferenceData.ProductCategory c : data.product_categories) {
                        if (c.parent_id == null) {
                            primaryCategories.add(c);
                        }
                    }
                }
                if (data.distributors != null) {
                    distributors.clear();
                    distributors.addAll(data.distributors);
                }
            }

            @Override
            public void onError(String message) {
                // Reference data is best-effort; fallback trader types remain available.
            }
        });
    }

    // ---- Submit ----------------------------------------------------------

    private void submit() {
        Map<String, String> form = new TreeMap<>();
        form.put("first_name", text(R.id.etFirstName));
        form.put("last_name", text(R.id.etLastName));
        form.put("id_number", text(R.id.etIdNumber));
        form.put("passport_number", text(R.id.etPassportNumber));
        form.put("mobile_number", fullPhone(ccpMobile, R.id.etMobile));
        form.put("email", text(R.id.etEmail));
        form.put("password", text(R.id.etPassword));

        form.put("business_name", text(R.id.etBusinessName));
        form.put("trading_name", text(R.id.etTradingName));
        form.put("business_registration_number", text(R.id.etBusinessReg));
        form.put("business_phone_number", fullPhone(ccpBusinessPhone, R.id.etBusinessPhone));
        form.put("business_email", text(R.id.etBusinessEmail));

        form.put("trader_type", String.valueOf(selectedTraderTypeId));
        form.put("primary_product_category_ids", joinLongs(selectedCategoryIds));

        form.put("address", text(R.id.etAddressSearch));
        if (pickedLat != null && pickedLng != null) {
            form.put("latitude", String.valueOf(pickedLat));
            form.put("longitude", String.valueOf(pickedLng));
        }

        form.put("preferred_distributor_ids", joinLongs(selectedDistributorIds));
        if (!selectedDistributorIds.isEmpty()) {
            // Postman exposes a single "recommended_distributor"; default to the first pick.
            form.put("recommended_distributor", String.valueOf(selectedDistributorIds.get(0)));
        }
        form.put("preferred_delivery_days", selectedDaysCsv());
        form.put("payment_method", paymentCode);

        // ---- Wallet ----
        form.put("wallet_activation", createWalletNow ? "now" : "later");
        if (createWalletNow) {
            form.put("wallet_id_type", walletIdTypeCode);
            form.put("wallet_id_number", text(R.id.etWalletIdNumber));
            if ("passport".equals(walletIdTypeCode)) {
                form.put("wallet_passport_country", text(R.id.etWalletPassportCountry));
                form.put("wallet_passport_exp_date", walletPassportExpIso);
            }
            form.put("wallet_dob", walletDobIso);
            form.put("wallet_gender", walletGender);
            form.put("wallet_street_address", text(R.id.etWalletStreet));
            form.put("wallet_suburb", text(R.id.etWalletSuburb));
            form.put("wallet_city", text(R.id.etWalletCity));
            form.put("wallet_province", walletProvinceCode);
            form.put("wallet_post_code", text(R.id.etWalletPostCode));
            form.put("wallet_ussd", walletUssd ? "1" : "0");
            form.put("wallet_internet", walletInternet ? "1" : "0");
            form.put("wallet_cross_border", walletCrossBorder ? "1" : "0");
        }

        List<Http.FilePart> files = new ArrayList<>();
        for (Map.Entry<String, Uri> e : docUris.entrySet()) {
            Http.FilePart part = readFilePart(e.getKey(), e.getValue());
            if (part != null) {
                files.add(part);
            }
        }
        if (logoBitmap != null) {
            files.add(logoFilePart());
        }

        btnContinue.setEnabled(false);
        btnContinue.setText(R.string.registering);
        ApiClient.get(this).registerTrader(form, files,
                new ApiCallback<RegisterTraderData>() {
                    @Override
                    public void onSuccess(RegisterTraderData data) {
                        startActivity(new Intent(RegisterTraderActivity.this,
                                RegisterSubmittedActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        btnContinue.setEnabled(true);
                        btnContinue.setText(R.string.register_btn);
                        Toast.makeText(RegisterTraderActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
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
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String mime = getContentResolver().getType(uri);
            String name = queryFileName(uri);
            return new Http.FilePart(field, name != null ? name : field, mime, out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private long fileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Nullable
    private String queryFileName(Uri uri) {
        String name = null;
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    // ---- Small helpers ---------------------------------------------------

    private String text(int id) {
        TextView tv = findViewById(id);
        return tv.getText() == null ? "" : tv.getText().toString().trim();
    }

    private boolean checked(int id) {
        return ((CheckBox) findViewById(id)).isChecked();
    }

    private boolean isValidEmail(String email) {
        return !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private String joinLongs(List<Long> values) {
        StringBuilder sb = new StringBuilder();
        for (Long v : values) {
            if (sb.length() > 0) sb.append(',');
            sb.append(v);
        }
        return sb.toString();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- MapView lifecycle ----------------------------------------------

    @Override protected void onStart() { super.onStart(); if (registerMap != null) registerMap.onStart(); }
    @Override protected void onResume() { super.onResume(); if (registerMap != null) registerMap.onResume(); }
    @Override protected void onPause() { if (registerMap != null) registerMap.onPause(); super.onPause(); }
    @Override protected void onStop() { if (registerMap != null) registerMap.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (registerMap != null) registerMap.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (registerMap != null) registerMap.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (registerMap != null && outState != null) {
            registerMap.onSaveInstanceState(outState);
        }
    }
}
