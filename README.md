LG Browser Service
==================

Java package: com.endpoint.lg.browser.service

Liquid Galaxy Interactive Spaces activity to manage browser windows in response to JSON messages.


Configuration variables for LG-CMS activities
---------------------------------------------

browser.service
    # Expects a defined input route to receive director messages
    space.activity.lg.browser.service.chrome.path       Where's chrome / chromium?
    space.activity.lg.browser.service.chrome.flags      What flags should we use for it?
    space.activity.lg.browser.service.poolSize          Initial size of browser process pool, default 2
    lg.window.viewport.target                           Viewport name

# Working config
lg.window.viewport.target=42-a
space.activity.lg.browser.service.chrome.flags.1=--enable-webgl --enable-accelerated-compositing
space.activity.lg.browser.service.chrome.flags.2=--disable-dev-tools --disable-logging --disable-metrics
space.activity.lg.browser.service.chrome.flags.3=--disable-metrics-reporting --disable-breakpad
space.activity.lg.browser.service.chrome.flags.4=--disable-default-apps --disable-extensions --disable-java
space.activity.lg.browser.service.chrome.flags.5=--disable-session-storage --disable-translate
space.activity.lg.browser.service.chrome.flags.6=--force-compositing-mod --no-first-run --incognito --kiosk
space.activity.lg.browser.service.chrome.path=/usr/bin/google-chrome
space.activity.lg.browser.service.poolSize=2


# New standard args:
STANDARD_ARGS="--enable-webgl --enable-accelerated-compositing --force-compositing-mod
--allow-file-access-from-files
--disable-default-apps --disable-java
--disable-session-storage --disable-translate
--disk-cache-size=2147483647
--disk-cache-dir=${HOME}/cache/${app_dir}
        # Note that touch stuff depends on something else in /tmp/lg-touch_id, which our ISO doesn't yet have
--touch-events=enabled --disable-pinch --overscroll-history-navigation=0
--disable-touch-editing
--log-level=0 --no-experiments --video-threads=${video_threads}
--disable-extensions-file-access-check
--crash-dumps-dir=${HOME}/cache/${app_dir}/crashes
--remote-debugging-port=${RAND_PORT}"
