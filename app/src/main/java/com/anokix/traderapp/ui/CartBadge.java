package com.anokix.traderapp.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.CartData;

/**
 * Shared helper that fills a small header cart badge. Paints the local mirror
 * ({@link MarketCart}) immediately, then refreshes it from the server cart
 * (GET api/trader/cart) so it reflects the real backend state — same contract as
 * {@code MarketplaceActivity.refreshCart()}. Hides the badge at zero, shows
 * "99+" beyond ninety-nine. Call from {@code onResume} so it picks up items added
 * elsewhere in the app.
 */
public final class CartBadge {

    private CartBadge() {}

    public static void refresh(Context context, final TextView badge) {
        if (badge == null || context == null) return;
        paint(badge);
        ApiClient.get(context).getCart(new ApiCallback<CartData>() {
            @Override
            public void onSuccess(CartData result) {
                MarketCart.get().hydrate(result);
                paint(badge);
            }

            @Override
            public void onError(String message) {
                // Keep the local mirror on failure.
            }
        });
    }

    private static void paint(TextView badge) {
        int count = MarketCart.get().itemCount();
        if (count > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            badge.setVisibility(View.GONE);
        }
    }
}
