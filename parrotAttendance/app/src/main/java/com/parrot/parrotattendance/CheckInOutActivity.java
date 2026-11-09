package com.parrot.parrotattendance;

import android.view.View;
import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;
import com.parrot.parrotattendance.api.ApiClient;
import com.parrot.parrotattendance.api.ApiService;
import com.parrot.parrotattendance.models.AttendanceResponse;
import com.parrot.parrotattendance.models.Office;
import com.parrot.parrotattendance.models.User;
import com.parrot.parrotattendance.utils.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class CheckInOutActivity extends AppCompatActivity {

    private static final String TAG = "CHECKIN_OUT_ACTIVITY";
    private static final int PERMISSION_LOCATION = 100;

    private TextView statusText, distanceText;
    private Button btnCheckIn, btnCheckOut;
    private ProgressBar progress;

    private FusedLocationProviderClient locationClient;

    private double userLat = 0, userLng = 0;
    private double officeLat = 0, officeLng = 0;
    private int officeRadius = 0;

    private boolean gpsReady = false;

    private ApiService api;
    private User user;
    private Office office;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkinout);

        Log.d(TAG, "Activity Started.");

        // ============================
        // Bind UI
        // ============================
        statusText = findViewById(R.id.statusText);
        distanceText = findViewById(R.id.distanceText);
        btnCheckIn = findViewById(R.id.btnCheckInNow);
        btnCheckOut = findViewById(R.id.btnCheckOutNow);
        progress = findViewById(R.id.progressCheck);

        // API + Session
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        api = ApiClient.getClient().create(ApiService.class);

        user = SessionManager.getUser(this);
        office = SessionManager.getOffice(this);

        if (user == null || office == null) {
            showAlert("Error", "Session expired. Please login again.");
            finish();
            return;
        }

        officeLat = office.latitude;
        officeLng = office.longitude;
        officeRadius = office.radius_meters;

        Log.d(TAG, "Office → lat:" + officeLat + " lng:" + officeLng + " radius:" + officeRadius);

        if (checkPermission()) startLocationUpdates();
        else requestPermission();

        // Buttons
        btnCheckIn.setOnClickListener(v -> attemptAction("checkin"));
        btnCheckOut.setOnClickListener(v -> attemptAction("checkout"));
    }

    // ======================================
    // PERMISSIONS
    // ======================================
    private boolean checkPermission() {
        return ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_LOCATION
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == PERMISSION_LOCATION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startLocationUpdates();
        } else {
            showAlert("Permission Required", "Location permission is required.");
        }
    }

    // ======================================
    // GPS TRACKING
    // ======================================
    private void startLocationUpdates() {

        LocationRequest req = LocationRequest.create()
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        locationClient.requestLocationUpdates(req, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {

                Location loc = result.getLastLocation();
                if (loc == null) return;

                gpsReady = true;

                userLat = loc.getLatitude();
                userLng = loc.getLongitude();

                double dist = distance(userLat, userLng, officeLat, officeLng);
                distanceText.setText("Distance: " + (int) dist + "m");

                if (dist <= officeRadius) {
                    statusText.setText("✅ Inside Office");
                    statusText.setTextColor(0xFF2E7D32);
                } else {
                    statusText.setText("❌ Outside Office");
                    statusText.setTextColor(0xFFC62828);
                }
            }
        }, getMainLooper());
    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        float[] res = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, res);
        return res[0];
    }

    // ======================================
    // ATTENDANCE SUBMISSION
    // ======================================
    private void attemptAction(String action) {

        if (!gpsReady) {
            showAlert("GPS Error", "Waiting for GPS...");
            return;
        }

        double dist = distance(userLat, userLng, officeLat, officeLng);

        if (dist > officeRadius) {
            showAlert("Attendance Declined", "You are outside the office radius.");
            return;
        }

        sendAttendance(action);
    }

    private void sendAttendance(String action) {

        progress.setVisibility(View.VISIBLE);

        Call<AttendanceResponse> call = api.attendance(
                user.id,
                action,
                userLat,
                userLng,
                "Android-App",
                "Africa/Nairobi",
                0 // mock gps
        );

        call.enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {

                progress.setVisibility(View.GONE);

                if (!response.isSuccessful()) {
                    showAlert("Server Error", "HTTP " + response.code());
                    return;
                }

                AttendanceResponse res = response.body();

                if (res == null) {
                    showAlert("Error", "Invalid server response.");
                    return;
                }

                if (res.isSuccess())
                    showAlert("Success", res.message);
                else
                    showAlert("Failed", res.message);
            }

            @Override
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                progress.setVisibility(View.GONE);
                showAlert("Network Error", t.getMessage());
            }
        });
    }

    // ======================================
    // ALERT DIALOG
    // ======================================
    private void showAlert(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
