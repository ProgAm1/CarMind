package com.carmind.app;

/** Non-exported component-test host. Absent from release; no launcher entry. */
public class SimulationPreviewActivity extends SimulationActivity {
    static boolean compactLargeTextTest;
    @Override protected void attachBaseContext(android.content.Context base) {
        if (compactLargeTextTest) {
            android.content.res.Configuration configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
            configuration.fontScale = 2f;
            configuration.densityDpi = 480;
            configuration.uiMode = (configuration.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    | android.content.res.Configuration.UI_MODE_NIGHT_YES;
            base = base.createConfigurationContext(configuration);
        }
        super.attachBaseContext(base);
    }
    @Override protected String currentOwner() { return "simulation-component-test"; }
    @Override protected String currentEmail() { return "COMPONENT TEST · simulated vehicle"; }
}
