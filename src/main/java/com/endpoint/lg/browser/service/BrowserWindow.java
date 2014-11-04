package com.endpoint.lg.browser.service;

import com.endpoint.lg.support.window.ManagedWindow;
import com.endpoint.lg.support.window.WindowGeometry;
import com.endpoint.lg.support.window.WindowIdentity;
import com.endpoint.lg.support.window.WindowInstanceIdentity;
import interactivespaces.activity.binary.NativeActivityRunner;
import interactivespaces.activity.binary.NativeActivityRunnerFactory;
import interactivespaces.activity.impl.BaseActivity;
import interactivespaces.service.web.client.WebSocketClient;
import interactivespaces.service.web.client.WebSocketClientService;
import interactivespaces.service.web.WebSocketHandler;
import interactivespaces.util.process.restart.LimitedRetryRestartStrategy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;

/**
 * Handles a single browser instance. Supports commands to navigate to a new
 * page, and to reload the existing page.
 *
 * @author Josh Tolley <josh@endpoint.com>
 */
public class BrowserWindow {
    private Log log;
    private WebSocketClientService webSocketClientService;
    private WebSocketClient debugWebSocket;
    private ManagedWindow window;
    private boolean enabled;
    private WindowIdentity windowId;
    private BrowserTabInfo tabInfo;

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

    public boolean isDebugConnected() {
        return debugWebSocket.isOpen();
    }

    BrowserWindow(BrowserTabInfo t, BaseActivity act, String className, Log lg, WebSocketClientService wsockService) {
        tabInfo = t;
        log = lg;
        webSocketClientService = wsockService;

        windowId = new WindowInstanceIdentity(className); // tmpdir.getAbsolutePath());
        window = new ManagedWindow(act, windowId);

        debugWebSocket =
            webSocketClientService.newWebSocketClient(t.webSocketDebuggerUrl, new BrowserDebugWebSocketHandler(), getLog());

        debugWebSocket.startup();
        disableWindow();
        getLog().debug("Created BrowserWindow object for tab ID " + t.id + " for tab type " + t.type + ", " + debugWebSocket);
    }

    private Log getLog() {
        return log;
    }

    public void disableWindow() {
        enabled = false;
        window.setVisible(false);
        window.lower();
    }

    public void positionWindow(Integer width, Integer height, Integer x, Integer y) {
        window.setGeometryOffset(new WindowGeometry(width, height, x, y));
        window.resize(width, height);
    }

    public void enableWindow() {
        enabled = true;
        window.setVisible(true);
        window.raise();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void navigate(String url) {
        // We could use JSON builders for this, but these commands are short,
        // and easy enough just to put together manually.

        if (! isDebugConnected()) {
            throw new RuntimeException("Can't navigate browser window; debug socket isn't connected");
        }

            // Note that this doesn't sanitize the url variable. If someone
            // gets worried about "url injection" they're welcome to change that.
        String command = "{\"id\":1,\"method\":\"Page.navigate\",\"params\":{\"url\":\"" + url + "\"}}";
        getLog().info("Sending navigate command: " + command);
        debugWebSocket.writeDataAsString(command);
        enableWindow();
    }

    public void reload() {
        if (! isDebugConnected()) {
            throw new RuntimeException("Can't reload browser window; debug socket isn't connected");
        }
        String command = "{\"id\":1,\"method\":\"Page.reload\",\"params\":{\"ignoreCache\":\"True\"}}";
        getLog().debug("Sending reload command: " + command);
        debugWebSocket.writeDataAsString(command);
    }

    public void shutdown() {
        debugWebSocket.shutdown();
        window.shutdown();
    }
}
