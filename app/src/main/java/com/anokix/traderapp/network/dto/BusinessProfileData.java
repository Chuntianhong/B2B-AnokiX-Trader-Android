package com.anokix.traderapp.network.dto;

import java.util.List;

/** data block of GET /api/trader/business/profile (read-only display in Settings). */
public class BusinessProfileData {
    public Profile profile;

    public static class Profile {
        public String trader_code;
        public String business_name;
        public String trading_name;
        public String business_email;
        public String business_phone_number;
        public String business_registration_number;
        public String id_number;
        public String passport_number;
        public String address;
        public String status;
        public String registration_status;
        public String company_logo_url;
        // Editor fields (Settings → Business Profile).
        public String trader_type;
        public String primary_product_category_ids;
        public String preferred_distributor_ids;
        public String preferred_delivery_days;
        public String payment_method;
        public Double latitude;
        public Double longitude;
        // Distributor-style fields (design parity; not yet returned by the trader API).
        public String vat_number;
        public String business_type;
        public String business_established_year;
        public String employee_count;
        public String annual_turnover;
        public String bank_name;
        public String account_holder_name;
        public String account_number;
        public String account_type;
        public String branch_code;
        public String distribution_coverage;
        public String active_clients_count;
        public String delivery_fleet_size;
        /** Either a JSON array or a JSON-encoded string of contacts, depending on endpoint. */
        public com.google.gson.JsonElement contact_persons;
        public Documents documents;
    }

    public static class Documents {
        public List<Doc> registration_document;
        public List<Doc> id_document;
        public List<Doc> proof_of_address;
        public List<Doc> store_front_photo;
        public List<Doc> store_interior_photo;
        public List<Doc> additional_document;
        // Distributor-style document slots (design parity).
        public List<Doc> company_registration_certificate;
        public List<Doc> tax_clearance_certificate;
        public List<Doc> vat_registration_certificate;
        public List<Doc> bank_confirmation_letter;
    }

    public static class Doc {
        public long id;
        public String file_url;
        public String original_name;
    }
}
