package com.anokix.traderapp.network.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * data block of GET /api/trader/cart (and the cart mutation endpoints).
 *
 * Real shape (api/trader/cart):
 *   data.items[] — one row per cart line: id, product_id, distributor_id,
 *                  quantity, unit_price, line_total, in_stock, stock_quantity,
 *                  a nested {@code product} and {@code distributor}.
 *   data.cart    — items_count, total_quantity, subtotal, currency{code, symbol}.
 *
 * A few legacy flat aliases are tolerated so older callers keep compiling. The
 * server is the source of truth: the app hydrates its local
 * {@link com.anokix.traderapp.model.MarketCart} from this for the badge, the My Cart
 * screen, and the order totals.
 */
public class CartData {

    public List<CartItem> items;
    public List<CartItem> cart_items;   // alias tolerance
    public Cart cart;

    public List<CartItem> lines() {
        if (items != null) return items;
        if (cart_items != null) return cart_items;
        return new ArrayList<>();
    }

    /** Distinct cart lines. */
    public int itemsCount() {
        if (cart != null && cart.items_count != null) return cart.items_count;
        return lines().size();
    }

    /** Total quantity across all lines (used for the header badge). */
    public int totalQuantity() {
        if (cart != null && cart.total_quantity != null) return cart.total_quantity;
        int q = 0;
        for (CartItem c : lines()) {
            q += c.quantity != null ? c.quantity : 0;
        }
        return q;
    }

    public double subtotal() {
        if (cart != null && cart.subtotal != null) return cart.subtotal;
        double s = 0;
        for (CartItem c : lines()) {
            if (c.line_total != null) {
                s += c.line_total;
            } else if (c.unit_price != null && c.quantity != null) {
                s += c.unit_price * c.quantity;
            }
        }
        return s;
    }

    public String currencySymbol() {
        if (cart != null && cart.currency != null && cart.currency.symbol != null
                && !cart.currency.symbol.trim().isEmpty()) {
            return cart.currency.symbol;
        }
        return "R";
    }

    public static class Cart {
        public Integer items_count;
        public Integer total_quantity;
        public Double subtotal;
        public Currency currency;
    }

    public static class Currency {
        public String code;
        public String symbol;
    }

    public static class CartItem {
        public String id;
        public String cart_item_id;     // alias tolerance
        public String product_id;
        public long distributor_id;
        public Integer quantity;
        public Double unit_price;
        public Double line_total;
        public Boolean in_stock;
        public Integer stock_quantity;
        public MarketplaceData.Product product;
        public CartDistributor distributor;

        // legacy flat aliases (older tolerant shape)
        public String name;
        public String image_url;

        public String cartItemId() {
            return cart_item_id != null ? cart_item_id : id;
        }

        /** Per-unit price for this line (the cart's unit_price = selling price). */
        public double unitPrice() {
            if (unit_price != null) return unit_price;
            if (product != null) return product.priceValue();
            return 0;
        }
    }

    public static class CartDistributor {
        public long id;
        public String display_name;
    }
}
