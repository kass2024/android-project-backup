package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.ConductCatalog;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class DisciplineActivity extends AppCompatActivity {
    private EditText etxtterm;
    private EditText edtSearch;
    private TextView txtstname, txtstclass, txtstregno, txtMarks, txtOccurrence, txtSanction, txtLegend, txtDeductLabel;
    private TextView btnEn, btnRw;
    private CircleImageView profileImage;
    private Spinner sptype;
    private Button btnsave;
    private CheckBox chknotify;
    private ListView lvCodes;
    private ProgressBar prb;
    private SQLiteDatabase db;
    private SharedPreferences preferences;
    private ConductCatalog.Code selected;
    private boolean reduceMarks = true;
    private final ArrayList<ConductCatalog.Code> allCodes = new ArrayList<>();
    private final ArrayList<Row> visibleRows = new ArrayList<>();
    private CodeAdapter codeAdapter;
    private String[] hayEn;
    private String[] hayRw;
    private final HashMap<String, Integer> priorCounts = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discipline);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        ConductCatalog.applyStoredIds(this, db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        bind();
        prepareCodes();
        loadPrior();
        applyFilter();
        pullServerIds();
    }

    private void bind() {
        prb = findViewById(R.id.prb);
        etxtterm = findViewById(R.id.etxtterm);
        edtSearch = findViewById(R.id.edt_code_search);
        sptype = findViewById(R.id.sptype);
        btnsave = findViewById(R.id.btnsave);
        chknotify = findViewById(R.id.chknotify);
        profileImage = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);
        lvCodes = findViewById(R.id.lv_codes);
        txtMarks = findViewById(R.id.txt_marks);
        txtOccurrence = findViewById(R.id.txt_occurrence);
        txtSanction = findViewById(R.id.txt_sanction);
        txtLegend = findViewById(R.id.txt_legend);
        txtDeductLabel = findViewById(R.id.txt_deduct_label);
        btnEn = findViewById(R.id.btn_lang_en);
        btnRw = findViewById(R.id.btn_lang_rw);

        sptype.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean wasReduce = reduceMarks;
                reduceMarks = position == 1;
                if (!reduceMarks && wasReduce) {
                    chknotify.setChecked(true);
                }
                applyActionUi();
                if (codeAdapter != null) codeAdapter.notifyDataSetChanged();
                showOccurrence();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        paintTypeSpinner(1);
        etxtterm.setText(getIntent().getStringExtra("term"));
        txtstregno.setText(getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(getIntent().getStringExtra("class"));
        StudentPhotoHelper.load(profileImage, getIntent().getStringExtra("photo"));

        codeAdapter = new CodeAdapter();
        lvCodes.setAdapter(codeAdapter);
        lvCodes.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleRows.size()) return;
            selected = visibleRows.get(position).code;
            codeAdapter.notifyDataSetChanged();
            showOccurrence();
        });
        btnEn.setOnClickListener(v -> {
            preferences.edit().putString(BehaviorMenuActivity.PREF_LANG, "en").apply();
            paintLang();
            applyFilter();
        });
        btnRw.setOnClickListener(v -> {
            preferences.edit().putString(BehaviorMenuActivity.PREF_LANG, "rw").apply();
            paintLang();
            applyFilter();
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnsave.setOnClickListener(v -> save());
        paintLang();
    }

    private boolean rw() {
        return BehaviorMenuActivity.isKinyarwanda(preferences);
    }

    private void paintLang() {
        boolean kinyarwanda = rw();
        btnEn.setBackgroundResource(kinyarwanda ? R.drawable.mode_tab_bg : R.drawable.mode_tab_active_bg);
        btnRw.setBackgroundResource(kinyarwanda ? R.drawable.mode_tab_active_bg : R.drawable.mode_tab_bg);
        btnEn.setTextColor(kinyarwanda ? getResources().getColor(R.color.textPrimary) : Color.WHITE);
        btnRw.setTextColor(kinyarwanda ? Color.WHITE : getResources().getColor(R.color.textPrimary));
        paintTypeSpinner(Math.max(0, sptype.getSelectedItemPosition()));
        applyActionUi();
    }

    private void paintTypeSpinner(int position) {
        boolean kinyarwanda = rw();
        int pos = position < 0 ? 1 : position;
        AdapterView.OnItemSelectedListener listener = sptype.getOnItemSelectedListener();
        sptype.setOnItemSelectedListener(null);
        sptype.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(kinyarwanda ? R.string.dis_arr_one_rw : R.string.dis_arr_one),
                        getString(kinyarwanda ? R.string.dis_arr_two_rw : R.string.dis_arr_two)
                }));
        sptype.setSelection(pos);
        reduceMarks = pos == 1;
        sptype.setOnItemSelectedListener(listener);
    }

    /** After a conduct code is chosen: deduct marks, or send the mistake as a parent remark. */
    private void applyActionUi() {
        boolean kinyarwanda = rw();
        chknotify.setVisibility(View.VISIBLE);
        if (reduceMarks) {
            chknotify.setText(getString(R.string.notify_parent));
            txtLegend.setText(getString(kinyarwanda ? R.string.conduct_legend_rw : R.string.conduct_legend));
            txtDeductLabel.setText(getString(kinyarwanda ? R.string.auto_deduct_rw : R.string.auto_deduct));
        } else {
            chknotify.setText(getString(kinyarwanda ? R.string.send_remarks_rw : R.string.send_remarks));
            txtLegend.setText(getString(kinyarwanda ? R.string.remark_legend_rw : R.string.remark_legend));
            txtDeductLabel.setText(getString(kinyarwanda ? R.string.remark_to_parent_rw : R.string.remark_to_parent));
        }
    }

    private void prepareCodes() {
        allCodes.clear();
        allCodes.addAll(ConductCatalog.load(this));
        hayEn = new String[allCodes.size()];
        hayRw = new String[allCodes.size()];
        for (int i = 0; i < allCodes.size(); i++) {
            ConductCatalog.Code code = allCodes.get(i);
            hayEn[i] = (code.codeNo + " " + code.title(false) + " " + code.category(false)).toLowerCase(Locale.US);
            hayRw[i] = (code.codeNo + " " + code.title(true) + " " + code.category(true)).toLowerCase(Locale.US);
        }
    }

    /** In-memory filter. No view rebuild and no database read while typing. */
    private void applyFilter() {
        String q = edtSearch.getText() == null ? "" : edtSearch.getText().toString().trim().toLowerCase(Locale.US);
        boolean kinyarwanda = rw();
        String[] hay = kinyarwanda ? hayRw : hayEn;
        visibleRows.clear();
        String lastCat = "";
        for (int i = 0; i < allCodes.size(); i++) {
            if (q.length() > 0 && hay[i].indexOf(q) < 0 && hayEn[i].indexOf(q) < 0 && hayRw[i].indexOf(q) < 0) {
                continue;
            }
            ConductCatalog.Code code = allCodes.get(i);
            String category = code.category(kinyarwanda);
            boolean header = !category.equals(lastCat);
            if (header) lastCat = category;
            visibleRows.add(new Row(code, header ? category : null));
        }
        if (codeAdapter != null) codeAdapter.notifyDataSetChanged();
        showOccurrence();
    }

    /** One local count for this student and term. The list never queries while scrolling. */
    private void loadPrior() {
        priorCounts.clear();
        int studentId = getIntent().getIntExtra("student_id", 0);
        int term = preferences.getInt("soma_term", 0);
        try {
            Cursor c = db.rawQuery(
                    "SELECT code_key, code_no, COUNT(*) FROM tb_discipline " +
                            "WHERE student_id=? AND IFNULL(active_term,0)=? AND IFNULL(code_no,0)>0 AND IFNULL(voided,0)=0 " +
                            "GROUP BY code_key, code_no",
                    new String[]{String.valueOf(studentId), String.valueOf(term)});
            while (c.moveToNext()) {
                priorCounts.put(c.getString(0) + "|" + c.getInt(1), c.getInt(2));
            }
            c.close();
        } catch (Exception ignored) {
        }
    }

    private int nextOccurrence(ConductCatalog.Code code) {
        if (code == null) return 1;
        Integer prev = priorCounts.get(code.categoryKey + "|" + code.codeNo);
        int n = (prev == null ? 0 : prev) + 1;
        if (n < 1) n = 1;
        if (n > 3) n = 3;
        return n;
    }

    private void showOccurrence() {
        boolean kinyarwanda = rw();
        if (selected == null) {
            txtMarks.setText("—");
            txtMarks.setTextColor(getResources().getColor(R.color.textMuted));
            txtOccurrence.setText(getString(kinyarwanda ? R.string.tap_conduct_code_rw : R.string.tap_conduct_code));
            txtSanction.setVisibility(View.GONE);
            return;
        }
        int n = selected.isFlat() ? 1 : nextOccurrence(selected);
        int marks = reduceMarks ? selected.marksFor(n) : 0;
        String pay = selected.sanction(kinyarwanda, 1);
        if (pay == null) pay = "";
        pay = pay.trim();
        if (selected.isFlat() && reduceMarks) {
            txtMarks.setText(marks > 0 ? ("\u2212" + marks) : "0");
            txtMarks.setTextColor(marks > 0
                    ? getResources().getColor(R.color.colorAccent)
                    : getResources().getColor(R.color.textMuted));
            txtOccurrence.setText(getString(kinyarwanda ? R.string.every_time_rw : R.string.every_time));
            if (pay.length() > 0) {
                txtSanction.setText(pay);
                txtSanction.setVisibility(View.VISIBLE);
            } else {
                txtSanction.setVisibility(View.GONE);
            }
            return;
        }
        if (!reduceMarks) {
            txtMarks.setText("0");
            txtMarks.setTextColor(getResources().getColor(R.color.textMuted));
            txtOccurrence.setText(getString(kinyarwanda ? R.string.mistake_committed_rw : R.string.mistake_committed)
                    + ": " + selected.title(kinyarwanda));
        } else if (marks <= 0) {
            txtMarks.setText("0");
            txtMarks.setTextColor(getResources().getColor(R.color.textMuted));
            txtOccurrence.setText(occurrenceLabel(n) + " · " + getString(kinyarwanda ? R.string.no_marks_now_rw : R.string.no_marks_now));
        } else {
            txtMarks.setText("\u2212" + marks);
            txtMarks.setTextColor(getResources().getColor(R.color.colorAccent));
            txtOccurrence.setText(occurrenceLabel(n));
        }
        String sanction = selected.sanction(kinyarwanda, n);
        if (sanction == null) sanction = "";
        sanction = sanction.trim();
        if (sanction.length() > 0) {
            txtSanction.setText(sanction);
            txtSanction.setVisibility(View.VISIBLE);
        } else {
            txtSanction.setVisibility(View.GONE);
        }
    }

    private int occurrence() {
        return nextOccurrence(selected);
    }

    private String occurrenceLabel(int n) {
        boolean kinyarwanda = rw();
        if (n <= 1) return getString(kinyarwanda ? R.string.occ_first_rw : R.string.occ_first);
        if (n == 2) return getString(kinyarwanda ? R.string.occ_second_rw : R.string.occ_second);
        return getString(kinyarwanda ? R.string.occ_third_rw : R.string.occ_third);
    }

    private void save() {
        if (selected == null) {
            Toast.makeText(this, R.string.select_conduct_code, Toast.LENGTH_LONG).show();
            return;
        }
        int studentId = getIntent().getIntExtra("student_id", 0);
        int type = reduceMarks ? 1 : 0;
        boolean flat = selected.isFlat();
        int n = flat ? 1 : occurrence();
        int marks = type == 1 ? selected.marksFor(n) : 0;
        String sanction = selected.sanction(rw(), n);
        if (sanction == null) sanction = "";
        String when = flat
                ? getString(rw() ? R.string.every_time_rw : R.string.every_time)
                : occurrenceLabel(n);
        String comment = selected.title(rw()) + " (" + when + ")"
                + (sanction.length() > 0 ? " — " + sanction : "");
        int notify = chknotify.isChecked() ? 1 : 0;
        int term = preferences.getInt("soma_term", 0);
        db.execSQL("INSERT INTO tb_discipline (student_id,type,notify_parent,comment,school_id,operator,marks,code_id,code_key,code_no,occurrence,active_term,sync,lang) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?)",
                new Object[]{
                        studentId,
                        type,
                        notify,
                        comment,
                        preferences.getInt(MainActivity.SK_ID, 0),
                        preferences.getInt("soma_id", 0),
                        marks,
                        selected.serverId,
                        selected.categoryKey,
                        selected.codeNo,
                        n,
                        term,
                        rw() ? "rw" : "en"
                });
        Toast.makeText(this, R.string.disc_saved_offline, Toast.LENGTH_LONG).show();
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
        finish();
    }

    private void pullServerIds() {
        if (!MainActivity.isNetworkAvailable(this)) return;
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        String url = MainActivity.SERVER + "discipline_codes?school_id=" + schoolId + "&lang=en";
        new HttpRequest(this).get(url, JSONObject.class, (object, statusCode, status) -> {
            if (object == null) return;
            JSONArray groups = object.optJSONArray("groups");
            if (groups == null) return;
            ConductCatalog.mergeServerGroups(DisciplineActivity.this, db, groups);
            runOnUiThread(() -> {
                prepareCodes();
                applyFilter();
            });
        });
    }

    private static class Row {
        final ConductCatalog.Code code;
        final String category;
        Row(ConductCatalog.Code code, String category) {
            this.code = code;
            this.category = category;
        }
    }

    private static class Holder {
        TextView cat;
        TextView title;
        TextView now;
        TextView nowLabel;
        TextView s1;
        TextView s2;
        TextView s3;
        View card;
    }

    private class CodeAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleRows.size(); }
        @Override public Object getItem(int position) { return visibleRows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_conduct_code, parent, false);
                h = new Holder();
                h.cat = convertView.findViewById(R.id.txt_cat);
                h.title = convertView.findViewById(R.id.txt_title);
                h.now = convertView.findViewById(R.id.txt_now);
                h.nowLabel = convertView.findViewById(R.id.txt_now_label);
                h.s1 = convertView.findViewById(R.id.txt_s1);
                h.s2 = convertView.findViewById(R.id.txt_s2);
                h.s3 = convertView.findViewById(R.id.txt_s3);
                h.card = convertView.findViewById(R.id.card);
                convertView.setTag(h);
            } else {
                h = (Holder) convertView.getTag();
            }
            Row item = visibleRows.get(position);
            boolean kinyarwanda = rw();
            if (item.category != null) {
                h.cat.setVisibility(View.VISIBLE);
                h.cat.setText(item.category);
            } else {
                h.cat.setVisibility(View.GONE);
            }
            h.title.setText(item.code.codeNo + ". " + item.code.title(kinyarwanda));
            boolean flat = item.code.isFlat();
            int n = flat ? 1 : nextOccurrence(item.code);
            int nowMarks = reduceMarks ? item.code.marksFor(n) : 0;
            if (!reduceMarks) {
                h.now.setText("0");
                h.now.setTextColor(getResources().getColor(R.color.textMuted));
                h.nowLabel.setText(getString(kinyarwanda ? R.string.send_remarks_rw : R.string.send_remarks));
            } else if (nowMarks <= 0) {
                h.now.setText("0");
                h.now.setTextColor(getResources().getColor(R.color.textMuted));
                h.nowLabel.setText(getString(kinyarwanda ? R.string.no_marks_now_rw : R.string.no_marks_now));
            } else {
                h.now.setText("\u2212" + nowMarks);
                h.now.setTextColor(Color.parseColor("#0369A1"));
                h.nowLabel.setText(getString(kinyarwanda
                        ? (flat ? R.string.every_time_rw : R.string.now_label_rw)
                        : (flat ? R.string.every_time : R.string.now_label)));
            }
            if (flat) {
                String pay = item.code.sanction(kinyarwanda, 1);
                if (pay == null) pay = "";
                h.s2.setVisibility(View.GONE);
                h.s3.setVisibility(View.GONE);
                h.s1.setVisibility(View.VISIBLE);
                h.s1.setText(pay.length() > 0 ? pay : getString(kinyarwanda ? R.string.every_time_rw : R.string.every_time));
                h.s1.setBackgroundResource(R.drawable.conduct_step_on);
                h.s1.setTextColor(Color.WHITE);
            } else {
                h.s1.setVisibility(View.VISIBLE);
                h.s2.setVisibility(View.VISIBLE);
                h.s3.setVisibility(View.VISIBLE);
                paintStep(h.s1, 1, n, item.code.m1, kinyarwanda);
                paintStep(h.s2, 2, n, item.code.m2, kinyarwanda);
                paintStep(h.s3, 3, n, item.code.m3, kinyarwanda);
            }
            boolean on = selected != null
                    && selected.categoryKey.equals(item.code.categoryKey)
                    && selected.codeNo == item.code.codeNo;
            h.card.setBackgroundResource(on ? R.drawable.conduct_card_on : R.drawable.conduct_card);
            return convertView;
        }
    }

    private void paintStep(TextView view, int step, int active, int marks, boolean kinyarwanda) {
        String label = stepLabel(step, kinyarwanda);
        view.setText(marks > 0 ? (label + "  \u2212" + marks) : (label + "  0"));
        boolean on = reduceMarks && step == active;
        view.setBackgroundResource(on ? R.drawable.conduct_step_on : R.drawable.conduct_step);
        view.setTextColor(on ? Color.WHITE : getResources().getColor(R.color.textPrimary));
    }

    private String stepLabel(int step, boolean kinyarwanda) {
        if (kinyarwanda) {
            if (step <= 1) return "1";
            if (step == 2) return "2";
            return "3";
        }
        if (step <= 1) return "1st";
        if (step == 2) return "2nd";
        return "3rd";
    }
}
