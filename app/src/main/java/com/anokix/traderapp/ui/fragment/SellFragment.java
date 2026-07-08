package com.anokix.traderapp.ui.fragment;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.Cart;
import com.anokix.traderapp.model.PosProduct;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.PosProductsData;
import com.anokix.traderapp.network.dto.VasCategoriesData;
import com.anokix.traderapp.network.dto.VasProductsData;
import com.anokix.traderapp.ui.CheckoutActivity;
import com.anokix.traderapp.ui.MainActivity;
import com.anokix.traderapp.ui.NotificationsActivity;
import com.anokix.traderapp.ui.SalesActivity;
import com.anokix.traderapp.ui.VasFormat;
import com.anokix.traderapp.ui.adapter.VasCategoryAdapter;
import com.anokix.traderapp.ui.adapter.VasProductAdapter;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sell / POS (Pagamio) — live product grid from {@code api/trader/pos/products}.
 * Search + category chips filter the grid; tapping "+" builds the Current Sale, and
 * the bottom bar opens the Complete Sale screen ({@link CheckoutActivity}).
 */
public class SellFragment extends Fragment {

    private static final String ALL = "All";

    private TextView cartTotalView;
    private TextView cartCountView;
    private ProgressBar progress;
    private TextView emptyView;
    private ChipGroup chipGroup;

    private ApiClient api;
    private final List<PosProduct> allProducts = new ArrayList<>();
    private ProductAdapter adapter;
    private String selectedCategory = ALL;
    private String searchQuery = "";

    // ---- "Sell Airtime & VAS" section ------------------------------------
    private static final int VAS_PRODUCT_LIMIT = 60;

    private VasCategoryAdapter vasCategoryAdapter;
    private VasProductAdapter vasProductAdapter;
    private RecyclerView vasCategoryList;
    private ProgressBar vasCategoryLoading, vasProductLoading;
    private TextView vasCategoryToggle, vasProductCategoryLabel, vasProductEmpty;
    private TextView vasSellPrice, vasCommission, vasReferenceCounter, vasBuyMyself, vasBuySomeone;
    private EditText vasRecipientInput, vasCustomAmountInput, vasReferenceInput;
    private View vasCustomAmountBlock;
    private MaterialButton vasSellButton;

    private VasCategoriesData.Node vasSelectedCategory;
    private VasProductsData.Product vasSelectedProduct;
    private boolean vasCategoriesExpanded;
    private boolean buyForSelf = true;
    private double commissionRate;
    private String ownPhone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = ApiClient.get(requireContext());

