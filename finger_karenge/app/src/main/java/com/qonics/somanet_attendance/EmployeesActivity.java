package com.qonics.somanet_attendance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;

import android.text.TextPaint;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.qonics.app.UserItem;
import com.qonics.app.UsersList;
import com.qonics.data.ImageSimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class EmployeesActivity extends AppCompatActivity {

    private TextView tv1, tv2, tv3;
    private TextPaint tp1, tp2, tp3;

    private ListView listView;
    private List<Map<String, Object>> mData;
    private SimpleAdapter adapter;
    SearchView srch;

    private Timer startTimer;
    private TimerTask startTask;
    Handler startHandler;
    int type = 0;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employees);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tv1 = findViewById(R.id.textView1);
        tv2 = findViewById(R.id.textView2);
        tv3 = findViewById(R.id.textView3);
        srch = findViewById(R.id.srch);
        srch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.trim().length() == 0)
                    addWegit(type);
                else
                    addWegit(type, s);
                return false;
            }
        });
        tp1 = tv1.getPaint();
        tp2 = tv2.getPaint();
        tp3 = tv3.getPaint();

        tv1.setOnClickListener(view -> {
            type = 0;
            SetTvStyle(1);
            addWegit();
        });

        tv2.setOnClickListener(view -> {
            type = 2;
            SetTvStyle(2);
            addWegit(2);
        });
        tv3.setOnClickListener(view -> {
            type = 3;
            SetTvStyle(3);
            addWegit(3);
        });

        listView = findViewById(R.id.listView1);
        mData = new ArrayList<Map<String, Object>>();
        adapter = new ImageSimpleAdapter(this, mData, R.layout.listview_personitem,
                new String[]{"title", "userid"},
                new int[]{R.id.title, R.id.info});
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                //Map<String, Object> item = (Map<String, Object>)parent.getItemAtPosition(pos);
                if (UsersList.getInstance().usersList.size() > 0) {
                    Intent intent = new Intent(EmployeesActivity.this, EnrollActivity.class);
                    Bundle bundle = new Bundle();
                    Map map = mData.get(pos);
                    bundle.putInt("userid", Integer.parseInt(map.get("userid").toString()));
                    intent.putExtras(bundle);
                    startActivityForResult(intent, 0);
                    overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
                }
            }
        });

        SetTvStyle(1);
        TimerStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UsersList.getInstance().LoadAll(this);
    }

    public void TimerStart() {
        startTimer = new Timer();
        startHandler = new Handler() {
            @SuppressLint("HandlerLeak")
            @Override
            public void handleMessage(Message msg) {
                addWegit();
                super.handleMessage(msg);
            }
        };
        startTask = new TimerTask() {
            @Override
            public void run() {
                TimeStop();

                Message message = new Message();
                message.what = 1;
                startHandler.sendMessage(message);
            }
        };
        startTimer.schedule(startTask, 100, 100);
    }

    public void TimeStop() {
        if (startTimer != null) {
            startTimer.cancel();
            startTimer = null;
            startTask.cancel();
            startTask = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case 0: {
//				SetTvStyle(1);
                srch.setQuery(null, false);
                addWegit(type);
            }
            break;
            case 1: {
                //Bundle bl= data.getExtras();
                //refdata=bl.getByteArray("refdata");
            }
            break;
            case 2:
                break;
            default:
                break;
        }
    }

    private void SetTvStyle(int i) {
        switch (i) {
            case 1:
                tv1.setTextColor(Color.RED);
                tp1.setFakeBoldText(true);
                tv2.setTextColor(Color.BLACK);
                tp2.setFakeBoldText(false);
                tv3.setTextColor(Color.BLACK);
                tp3.setFakeBoldText(false);
                break;
            case 2:
                tv1.setTextColor(Color.BLACK);
                tp1.setFakeBoldText(false);
                tv2.setTextColor(Color.RED);
                tp2.setFakeBoldText(true);
                tv3.setTextColor(Color.BLACK);
                tp3.setFakeBoldText(false);
                break;
            case 3:
                tv1.setTextColor(Color.BLACK);
                tp1.setFakeBoldText(false);
                tv2.setTextColor(Color.BLACK);
                tp2.setFakeBoldText(false);
                tv3.setTextColor(Color.RED);
                tp3.setFakeBoldText(true);
                break;
        }
    }

    public void addWegit() {
        addWegit(0, null);//all
    }

    public void addWegit(int tp) {
        addWegit(tp, null);//all
    }

    public void addWegit(int tp, String query) {
        mData.clear();
        adapter.notifyDataSetChanged();

        int n = UsersList.getInstance().usersList.size();
        if (n < 1) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("title", getString(R.string.txt_enrolinfo1));
            map.put("userid", getString(R.string.txt_enrolinfo2));
            mData.add(map);
            //adapter.notifyDataSetChanged();
        } else {
            for (int i = 0; i < n; i++) {
                UserItem person = UsersList.getInstance().usersList.get(i);
                if (tp == 2 && !(person.fp1 == null || person.fp2 == null)) {
                    //enrolled
                    continue;
                }
                if (tp == 3 && !(person.face1 == null || person.face2 == null)) {
                    //Face enrolled
                    continue;
                }
                if (query != null && !person.name.toLowerCase().contains(query.toLowerCase()))
                    continue;
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("title", person.name);
                map.put("userid", person.userid);
                mData.add(map);
                //adapter.notifyDataSetChanged();
            }
        }
        adapter.notifyDataSetChanged();
    }
}
