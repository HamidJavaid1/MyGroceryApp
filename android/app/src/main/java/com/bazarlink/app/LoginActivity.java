package com.bazarlink.app;

import android.content.Intent;
import android.Manifest;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiConfig;
import com.bazarlink.shared.api.TokenStore;
import com.bazarlink.shared.models.AuthResponse;
import com.bazarlink.shared.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    static final String EXTRA_SELECTED_ROLE = "selected_role";
    static final String EXTRA_PREFILL_USERNAME = "prefill_username";
    static final String EXTRA_DISPLAY_NAME = "display_name";

    private static final String ROLE_CUSTOMER = "customer";
    private static final String ROLE_SHOPKEEPER = "shopkeeper";
    private static final String ROLE_WHOLESALER = "wholesaler";
    private static final String DEFAULT_BASE_URL = ApiConfig.BASE_URL;
    private static final String DEMO_PASSWORD = "DemoPass123!";

    private TokenStore tokenStore;
    private ApiClient apiClient;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private String selectedRole = ROLE_CUSTOMER;
    private String pendingAuthenticatedRole = ROLE_CUSTOMER;
    private String pendingDisplayName = "";
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView statusText;
    private MaterialButton loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tokenStore = new TokenStore(this);
        selectedRole = selectedRoleFromIntent();
        apiClient = new ApiClient(this, DEFAULT_BASE_URL);
        locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> launchMainAfterLogin(pendingAuthenticatedRole, pendingDisplayName)
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(18));
        root.setBackground(createBackground());

        TextView chip = new TextView(this);
        chip.setText("Secure Access");
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable chipBackground = new GradientDrawable();
        chipBackground.setCornerRadius(dp(24));
        chipBackground.setColor(0x22FFFFFF);
        chipBackground.setStroke(dp(1), 0x44FFFFFF);
        chip.setBackground(chipBackground);
        root.addView(chip);

        TextView title = new TextView(this);
        title.setText("Welcome, " + roleLabel(selectedRole));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 29);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(20);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sign in to unlock your personalized " + roleLabel(selectedRole) + " dashboard and tools.");
        subtitle.setTextColor(0xFFCFD8E3);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(10);
        root.addView(subtitle, subtitleParams);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(22));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(6));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        cardParams.topMargin = dp(22);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView roleBadge = new TextView(this);
        roleBadge.setText(roleLabel(selectedRole));
        roleBadge.setTextColor(0xFF0F172A);
        roleBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        roleBadge.setTypeface(roleBadge.getTypeface(), android.graphics.Typeface.BOLD);

        TextView usernameLabel = new TextView(this);
        usernameLabel.setText("Username");
        usernameLabel.setTextColor(0xFF111827);
        usernameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        usernameLabel.setPadding(0, dp(14), 0, dp(6));

        usernameInput = new EditText(this);
        usernameInput.setHint("name@example.com");
        usernameInput.setSingleLine();
        usernameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(usernameInput);
        usernameInput.setText(getIntent().getStringExtra(EXTRA_PREFILL_USERNAME) != null
                ? getIntent().getStringExtra(EXTRA_PREFILL_USERNAME)
                : demoUsernameForRole(selectedRole));

        TextView passwordLabel = new TextView(this);
        passwordLabel.setText("Password");
        passwordLabel.setTextColor(0xFF111827);
        passwordLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        passwordLabel.setPadding(0, dp(12), 0, dp(6));

        passwordInput = new EditText(this);
        passwordInput.setHint("Enter your password");
        passwordInput.setText(DEMO_PASSWORD);
        passwordInput.setSingleLine();
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(passwordInput);

        TextView forgot = new TextView(this);
        forgot.setText("Forgot Password?");
        forgot.setTextColor(0xFF2563EB);
        forgot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        forgot.setGravity(Gravity.END);
        LinearLayout.LayoutParams forgotParams = new LinearLayout.LayoutParams(-1, -2);
        forgotParams.topMargin = dp(8);
        forgot.setLayoutParams(forgotParams);

        loginButton = new MaterialButton(this);
        loginButton.setText("Login as " + roleLabel(selectedRole));
        loginButton.setAllCaps(false);
        loginButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        loginButton.setCornerRadius(dp(16));
        loginButton.setMinHeight(dp(52));
        loginButton.setBackgroundColor(0xFF1967F2);
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, -2);
        loginParams.topMargin = dp(18);
        loginButton.setOnClickListener(v -> login());

        TextView divider = new TextView(this);
        divider.setText("or continue with");
        divider.setGravity(Gravity.CENTER);
        divider.setTextColor(0xFF6B7280);
        divider.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, -2);
        dividerParams.topMargin = dp(14);

        MaterialButton signupButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        signupButton.setText("Create an account");
        signupButton.setAllCaps(false);
        signupButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        signupButton.setCornerRadius(dp(16));
        signupButton.setMinHeight(dp(50));
        signupButton.setOnClickListener(v -> openSignup());
        LinearLayout.LayoutParams signupParams = new LinearLayout.LayoutParams(-1, -2);
        signupParams.topMargin = dp(12);

        statusText = new TextView(this);
        statusText.setText("Use the seeded demo account or register a new one.");
        statusText.setTextColor(0xFF2563EB);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(12);

        form.addView(roleBadge);
        form.addView(usernameLabel);
        form.addView(usernameInput);
        form.addView(passwordLabel);
        form.addView(passwordInput);
        form.addView(forgot);
        form.addView(loginButton, loginParams);
        form.addView(divider, dividerParams);
        form.addView(signupButton, signupParams);
        form.addView(statusText, statusParams);
        card.addView(form);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(card);
        root.addView(scrollView, cardParams);

        setContentView(root);
    }

    private void login() {
        Map<String, String> body = new HashMap<>();
        body.put("username", usernameInput.getText().toString().trim());
        body.put("password", passwordInput.getText().toString());
        loginButton.setEnabled(false);
        statusText.setText("Signing in...");
        apiClient.api().login(body).enqueue(new retrofit2.Callback<AuthResponse>() {
            @Override
            public void onResponse(retrofit2.Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                loginButton.setEnabled(true);
                AuthResponse auth = response.body();
                if (!response.isSuccessful() || auth == null) {
                    statusText.setText("Sign in failed. Check your username and password.");
                    Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                    return;
                }
                tokenStore.save(auth.access, auth.refresh);
                String role = auth.user != null && auth.user.role != null ? auth.user.role : selectedRole;
                String displayName = displayName(auth.user);
                pendingAuthenticatedRole = role;
                pendingDisplayName = displayName;
                requestLocationPermissionThenContinue(displayName);
            }

            @Override
            public void onFailure(retrofit2.Call<AuthResponse> call, Throwable t) {
                loginButton.setEnabled(true);
                statusText.setText("Sign in failed: " + (t.getMessage() == null ? "network error" : t.getMessage()));
                Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestLocationPermissionThenContinue(String displayName) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchMainAfterLogin(pendingAuthenticatedRole, displayName);
            return;
        }
        statusText.setText("Requesting location permission...");
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void launchMainAfterLogin(String role, String displayName) {
        startActivity(new Intent(LoginActivity.this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_HOME_ONLY, true)
                .putExtra(EXTRA_SELECTED_ROLE, role)
                .putExtra(EXTRA_DISPLAY_NAME, displayName));
        finish();
    }

    private String displayName(User user) {
        return user == null || user.username == null || user.username.trim().isEmpty() ? roleLabel(selectedRole) : user.username.trim();
    }

    private void openSignup() {
        startActivity(new Intent(this, SignUpActivity.class).putExtra(EXTRA_SELECTED_ROLE, selectedRole));
    }

    private String selectedRoleFromIntent() {
        String role = getIntent().getStringExtra(EXTRA_SELECTED_ROLE);
        if (ROLE_SHOPKEEPER.equals(role) || ROLE_WHOLESALER.equals(role)) {
            return role;
        }
        return ROLE_CUSTOMER;
    }

    private String roleLabel(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return "Shopkeeper";
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return "Wholesaler";
        }
        return "Customer";
    }

    private String demoUsernameForRole(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return "demo_shopkeeper";
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return "demo_wholesaler";
        }
        return "demo_customer";
    }

    private GradientDrawable createBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xFF172554, 0xFF0F3C8A, 0xFF082A63 }
        );
    }

    private void styleInput(EditText input) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF8FAFC);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFFE2E8F0);
        input.setBackground(background);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setMinHeight(dp(50));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
