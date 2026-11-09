package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.qonics.model.StaffItem;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StaffListActivity extends AppCompatActivity {

    private final List<StaffItem> allStaff = new ArrayList<>();
    private final List<StaffItem> visible = new ArrayList<>();
    private StaffAdapter adapter;
    private SwipeRefreshLayout swipe;
    private SearchView searchView;
    private TextView txtCount;
    private TextView txtError;
    private ProgressBar prbStaff;
    private SharedPreferences preferences;
    private String lastQuery = "";
    private boolean loading = false;
    private final ActivityResultLauncher<Intent> editLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                applyEditedStaff(result.getData());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        txtCount = findViewById(R.id.txtStaffCount);
        txtError = findViewById(R.id.txtStaffError);
        prbStaff = findViewById(R.id.prbStaff);
        searchView = findViewById(R.id.srchStaff);
        swipe = findViewById(R.id.swipeStaff);
        RecyclerView rv = findViewById(R.id.recyclerStaff);
        rv.setHasFixedSize(true);
        adapter = new StaffAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilter(newText);
                return true;
            }
        });
        swipe.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        if (loading) return;
        if (!MainActivity.isNetworkAvailable(this)) {
            swipe.setRefreshing(false);
            if (prbStaff != null) prbStaff.setVisibility(View.GONE);
            showError(getString(R.string.staff_need_online));
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId <= 0) {
            swipe.setRefreshing(false);
            if (prbStaff != null) prbStaff.setVisibility(View.GONE);
            showError(getString(R.string.staff_need_online));
            return;
        }
        loading = true;
        swipe.setRefreshing(true);
        txtError.setVisibility(View.GONE);
        String url = MainActivity.SERVER + "get_staff_list?school_id=" + schoolId;
        HttpRequest req = new HttpRequest(this);
        if (prbStaff != null && allStaff.isEmpty()) {
            req.progress(prbStaff);
        }
        req.get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                loading = false;
                swipe.setRefreshing(false);
                if (prbStaff != null) prbStaff.setVisibility(View.GONE);
                allStaff.clear();
                if (object != null && object.has("staffs")) {
                    try {
                        JSONArray arr = object.getJSONArray("staffs");
                        for (int i = 0; i < arr.length(); i++) {
                            allStaff.add(StaffItem.fromJson(arr.getJSONObject(i)));
                        }
                        txtError.setVisibility(View.GONE);
                    } catch (Exception e) {
                        showError(getString(R.string.error_occurred));
                    }
                } else if (object != null && object.has("error")) {
                    showError(object.optString("error", getString(R.string.error_occurred)));
                } else {
                    showError(getString(R.string.staff_need_online));
                }
                applyFilter(lastQuery);
            }
        });
    }

    private void applyEditedStaff(Intent data) {
        int id = data.getIntExtra("staff_id", 0);
        if (id <= 0) return;
        for (StaffItem item : allStaff) {
            if (item.id != id) continue;
            item.fname = data.getStringExtra("fname");
            item.lname = data.getStringExtra("lname");
            item.phone = data.getStringExtra("phone");
            item.email = data.getStringExtra("email");
            if (item.fname == null) item.fname = "";
            if (item.lname == null) item.lname = "";
            if (item.phone == null) item.phone = "";
            if (item.email == null) item.email = "";
            item.name = (item.fname + " " + item.lname).trim();
            break;
        }
        applyFilter(lastQuery);
    }

    private void applyFilter(String query) {
        lastQuery = query == null ? "" : query.trim();
        visible.clear();
        for (StaffItem item : allStaff) {
            if (item.matches(lastQuery)) {
                visible.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        if (lastQuery.isEmpty()) {
            txtCount.setText(getString(R.string.staff_count_fmt, visible.size()));
        } else {
            txtCount.setText(getString(R.string.staff_count_filtered_fmt, visible.size(), allStaff.size()));
        }
        if (allStaff.isEmpty() && txtError.getVisibility() != View.VISIBLE) {
            txtError.setText(R.string.staff_empty);
            txtError.setVisibility(View.VISIBLE);
        } else if (!allStaff.isEmpty() && visible.isEmpty()) {
            txtError.setText(getString(R.string.staff_no_match, lastQuery));
            txtError.setVisibility(View.VISIBLE);
        } else if (!visible.isEmpty()) {
            txtError.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openEdit(StaffItem staff) {
        Intent intent = new Intent(this, StaffEditActivity.class);
        intent.putExtra("staff_id", staff.id);
        intent.putExtra("fname", staff.fname);
        intent.putExtra("lname", staff.lname);
        intent.putExtra("phone", staff.phone);
        intent.putExtra("email", staff.email);
        intent.putExtra("post_title", staff.postTitle);
        intent.putExtra("status_label", staff.statusLabel);
        editLauncher.launch(intent);
    }

    private class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_staff_row, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            StaffItem s = visible.get(position);
            holder.name.setText(s.name);
            holder.status.setText(s.statusLabel);
            boolean active = s.status == 1 || s.status == 2;
            holder.status.setTextColor(getResources().getColor(active ? R.color.colorSuccess : R.color.colorError));
            String load = s.taughtCourses > 0
                    ? getString(R.string.staff_load_fmt, s.taughtCourses, s.taughtPeriods)
                    : getString(R.string.staff_no_courses);
            String privilege = s.postTitle == null || s.postTitle.isEmpty()
                    ? getString(R.string.staff_no_privilege)
                    : s.postTitle;
            holder.meta.setText(privilege + "  ·  " + load);
            String phone = s.phone == null || s.phone.isEmpty() ? "-" : s.phone;
            String email = s.email == null || s.email.isEmpty() ? "-" : s.email;
            holder.contact.setText(phone + "  ·  " + email);
            holder.itemView.setOnClickListener(v -> openEdit(s));
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView status;
            final TextView meta;
            final TextView contact;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.txtStaffName);
                status = itemView.findViewById(R.id.txtStaffStatus);
                meta = itemView.findViewById(R.id.txtStaffMeta);
                contact = itemView.findViewById(R.id.txtStaffContact);
            }
        }
    }
}
