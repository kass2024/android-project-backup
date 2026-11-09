package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.qonics.Adapter.BookAdapter;
import com.qonics.model.BookRecord;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class LibraryActivity extends AppCompatActivity implements BookAdapter.RefreshCallback {
    private static final String TAG = StudentActivity.class.getSimpleName();
    TextView txtcountdown,txtstname, txtstclass, txtstregno;
    ProgressBar prb;
    LinearLayout lnlcard;
    CircleImageView profile_image;
    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;
    String card ="";
    SwipeRefreshLayout swipe;
    Button btnBorrow,btnReturn;

    private List<BookRecord> leaveList = new ArrayList<>();
    private RecyclerView recyclerView;
    private BookAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        populateView();
    }

    public  void populateView(){
        txtcountdown = findViewById(R.id.txtcountdown);
        prb = findViewById(R.id.prb);
        profile_image = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        swipe = findViewById(R.id.swipe);
        txtstregno = findViewById(R.id.txtstregno);
        btnBorrow = findViewById(R.id.btnborrow);



        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));
        txtstregno.setText(getString(R.string.reg_number)+" "+getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(getString(R.string.classe)+" "+getIntent().getStringExtra("class"));
//        lnlcard.setVisibility(View.GONE);
        card = getIntent().getStringExtra("card");

        recyclerView = findViewById(R.id.recycler_view);

        mAdapter = new BookAdapter(leaveList,LibraryActivity.this,this) {
            @Override
            public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return super.onCreateViewHolder(parent, viewType);
            }
        };

        swipe.setOnRefreshListener(() -> {
            getLibraryHistory();
        });
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);

        getLibraryHistory();


        btnBorrow.setOnClickListener(view -> {
            Intent intent = new Intent(LibraryActivity.this,BorrowActivity.class);
            intent.putExtra("name", getIntent().getStringExtra("name"));
            intent.putExtra("class", getIntent().getStringExtra("class"));
            intent.putExtra("regno", getIntent().getStringExtra("regno"));
            intent.putExtra("photo", getIntent().getStringExtra("photo"));
            intent.putExtra("student_id", getIntent().getIntExtra("student_id",0));

            startActivityForResult(intent,0);
        });

    }
    private void getLibraryHistory() {
        req = new HttpRequest(this);
        String url = server + "get_library/" + preferences.getInt(MainActivity.SK_ID, 0)+ "/"+getIntent().getIntExtra("student_id",0);
        Log.e("URL",url);
        swipe.setRefreshing(true);
        req.get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                swipe.setRefreshing(false);
                if (object == null) {
                    Toast.makeText(LibraryActivity.this,getString(R.string.lbl_error)+status,Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Toast.makeText(LibraryActivity.this,getString(R.string.lbl_error)+object.getString("error"),Toast.LENGTH_LONG).show();
                        }if (object.has("leaves")) {
                            leaveList.clear();
                            JSONArray leaves = object.getJSONArray("leaves");
                            for (int a=0;a<leaves.length();a++){
                                JSONObject leave = leaves.getJSONObject(a);
                                leaveList.add(new BookRecord(leave.getInt("id"),leave.getInt("book_id"),leave.getInt("student_id"),leave.getInt("academic_year"),leave.getInt("term")
                                        ,leave.getInt("borrow_date"),leave.getInt("return_due_date"),leave.getInt("return_date"),leave.getInt("status")
                                        ,leave.getString("title"),leave.getString("author")));
                            }
                            mAdapter.notifyDataSetChanged();
                        }else {
                            Toast.makeText(LibraryActivity.this,getString(R.string.lbl_error)+object.getString("message"),Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            getLibraryHistory();
        }
    }

    @Override
    public void refreshList() {
        getLibraryHistory();
    }
}

