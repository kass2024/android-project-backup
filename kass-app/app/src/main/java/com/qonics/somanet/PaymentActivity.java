package com.qonics.somanet;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.model.Dual_data;
import com.qonics.model.StudentModel;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class PaymentActivity extends AppCompatActivity {
    CircleImageView profile_image;
    TextView txttransport,txtstname, txtstclass, txtstregno,txttitle,txtexpectedamount,txtremainingamount,txtpaidamount,txterror;
    ProgressBar prb;
    Spinner spyear,spterm;
    LinearLayout lnlcontent;
    Button btnclose,btnnotify;
    int remainingAmount = 0;

    ArrayList<Dual_data> years = new ArrayList<>();
    ArrayAdapter<Dual_data> yearAdapter;
    ArrayList<Dual_data> terms = new ArrayList<>();
    ArrayAdapter<Dual_data> termAdapter;
    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        yearAdapter = new DualAdapter(years);
        termAdapter = new DualAdapter(terms);
        populateView();
        loadData();
    }

    private void populateView() {
        txtexpectedamount = findViewById(R.id.txtexpectedamount);
        spterm = findViewById(R.id.spterm);
        spyear = findViewById(R.id.spyear);
        txttransport = findViewById(R.id.txttransport);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);
        txttitle = findViewById(R.id.txttitle);
        txtremainingamount = findViewById(R.id.txtremainamount);
        txtpaidamount = findViewById(R.id.txtpaidamount);
        txterror = findViewById(R.id.txterror);
        lnlcontent = findViewById(R.id.lnlcontent);
        lnlcontent.setVisibility(View.INVISIBLE);
        prb = findViewById(R.id.prb);
        btnclose = findViewById(R.id.btnclose);
        btnnotify = findViewById(R.id.btnnotify);
        btnnotify.setVisibility(View.GONE);
        profile_image = findViewById(R.id.profile_image);

        spyear.setAdapter(yearAdapter);
        spterm.setAdapter(termAdapter);
        txterror.setVisibility(View.GONE);
        prb.setVisibility(View.GONE);

        txtstregno.setText(getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(getIntent().getStringExtra("class"));
        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));

        btnclose.setOnClickListener(view -> {
            onBackPressed();
        });
        btnnotify.setOnClickListener(view -> {
            sendSMS();
        });
        spyear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i>0)
                    checkPayment();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        spterm.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i>0)
                    checkPayment();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void checkPayment() {
        if (spyear.getSelectedItemPosition()<=0 || spterm.getSelectedItemPosition()<=0){
            return;
        }
        req = new HttpRequest(this);
        txterror.setVisibility(View.GONE);
        String url = server + "payment_check/" + getIntent().getIntExtra("student_id",0)
                + "/"+terms.get(spterm.getSelectedItemPosition()).getId()+"/" + preferences.getInt(MainActivity.SK_ID, 0)
                +"/" + years.get(spyear.getSelectedItemPosition()).getId();
        Log.e("URL",url);
        req.progress(prb).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    txterror.setVisibility(View.VISIBLE);
                    txterror.setText(getString(R.string.lbl_error) + status);
                } else {
                    try {
                        if (!object.has("success")) {
                            txterror.setVisibility(View.VISIBLE);
                            txterror.setText(getString(R.string.lbl_error) + object.getString("message"));
                            lnlcontent.setVisibility(View.INVISIBLE);
                        }else {
                            lnlcontent.setVisibility(View.VISIBLE);
                            int expected = Integer.parseInt(object.getString("extra_amount"))+Integer.parseInt(object.getString("skl_amount"));
                            int paid = Integer.parseInt(object.getString("paidschoolfees"))+Integer.parseInt(object.getString("paidextra"));
                            int transport_money = Integer.parseInt(object.getString("transport_money"));
                            int remain = expected - paid;
                            txtexpectedamount.setText(getString(R.string.lbl_expected_amount)+String.format("%,d",expected));
                            txtremainingamount.setText(getString(R.string.lbl_remain_amount)+String.format("%,d",remain));
                            txtpaidamount.setText(getString(R.string.lbl_paid_amount)+String.format("%,d",paid));
                            txttransport.setText(getString(R.string.lbl_transport_balance)+String.format("%,d",transport_money));
                            if (remain==0){
                                remainingAmount = 0;
                                btnnotify.setVisibility(View.GONE);
                            }else{
                                remainingAmount = remain;
                                btnnotify.setVisibility(View.VISIBLE);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void loadData() {
        terms.add(new Dual_data(0,"Select term"));
        terms.add(new Dual_data(1,"Term one"));
        terms.add(new Dual_data(2,"Term two"));
        terms.add(new Dual_data(3,"Term three"));
        termAdapter.notifyDataSetChanged();
        req = new HttpRequest(this);
        txterror.setVisibility(View.GONE);

        String url = server + "get_years/" +  preferences.getInt(MainActivity.SK_ID, 0);
        req.progress(prb).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    txterror.setVisibility(View.VISIBLE);
                    txterror.setText(getString(R.string.lbl_error) + status);
                } else {
                    try {
                        if (object.has("error")) {
                            txterror.setVisibility(View.VISIBLE);
                            txterror.setText(getString(R.string.lbl_error) + object.getString("error"));
                        }else {
                            JSONArray jar = object.getJSONArray("years");
                            years.add(new Dual_data(0,"Select academic"));
                            for (int a=0;a<jar.length();a++) {
                                JSONObject data = jar.getJSONObject(a);
                                years.add(new Dual_data(data.getInt("id"),data.getString("title")));
                            }
                            yearAdapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void sendSMS() {
        req = new HttpRequest(this);
        txterror.setVisibility(View.GONE);
        Map map = new HashMap();
        map.put("student_id",getIntent().getIntExtra("student_id",0));
        map.put("school_id",preferences.getInt(MainActivity.SK_ID, 0));
        map.put("amount",remainingAmount);
        String url = server + "send_payment_notification";
        req.progress(prb).post(url, map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    txterror.setVisibility(View.VISIBLE);
                    txterror.setText(getString(R.string.lbl_error) + status);
                } else {
                    try {
                        if (object.has("success")) {
                            Toast.makeText(getApplicationContext(),"SMS Sent to parent",Toast.LENGTH_LONG).show();
                        }else {
                            txterror.setVisibility(View.VISIBLE);
                            txterror.setText(getString(R.string.lbl_error) + object.getString("message"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class DualAdapter extends ArrayAdapter<Dual_data> {
        List<Dual_data> lists;
        public DualAdapter(List<Dual_data> lists) {
            super(PaymentActivity.this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, lists);
            this.lists = lists;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,parent,false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(lists.get(position).getText1());
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,parent,false);

            TextView txt1 = v.findViewById(android.R.id.text1);
            txt1.setText(lists.get(position).getText1());
            return v;
        }
    }
}
