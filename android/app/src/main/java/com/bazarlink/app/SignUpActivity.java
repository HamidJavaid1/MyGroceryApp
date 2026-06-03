package com.bazarlink.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiConfig;
import com.bazarlink.shared.models.AuthResponse;
import com.bazarlink.shared.models.RegisterRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;

public class SignUpActivity extends AppCompatActivity {
    private static final String ROLE_CUSTOMER = "customer";
    private static final String ROLE_SHOPKEEPER = "shopkeeper";
    private static final String ROLE_WHOLESALER = "wholesaler";
    private static final String DEFAULT_BASE_URL = ApiConfig.BASE_URL;

    private ApiClient apiClient;
    private EditText usernameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText phoneInput;
    private EditText shopNameInput;
    private TextView shopLabel;
    private TextView statusText;
    private MaterialButton signupButton;
    private String selectedRole = ROLE_CUSTOMER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selectedRole = selectedRoleFromIntent();
        apiClient = new ApiClient(this, DEFAULT_BASE_URL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(18));
        root.setBackground(createBackground());

        TextView chip = new TextView(this);
        chip.setText("Create Account");
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
        title.setText("Register a new " + roleLabel(selectedRole) + " account");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(18);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Create your backend account once and use it across the app.");
        subtitle.setTextColor(0xFFCFD8E3);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        subtitle.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(8);
        root.addView(subtitle, subtitleParams);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(22));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(6));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        cardParams.topMargin = dp(18);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView nameLabel = new TextView(this);
        nameLabel.setText("Username");
        nameLabel.setTextColor(0xFF111827);
        nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        usernameInput = new EditText(this);
        usernameInput.setHint("Choose a username");
        usernameInput.setSingleLine();
        usernameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(usernameInput);

        TextView emailLabel = new TextView(this);
        emailLabel.setText("Email Address");
        emailLabel.setTextColor(0xFF111827);
        emailLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emailLabel.setPadding(0, dp(12), 0, 0);

        emailInput = new EditText(this);
        emailInput.setHint("name@example.com");
        emailInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setSingleLine();
        emailInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(emailInput);

        TextView passwordLabel = new TextView(this);
        passwordLabel.setText("Password");
        passwordLabel.setTextColor(0xFF111827);
        passwordLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        passwordLabel.setPadding(0, dp(12), 0, 0);

        passwordInput = new EditText(this);
        passwordInput.setHint("Create a password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSingleLine();
        passwordInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(passwordInput);

        TextView phoneLabel = new TextView(this);
        phoneLabel.setText("Phone Number");
        phoneLabel.setTextColor(0xFF111827);
        phoneLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        phoneLabel.setPadding(0, dp(12), 0, 0);

        phoneInput = new EditText(this);
        phoneInput.setHint("Phone number (required)");
        phoneInput.setSingleLine();
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(phoneInput);

        shopLabel = new TextView(this);
        shopLabel.setText("Shop Name");
        shopLabel.setTextColor(0xFF111827);
        shopLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        shopLabel.setPadding(0, dp(12), 0, 0);

        shopNameInput = new EditText(this);
        shopNameInput.setHint("Required for shopkeeper/wholesaler");
        shopNameInput.setSingleLine();
        shopNameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        styleInput(shopNameInput);

        signupButton = new MaterialButton(this);
        signupButton.setText("Create Account");
        signupButton.setAllCaps(false);
        signupButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        signupButton.setCornerRadius(dp(16));
        signupButton.setMinHeight(dp(52));
        signupButton.setBackgroundColor(0xFF1967F2);
        signupButton.setOnClickListener(v -> register());
        LinearLayout.LayoutParams signupParams = new LinearLayout.LayoutParams(-1, -2);
        signupParams.topMargin = dp(18);

        TextView switchToLogin = new TextView(this);
        switchToLogin.setText("Already have an account? Sign in");
        switchToLogin.setGravity(Gravity.CENTER);
        switchToLogin.setTextColor(0xFF2563EB);
        switchToLogin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        switchToLogin.setPadding(0, dp(14), 0, 0);
        switchToLogin.setOnClickListener(v -> finish());

        statusText = new TextView(this);
        statusText.setText("Fill in your details to register with the backend.");
        statusText.setTextColor(0xFF2563EB);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(12);

        form.addView(nameLabel);
        form.addView(usernameInput);
        form.addView(emailLabel);
        form.addView(emailInput);
        form.addView(passwordLabel);
        form.addView(passwordInput);
        form.addView(phoneLabel);
        form.addView(phoneInput);
        form.addView(shopLabel);
        form.addView(shopNameInput);
        form.addView(signupButton, signupParams);
        form.addView(switchToLogin);
        form.addView(statusText, statusParams);
        card.addView(form);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(card);
        root.addView(scrollView, cardParams);

        setContentView(root);
        updateRoleSpecificFields();
    }

    private void register() {
        String phoneNumber = phoneInput.getText().toString().trim();
        if (phoneNumber.isEmpty()) {
            phoneInput.setError("Phone number is required");
            phoneInput.requestFocus();
            return;
        }
        boolean requiresShopName = ROLE_SHOPKEEPER.equals(selectedRole) || ROLE_WHOLESALER.equals(selectedRole);
        String shopName = shopNameInput.getText().toString().trim();
        if (requiresShopName && shopName.isEmpty()) {
            shopNameInput.setError("Shop name is required");
            shopNameInput.requestFocus();
            return;
        }

        RegisterRequest request = new RegisterRequest();
        request.username = usernameInput.getText().toString().trim();
        request.email = emailInput.getText().toString().trim();
        request.password = passwordInput.getText().toString();
        request.phone_number = phoneNumber;
        request.shop_name = requiresShopName ? shopName : "";
        request.role = selectedRole;
        signupButton.setEnabled(false);
        statusText.setText("Creating account...");
        apiClient.api().register(request).enqueue(new retrofit2.Callback<AuthResponse>() {
            @Override
            public void onResponse(retrofit2.Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                signupButton.setEnabled(true);
                if (!response.isSuccessful() || response.body() == null) {
                    String errorMessage = "Sign up failed. Please check the details and try again.";
                    try {
                        if (response.errorBody() != null) {
                            String backendError = response.errorBody().string().trim();
                            if (!backendError.isEmpty()) {
                                errorMessage = "Sign up failed: " + backendError;
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    statusText.setText(errorMessage);
                    Toast.makeText(SignUpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    return;
                }
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class)
                        .putExtra(LoginActivity.EXTRA_SELECTED_ROLE, selectedRole)
                        .putExtra(LoginActivity.EXTRA_PREFILL_USERNAME, request.username));
                finish();
            }

            @Override
            public void onFailure(retrofit2.Call<AuthResponse> call, Throwable t) {
                signupButton.setEnabled(true);
                String message = "Sign up failed: " + (t.getMessage() == null ? "network error" : t.getMessage());
                statusText.setText(message);
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateRoleSpecificFields() {
        boolean requiresShopName = ROLE_SHOPKEEPER.equals(selectedRole) || ROLE_WHOLESALER.equals(selectedRole);
        shopLabel.setVisibility(requiresShopName ? View.VISIBLE : View.GONE);
        shopNameInput.setVisibility(requiresShopName ? View.VISIBLE : View.GONE);
        if (requiresShopName) {
            shopNameInput.setText(roleLabel(selectedRole) + " Shop");
            shopNameInput.setEnabled(true);
        } else {
            shopNameInput.setText("");
            shopNameInput.setEnabled(false);
        }
    }

    private String selectedRoleFromIntent() {
        String role = getIntent().getStringExtra(LoginActivity.EXTRA_SELECTED_ROLE);
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
        input.setMinHeight(dp(48));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
