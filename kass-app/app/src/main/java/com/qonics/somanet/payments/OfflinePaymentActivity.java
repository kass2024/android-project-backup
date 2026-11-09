package com.qonics.somanet.payments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qonics.Adapter.FeesAdapter;
import com.qonics.dbhelper.DatabaseHandler;
import com.qonics.model.Dual_data;
import com.qonics.model.FeeCart;
import com.qonics.model.FeeCartHelper;
import com.qonics.model.FeeOption;
import com.qonics.model.StudentModel;
import com.qonics.somanet.MainActivity;
import com.qonics.somanet.NFCForegroundUtil;
import com.qonics.somanet.R;
import com.qonics.utils.Callback;
import com.qonics.utils.ClassLabels;
import com.qonics.utils.HttpRequest;
import com.qonics.utils.PaymentUsbReader;
import com.qonics.utils.StudentPhotoHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import cn.pedant.SweetAlert.SweetAlertDialog;
import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Staff Fees Entry (matches web Fees Entry): year/term → balances → fee lines → cart → save.
 */
public class OfflinePaymentActivity extends AppCompatActivity {
    private static final String TAG = "OfflinePayment";

    CircleImageView profile_image;
    TextView txtstname, txtstclass, txtstregno, txtexpectedamount, txtremainingamount, txtpaidamount,
            txterror, txtAllCount, txtLinearView, txtYearLock, txtTermLock, txtPaying, txtPaidLabel, txtLeftLabel;
    LinearLayout kpiPaidCard, kpiLeftCard;
    ProgressBar prb;
    Spinner spyear, spterm, spfees, sppayment;
    LinearLayout lnlcontent, lnlAllFees, lnlshowOffList, lnlshowOnfList, lnlBack, lnlSuccess, lnlButtons, lnlForm, lnlFeeStatus;
    Button btnclose, btnSave, btnAdd, btnFinish;
    EditText edPaidAmount;

    SQLiteDatabase db;
    SharedPreferences preferences;
    String server = MainActivity.SERVER;
    String currentDate;

    ArrayList<Dual_data> years = new ArrayList<>();
    ArrayList<Dual_data> terms = new ArrayList<>();
    ArrayList<Dual_data> payment_mode = new ArrayList<>();
    ArrayList<FeeOption> feeOptions = new ArrayList<>();
    ArrayAdapter<Dual_data> yearAdapter;
    ArrayAdapter<Dual_data> termAdapter;
    ArrayAdapter<Dual_data> paymentAdapter;
    ArrayAdapter<FeeOption> feesAdapter;

