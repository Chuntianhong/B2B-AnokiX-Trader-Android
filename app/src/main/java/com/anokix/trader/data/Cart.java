package com.anokix.trader.data;

import com.anokix.trader.model.CartLine;
import com.anokix.trader.model.PosProduct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory POS "Current Sale" cart shared between the Sell screen and the
 * Checkout (Complete Sale) screen. A process-lifetime singleton — Pagamio POS
 * sales are committed via {@code api/trader/pos/sale} on completion.
 */
public final class Cart {

    private static final Cart POS_CART = new Cart();

    /** The single POS sale cart. */
    public static Cart get() {
        return POS_CART;
    }

    private final Map<Integer, CartLine> lines = new LinkedHashMap<>();

    private Cart() {}

    /** Add one unit of the product, creating the line if needed (clamped to stock). */
    public void add(PosProduct item) {
        CartLine line = lines.get(item.id);
        if (line == null) {
            lines.put(item.id, new CartLine(item.id, item.name, item.sku, item.imageUrl,
                    item.price, item.units, 1));
        } else if (line.qty < line.stock) {
            line.qty++;
        }
    }

    public void increment(int productId) {
        CartLine line = lines.get(productId);
        if (line != null && line.qty < line.stock) {
            line.qty++;
        }
    }

    public void decrement(int productId) {
        CartLine line = lines.get(productId);
        if (line != null) {
            line.qty--;
            if (line.qty <= 0) {
                lines.remove(productId);
            }
        }
    }

    public void remove(int productId) {
        lines.remove(productId);
    }

    public List<CartLine> lines() {
        return new ArrayList<>(lines.values());
    }

    public int itemCount() {
        int count = 0;
        for (CartLine line : lines.values()) {
            count += line.qty;
        }
        return count;
    }

    /** Sum of line totals (VAT-inclusive, before any discount). */
    public double subtotal() {
        double total = 0;
        for (CartLine line : lines.values()) {
            total += line.lineTotal();
        }
        return total;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public void clear() {
        lines.clear();
    }
}
