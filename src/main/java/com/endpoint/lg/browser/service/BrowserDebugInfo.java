package com.endpoint.lg.browser.service;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrowserDebugInfo {
    public BrowserTabInfo[] tabs;
}

