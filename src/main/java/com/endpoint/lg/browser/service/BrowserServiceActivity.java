package com.endpoint.lg.browser.service;

import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import interactivespaces.activity.binary.NativeActivityRunner;
import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.util.process.restart.LimitedRetryRestartStrategy;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.List;
import java.io.InputStream;
import java.io.IOException;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.map.ObjectMapper;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private NativeActivityRunnerFactory runnerFactory;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityStartup() {
        runnerFactory = getController().getNativeActivityRunnerFactory();
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

    // These should probably be part of com.endpoint.lg.support
    private class Window {
        public String activity, presentation_viewport;
        public int height, width, x_coord, y_coord;
        public String[] assets;
        public String toString() {
            return "Activity: " + activity + " and some other stuff";
        }
    }

    private class Scene {
        public String description, name, resource_uri, slug;
        public int duration;
        public Window[] windows;
        public String toString() {
            return "Desc: " + description;
        }
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
        getLog().info("Got a JSON message, and we're not parsing it here.");
    }

    public void onNewInputString(String channelName, String message) {
        ObjectMapper mapper = new ObjectMapper();
        Scene newScene;
        try {
            newScene = mapper.readValue(message, Scene.class);

            if (newScene.windows == null || newScene.windows.length == 0) {
                getLog().debug("browser service received message we couldn't understand");
                return;
            }
            for (Window window : newScene.windows) {
                getLog().debug("got window object of type " + window.activity);
                if (window.activity.equals("browser")) {
                    getLog().debug("Found a browser element: " + window);
                // handleBrowserCommand(window);
                }
            }
        }
        catch (IOException i) {
            getLog().error("IOException", i);
        }
//
//        if (! message.containsKey("windows")) {
//            getLog().debug("browser service received message we couldn't understand");
//            return;
//        }
//        try {
//            getLog().debug("Got windows object of type " + message.get("windows").getClass().getName());
//            for (Map<String, Object> window : (List<Map<String, Object>>) message.get("windows")) {
//                getLog().debug("got window object of type " + window.getClass().getName());
//                if (((String) window.get("activity")).equals("browser")) {
//                    getLog().debug("Found a browser element: " + window);
//                    handleBrowserCommand(window);
//                }
//            }
//        }
//        catch (ClassCastException c) {
//            getLog().error("Somewhere we failed to understand this message", c);
//        }
    }

    /*
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
    */
    public void handleBrowserCommand(Map<String, Object> cmd) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget;
        HttpResponse resp;
        StringBuilder sb;
        InputStream is;
        byte[] buffer = new byte[2048];
        int length;

        int debugPort = 9922;   // XXX Choose this in a way that won't fail
                                // miserably if some hardcoded port happens to
                                // be unavailable
        NativeActivityRunner runner = runnerFactory.newPlatformNativeActivityRunner(getLog());
        Map<String, Object> runnerConfig = Maps.newHashMap();

        runnerConfig.put(
            NativeActivityRunner.ACTIVITYNAME,
            getConfiguration().getRequiredPropertyString("space.activity.lg.browser.service.chrome.path")
        );
        runnerConfig
            .put(
                NativeActivityRunner.FLAGS,
                "--remote-debugging-port=" + debugPort + " " +
                    getConfiguration().getRequiredPropertyString("space.activity.lg.browser.service.chrome.flags")
        );

        // Is this useful? Initial testing didn't prove it did much good.
        // runner.setRestartStrategy(new LimitedRetryRestartStrategy(10, 100, 5000, getSpaceEnvironment()));

        runner.configure(runnerConfig);
        runner.startup();

        // XXX Do something to fail this loop eventually
        while (! runner.isRunning()) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) { }
        }

        // Establish connection to the debug stuff
            // XXX So how do we know it should be ready for our debug connection?
            // XXX Start a pool of browsers (or just one, or something) when this all starts, and get the debugger connected
        try {
            Thread.sleep(10000);
        }
        catch (InterruptedException e) {}
        httpget = new HttpGet("http://localhost:" + debugPort + "/json");
        try {
            resp = httpclient.execute(httpget);
            getLog().info("Response: " + resp);
            sb = new StringBuilder();
            is = resp.getEntity().getContent();
            while ((length = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, length));
            }
            is.close();
            getLog().info(sb);
        }
        catch (IOException e) {
            getLog().error("Exception connecting to browser debug port", e);
        }
    }
}
