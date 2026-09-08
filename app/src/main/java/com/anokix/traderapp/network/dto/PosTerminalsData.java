package com.anokix.traderapp.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code data} block of {@code GET api/common/pos/terminals} — the card machines
 * (PayCloud terminals) bound to the trader, offered when a POS sale is paid by card.
 *
 * <p>{@code configured} is the store-level flag: false means no card machine has been
 * set up at all, which is a different message to "set up, but none active right now".
 */
public class PosTerminalsData {

    public boolean configured;
    public List<Terminal> terminals;

    /**
     * The machines that can actually take a payment. Inactive terminals are still
     * returned by the API (so the portal can list them), but sending to one would
     * only fail at the gateway, so they are never offered as a choice.
     */
    public List<Terminal> activeTerminals() {
        List<Terminal> active = new ArrayList<>();
        if (terminals != null) {
            for (Terminal t : terminals) {
                if (t != null && t.isActive) {
                    active.add(t);
                }
            }
        }
        return active;
    }

    public static class Terminal {
        public int id;
        @SerializedName("terminal_sn")
        public String terminalSn;
        public String label;
        @SerializedName("store_no")
        public String storeNo;
        @SerializedName("is_active")
        public boolean isActive;
        @SerializedName("last_used_at")
        public String lastUsedAt;

        /**
         * What the cashier sees in the picker. Traders name their machines ("Front
         * counter"), but the label is nullable, so fall back to the serial number —
         * never an empty row that cannot be told apart from the next one.
         */
        public String displayName() {
            if (label != null && !label.trim().isEmpty()) {
                return label.trim();
            }
            if (terminalSn != null && !terminalSn.trim().isEmpty()) {
                return terminalSn.trim();
            }
            return "Card machine #" + id;
        }
    }
}
