package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * data block of GET /api/trader/marketplace/collection?type= — a titled, paginated
 * product collection (e.g. type=recommended / best_sellers) opened from a Marketplace
 * section's "View All". Products reuse {@link MarketplaceData.Product}.
 */
public class CollectionData {
    public Meta meta;
    public String type;
    public String title;
    public long distributor_id;
    public Pagination pagination;
    public List<MarketplaceData.Product> products;

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

    public static class Pagination {
        public int page;
        public int per_page;
        public int total;
        public int total_pages;
    }
}
