package com.example.mocklocationtest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 10;
    private LocationManager locationManager;
    private EditText latitude;
    private EditText longitude;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Mock Location Test");
        title.setTextSize(22);
        root.addView(title);

        latitude = field("Latitude", "37.7749");
        longitude = field("Longitude", "-122.4194");
        root.addView(latitude);
        root.addView(longitude);

        Button settingsButton = new Button(this);
        settingsButton.setText("Open developer mock-location settings");
        settingsButton.setOnClickListener(v -> openMockLocationSettings());
        root.addView(settingsButton);

        Button sendButton = new Button(this);
        sendButton.setText("Send test location");
        sendButton.setOnClickListener(v -> sendLocation());
        root.addView(sendButton);

        status = new TextView(this);
        status.setText("Select this app as the mock location app in Developer options.");
        root.addView(status);
        setContentView(root);
    }

    private EditText field(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(8194);
        return field;
    }

    private void openMockLocationSettings() {
        startActivity(new android.content.Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    }

    private void sendLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            return;
        }

        try {
            double lat = Double.parseDouble(latitude.getText().toString());
            double lon = Double.parseDouble(longitude.getText().toString());
            String provider = LocationManager.GPS_PROVIDER;
            if (!locationManager.isProviderEnabled(provider)) {
                status.setText("Enable GPS before sending a test location.");
                return;
            }
            if (!locationManager.getAllProviders().contains(provider)) {
                status.setText("GPS provider is unavailable.");
                return;
            }
            try {
                locationManager.addTestProvider(provider, false, false, false, false,
                        true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            } catch (IllegalArgumentException ignored) {
                // The provider already exists as a test provider.
            }
            locationManager.setTestProviderEnabled(provider, true);
            Location location = new Location(provider);
            location.setLatitude(lat);
            location.setLongitude(lon);
            location.setAccuracy(3.0f);
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            locationManager.setTestProviderLocation(provider, location);
            status.setText(String.format(Locale.US, "Sent %.6f, %.6f", lat, lon));
        } catch (SecurityException e) {
            status.setText("Mock location permission is not active for this app.");
        } catch (NumberFormatException e) {
            status.setText("Enter valid numeric coordinates.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("Location permission granted. Press Send test location again.");
        }
    }
}
