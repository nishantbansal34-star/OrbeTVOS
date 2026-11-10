# ORBÉ OS — Phase 1

*Entertainment, without the clutter.*

ORBÉ OS is an ad-free home screen for Android TV and Google TV that replaces the stock launcher. Every app, the DRM it needs, casting and the TV's hardware keep working, because they still run on the TV's own certified Android underneath.

## What's in Phase 1
| Spec item | Status |
|---|---|
| Nav: Home · Apps · Search, plus network status, clock, Control Center and settings | ✅ |
| Hero area (title, type, hint) | ✅ Shows the focused app or input. It can't show film or series artwork yet, because the launcher has no access to that content (see "Not possible as a launcher" below). |
| My Apps dock: pin, reorder, long-press menu (open, pin/unpin, move, hide, app info, uninstall) | ✅ |
| Home sections: My Apps, Recently Opened, Inputs, Recently Installed, Games, Music. Each can be turned on or off and reordered. | ✅ |
| Apps library: grid, sort (A–Z, recently used, recently installed), show/hide hidden apps, app info, uninstall | ✅ (no list view yet) |
| Search: installed apps and settings as you type, and hands off a film or show query to the TV's own search | ✅ |
| Control Center: volume, mute, network, Bluetooth, mirroring, HDMI inputs, display, sound, screensaver, accessibility, privacy | ✅ Opens from the nav bar or the Menu/Settings/Info key. |
| HDMI input switching | ✅ On TVs that expose inputs to apps. Streaming sticks have none. |
| Dark theme (default) and light theme, reduce motion (also follows the system animation setting) | ✅ |
| Memory readout in ORBÉ settings | ✅ |

## Not possible as a launcher
- **Continue Watching with real progress, and a cross-app film catalogue.** Android only lets the system launcher read other apps' "watch next" data. ORBÉ won't fake it.
- **Opening Control Center on a long press of Home, or over a playing app.** The system owns the Home button and drawing over other apps.
- **Picture modes, sound modes, sleep timer, brightness, VRR/ALLM.** These are vendor-private APIs, so ORBÉ shortcuts to the TV's own settings screens instead.
- **Profiles, parental PIN, ambient mode, notification centre.** These are planned for Phase 3–4.

## Performance design
- Zero libraries, no XML layouts and no resources: just framework Views plus R8.
- One hand-drawn View per tile, card and row. Moving focus only scales the tile on the GPU (1.04× over 180 ms), with no layout pass.
- Glass is used only on the nav bar and the side panels. It samples a 32 KB blurred copy of the wallpaper, so there's no live blur cost.
- Banners are decoded off the UI thread at their drawn size as RGB_565, with a 6 MB LRU cap.
- The Apps grid recycles views. The Apps and Search pages, panels and artwork are released when you open an app.
- No polling. The clock and network status update from system broadcasts, and only while the home screen is visible.

## Remote controls
| Key | Action |
|---|---|
| OK | Open |
| Hold OK | App options. On a section in ORBÉ settings, it starts reordering. |
| ◀ ▶ while moving | Reorder My Apps |
| Menu / Settings / Info | Control Center |
| Back | Close panel, then go back to Home |
| Home | Back to the start of Home |
