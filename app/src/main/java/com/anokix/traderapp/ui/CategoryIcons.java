package com.anokix.traderapp.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.dto.ReferenceData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps product categories to local icons when the API has no {@code logo_url}.
 * Uses stable {@code slug} first, then title keywords, then a default icon.
 */
public final class CategoryIcons {

    private static final Map<String, Integer> BY_SLUG = new HashMap<>();

    static {
        BY_SLUG.put("grocery-food", R.drawable.ic_shopping_bag);
        BY_SLUG.put("beauty-cosmetics", R.drawable.ic_heart);
        BY_SLUG.put("health-personal-care", R.drawable.ic_shield);
        BY_SLUG.put("home-kitchen", R.drawable.ic_store);
        BY_SLUG.put("books-stationery", R.drawable.ic_document);
        BY_SLUG.put("baby-products", R.drawable.ic_person);
        BY_SLUG.put("automotive", R.drawable.ic_deliveries);
        BY_SLUG.put("electronics", R.drawable.ic_products);
        BY_SLUG.put("fashion", R.drawable.ic_shopping_bag);
        BY_SLUG.put("industrial-tools", R.drawable.ic_briefcase);
        BY_SLUG.put("pet-supplies", R.drawable.ic_heart);
        BY_SLUG.put("sports-outdoors", R.drawable.ic_star);
        BY_SLUG.put("toys-games", R.drawable.ic_promo_gift);
        // Common child slugs if a sub-category is shown by mistake.
        BY_SLUG.put("beverages", R.drawable.ic_shopping_bag);
        BY_SLUG.put("snacks", R.drawable.ic_package);
        BY_SLUG.put("dairy", R.drawable.ic_package);
        BY_SLUG.put("frozen-food", R.drawable.ic_inventory);
        BY_SLUG.put("fruits-vegetables", R.drawable.ic_shopping_bag);
    }

    private CategoryIcons() {
    }

    @DrawableRes
    public static int iconFor(@Nullable ReferenceData.ProductCategory category) {
        if (category == null) return R.drawable.ic_package;
        if (category.slug != null) {
            Integer mapped = BY_SLUG.get(category.slug.trim().toLowerCase(Locale.US));
            if (mapped != null) return mapped;
        }
        return iconForTitle(category.title);
    }

    @DrawableRes
    private static int iconForTitle(@Nullable String title) {
        if (title == null || title.trim().isEmpty()) return R.drawable.ic_package;
        String t = title.toLowerCase(Locale.US);
        if (t.contains("grocery") || t.contains("food") || t.contains("beverage")) {
            return R.drawable.ic_shopping_bag;
        }
        if (t.contains("beauty") || t.contains("cosmetic") || t.contains("pet")) {
            return R.drawable.ic_heart;
        }
        if (t.contains("health") || t.contains("personal care")) {
            return R.drawable.ic_shield;
        }
        if (t.contains("home") || t.contains("kitchen")) {
            return R.drawable.ic_store;
        }
        if (t.contains("book") || t.contains("stationery")) {
            return R.drawable.ic_document;
        }
        if (t.contains("baby") || t.contains("kid")) {
            return R.drawable.ic_person;
        }
        if (t.contains("auto") || t.contains("vehicle")) {
            return R.drawable.ic_deliveries;
        }
        if (t.contains("electronic") || t.contains("laptop") || t.contains("phone")) {
            return R.drawable.ic_products;
        }
        if (t.contains("fashion") || t.contains("cloth")) {
            return R.drawable.ic_shopping_bag;
        }
        if (t.contains("industrial") || t.contains("tool")) {
            return R.drawable.ic_briefcase;
        }
        if (t.contains("sport") || t.contains("outdoor")) {
            return R.drawable.ic_star;
        }
        if (t.contains("toy") || t.contains("game")) {
            return R.drawable.ic_promo_gift;
        }
        if (t.contains("frozen") || t.contains("inventory")) {
            return R.drawable.ic_inventory;
        }
        return R.drawable.ic_package;
    }
}
