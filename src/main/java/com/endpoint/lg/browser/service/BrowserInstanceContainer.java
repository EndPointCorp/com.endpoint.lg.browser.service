package com.endpoint.lg.browser.service;

import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.configuration.Configuration;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.system.InteractiveSpacesEnvironment;
import interactivespaces.activity.impl.BaseActivity;
import org.apache.commons.logging.Log;
import com.endpoint.lg.browser.service.BrowserInstance;
import java.util.Map;
import java.util.List;

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

    BrowserInstanceContainer(BaseActivity act, Configuration cfg, Log lg, NativeActivityRunnerFactory nrf, InteractiveSpacesEnvironment ise) {
        activity = act;
        runnerFactory = nrf;
        log = lg;
        config = cfg;
        webSocketClientService = ise.getServiceRegistry().getService(WebSocketClientService.SERVICE_NAME);
    }

    private Log getLog() {
        return log;
    }

    /**
     */
    @SuppressWarnings("unchecked")
    public void handleBrowserCommand(Map<String, Object> cmd) {
        /* Typical scene message:
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
        // XXX Eventually, this should recycle browser instances and actually
        // behave as a container, rather than just creating a new one for each
        // message
        BrowserInstance b = new BrowserInstance(activity, config, log, runnerFactory, webSocketClientService);
        List<String> urls = (List<String>) cmd.get("assets");
        b.navigate(urls.get(0));
        // XXX Don't just lose the browser instance here. We've got to shut it down, at the very least.
    }
}
