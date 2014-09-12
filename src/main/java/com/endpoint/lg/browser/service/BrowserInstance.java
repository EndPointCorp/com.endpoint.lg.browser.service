package com.endpoint.lg.browser.service;

import interactivespaces.activity.binary.NativeActivityRunner;
import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.util.process.restart.LimitedRetryRestartStrategy;
import interactivespaces.configuration.Configuration;
import interactivespaces.util.data.json.JsonMapper;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.service.web.client.WebSocketClient;
import interactivespaces.service.web.WebSocketHandler;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.commons.logging.Log;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

public class BrowserInstance {
    private NativeActivityRunnerFactory runnerFactory;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;

    BrowserInstance(Configuration cfg, Log lg,
            NativeActivityRunnerFactory nrf, WebSocketClientService wsockService)
    {
        runnerFactory = nrf;
        log = lg;
        config = cfg;
        webSocketClientService = wsockService;
    }

    private Log getLog() {
        return log;
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
        int debugPort = 9922;   // XXX Choose this in a way that won't fail
                                // miserably if some hardcoded port happens to
                                // be unavailable
        NativeActivityRunner runner = runnerFactory.newPlatformNativeActivityRunner(getLog());
        Map<String, Object> runnerConfig = Maps.newHashMap();

        runnerConfig.put(
            NativeActivityRunner.ACTIVITYNAME,
            config.getRequiredPropertyString("space.activity.lg.browser.service.chrome.path")
        );
        runnerConfig
            .put(
                NativeActivityRunner.FLAGS,
                "--remote-debugging-port=" + debugPort + " " +
                    config.getRequiredPropertyString("space.activity.lg.browser.service.chrome.flags")
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
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {}

        getBrowserInstance(debugPort);
    }

    public void getBrowserInstance(int debugPort) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget;
        HttpResponse resp;
        StringBuilder sb;
        InputStream is;
        WebSocketClient wsclient;
        byte[] buffer = new byte[2048];
        int length;

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

            String url = getForegroundWSUrl(sb.toString());
            wsclient = getWSConnection(url);
            wsclient.startup();
        }
        catch (IOException e) {
            getLog().error("Exception connecting to browser debug port", e);
        }
    }

    @SuppressWarnings("unchecked")
    public String getForegroundWSUrl(String json) {
        JsonMapper jm = new JsonMapper();
        Map<String, Object> tabs = jm.parseObject(json);
        List<Map<String, String>> tablist = (List<Map<String, String>>) tabs.get("tabs");
        for (Map<String, String> t : tablist) {
            getLog().debug("Found tab: " + t.get("type") + " " + t.get("description"));
            if (t.get("type").equals("page"))
                return t.get("webSocketDebuggerUrl");
        }
        return null;
    }

    private class BrowserDebugWebSocketHandler implements WebSocketHandler {
        // XXX Make getter / setter for this, and turn it private
        public WebSocketClient wsclient;

        @Override
        public void onConnect() {
            String command = "{\"id\":1,\"method\":\"Page.navigate\",\"params\":{\"url\":\"http://www.endpoint.com\"}}";
            log.debug("Connect: " + command);
            wsclient.writeDataAsString( command );
        }

        @Override
        public void onClose() {
            log.debug("Close");
        }

        @Override
        public void onReceive(Object msg) {
            log.debug("Receive: " + msg.toString());
        }

    }

    public WebSocketClient getWSConnection(String url) {
        BrowserDebugWebSocketHandler handler = new BrowserDebugWebSocketHandler();
        WebSocketClient wsclient = webSocketClientService.newWebSocketClient(url, handler, getLog());
        handler.wsclient = wsclient;
        return wsclient;
    }
}
