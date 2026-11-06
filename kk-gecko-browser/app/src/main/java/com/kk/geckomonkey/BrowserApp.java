package com.kk.geckomonkey;

import android.app.Application;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/** One GeckoRuntime per Android process; retains extension storage across Activity recreation. */
public final class BrowserApp extends Application {
    private GeckoRuntime runtime;

    public GeckoRuntime runtime() {
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .extensionsProcessEnabled(true)
                .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }
        return runtime;
    }
}
