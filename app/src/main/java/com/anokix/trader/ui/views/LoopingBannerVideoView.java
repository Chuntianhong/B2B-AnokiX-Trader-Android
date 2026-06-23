package com.anokix.trader.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.anokix.trader.R;
import com.anokix.trader.network.MediaUrls;

/**
 * Muted, looping banner video using Media3 {@link PlayerView} for smooth HTTP
 * playback, built-in buffering feedback, and correct surface handling inside
 * {@link androidx.viewpager2.widget.ViewPager2}.
 */
public class LoopingBannerVideoView extends FrameLayout {

    private final PlayerView playerView;
    private ExoPlayer player;
    private String currentUrl;

    public LoopingBannerVideoView(Context context) {
        this(context, null);
    }

    public LoopingBannerVideoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_banner_player, this, true);
        playerView = findViewById(R.id.bannerPlayerView);
    }

    public void play(@Nullable String url) {
        String resolved = MediaUrls.resolve(url);
        if (resolved == null) {
            stopPlayback();
            return;
        }
        if (resolved.equals(currentUrl) && player != null) {
            player.setPlayWhenReady(true);
            return;
        }
        currentUrl = resolved;
        post(() -> {
            if (!resolved.equals(currentUrl)) return;
            ensurePlayer();
            player.setMediaItem(MediaItem.fromUri(resolved));
            player.prepare();
            player.setPlayWhenReady(true);
        });
    }

    /** Pauses decoding while keeping the player ready to resume quickly. */
    public void pausePlayback() {
        if (player != null) {
            player.setPlayWhenReady(false);
            player.pause();
        }
    }

    public void stopPlayback() {
        currentUrl = null;
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();
        }
    }

    private void ensurePlayer() {
        if (player != null) return;
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 30_000, 1_000, 1_500)
                .build();
        player = new ExoPlayer.Builder(getContext())
                .setLoadControl(loadControl)
                .build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVolume(0f);
        player.setPlayWhenReady(true);
        playerView.setPlayer(player);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        currentUrl = null;
        super.onDetachedFromWindow();
    }
}
