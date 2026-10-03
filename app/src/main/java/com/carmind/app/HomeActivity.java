package com.carmind.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;

/** Minimal authenticated landing screen for Sprint 1. */
public class HomeActivity extends Activity {
    private FirebaseAuth auth;
    private boolean navigating;
    private final FirebaseAuth.AuthStateListener listener = value -> {
        if (value.getCurrentUser() == null) openLogin();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ScreenInsets.apply(findViewById(R.id.root));
        auth = ((CarMindApplication) getApplication()).getAuth();
        findViewById(R.id.sign_out).setOnClickListener(view -> {
            if (auth != null) auth.signOut();
            openLogin();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth == null || auth.getCurrentUser() == null) {
            openLogin();
            return;
        }
        ((TextView) findViewById(R.id.account_email)).setText(auth.getCurrentUser().getEmail());
        auth.addAuthStateListener(listener);
    }

    @Override
    protected void onStop() {
        if (auth != null) auth.removeAuthStateListener(listener);
        super.onStop();
    }

    private void openLogin() {
        if (navigating) return;
        navigating = true;
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
