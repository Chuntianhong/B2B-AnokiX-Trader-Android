package com.anokix.traderapp.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.MockData;
import com.anokix.traderapp.model.MenuItem;
import com.anokix.traderapp.ui.NotificationBadge;
import com.anokix.traderapp.ui.AirtimeActivity;
import com.anokix.traderapp.ui.AnalyticsActivity;
import com.anokix.traderapp.ui.CustomersActivity;
import com.anokix.traderapp.ui.InventoryActivity;
import com.anokix.traderapp.ui.MarketplaceActivity;
import com.anokix.traderapp.ui.NotificationsActivity;
import com.anokix.traderapp.ui.OffersActivity;
import com.anokix.traderapp.ui.PaymentsActivity;
import com.anokix.traderapp.ui.RewardsActivity;
import com.anokix.traderapp.ui.SettingsActivity;
import com.anokix.traderapp.ui.SupportActivity;
import com.anokix.traderapp.ui.adapter.MenuAdapter;

public class MoreFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        RecyclerView list = view.findViewById(R.id.moreMenuList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new MenuAdapter(MockData.getMoreMenuItems(), this::openScreen));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            NotificationBadge.refresh(getContext(), (TextView) getView().findViewById(R.id.notificationBadge));
        }
    }

    private void openScreen(MenuItem item) {
        Class<?> target;
        switch (item.key) {
            case "marketplace":
                target = MarketplaceActivity.class;
                break;
            case "inventory":
                target = InventoryActivity.class;
                break;
            case "payments":
                target = PaymentsActivity.class;
                break;
            case "airtime":
                target = AirtimeActivity.class;
                break;
            case "rewards":
                target = RewardsActivity.class;
                break;
            case "promotions":
                target = OffersActivity.class;
                break;
            case "customers":
                target = CustomersActivity.class;
                break;
            case "analytics":
                target = AnalyticsActivity.class;
                break;
            case "notifications":
                target = NotificationsActivity.class;
                break;
            case "support":
                target = SupportActivity.class;
                break;
            case "settings":
                target = SettingsActivity.class;
                break;
            default:
                return;
        }
        startActivity(new Intent(requireContext(), target));
    }
}
