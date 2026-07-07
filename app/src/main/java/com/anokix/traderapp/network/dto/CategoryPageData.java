package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * data block of GET /api/trader/marketplace/category — the category detail page:
 * filtered/sorted/paginated {@link MarketplaceData.Product} products for a category,
 * the filter facets used to drive the Filters bottom sheet (each with live counts),
 * the resolved distributor, pagination and the trader's recently-purchased items.
 */
public class CategoryPageData {
    public Meta meta;
    public Category category;
    public long distributor_id;
    public Pagination pagination;
    public Filters filters;
    public List<MarketplaceData.Product> products;
    public List<MarketplaceData.Product> recently_purchased;
    public List<MarketplaceData.Banner> banners;

    /** Currency symbol for price formatting, defaulting to the SA rand. */
    public String currencySymbol() {
        if (meta != null && meta.currency != null && meta.currency.symbol != null
                && !meta.currency.symbol.trim().isEmpty()) {
            return meta.currency.symbol;
        }
        return "R";
    }

    public static class Meta {
        public Currency currency;
    }

    public static class Currency {
        public String code;
        public String symbol;
    }

    /** The category the page is scoped to. */
    public static class Category {
        public long id;
        public String title;
        public String slug;
        public Long parent_id;
    }

    public static class Pagination {
        public int page;
        public int per_page;
        public int total;
        public int total_pages;
    }

    /** Filter facets, each carrying a count of matching products. */
    public static class Filters {
        public List<Brand> brands;
        public List<SubCategory> categories;
        public List<PackSize> pack_sizes;
        public PriceRange price_range;
        public List<Promotion> promotions;
        public Availability availability;
    }

    public static class Brand {
        public long id;
        public String name;
        public int count;
    }

    public static class SubCategory {
        public long id;
        public String title;
        public String slug;
        public int count;
    }

    public static class PackSize {
        public String key;   // small | medium | large
        public String label; // "Small (< 1kg)"
        public int count;
    }

    public static class PriceRange {
        public double min;
        public double max;
    }

    public static class Promotion {
        public String key;   // on_promotion | buy_more_save_more | sponsored
        public String label;
        public int count;
    }

    public static class Availability {
        public int in_stock_count;
        public int total_count;
    }
}
