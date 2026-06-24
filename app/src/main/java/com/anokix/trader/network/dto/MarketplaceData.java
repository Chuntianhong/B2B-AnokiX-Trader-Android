package com.anokix.trader.network.dto;

import com.anokix.trader.network.MediaUrls;

import java.util.List;
import java.util.Locale;

/**
 * data block of GET /api/trader/marketplace.
 *
 * Mirrors the Trader Portal marketplace page: the active distributor, promo
 * banners, business-insight summary cards, recommended / best-seller products,
 * exclusive promotions, and the trader's own distributor list (for the
 * "Change Distributor" picker).
 */
public class MarketplaceData {

    public Meta meta;
    public Distributor selected_distributor;
    public List<Banner> banners;
    public List<Product> recommended;
    public List<Product> best_sellers;
    public List<Promotion> promotions;
    public BusinessInsights business_insights;
    public List<Distributor> my_distributors;

    /** Currency symbol for the page (defaults to the SA Rand "R"). */
    public String currencySymbol() {
        if (meta != null && meta.currency != null && meta.currency.symbol != null
                && !meta.currency.symbol.trim().isEmpty()) {
            return meta.currency.symbol;
        }
        return "R";
    }

    public static class Meta {
        public Currency currency;
    }

    public static class Currency {
        public String code;
        public String symbol;
    }

    public static class Distributor {
        public long id;
        public String distributor_code;
        public String company_legal_name;
        public String trading_name;
        public String display_name;
        public String company_email;
        public String company_phone_number;
        public String address;
        public String status;
        public String logo_url;
        public String preferred_delivery_days;

        public String displayName() {
            if (notEmpty(display_name)) return display_name;
            if (notEmpty(trading_name)) return trading_name;
            if (notEmpty(company_legal_name)) return company_legal_name;
            return distributor_code != null ? distributor_code : "Distributor";
        }
    }

    public static class Banner {
        public long id;
        public long campaign_id;
        public long distributor_id;
        public String campaign_type;
        public String name;
        public String description;
        public Media banner_file;
        public Media video_file;

        public String mediaUrl() {
            if (video_file != null && notEmpty(video_file.file_url)) {
                return MediaUrls.resolve(video_file.file_url);
            }
            if (banner_file != null && notEmpty(banner_file.file_url)) {
                return MediaUrls.resolve(banner_file.file_url);
            }
            return null;
        }

        public boolean isVideo() {
            return video_file != null && notEmpty(video_file.file_url);
        }
    }

    public static class Promotion {
        public long id;
        public long distributor_id;
        public String campaign_type;
        public String name;
        public String description;
        public Media banner_file;
        public Media video_file;

        public String mediaUrl() {
            if (banner_file != null && notEmpty(banner_file.file_url)) {
                return MediaUrls.resolve(banner_file.file_url);
            }
            if (video_file != null && notEmpty(video_file.file_url)) {
                return MediaUrls.resolve(video_file.file_url);
            }
            return null;
        }

        public boolean isVideo() {
            return (banner_file == null || !notEmpty(banner_file.file_url))
                    && video_file != null && notEmpty(video_file.file_url);
        }
    }

    public static class Media {
        public long id;
        public String media_type;
        public String file_path;
        public String file_url;
        public String original_name;
    }

    public static class BusinessInsights {
        public boolean is_static_data;
        public List<SummaryCard> summary_cards;
    }

    public static class SummaryCard {
        public String key;
        public String label;
        public String formatted_value;
        public boolean is_money;
        public String detail;
        public String action_label;
        public Trend trend;

        public String subtitle() {
            if (trend != null && notEmpty(trend.label)) return trend.label;
            if (notEmpty(detail)) return detail;
            if (notEmpty(action_label)) return action_label;
            return null;
        }

        public boolean trendUp() {
            return trend != null && "up".equalsIgnoreCase(trend.direction);
        }
    }

    public static class Trend {
        public String direction;
        public Integer percentage;
        public Integer count;
        public String label;
    }

    /** Product card shared by recommended / best_sellers and the product-detail call. */
    public static class Product {
        public String id;
        public long distributor_id;
        public String name;
        public String sku;
        public String barcode;
        public String description;
        public String brand_name;
        public String unit_name;
        public String category_title;
        public String parent_category_title;
        public String selling_price;
        public String cost_price;
        public String promotion_price;
        public int stock;
        public String stock_quantity;
        public String available_for_ordering;
        public String primary_product_image_url;
        public String image_url;

        public double priceValue() {
            double v = parse(selling_price);
            return v > 0 ? v : parse(promotion_price);
        }

        public String formattedPrice(String symbol) {
            return symbol + String.format(Locale.US, "%,.2f", priceValue());
        }

        public String imageUrl() {
            if (notEmpty(image_url)) return image_url;
            return primary_product_image_url;
        }

        public boolean inStock() {
            if (stock > 0) return true;
            return parse(stock_quantity) > 0;
        }

        public int stockCount() {
            if (stock > 0) return stock;
            return (int) parse(stock_quantity);
        }

        private static double parse(String s) {
            if (s == null) return 0;
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
