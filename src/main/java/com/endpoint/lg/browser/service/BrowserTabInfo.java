package com.endpoint.lg.browser.service;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrowserTabInfo {
    public String description, devtoolsFrontendUrl, faviconUrl, id, title, type, url, webSocketDebuggerUrl;
}
