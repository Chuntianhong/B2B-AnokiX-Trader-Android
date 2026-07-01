package com.anokix.traderapp.network.dto;

/**
 * data block of POST /api/common/login. Only the fields the app consumes are modelled.
 */
public class LoginData {
    public String token;
    public User user;
    public String portal_type;
    public PortalInfo portal_info;

    public static class User {
        public String id;
        public String first_name;
        public String last_name;
        public String email;
        public String phone_number;
        public String role_type;
        public String role_label;
    }

    /** Trader profile returned when {@code portal_type} is {@code trader}. */
    public static class PortalInfo {
        public String id;
        public String user_id;
        public String business_name;
        public String trading_name;
        public String company_logo;
        public String company_logo_url;
        public String id_number;
        public String passport_number;
        public String business_registration_number;
        public String business_phone_number;
        public String business_email;
        public String trader_type;
        public String primary_product_category_ids;
        public String trader_code;
        public String address;
        public String latitude;
        public String longitude;
        public String preferred_distributor_ids;
        public String recommended_distributor_id;
        public String preferred_delivery_days;
        public String payment_method;
        public String registration_status;
        public String status;

        /** Trading name, then legal business name. */
        public String displayStoreName() {
            if (trading_name != null && !trading_name.trim().isEmpty()) {
                return trading_name.trim();
            }
            if (business_name != null && !business_name.trim().isEmpty()) {
                return business_name.trim();
            }
            return "";
        }
    }
}
