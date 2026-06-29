package com.anokix.trader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anokix.trader.R;
import com.anokix.trader.network.ApiCallback;
import com.anokix.trader.network.ApiClient;
import com.anokix.trader.network.dto.DistributorListData;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Distributors screen. Lists the distributors the trader is partnered with
 * (GET api/trader/distributors). Tapping "View Distributor" opens
 * {@link DistributorDetailActivity}, where a change of distributor can be
 * requested.
 */
public class DistributorsActivity extends AppCompatActivity {

    private final List<DistributorListData.Distributor> items = new ArrayList<>();
    private DistributorAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView list;
    private View loading;
    private View emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distributors);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.emptyView);

        list = findViewById(R.id.distributorList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DistributorAdapter();
        list.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.purple_primary);
        swipeRefresh.setOnRefreshListener(() -> load(false));

        load(true);
    }

    private void load(boolean showSpinner) {
        if (showSpinner) {
            loading.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            list.setVisibility(View.GONE);
        }
        ApiClient.get(this).getDistributors(new ApiCallback<DistributorListData>() {
            @Override
            public void onSuccess(DistributorListData data) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                items.clear();
                if (data != null && data.distributors != null) {
                    items.addAll(data.distributors);
                }
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(DistributorsActivity.this, message, Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openDetail(DistributorListData.Distributor d) {
        Intent intent = new Intent(this, DistributorDetailActivity.class);
        intent.putExtra(DistributorDetailActivity.EXTRA_DISTRIBUTOR, d);
        startActivity(intent);
    }

    // ---- Adapter ---------------------------------------------------------

    private class DistributorAdapter extends RecyclerView.Adapter<DistributorAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_distributor_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DistributorListData.Distributor d = items.get(position);
            h.logo.setText(d.initial());
            h.name.setText(d.displayName());
            h.products.setText(d.products_available + "+");
            h.coverage.setText(notEmpty(d.address) ? d.address : "—");
            h.btnView.setOnClickListener(v -> openDetail(d));
            h.itemView.setOnClickListener(v -> openDetail(d));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView logo, name, products, coverage;
            final MaterialButton btnView;

            VH(@NonNull View v) {
                super(v);
                logo = v.findViewById(R.id.distributorLogo);
                name = v.findViewById(R.id.distributorName);
                products = v.findViewById(R.id.distributorProducts);
                coverage = v.findViewById(R.id.distributorCoverage);
                btnView = v.findViewById(R.id.btnView);
            }
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
