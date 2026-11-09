package com.qonics.somanet.teacher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.Adapter.CountOfflineMarkAdapter;
import com.qonics.Adapter.OfflineMarkAdapter;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.OfflineCourseModel;
import com.qonics.model.OfflineMarksModel;
import com.qonics.somanet.R;
import com.qonics.somanet.prints.PrintDataActivity;

import java.util.ArrayList;
import java.util.List;

public class ViewOfflineMarks extends AppCompatActivity implements CountOfflineMarkAdapter.OfflineCourseAdapterListener {
    RecyclerView recyclerView, recyclerViewCourse;
    OfflineMarkAdapter clientAdapter;
    CountOfflineMarkAdapter courseAdapter;
    List<OfflineMarksModel> clientList = new ArrayList<>();
    List<OfflineCourseModel> courseList = new ArrayList<>();
    DatabaseHandler db_helper = new DatabaseHandler(this);
    TextView txtlecturer,txttitle,txtCountOffline,txtCourseInfo;
    SharedPreferences preferences;
    String courseName = "", markType = "";
    Button btnsync;
    LinearLayout lnlbuttons,lnlCountId,lnlCourseId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_offline_marks);
        initializationData();
    }

    private  void initializationData(){
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        txtlecturer = findViewById(R.id.txtlecturer);
        txttitle = findViewById(R.id.txttitle);
        txtCourseInfo = findViewById(R.id.courseNameId);

        lnlbuttons = findViewById(R.id.lnlbuttons);
        lnlCountId = findViewById(R.id.lnlCountId);
        lnlCourseId = findViewById(R.id.lnlCourseId);
        btnsync = findViewById(R.id.btnSync);

        lnlbuttons.setVisibility(View.GONE);
        lnlCourseId.setVisibility(View.GONE);
        txtCountOffline = findViewById(R.id.countOfflineMarks);

        txtCountOffline.setText(""+db_helper.countAllOfflineMarks());


        txtlecturer.setText(getString(R.string.teacher) + " " + preferences.getString("soma_name", ""));
        txttitle.setText(getIntent().getIntExtra("who", 0) == 0 ? getString(R.string.title_new_entry) : "Class Daily attendance");

        recyclerView = findViewById(R.id.recyclerViewOffline);
        clientAdapter = new OfflineMarkAdapter(getApplicationContext(), clientList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(clientAdapter);

        recyclerView.setVisibility(View.GONE);

        recyclerViewCourse = findViewById(R.id.recyclerViewOffline2);
        courseAdapter = new CountOfflineMarkAdapter(getApplicationContext(), courseList, this);
        RecyclerView.LayoutManager mLayoutManager1 = new LinearLayoutManager(getApplicationContext());
        recyclerViewCourse.setLayoutManager(mLayoutManager1);
        recyclerViewCourse.setItemAnimator(new DefaultItemAnimator());
        recyclerViewCourse.setAdapter(courseAdapter);


        getAllOfflineCourse();

        btnsync.setOnClickListener(view -> {
            Toast.makeText(this, "Syncing offline marks", Toast.LENGTH_SHORT).show();
        });
    }

    private void getAllOfflineMarks(int course) {
        clientList.clear();
        clientList.addAll(db_helper.getAllOffline(course));
        clientAdapter.notifyDataSetChanged();
    }
    private void getAllOfflineCourse() {
        courseList.clear();
        courseList.addAll(db_helper.getAllOfflineCourse());
        courseAdapter.notifyDataSetChanged();
    }

    @Override
    public void onOfflineCourseSelected(OfflineCourseModel mark) {
        Toast.makeText(this, mark.getName()+" "+mark.getCourse_id()+" " +mark.getMarks()+"", Toast.LENGTH_SHORT).show();

        txtCourseInfo.setText(getString(R.string.lbl_course)+": " +mark.getName() + "\n" +getString(R.string.lbl_mark_type)+": " +mark.getCat()+ "\n" +"Total: "+mark.getMarks());
        getAllOfflineMarks(mark.getCourse_id());

        recyclerViewCourse.setVisibility(View.GONE);
        lnlCountId.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        lnlCourseId.setVisibility(View.VISIBLE);
        lnlbuttons.setVisibility(View.VISIBLE);

        Intent intent = new Intent(getApplicationContext(), PrintDataActivity.class);
        startActivity(intent);

    }
}