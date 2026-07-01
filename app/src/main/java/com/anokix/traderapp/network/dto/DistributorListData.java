package com.anokix.traderapp.network.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Response for GET api/trader/distributors — the distributors the trader is
 * partnered with. The list card and detail screen render straight from this;
 * there is no KPI summary.
 */
public class DistributorListData {

    public List<Distributor> distributors;

    /** One distributor partner. {@code Serializable} so the whole row can be
     *  handed to {@code DistributorDetailActivity} via an Intent extra. */
    public static class Distributor implements Serializable {
        public int id;
        public String distributor_code;
        public String company_legal_name;
        public String trading_name;
        public String display_name;
        public String company_email;
        public String company_phone_number;
        public String contact_name;
        public String contact_email;
        public String contact_phone;
        public String address;
        public String status;
        /** Encoded availability string (e.g. "1:8.00-12,15-22/2:..."); not parsed for display. */
        public String preferred_delivery_days;
        public int products_available;
        /** May be null; kept as String so a numeric or null value both decode cleanly. */
        public String minimum_order;
        public String logo;
        public String logo_url;

        /** Best human label — prefer the registered legal name, then backend display, then trading. */
        public String displayName() {
            if (notEmpty(company_legal_name)) return company_legal_name;
            if (notEmpty(display_name)) return display_name;
            if (notEmpty(trading_name)) return trading_name;
            return distributor_code == null ? "Distributor" : distributor_code;
        }

        /** Single uppercase initial for the logo tile. */
        public String initial() {
            String n = displayName().trim();
            return n.isEmpty() ? "D" : n.substring(0, 1).toUpperCase();
        }

        private static boolean notEmpty(String s) {
            return s != null && !s.isEmpty();
        }
    }
}
