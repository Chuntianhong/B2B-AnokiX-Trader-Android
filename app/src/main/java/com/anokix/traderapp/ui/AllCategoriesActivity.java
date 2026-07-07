package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.CartData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Marketplace › "View All" categories chooser. Shows every top-level product
 * category (from GET /api/common/base-info) in a grid with a local keyword
 * filter. Tapping a category opens {@link CategoryProductsActivity}.
 */
public class AllCategoriesActivity extends AppCompatActivity {

    private final ApiClient api = ApiClient.get(this);

    private TextView cartBadge, emptyView;
    private RecyclerView grid;
    private CategoryAdapter adapter;

    private final List<BaseInfoData.ProductCategory> all = new ArrayList<>();
    private final List<BaseInfoData.ProductCategory> filtered = new ArrayList<>();
    private String query = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_categories);

        cartBadge = findViewById(R.id.cartBadge);
        emptyView = findViewById(R.id.emptyView);
        grid = findViewById(R.id.categoriesGrid);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));

        adapter = new CategoryAdapter();
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(adapter);

        EditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.US);
                applyFilter();
            }
        });

        loadCategories();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    private void loadCategories() {
        api.getBaseInfo(new ApiCallback<BaseInfoData>() {
            @Override
            public void onSuccess(BaseInfoData result) {
                all.clear();
                if (result != null && result.product_categories != null) {
                    for (BaseInfoData.ProductCategory c : result.product_categories) {
                        if (c.parent_id == null) all.add(c); // top-level only
                    }
                    Collections.sort(all, (x, y) -> safe(x.title).compareToIgnoreCase(safe(y.title)));
                }
                applyFilter();
            }

            @Override
            public void onError(String message) {
                applyFilter();
            }
        });
    }

    private void applyFilter() {
        filtered.clear();
        for (BaseInfoData.ProductCategory c : all) {
            if (query.isEmpty() || safe(c.title).toLowerCase(Locale.US).contains(query)) {
                filtered.add(c);
            }
        }
        adapter.notifyDataSetChanged();
        boolean empty = filtered.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openCategory(BaseInfoData.ProductCategory c) {
        Intent i = new Intent(this, CategoryProductsActivity.class);
        i.putExtra(CategoryProductsActivity.EXTRA_CATEGORY_ID, String.valueOf(c.id));
        i.putExtra(CategoryProductsActivity.EXTRA_CATEGORY_TITLE, c.title);
        startActivity(i);
    }

    // ---- Cart badge ------------------------------------------------------

    private void refreshCart() {
        refreshCartBadge();
        api.getCart(new ApiCallback<CartData>() {
            @Override
            public void onSuccess(CartData result) {
                MarketCart.get().hydrate(result);
                refreshCartBadge();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void refreshCartBadge() {
        int count = MarketCart.get().itemCount();
        if (count > 0) {
            cartBadge.setVisibility(View.VISIBLE);
            cartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            cartBadge.setVisibility(View.GONE);
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    // ---- Adapter ---------------------------------------------------------

    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_grid, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            BaseInfoData.ProductCategory c = filtered.get(position);
            h.title.setText(safe(c.title));
            h.icon.setImageResource(CategoryIcons.iconFor(c.slug, c.title));
            h.itemView.setOnClickListener(v -> openCategory(c));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final ImageView icon;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.categoryTitle);
                icon = v.findViewById(R.id.categoryIcon);
            }
        }
    }
}
