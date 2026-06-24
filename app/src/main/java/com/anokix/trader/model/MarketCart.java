package com.anokix.trader.model;

import com.anokix.trader.network.dto.CartData;
import com.anokix.trader.network.dto.MarketplaceData;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide local cart for the trader marketplace. Acts as the source of
 * truth for the cart badge, the My Cart screen, and the order totals, while the
 * marketplace screens fire the matching /api/trader/cart/* calls best-effort.
 */
public final class MarketCart {

    public static class Line {
        public final MarketplaceData.Product product;
        public int quantity;
        public double unitPrice;
        /** Server-assigned cart_item_id once known (nullable). */
        public String cartItemId;
        /** Distributor this line belongs to (for order placement). */
        public String distributorId;
        /** Whether this line is selected for checkout (default true). */
        public boolean selected = true;

        Line(MarketplaceData.Product product, int quantity, double unitPrice) {
            this.product = product;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public double lineTotal() {
            return quantity * unitPrice;
        }
    }

    private static final MarketCart INSTANCE = new MarketCart();

    private final List<Line> lines = new ArrayList<>();

    /**
     * Selected distributor's {@code preferred_delivery_days} (from the marketplace
     * response), used to generate the Make Order delivery slots. Set when the
     * marketplace binds the selected distributor.
     */
    private String distributorDeliveryDays;

    private MarketCart() {}

    public void setDistributorDeliveryDays(String value) {
        this.distributorDeliveryDays = value;
    }

    public String getDistributorDeliveryDays() {
        return distributorDeliveryDays;
    }

    public static MarketCart get() {
        return INSTANCE;
    }

    public List<Line> lines() {
        return lines;
    }

    /** Add (or increment) a product line. Returns the affected line. */
    public Line add(MarketplaceData.Product product, int quantity, double unitPrice) {
        for (Line l : lines) {
            if (l.product != null && l.product.id != null && l.product.id.equals(product.id)) {
                l.quantity += quantity;
                l.unitPrice = unitPrice;
                return l;
            }
        }
        Line l = new Line(product, quantity, unitPrice);
        lines.add(l);
        return l;
    }

    public void setQuantity(Line line, int quantity) {
        if (quantity <= 0) {
            lines.remove(line);
        } else {
            line.quantity = quantity;
        }
    }

    public void remove(Line line) {
        lines.remove(line);
    }

    public void clear() {
        lines.clear();
    }

    /**
     * Replace the local lines with the server cart snapshot (GET /api/trader/cart),
     * so the badge, My Cart screen, and totals reflect the real backend state and
     * each line carries its server-assigned cart item id.
     */
    public void hydrate(CartData data) {
        lines.clear();
        if (data == null) return;
        for (CartData.CartItem item : data.lines()) {
            int qty = item.quantity != null ? item.quantity : 0;
            if (qty <= 0) continue;
            MarketplaceData.Product p = item.product;
            if (p == null) {
                p = new MarketplaceData.Product();
                p.id = item.product_id;
                p.name = item.name;
                p.image_url = item.image_url;
            }
            Line line = new Line(p, qty, item.unitPrice());
            line.cartItemId = item.cartItemId();
            if (item.distributor != null && item.distributor.id != 0) {
                line.distributorId = String.valueOf(item.distributor.id);
            } else if (item.distributor_id != 0) {
                line.distributorId = String.valueOf(item.distributor_id);
            }
            lines.add(line);
        }
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Number of distinct product lines. */
    public int distinctCount() {
        return lines.size();
    }

    /** Total quantity across all lines (used for the header badge). */
    public int itemCount() {
        int q = 0;
        for (Line l : lines) {
            q += l.quantity;
        }
        return q;
    }

    public double subtotal() {
        double s = 0;
        for (Line l : lines) {
            s += l.lineTotal();
        }
        return s;
    }

    /** Lines currently selected for checkout. */
    public List<Line> selectedLines() {
        List<Line> sel = new ArrayList<>();
        for (Line l : lines) {
            if (l.selected) sel.add(l);
        }
        return sel;
    }

    public int selectedCount() {
        return selectedLines().size();
    }

    /** Subtotal across the selected lines only. */
    public double selectedSubtotal() {
        double s = 0;
        for (Line l : selectedLines()) {
            s += l.lineTotal();
        }
        return s;
    }
}
