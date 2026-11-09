package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.dbhelper.DatabaseHandler;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.qonics.somanet.teacher.WelcomeActivity;
import com.qonics.utils.Callback;
import com.qonics.utils.ClassLabels;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.MenuClearanceHelper;
import com.qonics.utils.StaffLoginStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    HttpRequest req;
    SharedPreferences preferences;
    String server = MainActivity.SERVER;
    EditText etxtemail,etxtpass;
    Button btnlogin;
    Switch swrmb;
    ProgressBar prb;
    SQLiteDatabase db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        db = this.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Calendar c = Calendar.getInstance();
        if (preferences.getBoolean("remember_me",false) && (c.getTimeInMillis()/1000L)< preferences.getLong("remember_date",0)){
            if(preferences.getInt("soma_post",0) == 2){
                startActivity(new Intent(this,WelcomeActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        }

        populate();
    }

    private void populate() {
        etxtemail = findViewById(R.id.etxtemail);
        etxtpass = findViewById(R.id.etxtpass);
        btnlogin = findViewById(R.id.btnlogin);
        swrmb = findViewById(R.id.swrmb);
        prb = findViewById(R.id.prb);
        prb.setVisibility(View.GONE);
        btnlogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(etxtemail.getText().toString().trim().length()<3){
                    etxtemail.setError(getString(R.string.log_email_err));
                    etxtemail.requestFocus();
                }else if(etxtpass.getText().toString().trim().length()<6){
                    etxtpass.setError(getString(R.string.log_pass_err));
                    etxtpass.requestFocus();
                }else{
                    String email = etxtemail.getText().toString().trim();
                    String pass = etxtpass.getText().toString().trim();
                    login(email,pass);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefetchStaffAccounts();
    }

    /** While this phone is online, pull staff logins before the person taps Login. */
    private void prefetchStaffAccounts() {
        final int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId < 1 || staffAccountCount() > 0) return;
        new HttpRequest(this).timeouts(30, 90).get(server + "sync/staff_login/" + schoolId, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null || object.optJSONArray("staff_accounts") == null) return;
                int saved = StaffLoginStore.save(db, object);
                if (saved < 1) return;
                StaffLoginStore.saveDevice(preferences, object.optJSONObject("device"));
                preferences.edit().putInt("staff_login_saved", saved).apply();
                if (btnlogin != null) {
                    Snackbar.make(btnlogin, saved + " staff accounts saved. Login works offline now.", Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private int staffAccountCount() {
        Cursor count = db.rawQuery("select count(*) from tb_staff_login", null);
        int saved = 0;
        if (count.moveToFirst()) saved = count.getInt(0);
        count.close();
        return saved;
    }

    private void login(String email,String pass){
        Map map = new HashMap();
        map.put("email",email);
        map.put("password",pass);
        req = new HttpRequest(this);
        //login and fetch course, class and block if it is not the same school as old one
        //if it is different academic year prompt for student data reset and download new student list
        req.timeouts(20, 60).progress(prb).hide(btnlogin).post(server + "login", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    loginOffline(email, pass);
                } else {
                    try {
                        if (object.has("error")) {
                            if (object.getString("error").equals("0")) {
                                etxtpass.setError(getString(R.string.log_both_err));
                                etxtpass.requestFocus();
                            }else if (object.getString("error").equals("1")) {
                                etxtemail.setError(getString(R.string.log_acc_err));
                                etxtemail.requestFocus();
                            }else {
                                etxtpass.requestFocus();
                                Snackbar.make(btnlogin,getString(R.string.log_login_err)+" "+object.getString("error"),Snackbar.LENGTH_LONG).show();
                            }
                        } else {
                            //login succeed
                            if(preferences.getInt(MainActivity.SK_ID, 0) !=0 && preferences.getInt(MainActivity.SK_ID, 0) != object.getInt("soma_school_id")){
                                Snackbar.make(btnlogin,"Can not login, This device is assigned to "+preferences.getString(MainActivity.SK_NAME,""),Snackbar.LENGTH_LONG).show();
                                return;
                            }
                            saveStaffBundle(object);
                            syncCourses(object.getJSONArray("courses"));
                            syncClasses(object.getJSONArray("classes"));
                            applyNurseryFlags(object.getInt("soma_id"));
                            syncAssessmentType(object.getJSONArray("assessmentTypes"));
                            SharedPreferences.Editor editor = preferences.edit();
                            if (swrmb.isChecked()){
                                Calendar c = Calendar.getInstance();
//                                c.set(Calendar.HOUR_OF_DAY,23);
//                                c.set(Calendar.MINUTE,59);
                                editor.putBoolean("remember_me",true)
                                        .putLong("remember_date",(c.getTimeInMillis()/1000L)*2592000).apply();
                            }
                            editor.putString(MainActivity.LOGIN,"Ok!")
                                    .putInt(MainActivity.SK_ID,object.getInt("soma_school_id"))
                                    .putInt("soma_id",object.getInt("soma_id"))
                                    .putInt("soma_term",object.getInt("soma_term"))
                                    .putInt("soma_term_number",object.getInt("soma_term_number"))
                                    .putInt("soma_academic",object.getInt("soma_academic"))
                                    .putString("soma_academic_title", object.optString("soma_academic_title", ""))
                                    .putInt("soma_use_period",object.getInt("soma_use_period"))
                                    .putInt("soma_post",object.getInt("soma_post"))
                                    .putString("school_country",object.getString("school_country"))
                                    .putString("academic_type",object.getString("academic_type"))
                                    .putString(MainActivity.SK_NAME,object.getString("soma_school"))
                                    .putString(MainActivity.SK_PHONE,object.getString("school_phone"))
                                    .putString(MainActivity.SK_EMAIL,object.getString("school_email"))
                                    .putString("soma_name",object.getString("soma_name"))
                                    .putString("soma_post_title",object.getString("soma_post_title"))
                                    .putInt("soma_is_master", object.optInt("soma_is_master", 0))
                                    .putInt("device_term", object.getInt("soma_term"))
                                    .putInt("device_term_number", object.getInt("soma_term_number"))
                                    .putInt("device_academic", object.getInt("soma_academic"))
                                    .putString("device_academic_title", object.optString("soma_academic_title", ""))
                                    .putInt("device_use_period", object.getInt("soma_use_period"))
                                    .putString("device_country", object.optString("school_country", "Rwanda"))
                                    .putString("device_academic_type", object.optString("academic_type", ""))
                                    .putInt("device_is_master", object.optInt("soma_is_master", 0));
                            MenuClearanceHelper.saveFromLogin(editor, object);
                            editor.apply();
                            // Cache academic year title locally for offline dashboard header
                            try {
                                int yearId = object.optInt("soma_academic", 0);
                                String yearTitle = object.optString("soma_academic_title", "").trim();
                                if (yearId > 0 && !yearTitle.isEmpty()) {
                                    android.database.Cursor yc = db.rawQuery(
                                            "select id from tb_academic_year where id=?",
                                            new String[]{String.valueOf(yearId)}
                                    );
                                    if (yc.moveToNext()) {
                                        db.execSQL("update tb_academic_year set title=? where id=?",
                                                new Object[]{yearTitle, yearId});
                                    } else {
                                        db.execSQL("insert into tb_academic_year (id,title) values (?,?)",
                                                new Object[]{yearId, yearTitle});
                                    }
                                    yc.close();
                                }
                            } catch (Exception ignore) {
                            }
                            openHome(object.getInt("soma_post"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /** No network: accept a staff account saved during the last online login. */
    private void loginOffline(String email, String pass) {
        if (staffAccountCount() < 1 || preferences.getInt(MainActivity.SK_ID, 0) == 0) {
            Snackbar.make(btnlogin, getString(R.string.log_offline_need_sync), Snackbar.LENGTH_LONG).show();
            return;
        }
        String needle = email.trim().toLowerCase(Locale.US);
        String digits = needle.replaceAll("[^0-9]", "");
        Cursor c = db.rawQuery(
                "select id,school_id,email,password_hash,name,post,post_title,status,menu_keys,app_menus,menu_full from tb_staff_login",
                null);
        JSONObject match = null;
        while (c.moveToNext()) {
            String storedEmail = c.getString(2) == null ? "" : c.getString(2).trim().toLowerCase(Locale.US);
            String storedPhone = "";
            Cursor phone = db.rawQuery("select phone from tb_staff_login where id=?", new String[]{String.valueOf(c.getInt(0))});
            if (phone.moveToFirst() && phone.getString(0) != null) {
                storedPhone = phone.getString(0).replaceAll("[^0-9]", "");
            }
            phone.close();
            boolean emailHit = storedEmail.length() > 0 && storedEmail.equals(needle);
            boolean phoneHit = digits.length() >= 9 && storedPhone.length() >= 9 && storedPhone.endsWith(digits.substring(Math.max(0, digits.length() - 9)));
            if (!emailHit && !phoneHit) continue;
            match = new JSONObject();
            try {
                match.put("id", c.getInt(0));
                match.put("school_id", c.getInt(1));
                match.put("password_hash", c.getString(3));
                match.put("name", c.getString(4));
                match.put("post", c.getInt(5));
                match.put("post_title", c.getString(6));
                match.put("status", c.getInt(7));
                match.put("menu_keys", c.getString(8));
                match.put("app_menus", c.getString(9));
                match.put("menu_full", c.getInt(10));
            } catch (JSONException e) {
                match = null;
            }
            break;
        }
        c.close();
        if (match == null) {
            etxtpass.setError(getString(R.string.log_both_err));
            etxtpass.requestFocus();
            return;
        }
        int status = match.optInt("status");
        if (status != 1 && status != 2) {
            etxtemail.setError(getString(R.string.log_acc_err));
            etxtemail.requestFocus();
            return;
        }
        int deviceSchool = preferences.getInt(MainActivity.SK_ID, 0);
        if (deviceSchool != 0 && deviceSchool != match.optInt("school_id")) {
            Snackbar.make(btnlogin, "Can not login, This device is assigned to " + preferences.getString(MainActivity.SK_NAME, ""), Snackbar.LENGTH_LONG).show();
            return;
        }
        String hash = match.optString("password_hash", "");
        boolean ok = false;
        try {
            ok = hash.startsWith("$2") && BCrypt.verifyer().verify(pass.toCharArray(), hash.toCharArray()).verified;
        } catch (Exception ignored) {
            ok = false;
        }
        if (!ok) {
            etxtpass.setError(getString(R.string.log_both_err));
            etxtpass.requestFocus();
            return;
        }
        int staffId = match.optInt("id");
        loadStaffWork(staffId);
        SharedPreferences.Editor editor = preferences.edit();
        if (swrmb.isChecked()) {
            Calendar cal = Calendar.getInstance();
            editor.putBoolean("remember_me", true)
                    .putLong("remember_date", (cal.getTimeInMillis() / 1000L) * 2592000);
        }
        int post = match.optInt("post");
        editor.putString(MainActivity.LOGIN, "Ok!")
                .putInt("soma_id", staffId)
                .putInt("soma_post", post)
                .putString("soma_name", match.optString("name", ""))
                .putString("soma_post_title", match.optString("post_title", ""))
                .putInt("soma_term", preferences.getInt("device_term", preferences.getInt("soma_term", 0)))
                .putInt("soma_term_number", preferences.getInt("device_term_number", preferences.getInt("soma_term_number", 0)))
                .putInt("soma_academic", preferences.getInt("device_academic", preferences.getInt("soma_academic", 0)))
                .putString("soma_academic_title", preferences.getString("device_academic_title", preferences.getString("soma_academic_title", "")))
                .putInt("soma_use_period", preferences.getInt("device_use_period", preferences.getInt("soma_use_period", 0)))
                .putString("school_country", preferences.getString("device_country", preferences.getString("school_country", "Rwanda")))
                .putString("academic_type", preferences.getString("device_academic_type", preferences.getString("academic_type", "")))
                .putInt("soma_is_master", preferences.getInt("device_is_master", preferences.getInt("soma_is_master", 0)));
        try {
            JSONObject menus = new JSONObject();
            menus.put("menu_full_access", match.optInt("menu_full") == 1);
            String keys = match.optString("menu_keys", "");
            String appMenus = match.optString("app_menus", "");
            if (keys.length() > 0) menus.put("menu_keys", new JSONArray(keys));
            if (appMenus.length() > 0) menus.put("app_menus", new JSONObject(appMenus));
            MenuClearanceHelper.saveFromLogin(editor, menus);
        } catch (JSONException ignored) {
        }
        editor.apply();
        openHome(post);
    }

    private void saveStaffBundle(JSONObject object) {
        StaffLoginStore.save(db, object);
    }

    private void loadStaffWork(int staffId) {
        db.execSQL("delete from tb_course");
        db.execSQL("delete from tb_class");
        Cursor courses = db.rawQuery("select id,title,code,marks,class_id from tb_staff_course where staff_id=?", new String[]{String.valueOf(staffId)});
        while (courses.moveToNext()) {
            db.execSQL("insert into tb_course (id,title,code,marks,class_id) values (?,?,?,?,?)",
                    new Object[]{courses.getInt(0), courses.getString(1), courses.getString(2), courses.getString(3), courses.getInt(4)});
        }
        courses.close();
        Cursor classes = db.rawQuery("select id,title,is_nursery from tb_staff_class where staff_id=?", new String[]{String.valueOf(staffId)});
        while (classes.moveToNext()) {
            String title = classes.getString(1);
            if (ClassLabels.isHoliday(title)) continue;
            db.execSQL("insert into tb_class (id,title,is_nursery) values (?,?,?)",
                    new Object[]{classes.getInt(0), title, classes.getInt(2)});
        }
        classes.close();
    }

    private void applyNurseryFlags(int staffId) {
        db.execSQL("update tb_class set is_nursery = ifnull((select is_nursery from tb_staff_class where tb_staff_class.id = tb_class.id and tb_staff_class.staff_id = ?), is_nursery)",
                new Object[]{staffId});
    }

    private void openHome(int post) {
        Intent intent;
        if (post == 2) {
            intent = new Intent(LoginActivity.this, WelcomeActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    private void syncCourses (JSONArray jarr) {
        //remove existing courses
        db.execSQL("delete from tb_course");
        try {
            int version = 0;
            for (int a = 0; jarr.length()>a;a++){
                JSONObject data = jarr.getJSONObject(a);
                try {
                    String name = escapeString(data.getString("title"));
                    String sql = "insert into tb_course (id,title,code,marks,class_id) values (?,?,?,?,?)";
                    Object[] args = new Object[]{data.getInt("id"),name,data.getString("code"),
                            data.getString("marks"), data.getInt("class_id")};
                    db.execSQL(sql,args);
                    Log.e("Course", data.getString("title"));
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void syncClasses (JSONArray jarr) {
        //remove existing courses
        db.execSQL("delete from tb_class");
        try {
            int version = 0;
            for (int a = 0; jarr.length()>a;a++){
                JSONObject data = jarr.getJSONObject(a);
                try {
                    String name = escapeString(data.getString("title"));
                    if (ClassLabels.isHoliday(name)) continue;
                    String sql = "insert into tb_class (id,title) values (?,?)";
                    Object[] args = new Object[]{data.getInt("id"),name};
                    db.execSQL(sql,args);
                    Log.e("Class", data.getString("title"));
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void syncAssessmentType (JSONArray jarr) {
        //remove existing courses
        db.execSQL("delete from tb_marks_type");
        try {
            int version = 0;
            for (int a = 0; jarr.length()>a;a++){
                JSONObject data = jarr.getJSONObject(a);
                try {
                    String name = escapeString(data.getString("title"));
                    String sql = "insert into tb_marks_type (id,academic_type,title) values (?,?,?)";
                    Object[] args = new Object[]{data.getInt("id"),data.getInt("academic_type_id"),name};
                    db.execSQL(sql,args);
                    Log.e("Type", data.getString("title"));
                }catch (Exception ex){
                    ex.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void setLocale(String lang,int position) {
        Locale myLocale = new Locale(lang);
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.locale = myLocale;
        res.updateConfiguration(conf, dm);
        preferences.edit().putInt("lang",position).apply();

        /*
        Intent refresh = new Intent(this, StartActivity.class);
        startActivity(refresh);
        finish();
        */
    }
    private String escapeString(String s) {
        if (s.contains("'")) {
            s = s.replaceAll("'", " ");
        }
        return s;
    }

}
