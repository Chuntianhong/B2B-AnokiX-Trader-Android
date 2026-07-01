package com.anokix.traderapp.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.MockData;
import com.anokix.traderapp.messaging.PushManager;
import com.anokix.traderapp.model.MenuItem;
import com.anokix.traderapp.session.SessionManager;
import com.anokix.traderapp.ui.adapter.DrawerMenuAdapter;
import com.anokix.traderapp.ui.fragment.DashboardFragment;
import com.anokix.traderapp.ui.fragment.MoreFragment;
import com.anokix.traderapp.ui.fragment.OrdersFragment;
import com.anokix.traderapp.ui.fragment.SellFragment;
import com.anokix.traderapp.ui.fragment.WalletFragment;
import com.anokix.traderapp.ui.views.TypefaceCache;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    /** Intent extra: open a specific bottom-nav tab ("home"/"sell"/"orders"/"wallet"/"more"). */
    public static final String EXTRA_OPEN_TAB = "open_tab";

    private DrawerLayout drawerLayout;
    private BottomNavigationView bottomNav;
    private DrawerMenuAdapter drawerAdapter;

    /** POST_NOTIFICATIONS prompt (Android 13+); result is ignored — pushes degrade gracefully. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        bottomNav = findViewById(R.id.bottomNav);

        setupDrawer();
        setupBottomNav();

        if (savedInstanceState == null) {
            if (!selectTabFromIntent(getIntent())) {
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
        }

        // FCM: ensure the channel exists, ask for the runtime permission, register the
        // token, then apply any routing a notification tap carried in.
        PushManager.ensureChannel(this);
        requestNotificationPermission();
        PushManager.syncToken(this);
        applyPushRouting(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectTabFromIntent(intent);
        applyPushRouting(intent);
    }

    /** Open the screen a tapped notification points at (order/return/stock), then clear it. */
    private void applyPushRouting(Intent intent) {
        PushManager.handleRouting(this, intent);
        PushManager.clearRouting(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Returns true if the intent requested (and we applied) a specific tab. */
    private boolean selectTabFromIntent(Intent intent) {
        if (intent == null) return false;
        String tab = intent.getStringExtra(EXTRA_OPEN_TAB);
        if (tab == null) return false;
        switch (tab) {
            case "sell":   bottomNav.setSelectedItemId(R.id.nav_sell);   return true;
            case "orders": bottomNav.setSelectedItemId(R.id.nav_orders); return true;
            case "wallet": bottomNav.setSelectedItemId(R.id.nav_wallet); return true;
            case "more":   bottomNav.setSelectedItemId(R.id.nav_more);   return true;
            default:       bottomNav.setSelectedItemId(R.id.nav_home);   return true;
        }
    }

    private void setupBottomNav() {
        styleBottomNavMenu();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            String key;
            if (id == R.id.nav_sell) {
                fragment = new SellFragment();
                key = "sell";
            } else if (id == R.id.nav_orders) {
                fragment = new OrdersFragment();
                key = "orders";
            } else if (id == R.id.nav_wallet) {
                fragment = new WalletFragment();
                key = "wallet";
            } else if (id == R.id.nav_more) {
                fragment = new MoreFragment();
                key = "more";
            } else {
                fragment = new DashboardFragment();
                key = "home";
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            if (drawerAdapter != null) {
                drawerAdapter.setSelectedByKey(key);
            }
            return true;
        });
    }

    private void styleBottomNavMenu() {
        float textSizeSp = getResources().getDimension(R.dimen.bottom_nav_text_size)
                / getResources().getDisplayMetrics().scaledDensity;
        bottomNav.post(() -> TypefaceCache.applyFontToBottomNavigationViewMenu(bottomNav, this, textSizeSp));
    }

    private void setupDrawer() {
        RecyclerView drawerList = findViewById(R.id.drawerMenuList);
        drawerList.setLayoutManager(new LinearLayoutManager(this));
        drawerAdapter = new DrawerMenuAdapter(MockData.getDrawerMenuItems(), this::onDrawerItemClicked);
        drawerList.setAdapter(drawerAdapter);
        bindDrawerProfile();
    }

    private void bindDrawerProfile() {
        SessionManager session = SessionManager.get(this);
        String storeName = session.getStoreDisplayName();
        if (storeName.isEmpty()) {
            storeName = "Sipho's Spaza";
        }
        String accountName = session.getFullName();
        if (accountName.isEmpty()) {
            accountName = session.getRoleLabel();
        }
        if (accountName.isEmpty()) {
            accountName = "Trader Account";
        }

        android.widget.TextView nameView = findViewById(R.id.drawerProfileName);
        android.widget.TextView emailView = findViewById(R.id.drawerProfileEmail);
        android.widget.TextView avatar = findViewById(R.id.drawerProfileAvatar);
        nameView.setText(storeName);
        emailView.setText(accountName);
        avatar.setText(storeName.substring(0, 1).toUpperCase());

        findViewById(R.id.drawerLogout).setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout, (d, w) -> {
                    PushManager.unregister(this);   // drop this device's token server-side
                    SessionManager.get(this).clear();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .show();
    }

    private void onDrawerItemClicked(MenuItem item) {
        closeDrawer();
        switch (item.key) {
            case "home":
                bottomNav.setSelectedItemId(R.id.nav_home);
                break;
            case "sell":
                bottomNav.setSelectedItemId(R.id.nav_sell);
                break;
            case "orders":
                bottomNav.setSelectedItemId(R.id.nav_orders);
                break;
            case "wallet":
                bottomNav.setSelectedItemId(R.id.nav_wallet);
                break;
            case "inventory":
                startActivity(new Intent(this, InventoryActivity.class));
                break;
            case "goods_received":
                startActivity(new Intent(this, GoodsReceivedActivity.class));
                break;
            case "goods_returns":
                startActivity(new Intent(this, GoodsReturnsActivity.class));
                break;
            case "sales":
                startActivity(new Intent(this, SalesActivity.class));
                break;
            case "invoices":
                startActivity(new Intent(this, InvoicesActivity.class));
                break;
            case "distributors":
                startActivity(new Intent(this, DistributorsActivity.class));
                break;
            case "marketplace":
                startActivity(new Intent(this, MarketplaceActivity.class));
                break;
            case "cart":
                startActivity(new Intent(this, CartActivity.class));
                break;
            case "payments":
                startActivity(new Intent(this, PaymentsActivity.class));
                break;
            case "airtime":
                startActivity(new Intent(this, AirtimeActivity.class));
                break;
            case "rewards":
                startActivity(new Intent(this, RewardsActivity.class));
                break;
            case "promotions":
                startActivity(new Intent(this, OffersActivity.class));
                break;
            case "customers":
                startActivity(new Intent(this, CustomersActivity.class));
                break;
            case "analytics":
                startActivity(new Intent(this, AnalyticsActivity.class));
                break;
            case "reports":
                startActivity(new Intent(this, ReportsActivity.class));
                break;
            case "notifications":
                startActivity(new Intent(this, NotificationsActivity.class));
                break;
            case "support":
                startActivity(new Intent(this, SupportActivity.class));
                break;
            case "settings":
                startActivity(new Intent(this, SettingsActivity.class));
                break;
        }
    }

    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(findViewById(R.id.drawerContent));
        }
    }

    public void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
    }
}