    List<FeeCart> cart;
    FeesAdapter mCartAdapter;
    int remainingAmount = 0;
    int studentId;
    boolean saving;
    boolean applyingDefaults;
    FeeOption selectedFee;
    PaymentUsbReader paymentUsb;
    NfcAdapter nfcAdapter;
    NFCForegroundUtil nfcForegroundUtil;
    final HashMap<String, StudentModel> cardIndex = new HashMap<>();
    String parentPhone = "";
    String lastCard = "";
    String inFlightCard = "";
    long lastCardAt;
    int schoolCheckExpected = -1;
    int schoolCheckPaid;
    boolean schoolLinesMissing;
    AlertDialog missingCardDialog;
    static final int SKY = Color.parseColor("#0EA5E9");
    static final int GOOD = Color.parseColor("#166534");
    static final int LOW = Color.parseColor("#B91C1C");
    static final int MUTED = Color.parseColor("#334155");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_payment);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        studentId = getIntent().getIntExtra("student_id", 0);

        db = openOrCreateDatabase("somanet_attendance_app_db", Context.MODE_PRIVATE, null);
        new DatabaseHandler(this).onCreate(db);
        currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        yearAdapter = new DualAdapter(years);
        termAdapter = new DualAdapter(terms);
        paymentAdapter = new DualAdapter(payment_mode);
        feesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, feeOptions);

        cart = FeeCartHelper.getCart();
        cart.clear();

        bindViews();
        wireActions();
        loadStaticSpinners();
        loadYears();
        startCardReader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcForegroundUtil != null) {
            try {
                if (!nfcAdapter.isEnabled()) {
                    Toast.makeText(this, R.string.activate_nfc, Toast.LENGTH_LONG).show();
                }
                nfcForegroundUtil.enableScanReader();
            } catch (Exception e) {
                Log.e(TAG, "NFC enable failed", e);
            }
        }
        handleNfcIntent(getIntent());
    }

    @Override
    protected void onPause() {
        if (nfcForegroundUtil != null) {
            nfcForegroundUtil.disableForeground();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (paymentUsb != null) {
            paymentUsb.stop();
            paymentUsb = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (paymentUsb != null && PaymentUsbReader.isPhysicalKeyboard(event)) {
            paymentUsb.onKey(event);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void bindViews() {
        txtexpectedamount = findViewById(R.id.txtexpectedamount);
        txtpaidamount = findViewById(R.id.txtpaidamount);
        txtremainingamount = findViewById(R.id.txtremainamount);
        txterror = findViewById(R.id.txterror);
        txtAllCount = findViewById(R.id.countAllFees);
        txtLinearView = findViewById(R.id.txtLinearView);
        spterm = findViewById(R.id.spterm);
        spyear = findViewById(R.id.spyear);
        spfees = findViewById(R.id.spfeesId);
        sppayment = findViewById(R.id.sppaymentModeId);
        lnlcontent = findViewById(R.id.lnlcontent);
        lnlAllFees = findViewById(R.id.lnlAllFees);
        lnlshowOffList = findViewById(R.id.showOffList);
        lnlshowOnfList = findViewById(R.id.showOnfList);
        lnlBack = findViewById(R.id.lnlBack);
        lnlSuccess = findViewById(R.id.lnlSuccess);
        lnlButtons = findViewById(R.id.lnlButtons);
        lnlForm = findViewById(R.id.lnlForm);
        lnlFeeStatus = findViewById(R.id.lnlFeeStatus);
        txtYearLock = findViewById(R.id.txtYearLock);
        txtTermLock = findViewById(R.id.txtTermLock);
        txtPaying = findViewById(R.id.txtPaying);
        txtPaidLabel = findViewById(R.id.txtPaidLabel);
        txtLeftLabel = findViewById(R.id.txtLeftLabel);
        kpiPaidCard = findViewById(R.id.kpiPaidCard);
        kpiLeftCard = findViewById(R.id.kpiLeftCard);
        if (txtexpectedamount != null) {
            txtexpectedamount.setTextColor(SKY);
            txtexpectedamount.setTypeface(Typeface.DEFAULT_BOLD);
        }
        prb = findViewById(R.id.prb);
        btnclose = findViewById(R.id.btnclose);
        btnSave = findViewById(R.id.btnsave);
        btnAdd = findViewById(R.id.btnAddMore);
        btnFinish = findViewById(R.id.btnFinish);
        edPaidAmount = findViewById(R.id.feeAmount);
        profile_image = findViewById(R.id.profile_image);
        txtstname = findViewById(R.id.txtstname);
        txtstclass = findViewById(R.id.txtstclass);
        txtstregno = findViewById(R.id.txtstregno);

        spyear.setAdapter(yearAdapter);
        spterm.setAdapter(termAdapter);
        spfees.setAdapter(feesAdapter);
        sppayment.setAdapter(paymentAdapter);

        txtstregno.setText(getIntent().getStringExtra("regno"));
        txtstname.setText(getIntent().getStringExtra("name"));
        txtstclass.setText(ClassLabels.visible(getIntent().getStringExtra("class")));
        StudentPhotoHelper.load(profile_image, getIntent().getStringExtra("photo"));

        if (txterror != null) {
            txterror.setVisibility(View.GONE);
        }
        if (prb != null) {
            prb.setVisibility(View.GONE);
        }
        if (lnlSuccess != null) {
            lnlSuccess.setVisibility(View.GONE);
        }
        if (lnlshowOnfList != null) {
            lnlshowOnfList.setVisibility(View.GONE);
        }
        if (lnlshowOffList != null) {
            lnlshowOffList.setVisibility(View.VISIBLE);
        }
        if (lnlForm != null) lnlForm.setVisibility(View.GONE);
        if (lnlAllFees != null) lnlAllFees.setVisibility(View.GONE);
        parentPhone = getIntent().getStringExtra("phone");
        if (parentPhone == null) parentPhone = "";
        Button btnCall = findViewById(R.id.btnCallParent);
        Button btnSms = findViewById(R.id.btnSmsParent);
        if (btnCall != null) btnCall.setOnClickListener(v -> callParent());
        if (btnSms != null) btnSms.setOnClickListener(v -> smsParent());

        ListView listViewCatalog = findViewById(R.id.ListViewCatalog);
        mCartAdapter = new FeesAdapter(cart, getLayoutInflater(), true);
        listViewCatalog.setAdapter(mCartAdapter);
        listViewCatalog.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < cart.size()) {
                cart.remove(position);
                refreshCartUi();
            }
        });
        refreshCartUi();
    }

    private void wireActions() {
        AdapterView.OnItemSelectedListener reload = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (applyingDefaults) return;
                if (spyear.getSelectedItemPosition() > 0 && spterm.getSelectedItemPosition() > 0) {
                    loadFeesAndBalances();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spyear.setOnItemSelectedListener(reload);
        spterm.setOnItemSelectedListener(reload);

        spfees.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position < feeOptions.size()) {
                    FeeOption opt = feeOptions.get(position);
                    edPaidAmount.setText(String.valueOf(Math.max(0, opt.balance)));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnAdd.setOnClickListener(v -> addFeeToCart());
        btnSave.setOnClickListener(v -> confirmAndSave());
        btnclose.setOnClickListener(v -> finish());
        btnFinish.setOnClickListener(v -> finish());

        if (lnlAllFees != null) {
            lnlAllFees.setOnClickListener(v -> showCartList(true));
        }
        if (txtLinearView != null) {
            txtLinearView.setOnClickListener(v -> showCartList(true));
        }
        if (lnlBack != null) {
            lnlBack.setOnClickListener(v -> showCartList(false));
        }
    }

    private void showCartList(boolean showList) {
        if (lnlshowOnfList != null) {
            lnlshowOnfList.setVisibility(showList ? View.VISIBLE : View.GONE);
        }
        if (lnlshowOffList != null) {
            lnlshowOffList.setVisibility(showList ? View.GONE : View.VISIBLE);
        }
    }

    private void loadStaticSpinners() {
        terms.clear();
        terms.add(new Dual_data(0, getString(R.string.select_term)));
        terms.add(new Dual_data(1, getString(R.string.term1)));
        terms.add(new Dual_data(2, getString(R.string.term2)));
        terms.add(new Dual_data(3, getString(R.string.term3)));
        termAdapter.notifyDataSetChanged();

        payment_mode.clear();
        payment_mode.add(new Dual_data(0, getString(R.string.lbl_payment_mode)));
        payment_mode.add(new Dual_data(1, "Bank Slip"));
        payment_mode.add(new Dual_data(2, "Cash"));
        payment_mode.add(new Dual_data(3, "Cheque"));
        payment_mode.add(new Dual_data(4, "Mobile Money"));
        payment_mode.add(new Dual_data(5, "Airtel Money"));
        paymentAdapter.notifyDataSetChanged();
        sppayment.setSelection(2); // Cash default like desk payments
    }

    private void loadYears() {
        years.clear();
        years.add(new Dual_data(0, getString(R.string.select_year)));
        if (hasNetwork()) {
            HttpRequest req = new HttpRequest(this);
            String url = server + "get_years/" + preferences.getInt(MainActivity.SK_ID, 0);
            req.progress(prb).get(url, JSONObject.class, (object, status_code, status) -> {
                if (object != null && object.has("years")) {
                    try {
                        JSONArray jar = object.getJSONArray("years");
                        for (int i = 0; i < jar.length(); i++) {
                            JSONObject y = jar.getJSONObject(i);
                            years.add(new Dual_data(y.getInt("id"), y.getString("title")));
                        }
                        yearAdapter.notifyDataSetChanged();
                        selectActiveYearAndTerm();
                        return;
                    } catch (JSONException e) {
                        Log.e(TAG, "years parse", e);
                    }
                }
                loadYearsFromLocal();
            });
        } else {
            loadYearsFromLocal();
        }
    }

    private void loadYearsFromLocal() {
        Cursor c = db.rawQuery("select * from tb_academic_year ORDER BY title DESC", null);
        while (c.moveToNext()) {
            years.add(new Dual_data(c.getInt(c.getColumnIndex("id")), c.getString(c.getColumnIndex("title"))));
        }
        c.close();
        yearAdapter.notifyDataSetChanged();
        selectActiveYearAndTerm();
    }

    /** Active academic year and term already set on the web for this school. */
    private void selectActiveYearAndTerm() {
        int yearId = preferences.getInt("soma_academic", 0);
        if (yearId < 1) yearId = preferences.getInt("device_academic", 0);
        int termNo = preferences.getInt("soma_term_number", 0);
        if (termNo < 1) termNo = preferences.getInt("device_term_number", 0);
        if (termNo < 1) termNo = 1;
        applyingDefaults = true;
        int yearPos = 0;
        for (int i = 0; i < years.size(); i++) {
            if (years.get(i).getId() == yearId) {
                yearPos = i;
                break;
            }
        }
        if (yearPos == 0 && years.size() > 1) yearPos = 1;
        int termPos = 0;
        for (int i = 0; i < terms.size(); i++) {
            if (terms.get(i).getId() == termNo) {
                termPos = i;
                break;
            }
        }
        spyear.setSelection(yearPos);
        spterm.setSelection(termPos);
        spyear.setEnabled(false);
        spterm.setEnabled(false);
        if (txtYearLock != null && yearPos < years.size()) {
            txtYearLock.setText(String.valueOf(years.get(yearPos)));
        }
        if (txtTermLock != null && termPos < terms.size()) {
            txtTermLock.setText(String.valueOf(terms.get(termPos)));
        }
        applyingDefaults = false;
        loadFeesAndBalances();
    }

    private void loadFeesAndBalances() {
        if (studentId < 1) {
            return;
        }
        int yearId = years.get(spyear.getSelectedItemPosition()).getId();
        int termId = terms.get(spterm.getSelectedItemPosition()).getId();
        if (yearId < 1 || termId < 1) {
            return;
        }

        if (!hasNetwork()) {
            if (!loadCachedBalances()) {
                showError("No saved fees for this student yet. Log in once with internet so the server balances are stored on this phone.");
            }
            return;
        }

        // Summary totals (same as PaymentActivity / payment_check)
        HttpRequest checkReq = new HttpRequest(this);
        String checkUrl = server + "payment_check/" + studentId + "/" + termId + "/"
                + preferences.getInt(MainActivity.SK_ID, 0) + "/" + yearId;
        checkReq.progress(prb).get(checkUrl, JSONObject.class, (object, status_code, status) -> {
            if (object != null && object.has("success")) {
                try {
                    int expected = parseIntSafe(object.optString("extra_amount"))
                            + parseIntSafe(object.optString("skl_amount"));
                    int paid = parseIntSafe(object.optString("paidschoolfees"))
                            + parseIntSafe(object.optString("paidextra"));
                    remainingAmount = expected - paid;
                    schoolCheckExpected = parseIntSafe(object.optString("skl_amount"));
                    schoolCheckPaid = parseIntSafe(object.optString("paidschoolfees"));
                    paintTotals(expected, paid, Math.max(0, remainingAmount));
                    refreshSchoolKpi();
                    if (lnlcontent != null) {
                        lnlcontent.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "payment_check", e);
                }
            } else if (object != null && object.has("message")) {
                showError(object.optString("message"));
            }
        });

        // Line items for invoice (web get_fees)
        HttpRequest feesReq = new HttpRequest(this);
        String feesUrl = server + "get_fees?student_id=" + studentId
                + "&academic_year=" + yearId + "&term=" + termId;
        feesReq.get(feesUrl, JSONObject.class, (object, status_code, status) -> {
            feeOptions.clear();
            feeOptions.add(new FeeOption(0, 0, getString(R.string.fee_type), 0, 0, 0));
            if (object == null) {
                showError(status != null ? status : "Failed to load fees");
                feesAdapter.notifyDataSetChanged();
                showFeeBuckets(null, null);
                return;
            }
            try {
                if (object.has("error")) {
                    showError(object.getString("error"));
                    showFeeBuckets(null, null);
                } else {
                    addFeeRows(object.optJSONArray("schoolFees"), 0);
                    addFeeRows(object.optJSONArray("extraFees"), 1);
                    showFeeBuckets(object.optJSONArray("schoolFees"), object.optJSONArray("extraFees"));
                    rememberBalances(object.optJSONArray("schoolFees"), object.optJSONArray("extraFees"));
                    if (feeOptions.size() == 1 && object.has("message")) {
                        showError(object.optString("message"));
                    } else if (txterror != null) {
                        txterror.setVisibility(View.GONE);
                    }
                }
            } catch (JSONException e) {
                showError(e.getMessage());
            }
            feesAdapter.notifyDataSetChanged();
        });
    }

    private void showFeeBuckets(JSONArray schoolFees, JSONArray extraFees) {
        if (lnlFeeStatus == null) return;
        lnlFeeStatus.removeAllViews();
        selectedFee = null;
        ArrayList<FeeOption> school = new ArrayList<>();
        ArrayList<FeeOption> feeding = new ArrayList<>();
        ArrayList<FeeOption> transport = new ArrayList<>();
        ArrayList<FeeOption> other = new ArrayList<>();
        collect(schoolFees, 0, school);
        if (extraFees != null) {
            for (int i = 0; i < extraFees.length(); i++) {
                FeeOption opt = optionFrom(extraFees.optJSONObject(i), 1);
                if (opt == null) continue;
                String title = opt.title.toLowerCase(Locale.US);
                if (title.contains("feed") || title.contains("lunch") || title.contains("meal")) {
                    feeding.add(opt);
                } else if (title.contains("transport")) {
                    transport.add(opt);
                } else {
                    other.add(opt);
                }
            }
        }
        schoolLinesMissing = school.isEmpty();
        lnlFeeStatus.addView(amountCard("School fees", sum(school), "school-card"));
        lnlFeeStatus.addView(amountCard("Feeding", sum(feeding), "feeding-card"));
        lnlFeeStatus.addView(amountCard("Transport", sum(transport), "transport-card"));
        refreshSchoolKpi();
        if (!other.isEmpty()) {
            TextView head = statusLine("Other");
            head.setTextColor(MUTED);
            lnlFeeStatus.addView(head);
            for (FeeOption opt : other) {
                lnlFeeStatus.addView(amountCard(opt.title, opt, null));
            }
        }
    }

    private FeeOption sum(ArrayList<FeeOption> lines) {
        if (lines == null || lines.isEmpty()) return null;
        int expected = 0;
        int paid = 0;
        int balance = 0;
        FeeOption payable = lines.get(0);
        for (FeeOption opt : lines) {
            expected += opt.expected;
            paid += opt.paid;
            balance += opt.balance;
            if (opt.balance > payable.balance) payable = opt;
        }
        return new FeeOption(payable.feesId, payable.feesType, payable.title, expected, paid, balance);
    }

    private void collect(JSONArray arr, int type, ArrayList<FeeOption> into) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            FeeOption opt = optionFrom(arr.optJSONObject(i), type);
            if (opt != null) into.add(opt);
        }
    }

    private FeeOption optionFrom(JSONObject row, int feesType) {
        if (row == null) return null;
        int feesId = row.optInt("feesId", row.optInt("fees_id", row.optInt("id", 0)));
        if (feesId < 1 && row.optString("title", "").trim().isEmpty()) return null;
        String title = row.optString("title", "Fee");
        int expected = (int) Math.round(row.optDouble("expected", 0));
        int paid = (int) Math.round(row.optDouble("paid", 0));
        int balance = (int) Math.round(row.optDouble("balance", expected - paid));
        if (balance < 0) balance = 0;
        return new FeeOption(feesId, feesType, title, expected, paid, balance);
    }

    private View amountCard(String label, FeeOption opt, String tag) {
        boolean known = opt != null;
        int expected = known ? opt.expected : 0;
        int paid = known ? opt.paid : 0;
        int left = known ? opt.balance : 0;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (tag != null) card.setTag(tag);
        card.setBackground(pill("#FFFFFF"));
        int pad = dp(12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#0F172A"));
        card.addView(title);

        if (!known) {
            TextView miss = new TextView(this);
            miss.setTag("body");
            miss.setText("Not set for this term");
            miss.setTextColor(Color.parseColor("#64748B"));
            miss.setTextSize(14);
            card.addView(miss);
        } else {
            card.addView(figureRow(expected, paid, left));
            card.setOnClickListener(v -> chooseFee(opt));
        }
        return card;
    }

    private LinearLayout figureRow(int expected, int paid, int left) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setTag("figures");
        row.addView(figure("Expected", money(expected), SKY));
        row.addView(figure("Paid", money(paid), paidColor(expected, paid)));
        row.addView(figure("Left", money(left), leftColor(left)));
        return row;
    }

    private LinearLayout figure(String label, String value, int color) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setTextColor(color);
        TextView val = new TextView(this);
        val.setTag(label.toLowerCase(Locale.US));
        val.setText(value);
        val.setTextSize(15);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(color);
        col.addView(lab);
        col.addView(val);
        return col;
    }

    private int paidColor(int expected, int paid) {
        if (expected <= 0 && paid <= 0) return MUTED;
        if (expected > 0 && paid < expected) return LOW;
        return GOOD;
    }

    private int leftColor(int left) {
        return left > 0 ? LOW : GOOD;
    }

    private void paintTotals(int expected, int paid, int left) {
        if (txtexpectedamount != null) {
            txtexpectedamount.setText(money(expected));
            txtexpectedamount.setTextColor(SKY);
            txtexpectedamount.setTypeface(Typeface.DEFAULT_BOLD);
        }
        int paidTint = paidColor(expected, paid);
        if (txtpaidamount != null) {
            txtpaidamount.setText(money(paid));
            txtpaidamount.setTextColor(paidTint);
            txtpaidamount.setTypeface(Typeface.DEFAULT_BOLD);
        }
        if (txtPaidLabel != null) txtPaidLabel.setTextColor(paidTint);
        tintCard(kpiPaidCard, paidTint == LOW ? "#FEE2E2" : (paidTint == GOOD ? "#DCFCE7" : "#FFFFFF"));
        int leftTint = leftColor(left);
        if (txtremainingamount != null) {
            txtremainingamount.setText(money(Math.max(0, left)));
            txtremainingamount.setTextColor(leftTint);
            txtremainingamount.setTypeface(Typeface.DEFAULT_BOLD);
        }
        if (txtLeftLabel != null) txtLeftLabel.setTextColor(leftTint);
        tintCard(kpiLeftCard, left > 0 ? "#FEE2E2" : "#DCFCE7");
    }

    private void tintCard(View card, String color) {
        if (card == null) return;
        card.setBackground(pill(color));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void showMissingCard(String cardId) {
        if (isFinishing()) return;
        if (missingCardDialog != null && missingCardDialog.isShowing()) return;
        TextView msg = new TextView(this);
        msg.setText("No student found for this card\n\n" + cardId);
        msg.setTextSize(22);
        msg.setTypeface(Typeface.DEFAULT_BOLD);
        msg.setTextColor(LOW);
        msg.setGravity(Gravity.CENTER);
        int pad = dp(28);
        msg.setPadding(pad, pad, pad, pad);
        missingCardDialog = new AlertDialog.Builder(this)
                .setCancelable(true)
                .setView(msg)
                .setPositiveButton("OK", null)
                .create();
        missingCardDialog.show();
    }

    private void chooseFee(FeeOption opt) {
        if (opt == null || opt.balance <= 0) {
            Toast.makeText(this, "Nothing left to pay on this fee", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedFee = opt;
        edPaidAmount.setText(String.valueOf(opt.balance));
        if (txtPaying != null) {
            txtPaying.setText("Paying " + opt.title);
        }
    }

    private TextView statusLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(lp);
        return tv;
    }

    private GradientDrawable pill(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        return d;
    }

    private String money(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    /** School-fee total from payment_check when the fee list has no school line yet. */
    private void refreshSchoolKpi() {
        if (!schoolLinesMissing || lnlFeeStatus == null || lnlFeeStatus.getChildCount() == 0) return;
        if (schoolCheckExpected < 0) return;
        int balance = Math.max(0, schoolCheckExpected - schoolCheckPaid);
        View card = lnlFeeStatus.findViewWithTag("school-card");
        if (!(card instanceof LinearLayout)) return;
        LinearLayout box = (LinearLayout) card;
        View figures = box.findViewWithTag("figures");
        if (figures == null) {
            View body = box.findViewWithTag("body");
            if (body != null) box.removeView(body);
            box.addView(figureRow(schoolCheckExpected, schoolCheckPaid, balance));
            return;
        }
        setFigure(figures, "expected", money(schoolCheckExpected), SKY);
        setFigure(figures, "paid", money(schoolCheckPaid), paidColor(schoolCheckExpected, schoolCheckPaid));
        setFigure(figures, "left", money(balance), leftColor(balance));
    }

    private void setFigure(View figures, String tag, String value, int color) {
        View valueView = figures.findViewWithTag(tag);
        if (!(valueView instanceof TextView)) return;
        ((TextView) valueView).setText(value);
        ((TextView) valueView).setTextColor(color);
        if (valueView.getParent() instanceof LinearLayout) {
            LinearLayout col = (LinearLayout) valueView.getParent();
            if (col.getChildCount() > 0 && col.getChildAt(0) instanceof TextView) {
                ((TextView) col.getChildAt(0)).setTextColor(color);
            }
        }
    }

    private void startCardReader() {
        rebuildCardIndex();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcForegroundUtil = new NFCForegroundUtil(this);
            nfcForegroundUtil.setTagListener(this::onNfcTag);
        }
        paymentUsb = new PaymentUsbReader();
        paymentUsb.start(this, new PaymentUsbReader.Listener() {
            @Override
            public void onReader(boolean connected) {
            }

            @Override
            public void onCard(String uid) {
                runOnUiThread(() -> onPaymentCard(uid));
            }
        });
    }

    private void onNfcTag(Tag tag) {
        if (tag == null) return;
        String cardId = NFCForegroundUtil.bytesToHex(tag.getId());
        if (cardId.isEmpty()) return;
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(30);
        } catch (Exception ignored) {
        }
        onPaymentCard(cardId);
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            onNfcTag(tag);
        }
    }

    private void onPaymentCard(String uid) {
        if (uid == null) return;
        String cardId = uid.trim().toUpperCase(Locale.US);
        if (cardId.length() < 4) return;
        long now = SystemClock.elapsedRealtime();
        if (cardId.equals(inFlightCard)) return;
        if (cardId.equals(lastCard) && now - lastCardAt < 900) return;
        lastCard = cardId;
        lastCardAt = now;
        StudentModel hit = findCachedCard(cardId);
        if (hit != null) {
            showStudent(hit);
            return;
        }
        lookupCardOnline(cardId);
    }

    private void showStudent(StudentModel student) {
        if (student == null || student.getId() < 1) return;
        if (student.getId() == studentId) return;
        if (cart != null && !cart.isEmpty()) {
            Toast.makeText(this, "Save or clear the fee list, then tap the next card", Toast.LENGTH_LONG).show();
            return;
        }
        studentId = student.getId();
        if (student.getPhone() != null && student.getPhone().trim().length() > 3) {
            parentPhone = student.getPhone().trim();
        }
        txtstname.setText(student.getName());
        txtstregno.setText(student.getRegno());
        txtstclass.setText(ClassLabels.visible(student.getClasse()));
        StudentPhotoHelper.load(profile_image, student.getPhoto());
        selectedFee = null;
        if (edPaidAmount != null) edPaidAmount.setText("");
        if (txtPaying != null) txtPaying.setText("Tap a fee to pay");
        schoolCheckExpected = -1;
        if (txtexpectedamount != null) txtexpectedamount.setText("—");
        if (txtpaidamount != null) txtpaidamount.setText("—");
        if (txtremainingamount != null) txtremainingamount.setText("—");
        loadFeesAndBalances();
    }

    private StudentModel findCachedCard(String cardId) {
        for (String variant : NFCForegroundUtil.lookupVariants(cardId)) {
            StudentModel hit = cardIndex.get(variant);
            if (hit != null) return hit;
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
            String card = c.getString(7);
            if (card == null) continue;
            card = card.toUpperCase(Locale.US).replaceAll("[^A-F0-9]", "");
            StudentModel model = new StudentModel(
                    c.getInt(0), c.getString(1), c.getString(2), c.getInt(3),
                    c.getString(4), c.getString(5), c.getString(6), term, card, c.getString(8));
            for (String variant : NFCForegroundUtil.lookupVariants(card)) {
                cardIndex.put(variant, model);
            }
        }
        c.close();
    }

    private void lookupCardOnline(String cardId) {
        int schoolId = preferences.getInt(MainActivity.SK_ID, 0);
        if (!hasNetwork() || schoolId <= 0) {
            showMissingCard(cardId);
            return;
        }
        inFlightCard = cardId;
        Map<String, Object> map = new HashMap<>();
        map.put("card", cardId);
        map.put("school_id", schoolId);
        new HttpRequest(this).timeouts(2, 3).post(server + "discipline_card_scan", map, (object, status_code, status) -> runOnUiThread(() -> {
            inFlightCard = "";
            StudentModel cached = findCachedCard(cardId);
            if (cached != null) {
                showStudent(cached);
                return;
            }
            JSONObject st = object == null ? null : object.optJSONObject("student");
            if (st == null || st.optInt("id", 0) < 1) {
                showMissingCard(cardId);
                return;
            }
            String stored = NFCForegroundUtil.storageForm(cardId);
            StudentModel model = new StudentModel(
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
            for (String variant : NFCForegroundUtil.lookupVariants(cardId)) {
                cardIndex.put(variant, model);
            }
            showStudent(model);
        }));
    }

    private void addFeeRows(JSONArray arr, int feesType) throws JSONException {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.getJSONObject(i);
            int feesId = row.optInt("feesId", row.optInt("fees_id", 0));
            String title = row.optString("title", "Fee " + feesId);
            int expected = (int) Math.round(row.optDouble("expected", 0));
            int paid = (int) Math.round(row.optDouble("paid", 0));
            int balance = (int) Math.round(row.optDouble("balance", expected - paid));
            if (balance < 0) {
                balance = 0;
            }
            if (feesId > 0 && balance > 0) {
                feeOptions.add(new FeeOption(feesId, feesType, title, expected, paid, balance));
            }
        }
    }

    private void addFeeToCart() {
        int modePos = sppayment.getSelectedItemPosition();
        int termPos = spterm.getSelectedItemPosition();
        if (selectedFee == null || selectedFee.feesId < 1) {
            Toast.makeText(this, "Tap School fees, Feeding, Transport, or another fee first", Toast.LENGTH_LONG).show();
            return;
        }
        if (modePos <= 0) {
            Toast.makeText(this, "Select payment mode", Toast.LENGTH_SHORT).show();
            return;
        }
        String amountStr = edPaidAmount.getText().toString().trim();
        if (amountStr.isEmpty() || parseIntSafe(amountStr) <= 0) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
            return;
        }
        FeeOption opt = selectedFee;
        int amount = parseIntSafe(amountStr);
        if (amount > opt.balance) {
            Toast.makeText(this, "Amount exceeds remaining (" + opt.balance + ")", Toast.LENGTH_LONG).show();
            return;
        }
        for (FeeCart existing : cart) {
            if (existing.getFeeId() == opt.feesId && existing.getFeeType() == opt.feesType) {
                Toast.makeText(this, "Fee already in cart", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        String uuid = UUID.randomUUID().toString();
        cart.add(new FeeCart(
                uuid,
                studentId,
                opt.feesId,
                opt.feesType,
                opt.title,
                String.valueOf(amount),
                payment_mode.get(modePos).getId(),
                terms.get(termPos).getId(),
                preferences.getInt("soma_id", 0),
                currentDate
        ));
        refreshCartUi();
        Toast.makeText(this, "Added to invoice", Toast.LENGTH_SHORT).show();
        showCartList(true);
    }

    private void refreshCartUi() {
        if (mCartAdapter != null) {
            mCartAdapter.notifyDataSetChanged();
        }
        if (txtAllCount != null) {
            txtAllCount.setText(String.valueOf(cart.size()));
        }
    }

    private void confirmAndSave() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "Add at least one fee first", Toast.LENGTH_SHORT).show();
            showCartList(false);
            return;
        }
        if (saving) {
            return;
        }
        SweetAlertDialog dlg = new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE);
        dlg.setTitleText(getString(R.string.confirm_payment));
        dlg.setContentText(cart.size() + " fee line(s)");
        dlg.setConfirmText(getString(R.string.save));
        dlg.setCancelText(getString(R.string.cancel));
        dlg.setConfirmClickListener(sweetAlertDialog -> {
            sweetAlertDialog.dismissWithAnimation();
            savePayment();
        });
        dlg.setCancelClickListener(SweetAlertDialog::dismissWithAnimation);
        dlg.show();
    }

    private void savePayment() {
        saving = true;
        if (hasNetwork()) {
            saveOnline();
        } else {
            saveOfflineLocal();
        }
    }

    private void saveOnline() {
        try {
            JSONArray items = new JSONArray();
            for (FeeCart line : cart) {
                JSONObject o = new JSONObject();
                o.put("fees_id", line.getFeeId());
                o.put("fees_type", line.getFeeType());
                o.put("amount", line.getPaidAmount());
                o.put("payment_mode", line.getPaymentMode());
                items.put(o);
            }
            Map<String, Object> map = new HashMap<>();
            map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
            map.put("student_id", studentId);
            map.put("operator", preferences.getInt("soma_id", 0));
            map.put("items", items.toString());

            HttpRequest req = new HttpRequest(this);
            req.progress(prb).hide(btnSave).post(server + "save_fee_payment", map, new Callback() {
                @Override
                public void HttpCallback(JSONObject object, int status_code, String status) {
                    saving = false;
                    if (object == null) {
                        showError(status);
                        // Fall back to local queue
                        saveOfflineLocal();
                        return;
                    }
                    if (object.has("error")) {
                        showError(object.optString("error"));
                        return;
                    }
                    if (object.has("success")) {
                        onSaveSuccess(true);
                    } else {
                        showError(getString(R.string.server_error));
                    }
                }
            });
        } catch (JSONException e) {
            saving = false;
            showError(e.getMessage());
        }
    }

    private void saveOfflineLocal() {
        try {
            for (FeeCart line : cart) {
                db.execSQL(
                        "insert into tb_fee_records (id,student_id,fee_id,fee_type,paid_amount,payment_mode,academic_term,sync,created_by,created_at) values (?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{
                                line.getId(),
                                String.valueOf(line.getStudentId()),
                                line.getFeeId(),
                                line.getFeeType(),
                                line.getPaidAmount(),
                                String.valueOf(line.getPaymentMode()),
                                line.getTerm(),
                                1,
                                line.getCreatedBy(),
                                line.getCreatedAt()
                        }
                );
            }
            saving = false;
            onSaveSuccess(false);
        } catch (Exception e) {
            saving = false;
            showError(e.getMessage());
        }
    }

    private void onSaveSuccess(boolean online) {
        if (lnlSuccess != null) {
            lnlSuccess.setVisibility(View.VISIBLE);
        }
        if (lnlshowOffList != null) {
            lnlshowOffList.setVisibility(View.GONE);
        }
        if (lnlshowOnfList != null) {
            lnlshowOnfList.setVisibility(View.GONE);
        }
        Toast.makeText(this, online ? "Payment saved" : "Saved offline — will sync", Toast.LENGTH_LONG).show();

        if (online && remainingAmount > 0) {
            new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                    .setTitleText("Notify parent?")
                    .setContentText("Send payment reminder SMS")
                    .setConfirmText("Send SMS")
                    .setCancelText("Skip")
                    .setConfirmClickListener(d -> {
                        d.dismissWithAnimation();
                        sendPaymentNotification();
                    })
                    .setCancelClickListener(SweetAlertDialog::dismissWithAnimation)
                    .show();
        }

        cart.clear();
        refreshCartUi();
        if (online && spyear.getSelectedItemPosition() > 0 && spterm.getSelectedItemPosition() > 0) {
            loadFeesAndBalances();
        }
    }

    private void sendPaymentNotification() {
        Map<String, Object> map = new HashMap<>();
        map.put("student_id", studentId);
        map.put("school_id", preferences.getInt(MainActivity.SK_ID, 0));
        map.put("amount", remainingAmount);
        new HttpRequest(this).progress(prb).post(server + "send_payment_notification", map, (object, status_code, status) -> {
            if (object != null && object.has("success")) {
                Toast.makeText(this, "SMS sent to parent", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, object != null ? object.optString("message", status) : status, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean loadCachedBalances() {
        Cursor c = db.rawQuery(
                "SELECT expected, paid, left_amount, phone, payload FROM tb_fee_balance WHERE student_id=?",
                new String[]{String.valueOf(studentId)});
        boolean found = c.moveToFirst();
        if (!found) {
            c.close();
            return false;
        }
        int expected = c.getInt(0);
        int paid = c.getInt(1);
        int left = c.getInt(2);
        String phone = c.getString(3);
        String payload = c.getString(4);
        c.close();
        if (phone != null && phone.trim().length() > 3) parentPhone = phone.trim();
        remainingAmount = left;
        paintTotals(expected, paid, left);
        JSONArray school = new JSONArray();
        JSONArray extra = new JSONArray();
        try {
            JSONArray lines = new JSONArray(payload == null ? "[]" : payload);
            for (int i = 0; i < lines.length(); i++) {
                JSONObject line = lines.optJSONObject(i);
                if (line == null) continue;
                int type = line.optInt("feesType", line.optInt("fees_type", 1));
                String title = line.optString("title", "");
                if (type == 0 || title.toLowerCase(Locale.US).contains("school fee")) school.put(line);
                else extra.put(line);
            }
        } catch (JSONException ignored) {
        }
        showFeeBuckets(school, extra);
        if (txterror != null) txterror.setVisibility(View.GONE);
        if (lnlcontent != null) lnlcontent.setVisibility(View.VISIBLE);
        return true;
    }

    private void rememberBalances(JSONArray school, JSONArray extra) {
        try {
            JSONArray lines = new JSONArray();
            int expected = 0;
            int paid = 0;
            if (school != null) {
                for (int i = 0; i < school.length(); i++) {
                    JSONObject row = school.optJSONObject(i);
                    if (row == null) continue;
                    row.put("feesType", 0);
                    lines.put(row);
                    expected += (int) Math.round(row.optDouble("expected", 0));
                    paid += (int) Math.round(row.optDouble("paid", 0));
                }
            }
            if (extra != null) {
                for (int i = 0; i < extra.length(); i++) {
                    JSONObject row = extra.optJSONObject(i);
                    if (row == null) continue;
                    row.put("feesType", 1);
                    lines.put(row);
                    expected += (int) Math.round(row.optDouble("expected", 0));
                    paid += (int) Math.round(row.optDouble("paid", 0));
                }
            }
            int yearId = 0;
            int termId = 0;
            if (spyear.getSelectedItemPosition() > 0 && spyear.getSelectedItemPosition() < years.size()) {
                yearId = years.get(spyear.getSelectedItemPosition()).getId();
            }
            if (spterm.getSelectedItemPosition() > 0 && spterm.getSelectedItemPosition() < terms.size()) {
                termId = terms.get(spterm.getSelectedItemPosition()).getId();
            }
            db.execSQL(
                    "INSERT OR REPLACE INTO tb_fee_balance (student_id,year,term,expected,paid,left_amount,phone,payload) VALUES (?,?,?,?,?,?,?,?)",
                    new Object[]{studentId, yearId, termId, expected, paid, Math.max(0, expected - paid), parentPhone, lines.toString()});
        } catch (Exception e) {
            Log.e(TAG, "remember balances", e);
        }
    }

    private String parentNumber() {
        if (parentPhone != null && parentPhone.trim().length() > 3) return parentPhone.trim();
        Cursor c = db.rawQuery("SELECT phone FROM tb_student WHERE id=?", new String[]{String.valueOf(studentId)});
        if (c.moveToFirst() && c.getString(0) != null) parentPhone = c.getString(0).trim();
        c.close();
        if (parentPhone != null && parentPhone.trim().length() > 3) return parentPhone.trim();
        c = db.rawQuery("SELECT phone FROM tb_fee_balance WHERE student_id=?", new String[]{String.valueOf(studentId)});
        if (c.moveToFirst() && c.getString(0) != null) parentPhone = c.getString(0).trim();
        c.close();
        return parentPhone == null ? "" : parentPhone.trim();
    }

    private void callParent() {
        String phone = parentNumber();
        if (phone.length() < 4) {
            showNotice("No parent phone", "This student has no parent phone saved from the server.");
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    private void smsParent() {
        String phone = parentNumber();
        if (phone.length() < 4) {
            showNotice("No parent phone", "This student has no parent phone saved from the server.");
            return;
        }
        if (remainingAmount <= 0) {
            showNotice("Nothing to remind", "This student has no remaining balance.");
            return;
        }
        String name = txtstname == null ? "your child" : txtstname.getText().toString();
        String body = "Dear parent, " + name + " still has a remaining balance of "
                + money(remainingAmount) + ". Please pay the remaining amount. Thank you.";
        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
        sms.putExtra("sms_body", body);
        startActivity(sms);
    }

    private void showNotice(String title, String message) {
        TextView msg = new TextView(this);
        msg.setText(title + "\n\n" + message);
        msg.setTextSize(20);
        msg.setTypeface(Typeface.DEFAULT_BOLD);
        msg.setTextColor(LOW);
        msg.setGravity(Gravity.CENTER);
        int pad = dp(24);
        msg.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this).setView(msg).setPositiveButton("OK", null).show();
    }

    private void showError(String msg) {
        if (txterror != null) {
            txterror.setVisibility(View.VISIBLE);
            txterror.setText(msg);
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private static int parseIntSafe(String v) {
        try {
            if (v == null || v.trim().isEmpty()) {
                return 0;
            }
            return (int) Math.round(Double.parseDouble(v.trim().replace(",", "")));
        } catch (Exception e) {
            return 0;
        }
    }

    private class DualAdapter extends ArrayAdapter<Dual_data> {
        List<Dual_data> lists;

        DualAdapter(List<Dual_data> lists) {
            super(OfflinePaymentActivity.this, android.R.layout.simple_spinner_dropdown_item, lists);
            this.lists = lists;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            ((TextView) v.findViewById(android.R.id.text1)).setText(lists.get(position).getText1());
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent);
        }
    }
}
