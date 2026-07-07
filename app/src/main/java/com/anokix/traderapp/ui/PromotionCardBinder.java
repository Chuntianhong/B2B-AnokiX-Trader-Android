package com.anokix.traderapp.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.bumptech.glide.Glide;

/**
 * Binds an {@code item_promotion_card.xml} (overlaid banner-style promo card) —
 * shared by the Marketplace "Current Promotions" section and the Promotions
 * "View All" screen so both render identically.
 */
final class PromotionCardBinder {

    private PromotionCardBinder() { }

    static void bind(Activity activity, View card, MarketplaceData.Promotion p, Runnable onShop) {
        ImageView img = card.findViewById(R.id.promoImage);
        String url = p.mediaUrl();
        if (url != null && !p.isVideo() && !activity.isFinishing() && !activity.isDestroyed()) {
            Glide.with(img).load(url).centerCrop().into(img);
        }
        ((TextView) card.findViewById(R.id.promoTitle)).setText(p.name != null ? p.name : "");
        ((TextView) card.findViewById(R.id.promoDescription))
                .setText(p.description != null ? p.description : "");

        TextView date = card.findViewById(R.id.promoDate);
        boolean hasDate = p.end_date != null && !p.end_date.trim().isEmpty();
        date.setVisibility(hasDate ? View.VISIBLE : View.GONE);
        if (hasDate) date.setText(p.end_date);

        card.findViewById(R.id.promoShop).setOnClickListener(v -> {
            if (onShop != null) onShop.run();
        });
    }
}
