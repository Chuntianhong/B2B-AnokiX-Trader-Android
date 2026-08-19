package com.anokix.traderapp.ui.wallet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The landing spot for {@code anokix://topup-return}, the {@code return_url} handed to
 * PayCloud when a wallet top-up starts.
 *
 * When PayCloud is finished with the checkout page it redirects the browser here.
 * Android resolves the scheme to this activity, which relaunches {@link WalletActivity}
 * with {@code CLEAR_TOP | SINGLE_TOP} — that finishes the Custom Tab sitting above it
 * and reveals the Top Up sheet exactly as it was left, without recreating the wallet
 * underneath it. The sheet's own {@code onResume} then polls api/common/topup/status and
 * reports the outcome.
 *
 * It has to be exported for the browser to reach it, so it deliberately reads nothing
 * from the intent and carries nothing across: no caller can make it do anything beyond
 * bringing the app to the front. The wallet is only ever credited by PayCloud's signed
 * webhook, verified server-side, so a forged redirect here achieves nothing but a
 * status poll.
 */
public class TopupReturnActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent back = new Intent(this, WalletActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(back);
        finish();
    }
}
