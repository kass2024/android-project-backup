package com.parrot.parrotattendance;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.gson.Gson;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;     // <-- IMPORTANT
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import com.parrot.parrotattendance.models.Office;
import com.parrot.parrotattendance.utils.SessionManager;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "ATTENDANCE_APP"; // LOGCAT TAG

    private GoogleMap mMap;
    private FusedLocationProviderClient locationClient;

    TextView statusText;

    private double officeLat;
    private double officeLng;
    private int officeRadius;

    private static final int LOCATION_PERMISSION = 100;

    private boolean officeCircleDrawn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Log.d(TAG, "MapsActivity started.");

        statusText = findViewById(R.id.statusText);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        // Load office from session
        Office office = SessionManager.getOffice(this);

        if (office == null) {
            Log.e(TAG, "❌ ERROR: office object is NULL in session.");
            Toast.makeText(this, "Office data not found!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "Office Loaded → " + new Gson().toJson(office));

        officeLat = office.latitude;
        officeLng = office.longitude;
        officeRadius = office.radius_meters;

        Log.d(TAG, "Office Coordinates → LAT=" + officeLat + " LNG=" + officeLng);
        Log.d(TAG, "Office Radius → " + officeRadius + " meters");

        if (officeLat == 0 || officeLng == 0) {
            Log.e(TAG, "❌ ERROR: Office LAT/LNG is ZERO — check database!");
        }

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            Log.d(TAG, "MapFragment found. Initializing map...");
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "❌ ERROR: MapFragment is NULL");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "Google Map is ready.");

        mMap = googleMap;

        if (checkPermission()) {
            Log.d(TAG, "Location permission GRANTED. Enabling GPS.");
            mMap.setMyLocationEnabled(true);
            startLocationTracking();
        } else {
            Log.w(TAG, "Location permission NOT GRANTED. Requesting...");
            requestPermission();
        }
    }

    private boolean checkPermission() {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION
        );
    }

    private void drawOfficeOnMap() {
        if (officeCircleDrawn) return;

        LatLng officeLocation = new LatLng(officeLat, officeLng);

        Log.d(TAG, "Drawing office circle on map...");

        mMap.addCircle(new CircleOptions()
                .center(officeLocation)
                .radius(officeRadius)
                .strokeColor(0xFF009688)
                .fillColor(0x33009688)
                .strokeWidth(3));

        mMap.addMarker(new MarkerOptions()
                .position(officeLocation)
                .title("Assigned Office"));

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(officeLocation, 17));

        officeCircleDrawn = true;
        Log.d(TAG, "Office circle drawn successfully.");
    }

    private void startLocationTracking() {
        Log.d(TAG, "Starting live GPS tracking...");

        LocationRequest request = LocationRequest.create()
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        locationClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {

                Location loc = result.getLastLocation();
                if (loc == null) {
                    Log.w(TAG, "WARNING: Location result is NULL");
                    return;
                }

                Log.d(TAG, "GPS Update → LAT=" + loc.getLatitude() + " LNG=" + loc.getLongitude());

                drawOfficeOnMap();

                boolean isMock = loc.isFromMockProvider();
                double dist = distance(
                        loc.getLatitude(),
                        loc.getLongitude(),
                        officeLat,
                        officeLng
                );

                Log.d(TAG, "Distance from office → " + dist + " meters");

                if (isMock) {
                    Log.e(TAG, "❌ FAKE GPS DETECTED");
                    statusText.setText("❌ Fake GPS detected!");
                    statusText.setTextColor(0xFFFF0000);
                    return;
                }

                if (dist <= officeRadius) {
                    statusText.setText("✅ Inside Office (" + (int) dist + "m)");
                    statusText.setTextColor(0xFF00AA00);
                } else {
                    statusText.setText("❌ Outside Office (" + (int) dist + "m)");
                    statusText.setTextColor(0xFFFF0000);
                }
            }
        }, getMainLooper());
    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return result[0];
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == LOCATION_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "Permission granted via dialog. Reloading map.");
            onMapReady(mMap);

        } else {
            Log.e(TAG, "❌ Permission denied by user.");
            Toast.makeText(this, "Location permission required!", Toast.LENGTH_LONG).show();
        }
    }
}
