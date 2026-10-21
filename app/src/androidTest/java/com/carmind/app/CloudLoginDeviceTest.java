package com.carmind.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in cloud acceptance test. Creates and removes only its own disposable test identity. */
@RunWith(AndroidJUnit4.class)
public class CloudLoginDeviceTest {
    @Test public void firebaseLoginWrongPasswordSessionAndSignOut() throws Exception {
        assumeTrue("Cloud tests require an explicit opt-in", "true".equals(
                InstrumentationRegistry.getArguments().getString("cloudAuthTest")));
        Instrumentation instrument = InstrumentationRegistry.getInstrumentation();
        Context context = instrument.getTargetContext();
        CarMindApplication app = (CarMindApplication) context.getApplicationContext();
        FirebaseAuth auth = app.getAuth();
        assertNotNull("Restore the team's Firebase configuration before this test", auth);
        assertNull("Use an isolated signed-out emulator; never replace a person's session", auth.getCurrentUser());
        String email = "carmind-test-" + UUID.randomUUID() + "@example.com";
        String password = "Test!8-" + UUID.randomUUID();
        String uid = null;
        Activity home = null, simulation = null;
        try {
            FirebaseUser fixture = Tasks.await(auth.createUserWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS).getUser();
            assertNotNull(fixture);
            uid = fixture.getUid();
            auth.signOut();
            try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
                login.onActivity(a -> {
                    assertTrue("Configured Firebase login must be enabled", a.findViewById(R.id.sign_in).isEnabled());
                    a.findViewById(R.id.sign_in).performClick();
                    assertEquals(a.getString(R.string.email_required), ((TextView) a.findViewById(R.id.error)).getText().toString());
                    ((EditText) a.findViewById(R.id.email)).setText("not-an-email");
                    a.findViewById(R.id.sign_in).performClick();
                    assertEquals(a.getString(R.string.email_invalid), ((TextView) a.findViewById(R.id.error)).getText().toString());
                    ((EditText) a.findViewById(R.id.email)).setText(email);
                    a.findViewById(R.id.sign_in).performClick();
                    assertEquals(a.getString(R.string.password_required), ((TextView) a.findViewById(R.id.error)).getText().toString());
                    ((EditText) a.findViewById(R.id.password)).setText("Wrong-test-password!7");
                    a.findViewById(R.id.sign_in).performClick();
                });
                waitFor(() -> {
                    AtomicReference<String> error = new AtomicReference<>();
                    login.onActivity(a -> error.set(((TextView) a.findViewById(R.id.error)).getText().toString()));
                    return context.getString(R.string.invalid_credentials).equals(error.get());
                });
                assertNull(auth.getCurrentUser());
                Instrumentation.ActivityMonitor monitor = instrument.addMonitor(HomeActivity.class.getName(), null, false);
                try {
                    login.onActivity(a -> {
                        ((EditText) a.findViewById(R.id.password)).setText(password);
                        a.findViewById(R.id.sign_in).performClick();
                        assertFalse("Duplicate submissions disabled", a.findViewById(R.id.sign_in).isEnabled());
                    });
                    home = instrument.waitForMonitorWithTimeout(monitor, 30000);
                    assertNotNull("Valid cloud credentials open Home", home);
                } finally { instrument.removeMonitor(monitor); }
            }
            assertNotNull(auth.getCurrentUser());
            assertEquals(uid, auth.getCurrentUser().getUid());
            Activity previousHome = home;
            context.startActivity(new android.content.Intent(context, LoginActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            AtomicReference<Activity> restoredHome = new AtomicReference<>();
            waitFor(() -> {
                instrument.runOnMainSync(() -> {
                    for (Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                        if (activity instanceof HomeActivity && activity != previousHome) restoredHome.set(activity);
                    }
                });
                return restoredHome.get() != null;
            });
            home = restoredHome.get();
            assertEquals(uid, auth.getCurrentUser().getUid());
            Activity signedInHome = home;
            Instrumentation.ActivityMonitor simMonitor = instrument.addMonitor(SimulationActivity.class.getName(), null, false);
            try {
                instrument.runOnMainSync(() -> {
                    assertEquals(email, ((TextView) signedInHome.findViewById(R.id.account_email)).getText().toString());
                    signedInHome.findViewById(R.id.classroom_demo).performClick();
                });
                simulation = instrument.waitForMonitorWithTimeout(simMonitor, 10000);
                assertNotNull("Demo is reachable after real Firebase sign-in", simulation);
            } finally { instrument.removeMonitor(simMonitor); }
            Activity signedInSimulation = simulation;
            Instrumentation.ActivityMonitor loginMonitor = instrument.addMonitor(LoginActivity.class.getName(), null, false);
            try {
                instrument.runOnMainSync(() -> signedInSimulation.findViewById(R.id.demo_sign_out).performClick());
                Activity signedOut = instrument.waitForMonitorWithTimeout(loginMonitor, 10000);
                assertNotNull("Sign-out returns to login", signedOut);
                assertNull(auth.getCurrentUser());
                instrument.runOnMainSync(signedOut::finish);
            } finally { instrument.removeMonitor(loginMonitor); }
        } finally {
            // Reauthenticate only this test's own identity before deleting it.
            if (uid != null) {
                FirebaseUser cleanup = Tasks.await(auth.signInWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS).getUser();
                assertNotNull(cleanup);
                assertEquals("Never delete any other account", uid, cleanup.getUid());
                Tasks.await(cleanup.delete(), 30, TimeUnit.SECONDS);
                context.getSharedPreferences("carmind_demo_readings_v1", 0).edit().remove(uid).commit();
            }
            auth.signOut();
        }
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        long until = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < until) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(200);
        }
        fail("Cloud login did not reach the expected state within 30 seconds");
    }
}
