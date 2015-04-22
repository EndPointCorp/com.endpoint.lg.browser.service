/*
 * Copyright (C) 2015 End Point Corporation
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.endpoint.lg.browser.service;

import com.endpoint.lg.browser.service.BrowserInstance;
import com.endpoint.lg.support.message.Scene;
import com.endpoint.lg.support.message.Window;
import com.google.common.collect.Maps;
import interactivespaces.activity.impl.ros.BaseRoutableRosActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BrowserServiceActivity extends BaseRoutableRosActivity {
    private List<BrowserInstance> browsers;
    private HashSet<String> viewports;

    @Override
    public void onActivitySetup() {
        getLog().info("Activity com.endpoint.lg.browser.service setup");
    }

    @Override
    public void onActivityConfiguration(Map<String, Object> update) {
        getLog().debug("Browser service configuration signalled");
        reloadViewports();
    }

    private void reloadViewports() {
        viewports = new HashSet<String>(Arrays.asList(
                        getConfiguration().getRequiredPropertyString("lg.window.viewport.target").split(",")
                    ));
        getLog().debug("Reloaded viewports for browser activity");
    }

    public ArrayList<BrowserStaticPageConf> getStaticPages() {
        ArrayList<BrowserStaticPageConf> staticPages = new ArrayList<BrowserStaticPageConf>();
        String staticPageNames = getConfiguration().getPropertyString("lg.browser.static_pages", "");
        String staticName;

        for (String pageName : staticPageNames.split(",")) {
            BrowserStaticPageConf page = new BrowserStaticPageConf();
            page.name = pageName;
            page.url = getConfiguration().getPropertyString("lg.browser.static_page." + pageName + ".url", "");
            page.viewport = getConfiguration().getPropertyString("lg.browser.static_page." + pageName + ".viewport", "");
            page.height = getConfiguration().getPropertyInteger("lg.browser.static_page." + pageName + ".height", 0);
            page.width = getConfiguration().getPropertyInteger("lg.browser.static_page." + pageName + ".width", 0);
            page.x_coord = getConfiguration().getPropertyInteger("lg.browser.static_page." + pageName + ".x_coord", 0);
            page.y_coord = getConfiguration().getPropertyInteger("lg.browser.static_page." + pageName + ".y_coord", 0);

            if (page.url.equals("") || page.viewport.equals("")) {
                getLog().warn("Browser service static page " + pageName + " isn't completly defined. Needs lg.browser.static_page." + pageName + ".url and lg.browser.static_page." + pageName + ".viewport configuration values.");
            }
            else {
                staticPages.add(page);
            }
        }
        return staticPages;
    }

    @Override
    public void onActivityStartup() {
        Integer browserPoolSize;
        browsers = new ArrayList<BrowserInstance>();
        ArrayList<BrowserStaticPageConf> staticPages = getStaticPages();

        // Default is 2 browsers, if we don't say otherwise, plus one for each static instance
        browserPoolSize = getConfiguration().getPropertyInteger("space.activity.lg.browser.service.poolSize", 2);
        for (int i = 0; i < browserPoolSize; i++) {
            newBrowser();
        }
        for (BrowserStaticPageConf p : staticPages) {
            newBrowser(p);
        }

        reloadViewports();
        getLog().info("Activity com.endpoint.lg.browser.service startup");
    }

    public BrowserInstance newBrowser(BrowserStaticPageConf p) {
        BrowserInstance bi = newBrowser();
        bi.navigate(p);
        return bi;
    }
    
    public BrowserInstance newBrowser() {
        BrowserInstance bi = new BrowserInstance(this, getConfiguration(), getLog(), getSpaceEnvironment());
        browsers.add(bi);
        return bi;
    }

    @Override
    public void onActivityPostStartup() {
        getLog().info("Activity com.endpoint.lg.browser.service post startup");
    }

    @Override
    public void onActivityActivate() {
        getLog().info("Activity com.endpoint.lg.browser.service activate");
    }

    @Override
    public void onActivityDeactivate() {
        getLog().info("Activity com.endpoint.lg.browser.service deactivate");
    }

    @Override
    public void onActivityPreShutdown() {
        getLog().info("Activity com.endpoint.lg.browser.service pre shutdown");
        for (BrowserInstance b : browsers) {
            b.shutdown();
        }
    }

    @Override
    public void onActivityShutdown() {
        getLog().info("Activity com.endpoint.lg.browser.service shutdown");
    }

    @Override
    public void onActivityCleanup() {
        getLog().info("Activity com.endpoint.lg.browser.service cleanup");
    }

    public void onNewInputJson(String channelName, Map<String, Object> message) {
        Iterator<BrowserInstance> i;
        BrowserInstance bi;
        HashSet<Window> windows;
        Scene s;

        if (!isActivated()) {
            getLog().info("Received message, but activity isn't yet running.");
            return;
        }

        getLog().debug("Browser service activity got a new message: " + message);
        try {
            s = Scene.fromJson(jsonStringify(message));

            // Collect all windows in the new scene into a set. I can review this
            // set for windows that are already implemented in active browsers, and
            // remove those from the set before further processing.
            windows = new HashSet<Window>();
            for (Window w : s.windows) {
                if (w.activity.equals("browser") && viewports.contains(w.presentation_viewport)) {
                    getLog().debug("Adding window to scene: " + w.toString());
                    windows.add(w);
                }
                else {
                    getLog().debug("Not handling window message, because it doesn't match viewports or activity");
                }
            }

            // Look for browsers currently running any of the windows in this new scene
            for (BrowserInstance b : browsers) {
                if (!b.isAvailable() && !b.isStatic()) {
                    // If it's not available, it must be running something.
                    // Does the Window it's running appear in the current
                    // Scene? If so, leave it alone. If not, disable it.
                    // Ignore static windows
                    if (windows.contains(b.getCurrentWindow())) {
                        getLog().info("Removed window because it's already displayed: " + b.getCurrentWindow().toString());
                        windows.remove(b.getCurrentWindow());
                    }
                    else {
                        getLog().debug("Disabling browser with currentWindow " + b.getCurrentWindow().toString());
                        b.disable();
                    }
                }
            }
            i = browsers.iterator();
            for (Window w : windows) {
                if (i.hasNext()) {
                    bi = i.next();
                }
                else {
                    bi = newBrowser();
                }
                bi.handleBrowserCommand(w);
            }
        }
        catch (IOException e) {
            getLog().error("Couldn't parse JSON message", e);
        }
    }
}
