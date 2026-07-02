package com.anokix.traderapp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

/**
 * Loads a distributor logo into an {@link ImageView} that is overlaid on top of
 * the coloured initial tile. When a {@code logo_url} is present the image is
 * shown with rounded corners (matching the tile's 10dp radius, cached by Glide);
 * otherwise the ImageView is hidden so the initial tile shows through.
 */
final class LogoLoader {

    private LogoLoader() {}

    /** Corner radius in dp, matching {@code bg_inv_thumb} / {@code bg_dist_avatar}. */
    private static final int RADIUS_DP = 10;

    static void load(ImageView image, String url) {
        // Async API callbacks can arrive after the host activity is gone; Glide
        // throws "cannot start a load for a destroyed activity" in that case.
        if (isHostGone(image)) return;

        if (url != null && !url.trim().isEmpty()) {
            int radiusPx = Math.round(
                    RADIUS_DP * image.getResources().getDisplayMetrics().density);
            image.setVisibility(View.VISIBLE);
            Glide.with(image)
                    .load(url)
                    .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                    .into(image);
        } else {
            Glide.with(image).clear(image);
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
        }
    }

    /** True when the ImageView's host activity is finishing or destroyed. */
    private static boolean isHostGone(ImageView image) {
        Context ctx = image.getContext();
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity a = (Activity) ctx;
                return a.isFinishing() || a.isDestroyed();
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return false;
    }
}
