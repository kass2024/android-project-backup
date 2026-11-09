package com.qonics.somanet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BehaviorMenuActivity extends AppCompatActivity {
    static final String PREF_LANG = "disc_lang";

    private SharedPreferences preferences;
    private TextView btnEn;
    private TextView btnRw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_behavior_menu);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        btnEn = findViewById(R.id.btn_lang_en);
        btnRw = findViewById(R.id.btn_lang_rw);
        LinearLayout record = findViewById(R.id.lnl_record);
        LinearLayout completed = findViewById(R.id.lnl_completed);
        LinearLayout list = findViewById(R.id.lnl_record_list);
        LinearLayout lateness = findViewById(R.id.lnl_lateness);

        btnEn.setOnClickListener(v -> setLang("en"));
        btnRw.setOnClickListener(v -> setLang("rw"));
        paintLang();

        record.setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchStudentActivity.class);
            intent.putExtra("who", 0);
            startActivity(intent);
        });
        completed.setOnClickListener(v -> openList("completed"));
        list.setOnClickListener(v -> openList("record"));
        lateness.setOnClickListener(v -> startActivity(new Intent(this, LatenessReportActivity.class)));
    }

    private void openList(String mode) {
        Intent intent = new Intent(this, DisciplineRecordActivity.class);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

    private void setLang(String lang) {
        preferences.edit().putString(PREF_LANG, lang).apply();
        paintLang();
    }

    private void paintLang() {
        boolean rw = "rw".equals(preferences.getString(PREF_LANG, "en"));
        style(btnEn, !rw);
        style(btnRw, rw);
    }

    private void style(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.mode_tab_active_bg : R.drawable.mode_tab_bg);
        tab.setTextColor(active ? Color.WHITE : getResources().getColor(R.color.textPrimary));
    }

    static boolean isKinyarwanda(SharedPreferences preferences) {
        return preferences != null && "rw".equals(preferences.getString(PREF_LANG, "en"));
    }
}
