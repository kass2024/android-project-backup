package com.parrot.parrotattendance;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import com.parrot.parrotattendance.api.ApiClient;
import com.parrot.parrotattendance.api.ApiService;
import com.parrot.parrotattendance.models.LoginResponse;
import com.parrot.parrotattendance.models.User;
import com.parrot.parrotattendance.models.Office;
import com.parrot.parrotattendance.utils.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText inputUsername, inputPassword;
    private ImageView btnTogglePassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private ApiService api;

    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===========================
        // AUTO-LOGIN IF ALREADY SIGNED IN
        // ===========================
        User savedUser = SessionManager.getUser(LoginActivity.this);
        if (savedUser != null) {
            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Bind UI
        bindViews();

        // API service
        api = ApiClient.getClient().create(ApiService.class);

        // Listeners
        btnLogin.setOnClickListener(v -> validateLogin());
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
    }

    private void bindViews() {
        inputUsername = findViewById(R.id.inputUsername);
        inputPassword = findViewById(R.id.inputPassword);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressLogin);
    }

    // ===========================
    // PASSWORD SHOW/HIDE TOGGLE
    // ===========================
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            // Hide password
            inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            btnTogglePassword.setImageResource(R.drawable.ic_eye_closed);
        } else {
            // Show password
            inputPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            btnTogglePassword.setImageResource(R.drawable.ic_eye_open);
        }
        inputPassword.setSelection(inputPassword.getText().length());
        isPasswordVisible = !isPasswordVisible;
    }

    // ===========================
    // FORM VALIDATION
    // ===========================
    private void validateLogin() {
        String username = inputUsername.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (username.isEmpty()) {
            inputUsername.setError("Enter username");
            inputUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            inputPassword.setError("Enter password");
            inputPassword.requestFocus();
            return;
        }

        startLoginProcess(username, password);
    }

    // ===========================
    // LOGIN API CALL
    // ===========================
    private void startLoginProcess(String username, String password) {
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        api.login(username, password).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {

                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);

                if (!response.isSuccessful()) {
                    showToast("Server error: " + response.code());
                    return;
                }

                LoginResponse res = response.body();

                if (res == null) {
                    showToast("Unexpected server response");
                    return;
                }

                if (!"success".equalsIgnoreCase(res.status)) {
                    showToast("Invalid username or password");
                    return;
                }

                // Store user and office
                User user = res.user;
                Office office = res.user.office;

                SessionManager.save(LoginActivity.this, user, office);

                // Navigate to dashboard
                Intent i = new Intent(LoginActivity.this, DashboardActivity.class);
                startActivity(i);
                finish();
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);
                showToast("Network error: " + t.getMessage());
            }
        });
    }

    // ===========================
    // HELPER: Show Toast
    // ===========================
    private void showToast(String msg) {
        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
}
