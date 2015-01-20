package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserWindow;
import com.endpoint.lg.support.interactivespaces.ConfigurationHelper;
import com.endpoint.lg.support.message.Window;
import com.google.common.collect.Maps;
import interactivespaces.activity.binary.NativeActivityRunner;
import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.activity.impl.BaseActivity;
import interactivespaces.configuration.Configuration;
import interactivespaces.InteractiveSpacesException;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.system.InteractiveSpacesEnvironment;
import interactivespaces.util.data.json.JsonMapper;
import interactivespaces.util.process.NativeApplicationRunner;
import interactivespaces.util.process.NativeApplicationRunnerCollection;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.logging.Log;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Class to manage one browser instance, with one window
 *
 * @author Josh Tolley <josh@endpoint.com>
*/
public class BrowserInstance {
    private static int MIN_DEBUG_PORT = 19000;
    private static int MAX_DEBUG_PORT = 20000;
    private static String INITIAL_URL = "http://localhost/0xDEADBEEF";

    private NativeApplicationRunnerCollection runnerCollection;
    private NativeApplicationRunner runner;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;
    private BaseActivity activity;
    private int debugPort = 0;
    private BrowserWindow window;
    private Window currentWindow;               // The Window message we processed to get into our current state
    private boolean isAvailable;

    BrowserInstance(BaseActivity act, Configuration cfg, Log lg, InteractiveSpacesEnvironment ise) {
        final File tmpdir = act.getActivityFilesystem().getTempDataDirectory();
        String className;
        HttpResponse resp;
        int i = 0;

        activity = act;
        log = lg;
        config = cfg;
        webSocketClientService = ise.getServiceRegistry().getService(WebSocketClientService.SERVICE_NAME);

        runnerCollection = new NativeApplicationRunnerCollection(ise, lg);
        runner = runnerCollection.newNativeApplicationRunner();
        activity.addManagedResource(runnerCollection);

        debugPort = findDebugPort();
        if (debugPort == 0) {
            getLog().error("Couldn't start browser instance, because I couldn't find a debug port");
            return;
        }

        className = runBrowser();

        while ((window == null || !window.isDebugConnected()) && i < 20) {
            i++;
            try {
                Thread.sleep(100);
                resp = getDebugHttp(debugPort);
                if (resp != null)
                    connectDebugWS(debugPort, resp, className);
            } catch(InterruptedException e) { }
        }

        if (!runner.isRunning()) {
            throw new InteractiveSpacesException("Failed to run browser instance");
        }
        if (window != null && ! window.isDebugConnected()) {
            throw new InteractiveSpacesException("Failed to connect to the browser instance's debug socket");
        }
        if (window == null) {
            throw new InteractiveSpacesException("Failed to create managed window");
        }

        isAvailable = true;
        currentWindow = null;
    }

    /**
     * Attempts to get an HTTP debug response from a Chrome browser listening on the given debugPort
     */
    private HttpResponse getDebugHttp(int debugPort) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget;
        HttpResponse resp;

