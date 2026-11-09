package com.qonics.somanet;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HostelAssignActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hostel_assign);
        TextView txt = findViewById(R.id.txtStudent);
        String name = getIntent().getStringExtra("name");
        String regno = getIntent().getStringExtra("regno");
        txt.setText((name == null ? "" : name) + (regno == null || regno.isEmpty() ? "" : " · " + regno));
    }
}
