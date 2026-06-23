package com.anokix.trader.ui.fragment;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.trader.R;
import com.anokix.trader.data.Cart;
import com.anokix.trader.data.MockData;
import com.anokix.trader.model.ProductItem;
import com.anokix.trader.ui.CheckoutActivity;
import com.anokix.trader.ui.MainActivity;
import com.anokix.trader.ui.NotificationsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sell / POS — mirrors the Trader Portal /pos page: search + scan, category chips and a
 * 2-column product grid. Tap Add to build the cart; checkout opens the payment flow.
 * Powered by Pagamio in production — mock-first here.
 */
public class SellFragment extends Fragment {

    private static final String[] CATEGORIES = {
            "All Products", "Beverages", "Snacks", "Household",
            "Personal Care", "Baby Care", "Frozen Foods", "Others"};

    private TextView cartTotalView;
    private TextView cartCountView;
    private List<ProductItem> allProducts;
    private ProductAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.hamburgerButton).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        cartTotalView = view.findViewById(R.id.cartTotal);
        cartCountView = view.findViewById(R.id.cartCount);
        allProducts = MockData.getProductItems();

        view.findViewById(R.id.scanButton).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Barcode scanner coming soon.", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.checkoutButton).setOnClickListener(v -> {
            if (Cart.get().isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(requireContext(), CheckoutActivity.class));
            }
        });

        buildCategoryChips(view);

        RecyclerView list = view.findViewById(R.id.posProducts);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductAdapter(new ArrayList<>(allProducts));
        list.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCartBar();
    }

    private void buildCategoryChips(View root) {
        ChipGroup group = root.findViewById(R.id.posCategoryChips);
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String label = CATEGORIES[i];
            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(i == 0);
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector));
            chip.setOnClickListener(v -> filterCategory(label));
            group.addView(chip);
        }
    }

    private void filterCategory(String label) {
        List<ProductItem> filtered = new ArrayList<>();
        for (ProductItem p : allProducts) {
            if (label.equals("All Products") || label.equalsIgnoreCase(p.category)) {
                filtered.add(p);
            }
        }
        adapter.setItems(filtered);
    }

    private void addToCart(ProductItem item) {
        Cart.get().add(item);
        refreshCartBar();
        Toast.makeText(requireContext(), "Added " + item.name, Toast.LENGTH_SHORT).show();
    }

    private void refreshCartBar() {
        Cart cart = Cart.get();
        cartTotalView.setText(String.format(Locale.US, "R%,.2f", cart.subtotal()));
        int count = cart.itemCount();
        if (count > 0) {
            cartCountView.setVisibility(View.VISIBLE);
            cartCountView.setText(String.valueOf(count));
        } else {
            cartCountView.setVisibility(View.GONE);
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private List<ProductItem> items;

        ProductAdapter(List<ProductItem> items) {
            this.items = items;
        }

        void setItems(List<ProductItem> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_market_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ProductItem item = items.get(position);
            h.image.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(item.colorHex)));
            h.initial.setText(item.name.substring(0, 1));
            h.name.setText(item.name);
            h.pack.setText(item.pack);
            h.price.setText(item.price);
            h.stock.setText(item.stockStatus);

            boolean out = "Out of Stock".equalsIgnoreCase(item.stockStatus);
            h.stock.setTextColor(ContextCompat.getColor(requireContext(),
                    out ? R.color.danger : R.color.success));

            if (item.badge != null) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(item.badge);
                h.badge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#7C3AED")));
            } else {
                h.badge.setVisibility(View.GONE);
            }

            h.add.setEnabled(!out);
            h.add.setText(out ? "Out of Stock" : "Add");
            h.add.setOnClickListener(out ? null : v -> addToCart(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final View image;
            final TextView initial, badge, name, pack, price, stock;
            final MaterialButton add;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.productImage);
                initial = v.findViewById(R.id.productImageInitial);
                badge = v.findViewById(R.id.productBadge);
                name = v.findViewById(R.id.productName);
                pack = v.findViewById(R.id.productPack);
                price = v.findViewById(R.id.productPrice);
                stock = v.findViewById(R.id.productStock);
                add = v.findViewById(R.id.productAdd);
            }
        }
    }
}
