package com.anokix.traderapp.network.dto;

/**
 * data block of GET /api/common/product-detail?id={id}, shaped as
 * {@code data.product}. Reuses {@link MarketplaceData.Product} since the
 * server returns the same product shape used on the marketplace cards.
 */
public class CommonProductData {
    public MarketplaceData.Product product;
}
