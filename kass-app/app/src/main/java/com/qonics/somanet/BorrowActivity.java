package com.qonics.somanet;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.snackbar.Snackbar;
import com.qonics.model.Dual_data;
import com.qonics.utils.Callback;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class BorrowActivity extends AppCompatActivity {
    private static final String TAG = StudentActivity.class.getSimpleName();

    TextView txtcountdown, txtstname, txtstclass, txtstregno;
    ProgressBar prb;
    CircleImageView profile_image;
    String server = MainActivity.SERVER;
    HttpRequest req;
    SharedPreferences preferences;
    String card = "";
    Button btnsave;
    EditText borrow_date, return_date;
    TextView txttitle, txtauthor;
    SearchView srchbook;
    private int mYear, mMonth, mDay;
    ArrayList<Dual_data> bookList = new ArrayList<>();
    ArrayAdapter<Dual_data> adapter;
    int book_id = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_borrow);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        adapter = new BookSearchAdapter();
        populateView();
    }

    public void populateView() {
        srchbook = findViewById(R.id.srchbook);
        txtcountdown = findViewById(R.id.txtcountdown);
        profile_image = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);
        borrow_date = findViewById(R.id.bw_date_from);
        return_date = findViewById(R.id.bw_date_to);
        txttitle = findViewById(R.id.txttitle);
        txtauthor = findViewById(R.id.txtauthor);
        prb = findViewById(R.id.prb);
        prb.setVisibility(View.GONE);

        btnsave = findViewById(R.id.btnSave);

        card = getIntent().getStringExtra("card");
        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));
        txtstregno.setText("Reg Number: " + getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText("Class: " + getIntent().getStringExtra("class"));

        // ✅ FIX: use androidx.appcompat.R.id.search_src_text
        SearchView.SearchAutoComplete autoComplete =
                srchbook.findViewById(androidx.appcompat.R.id.search_src_text);

        autoComplete.setAdapter(adapter);
//        autoComplete.setThreshold(3);
        autoComplete.setOnItemClickListener((adapterView, view, i, l) -> {
            Dual_data book = bookList.get(i);
            book_id = book.getId();
            txtauthor.setText(getString(R.string.lbl_author) + book.getText2());
            txttitle.setText(getString(R.string.lbl_title) + book.getText1());
            srchbook.setQuery(book.getText1(), false);
        });

        srchbook.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() > 2) {
                    searchbook(newText);
                }
                return false;
            }
        });

        borrow_date.setOnFocusChangeListener((view, b) -> {
            if (b) {
                final Calendar c = Calendar.getInstance();
                mYear = c.get(Calendar.YEAR);
                mMonth = c.get(Calendar.MONTH);
                mDay = c.get(Calendar.DAY_OF_MONTH); // ✅ fixed missing parenthesis

                DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                        (DatePicker view1, int year, int monthOfYear, int dayOfMonth) ->
                                borrow_date.setText(dayOfMonth + "-" + (monthOfYear + 1) + "-" + year),
                        mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });

        return_date.setOnFocusChangeListener((view, b) -> {
            if (b) {
                final Calendar c = Calendar.getInstance();
                mYear = c.get(Calendar.YEAR);
                mMonth = c.get(Calendar.MONTH);
                mDay = c.get(Calendar.DAY_OF_MONTH); // ✅ fixed missing parenthesis

                DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                        (DatePicker view12, int year, int monthOfYear, int dayOfMonth) ->
                                return_date.setText(dayOfMonth + "-" + (monthOfYear + 1) + "-" + year),
                        mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });

        btnsave.setOnClickListener(view -> {
            if (book_id == 0) {
                Toast.makeText(BorrowActivity.this, getString(R.string.choose_book_lend), Toast.LENGTH_LONG).show();
            } else if (borrow_date.getText().toString().trim().length() < 3) {
                borrow_date.setError(getString(R.string.choose_borrow_date));
                borrow_date.requestFocus();
            } else if (return_date.getText().toString().trim().length() < 3) {
                return_date.setError(getString(R.string.choose_return_date));
                return_date.requestFocus();
            } else {
                String bdate = borrow_date.getText().toString().trim();
                String rdate = return_date.getText().toString().trim();
                save(book_id, bdate, rdate);
            }
        });
    }

    private void searchbook(String query) {
        req = new HttpRequest(this);
        String url = server + "get_book/" + preferences.getInt(MainActivity.SK_ID, 0) + "/" + query;
        Log.e("URL", url);
        bookList.clear();
        adapter.notifyDataSetChanged();
        req.progress(prb).hide(btnsave).get(url, JSONObject.class, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    Toast.makeText(BorrowActivity.this, getString(R.string.lbl_error) + status, Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Toast.makeText(BorrowActivity.this, getString(R.string.lbl_error) + object.getString("error"), Toast.LENGTH_LONG).show();
                        } else if (object.has("books")) {
                            JSONArray books = object.getJSONArray("books");
                            for (int a = 0; a < books.length(); a++) {
                                JSONObject book = books.getJSONObject(a);
                                bookList.add(new Dual_data(book.getInt("id"), book.getString("title"), book.getString("author")));
                            }
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(BorrowActivity.this, getString(R.string.lbl_error) + object.getString("message"), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void save(int book, String bdate, String rdate) {
        Map<String, Object> map = new HashMap<>();
        map.put("borrow_date", bdate);
        map.put("return_due_date", rdate);
        map.put("book_id", book);
        map.put("student_id", getIntent().getIntExtra("student_id", 0));
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("created_by", preferences.getInt("soma_id", 0));
        req = new HttpRequest(this);
        req.progress(prb).hide(btnsave).post(server + "/save_borrow_book", map, new Callback() {
            @Override
            public void HttpCallback(JSONObject object, int status_code, String status) {
                if (object == null) {
                    Snackbar.make(btnsave, getString(R.string.lbl_error) + status, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        if (object.has("error")) {
                            Toast.makeText(BorrowActivity.this, getString(R.string.oops) + object.getString("error"), Toast.LENGTH_LONG).show();
                            Snackbar.make(btnsave, getString(R.string.lbl_error) + object.getString("error"), Snackbar.LENGTH_LONG).show();
                        } else if (object.has("success")) {
                            Toast.makeText(BorrowActivity.this, getString(R.string.book_borrowed), Toast.LENGTH_LONG).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(BorrowActivity.this, getString(R.string.oops) + object.getString("message"), Toast.LENGTH_LONG).show();
                            Snackbar.make(btnsave, getString(R.string.lbl_error) + object.getString("message"), Snackbar.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class BookSearchAdapter extends ArrayAdapter<Dual_data> {
        public BookSearchAdapter() {
            super(BorrowActivity.this, R.layout.two_row, bookList);
        }

        @Override
        public int getCount() {
            return bookList.size();
        }

        @Nullable
        @Override
        public Dual_data getItem(int position) {
            return bookList.get(position);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.two_row, parent, false);
            }
            TextView txt1 = v.findViewById(R.id.txt1);
            TextView txt2 = v.findViewById(R.id.txt2);
            Dual_data data = bookList.get(position);
            txt1.setText(data.getText1());
            txt2.setText(data.getText2());
            return v;
        }
    }
}
