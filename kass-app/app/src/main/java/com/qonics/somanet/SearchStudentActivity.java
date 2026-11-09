package com.qonics.somanet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.StudentModel;
import com.qonics.somanet.payments.OfflinePaymentActivity;
import com.qonics.utils.Callback;
import com.qonics.utils.ClassLabels;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.PaymentUsbReader;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import de.hdodenhof.circleimageview.CircleImageView;

public class SearchStudentActivity extends AppCompatActivity {
    private static final String TAG = SearchStudentActivity.class.getSimpleName();

    SharedPreferences preferences;
    LinearLayout lnlwarning, lnlsearch;
    ProgressBar prb;
    SearchView srchstudent;
    TextView txterror;
    Button btnsearch;
    CheckBox chksearchtype;
    ListView lvstudent;
    HttpRequest req;
    String server = MainActivity.SERVER;

    Timer timer;
    TimerTask timerTask;
    int who = 0; // 0: discipline,1: permission,2: students,3: payment,4: library,5: offline payment,6: material check,7: hostel assign,8: hostel unassign,9: student photo
    ArrayList<StudentModel> students = new ArrayList<>();
    ArrayAdapter adapter;
    boolean SCAN_CARD = false;
    boolean pending = false;

    NFCForegroundUtil nfcForegroundUtil = null;
    private NfcAdapter nfcAdapter;
    SQLiteDatabase db;
    private final HashMap<String, StudentModel> cardIndex = new HashMap<>();
    private String lastUid = "";
    private long lastUidAt = 0;
    private boolean opened = false;
    private String inFlightUid = "";
    private int rosterPages = 0;
    private PaymentUsbReader paymentUsb;
    private boolean paymentUsbConnected;
    private boolean usbForcedCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_student);

        db = this.openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        chksearchtype = findViewById(R.id.chksearchtype);
        lnlsearch = findViewById(R.id.lnlsearch);
        who = getIntent().getIntExtra("who", 0);

        // NFC initialization only (Speedata device removed)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            chksearchtype.setChecked(false);
            chksearchtype.setEnabled(false);
        } else {
            nfcForegroundUtil = new NFCForegroundUtil(this);
            nfcForegroundUtil.setTagListener(this::onNfcTag);
            chksearchtype.setEnabled(true);
            // Discipline and payment start on the card, the same way Pocket Money does.
            chksearchtype.setChecked(who == 0 || isPaymentSearch());
        }

        adapter = new StudentAdapter();
        populateView();
        rebuildCardIndex();
        applyCardMode(chksearchtype.isChecked());
        refreshCardRoster();
        if (isPaymentSearch()) {
            paymentUsb = new PaymentUsbReader();
            paymentUsb.start(this, new PaymentUsbReader.Listener() {
                @Override
                public void onReader(boolean connected) {
                    if (isFinishing()) return;
                    runOnUiThread(() -> onPaymentReader(connected));
                }

                @Override
                public void onCard(String uid) {
                    if (isFinishing()) return;
                    runOnUiThread(() -> verifyPaymentCard(uid));
                }
            });
        }
    }

    private boolean isPaymentSearch() {
        return who == 3 || who == 5;
    }

    /** USB reader on the payment search only. The checkbox follows the reader. */
    private void onPaymentReader(boolean connected) {
        if (!isPaymentSearch() || chksearchtype == null) return;
        paymentUsbConnected = connected;
        if (connected) {
            chksearchtype.setEnabled(true);
            if (!chksearchtype.isChecked()) {
                usbForcedCard = true;
                chksearchtype.setChecked(true);
            }
        } else if (usbForcedCard) {
            usbForcedCard = false;
            if (nfcAdapter == null) chksearchtype.setEnabled(false);
            chksearchtype.setChecked(false);
        }
        applyCardMode(chksearchtype.isChecked());
    }

    private void verifyPaymentCard(String uid) {
        if (!isPaymentSearch() || uid == null || opened) return;
        String cardId = uid.trim().toUpperCase();
        if (cardId.length() < 4) return;
        long now = SystemClock.elapsedRealtime();
        if (cardId.equals(inFlightUid)) return;
        if (cardId.equals(lastUid) && now - lastUidAt < 900) return;
        lastUid = cardId;
        lastUidAt = now;
        if (chksearchtype != null && !chksearchtype.isChecked()) {
            usbForcedCard = true;
            chksearchtype.setChecked(true);
        }
        StudentModel local = findCachedCard(cardId);
        if (local != null) {
            openStudent(local);
            return;
        }
        lookupCardOnline(cardId);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isPaymentSearch() && paymentUsb != null && chksearchtype != null && chksearchtype.isChecked()
                && PaymentUsbReader.isPhysicalKeyboard(event)) {
            paymentUsb.onKey(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Card mode stays on from this screen until the marks deduction activity opens. */
    private void applyCardMode(boolean card) {
        boolean on = card && (nfcAdapter != null || (isPaymentSearch() && paymentUsbConnected));
        SCAN_CARD = on;
        if (lnlsearch != null) {
            lnlsearch.setVisibility(on ? View.GONE : View.VISIBLE);
        }
        View swipe = findViewById(R.id.imgswipe);
        if (swipe != null) {
            swipe.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private void populateView() {
        lnlwarning = findViewById(R.id.lnlwarning);
        txterror = findViewById(R.id.txterror);
        lnlwarning.setVisibility(View.GONE);

        prb = findViewById(R.id.prb);
        srchstudent = findViewById(R.id.srchstudent);
        btnsearch = findViewById(R.id.btnsearch);
        lvstudent = findViewById(R.id.lvstudent);

        prb.setVisibility(View.INVISIBLE);

        lvstudent.setAdapter(adapter);
        lvstudent.setOnItemClickListener((adapterView, view, i, l) -> launchActivity(students.get(i)));

        chksearchtype.setOnCheckedChangeListener((compoundButton, b) -> {
            lnlwarning.setVisibility(View.GONE);
            applyCardMode(b);
            if (!b) {
                searchStudentsByName(srchstudent.getQuery().toString().trim());
            }
        });

        srchstudent.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                searchStudentsByName(query == null ? "" : query.trim());
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                searchStudentsByName(newText == null ? "" : newText.trim());
                return true;
            }
        });

        btnsearch.setOnClickListener(view ->
                searchStudentsByName(srchstudent.getQuery().toString().trim()));
        searchStudentsByName("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        SCAN_CARD = true;
        if (nfcAdapter != null && nfcForegroundUtil != null) {
            try {
                if (!nfcAdapter.isEnabled()) {
                    Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG).show();
                }
                if (isPaymentSearch()) {
                    nfcForegroundUtil.enableScanReader();
                } else {
                    nfcForegroundUtil.enableForeground();
                }
            } catch (Exception e) {
                Log.e(TAG, "NFC enable failed", e);
            }
        }
        // Also handle tag if activity was (re)started with an NFC intent
        handleNfcIntent(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        SCAN_CARD = false;
        if (nfcForegroundUtil != null) {
            nfcForegroundUtil.disableForeground();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (timer != null) {
                timer.purge();
                timer.cancel();
            }
        } catch (Exception ignored) {}
        if (paymentUsb != null) {
            paymentUsb.stop();
            paymentUsb = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }

    private void onNfcTag(Tag tag) {
        if (tag == null || opened) {
            return;
        }
        String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
        if (cardId.isEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (cardId.equals(inFlightUid)) {
            return;
        }
        if (cardId.equals(lastUid) && now - lastUidAt < 900) {
            return;
        }
        lastUid = cardId;
        lastUidAt = now;
        Log.e(TAG, "NFC UID=" + cardId + " stored=" + NFCForegroundUtil.storageForm(cardId));
        if (!chksearchtype.isChecked()) {
            chksearchtype.setChecked(true);
        }
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(30);
            }
        } catch (Exception ignored) {
        }
        StudentModel local = findCachedCard(cardId);
        if (local != null) {
            openStudent(local);
            return;
        }
        lookupCardOnline(cardId);
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            onNfcTag(tag);
        }
    }

    private String bytesToHexString(byte[] src) {
        return NFCForegroundUtil.bytesToHex(src);
    }

    private void lookupCardOnline(String cardId) {
        StudentModel again = findCachedCard(cardId);
        if (again != null) {
            openStudent(again);
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (!isNetworkAvailable() || schoolId <= 0) {
            showCardMiss(cardId, false);
            return;
        }
        inFlightUid = cardId;
        lnlwarning.setVisibility(View.GONE);
        prb.setVisibility(View.VISIBLE);
        Map<String, Object> map = new HashMap<>();
        // Same value the web USB scanner posts: reader hex, not pre-reversed.
        // The server tries that value and the byte-reversed storage form.
        map.put("card", cardId);
        map.put("school_id", schoolId);
        req = new HttpRequest(this);
        req.timeouts(2, 3).post(server + "discipline_card_scan", map, (object, status_code, status) -> {
            if (opened) {
                return;
            }
            inFlightUid = "";
            prb.setVisibility(View.INVISIBLE);
            StudentModel cached = findCachedCard(cardId);
            if (cached != null) {
                openStudent(cached);
                return;
            }
            JSONObject st = object == null ? null : object.optJSONObject("student");
            if (st != null && st.optInt("id", 0) > 0) {
                String stored = cleanCard(object.optString("card", NFCForegroundUtil.storageForm(cardId)));
                StudentModel known = findStudentById(st.optInt("id"));
                StudentModel model = known != null ? known : new StudentModel(
                        st.optInt("id"),
                        st.optString("name", ""),
                        st.optString("regno", ""),
                        st.optInt("class_id", 0),
                        st.optString("class", ""),
                        st.optString("sex", ""),
                        st.optString("photo", ""),
                        preferences.getInt("soma_term_number", 0),
                        stored,
                        st.optString("phone", "")
                );
                try {
                    rememberStudent(model, stored);
                } catch (Exception e) {
                    Log.e(TAG, "remember card", e);
                }
                openStudent(model);
                return;
            }
            showCardMiss(cardId, object != null);
        });
    }

    private void showCardMiss(String cardId, boolean online) {
        pending = false;
        String stored = NFCForegroundUtil.storageForm(cardId);
        lnlwarning.setVisibility(View.VISIBLE);
        if (online) {
            txterror.setText("⚠️ No student found for card: " + cardId
                    + " (also tried " + stored + ")");
        } else {
            txterror.setText("⚠️ No student found for card: " + stored
                    + ". Connect once so assigned cards can load, then this scan works offline.");
        }
    }

    private StudentModel findStudentById(int id) {
        if (id <= 0) {
            return null;
        }
        Cursor c = db.rawQuery(
                "SELECT id, name, regno, class_id, classe, sex, photo, card, phone FROM tb_student WHERE id=?",
                new String[]{String.valueOf(id)});
        StudentModel model = null;
        if (c.moveToFirst()) {
            model = new StudentModel(
                    c.getInt(0), c.getString(1), c.getString(2), c.getInt(3),
                    c.getString(4), c.getString(5), c.getString(6),
                    preferences.getInt("soma_term_number", 0),
                    c.getString(7), c.getString(8));
        }
        c.close();
        return model;
    }

    private StudentModel findCachedCard(String cardId) {
        for (String variant : NFCForegroundUtil.lookupVariants(cardId)) {
            StudentModel hit = cardIndex.get(variant);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void rebuildCardIndex() {
        cardIndex.clear();
        Cursor c = db.rawQuery(
                "SELECT id, name, regno, class_id, classe, sex, photo, card, phone FROM tb_student "
                        + "WHERE card IS NOT NULL AND TRIM(card)<>'' AND IFNULL(status,1)<>0",
                null);
        int term = preferences.getInt("soma_term_number", 0);
        while (c.moveToNext()) {
            StudentModel model = new StudentModel(
                    c.getInt(0), c.getString(1), c.getString(2), c.getInt(3),
                    c.getString(4), c.getString(5), c.getString(6), term,
                    cleanCard(c.getString(7)), c.getString(8));
            indexStudent(model);
        }
        c.close();
    }

    private void indexStudent(StudentModel model) {
        if (model == null || model.getCard() == null) {
            return;
        }
        for (String variant : NFCForegroundUtil.lookupVariants(model.getCard())) {
            cardIndex.put(variant, model);
        }
    }

    private void refreshCardRoster() {
        if (!isNetworkAvailable() || opened) {
            return;
        }
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (schoolId <= 0 || rosterPages >= 4) {
            return;
        }
        rosterPages++;
        long updatedAt = rosterPages == 1 ? 0 : preferences.getLong("card_roster_updated_at", 0);
        int afterId = rosterPages == 1 ? 0 : preferences.getInt("card_roster_after_id", 0);
        String url = server + "sync/student_v2/" + schoolId
                + "?updatedAt=" + updatedAt + "&afterId=" + afterId + "&limit=800";
        new HttpRequest(this).timeouts(5, 12).get(url, JSONObject.class, (object, status_code, status) -> {
            if (object == null || opened) {
                return;
            }
            JSONArray rows = object.optJSONArray("students");
            if (rows != null && rows.length() > 0) {
                applyCardRoster(rows);
                StudentModel waiting = findCachedCard(lastUid);
                if (waiting != null) {
                    openStudent(waiting);
                    return;
                }
            }
            if (object.optBoolean("has_more", false) && rows != null && rows.length() > 0) {
                preferences.edit()
                        .putLong("card_roster_updated_at", object.optLong("next_updated_at", updatedAt))
                        .putInt("card_roster_after_id", object.optInt("next_id", afterId))
                        .apply();
                refreshCardRoster();
            }
        });
    }

    private void applyCardRoster(JSONArray rows) {
        db.beginTransaction();
        try {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject st = rows.optJSONObject(i);
                if (st == null) {
                    continue;
                }
                int id = st.optInt("id", 0);
                String card = cleanCard(st.optString("card", ""));
                if (id <= 0 || card.isEmpty()) {
                    continue;
                }
                String name = st.optString("name", "");
                String regno = st.optString("regno", "");
                String classe = st.optString("class", "");
                int classId = st.optInt("class_id", 0);
                String photo = st.optString("photo", "");
                String sex = st.optString("sex", "");
                String phone = st.optString("phone", "");
                Cursor exists = db.rawQuery(
                        "SELECT IFNULL(status,1) FROM tb_student WHERE id=?",
                        new String[]{String.valueOf(id)});
                boolean found = exists.moveToFirst();
                int status = found ? exists.getInt(0) : 1;
                exists.close();
                if (found) {
                    if (status == 0) {
                        continue;
                    }
                    db.execSQL(
                            "UPDATE tb_student SET card=?, name=?, regno=?, classe=?, class_id=?, photo=?, phone=?, sex=? WHERE id=?",
                            new Object[]{card, name, regno, classe, classId, photo, phone, sex, id});
                } else {
                    try {
                        db.execSQL(
                                "INSERT INTO tb_student (id,regno,name,card,classe,dept,studying_mode,photo,class_id,phone,sex,status,record_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                new Object[]{id, regno, name, card, classe, "", 0, photo, classId, phone, sex, 1, 0});
                    } catch (Exception ex) {
                        Log.e(TAG, "card roster insert " + id, ex);
                    }
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "card roster", e);
        } finally {
            db.endTransaction();
        }
        rebuildCardIndex();
        Log.e(TAG, "Card roster indexed " + cardIndex.size() + " uid forms");
    }

    private static String cleanCard(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toUpperCase().replaceAll("[^A-F0-9]", "");
    }

    private void openStudent(StudentModel student) {
        if (opened || student == null) {
            return;
        }
        opened = true;
        inFlightUid = "";
        launchActivity(student);
    }

    private StudentModel studentFromApiOrLocal(JSONObject st, String card) throws Exception {
        int id = st.getInt("id");
        Cursor c = db.rawQuery("SELECT * FROM tb_student WHERE id=" + id, null);
        if (c.moveToFirst()) {
            String saved = cleanCard(card);
            if (saved.isEmpty()) {
                saved = cleanCard(c.getString(c.getColumnIndex("card")));
            }
            StudentModel m = new StudentModel(
                    c.getInt(c.getColumnIndex("id")),
                    c.getString(c.getColumnIndex("name")),
                    c.getString(c.getColumnIndex("regno")),
                    c.getInt(c.getColumnIndex("class_id")),
                    c.getString(c.getColumnIndex("classe")),
                    c.getString(c.getColumnIndex("sex")),
                    c.getString(c.getColumnIndex("photo")),
                    preferences.getInt("soma_term_number", 0),
                    saved,
                    c.getString(c.getColumnIndex("phone"))
            );
            c.close();
            rememberStudent(m, saved);
            return m;
        }
        c.close();
        rememberStudent(new StudentModel(
                id,
                st.optString("name"),
                st.optString("regno"),
                st.optInt("class_id", 0),
                st.optString("class", ""),
                "",
                st.optString("photo", ""),
                preferences.getInt("soma_term_number", 0),
                cleanCard(card),
                st.optString("phone", "")
        ), card);
        return new StudentModel(
                id,
                st.optString("name"),
                st.optString("regno"),
                st.optInt("class_id", 0),
                st.optString("class", ""),
                "",
                st.optString("photo", ""),
                preferences.getInt("soma_term_number", 0),
                card,
                st.optString("phone", "")
        );
    }

    private void rememberStudent(StudentModel model, String storedCard) {
        if (model == null) {
            return;
        }
        String card = cleanCard(storedCard);
        if (card.isEmpty()) {
            card = cleanCard(model.getCard());
        }
        if (card.isEmpty() || model.getId() <= 0) {
            return;
        }
        Cursor exists = db.rawQuery(
                "SELECT IFNULL(status,1) FROM tb_student WHERE id=?",
                new String[]{String.valueOf(model.getId())});
        boolean found = exists.moveToFirst();
        int status = found ? exists.getInt(0) : 1;
        exists.close();
        if (found) {
            if (status != 0) {
                db.execSQL("UPDATE tb_student SET card=? WHERE id=?", new Object[]{card, model.getId()});
            }
        } else {
            try {
                db.execSQL(
                        "INSERT INTO tb_student (id,regno,name,card,classe,dept,studying_mode,photo,class_id,phone,sex,status,record_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                                model.getId(), model.getRegno(), model.getName(), card, model.getClasse(),
                                "", 0, model.getPhoto(), model.getClassId(), model.getPhone(), model.getSex(), 1, 0
                        });
            } catch (Exception ex) {
                Log.e(TAG, "remember card " + model.getId(), ex);
            }
        }
        indexStudent(new StudentModel(
                model.getId(), model.getName(), model.getRegno(), model.getClassId(), model.getClasse(),
                model.getSex(), model.getPhoto(), model.getTermInt(), card, model.getPhone()));
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void searchStudent(String data) {
        if (chksearchtype.isChecked() && data != null && data.length() >= 3) {
            StudentModel hit = findCachedCard(data);
            if (hit != null) {
                openStudent(hit);
            } else {
                lookupCardOnline(data);
            }
            return;
        }
        searchStudentsByName(data == null ? "" : data.trim());
    }

    /** All synced students, filtered from the first character. Local database only. */
    private void searchStudentsByName(String data) {
        pending = true;
        lnlwarning.setVisibility(View.GONE);
        students.clear();
        String sql = "SELECT id, name, regno, class_id, classe, sex, photo, card, phone FROM tb_student ";
        String[] args = null;
        if (data != null && data.length() > 0) {
            sql += "WHERE name LIKE ? OR regno LIKE ? OR classe LIKE ? ";
            String like = "%" + data + "%";
            args = new String[]{like, like, like};
        }
        sql += "ORDER BY name COLLATE NOCASE";
        Cursor c = args == null ? db.rawQuery(sql, null) : db.rawQuery(sql, args);
        int term = preferences.getInt("soma_term_number", 0);
        while (c.moveToNext()) {
            students.add(new StudentModel(
                    c.getInt(0),
                    c.getString(1),
                    c.getString(2),
                    c.getInt(3),
                    c.getString(4),
                    c.getString(5),
                    c.getString(6),
                    term,
                    c.getString(7),
                    c.getString(8)
            ));
        }
        c.close();
        adapter.notifyDataSetChanged();
        if (students.isEmpty() && data != null && data.length() > 0) {
            lnlwarning.setVisibility(View.VISIBLE);
            txterror.setText("No student found for: " + data);
        }
        pending = false;
    }

    private void launchActivity(StudentModel student) {
        Class<?> destination = null;
        switch (who) {
            case 0: destination = DisciplineActivity.class; break;
            case 1: destination = PermissionActivity.class; break;
            case 2: destination = StudentActivity.class; break;
            case 3: destination = PaymentActivity.class; break;
            case 4: destination = LibraryActivity.class; break;
            case 5: destination = OfflinePaymentActivity.class; break;
            case 6: destination = MaterialCheckActivity.class; break;
            case 7: destination = HostelAssignActivity.class; break;
            case 8: destination = HostelUnassignActivity.class; break;
            case 9: destination = StudentActivity.class; break;
        }
        if (destination == null) return;

        Intent intent = new Intent(this, destination);
        intent.putExtra("student_id", student.getId());
        intent.putExtra("name", student.getName());
        intent.putExtra("class", ClassLabels.visible(student.getClasse()));
        intent.putExtra("classId", student.getClassId());
        intent.putExtra("regno", student.getRegno());
        intent.putExtra("term", student.getTerm(this));
        intent.putExtra("term_int", student.getTermInt());
        intent.putExtra("card", student.getCard());
        intent.putExtra("phone", student.getPhone());
        intent.putExtra("photo", student.getPhoto());
        if (who == 9) {
            intent.putExtra("focus_photo", true);
        }
        startActivity(intent);
        finish();
    }

    private void showSnackBar(String msg) {
        Snackbar snackbar = Snackbar.make(lnlwarning, msg, Snackbar.LENGTH_LONG);
        snackbar.setActionTextColor(Color.RED);
        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setTextColor(Color.WHITE);
        snackbar.show();
    }

    private class StudentAdapter extends ArrayAdapter<StudentModel> {
        public StudentAdapter() {
            super(SearchStudentActivity.this, R.layout.one_student, students);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) v = getLayoutInflater().inflate(R.layout.one_student, parent, false);

            TextView txtname = v.findViewById(R.id.txtstname);
            TextView txtclass = v.findViewById(R.id.txtstclass);
            TextView txtregno = v.findViewById(R.id.txtstregno);
            CircleImageView img = v.findViewById(R.id.profile_image);

            StudentModel st = students.get(position);
            txtname.setText(st.getName());
            String shownClass = ClassLabels.visible(st.getClasse());
            txtclass.setText(shownClass.isEmpty() ? "" : "Class: " + shownClass);
            txtregno.setText(st.getRegno());

            if (st.getPhoto() != null && st.getPhoto().length() > 3) {
                StudentPhotoHelper.load(img, st.getPhoto());
            } else {
                img.setImageResource(R.drawable.placeholder_image);
            }

            return v;
        }
    }
}

