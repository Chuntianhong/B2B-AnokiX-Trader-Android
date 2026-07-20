package com.anokix.traderapp.network.dto;

import java.util.List;

/**
 * data block of GET /api/trader/dashboard. Mirrors the trader web-portal dashboard:
 * the four stat blocks (today's sales, anokiX wallet, anokiX rewards, pending orders),
 * the running promotions carousel, recent orders and top selling products.
 */
public class TraderDashboardData {
    public Dashboard dashboard;

    public static class Dashboard {
        public Meta meta;
        public TodaySales today_sales;
        public Wallet wallet;
        public Rewards rewards;
        public PendingOrders pending_orders;
        public List<Promotion> promotions;
        public List<RecentOrder> recent_orders;
        public List<TopProduct> top_products;
    }

    public static class Meta {
        public Currency currency;
    }

    public static class Currency {
        public String code;
        public String symbol;
    }

    // ---- Block 1: Today's Sales -------------------------------------------

    public static class TodaySales {
        public double amount;
        public int transactions;
        public int items;
        /** null when there is no comparable previous period. */
        public Double delta_percent;
        /** "up" / "down" / null. */
        public String trend;
    }

    // ---- Block 2: anokiX wallet (powered by IMB) --------------------------

    public static class Wallet {
        public String status;
        public String account_number;
        public String activated_at;
        public String last_error;
        public boolean configured;
        public boolean payments_enabled;
        public Balance balance;
    }

    public static class Balance {
        public double current;
        public double available;
        public double pending;
        public List<WalletTxn> transactions;
        /** ISO-8601 timestamp the balance was read at. */
        public String as_of;
        public boolean stale;
    }

    public static class WalletTxn {
        public String date;
        /** Server sends this as a string, e.g. "-60.00". */
        public String amount;
        public long transaction_id;
        public boolean proof_of_payment;
        public boolean pending;
    }

    // ---- Block 3: anokiX rewards (powered by Limes) ------------------------

    public static class Rewards {
        public long points;
        public double value;
        public long earned_this_month;
    }

    // ---- Block 4: Pending Orders -------------------------------------------

    public static class PendingOrders {
        public int count;
        public double total_value;
    }

    // ---- Promotions carousel ------------------------------------------------

    public static class Promotion {
        public long id;
        public long distributor_id;
        public String campaign_type;
        public String name;
        public String description;
        public String objective;
        public String start_date;
        public String end_date;
        public BannerFile banner_file;
        public BannerFile video_file;
        public Distributor distributor;

        /** Banner image to show in the carousel, or null when the campaign has none. */
        public String bannerUrl() {
            return banner_file == null ? null : banner_file.file_url;
        }
    }

    public static class BannerFile {
        public long id;
        public String media_type;
        public String file_path;
        public String file_url;
        public String original_name;
    }

    public static class Distributor {
        public long id;
        public String distributor_code;
        public String display_name;
        public String trading_name;
        public String company_legal_name;
        public String logo_url;
    }

    // ---- Recent orders -------------------------------------------------------

    public static class RecentOrder {
        public long id;
        public String order_number;
        public String status;
        public String status_key;
        public double subtotal;
        public double total_amount;
        public String payment_method;
        public String payment_status;
        public String delivery_date;
        public Distributor distributor;
        /** "15/07/2026 13:58:04" */
        public String created_at;
        public String updated_at;
    }

    // ---- Top selling products ------------------------------------------------

    public static class TopProduct {
        public long product_id;
        public String name;
        public String sku;
        public double quantity;
        public double revenue;
        public String image_url;
    }
}
