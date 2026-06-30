package com.anokix.trader.network.dto;

import java.util.List;

/**
 * data block of GET /api/common/base-info. Models the lookup lists the app needs:
 * currencies + timezones (store editor) and brands / product categories / units
 * (Create Product wizard).
 */
public class BaseInfoData {
    public User user;
    public List<Currency> currencies;
    public List<Timezone> timezones;
    public List<Brand> brands;
    public List<ProductCategory> product_categories;
    public List<ProductUnit> product_units;

    /** The signed-in user (Settings → Profile prefill). */
    public static class User {
        public String id;
        public String email;
        public String first_name;
        public String last_name;
        public String phone_number;
        public String role_label;
        public String role_type;
    }

    public static class Currency {
        public String id;
        public String code;
        public String name;
        public String symbol;
    }

    public static class Timezone {
        public String name;
        public String region;
        public String label;
        public String time_zone;
        public long time_offset;
    }

    public static class Brand {
        public long id;
        public String name;
        public Long supplier_id;
        public String logo_url;
    }

    public static class ProductCategory {
        public long id;
        public String title;
        public String slug;
        public String description;
        public Long parent_id;
    }

    public static class ProductUnit {
        public String id;
        public String name;
    }
}
