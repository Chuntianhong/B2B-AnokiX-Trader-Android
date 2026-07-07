package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * data block of GET /api/trader/marketplace/promotions — the titled, paginated
 * "Current Promotions" list opened from the Marketplace section's "View All".
 * Promotions reuse {@link MarketplaceData.Promotion}. (Named to avoid the existing
 * distributor {@link PromotionsData}.)
 */
public class PromotionCollectionData {
    public Meta meta;
    public String title;
    public long distributor_id;
    public Pagination pagination;
    public List<MarketplaceData.Promotion> promotions;

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
