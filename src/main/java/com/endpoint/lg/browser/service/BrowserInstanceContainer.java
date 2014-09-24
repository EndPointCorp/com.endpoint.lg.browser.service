package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserInstance;
import com.endpoint.lg.support.message.Window;
import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.activity.impl.BaseActivity;
import interactivespaces.InteractiveSpacesException;
import interactivespaces.configuration.Configuration;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.system.InteractiveSpacesEnvironment;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;

/**
 * Class to interact with browser instances. Ideally this will manage a running
 * browser instance, or set thereof, and know which instance to use when a new
 * command comes in. For now, it's just a (fairly useless) layer.
 *
 * @author Josh Tolley <josh@endpoint.com>
*/
public class BrowserInstanceContainer {
    private NativeActivityRunnerFactory runnerFactory;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;
    private BaseActivity activity;
    private List<BrowserInstance> browsers;

    BrowserInstanceContainer(BaseActivity act, Configuration cfg, Log lg, NativeActivityRunnerFactory nrf, InteractiveSpacesEnvironment ise) {
        activity = act;
        runnerFactory = nrf;
        log = lg;
        config = cfg;
        browsers = new ArrayList<BrowserInstance>();
        webSocketClientService = ise.getServiceRegistry().getService(WebSocketClientService.SERVICE_NAME);
    }

    private Log getLog() {
        return log;
    }

    /**
     * Signals container that all existing browsers can be recycled, such as
     * when a new scene message comes in
     */
    public void newScene() {
        for (BrowserInstance b : browsers) {
            b.disableInstance();
        }
    }

    /**
     * Responds to individual window commands
     */
    @SuppressWarnings("unchecked")
    public void handleBrowserCommand(Window window) {
        boolean found = false;
        int debugPort;
        ServerSocket s = null;

        // XXX Do something with geometry stuff

        // Search for a disabled instance we can use for this command.
        for (BrowserInstance b : browsers) {
            if (! b.isEnabled()) {
                b.navigate(window.assets[0]);
                found = true;
                break;
            }
        }

        // Start another instance if we can't find one available already
        if (! found) {
            // Find an appropriate debug port. This could fail, if we find a
            // port and then something steals it. Relatively unlikely, I hope.
            debugPort = 0;
            for (int i = 9900; i < 10000; i++) {
                try {
                    s = new ServerSocket(i);
                    s.setReuseAddress(true);
                    debugPort = i;
                }
                catch (IOException e) {
                    // Port isn't available
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
            }
            else {
                getLog().debug("Found debug port " + debugPort + " for new browser instance");
                BrowserInstance b = new BrowserInstance(debugPort, activity, config, log, runnerFactory, webSocketClientService);
                browsers.add(b);
                b.navigate(window.assets[0]);
            }
        }
    }
}
