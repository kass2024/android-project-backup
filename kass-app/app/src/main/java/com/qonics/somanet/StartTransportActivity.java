package com.qonics.somanet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qonics.model.Buses;
import com.qonics.model.Routes;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class StartTransportActivity extends AppCompatActivity {
    Spinner spbus, sproute, spway;
    Button btnstart;
    TextView txterror;
    ProgressBar prb;

    HttpRequest req;
    String server = MainActivity.SERVER;
    SharedPreferences preferences;
    ArrayList<Buses> buses = new ArrayList<>();
    ArrayList<Routes> routes = new ArrayList<>();

    ArrayAdapter<Buses> bAdapter;
    ArrayAdapter<Routes> rAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_transport);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        bAdapter = new BusesAdapter();
        rAdapter = new RouteAdapter();
        populateView();
        getData();
    }

    private void populateView() {
        prb = findViewById(R.id.prb);
        txterror = findViewById(R.id.txterror);
        spbus = findViewById(R.id.spbus);
        spway = findViewById(R.id.spway);
        sproute = findViewById(R.id.sproutes);
        btnstart = findViewById(R.id.btnstart);

        spbus.setAdapter(bAdapter);
        sproute.setAdapter(rAdapter);

        // ✅ Fixed: use android.R.layout.simple_spinner_dropdown_item
        spway.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Home ---> School", "School ---> Home"}
        ));

        btnstart.setOnClickListener(view -> {
            if (spbus.getSelectedItemPosition() == -1) {
                Toast.makeText(this, getString(R.string.select_bus), Toast.LENGTH_SHORT).show();
            } else if (sproute.getSelectedItemPosition() == -1) {
                Toast.makeText(this, getString(R.string.select_route), Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, TransportActivity.class);
                intent.putExtra("bus", buses.get(spbus.getSelectedItemPosition()).getId());
                intent.putExtra("bus_title", buses.get(spbus.getSelectedItemPosition()).getPlate());
                intent.putExtra("bus_place", buses.get(spbus.getSelectedItemPosition()).getPlaces());
                intent.putExtra("route", routes.get(sproute.getSelectedItemPosition()).getId());
                intent.putExtra("route_title", routes.get(sproute.getSelectedItemPosition()).getTitle());
                intent.putExtra("route_details", routes.get(sproute.getSelectedItemPosition()).getDetails());
                intent.putExtra("way", spway.getSelectedItemPosition());
                startActivity(intent);
                finish();
            }
        });
    }

    private void getData() {
        req = new HttpRequest(this);
        txterror.setVisibility(View.GONE);
        String url = server + "get_transport_data/" + preferences.getInt(MainActivity.SK_ID, 0);
        Log.e("URL", url);

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
                        } else {
                            JSONArray bss = object.getJSONArray("buses");
                            JSONArray rtes = object.getJSONArray("routes");

                            for (int a = 0; a < bss.length(); a++) {
                                JSONObject bdata = bss.getJSONObject(a);
                                buses.add(new Buses(
                                        bdata.getInt("id"),
                                        bdata.getString("car_maker"),
                                        bdata.getString("car_model"),
                                        bdata.getString("plate"),
                                        bdata.getInt("places")
                                ));
                            }

                            for (int b = 0; b < rtes.length(); b++) {
                                JSONObject rdata = rtes.getJSONObject(b);
                                routes.add(new Routes(
                                        rdata.getInt("id"),
                                        rdata.getString("title"),
                                        rdata.getString("details"),
                                        rdata.getInt("price")
                                ));
                            }

                            bAdapter.notifyDataSetChanged();
                            rAdapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class BusesAdapter extends ArrayAdapter<Buses> {
        public BusesAdapter() {
            super(StartTransportActivity.this, R.layout.two_row, buses);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.two_row, parent, false);
            TextView txt1 = v.findViewById(R.id.txt1);
            TextView txt2 = v.findViewById(R.id.txt2);
            Buses bus = buses.get(position);
            txt1.setText(bus.getMake() + " " + bus.getModel() + " #" + bus.getPlaces());
            txt2.setText(bus.getPlate());
            return v;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.two_row, parent, false);
            TextView txt1 = v.findViewById(R.id.txt1);
            TextView txt2 = v.findViewById(R.id.txt2);
            Buses bus = buses.get(position);
            txt1.setText(bus.getMake() + " " + bus.getModel() + " #" + bus.getPlaces());
            txt2.setText(bus.getPlate());
            return v;
        }
    }

    private class RouteAdapter extends ArrayAdapter<Routes> {
        public RouteAdapter() {
            super(StartTransportActivity.this, R.layout.two_row, routes);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.two_row, parent, false);
            TextView txt1 = v.findViewById(R.id.txt1);
            TextView txt2 = v.findViewById(R.id.txt2);
            Routes route = routes.get(position);
            txt1.setText(route.getTitle());
            txt2.setText(route.getDetails());
            return v;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null)
                v = getLayoutInflater().inflate(R.layout.two_row, parent, false);
            TextView txt1 = v.findViewById(R.id.txt1);
            TextView txt2 = v.findViewById(R.id.txt2);
            Routes route = routes.get(position);
            txt1.setText(route.getTitle());
            txt2.setText(route.getDetails());
            return v;
        }
    }
}
