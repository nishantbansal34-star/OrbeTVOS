package com.nishant.orbeos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Installed apps, TV inputs, and artwork decoding — all off the UI thread. */
final class AppRepo {

    static final class AppEntry {
        final String pkg;
        final ComponentName cn;
        final String label;
        final String kind;
        final boolean leanback;
        final boolean system;
        final long installed;
        boolean banner;

        AppEntry(String pkg, String cls, String label, String kind, boolean leanback, boolean system, long installed) {
            this.pkg = pkg;
            this.cn = new ComponentName(pkg, cls);
            this.label = label;
            this.kind = kind;
            this.leanback = leanback;
            this.system = system;
            this.installed = installed;
        }
    }

    static final class InputEntry {
        final String id;
        final String label;

        InputEntry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    /** Network work (weather, series, posters) never blocks local icon decoding. */
    static final ExecutorService NET = Executors.newFixedThreadPool(2);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Hard cap on decoded artwork; evicted art is re-decoded on demand. */
    static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static final HashMap<String, ArrayList<Runnable>> WAITING = new HashMap<>();

    private AppRepo() {}

    static void runBackground(final Runnable r) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }
        });
    }

    static List<AppEntry> query(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppEntry> map = new LinkedHashMap<>();
        add(pm, map, Intent.CATEGORY_LEANBACK_LAUNCHER, ctx.getPackageName(), true);
        add(pm, map, Intent.CATEGORY_LAUNCHER, ctx.getPackageName(), false);
        ArrayList<AppEntry> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }

    private static void add(PackageManager pm, LinkedHashMap<String, AppEntry> map,
                            String category, String self, boolean leanback) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(i, 0);
        } catch (RuntimeException e) {
            return;
        }
        for (ResolveInfo r : found) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null || ai.packageName.equals(self) || map.containsKey(ai.packageName)) continue;
            String label = String.valueOf(r.loadLabel(pm)).trim();
            long installed = 0;
            try {
                installed = pm.getPackageInfo(ai.packageName, 0).firstInstallTime;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            ApplicationInfo app = ai.applicationInfo;
            boolean system = app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            map.put(ai.packageName, new AppEntry(ai.packageName, ai.name, label, kindOf(app),
                    leanback, system, installed));
        }
    }

    private static String kindOf(ApplicationInfo ai) {
        if (ai == null || Build.VERSION.SDK_INT < 26) return "App";
        switch (ai.category) {
            case ApplicationInfo.CATEGORY_VIDEO: return "Video";
            case ApplicationInfo.CATEGORY_AUDIO: return "Music";
            case ApplicationInfo.CATEGORY_GAME: return "Game";
            case ApplicationInfo.CATEGORY_IMAGE: return "Photos";
            case ApplicationInfo.CATEGORY_NEWS: return "News";
            case ApplicationInfo.CATEGORY_SOCIAL: return "Social";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "Productivity";
            case ApplicationInfo.CATEGORY_MAPS: return "Maps";
            default: return "App";
        }
    }

    /** HDMI and other pass-through inputs the TV exposes (empty on streaming sticks). */
    static List<InputEntry> inputs(Context ctx) {
        ArrayList<InputEntry> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
                CharSequence label = info.loadCustomLabel(ctx);
                if (label == null || label.length() == 0) label = info.loadLabel(ctx);
                out.add(new InputEntry(info.getId(), String.valueOf(label)));
            }
        } catch (Throwable ignored) {
            // Not a TV with an input framework.
        }
        return out;
    }

    static void runNetwork(final Runnable r) {
        NET.execute(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }
        });
    }

    /** Downloads a remote image (e.g. a series poster) at exactly tw x th, cached like icons. */
    static void requestRemote(final String key, final String url, final int tw, final int th, Runnable done) {
        ArrayList<Runnable> waiting = WAITING.get(key);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(done);
        WAITING.put(key, waiting);
        runNetwork(new Runnable() {
            @Override
            public void run() {
                Bitmap out = null;
                try {
                    out = Net.image(url, tw, th);
                } catch (Throwable ignored) {
                }
                final Bitmap result = out;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) CACHE.put(key, result);
                        ArrayList<Runnable> cbs = WAITING.remove(key);
                        if (cbs != null) for (Runnable r : cbs) r.run();
                    }
                });
            }
        });
    }

    /** Full-screen scene images for the top shelf: at most 4 kept (about 1 MB each). */
    static final LruCache<String, Bitmap> BACKS = new LruCache<>(4);
    private static final java.util.HashSet<String> NO_BACK = new java.util.HashSet<>();

    /** Finds and downloads a show's background image from TVmaze at 960x540. */
    static void requestBackdrop(final int showId, Runnable done) {
        final String key = "bg:" + showId;
        if (NO_BACK.contains(key)) return;
        ArrayList<Runnable> waiting = WAITING.get(key);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(done);
        WAITING.put(key, waiting);
        runNetwork(new Runnable() {
            @Override
            public void run() {
                Bitmap out = null;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(
                            Net.get("https://api.tvmaze.com/shows/" + showId + "/images"));
                    String url = null;
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        if (!"background".equals(o.optString("type"))) continue;
                        org.json.JSONObject res = o.optJSONObject("resolutions");
                        org.json.JSONObject orig = res == null ? null : res.optJSONObject("original");
                        if (orig != null) {
                            url = orig.optString("url").replace("http://", "https://");
                            break;
                        }
                    }
                    if (url != null && !url.isEmpty()) out = Net.image(url, 960, 540);
                } catch (Throwable ignored) {
                }
                final Bitmap result = out;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) BACKS.put(key, result);
                        else NO_BACK.add(key);
                        ArrayList<Runnable> cbs = WAITING.remove(key);
                        if (cbs != null && result != null) for (Runnable r : cbs) r.run();
                    }
                });
            }
        });
    }

    static Bitmap cached(AppEntry a) {
        return CACHE.get(a.pkg);
    }

    /** Decodes a banner (or icon) at the exact size it is drawn; banners as RGB_565. */
    static void request(Context ctx, final AppEntry a, final int tw, final int th,
                        final int iconPx, Runnable done) {
        ArrayList<Runnable> waiting = WAITING.get(a.pkg);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(done);
        WAITING.put(a.pkg, waiting);
        final PackageManager pm = ctx.getApplicationContext().getPackageManager();
        runBackground(new Runnable() {
            @Override
            public void run() {
                Bitmap out = null;
                boolean banner = false;
                try {
                    ActivityInfo ai = pm.getActivityInfo(a.cn, 0);
                    // TV banners live in different places depending on the app and the TV maker.
                    Drawable d = null;
                    String src = "none";
                    try { d = pm.getActivityBanner(a.cn); src = "activityBanner"; } catch (Throwable ignored) { }
                    if (d == null) try { d = pm.getApplicationBanner(a.pkg); src = "appBanner"; } catch (Throwable ignored) { }
                    if (d == null) try {
                        Intent li = pm.getLeanbackLaunchIntentForPackage(a.pkg);
                        if (li != null) { d = pm.getActivityBanner(li); src = "leanbackBanner"; }
                    } catch (Throwable ignored) { }
                    if (d == null) { d = ai.loadBanner(pm); src = "loadBanner"; }
                    if (d == null) { d = ai.loadIcon(pm); src = "icon"; }
                    if (d != null) {
                        out = Art.card(d, tw, th, a.pkg, src);
                        banner = out != null;
                    }
                } catch (Throwable ignored) {
                    // Removed mid-load or a broken drawable: tile shows a monogram.
                }
                final Bitmap result = out;
                final boolean isBanner = banner;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            a.banner = isBanner;
                            CACHE.put(a.pkg, result);
                        }
                        ArrayList<Runnable> cbs = WAITING.remove(a.pkg);
                        if (cbs != null) for (Runnable r : cbs) r.run();
                    }
                });
            }
        });
    }
}
