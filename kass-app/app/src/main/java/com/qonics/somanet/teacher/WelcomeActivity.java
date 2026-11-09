package com.qonics.somanet.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.somanet.CoreService;
import com.qonics.somanet.DailyAttendanceActivity;
import com.qonics.somanet.LeavesActivity;
import com.qonics.somanet.LecturerActivity;
import com.qonics.somanet.LoginActivity;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.R;
import com.qonics.somanet.SearchStudentActivity;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.MenuClearanceHelper;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

import android.view.View;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class WelcomeActivity extends AppCompatActivity {

    Button btnlogout;
    SharedPreferences preferences;

    LinearLayout lnlstudents, lnlstaff, lnlattendance, lnldailyattendance, lnlleaves, lnlMarksEntry;
    TextView txtinfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_teacher_welcome);
        try {
            startService(new Intent(this, CoreService.class));
        } catch (Exception ignored) {
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        //Make sure to change the page title
        txtinfo = findViewById(R.id.txtinfo);
        txtinfo.setText(preferences.getString("soma_name","")+"\n"
                +preferences.getString("soma_post_title","")+"\n");

        btnlogout = findViewById(R.id.btnlogout);

        btnlogout.setOnClickListener(view -> {
            preferences.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        lnlstudents = findViewById(R.id.lnlstudent);
        lnlstaff = findViewById(R.id.lnlstaff);

        lnlstudents.setOnClickListener(view -> {
            Intent intent;
            if (MenuClearanceHelper.hasAnyKey(preferences,
                    "assign-card", "student-photo", "student_material_check", "hostel_allocate")) {
                intent = new Intent(this, com.qonics.somanet.StudentsMenuActivity.class);
            } else {
                intent = new Intent(this, SearchStudentActivity.class);
                intent.putExtra("who",2);
            }
            startActivity(intent);
        });

        if (lnlstaff != null) {
            lnlstaff.setOnClickListener(view ->
                    startActivity(new Intent(this, com.qonics.somanet.StaffListActivity.class)));
        }

        lnlattendance = findViewById(R.id.lnlattendance);
        lnlattendance.setOnClickListener(view -> {
            Intent intent = new Intent(this, LecturerActivity.class);
            intent.putExtra("who",0);
            startActivity(intent);
        });

        lnldailyattendance = findViewById(R.id.lnldailyattendance);
        lnldailyattendance.setOnClickListener(view -> {
            if (preferences.getInt("soma_is_master", 0) != 1) {
                Intent intent = new Intent(this, LecturerActivity.class);
                intent.putExtra("who", 1);
                startActivity(intent);
                return;
            }
            SweetAlertDialog sa = new SweetAlertDialog(this,SweetAlertDialog.NORMAL_TYPE);
            sa.setTitle("Attendance type");
            sa.setContentText("Which option do you want to use?")
                    .setConfirmButton("CARD",sweetAlertDialog -> {
                        Intent intent = new Intent(this, DailyAttendanceActivity.class);
                        startActivity(intent);
                        sweetAlertDialog.dismiss();
                    })
                    .setCancelButton("NAMES",sweetAlertDialog -> {
                        Intent intent = new Intent(this,LecturerActivity.class);
                        intent.putExtra("who",1);
                        startActivity(intent);
                        sweetAlertDialog.dismiss();
                    })
                    .setCancelable(true);
            sa.show();
        });

        lnlleaves = findViewById(R.id.lnlleaves);
        lnlleaves.setOnClickListener(view -> {
            Intent intent = new Intent(this, LeavesActivity.class);
            startActivity(intent);
        });

        lnlMarksEntry = findViewById(R.id.lnlmarksentry);
        lnlMarksEntry.setOnClickListener(view -> {
            Intent intent = new Intent(this, MarksEntryActivity.class);
            intent.putExtra("who",0);
            startActivity(intent);
        });

        applyMenuClearance();
        refreshMenuClearanceFromServer();
    }

    private void applyMenuClearance() {
        Map<String, View> tiles = new LinkedHashMap<>();
        tiles.put("students", lnlstudents);
        tiles.put("staffs", lnlstaff);
        tiles.put("attendance", lnlattendance);
        tiles.put("daily_attendance", lnldailyattendance);
        tiles.put("leaves", lnlleaves);
        tiles.put("marks", lnlMarksEntry);
        MenuClearanceHelper.applyAll(preferences, tiles);
    }

    private void refreshMenuClearanceFromServer() {
        int postId = preferences.getInt("soma_post", 0);
        int staffId = preferences.getInt("soma_id", 0);
        if (postId <= 0 && staffId <= 0) return;
        String url = MainActivity.SERVER + "menu_clearance/" + postId
                + (staffId > 0 ? ("?staff_id=" + staffId) : "");
        new HttpRequest(this).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.has("error")) return;
                SharedPreferences.Editor ed = preferences.edit();
                MenuClearanceHelper.saveFromLogin(ed, object);
                ed.apply();
                applyMenuClearance();
            }
        });
    }
}
