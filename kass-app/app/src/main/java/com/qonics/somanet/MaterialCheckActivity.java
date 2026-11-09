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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Material check (web-aligned):
 * Expected / Brought / Missing are always consistent (Missing = Expected − Brought).
 * If Missing &gt; 0, show an "Add now" entry to record newly brought items;
 * on save: Brought += Add now (capped at Expected) until Complete.
 */
public class MaterialCheckActivity extends AppCompatActivity {
    private static final String TAG = "MaterialCheckActivity";

    private SQLiteDatabase db;
    private SharedPreferences preferences;
    private LinearLayout lnlMaterials;
    private TextView kpiTotal;
    private TextView kpiComplete;
    private TextView kpiPartial;
    private TextView kpiMissing;
    private int studentId;
    private int classId;
    private int academicYear;
    private final ArrayList<MaterialRow> rows = new ArrayList<>();
    private final ArrayList<String> lastSavedIds = new ArrayList<>();
    private TextView txtName;
    private TextView txtClass;
    private TextView txtRegno;
    private TextView txtLastChecked;
    private CircleImageView profileImage;

    private static class MaterialRow {
        int materialId;
        String name;
        String unit;
        double quantityRequired;
        /** Saved total already brought (before this visit's add). */
        double savedBrought;
        String existingId;
        TextView txtBrought;
        TextView txtMissing;
        TextView txtStatus;
        View panelNewEntry;
        EditText etAddNow;
        boolean updatingUi;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_material_check);
        applyPhoneInsets();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);

        studentId = getIntent().getIntExtra("student_id", 0);
        classId = getIntent().getIntExtra("classId", 0);
        academicYear = getIntent().getIntExtra("academic_year",
                preferences.getInt("soma_academic", 0));

        profileImage = findViewById(R.id.profile_image);
        txtName = findViewById(R.id.txtstname);
        txtClass = findViewById(R.id.txtstclass);
        txtRegno = findViewById(R.id.txtstregno);
        txtLastChecked = findViewById(R.id.txt_last_checked);
        lnlMaterials = findViewById(R.id.lnl_materials);
        kpiTotal = findViewById(R.id.kpi_total);
        kpiComplete = findViewById(R.id.kpi_complete);
        kpiPartial = findViewById(R.id.kpi_partial);
        kpiMissing = findViewById(R.id.kpi_missing);
        Button btnSave = findViewById(R.id.btnsave);

        txtName.setText(cleanMeta(getIntent().getStringExtra("name")));
        txtClass.setText(cleanMeta(getIntent().getStringExtra("class")));
        txtRegno.setText(cleanMeta(getIntent().getStringExtra("regno")));
        StudentPhotoHelper.load(profileImage, getIntent().getStringExtra("photo"));

        loadMaterials();
        btnSave.setOnClickListener(v -> saveChecks());
    }

    private void applyPhoneInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);

        final View root = findViewById(R.id.root_material_check);
        final View footer = findViewById(R.id.footer_save);
        if (root == null || footer == null) return;

        final int padStart = ViewCompat.getPaddingStart(footer);
        final int padEnd = ViewCompat.getPaddingEnd(footer);
        final int padTop = footer.getPaddingTop();
        final int padBottom = footer.getPaddingBottom();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // adjustResize already lifts the window above the keyboard.
            // Padding the footer by the keyboard height hides the quantity field on a small phone.
            ViewCompat.setPaddingRelative(footer, padStart, padTop, padEnd, padBottom + bars.bottom);
            return insets;
        });
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    private void loadMaterials() {
        String bootstrapJson = getIntent().getStringExtra("live_materials_json");
        if (bootstrapJson != null && !bootstrapJson.trim().isEmpty()) {
            try {
                bindBootstrapMaterials(new JSONArray(bootstrapJson));
            } catch (JSONException e) {
                Log.e(TAG, "Could not parse bootstrap materials", e);
            }
        }
        if (MainActivity.isNetworkAvailable(this)) {
            loadMaterialsFromServer();
            return;
        }
        if (rows.isEmpty()) {
            loadMaterialsFromLocal();
        }
    }

    private void loadMaterialsFromLocal() {
        lnlMaterials.removeAllViews();
        rows.clear();

        Cursor c = db.rawQuery(
                "SELECT crm.material_id, crm.quantity, rm.name, rm.unit " +
                        "FROM tb_class_required_materials crm " +
                        "INNER JOIN tb_required_materials rm ON rm.id = crm.material_id " +
                        "WHERE crm.class_id=? AND crm.academic_year=? AND rm.active=1 AND crm.quantity > 0 " +
                        "ORDER BY rm.sort_order ASC, rm.name ASC",
                new String[]{String.valueOf(classId), String.valueOf(academicYear)});

        while (c.moveToNext()) {
            MaterialRow row = new MaterialRow();
            row.materialId = c.getInt(c.getColumnIndex("material_id"));
            row.quantityRequired = c.getDouble(c.getColumnIndex("quantity"));
            row.name = c.getString(c.getColumnIndex("name"));
            row.unit = c.getString(c.getColumnIndex("unit"));
            row.savedBrought = 0;
            row.existingId = null;

            Cursor existing = db.rawQuery(
                    "SELECT id, quantity_brought FROM tb_student_material_checks " +
                            "WHERE student_id=? AND material_id=? AND academic_year=? LIMIT 1",
                    new String[]{String.valueOf(studentId), String.valueOf(row.materialId), String.valueOf(academicYear)});
            if (existing.moveToFirst()) {
                row.existingId = existing.getString(existing.getColumnIndex("id"));
                row.savedBrought = existing.getDouble(existing.getColumnIndex("quantity_brought"));
            }
            existing.close();

            if (row.savedBrought < 0) row.savedBrought = 0;
            if (row.savedBrought > row.quantityRequired) {
                row.savedBrought = row.quantityRequired;
            }

            addMaterialView(row);
            rows.add(row);
        }
        c.close();

        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_required_materials);
            empty.setPadding(8, 16, 8, 16);
            empty.setTextColor(getResources().getColor(R.color.textMuted));
            lnlMaterials.addView(empty);
        }
        updateLiveKpi();
    }

    private void loadMaterialsFromServer() {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        String url = MainActivity.SERVER + "get_material_student_context"
                + "?school_id=" + schoolId
                + "&student_id=" + studentId
                + "&year=" + academicYear;
        new HttpRequest(this).get(url, JSONObject.class, (object, statusCode, status) -> {
            if (object == null || object.optBoolean("success", false) == false) {
                Log.e(TAG, "Live material context failed: " + status);
                loadMaterialsFromLocal();
                return;
            }
            bindLiveStudentHeader(object.optJSONObject("student"));
            bindLiveMaterials(object.optJSONArray("materials"));
        });
    }

    private void bindLiveStudentHeader(JSONObject student) {
        if (student == null) return;
        String name = cleanMeta(student.optString("name", ""));
        String classLabel = cleanMeta(student.optString("class_label", ""));
        String regno = cleanMeta(student.optString("regno", ""));
        String photo = cleanMeta(student.optString("photo", ""));
        int liveClassId = student.optInt("class_id", classId);
        if (!name.isEmpty()) txtName.setText(name);
        if (!classLabel.isEmpty()) txtClass.setText(classLabel);
        if (!regno.isEmpty()) txtRegno.setText(regno);
        if (!photo.isEmpty()) StudentPhotoHelper.load(profileImage, photo);
        renderLastChecked(
                cleanMeta(student.optString("checker_name", "")),
                cleanMeta(student.optString("checker_post", "")),
                cleanMeta(student.optString("checked_at", ""))
        );
        classId = liveClassId;
    }

    private void bindLiveMaterials(JSONArray materials) {
        lnlMaterials.removeAllViews();
        rows.clear();
        if (materials == null) {
            updateLiveKpi();
            return;
        }
        for (int i = 0; i < materials.length(); i++) {
            JSONObject item = materials.optJSONObject(i);
            if (item == null) continue;
            MaterialRow row = new MaterialRow();
            row.materialId = item.optInt("material_id", 0);
            row.quantityRequired = item.optDouble("quantity_required", 0);
            row.name = item.optString("name", "");
            row.unit = item.optString("unit", "");
            row.savedBrought = item.optDouble("quantity_brought", 0);
            if (row.savedBrought < 0) row.savedBrought = 0;
            if (row.savedBrought > row.quantityRequired) {
                row.savedBrought = row.quantityRequired;
            }
            row.existingId = item.has("check_id") && item.optInt("check_id", 0) > 0
                    ? String.valueOf(item.optInt("check_id"))
                    : null;
            addMaterialView(row);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_required_materials);
            empty.setPadding(8, 16, 8, 16);
            empty.setTextColor(getResources().getColor(R.color.textMuted));
            lnlMaterials.addView(empty);
        }
        updateLiveKpi();
    }

    private void bindBootstrapMaterials(JSONArray materials) {
        lnlMaterials.removeAllViews();
        rows.clear();
        if (materials == null) {
            updateLiveKpi();
            return;
        }
        for (int i = 0; i < materials.length(); i++) {
            JSONObject item = materials.optJSONObject(i);
            if (item == null) continue;
            MaterialRow row = new MaterialRow();
            row.materialId = item.optInt("material_id", 0);
            row.quantityRequired = item.has("quantity_required")
                    ? item.optDouble("quantity_required", 0)
                    : item.optDouble("required", 0);
            row.name = item.optString("name", "");
            row.unit = item.optString("unit", "");
            row.savedBrought = item.has("quantity_brought")
                    ? item.optDouble("quantity_brought", 0)
                    : item.optDouble("brought", 0);
            if (row.savedBrought < 0) row.savedBrought = 0;
            if (row.savedBrought > row.quantityRequired) {
                row.savedBrought = row.quantityRequired;
            }
            row.existingId = item.has("check_id") && item.optInt("check_id", 0) > 0
                    ? String.valueOf(item.optInt("check_id"))
                    : null;
            addMaterialView(row);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_required_materials);
            empty.setPadding(8, 16, 8, 16);
            empty.setTextColor(getResources().getColor(R.color.textMuted));
            lnlMaterials.addView(empty);
        }
        updateLiveKpi();
    }

    private void addMaterialView(final MaterialRow row) {
        View box = LayoutInflater.from(this).inflate(R.layout.item_material_check_row, lnlMaterials, false);
        TextView txtName = box.findViewById(R.id.txt_material_name);
        TextView txtUnit = box.findViewById(R.id.txt_material_unit);
        TextView txtExpected = box.findViewById(R.id.txt_expected);
        TextView txtBrought = box.findViewById(R.id.txt_brought);
        TextView txtMissing = box.findViewById(R.id.txt_missing);
        TextView txtStatus = box.findViewById(R.id.txt_status);
        View panelNew = box.findViewById(R.id.panel_new_entry);
        EditText etAddNow = box.findViewById(R.id.et_add_now);

        txtName.setText(row.name != null ? row.name : ("Material #" + row.materialId));
        txtUnit.setText(row.unit != null && !row.unit.isEmpty() ? row.unit : "");
        txtExpected.setText(fmtQty(row.quantityRequired));

        row.txtBrought = txtBrought;
        row.txtMissing = txtMissing;
        row.txtStatus = txtStatus;
        row.panelNewEntry = panelNew;
        row.etAddNow = etAddNow;

        View.OnClickListener openEntry = v -> focusBroughtField(row);
        txtBrought.setOnClickListener(openEntry);
        panelNew.setOnClickListener(openEntry);
        etAddNow.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            v.postDelayed(() -> v.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, Math.max(v.getWidth(), 1), Math.max(v.getHeight(), 1)),
                    true), 200);
        });

        refreshRowUi(row);

        etAddNow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (row.updatingUi) return;
                refreshRowUi(row);
                updateLiveKpi();
            }
        });

        lnlMaterials.addView(box);
    }

    private void focusBroughtField(MaterialRow row) {
        if (row.panelNewEntry == null || row.etAddNow == null) return;
        if (row.panelNewEntry.getVisibility() != View.VISIBLE) return;
        row.etAddNow.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(row.etAddNow, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void refreshRowUi(MaterialRow row) {
        double remainingBefore = Math.max(0, row.quantityRequired - row.savedBrought);
        boolean needsMore = remainingBefore > 0.0001;

        if (row.panelNewEntry != null) {
            row.panelNewEntry.setVisibility(needsMore ? View.VISIBLE : View.GONE);
        }
        if (!needsMore && row.etAddNow != null) {
            row.updatingUi = true;
            row.etAddNow.setText("");
            row.updatingUi = false;
        }

        double add = needsMore ? parseAdd(row) : 0;
        if (add < 0) add = 0;
        if (add > remainingBefore) add = remainingBefore;

        double previewBrought = Math.min(row.quantityRequired, row.savedBrought + add);
        double previewMissing = Math.max(0, row.quantityRequired - previewBrought);

        row.txtBrought.setText(fmtQty(previewBrought));
        row.txtMissing.setText(fmtQty(previewMissing));

        String st = statusFor(row.quantityRequired, previewBrought);
        applyStatus(row.txtStatus, st);

        if ("complete".equals(st)) {
            row.txtBrought.setTextColor(getResources().getColor(R.color.colorSuccess));
            row.txtMissing.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else {
            row.txtBrought.setTextColor(getResources().getColor(R.color.colorAccent));
            row.txtMissing.setTextColor(getResources().getColor(R.color.colorError));
        }
    }

    private String statusFor(double required, double brought) {
        if (brought >= required && required > 0) return "complete";
        if (brought > 0) return "partial";
        return "missing";
    }

    private void applyStatus(TextView tv, String status) {
        if ("complete".equals(status)) {
            tv.setText(R.string.status_complete);
            tv.setBackgroundResource(R.drawable.smc_status_complete_bg);
        } else if ("partial".equals(status)) {
            tv.setText(R.string.status_partial);
            tv.setBackgroundResource(R.drawable.smc_status_partial_bg);
        } else {
            tv.setText(R.string.status_missing);
            tv.setBackgroundResource(R.drawable.smc_status_missing_bg);
        }
    }

    private void updateLiveKpi() {
        int complete = 0, partial = 0, missing = 0;
        for (MaterialRow row : rows) {
            double brought = previewBrought(row);
            String st = statusFor(row.quantityRequired, brought);
            if ("complete".equals(st)) complete++;
            else if ("partial".equals(st)) partial++;
            else missing++;
        }
        kpiTotal.setText(String.valueOf(rows.size()));
        kpiComplete.setText(String.valueOf(complete));
        kpiPartial.setText(String.valueOf(partial));
        kpiMissing.setText(String.valueOf(missing));
    }

    private double previewBrought(MaterialRow row) {
        double remaining = Math.max(0, row.quantityRequired - row.savedBrought);
        double add = remaining > 0.0001 ? parseAdd(row) : 0;
        if (add < 0) add = 0;
        if (add > remaining) add = remaining;
        return Math.min(row.quantityRequired, row.savedBrought + add);
    }

    private double parseAdd(MaterialRow row) {
        if (row.etAddNow == null) return 0;
        try {
            String raw = row.etAddNow.getText().toString().trim();
            if (raw.isEmpty()) return 0;
            return Math.max(0, Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmtQty(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.0001) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static String cleanMeta(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private void renderLastChecked(String checkerName, String checkerPost, String checkedAt) {
        if (txtLastChecked == null) return;
        String who = cleanMeta(checkerName);
        String post = cleanMeta(checkerPost);
        String at = cleanMeta(checkedAt);
        if (who.isEmpty() && at.isEmpty()) {
            txtLastChecked.setVisibility(View.GONE);
            return;
        }
        if (!post.isEmpty()) {
            who = who.isEmpty() ? post : (who + " · " + post);
        }
        if (!at.isEmpty()) {
            who = who.isEmpty() ? at : (who + " · " + at);
        }
        txtLastChecked.setText(getString(R.string.material_last_checked_fmt, who));
        txtLastChecked.setVisibility(View.VISIBLE);
    }

    private void saveChecks() {
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.no_required_materials, Toast.LENGTH_SHORT).show();
            return;
        }
        int staffId = preferences.getInt("soma_id", 0);
        String staffName = cleanMeta(preferences.getString("soma_name", ""));
        String staffPost = cleanMeta(preferences.getString("soma_post_title", ""));
        String checkedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        int now = (int) (System.currentTimeMillis() / 1000);

        try {
            boolean anyChange = false;
            lastSavedIds.clear();
            for (MaterialRow row : rows) {
                double remaining = Math.max(0, row.quantityRequired - row.savedBrought);
                double add = 0;
                if (remaining > 0.0001) {
                    try {
                        String raw = row.etAddNow.getText().toString().trim();
                        add = raw.isEmpty() ? 0 : Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.invalid_quantity, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (add < 0) add = 0;
                if (add > remaining) add = remaining;

                // Already complete and no new entry → keep as-is (still re-save for sync safety)
                double brought = Math.min(row.quantityRequired, row.savedBrought + add);
                if (add > 0.0001 || row.existingId == null) {
                    anyChange = true;
                }

                String id = row.existingId != null ? row.existingId : UUID.randomUUID().toString();
                lastSavedIds.add(id);
                Cursor exists = db.rawQuery("SELECT id FROM tb_student_material_checks WHERE id=?", new String[]{id});
                boolean found = exists.moveToFirst();
                exists.close();

                if (found) {
                    db.execSQL("UPDATE tb_student_material_checks SET class_id=?, quantity_required=?, quantity_brought=?, notes=?, checked_by=?, checker_name=?, checker_post=?, checked_at=?, sync=1, updated_at=? WHERE id=?",
                            new Object[]{classId, row.quantityRequired, brought, "", staffId, staffName, staffPost, checkedAt, now, id});
                } else {
                    db.execSQL("DELETE FROM tb_student_material_checks WHERE student_id=? AND material_id=? AND academic_year=?",
                            new Object[]{studentId, row.materialId, academicYear});
                    db.execSQL("INSERT INTO tb_student_material_checks (id,server_id,student_id,class_id,material_id,academic_year,quantity_required,quantity_brought,notes,checked_by,checker_name,checker_post,checked_at,sync,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            new Object[]{id, 0, studentId, classId, row.materialId, academicYear, row.quantityRequired, brought, "", staffId, staffName, staffPost, checkedAt, 1, now});
                }
                row.existingId = id;
                row.savedBrought = brought;
            }

            if (MainActivity.isNetworkAvailable(this)) {
                uploadMaterialChecksNow(anyChange);
            } else {
                renderLastChecked(staffName, staffPost, checkedAt);
                triggerSync();
                Toast.makeText(this, "Material check saved offline. It will sync later.", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void uploadMaterialChecksNow(boolean anyChange) {
        JSONArray jsonArray = new JSONArray();
        Cursor c = db.rawQuery("select * from tb_student_material_checks where student_id=? and academic_year=? and sync=1",
                new String[]{String.valueOf(studentId), String.valueOf(academicYear)});
        while (c.moveToNext()) {
            Map<String, Object> mmp = new HashMap<>();
            mmp.put("id", c.getString(c.getColumnIndex("id")));
            mmp.put("student_id", c.getInt(c.getColumnIndex("student_id")));
            mmp.put("class_id", c.getInt(c.getColumnIndex("class_id")));
            mmp.put("material_id", c.getInt(c.getColumnIndex("material_id")));
            mmp.put("academic_year", c.getInt(c.getColumnIndex("academic_year")));
            mmp.put("quantity_required", c.getDouble(c.getColumnIndex("quantity_required")));
            mmp.put("quantity_brought", c.getDouble(c.getColumnIndex("quantity_brought")));
            String notes = c.getString(c.getColumnIndex("notes"));
            mmp.put("notes", notes == null || "null".equalsIgnoreCase(notes) ? "" : notes);
            mmp.put("checked_by", c.getInt(c.getColumnIndex("checked_by")));
            mmp.put("checker_name", cleanMeta(c.getString(c.getColumnIndex("checker_name"))));
            mmp.put("checker_post", cleanMeta(c.getString(c.getColumnIndex("checker_post"))));
            mmp.put("checked_at", c.getString(c.getColumnIndex("checked_at")));
            mmp.put("updated_at", c.getInt(c.getColumnIndex("updated_at")));
            jsonArray.put(new JSONObject(mmp));
        }
        c.close();

        Map<String, Object> map = new HashMap<>();
        map.put("school", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("records", jsonArray.toString());
        new HttpRequest(this).timeouts(20, 40).post(MainActivity.SERVER + "upload_data/material_checks", map, (object, status_code, status) -> {
            if (object != null && object.has("success")) {
                try {
                    JSONArray ids = object.optJSONArray("ids");
                    if (ids != null) {
                        for (int i = 0; i < ids.length(); i++) {
                            db.execSQL("UPDATE tb_student_material_checks SET sync=0 WHERE id=?", new Object[]{ids.getString(i)});
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Could not parse material upload response", e);
                }
                Toast.makeText(this, anyChange ? "Material check saved to live server." : "Material check already up to date.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            String serverError = object == null ? "" : object.optString("error", "");
            Log.e(TAG, "Live material save failed: " + status + " " + serverError);
            triggerSync();
            Toast.makeText(this, "Live save failed. Stored offline for later sync.", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void triggerSync() {
        Intent syncIntent = new Intent("sync.qonics.com.somanet");
        syncIntent.setPackage(getPackageName());
        sendBroadcast(syncIntent);
    }
}
