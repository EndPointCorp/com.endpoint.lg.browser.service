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

/**
 * Handles a single browser instance. Supports commands to navigate to a new
 * page, and to reload the existing page.
 *
 * @author Josh Tolley <josh@endpoint.com>
 */
public class BrowserInstance {
    private NativeActivityRunnerFactory runnerFactory;
    private Log log;
    private Configuration config;
    private WebSocketClientService webSocketClientService;
    private WebSocketClient debugWebSocket;

    BrowserInstance(Configuration cfg, Log lg,
            NativeActivityRunnerFactory nrf, WebSocketClientService wsockService)
    {
        int debugPort = 9922;   // XXX Choose this in a way that won't fail
                                // miserably if some hardcoded port happens to
                                // be unavailable

        runnerFactory = nrf;
        log = lg;
        config = cfg;
        webSocketClientService = wsockService;
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
            // XXX Start a pool of browsers (or just one, or something) when
            // this all starts, and get the debugger connected
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {}

        connectDebugger(debugPort);
    }

    private Log getLog() {
        return log;
    }

    private void connectDebugger(int debugPort) {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget;
        HttpResponse resp;
        StringBuilder sb;
        InputStream is;
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
            createWSConnection(url);
            debugWebSocket.startup();
        }
        catch (IOException e) {
            getLog().error("Exception connecting to browser debug port", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String getForegroundWSUrl(String json) {
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
        @Override
        public void onConnect() {
            getLog().debug("Connect");
        }

        @Override
        public void onClose() {
            getLog().debug("Close");
        }

        @Override
        public void onReceive(Object msg) {
            getLog().debug("Receive: " + msg.toString());
        }

    }

    private void createWSConnection(String url) {
        debugWebSocket = webSocketClientService.newWebSocketClient(url, new BrowserDebugWebSocketHandler(), getLog());
    }

    public void navigate(String url) {
        // Note that we could use JSON builders for this, but these commands
        // are short, and easy enough just to put together manually.

        // XXX Do something if we're not yet connected

            // Note that this doesn't sanitize the url variable. If someone
            // gets worried about "url injection" they're welcome to change that.
        String command = "{\"id\":1,\"method\":\"Page.navigate\",\"params\":{\"url\":\"" + url + "\"}}";
        getLog().info("Sending navigate command: " + command);
        debugWebSocket.writeDataAsString(command);
    }

    public void reload() {
        // XXX Do something if we're not yet connected
        String command = "{\"id\":1,\"method\":\"Page.reload\",\"params\":{\"ignoreCache\":\"True\"}}";
        getLog().debug("Sending reload command: " + command);
        debugWebSocket.writeDataAsString(command);
    }
}