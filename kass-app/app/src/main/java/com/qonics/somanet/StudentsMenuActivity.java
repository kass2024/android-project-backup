package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.utils.MenuClearanceHelper;
import com.qonics.utils.SyncStatusHelper;

public class StudentsMenuActivity extends AppCompatActivity {

    private SyncStatusHelper syncHelper;
    private SharedPreferences preferences;
    private LinearLayout lnlAssign;
    private LinearLayout lnlPhoto;
    private LinearLayout lnlMaterial;
    private LinearLayout lnlHostel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_students_menu);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        lnlAssign = findViewById(R.id.lnl_assign_card);
        TextView btnSync = findViewById(R.id.btn_sync_now);
        lnlPhoto = findViewById(R.id.lnl_change_picture);
        lnlMaterial = findViewById(R.id.lnl_material_check);
        lnlHostel = findViewById(R.id.lnl_hostel);

        syncHelper = new SyncStatusHelper(this, btnSync);

        lnlAssign.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 2);
            startActivity(intent);
        });

        btnSync.setOnClickListener(v -> syncHelper.triggerSync());

        lnlPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 9);
            startActivity(intent);
        });

        lnlMaterial.setOnClickListener(v ->
                startActivity(new Intent(this, MaterialCheckMenuActivity.class)));

        lnlHostel.setOnClickListener(v ->
                startActivity(new Intent(this, HostelMenuActivity.class)));

        applyStudentMenuClearance();
        animateIn(lnlAssign, 40);
        animateIn(lnlPhoto, 90);
        animateIn(lnlMaterial, 140);
        animateIn(lnlHostel, 190);
        animateIn(btnSync, 240);
    }

    private void applyStudentMenuClearance() {
        if (lnlAssign != null) {
            lnlAssign.setVisibility(MenuClearanceHelper.hasAnyKey(preferences,
                    "assign-card", "student-cards", "students") ? View.VISIBLE : View.GONE);
        }
        if (lnlPhoto != null) {
            lnlPhoto.setVisibility(MenuClearanceHelper.hasAnyKey(preferences,
                    "student-photo") ? View.VISIBLE : View.GONE);
        }
        boolean boardingMenus = MenuClearanceHelper.canUseMaterialAndDormitory(preferences);
        if (lnlMaterial != null) {
            lnlMaterial.setVisibility(boardingMenus ? View.VISIBLE : View.GONE);
        }
        if (lnlHostel != null) {
            lnlHostel.setVisibility(boardingMenus ? View.VISIBLE : View.GONE);
        }
    }

    private void animateIn(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(18f);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(280).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyStudentMenuClearance();
        if (syncHelper != null) {
            syncHelper.onResume(false);
        }
    }

    @Override
    protected void onPause() {
        if (syncHelper != null) {
            syncHelper.onPause();
        }
        super.onPause();
    }
}
