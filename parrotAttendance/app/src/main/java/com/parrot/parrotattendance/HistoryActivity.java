package com.parrot.parrotattendance;

import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.parrot.parrotattendance.api.ApiClient;
import com.parrot.parrotattendance.api.ApiService;
import com.parrot.parrotattendance.models.DailyReportResponse;
import com.parrot.parrotattendance.models.MonthlyReportResponse;
import com.parrot.parrotattendance.models.User;
import com.parrot.parrotattendance.utils.SessionManager;

import java.util.Calendar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private TextView txtSelectedDate, txtSelectedMonth;
    private Button btnPickDate;
    private Spinner spinnerMonth;
    private ProgressBar progress;
    private LinearLayout resultsContainer;

    private ApiService api;
    private User user;

    private final String[] monthNames = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        user = SessionManager.getUser(this);
        if (user == null) {
            showAlert("Error", "Session expired. Login again.");
            finish();
            return;
        }

        api = ApiClient.getClient().create(ApiService.class);

        bindUI();
        setupMonthSpinner();
        setupListeners();
    }

    private void bindUI() {
        txtSelectedDate = findViewById(R.id.txtSelectedDate);
        txtSelectedMonth = findViewById(R.id.txtSelectedMonth);
        btnPickDate = findViewById(R.id.btnPickDate);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        resultsContainer = findViewById(R.id.resultsContainer);
        progress = findViewById(R.id.progressHistory);
    }

    private void setupListeners() {
        btnPickDate.setOnClickListener(v -> pickDailyDate());
    }

    // =========================================================================
    // DATE PICKER → Auto load daily report
    // =========================================================================
    private void pickDailyDate() {
        Calendar c = Calendar.getInstance();

        new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    String date = String.format("%d-%02d-%02d", year, month + 1, day);
                    txtSelectedDate.setText(date);
                    loadDailyReport(date);
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    // =========================================================================
    // MONTH SPINNER → Auto load monthly report
    // =========================================================================
    private void setupMonthSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, monthNames
        );
        spinnerMonth.setAdapter(adapter);

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

                String selectedName = monthNames[pos];
                txtSelectedMonth.setText(selectedName);

                // Convert month to YYYY-MM
                Calendar c = Calendar.getInstance();
                String monthNum = String.format("%02d", pos + 1);
                String value = c.get(Calendar.YEAR) + "-" + monthNum;

                loadMonthlyReport(value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // =========================================================================
    // DAILY REPORT
    // =========================================================================
    private void loadDailyReport(String date) {
        progress.setVisibility(View.VISIBLE);
        resultsContainer.removeAllViews();

        api.getDailyReport(user.id, date).enqueue(new Callback<DailyReportResponse>() {
            @Override
            public void onResponse(Call<DailyReportResponse> call, Response<DailyReportResponse> response) {
                progress.setVisibility(View.GONE);

                if (!response.isSuccessful()) {
                    showAlert("Server Error", "HTTP " + response.code());
                    return;
                }

                DailyReportResponse res = response.body();
                if (res == null || !res.isSuccess()) {
                    showAlert("No Data", res != null ? res.message : "Unknown error.");
                    return;
                }

                showDailyCard(res, date);
            }

            @Override
            public void onFailure(Call<DailyReportResponse> call, Throwable t) {
                progress.setVisibility(View.GONE);
                showAlert("Network Error", t.getMessage());
            }
        });
    }

    private void showDailyCard(DailyReportResponse res, String date) {
        String text =
                "📅 Date: " + date + "\n\n" +
                        "➡ Check-In: " + res.check_in_time + "\n" +
                        "➡ Check-Out: " + res.check_out_time + "\n" +
                        "⏱ Worked: " + res.worked_minutes + " minutes\n" +
                        "💰 Salary: " + res.salary + " RWF";

        TextView card = formatCard(text);
        resultsContainer.addView(card);
    }

    // =========================================================================
    // MONTHLY REPORT
    // =========================================================================
    private void loadMonthlyReport(String month) {
        progress.setVisibility(View.VISIBLE);
        resultsContainer.removeAllViews();

        api.getMonthlyReport(user.id, month).enqueue(new Callback<MonthlyReportResponse>() {
            @Override
            public void onResponse(Call<MonthlyReportResponse> call, Response<MonthlyReportResponse> response) {
                progress.setVisibility(View.GONE);

                if (!response.isSuccessful()) {
                    showAlert("Server Error", "HTTP " + response.code());
                    return;
                }

                MonthlyReportResponse res = response.body();
                if (res == null || !res.isSuccess()) {
                    showAlert("Error", res != null ? res.message : "Unknown error");
                    return;
                }

                showMonthlyCard(res, month);
            }

            @Override
            public void onFailure(Call<MonthlyReportResponse> call, Throwable t) {
                progress.setVisibility(View.GONE);
                showAlert("Network Error", t.getMessage());
            }
        });
    }

    private void showMonthlyCard(MonthlyReportResponse res, String month) {
        String text =
                "📆 Month: " + month + "\n\n" +
                        "✔ Days Worked: " + res.total_days + "\n" +
                        "⏱ Hours Worked: " + res.total_hours + "\n" +
                        "💰 Total Salary: " + res.total_salary + " RWF\n" +
                        "⚠ Late Check-ins: " + res.late_count + "\n" +
                        "❌ Missed Days: " + res.missed_days;

        TextView card = formatCard(text);
        resultsContainer.addView(card);
    }

    // =========================================================================
    // Helper – beautiful card design
    // =========================================================================
    private TextView formatCard(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(30, 30, 30, 30);
        tv.setTextSize(16);
        tv.setBackground(getDrawable(R.drawable.rounded_card));
        tv.setElevation(8f);
        return tv;
    }

    // =========================================================================
    // Alert Dialog
    // =========================================================================
    private void showAlert(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
}
