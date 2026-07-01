package com.anokix.traderapp.network.dto;

import com.anokix.traderapp.model.PosProduct;

import java.util.List;

/** {@code data} block of {@code GET api/trader/pos/products}. */
public class PosProductsData {
    public List<PosProduct> products;
}
