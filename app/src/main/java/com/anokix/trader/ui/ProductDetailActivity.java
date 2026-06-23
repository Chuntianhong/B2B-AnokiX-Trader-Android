package com.anokix.trader.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.trader.R;
import com.anokix.trader.model.MarketCart;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.CartData;
import com.anokix.trader.network.dto.CommonProductData;
import com.anokix.trader.network.dto.MarketplaceData;
import com.bumptech.glide.Glide;

import java.util.Locale;

/**
 * Add to Cart screen (mirrors Add_to_Cart.png). Shows the product passed from the
 * marketplace immediately, then refreshes from GET /api/common/product-detail.
 * Adjusting the quantity and tapping Add to Cart updates the local {@link MarketCart}
 * and POSTs to /api/trader/cart/add.
 */
public class ProductDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "product_id";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_IMAGE = "image";
    public static final String EXTRA_PRICE = "price";
    public static final String EXTRA_BRAND = "brand";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_SKU = "sku";
    public static final String EXTRA_BARCODE = "barcode";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_STOCK = "stock";
    public static final String EXTRA_DISTRIBUTOR = "distributor";
    public static final String EXTRA_CURRENCY = "currency";

    private final ApiClient api = ApiClient.get(this);

    private MarketplaceData.Product product = new MarketplaceData.Product();
    private String currency = "R";
    private String distributorName = "";
    private int quantity = 1;
    private int stock = 0;

    private TextView qtyView, priceView, stockCountView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        currency = getStringExtra(EXTRA_CURRENCY, "R");
        distributorName = getStringExtra(EXTRA_DISTRIBUTOR, "");
        stock = getIntent().getIntExtra(EXTRA_STOCK, 0);

        product.id = getStringExtra(EXTRA_PRODUCT_ID, "");
        product.name = getStringExtra(EXTRA_NAME, "");
        product.image_url = getStringExtra(EXTRA_IMAGE, null);
        product.brand_name = getStringExtra(EXTRA_BRAND, "");
        product.category_title = getStringExtra(EXTRA_CATEGORY, "");
        product.sku = getStringExtra(EXTRA_SKU, "");
        product.barcode = getStringExtra(EXTRA_BARCODE, "");
        product.description = getStringExtra(EXTRA_DESCRIPTION, "");
        product.selling_price = String.valueOf(getIntent().getDoubleExtra(EXTRA_PRICE, 0));
        product.stock = stock;

        qtyView = findViewById(R.id.detailQty);
        priceView = findViewById(R.id.detailPrice);
        stockCountView = findViewById(R.id.detailStockCount);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.detailMinus).setOnClickListener(v -> changeQty(-1));
        findViewById(R.id.detailPlus).setOnClickListener(v -> changeQty(1));
        findViewById(R.id.btnAddToCart).setOnClickListener(v -> addToCart());

        bind();
        loadDetail();
    }

    private void loadDetail() {
        if (product.id == null || product.id.isEmpty()) return;
        api.getCommonProduct(product.id, new ApiCallback<CommonProductData>() {
            @Override
            public void onSuccess(CommonProductData result) {
                if (result != null && result.product != null) {
                    product = result.product;
                    stock = product.stockCount();
                    bind();
                }
            }

            @Override
            public void onError(String message) {
                // Keep the values passed from the marketplace.
            }
        });
    }

    private void bind() {
        ImageView image = findViewById(R.id.detailImage);
        ((TextView) findViewById(R.id.detailName)).setText(safe(product.name));
        ((TextView) findViewById(R.id.detailBrand)).setText(
                product.brand_name != null && !product.brand_name.isEmpty()
                        ? getString(R.string.by_brand, product.brand_name) : "");
        priceView.setText(product.formattedPrice(currency));
        ((TextView) findViewById(R.id.detailDescription)).setText(safe(product.description));

        boolean inStock = product.inStock() || stock > 0;
        TextView stockLabel = findViewById(R.id.detailStock);
        stockLabel.setText(inStock ? R.string.in_stock : R.string.out_of_stock);
        stockCountView.setText(String.format(Locale.US, "(%d %s)",
                Math.max(stock, product.stockCount()), getString(R.string.in_stock_suffix)));

        infoRow(R.id.rowDistributor, getString(R.string.distributor), distributorName);
        infoRow(R.id.rowCategory, getString(R.string.category),
                product.category_title != null ? product.category_title : "");
        infoRow(R.id.rowSku, getString(R.string.sku), product.sku != null ? product.sku : "");
        infoRow(R.id.rowBarcode, getString(R.string.barcode),
                product.barcode != null ? product.barcode : "");

        if (product.imageUrl() != null) {
            Glide.with(this).load(product.imageUrl()).centerCrop().into(image);
        }
        qtyView.setText(String.valueOf(quantity));
    }

    private void infoRow(int includeId, String label, String value) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.infoLabel)).setText(label);
        ((TextView) root.findViewById(R.id.infoValue)).setText(value);
    }

    private void changeQty(int delta) {
        int max = Math.max(1, Math.max(stock, product.stockCount()));
        quantity = Math.max(1, Math.min(quantity + delta, max));
        qtyView.setText(String.valueOf(quantity));
    }

    private void addToCart() {
        double unitPrice = product.priceValue();
        // Optimistic local update so the badge bumps immediately.
        MarketCart.get().add(product, quantity, unitPrice);
        api.addToCart(product.id, quantity, unitPrice, product.barcode, product.sku,
                new ApiCallback<CartData>() {
                    @Override
                    public void onSuccess(CartData result) {
                        // Re-pull the authoritative cart so the local mirror picks up
                        // the server-assigned cart item ids and totals.
                        api.getCart(new ApiCallback<CartData>() {
                            @Override public void onSuccess(CartData c) { MarketCart.get().hydrate(c); }
                            @Override public void onError(String message) {}
                        });
                    }

                    @Override
                    public void onError(String message) {
                        // Local cart already updated; the server sync is best-effort.
                    }
                });
        Toast.makeText(this, getString(R.string.added_to_cart, product.name), Toast.LENGTH_SHORT).show();
        finish();
    }

    private String getStringExtra(String key, String fallback) {
        String v = getIntent().getStringExtra(key);
        return v != null ? v : fallback;
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