        view.findViewById(R.id.hamburgerButton).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        view.findViewById(R.id.posHistoryButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SalesActivity.class)));

        cartTotalView = view.findViewById(R.id.cartTotal);
        cartCountView = view.findViewById(R.id.cartCount);
        progress = view.findViewById(R.id.posProgress);
        emptyView = view.findViewById(R.id.posEmpty);
        chipGroup = view.findViewById(R.id.posCategoryChips);

        view.findViewById(R.id.scanButton).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Barcode scanner coming soon.", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.checkoutButton).setOnClickListener(v -> {
            if (Cart.get().isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(requireContext(), CheckoutActivity.class));
            }
        });

        EditText search = view.findViewById(R.id.posSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
        });

        RecyclerView list = view.findViewById(R.id.posProducts);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductAdapter();
        list.setAdapter(adapter);

        setupVasSection(view);

        loadProducts();
        loadVasCategories();
        loadOwnPhone();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCartBar();
    }

    private void loadProducts() {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        api.getPosProducts(new ApiCallback<PosProductsData>() {
            @Override
            public void onSuccess(PosProductsData data) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                allProducts.clear();
                if (data != null && data.products != null) {
                    allProducts.addAll(data.products);
                }
                buildCategoryChips();
                applyFilters();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                applyFilters();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildCategoryChips() {
        chipGroup.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        categories.add(ALL);
        for (PosProduct p : allProducts) {
            if (p.category != null && !p.category.isEmpty()) {
                categories.add(p.category);
            }
        }
        boolean first = true;
        for (String label : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(first || label.equals(selectedCategory));
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector));
            chip.setOnClickListener(v -> {
                selectedCategory = label;
                applyFilters();
            });
            chipGroup.addView(chip);
            first = false;
        }
    }

    private void applyFilters() {
        List<PosProduct> filtered = new ArrayList<>();
        for (PosProduct p : allProducts) {
            boolean catOk = ALL.equals(selectedCategory)
                    || selectedCategory.equalsIgnoreCase(p.category);
            boolean searchOk = searchQuery.isEmpty()
                    || (p.name != null && p.name.toLowerCase(Locale.US).contains(searchQuery))
                    || (p.sku != null && p.sku.toLowerCase(Locale.US).contains(searchQuery))
                    || (p.barcode != null && p.barcode.toLowerCase(Locale.US).contains(searchQuery));
            if (catOk && searchOk) {
                filtered.add(p);
            }
        }
        adapter.setItems(filtered);
        boolean empty = filtered.isEmpty() && progress.getVisibility() != View.VISIBLE;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void addToCart(PosProduct item) {
        Cart.get().add(item);
        refreshCartBar();
        Toast.makeText(requireContext(), getString(R.string.added_to_cart, item.name), Toast.LENGTH_SHORT).show();
    }

    private void refreshCartBar() {
        Cart cart = Cart.get();
        cartTotalView.setText(money(cart.subtotal()));
        int count = cart.itemCount();
        if (count > 0) {
            cartCountView.setVisibility(View.VISIBLE);
            cartCountView.setText(String.valueOf(count));
        } else {
            cartCountView.setVisibility(View.GONE);
        }
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    // ====================================================================
    //  Sell Airtime & VAS  (api/common/vas/*, wallet-paid, same as the
    //  standalone Airtime screen but with POS wording + a "Buy for" panel).
    // ====================================================================

    private void setupVasSection(@NonNull View view) {
        vasCategoryList = view.findViewById(R.id.vasCategoryList);
        vasCategoryLoading = view.findViewById(R.id.vasCategoryLoading);
        vasProductLoading = view.findViewById(R.id.vasProductLoading);
        vasCategoryToggle = view.findViewById(R.id.vasCategoryToggle);
        vasProductCategoryLabel = view.findViewById(R.id.vasProductCategoryLabel);
        vasProductEmpty = view.findViewById(R.id.vasProductEmpty);
        vasSellPrice = view.findViewById(R.id.vasSellPrice);
        vasCommission = view.findViewById(R.id.vasCommission);
        vasReferenceCounter = view.findViewById(R.id.vasReferenceCounter);
        vasBuyMyself = view.findViewById(R.id.vasBuyMyself);
        vasBuySomeone = view.findViewById(R.id.vasBuySomeone);
        vasRecipientInput = view.findViewById(R.id.vasRecipientInput);
        vasCustomAmountInput = view.findViewById(R.id.vasCustomAmountInput);
        vasReferenceInput = view.findViewById(R.id.vasReferenceInput);
        vasCustomAmountBlock = view.findViewById(R.id.vasCustomAmountBlock);
        vasSellButton = view.findViewById(R.id.vasSellButton);

        vasCategoryAdapter = new VasCategoryAdapter(this::selectVasCategory);
        vasCategoryList.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        vasCategoryList.setAdapter(vasCategoryAdapter);

        vasProductAdapter = new VasProductAdapter(this::selectVasProduct);
        vasProductAdapter.setPriceFirst(true); // POS shows the price on top, name below.
        RecyclerView vasProductList = view.findViewById(R.id.vasProductList);
        vasProductList.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        vasProductList.setAdapter(vasProductAdapter);

        vasCategoryToggle.setOnClickListener(v -> toggleVasCategories());

        SimpleWatcher summaryWatcher = new SimpleWatcher(this::updateVasSummary);
        vasCustomAmountInput.addTextChangedListener(summaryWatcher);
        vasReferenceInput.addTextChangedListener(new SimpleWatcher(() ->
                vasReferenceCounter.setText(getString(R.string.vas_ref_counter,
                        vasReferenceInput.getText().length()))));

        vasBuyMyself.setOnClickListener(v -> setBuyFor(true));
        vasBuySomeone.setOnClickListener(v -> setBuyFor(false));
        vasSellButton.setOnClickListener(v -> sellVas());

        setBuyFor(true);
        updateVasSummary();
    }

    private void loadVasCategories() {
        vasCategoryLoading.setVisibility(View.VISIBLE);
        vasCategoryList.setVisibility(View.GONE);
        api.getVasCategories(new ApiCallback<VasCategoriesData>() {
            @Override
            public void onSuccess(VasCategoriesData data) {
                if (!isAdded()) return;
                vasCategoryLoading.setVisibility(View.GONE);
                vasCategoryList.setVisibility(View.VISIBLE);
                commissionRate = data == null ? 0 : data.commission_rate;
                List<VasCategoriesData.Node> leaves = data == null ? null : data.leaves();
                vasCategoryAdapter.setItems(leaves);
                if (leaves != null && !leaves.isEmpty()) {
                    selectVasCategory(leaves.get(0));
                }
                updateVasSummary();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                vasCategoryLoading.setVisibility(View.GONE);
                vasCategoryList.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleVasCategories() {
        vasCategoriesExpanded = !vasCategoriesExpanded;
        vasCategoryToggle.setText(vasCategoriesExpanded ? R.string.vas_show_less : R.string.vas_view_all);
        vasCategoryList.setLayoutManager(vasCategoriesExpanded
                ? new GridLayoutManager(requireContext(), 3)
                : new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        vasCategoryAdapter.setExpanded(vasCategoriesExpanded);
    }

    private void selectVasCategory(VasCategoriesData.Node node) {
        vasSelectedCategory = node;
        vasCategoryAdapter.setSelectedId(node.id);
        vasProductCategoryLabel.setText(buildPoweredByLabel(node.name));
        // Reset the product selection when switching category.
        vasSelectedProduct = null;
        vasProductAdapter.setSelectedId(null);
        vasCustomAmountBlock.setVisibility(View.GONE);
        updateVasSummary();
        loadVasProducts(node.id);
    }

    /**
     * "&lt;Category&gt; powered by Limes" — category name (primary, bold) + " powered by "
     * (dark gray) + "Limes" (green, bold), matching the web portal + Airtime screen.
     */
    private CharSequence buildPoweredByLabel(String categoryName) {
        String name = categoryName == null ? "" : categoryName;
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int start = sb.length();
        sb.append(name);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_primary)),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = sb.length();
        sb.append(" powered by ");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_secondary)),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        start = sb.length();
        sb.append("Limes");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.success)),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return sb;
    }

    private void loadVasProducts(String categoryId) {
        vasProductLoading.setVisibility(View.VISIBLE);
        vasProductEmpty.setVisibility(View.GONE);
        vasProductAdapter.setItems(null);
        api.getVasProducts(categoryId, 1, VAS_PRODUCT_LIMIT, new ApiCallback<VasProductsData>() {
            @Override
            public void onSuccess(VasProductsData data) {
                if (!isAdded()) return;
                vasProductLoading.setVisibility(View.GONE);
                List<VasProductsData.Product> products = data == null ? null : data.products;
                vasProductAdapter.setItems(products);
                vasProductEmpty.setVisibility(
                        products == null || products.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                vasProductLoading.setVisibility(View.GONE);
                vasProductEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void selectVasProduct(VasProductsData.Product product) {
        vasSelectedProduct = product;
        vasProductAdapter.setSelectedId(product.id);
        vasCustomAmountBlock.setVisibility(product.isAdHoc ? View.VISIBLE : View.GONE);
        updateVasSummary();
    }

    /** The amount charged: ad-hoc → the custom input; fixed → the product price. */
    private double vasCurrentAmount() {
        if (vasSelectedProduct == null) return 0;
        if (vasSelectedProduct.isAdHoc) {
            String raw = vasCustomAmountInput.getText().toString().trim();
            try {
                return raw.isEmpty() ? 0 : Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return vasSelectedProduct.price;
    }

    private void updateVasSummary() {
        double amount = vasCurrentAmount();
        vasSellPrice.setText(VasFormat.money(amount));
        vasCommission.setText(VasFormat.money(amount * commissionRate));
        vasRecipientInput.setError(null);
        vasCustomAmountInput.setError(null);
    }

    /** Toggle "Buy for": Myself prefills the trader's own number; Someone else clears it. */
    private void setBuyFor(boolean self) {
        buyForSelf = self;
        vasBuyMyself.setBackgroundResource(
                self ? R.drawable.bg_vas_toggle_selected : R.drawable.bg_vas_toggle_unselected);
        vasBuyMyself.setTextColor(ContextCompat.getColor(requireContext(),
                self ? R.color.success : R.color.text_primary));
        vasBuySomeone.setBackgroundResource(
                self ? R.drawable.bg_vas_toggle_unselected : R.drawable.bg_vas_toggle_selected);
        vasBuySomeone.setTextColor(ContextCompat.getColor(requireContext(),
                self ? R.color.text_primary : R.color.success));

        if (self) {
            vasRecipientInput.setText(ownPhone == null ? "" : ownPhone);
        } else {
            vasRecipientInput.setText("");
        }
        vasRecipientInput.setError(null);
    }

    private void loadOwnPhone() {
        api.getBaseInfo(new ApiCallback<BaseInfoData>() {
            @Override
            public void onSuccess(BaseInfoData data) {
                if (!isAdded()) return;
                ownPhone = data != null && data.user != null ? data.user.phone_number : null;
                // If "Myself" is active and the field is still empty, prefill it now.
                if (buyForSelf && ownPhone != null && !ownPhone.isEmpty()
                        && vasRecipientInput.getText().length() == 0) {
                    vasRecipientInput.setText(ownPhone);
                }
            }

            @Override
            public void onError(String message) {
                // Non-fatal: the buyer can still type the number manually.
            }
        });
    }

    private boolean validateVasOrder() {
        if (vasSelectedProduct == null) {
            showVasValidation(R.string.vas_err_select_product, null);
            return false;
        }
        String msisdn = vasRecipientInput.getText().toString().trim();
        if (msisdn.isEmpty()) {
            showVasValidation(R.string.vas_err_recipient_empty, vasRecipientInput);
            return false;
        }
        if (!isValidMsisdn(msisdn)) {
            showVasValidation(R.string.vas_err_recipient_invalid, vasRecipientInput);
            return false;
        }
        if (vasCurrentAmount() <= 0) {
            showVasValidation(R.string.vas_err_amount,
                    vasSelectedProduct.isAdHoc ? vasCustomAmountInput : null);
            return false;
        }
        return true;
    }

    /** SA mobile number: 10 digits (0xxxxxxxxx) or 11 digits (27xxxxxxxxx). */
    private boolean isValidMsisdn(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        return (digits.length() == 10 && digits.startsWith("0"))
                || (digits.length() == 11 && digits.startsWith("27"));
    }

    private void showVasValidation(int messageRes, @Nullable EditText field) {
        if (field != null) {
            field.setError(getString(messageRes));
            field.requestFocus();
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.vas_validation_title)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void sellVas() {
        if (!validateVasOrder() || vasSelectedCategory == null) return;
        String msisdn = vasRecipientInput.getText().toString().trim();
        double amount = vasCurrentAmount();

        vasSellButton.setEnabled(false);
        vasSellButton.setText(R.string.processing);
        final VasProductsData.Product product = vasSelectedProduct;
        api.purchaseVas(product.id, msisdn, amount, product.sku, product.name,
                vasSelectedCategory.name, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        if (!isAdded()) return;
                        vasSellButton.setText(R.string.vas_sell_now);
                        vasSellButton.setEnabled(true);
                        onVasSellComplete();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        vasSellButton.setText(R.string.vas_sell_now);
                        vasSellButton.setEnabled(true);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.vas_sell_now)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
    }

    private void onVasSellComplete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.vas_purchase_success_title)
                .setMessage(R.string.pos_vas_success_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        // Reset the VAS form (keep the selected category + "Buy for" choice).
        vasSelectedProduct = null;
        vasProductAdapter.setSelectedId(null);
        vasCustomAmountBlock.setVisibility(View.GONE);
        vasCustomAmountInput.setText("");
        vasReferenceInput.setText("");
        updateVasSummary();
    }

    /** Minimal TextWatcher that runs a callback on every change. */
    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            onChange.run();
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private List<PosProduct> items = new ArrayList<>();

        void setItems(List<PosProduct> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pos_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PosProduct item = items.get(position);
            h.name.setText(item.name);
            h.sku.setText(item.sku == null || item.sku.isEmpty() ? "" : "SKU: " + item.sku);
            h.price.setText(money(item.price));

            boolean sellable = item.sellable();
            h.stock.setText(sellable
                    ? item.units + " in stock"
                    : getString(R.string.out_of_stock));
            h.stock.setTextColor(ContextCompat.getColor(requireContext(),
                    sellable ? R.color.success : R.color.danger));

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Glide.with(h.image.getContext()).load(item.imageUrl).centerCrop().into(h.image);
            } else {
                h.image.setImageDrawable(null);
            }

            h.add.setEnabled(sellable);
            h.add.setAlpha(sellable ? 1f : 0.4f);
            h.add.setOnClickListener(sellable ? v -> addToCart(item) : null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView image;
            final TextView name, sku, price, stock;
            final MaterialButton add;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.posProductImage);
                name = v.findViewById(R.id.posProductName);
                sku = v.findViewById(R.id.posProductSku);
                price = v.findViewById(R.id.posProductPrice);
                stock = v.findViewById(R.id.posProductStock);
                add = v.findViewById(R.id.posProductAdd);
            }
        }
    }
}
