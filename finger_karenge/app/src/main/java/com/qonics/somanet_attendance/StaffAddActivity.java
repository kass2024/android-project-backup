package com.qonics.somanet_attendance;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.qonics.data.Staff;

import java.util.ArrayList;

public class StaffAddActivity extends AppCompatActivity {
    ListView lvstaff;
    TextView txtstaff;
    ArrayList<Staff> staffs = new ArrayList();
    ArrayAdapter<Staff> adapter;
    SQLiteDatabase db;
    int staffCount = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_add);
        db = this.openOrCreateDatabase("somanet_db", Context.MODE_PRIVATE, null);
        getSupportActionBar().setTitle("Staff picker");

        adapter = new StaffAdapter();
        populateView();
        populateStaff();
    }

    private void populateView() {
        lvstaff = findViewById(R.id.lvstaff);
        txtstaff = findViewById(R.id.txtstaff);
        lvstaff.setAdapter(adapter);
        lvstaff.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String staff_id = staffs.get(i).getId();
                String staff_name = staffs.get(i).getFname()+" "+staffs.get(i).getLname();
                Intent intent = new Intent(StaffAddActivity.this,EnrollActivity.class);
                intent.putExtra("staff_id",staff_id);
                intent.putExtra("staff_name",staff_name);
                startActivityForResult(intent,0);
            }
        });
    }

    private void populateStaff(){
        Cursor cursor = db.rawQuery("select * from staff",null);
        while (cursor.moveToNext()){
            staffCount++;
//            if (!GlobalData.getInstance().IsHaveUserItem(cursor.getString(0))) {//check if staff is not available before add
//                staffs.add(new Staff(cursor.getString(0),cursor.getString(1), cursor.getString(2)));
//            }
        }

        if (staffCount == 0)
            txtstaff.setText("Oops! Not staff found");
        else if (staffs.size() == 0)
            txtstaff.setText("All staffs enrolled ("+staffCount+")");
        else if (staffCount == staffs.size())
            txtstaff.setText("All staff not enrolled ("+staffCount+")");
        else if (staffCount > staffs.size())
            txtstaff.setText(staffs.size()+" Staff(s) not enrolled ("+staffCount+")");
        adapter.notifyDataSetChanged();
    }
    private class StaffAdapter extends ArrayAdapter<Staff> {
        public StaffAdapter() {
            super(StaffAddActivity.this, R.layout.support_simple_spinner_dropdown_item, staffs);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v= convertView;
            if (v == null)
                v= getLayoutInflater().inflate(R.layout.support_simple_spinner_dropdown_item,parent,false);

            Staff staff = staffs.get(position);
            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setTextSize(20);
            txt1.setText(staff.getFname()+" "+staff.getLname());
            return v;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }
}
