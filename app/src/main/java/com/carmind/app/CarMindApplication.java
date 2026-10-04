package com.carmind.app;

import android.app.Application;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class CarMindApplication extends Application {
    private FirebaseAuth auth;
    // Keep one request through activity recreation. Never keep the password here.
    Task<AuthResult> pendingSignIn;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp app;
        if (BuildConfig.AUTH_EMULATOR) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:carminddemo")
                    .setApiKey("demo-api-key")
                    .setProjectId("demo-carmind")
                    .build();
            app = FirebaseApp.initializeApp(this, options, "carmind-local");
        } else {
            app = FirebaseApp.initializeApp(this);
        }
        if (app != null) {
            auth = FirebaseAuth.getInstance(app);
            if (BuildConfig.AUTH_EMULATOR) auth.useEmulator("10.0.2.2", 9099);
            if (BuildConfig.DEBUG) Log.d("CarMindAuth", "Firebase initialized"
                    + "\nproject=" + app.getOptions().getProjectId()
                    + "\nenvironment=" + (BuildConfig.AUTH_EMULATOR ? "local emulator" : "cloud"));
        }
    }

    /** Registration and login must share this instance and Firebase project. */
    public FirebaseAuth getAuth() { return auth; }
}
