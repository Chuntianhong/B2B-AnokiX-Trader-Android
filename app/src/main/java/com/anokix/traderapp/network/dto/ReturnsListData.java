package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * Response for GET api/trader/returns — the trader's Goods Returns (GRN) plus a
 * KPI summary. A return reduces stock immediately; the distributor reviews it and
 * may issue a credit note (shown once the return is approved).
 */
public class ReturnsListData {

    public List<Grn> returns;
    public Summary summary;

    /** One Goods Return Note (header + line items). */
    public static class Grn {
        public String id;
        public String grn_number;
        public String trader_id;
        public String distributor_id;
        public String source_grv_id;
        /** Overall reason key: damaged | expired | incorrect | overstock | recall | other. */
        public String reason;
        /** submitted | approved | rejected. */
        public String status;
        public String item_count;
        public String total_quantity;
        public String note;
        public String decision_note;
        public String approved_by;
        public String approved_at;
        public String credit_note_id;
        public String created_at;
        public String distributor_name;
        public String credit_note_number;
        public Double credit_note_total;
        public List<Item> items;

        public int lineCount() {
            return items == null ? 0 : items.size();
        }

        public boolean isApproved() {
            return "approved".equalsIgnoreCase(status);
        }

        public boolean hasCreditNote() {
            return credit_note_number != null && !credit_note_number.isEmpty();
        }
    }

    /** One returned line. */
    public static class Item {
        public int id;
        public int product_id;
        public String name;
        public int quantity;
        public String reason;
    }

    /** KPI cards at the top of the Returns screen. */
    public static class Summary {
        public int total_returns;
        public int approved;
        public int pending;
        public double credit_issued;
    }
}
