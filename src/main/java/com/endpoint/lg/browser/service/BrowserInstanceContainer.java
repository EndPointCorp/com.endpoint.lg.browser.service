package com.endpoint.lg.browser.service;

import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.configuration.Configuration;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.system.InteractiveSpacesEnvironment;
import org.apache.commons.logging.Log;
import com.endpoint.lg.browser.service.BrowserInstance;
import java.util.Map;

public class BrowserInstanceContainer {
    private NativeActivityRunnerFactory runnerFactory;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;

    BrowserInstanceContainer(Configuration cfg, Log lg, NativeActivityRunnerFactory nrf, InteractiveSpacesEnvironment ise) {
        runnerFactory = nrf;
        log = lg;
        config = cfg;
        webSocketClientService = ise.getServiceRegistry().getService(WebSocketClientService.SERVICE_NAME);
    }

    private Log getLog() {
        return log;
    }

    public void handleBrowserCommand(Map<String, Object> cmd) {
        // XXX Eventually, this should recycle browser instances and actually
        // behave as a container, rather than just creating a new one for each
        // message
        BrowserInstance b = new BrowserInstance(config, log, runnerFactory, webSocketClientService);
        b.handleBrowserCommand(cmd);
    }
}
