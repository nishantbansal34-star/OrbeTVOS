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
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Hard cap on decoded artwork; evicted art is re-decoded on demand. */
    static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
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
                    Drawable d = ai.loadBanner(pm);
                    if (d != null) {
                        out = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
                        Canvas c = new Canvas(out);
                        c.drawColor(0xFF191C24);
                        d.setBounds(0, 0, tw, th);
                        d.draw(c);
                        banner = true;
                    } else {
                        d = ai.loadIcon(pm);
                        if (d != null) {
                            out = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(out);
                            d.setBounds(0, 0, iconPx, iconPx);
                            d.draw(c);
                        }
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
