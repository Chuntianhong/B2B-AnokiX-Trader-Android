package com.anokix.trader.data;

import com.anokix.trader.model.CartLine;
import com.anokix.trader.model.ProductItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory POS cart shared between the Sell screen and Checkout. A process-lifetime
 * singleton — fine for mock-first; would move behind the repository when the POS
 * (Pagamio) API is wired.
 */
public final class Cart {

    /** POS "sell" cart. */
    public static final String POS = "pos";
    /** Marketplace "order stock" cart. */
    public static final String ORDER = "order";

    private static final Cart POS_CART = new Cart();
    private static final Cart ORDER_CART = new Cart();

    /** Default accessor returns the POS cart (used by the Sell screen). */
    public static Cart get() {
        return POS_CART;
    }

    public static Cart orders() {
        return ORDER_CART;
    }

    /** Resolve a cart by key ({@link #POS} / {@link #ORDER}). */
    public static Cart byKey(String key) {
        return ORDER.equals(key) ? ORDER_CART : POS_CART;
    }

    private final Map<String, CartLine> lines = new LinkedHashMap<>();

    private Cart() {}

    /** Add one unit of the product, creating the line if needed. */
    public void add(ProductItem item) {
        CartLine line = lines.get(item.id);
        if (line == null) {
            lines.put(item.id, new CartLine(item.id, item.name, parsePrice(item.price), 1));
        } else {
            line.qty++;
        }
    }

    public void increment(String productId) {
        CartLine line = lines.get(productId);
        if (line != null) {
            line.qty++;
        }
    }

    public void decrement(String productId) {
        CartLine line = lines.get(productId);
        if (line != null) {
            line.qty--;
            if (line.qty <= 0) {
                lines.remove(productId);
            }
        }
    }

    public void remove(String productId) {
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

    public static double parsePrice(String price) {
        if (price == null) {
            return 0;
        }
        String digits = price.replaceAll("[^0-9.]", "");
        try {
            return digits.isEmpty() ? 0 : Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
