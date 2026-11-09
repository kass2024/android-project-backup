package com.qonics.somanet;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.utils.HttpRequest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DisciplineRecordActivity extends AppCompatActivity {
    private SQLiteDatabase db;
    private SharedPreferences preferences;
    private LinearLayout rows;
    private TextView empty;
    private boolean completedOnly;
    private int loadGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discipline_record);
        completedOnly = "completed".equals(getIntent().getStringExtra("mode"));
        TextView title = findViewById(R.id.txt_title);
        title.setText(completedOnly ? R.string.completed_discipline : R.string.discipline_record);
        rows = findViewById(R.id.lnl_rows);
        empty = findViewById(R.id.txt_empty);
        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        load();
    }

    private void load() {
        final int gen = ++loadGen;
        rows.removeAllViews();
        empty.setText(R.string.disc_loading);
        empty.setVisibility(View.VISIBLE);
        if (!MainActivity.isNetworkAvailable(this)) {
            paint(readLocal(true));
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("marks_only", completedOnly ? "1" : "0");
        new HttpRequest(this).post(MainActivity.SERVER + "discipline_records", map, (object, code, status) -> {
            if (gen != loadGen) return;
            ArrayList<Item> items = new ArrayList<>();
            if (object != null && object.optJSONArray("records") != null) {
                JSONArray arr = object.optJSONArray("records");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.optJSONObject(i);
                    if (row == null) continue;
                    Item item = new Item();
                    item.serverId = row.optInt("id");
                    item.studentId = row.optInt("student_id");
                    item.name = row.optString("name", "");
                    item.regno = row.optString("regno", "");
                    item.classe = row.optString("class", "");
                    item.comment = row.optString("comment", "");
                    item.marks = row.optInt("marks");
                    item.voided = row.optInt("voided") == 1;
                    item.reason = row.optString("reason", "");
                    item.deletedBy = row.optString("deleted_by", "");
                    items.add(item);
                }
            }
            if (object == null || object.optJSONArray("records") == null) {
                paint(readLocal(true));
                return;
            }
            ArrayList<Item> pending = readLocal(false);
            for (int i = pending.size() - 1; i >= 0; i--) {
                items.add(0, pending.get(i));
            }
            paint(items);
        });
    }

    private ArrayList<Item> readLocal(boolean includeSynced) {
        ArrayList<Item> items = new ArrayList<>();
        String where = includeSynced
                ? "1=1 "
                : "(IFNULL(d.sync,1)=1 OR (IFNULL(d.voided,0)=1 AND IFNULL(d.server_id,0)=0)) ";
        String sql = "SELECT d.id, IFNULL(d.comment,''), IFNULL(d.marks,0), IFNULL(d.voided,0), IFNULL(d.sync,1), " +
                "IFNULL(d.void_reason,''), IFNULL(d.voided_name,''), IFNULL(s.name,''), IFNULL(s.regno,''), " +
                "IFNULL(s.classe,''), IFNULL(d.student_id,0) " +
                "FROM tb_discipline d LEFT JOIN tb_student s ON s.id = d.student_id " +
                "WHERE " + where;
        if (completedOnly) sql += "AND IFNULL(d.type,1)='1' ";
        sql += "ORDER BY d.id DESC";
        Cursor c = db.rawQuery(sql, null);
        while (c.moveToNext()) {
            Item item = new Item();
            item.localId = c.getInt(0);
            item.comment = c.getString(1);
            item.marks = c.getInt(2);
            item.voided = c.getInt(3) == 1;
            item.pending = c.getInt(4) == 1 && !item.voided;
            item.reason = c.getString(5);
            item.deletedBy = c.getString(6);
            item.name = c.getString(7);
            item.regno = c.getString(8);
            item.classe = c.getString(9);
            item.studentId = c.getInt(10);
            items.add(item);
        }
        c.close();
        return items;
    }

    private void paint(ArrayList<Item> items) {
        rows.removeAllViews();
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        int red = getResources().getColor(R.color.colorError);
        for (Item item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            if (item.voided) {
                card.setBackgroundColor(0xFFFFEBEE);
            } else {
                card.setBackgroundResource(R.drawable.menu_tile_bg);
            }
            card.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = pad / 2;
            card.setLayoutParams(lp);

            TextView who = new TextView(this);
            who.setTypeface(Typeface.DEFAULT_BOLD);
            who.setTextColor(item.voided ? red : getResources().getColor(R.color.textPrimary));
            String title = (item.name != null && item.name.length() > 0 ? item.name : "Student")
                    + (item.regno != null && item.regno.length() > 0 ? " · " + item.regno : "")
                    + (item.classe != null && item.classe.length() > 0 ? " · " + item.classe : "");
            who.setText(title);
            if (item.voided) who.setPaintFlags(who.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            TextView body = new TextView(this);
            body.setTextColor(item.voided ? red : getResources().getColor(R.color.textMuted));
            String status = item.voided ? "" : (item.pending ? "\n" + getString(R.string.disc_waiting) : "\n" + getString(R.string.disc_synced));
            body.setText((item.comment == null ? "" : item.comment) + "\n" + getString(R.string.disc_marks_line, item.marks) + status);
            if (item.voided) body.setPaintFlags(body.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

            card.addView(who);
            card.addView(body);
            if (item.voided) {
                TextView note = new TextView(this);
                note.setTextColor(red);
                note.setPadding(0, pad / 3, 0, 0);
                String by = item.deletedBy == null || item.deletedBy.length() == 0 ? getString(R.string.disc_staff) : item.deletedBy;
                String why = item.reason == null ? "" : item.reason;
                note.setText(getString(R.string.disc_deleted_line, by, why) + "\n" + getString(R.string.disc_marks_back, item.marks));
                card.addView(note);
            } else {
                TextView remove = new TextView(this);
                remove.setText(R.string.disc_delete);
                remove.setTextColor(red);
                remove.setPadding(0, pad / 2, 0, 0);
                remove.setOnClickListener(v -> askDelete(item));
                card.addView(remove);
            }
            rows.addView(card);
        }
        empty.setText(R.string.disc_no_records);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void askDelete(Item item) {
        EditText input = new EditText(this);
        input.setHint(R.string.disc_delete_reason);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.disc_delete_title)
                .setMessage(R.string.disc_delete_message)
                .setView(input)
                .setPositiveButton(R.string.disc_delete, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = input.getText() == null ? "" : input.getText().toString().trim();
            if (reason.length() < 3) {
                Toast.makeText(this, R.string.disc_reason_required, Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            doDelete(item, reason);
        }));
        dialog.show();
    }

    private void doDelete(Item item, String reason) {
        String staff = preferences.getString("soma_name", "");
        if (staff == null || staff.trim().length() == 0) staff = getString(R.string.disc_staff);
        int operator = preferences.getInt("soma_id", 0);
        if (item.serverId < 1) {
            db.execSQL("UPDATE tb_discipline SET voided=1, sync=0, void_reason=?, voided_name=? WHERE id=?",
                    new Object[]{reason, staff, item.localId});
            Toast.makeText(this, getString(R.string.disc_marks_back, item.marks), Toast.LENGTH_LONG).show();
            load();
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("id", item.serverId);
        map.put("reason", reason);
        map.put("operator", operator);
        map.put("deleted_by", staff);
        final String staffName = staff;
        new HttpRequest(this).post(MainActivity.SERVER + "void_discipline", map, (object, code, status) -> {
            if (object == null || object.has("error") || !object.has("success")) {
                String msg = object != null ? object.optString("error", getString(R.string.disc_delete_failed)) : getString(R.string.disc_delete_failed);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }
            try {
                db.execSQL("UPDATE tb_discipline SET voided=1, void_reason=?, voided_name=? " +
                                "WHERE IFNULL(voided,0)=0 AND student_id=? AND IFNULL(comment,'')=? AND IFNULL(marks,0)=?",
                        new Object[]{reason, staffName, String.valueOf(item.studentId), item.comment == null ? "" : item.comment, item.marks});
            } catch (Exception ignored) {
            }
            int returned = object.optInt("marks_returned", item.marks);
            Toast.makeText(this, getString(R.string.disc_marks_back, returned), Toast.LENGTH_LONG).show();
            load();
        });
    }

    private static class Item {
        int serverId;
        int localId;
        int studentId;
        int marks;
        String name = "";
        String regno = "";
        String classe = "";
        String comment = "";
        String reason = "";
        String deletedBy = "";
        boolean voided;
        boolean pending;
    }
}