        httpget = new HttpGet("http://localhost:" + debugPort + "/json");
        try {
            resp = httpclient.execute(httpget);
            getLog().debug("Response: " + resp);
            return resp;
        }
        catch (IOException e) {
            // Don't want to log all these. It's expected we'll fail several times before success
            // getLog().error("Exception connecting to browser debug port", e);
            return null;
        }
    }

    /**
     * Connects a websocket to each available browser window that isn't already connected
     */
    private void connectDebugWS(int debugPort, HttpResponse resp, String className) {
        StringBuilder sb;
        InputStream is;
        byte[] buffer = new byte[2048];
        int length;

        ObjectMapper om = new ObjectMapper();
        try {
            sb = new StringBuilder();
            is = resp.getEntity().getContent();
            sb.append("{\"tabs\":");
            while ((length = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, length));
            }
            sb.append("}");
            is.close();
            getLog().debug(sb);
            BrowserDebugInfo d = om.readValue(sb.toString(), BrowserDebugInfo.class);

            // Here we might have multiple tabs open. We need to be sure we
            // only connect to the visible one. We do this by checking the URL
            // for the one we requested when starting the instance.
            for (BrowserTabInfo t : d.tabs) {
                if (t.type.equals("page") && t.url.equals(INITIAL_URL)) {
                    window = new BrowserWindow(t, activity, className, getLog(), webSocketClientService);
                    break;
                }
            }
        }
        catch (IOException e) {
            getLog().error("Exception connecting to browser debug port", e);
        }
    }

    private String runBrowser() {
        Map<String, Object> runnerConfig = Maps.newHashMap();
        String className =
            activity.getActivityFilesystem().getTempDataDirectory().getAbsolutePath() + "/" +
            UUID.randomUUID().toString().replace("-", "");

        runnerConfig.put(
            NativeActivityRunner.EXECUTABLE_PATHNAME,
            config.getRequiredPropertyString("space.activity.lg.browser.service.chrome.path")
        );
        runnerConfig.put(
                NativeActivityRunner.EXECUTABLE_FLAGS,
                "--user-data-dir=" + className + " " +
                "--remote-debugging-port=" + debugPort + " " +
                getConfigArray("space.activity.lg.browser.service.chrome.flags")
                + " --class=" + className + " " + INITIAL_URL
        );

        // Is this useful? Initial testing didn't prove it did much good.
        // runner.setRestartStrategy(new LimitedRetryRestartStrategy(10, 100, 5000, getSpaceEnvironment()));

        runner.configure(runnerConfig);
        runnerCollection.addNativeApplicationRunner(runner);
        return className;
    }

    private String getConfigArray(String key) {
        return ConfigurationHelper.getConfigurationConcat(config, key, " ");
    }

    private Log getLog() {
        return log;
    }

    /**
     * Signals container that all existing browsers can be recycled, such as
     * when a new scene message comes in
     */
    public void disable() {
        currentWindow = null;
        isAvailable = true;
        if (window != null) window.disableWindow();
    }

    public Window getCurrentWindow() {
        return currentWindow;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Responds to individual window commands
     */
    @SuppressWarnings("unchecked")
    public void handleBrowserCommand(Window w) {
        boolean found = false;

        currentWindow = w;
        isAvailable = false;
        getLog().debug("Positioning browser window to " + w.x_coord + ", " + w.y_coord + ", with dimensions " + w.width + "x" + w.height);
        window.setViewport(w.presentation_viewport);
        window.positionWindow(w.width, w.height, w.x_coord, w.y_coord);
        window.enableWindow();
        window.navigate(w.assets[0]);
    }

    /**
     * Finds an available debug port
     *
     * This could fail, if we find a port and something steals it before we get
     * a browser listening on it. This seems unlikely. Note that chromium
     * doesn't fail if its assigned debug port is unavailable (at least so far
     * as I can tell)
     */
    private int findDebugPort() {
        int debugPort = 0;
        ServerSocket s = null;

        for (int i = MIN_DEBUG_PORT; i < MAX_DEBUG_PORT; i++) {
            try {
                s = new ServerSocket(i);
                s.setReuseAddress(true);
                debugPort = i;
            }
            catch (IOException e) {
                // Port isn't available
                getLog().debug("Port " + i + " isn't available");
            }
            finally {
                try {
                    if (s != null) {
                        s.close();
                    }
                }
                catch (IOException e) {
                    // s wasn't opened. Don't throw this
                }
            }
            if (debugPort != 0)
                break;
        }
        if (debugPort == 0) {
            getLog().error("Couldn't find unused debug port for new browser activity");
            return 0;
        }
        getLog().debug("Found debug port " + debugPort + " for new browser instance");
        return debugPort;
    }

    public void shutdown() {
        window.shutdown();
        runner.shutdown();
    }
}
