package com.anokix.trader.network.dto;

import com.anokix.trader.model.PosProduct;

import java.util.List;

/** {@code data} block of {@code GET api/trader/pos/products}. */
public class PosProductsData {
    public List<PosProduct> products;
}
