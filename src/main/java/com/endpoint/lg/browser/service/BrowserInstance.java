package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserWindow;
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

    private NativeActivityRunnerFactory runnerFactory;
    private NativeActivityRunner runner;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;
    private BaseActivity activity;
    private int debugPort = 0;
    private BrowserWindow window;
//    private List<BrowserWindow> windows;
//    private Set<String> connectedWindows;

    BrowserInstance(BaseActivity act, Configuration cfg, Log lg, NativeActivityRunnerFactory nrf, InteractiveSpacesEnvironment ise) {
        final File tmpdir = act.getActivityFilesystem().getTempDataDirectory();
        String className;

        runnerFactory = nrf;
        activity = act;
        log = lg;
        config = cfg;
        webSocketClientService = ise.getServiceRegistry().getService(WebSocketClientService.SERVICE_NAME);
        runner = runnerFactory.newPlatformNativeActivityRunner(log);

        debugPort = findDebugPort();
        if (debugPort == 0) {
            getLog().error("Couldn't start browser instance, because I couldn't find a debug port");
            return;
        }

        className = runBrowser();

        // XXX Do something to fail this loop eventually
        while (! runner.isRunning()) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) { }
        }

        // Establish connection to the debug stuff
            // XXX So how do we know it should be ready for our debug connection?
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {}

        connectDebug(debugPort, className);
    }

    /**
     * Connects a websocket to each available browser window that isn't already connected
     */
    private void connectDebug(int debugPort, String className) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget;
        HttpResponse resp;
        StringBuilder sb;
        InputStream is;
        byte[] buffer = new byte[2048];
        int length;

        ObjectMapper om = new ObjectMapper();
        httpget = new HttpGet("http://localhost:" + debugPort + "/json");
        try {
            resp = httpclient.execute(httpget);
            getLog().debug("Response: " + resp);
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
            for (BrowserTabInfo t : d.tabs) {
                if (t.type.equals("page")) {
                    window = new BrowserWindow(t, activity, className, getLog(), webSocketClientService);
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
                    config.getRequiredPropertyString("space.activity.lg.browser.service.chrome.flags")
                    + " --class=" + className
        );

        // Is this useful? Initial testing didn't prove it did much good.
        // runner.setRestartStrategy(new LimitedRetryRestartStrategy(10, 100, 5000, getSpaceEnvironment()));

        runner.configure(runnerConfig);
        activity.addManagedResource(runner);
        return className;
    }

    private Log getLog() {
        return log;
    }

    /**
     * Signals container that all existing browsers can be recycled, such as
     * when a new scene message comes in
     */
    public void disable() {
        window.disableWindow();
    }

    /**
     * Responds to individual window commands
     */
    @SuppressWarnings("unchecked")
    public void handleBrowserCommand(Window w) {
        boolean found = false;

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
