package com.carmind.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

public class LoginActivity extends Activity {
    private CarMindApplication application;
    private FirebaseAuth auth;
    private EditText email, password;
    private Button signIn, register;
    private CheckBox showPassword;
    private ProgressBar progress;
    private TextView error;
    private boolean navigating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ScreenInsets.apply(findViewById(R.id.root));
        application = (CarMindApplication) getApplication();
        auth = application.getAuth();
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        signIn = findViewById(R.id.sign_in);
        register = findViewById(R.id.register);
        showPassword = findViewById(R.id.show_password);
        progress = findViewById(R.id.progress);
        error = findViewById(R.id.error);
        signIn.setOnClickListener(view -> signIn());
        password.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { signIn(); return true; }
            return false;
        });
        showPassword.setOnCheckedChangeListener((button, checked) -> {
            int cursor = password.getSelectionStart();
            password.setTransformationMethod(checked ? null : PasswordTransformationMethod.getInstance());
            if (cursor >= 0) password.setSelection(cursor);
        });
        register.setOnClickListener(view -> startActivity(registrationIntent()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The teammate adds RegistrationActivity and declares it in the manifest.
        boolean registrationAvailable;
        try {
            registrationAvailable = getPackageManager().getActivityInfo(registrationIntent().getComponent(), 0).enabled;
        } catch (PackageManager.NameNotFoundException missing) {
            registrationAvailable = false;
        }
        register.setVisibility(registrationAvailable ? View.VISIBLE : View.GONE);
        if (auth == null) {
            setBusy(false);
            signIn.setEnabled(false);
            showError(R.string.auth_unavailable);
        } else if (auth.getCurrentUser() != null) {
            application.pendingSignIn = null;
            openHome();
        } else if (application.pendingSignIn != null) {
            observeSignIn();
        }
    }

    private Intent registrationIntent() {
        return new Intent().setClassName(this, "com.carmind.app.RegistrationActivity");
    }

    private void signIn() {
        if (auth == null || application.pendingSignIn != null) return;
        error.setVisibility(View.GONE);
        email.setError(null);
        password.setError(null);
        String emailValue = email.getText().toString().trim();
        String passwordValue = password.getText().toString();
        switch (LoginValidation.validate(emailValue, passwordValue)) {
            case EMAIL_REQUIRED:
                showError(R.string.email_required); email.requestFocus(); return;
            case EMAIL_INVALID:
                showError(R.string.email_invalid); email.requestFocus(); return;
            case PASSWORD_REQUIRED:
                showError(R.string.password_required); password.requestFocus(); return;
            case NONE: break;
        }
        application.pendingSignIn = auth.signInWithEmailAndPassword(emailValue, passwordValue);
        observeSignIn();
    }

    private void observeSignIn() {
        setBusy(true);
        application.pendingSignIn.addOnCompleteListener(this, this::finishSignIn);
    }

    private void finishSignIn(Task<AuthResult> task) {
        application.pendingSignIn = null;
        setBusy(false);
        if (task.isSuccessful()) {
            password.setText("");
            openHome();
        } else {
            Exception failure = task.getException();
            int message = R.string.sign_in_failed;
            if (failure instanceof FirebaseNetworkException) message = R.string.network_error;
            else if (failure instanceof FirebaseTooManyRequestsException) message = R.string.too_many_requests;
            else if (failure instanceof FirebaseAuthException) {
                String code = ((FirebaseAuthException) failure).getErrorCode();
                if (code.equals("ERROR_INVALID_CREDENTIAL") || code.equals("ERROR_WRONG_PASSWORD")
                        || code.equals("ERROR_USER_NOT_FOUND") || code.equals("ERROR_INVALID_LOGIN_CREDENTIALS")) {
                    message = R.string.invalid_credentials;
                } else if (code.equals("ERROR_USER_DISABLED")) message = R.string.account_disabled;
            }
            showError(message);
        }
    }

    private void setBusy(boolean busy) {
        email.setEnabled(!busy);
        password.setEnabled(!busy);
        showPassword.setEnabled(!busy);
        signIn.setEnabled(!busy);
        register.setEnabled(!busy);
        signIn.setText(busy ? R.string.signing_in : R.string.sign_in);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showError(int message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void openHome() {
        if (navigating) return;
        navigating = true;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override
    protected void onStop() {
        password.setText("");
        showPassword.setChecked(false);
        super.onStop();
    }
}
