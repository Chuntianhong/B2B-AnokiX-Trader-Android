package com.anokix.traderapp.ui.pos;

import androidx.annotation.Nullable;

import com.anokix.traderapp.model.PosProduct;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a scanned or typed code to a POS product.
 *
 * <p>{@code api/trader/pos/products} carries no QR field, so both the barcode scanner and
 * the QR scanner match on the same two identifiers: {@code barcode} and {@code sku}. A 1D
 * barcode is that value verbatim; a QR label often wraps it (a stock-take URL, a JSON blob,
 * a {@code KEY:value} line), so when the raw payload misses we retry against the tokens
 * pulled out of it rather than declaring "not found" on a code the shop can plainly read.
 */
public final class ProductCodeMatcher {

    private ProductCodeMatcher() {}

    /** Longest token worth trying as an identifier — beyond this it is prose, not a code. */
    private static final int MAX_TOKEN = 64;

    /**
     * @return the product whose barcode or SKU matches {@code raw}, or {@code null}.
     *         Barcode is checked before SKU across the whole list, so a code that is one
     *         product's barcode never loses to another product's SKU.
     */
    @Nullable
    public static PosProduct find(@Nullable List<PosProduct> products, @Nullable String raw) {
        if (products == null || products.isEmpty() || raw == null) {
            return null;
        }
        String code = raw.trim();
        if (code.isEmpty()) {
            return null;
        }
        PosProduct hit = matchExact(products, code);
        if (hit != null) {
            return hit;
        }
        // Structured QR payload — try each identifier-shaped token inside it.
        for (String token : tokenize(code)) {
            hit = matchExact(products, token);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Nullable
    private static PosProduct matchExact(List<PosProduct> products, String code) {
        for (PosProduct p : products) {
            if (equalsCode(p.barcode, code)) {
                return p;
            }
        }
        for (PosProduct p : products) {
            if (equalsCode(p.sku, code)) {
                return p;
            }
        }
        return null;
    }

    private static boolean equalsCode(@Nullable String value, String code) {
        return value != null && !value.trim().isEmpty()
                && value.trim().equalsIgnoreCase(code);
    }

    /**
     * Splits a QR payload into candidate identifiers on the punctuation that separates
     * fields in URLs, JSON and key/value labels. Ordering is preserved so the earliest
     * plausible token wins.
     */
    private static List<String> tokenize(String payload) {
        List<String> tokens = new ArrayList<>();
        for (String part : payload.split("[\\s/?&=,;:|\"'{}\\[\\]\\\\<>]+")) {
            String token = part.trim();
            if (token.isEmpty() || token.length() > MAX_TOKEN || !hasDigitOrLetter(token)) {
                continue;
            }
            String lower = token.toLowerCase(Locale.US);
            // Drop URL scaffolding — "https", "http", "www.acme.co.za" and the like.
            if (lower.equals("http") || lower.equals("https") || lower.startsWith("www.")) {
                continue;
            }
            if (!tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean hasDigitOrLetter(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isLetterOrDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
