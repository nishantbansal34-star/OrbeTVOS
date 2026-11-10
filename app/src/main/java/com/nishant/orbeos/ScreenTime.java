package com.nishant.orbeos;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Watch time per app, from Android's own usage records. Everything stays on the TV.
 * Needs "Usage access" for ORBÉ (granted once by the update script, or in Settings).
 */
final class ScreenTime {
    private static final String[] IGNORE = {
            "com.android.systemui", "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher", "com.google.android.tungsten.setupwraith", "android",
            "com.android.tv.settings", "com.android.settings", "com.google.android.tv.settings",
            "com.google.android.katniss", "com.google.android.apps.tv.dreamx", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
    };

    private ScreenTime() {}

    @SuppressWarnings("deprecation")
    static boolean allowed(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean ignored(String pkg, String self) {
        if (pkg == null || pkg.equals(self)) return true;
        for (String s : IGNORE) if (s.equals(pkg)) return true;
        return false;
    }

    /** Foreground time today per package, largest first. */
    @SuppressWarnings("deprecation")
    static LinkedHashMap<String, Long> today(Context c) {
        HashMap<String, Long> total = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis(), end = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(start, end);
            HashMap<String, Long> open = new HashMap<>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int type = e.getEventType();
                String p = e.getPackageName();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (!open.containsKey(p)) open.put(p, e.getTimeStamp());
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    Long s = open.remove(p);
                    if (s != null) add(total, p, e.getTimeStamp() - s);
                }
            }
            for (Map.Entry<String, Long> o : open.entrySet()) {
                // Still open (e.g. paused under the home screen): count up to now, at most 3 h.
                add(total, o.getKey(), Math.min(end - o.getValue(), 3 * 3600_000L));
            }
        } catch (Throwable ignored) {
        }
        return sorted(total, c.getPackageName());
    }

    /** Foreground time over the last 7 days per package, largest first. */
    static LinkedHashMap<String, Long> week(Context c) {
        HashMap<String, Long> total = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            List<UsageStats> list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 7L * 86400_000L, end);
            if (list != null) for (UsageStats u : list) add(total, u.getPackageName(), u.getTotalTimeInForeground());
        } catch (Throwable ignored) {
        }
        return sorted(total, c.getPackageName());
    }

    private static void add(HashMap<String, Long> m, String p, long ms) {
        if (ms <= 0) return;
        Long cur = m.get(p);
        m.put(p, (cur == null ? 0 : cur) + ms);
    }

    private static LinkedHashMap<String, Long> sorted(HashMap<String, Long> m, String self) {
        ArrayList<Map.Entry<String, Long>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : l) {
            if (ignored(e.getKey(), self) || e.getValue() < 60_000L) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    static long sum(Map<String, Long> m) {
        long t = 0;
        for (long v : m.values()) t += v;
        return t;
    }

    static String fmt(long ms) {
        long m = ms / 60000;
        if (m < 1) return "0m";
        long h = m / 60;
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
    }
}
