# Make ORBÉ OS the only home screen (and strip the ads)

Everything here uses `disable-user`, which is **fully reversible**. Nothing is deleted.
Do the steps in order. Don't disable the stock launcher until ORBÉ OS is installed and opens.

## 1. Turn on ADB on the TV
Settings → System → About → click **Android TV OS build** 7 times → Developer options →
turn on **USB debugging** and **Network debugging** (or "ADB over network").
Note the TV's IP address (Settings → Network).

## 2. Connect from your phone
Install **Bugjaeger** or **Remote ADB Shell** from the Play Store, connect to `TV_IP:5555`,
and accept the prompt on the TV.

## 3. Install ORBÉ OS
Either open `https://github.com/<you>/<repo>/releases/latest/download/OrbeOS.apk` in the
**Downloader** app on the TV, or install the APK from your phone through Bugjaeger.

## 4. Pre-compile it for maximum smoothness (one time, after each update)
```
cmd package compile -m speed -f com.nishant.orbeos
```
This compiles the launcher to native code ahead of time: no JIT warm-up stutter.

## 5. Remove the ad-filled stock home screen

**Google TV** (Chromecast with Google TV, newer Sony/TCL/Hisense):
```
pm disable-user --user 0 com.google.android.apps.tv.launcherx
pm disable-user --user 0 com.google.android.tungsten.setupwraith
```
**Android TV** (older interface):
```
pm disable-user --user 0 com.google.android.tvlauncher
pm disable-user --user 0 com.google.android.leanbacklauncher
```
Then press Home and pick **ORBÉ OS → Always**.

## 6. Ads and recommendation services (safe to disable)
```
pm disable-user --user 0 com.google.android.tvrecommendations
pm disable-user --user 0 com.google.android.apps.tv.dreamx   # ad-filled ambient screensaver
```
Brand bloatware (Xiaomi PatchWall, TCL channels, Samsung/Sony promos) depends on the
model. Run `pm list packages` and decide case by case.

## Undo anything
```
pm enable com.google.android.apps.tv.launcherx
pm enable com.google.android.tungsten.setupwraith
```
(Use the same pattern for any package above.)
