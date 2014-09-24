package com.endpoint.lg.browser.service;

import com.endpoint.lg.support.window.WindowIdentity;
import com.endpoint.lg.support.window.WindowInstanceIdentity;
import com.endpoint.lg.support.window.ManagedWindow;
import interactivespaces.activity.impl.BaseActivity;
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
import java.io.File;

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
    private WindowIdentity windowId;
    private ManagedWindow window;
    private BaseActivity activity;
    private NativeActivityRunner runner;
    private boolean enabled;
    private int debugPort;

    BrowserInstance(int _debugPort, BaseActivity act, Configuration cfg, Log lg,
            NativeActivityRunnerFactory nrf, WebSocketClientService wsockService)
    {
        final File tmpdir = act.getActivityFilesystem().getTempDataDirectory();
        windowId = new WindowInstanceIdentity(tmpdir.getAbsolutePath());
        window = new ManagedWindow(act, windowId);

        disableInstance();
        activity = act;
        runnerFactory = nrf;
        log = lg;
        config = cfg;
        webSocketClientService = wsockService;
        runner = runnerFactory.newPlatformNativeActivityRunner(getLog());
        debugPort = _debugPort;
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
        activity.addManagedResource(runner);
        activity.addManagedResource(window);

        // XXX Do something to fail this loop eventually
        while (! runner.isRunning()) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) { }
        }

        // Establish connection to the debug stuff
            // XXX So how do we know it should be ready for our debug connection?
        try {
            Thread.sleep(1500);
        }
        catch (InterruptedException e) {}

        connectDebugger(debugPort);
        window.setVisible(false);
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

            // You'd think addManagedResource would be the way to go. Somehow
            // it sets debugWebSocket to null, though, so later navigate
            // commands fail. So I'm just calling startup() instead
            debugWebSocket.startup();
            //activity.addManagedResource(debugWebSocket);
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

    public void disableInstance() {
        enabled = false;
        window.setVisible(false);
    }

    public void enableInstance() {
        enabled = true;
        window.setVisible(true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void navigate(String url) {
        // We could use JSON builders for this, but these commands are short,
        // and easy enough just to put together manually.

        // XXX Do something if we're not yet connected

            // Note that this doesn't sanitize the url variable. If someone
            // gets worried about "url injection" they're welcome to change that.
        String command = "{\"id\":1,\"method\":\"Page.navigate\",\"params\":{\"url\":\"" + url + "\"}}";
        getLog().info("Sending navigate command: " + command);
        debugWebSocket.writeDataAsString(command);
        enableInstance();
    }

    public void reload() {
        // XXX Do something if we're not yet connected
        String command = "{\"id\":1,\"method\":\"Page.reload\",\"params\":{\"ignoreCache\":\"True\"}}";
        getLog().debug("Sending reload command: " + command);
        debugWebSocket.writeDataAsString(command);
    }

    public void shutdown() {
        debugWebSocket.shutdown();
        runner.shutdown();
        window.shutdown();
    }

    // XXX there should be some way to fiddle with geometry and window location
    // It's suggested I look in the appwrapper config in lg-cms for examples of how this is currently done
}
