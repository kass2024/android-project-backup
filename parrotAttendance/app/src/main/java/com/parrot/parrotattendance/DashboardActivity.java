package com.parrot.parrotattendance;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.parrot.parrotattendance.models.User;
import com.parrot.parrotattendance.utils.SessionManager;

public class DashboardActivity extends AppCompatActivity {

    private TextView txtUserName, txtUserRole;
    private Button btnStartAttendance, btnMap, btnHistory, btnSalary, btnLogout;

    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Load user session
        user = SessionManager.getUser(this);

        if (user == null) {
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Bind UI
        txtUserName = findViewById(R.id.txtUserName);
        txtUserRole = findViewById(R.id.txtUserRole);

        btnStartAttendance = findViewById(R.id.btnStartAttendance);
        btnMap = findViewById(R.id.btnMap);
        btnHistory = findViewById(R.id.btnHistory);
        btnSalary = findViewById(R.id.btnSalary);
        btnLogout = findViewById(R.id.btnLogout);

        // Set user info
        txtUserName.setText(user.full_name);
        txtUserRole.setText(user.role != null ? user.role.toUpperCase() : "STAFF");

        // Open Map
        btnMap.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, MapsActivity.class))
        );

        // Attendance History
        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, HistoryActivity.class))
        );

        // Salary Page
        btnSalary.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, SalaryActivity.class))
        );

        // Logout
        btnLogout.setOnClickListener(v -> {
            SessionManager.logout(DashboardActivity.this);
            startActivity(new Intent(DashboardActivity.this, LoginActivity.class));
            finish();
        });

        // NEW SINGLE ATTENDANCE BUTTON
        btnStartAttendance.setOnClickListener(v -> {
            Intent i = new Intent(DashboardActivity.this, CheckInOutActivity.class);
            startActivity(i);
        });
    }
}
