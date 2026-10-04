package com.carmind.app;

import android.app.Application;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class CarMindApplication extends Application {
    private FirebaseAuth auth;
    // Keep one request through activity recreation. Never keep the password here.
    Task<AuthResult> pendingSignIn;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp app = FirebaseApp.initializeApp(this);
        if (app != null) {
            auth = FirebaseAuth.getInstance(app);
        }
    }

    /** Registration and login must share this instance and Firebase project. */
    public FirebaseAuth getAuth() { return auth; }
}
