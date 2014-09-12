package com.endpoint.lg.browser.service;

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.List;
import com.endpoint.lg.browser.service.BrowserInstanceContainer;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private BrowserInstanceContainer bc;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityStartup() {
        bc = new BrowserInstanceContainer(
            getConfiguration(), getLog(),
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
        if (! message.containsKey("windows")) {
            getLog().debug("browser service received message we couldn't understand");
            return;
        }
        try {
            parseWindowMessage(message);
        }
        catch (ClassCastException c) {
            getLog().error("Somewhere we failed to understand this message", c);
        }
    }

    @SuppressWarnings("unchecked")
    public void parseWindowMessage(Map<String, Object> message) {
        getLog().debug("Got windows object of type " + message.get("windows").getClass().getName());
        for (Map<String, Object> window : (List<Map<String, Object>>) message.get("windows")) {
            getLog().debug("got window object of type " + window.getClass().getName());
            if (((String) window.get("activity")).equals("browser")) {
                getLog().debug("Found a browser element: " + window);
                bc.handleBrowserCommand(window);
            }
        }
    }

}
