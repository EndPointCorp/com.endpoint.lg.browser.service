package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserInstance;
import com.endpoint.lg.support.message.Scene;
import com.endpoint.lg.support.message.Window;
import com.google.common.collect.Maps;
import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private List<BrowserInstance> browsers;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityStartup() {
        Integer browserPoolSize;
        browsers = new ArrayList<BrowserInstance>();

        // Default is 2 browsers, if we don't say otherwise
        browserPoolSize = getConfiguration().getPropertyInteger("space.activity.lg.browser.service.poolSize", 2);
        for (int i = 0; i < browserPoolSize; i++) {
            newBrowser();
        }
        getLog().info("Activity com.endpoint.lg.browser.service startup");
    }
    
    public BrowserInstance newBrowser() {
        BrowserInstance bi = new BrowserInstance(this, getConfiguration(), getLog(), getSpaceEnvironment());
        browsers.add(bi);
        return bi;
    }

    @Override
    public void onActivityPostStartup() {
        getLog().info("Activity com.endpoint.lg.browser.service post startup");
    }

    @Override
    public void onActivityActivate() {
        getLog().info("Activity com.endpoint.lg.browser.service activate");
    }

    @Override
    public void onActivityDeactivate() {
        getLog().info("Activity com.endpoint.lg.browser.service deactivate");
    }

    @Override
    public void onActivityPreShutdown() {
        getLog().info("Activity com.endpoint.lg.browser.service pre shutdown");
        for (BrowserInstance b : browsers) {
            b.shutdown();
        }
    }

    @Override
    public void onActivityShutdown() {
        getLog().info("Activity com.endpoint.lg.browser.service shutdown");
    }

    @Override
    public void onActivityCleanup() {
        getLog().info("Activity com.endpoint.lg.browser.service cleanup");
    }

    public void onNewInputJson(String channelName, Map<String, Object> message) {
        Scene s;
        Iterator<BrowserInstance> i;
        BrowserInstance bi;

        if (!isActivated()) {
            getLog().info("Received message, but activity isn't yet running.");
            return;
        }

        getLog().debug("Browser service activity got a new message: " + message);
        try {
            s = Scene.fromJson(jsonStringify(message));
            for (BrowserInstance b : browsers) {
                b.disable();
            }
            i = browsers.iterator();
            for (Window w : s.windows) {
                if (w.activity.equals("browser") &&
                        Arrays.asList(
                            getConfiguration().getRequiredPropertyString("lg.window.viewport.target").split(",")
                        ).indexOf(w.presentation_viewport) != -1
                    ) {
                    if (i.hasNext()) {
                        bi = i.next();
                    }
                    else {
                        bi = newBrowser();
                    }
                    bi.handleBrowserCommand(w);
                }
            }
        }
        catch (IOException e) {
            getLog().error("Couldn't parse JSON message", e);
        }
    }
}
