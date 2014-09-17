package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserInstanceContainer;
import com.endpoint.lg.support.message.Scene;
import com.endpoint.lg.support.message.Window;
import com.google.common.collect.Maps;
import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private BrowserInstanceContainer bc;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityStartup() {
        bc = new BrowserInstanceContainer(
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

    /*
        {
          "description": "<p>Its local name is Tāžī Spay (Pashto: تاژي سپی‎)</p>", 
          "duration": 33, 
          "name": "Afghan Hound", 
          "resource_uri": "/director_api/scene/afghan-hound/", 
          "slug": "afghan-hound", 
          "windows": [
            {
              "activity": "video", 
                ...
            }, 
            {
              "activity": "earth", 
                ...
            }, 
            {
              "activity": "browser", 
              "assets": [
                "http://lg-cms/media/assets/afghanistan_info.html"
              ], 
              "height": 1920, 
              "presentation_viewport": "42-b", 
              "width": 1080, 
              "x_coord": 0, 
              "y_coord": 0
            }
          ]
        }, 
    */
    public void onNewInputJson(String channelName, Map<String, Object> message) {
        Scene s;

        try {
            s = Scene.fromJson(jsonStringify(message));
            for (Window w : s.windows) {
                if (w.activity.equals("browser")) {
                    bc.handleBrowserCommand(w);
                }
            }
        }
        catch (IOException e) {
            getLog().error("Couldn't parse JSON message", e);
        }
    }
}
