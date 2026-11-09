package com.qonics.somanet;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HostelResidentsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hostel_residents);
        TextView info = findViewById(R.id.txtInfo);
        int hostelId = getIntent().getIntExtra("hostel_id", 0);
        int year = getIntent().getIntExtra("academic_year", 0);
        info.setText(getString(R.string.hostel_allocation) + " · " + hostelId + " / " + year);
    }
}
