package com.anokix.traderapp.ui;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.AutocompleteData;
import com.anokix.traderapp.network.dto.CoordinateData;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Address picker for the Make Order screen — a search box (Google Places
 * autocomplete) plus an interactive Google Map: tap to drop a draggable pin or
 * drag it to refine, reverse-geocoding to fill the address. Mirrors the map +
 * autocomplete in {@link RegisterTraderActivity}. Returns the chosen
 * {@code EXTRA_ADDRESS} (+ optional lat/lng) to the caller.
 */
public class AddressPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_LAT = "latitude";
    public static final String EXTRA_LNG = "longitude";

    private final ApiClient api = ApiClient.get(this);

    private MapView mapView;
    private GoogleMap googleMap;
    private Marker marker;
    private EditText searchField;

    private Double pickedLat, pickedLng;
    private boolean suppressWatcher;

    private ListPopupWindow addressPopup;
    private final List<AutocompleteData.Prediction> predictions = new ArrayList<>();
    private final Handler autocompleteHandler = new Handler(Looper.getMainLooper());
    private Runnable autocompleteRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_picker);

        searchField = findViewById(R.id.etAddressSearch);
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        ((MaterialButton) findViewById(R.id.btnUseAddress)).setOnClickListener(v -> useAddress());

        String initial = getIntent().getStringExtra(EXTRA_ADDRESS);
        if (initial != null) setAddressText(initial);
        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            double lat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
            double lng = getIntent().getDoubleExtra(EXTRA_LNG, 0);
            if (lat != 0 || lng != 0) { pickedLat = lat; pickedLng = lng; }
        }

        setupAutocomplete();

        mapView = findViewById(R.id.pickerMap);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    // ---- Map -------------------------------------------------------------

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.setOnMapClickListener(latLng -> {
            pickedLat = latLng.latitude;
            pickedLng = latLng.longitude;
            placeMarker(latLng);
            reverseGeocode(latLng);
        });
        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker m) { }
            @Override public void onMarkerDrag(Marker m) { }
            @Override public void onMarkerDragEnd(Marker m) {
                pickedLat = m.getPosition().latitude;
                pickedLng = m.getPosition().longitude;
                reverseGeocode(m.getPosition());
            }
        });

        if (pickedLat != null && pickedLng != null) {
            moveMapTo(new LatLng(pickedLat, pickedLng), true);
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(-26.2041, 28.0473), 9f)); // Johannesburg, South Africa
        }
    }

    private void moveMapTo(LatLng latLng, boolean placePin) {
        if (googleMap == null) return;
        if (placePin) placeMarker(latLng);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    private void placeMarker(LatLng latLng) {
        if (googleMap == null) return;
        if (marker == null) {
            marker = googleMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
        } else {
            marker.setPosition(latLng);
        }
    }

    private void reverseGeocode(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(
                        latLng.latitude, latLng.longitude, 1);
                if (results != null && !results.isEmpty()
                        && results.get(0).getMaxAddressLineIndex() >= 0) {
                    final String line = results.get(0).getAddressLine(0);
                    runOnUiThread(() -> setAddressText(line));
                }
            } catch (Exception ignored) {
                // Offline / unavailable geocoder: the pin lat/lng is still captured.
            }
        }).start();
    }

    // ---- Autocomplete ----------------------------------------------------

    private void setupAutocomplete() {
        addressPopup = new ListPopupWindow(this);
        addressPopup.setAnchorView(searchField);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (suppressWatcher) return;
                String keyword = s.toString().trim();
                if (autocompleteRunnable != null) {
                    autocompleteHandler.removeCallbacks(autocompleteRunnable);
                }
                if (keyword.length() < 3) {
                    addressPopup.dismiss();
                    return;
                }
                autocompleteRunnable = () -> queryAutocomplete(keyword);
                autocompleteHandler.postDelayed(autocompleteRunnable, 350);
            }
        });
    }

    private void queryAutocomplete(String keyword) {
        api.getAddressAutocomplete(keyword, new ApiCallback<AutocompleteData>() {
            @Override
            public void onSuccess(AutocompleteData data) {
                predictions.clear();
                if (data != null && data.predictions != null) {
                    predictions.addAll(data.predictions);
                }
                showPredictions();
            }

            @Override public void onError(String message) { }
        });
    }

    private void showPredictions() {
        if (predictions.isEmpty()) {
            addressPopup.dismiss();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (AutocompleteData.Prediction p : predictions) {
            labels.add(p.description);
        }
        addressPopup.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));
        addressPopup.setOnItemClickListener((parent, view, position, id) -> {
            addressPopup.dismiss();
            AutocompleteData.Prediction p = predictions.get(position);
            setAddressText(p.description);
            resolvePlace(p.place_id);
        });
        addressPopup.show();
    }

    private void resolvePlace(String placeId) {
        if (placeId == null || placeId.isEmpty()) return;
        api.getCoordinateFromPlaceId(placeId, new ApiCallback<CoordinateData>() {
            @Override
            public void onSuccess(CoordinateData data) {
                if (data == null) return;
                pickedLat = data.latitude;
                pickedLng = data.longitude;
                if (data.formatted_address != null && !data.formatted_address.isEmpty()) {
                    setAddressText(data.formatted_address);
                }
                moveMapTo(new LatLng(data.latitude, data.longitude), true);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AddressPickerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setAddressText(String value) {
        suppressWatcher = true;
        searchField.setText(value);
        searchField.setSelection(value != null ? value.length() : 0);
        suppressWatcher = false;
    }

    // ---- Result ----------------------------------------------------------

    private void useAddress() {
        String value = searchField.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, R.string.no_address_set, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_ADDRESS, value);
        if (pickedLat != null && pickedLng != null) {
            result.putExtra(EXTRA_LAT, pickedLat);
            result.putExtra(EXTRA_LNG, pickedLng);
        }
        setResult(RESULT_OK, result);
        finish();
    }

    // ---- MapView lifecycle ----------------------------------------------

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null && outState != null) {
            mapView.onSaveInstanceState(outState);
        }
    }
}
