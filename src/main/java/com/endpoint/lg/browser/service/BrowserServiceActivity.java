package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserInstance;
import com.endpoint.lg.support.message.Scene;
import com.endpoint.lg.support.message.Window;
import com.google.common.collect.Maps;
import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private BrowserInstance bc;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityStartup() {
        bc = new BrowserInstance(
            this, getConfiguration(), getLog(),
            getController().getNativeActivityRunnerFactory(),
            getSpaceEnvironment());
        getLog().info("Activity com.endpoint.lg.browser.service startup");
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

        getLog().debug("Browser service activity got new message: " + message);
        try {
            s = Scene.fromJson(jsonStringify(message));
            bc.newScene();
            for (Window w : s.windows) {
                if (w.activity.equals("browser") && w.presentation_viewport.equals(
                        getConfiguration().getRequiredPropertyString("space.activity.browser.viewport")
                    )) {
                    bc.handleBrowserCommand(w);
                }
            }
        }
        catch (IOException e) {
            getLog().error("Couldn't parse JSON message", e);
        }
    }
}
