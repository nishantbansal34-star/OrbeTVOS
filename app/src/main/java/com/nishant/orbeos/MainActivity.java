package com.nishant.orbeos;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * ORBÉ OS home, TV-shelf style: a full-screen "top shelf" showing the focused app's
 * artwork, a glass dock of favourite apps, and optional rows below. One Activity,
 * built in code; pages and panels are created lazily and released under memory pressure.
 */
public final class MainActivity extends Activity {

    // ------------------------------------------------------------------ constants

    private static final String[] DEFAULT_FAVS = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tv",
            "in.startv.hotstar",
            "com.jio.media.stb.ondemand",
            "com.spotify.tv.android",
            "com.plexapp.android",
            "com.google.android.youtube.tvmusic",
    };
    /** Rows below the dock. (My Apps always lives in the dock.) */
    private static final String[] SECTION_IDS = {"series", "recent", "inputs", "installed", "games", "music"};
    private static final String[] SECTION_NAMES = {"Series for you", "Recently Opened", "Inputs", "Recently Installed", "Games", "Music"};
    private static final String DEFAULT_OFF = "installed,games,music";

    private static final int PAGE_HOME = 0, PAGE_APPS = 1, PAGE_SEARCH = 2;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class Shortcut {
        final String label;
        final int icon;
        final String action;

        Shortcut(String label, int icon, String action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }
    }

    private static final Shortcut[] SHORTCUTS = {
            new Shortcut("Network & Internet", Icons.WIFI, Settings.ACTION_WIFI_SETTINGS),
            new Shortcut("Bluetooth & remotes", Icons.BLUETOOTH, Settings.ACTION_BLUETOOTH_SETTINGS),
            new Shortcut("Display & picture", Icons.DISPLAY, Settings.ACTION_DISPLAY_SETTINGS),
            new Shortcut("Sound", Icons.SOUND, Settings.ACTION_SOUND_SETTINGS),
            new Shortcut("Screen mirroring", Icons.CAST, Settings.ACTION_CAST_SETTINGS),
            new Shortcut("Screensaver", Icons.MOON, Settings.ACTION_DREAM_SETTINGS),
            new Shortcut("Accessibility", Icons.ACCESS, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            new Shortcut("Date & time", Icons.CLOCK, Settings.ACTION_DATE_SETTINGS),
            new Shortcut("Storage", Icons.STORAGE, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            new Shortcut("Apps & permissions", Icons.GRID, Settings.ACTION_APPLICATION_SETTINGS),
            new Shortcut("Privacy", Icons.SHIELD, Settings.ACTION_PRIVACY_SETTINGS),
            new Shortcut("About this TV", Icons.INFO, Settings.ACTION_DEVICE_INFO_SETTINGS),
            new Shortcut("All system settings", Icons.GEAR, Settings.ACTION_SETTINGS),
    };

    private interface TileOp {
        void run(TileView t);
    }

    // ------------------------------------------------------------------ state

    private SharedPreferences prefs;
    private float density;
    private int padH, gap, tileW, tileH, labelH, iconPx, shelfH, posterW, posterH;

    // Live widgets & recommendations
    private WidgetView weatherWidget, timeWidget;
    private Weather.Now weather;
    private long weatherTried;
    private List<Series.Show> series = new ArrayList<>();
    private long seriesAt;
    private boolean seriesLoading;
    private java.util.LinkedHashMap<String, Long> watchToday = new java.util.LinkedHashMap<>();
    private java.util.LinkedHashMap<String, Long> watchWeek = new java.util.LinkedHashMap<>();
    private boolean usageAllowed;

    private List<AppRepo.AppEntry> apps = new ArrayList<>();
    private List<AppRepo.InputEntry> inputs = new ArrayList<>();
    private final ArrayList<String> favs = new ArrayList<>();
    private final ArrayList<String> recents = new ArrayList<>();
    private final HashSet<String> hidden = new HashSet<>();
    private final ArrayList<String> sectionOrder = new ArrayList<>();
    private final HashSet<String> sectionOff = new HashSet<>();
    private boolean favsLoaded;
    private boolean homeDirty;
    private boolean firstFocusDone;

    private FrameLayout root;
    private BackdropView backdrop;
    private FrameLayout pages;
    private int currentPage = PAGE_HOME;
    private TextView clock;
    private IconButton netIcon;
    private String netLabel = "Offline";

    private View pageHome;
    private ScrollView homeScroll;
    private View hero;
    private TextView heroKind, heroTitle, heroLine;
    private LinearLayout dockRow;
    private HorizontalScrollView dockScroll;
    private LinearLayout sectionsBox;
    private TileView movingTile;

    private View pageApps;
    private GridView grid;
    private AppsAdapter adapter;
    private TileView gridSelected;
    private TextView appsCount;
    private TextView sortPill, hiddenPill;
    private final ArrayList<AppRepo.AppEntry> appsView = new ArrayList<>();
    private int sortMode;
    private boolean showHidden;

    private View pageSearch;
    private EditText searchBox;
    private LinearLayout resultsBox;
    private final Runnable searchRun = new Runnable() {
        @Override
        public void run() {
            runSearch();
        }
    };

    private View overlay;
    private View overlayReturn;
    private RowView movingSection;
    private LinearLayout sectionList;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());

    // ------------------------------------------------------------------ receivers

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            if (System.currentTimeMillis() - weatherTried > 30 * 60_000L) refreshWeather();
        }
    };

    private final BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateNet();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            reloadAll();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("orbe", MODE_PRIVATE);
        loadPrefs();
        computeSizes();
        Theme.reduceMotion = prefs.getBoolean("reduceMotion", false) || systemAnimationsOff();
        Theme.apply(prefs.getBoolean("light", false));
        buildUi();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        register(packageReceiver, pf);

        reloadAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, tf);
        register(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateClock();
        updateNet();
        forEachTile(root, new TileOp() {
            @Override
            public void run(TileView t) {
                t.ensureArt();
            }
        });
        refreshLive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregister(timeReceiver);
        unregister(netReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (homeDirty) {
            homeDirty = false;
            buildSections();
            if (pageApps != null) refreshAppsView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregister(packageReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeOverlay(false);
        endMove();
        showPage(PAGE_HOME);
        if (homeScroll != null) homeScroll.scrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) {
            closeOverlay(true);
        } else if (movingTile != null) {
            endMove();
        } else if (currentPage != PAGE_HOME) {
            showPage(PAGE_HOME);
            focusFirstTile();
        } else if (homeScroll != null && homeScroll.getScrollY() > 0) {
            homeScroll.smoothScrollTo(0, 0);
            focusFirstTile();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_INFO:
                if (overlay == null) openControlCenter();
                else closeOverlay(true);
                return true;
            case KeyEvent.KEYCODE_SEARCH:
                closeOverlay(false);
                showPage(PAGE_SEARCH);
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            closeOverlay(false);
            if (currentPage != PAGE_APPS && pageApps != null) {
                pages.removeView(pageApps);
                pageApps = null;
                grid = null;
                adapter = null;
                gridSelected = null;
            }
            if (currentPage != PAGE_SEARCH && pageSearch != null) {
                pages.removeView(pageSearch);
                pageSearch = null;
                searchBox = null;
                resultsBox = null;
            }
            AppRepo.CACHE.evictAll();
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            if (backdrop != null) backdrop.clear();
            forEachTile(root, new TileOp() {
                @Override
                public void run(TileView t) {
                    t.dropArt();
                }
            });
        }
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }

    private void unregister(BroadcastReceiver r) {
        try {
            unregisterReceiver(r);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean systemAnimationsOff() {
        try {
            return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ prefs

    private void loadPrefs() {
        hidden.clear();
        hidden.addAll(csv(prefs.getString("hidden", "")));
        recents.clear();
        recents.addAll(csv(prefs.getString("recent", "")));
        sectionOrder.clear();
        for (String s : csv(prefs.getString("sections", ""))) {
            if (indexOf(SECTION_IDS, s) >= 0 && !sectionOrder.contains(s)) sectionOrder.add(s);
        }
        for (String s : SECTION_IDS) {
            if (sectionOrder.contains(s)) continue;
            if (s.equals("series")) sectionOrder.add(0, s);   // new in 2.2: goes first
            else sectionOrder.add(s);
        }
        sectionOff.clear();
        sectionOff.addAll(csv(prefs.getString("sectionsOff", DEFAULT_OFF)));
        sortMode = prefs.getInt("sort", 0);
    }

    private static List<String> csv(String s) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private void putList(String key, java.util.Collection<String> list) {
        prefs.edit().putString(key, TextUtils.join(",", list)).apply();
    }

    private static int indexOf(String[] arr, String s) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(s)) return i;
        return -1;
    }

    private void loadFavs() {
        favs.clear();
        String saved = prefs.getString("fav", null);
        if (saved != null) {
            for (String p : csv(saved)) if (find(p) != null) favs.add(p);
        } else {
            for (String p : DEFAULT_FAVS) if (find(p) != null) favs.add(p);
            for (AppRepo.AppEntry a : apps) {
                if (favs.size() >= 8) break;
                if (!favs.contains(a.pkg) && !a.system) favs.add(a.pkg);
            }
            putList("fav", favs);
        }
        favsLoaded = true;
    }

    // ------------------------------------------------------------------ data

    private void reloadAll() {
        final Context ctx = getApplicationContext();
        AppRepo.runBackground(new Runnable() {
            @Override
            public void run() {
                final List<AppRepo.AppEntry> list = AppRepo.query(ctx);
                final List<AppRepo.InputEntry> ins = AppRepo.inputs(ctx);
                AppRepo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        apps = list;
                        inputs = ins;
                        loadFavs();
                        buildSections();
                        if (pageApps != null) refreshAppsView();
                    }
                });
            }
        });
    }

    private AppRepo.AppEntry find(String pkg) {
        for (AppRepo.AppEntry a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    // ------------------------------------------------------------------ sizes & helpers

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void computeSizes() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        density = dm.density;
        Theme.screenW = dm.widthPixels;
        Theme.screenH = dm.heightPixels;
        padH = dp(56);
        gap = dp(24);
        int contentW = dm.widthPixels - 2 * padH;
        tileW = (contentW - 4 * gap) / 5;
        tileH = tileW * 3 / 5;              // 5:3 icon cards
        labelH = dp(30);
        iconPx = Math.round(tileH * 0.52f);
        shelfH = Math.round(dm.heightPixels * 0.54f);
        posterW = Math.round(tileW * 0.62f);
        posterH = posterW * 3 / 2;
    }

    private void applyWallpaper() {
        BitmapDrawable d = new BitmapDrawable(getResources(), Theme.wallpaper);
        d.setFilterBitmap(true);
        d.setDither(true);
        getWindow().setBackgroundDrawable(d);
    }

    private TextView text(float sp, String family, int style, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, style));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private TextView pill(String label, View.OnClickListener click) {
        final TextView t = text(16, "sans-serif-medium", Typeface.NORMAL, Theme.text);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), 0, dp(22), 0);
        final Theme.Surface s = new Theme.Surface(Theme.ROLE_GLASS, dp(22), density);
        t.setBackground(s);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                s.setFocused(has);
                t.setTextColor(has ? Theme.onFocus : Theme.text);
                float sc = has ? 1.06f : 1f;
                t.animate().scaleX(sc).scaleY(sc).setDuration(Theme.dur(160)).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, dp(44));
        lp.leftMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private TileView newTile(boolean focusable) {
        return new TileView(this, tileW, tileH, labelH, iconPx, density, focusable);
    }

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH + labelH);
        lp.rightMargin = gap;
        return lp;
    }

    private static void forEachTile(View v, TileOp op) {
        if (v instanceof TileView) {
            op.run((TileView) v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) forEachTile(g.getChildAt(i), op);
        }
    }

    private static boolean isInside(View v, View parent) {
        while (v != null) {
            if (v == parent) return true;
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return false;
    }

    private HorizontalScrollView rowScroller(LinearLayout row) {
        HorizontalScrollView hsv = new RowScroller(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setSmoothScrollingEnabled(true);
        hsv.setClipToPadding(false);
        hsv.setClipChildren(false);
        // Room for the lifted card + its shadow.
        hsv.setPadding(padH, dp(16), padH, dp(4));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hsv.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        return hsv;
    }

    // ------------------------------------------------------------------ UI shell

    private void buildUi() {
        applyWallpaper();
        overlay = null;
        overlayReturn = null;
        movingTile = null;
        movingSection = null;
        pageApps = null;
        grid = null;
        adapter = null;
        gridSelected = null;
        pageSearch = null;
        searchBox = null;
        resultsBox = null;
        firstFocusDone = false;

        root = new FrameLayout(this);
        root.setClipChildren(false);
        backdrop = new BackdropView(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));

        pages = new FrameLayout(this);
        pages.setClipChildren(false);
        pageHome = buildHomePage();
        pages.addView(pageHome, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(pages, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(root);
        currentPage = PAGE_HOME;
        updateClock();
        updateNet();
        if (favsLoaded) buildSections();
    }

    /** Glass status pill, top-right: search · network · clock · Control Center · settings. */
    private View buildStatusPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(new Theme.Surface(Theme.ROLE_GLASS, dp(28), density));
        pill.setPadding(dp(6), dp(6), dp(6), dp(6));

        IconButton search = new IconButton(this, Icons.SEARCH, "Search", density, true);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(PAGE_SEARCH);
            }
        });
        pill.addView(search);

        netIcon = new IconButton(this, Icons.WIFI, "Network", density, false);
        pill.addView(netIcon);
        clock = text(19, "sans-serif-medium", Typeface.NORMAL, Theme.text);
        clock.setPadding(0, 0, dp(10), 0);
        pill.addView(clock);

        IconButton cc = new IconButton(this, Icons.SLIDERS, "Control Center", density, true);
        cc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openControlCenter();
            }
        });
        pill.addView(cc);
        IconButton gear = new IconButton(this, Icons.GEAR, "ORBÉ settings", density, true);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrbeSettings();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(WRAP, WRAP);
        glp.leftMargin = dp(4);
        pill.addView(gear, glp);
        return pill;
    }

    private void showPage(int i) {
        View target;
        if (i == PAGE_APPS) {
            if (pageApps == null) {
                pageApps = buildAppsPage();
                pages.addView(pageApps, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            target = pageApps;
        } else if (i == PAGE_SEARCH) {
            if (pageSearch == null) {
                pageSearch = buildSearchPage();
                pages.addView(pageSearch, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            target = pageSearch;
        } else {
            target = pageHome;
        }
        boolean changed = i != currentPage || target.getVisibility() != View.VISIBLE;
        currentPage = i;
        View[] all = {pageHome, pageApps, pageSearch};
        for (View p : all) if (p != null) p.setVisibility(p == target ? View.VISIBLE : View.GONE);
        if (i != PAGE_HOME) backdrop.show(null);
        if (changed && Theme.dur(160) > 0) {
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(Theme.dur(160)).start();
        }
        if (i == PAGE_APPS && grid != null) grid.requestFocus();
        if (i == PAGE_SEARCH && searchBox != null) searchBox.requestFocus();
    }

    private void updateClock() {
        if (clock != null) clock.setText(DateFormat.getTimeFormat(this).format(new Date()));
    }

    @SuppressWarnings("deprecation")
    private void updateNet() {
        if (netIcon == null) return;
        int icon = Icons.OFFLINE;
        netLabel = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    icon = Icons.ETHERNET;
                    netLabel = "Ethernet";
                } else {
                    icon = Icons.WIFI;
                    netLabel = "Wi-Fi";
                }
            }
        } catch (SecurityException ignored) {
        }
        netIcon.setIcon(icon);
        netIcon.setContentDescription(netLabel);
    }

    // ------------------------------------------------------------------ home page

    private View buildHomePage() {
        homeScroll = new ScrollView(this);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeScroll.setSmoothScrollingEnabled(true);
        homeScroll.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(0, 0, 0, dp(48));

        // Top shelf: artwork shows through from the backdrop behind.
        FrameLayout shelf = new FrameLayout(this);
        shelf.setClipChildren(false);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        plp.setMargins(0, dp(28), padH, 0);
        shelf.addView(buildStatusPill(), plp);

        LinearLayout heroBox = new LinearLayout(this);
        heroBox.setOrientation(LinearLayout.VERTICAL);
        heroKind = text(15, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        heroKind.setAllCaps(true);
        heroKind.setLetterSpacing(0.2f);
        heroTitle = text(56, "sans-serif", Typeface.BOLD, Theme.text);
        heroTitle.setShadowLayer(dp(12), 0, dp(2), 0x66000000);
        heroLine = text(17, "sans-serif", Typeface.NORMAL, Theme.text2);
        heroBox.addView(heroKind, new LinearLayout.LayoutParams(dp(450), dp(24)));
        heroBox.addView(heroTitle, new LinearLayout.LayoutParams(dp(450), dp(76)));
        heroBox.addView(heroLine, new LinearLayout.LayoutParams(dp(450), dp(26)));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
        hlp.setMargins(padH, 0, 0, dp(22));
        shelf.addView(heroBox, hlp);
        hero = heroBox;

        // Live widgets, bottom-right of the shelf.
        LinearLayout widgets = new LinearLayout(this);
        widgets.setOrientation(LinearLayout.HORIZONTAL);
        widgets.setClipChildren(false);
        weatherWidget = new WidgetView(this, 186, 118, density);
        weatherWidget.headIcon = Icons.SUN;
        weatherWidget.set(prefs.getString("wCity", "Aligarh"), "--°", "Getting weather…");
        weatherWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askCity();
            }
        });
        timeWidget = new WidgetView(this, 186, 118, density);
        timeWidget.headIcon = Icons.HOURGLASS;
        timeWidget.set("Watch time today", "--", "");
        timeWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWatchTime();
            }
        });
        View.OnFocusChangeListener toTop = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (has) scrollHomeTop();
            }
        };
        weatherWidget.setOnFocusChangeListener(toTop);
        timeWidget.setOnFocusChangeListener(toTop);
        widgets.addView(weatherWidget);
        LinearLayout.LayoutParams twl = new LinearLayout.LayoutParams(WRAP, WRAP);
        twl.leftMargin = dp(12);
        widgets.addView(timeWidget, twl);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        wlp.setMargins(0, 0, padH, dp(24));
        shelf.addView(widgets, wlp);
        if (weather != null) showWeather();
        showWatchTime();

        col.addView(shelf, new LinearLayout.LayoutParams(MATCH, shelfH));

        // Glass dock with My Apps.
        FrameLayout dock = new FrameLayout(this);
        dock.setClipChildren(false);
        View glass = new View(this);
        glass.setBackground(new Theme.Surface(Theme.ROLE_GLASS, dp(32), density));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(MATCH, MATCH);
        gl.setMargins(padH - dp(20), dp(6), padH - dp(20), 0);
        dock.addView(glass, gl);
        dockRow = new LinearLayout(this);
        dockScroll = rowScroller(dockRow);
        dockScroll.setPadding(padH, dp(22), padH, dp(14));
        dock.addView(dockScroll, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(dock, new LinearLayout.LayoutParams(MATCH, WRAP));

        sectionsBox = new LinearLayout(this);
        sectionsBox.setOrientation(LinearLayout.VERTICAL);
        sectionsBox.setClipChildren(false);
        col.addView(sectionsBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        homeScroll.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));

        // Scrolling into the rows fades the shelf out, like a TV top shelf.
        homeScroll.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (homeScroll == null) return;
                float t = Math.min(1f, homeScroll.getScrollY() / (float) Math.max(1, shelfH));
                backdrop.setAlpha(1f - 0.75f * t);
                hero.setAlpha(1f - t);
            }
        });

        setHero("Welcome", "ORBÉ OS", "Entertainment, without the clutter.");
        return homeScroll;
    }

    private void setHero(String kind, String title, String line) {
        if (heroTitle == null) return;
        heroKind.setText(kind);
        heroTitle.setText(title);
        heroLine.setText(line);
        int d = Theme.dur(200);
        if (d > 0) {
            heroTitle.setAlpha(0.3f);
            heroTitle.setTranslationY(dp(6));
            heroTitle.animate().alpha(1f).translationY(0f).setDuration(d).setInterpolator(TileView.EASE).start();
        }
    }

    private String sectionName(String id) {
        int i = indexOf(SECTION_IDS, id);
        return i >= 0 ? SECTION_NAMES[i] : id;
    }

    private void buildSections() {
        if (sectionsBox == null || !favsLoaded) return;
        View f = getCurrentFocus();
        String focusTag = f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
        boolean focusWasHere = f != null && (isInside(f, sectionsBox) || isInside(f, dockRow));

        endMove();
        dockRow.removeAllViews();
        for (String pkg : favs) {
            AppRepo.AppEntry a = find(pkg);
            if (a != null && !hidden.contains(pkg)) dockRow.addView(appTile(a, "apps"), tileParams());
        }
        dockRow.addView(iconTile("All Apps", Icons.GRID, "apps:+", "Library", "All Apps",
                "Every app on this TV  ·  Hold OK on any app to pin it here", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPage(PAGE_APPS);
                    }
                }), tileParams());

        sectionsBox.removeAllViews();
        for (String id : sectionOrder) {
            if (sectionOff.contains(id)) continue;
            List<View> tiles = tilesFor(id);
            if (tiles.isEmpty()) continue;
            addSection(sectionsBox, sectionName(id), tiles);
        }

        if (overlay != null || currentPage != PAGE_HOME) return;
        View back = null;
        if (focusTag != null) {
            back = dockRow.findViewWithTag(focusTag);
            if (back == null) back = sectionsBox.findViewWithTag(focusTag);
        }
        if (back != null) {
            back.requestFocus();
        } else if (focusWasHere || !firstFocusDone || getCurrentFocus() == null) {
            focusFirstTile();
        }
        firstFocusDone = true;
    }

    private LinearLayout addSection(LinearLayout box, String title, List<View> tiles) {
        TextView t = text(18, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        t.setText(title);
        t.setPadding(padH, dp(18), 0, 0);
        box.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout row = new LinearLayout(this);
        HorizontalScrollView hsv = rowScroller(row);
        for (View v : tiles) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = gap;
            row.addView(v, lp);
        }
        box.addView(hsv, new LinearLayout.LayoutParams(MATCH, WRAP));
        return row;
    }

    private List<View> tilesFor(String id) {
        ArrayList<View> out = new ArrayList<>();
        switch (id) {
            case "series":
                for (Series.Show sh : series) out.add(posterTile(sh));
                break;
            case "recent": {
                int n = 0;
                for (String pkg : recents) {
                    AppRepo.AppEntry a = find(pkg);
                    if (a == null || hidden.contains(pkg)) continue;
                    out.add(appTile(a, "recent"));
                    if (++n >= 10) break;
                }
                break;
            }
            case "inputs":
                for (final AppRepo.InputEntry in : inputs) {
                    out.add(iconTile(in.label, Icons.INPUT, "inputs:" + in.id, "Input", in.label,
                            "Switch to this input", new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    switchInput(in);
                                }
                            }));
                }
                break;
            case "installed": {
                ArrayList<AppRepo.AppEntry> list = new ArrayList<>();
                for (AppRepo.AppEntry a : apps) if (!a.system && !hidden.contains(a.pkg)) list.add(a);
                Collections.sort(list, new Comparator<AppRepo.AppEntry>() {
                    @Override
                    public int compare(AppRepo.AppEntry x, AppRepo.AppEntry y) {
                        return Long.compare(y.installed, x.installed);
                    }
                });
                for (int i = 0; i < list.size() && i < 10; i++) out.add(appTile(list.get(i), "installed"));
                break;
            }
            case "games":
            case "music": {
                String kind = id.equals("games") ? "Game" : "Music";
                for (AppRepo.AppEntry a : apps) {
                    if (a.kind.equals(kind) && !hidden.contains(a.pkg)) out.add(appTile(a, id));
                }
                break;
            }
            default:
                break;
        }
        return out;
    }

    private TileView appTile(final AppRepo.AppEntry a, final String section) {
        final TileView t = newTile(true);
        t.bind(a);
        t.setTag(section + ":" + a.pkg);
        t.artReady = new Runnable() {
            @Override
            public void run() {
                if (currentPage == PAGE_HOME) backdrop.show(t.art());
            }
        };
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(a);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openAppMenu(a, t, section.equals("apps"));
                return true;
            }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (!has) return;
                setHero(kindLabel(a), a.label, "OK to open  ·  Hold OK for options");
                if (currentPage == PAGE_HOME) backdrop.show(t.art());
                if (section.equals("apps")) scrollHomeTop();
            }
        });
        if (section.equals("apps")) {
            t.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    return onMoveKey(t, code, e);
                }
            });
        }
        return t;
    }

    private TileView iconTile(String label, int icon, String tag, final String heroK, final String heroT,
                              final String heroL, View.OnClickListener click) {
        TileView t = newTile(true);
        t.bindIcon(label, icon);
        t.setTag(tag);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (!has) return;
                setHero(heroK, heroT, heroL);
                if (currentPage == PAGE_HOME) backdrop.show(null);
                if (v.getParent() == dockRow) scrollHomeTop();
            }
        });
        return t;
    }

    private TileView posterTile(final Series.Show sh) {
        final TileView t = new TileView(this, posterW, posterH, labelH, iconPx, density, true);
        t.bindRemote("poster:" + sh.id, sh.image, sh.name);
        t.setTag("series:" + sh.id);
        final AppRepo.AppEntry app = sh.pkg != null ? find(sh.pkg) : null;
        final String where = app != null ? app.label : (sh.channel.isEmpty() ? "Streaming" : sh.channel);
        t.artReady = new Runnable() {
            @Override
            public void run() {
                if (currentPage == PAGE_HOME) backdrop.show(t.art());
            }
        };
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (!has) return;
                String meta = sh.episode + (sh.rating > 0 ? "  ·  ★ " + sh.rating : "")
                        + (app != null ? "  ·  OK to find it" : "  ·  Not on your apps");
                setHero(where + (sh.genres.isEmpty() ? "" : "  ·  " + sh.genres), sh.name, meta);
                if (currentPage == PAGE_HOME) backdrop.show(t.art());
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSearch(sh.name);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (app != null) launchApp(app);
                else systemSearch(sh.name);
                return true;
            }
        });
        return t;
    }

    private String kindLabel(AppRepo.AppEntry a) {
        Long ms = watchToday.get(a.pkg);
        String watched = ms != null && ms >= 60_000L ? "Watched " + ScreenTime.fmt(ms) + " today" : "";
        String kind = "App".equals(a.kind) ? "" : a.kind;
        if (kind.isEmpty()) return watched;
        return watched.isEmpty() ? kind : kind + "  ·  " + watched;
    }

    private String dateShort() {
        return new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date());
    }

    /** Weather every 30 min, watch time on every return home, series every 6 h. */
    private void refreshLive() {
        if (System.currentTimeMillis() - weatherTried > 30 * 60_000L || weather == null) refreshWeather();
        refreshWatchTime();
    }

    private void refreshWeather() {
        weatherTried = System.currentTimeMillis();
        final double lat = Double.longBitsToDouble(prefs.getLong("wLat", Double.doubleToLongBits(27.8974)));
        final double lon = Double.longBitsToDouble(prefs.getLong("wLon", Double.doubleToLongBits(78.0880)));
        final String city = prefs.getString("wCity", "Aligarh");
        AppRepo.runNetwork(new Runnable() {
            @Override
            public void run() {
                Weather.Now n = null;
                try {
                    n = Weather.fetch(lat, lon, city);
                } catch (Throwable ignored) {
                }
                final Weather.Now res = n;
                AppRepo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (res != null) {
                            weather = res;
                            showWeather();
                        } else if (weather == null && weatherWidget != null) {
                            weatherWidget.set(city, "--°", "Weather unavailable");
                        }
                    }
                });
            }
        });
    }

    private void showWeather() {
        if (weatherWidget == null || weather == null) return;
        weatherWidget.sideIcon = Weather.icon(weather.code, weather.day);
        weatherWidget.headIcon = Icons.SUN;
        weatherWidget.set(weather.city, Math.round(weather.temp) + "°",
                Weather.describe(weather.code) + "  ·  H " + Math.round(weather.hi) + "°  L " + Math.round(weather.lo) + "°");
    }

    private void refreshWatchTime() {
        final Context ctx = getApplicationContext();
        AppRepo.runBackground(new Runnable() {
            @Override
            public void run() {
                final boolean ok = ScreenTime.allowed(ctx);
                final java.util.LinkedHashMap<String, Long> t = ok ? ScreenTime.today(ctx) : new java.util.LinkedHashMap<String, Long>();
                final java.util.LinkedHashMap<String, Long> w = ok ? ScreenTime.week(ctx) : new java.util.LinkedHashMap<String, Long>();
                AppRepo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        usageAllowed = ok;
                        watchToday = t;
                        watchWeek = w;
                        showWatchTime();
                        if (series.isEmpty() || System.currentTimeMillis() - seriesAt > 6 * 3600_000L) refreshSeries();
                    }
                });
            }
        });
    }

    private void showWatchTime() {
        if (timeWidget == null) return;
        if (!usageAllowed) {
            timeWidget.bars = null;
            timeWidget.set("Watch time today", "—", "Press OK to turn on");
            return;
        }
        long total = ScreenTime.sum(watchToday);
        float[] bars = new float[Math.min(3, watchToday.size())];
        StringBuilder top = new StringBuilder();
        int i = 0;
        for (java.util.Map.Entry<String, Long> e : watchToday.entrySet()) {
            if (i >= 3) break;
            bars[i] = total > 0 ? e.getValue() / (float) total : 0f;
            if (i < 2) {
                AppRepo.AppEntry a = find(e.getKey());
                if (top.length() > 0) top.append("  ·  ");
                top.append(a != null ? a.label : e.getKey()).append(' ').append(ScreenTime.fmt(e.getValue()));
            }
            i++;
        }
        timeWidget.bars = bars;
        timeWidget.set("Watch time today", ScreenTime.fmt(total), top.length() > 0 ? top.toString() : "Nothing watched yet");
    }

    private void refreshSeries() {
        if (seriesLoading || apps.isEmpty()) return;
        seriesLoading = true;
        final List<AppRepo.AppEntry> appsNow = apps;
        final java.util.LinkedHashMap<String, Long> week = watchWeek;
        AppRepo.runNetwork(new Runnable() {
            @Override
            public void run() {
                List<Series.Show> got = null;
                try {
                    got = Series.fetch(appsNow, week);
                } catch (Throwable ignored) {
                }
                final List<Series.Show> res = got;
                AppRepo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        seriesLoading = false;
                        if (res == null || res.isEmpty()) return;
                        series = res;
                        seriesAt = System.currentTimeMillis();
                        buildSections();
                    }
                });
            }
        });
    }

    private void askCity() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint("City, e.g. Aligarh");
        in.setText(prefs.getString("wCity", "Aligarh"));
        in.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Weather city")
                .setView(in)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        final String name = in.getText().toString().trim();
                        if (name.isEmpty()) return;
                        AppRepo.runNetwork(new Runnable() {
                            @Override
                            public void run() {
                                String[] g = null;
                                try {
                                    g = Weather.geocode(name);
                                } catch (Throwable ignored) {
                                }
                                final String[] res = g;
                                AppRepo.MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (res == null) {
                                            Toast.makeText(MainActivity.this, "Couldn't find “" + name + "”", Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        prefs.edit().putString("wCity", res[0])
                                                .putLong("wLat", Double.doubleToLongBits(Double.parseDouble(res[1])))
                                                .putLong("wLon", Double.doubleToLongBits(Double.parseDouble(res[2]))).apply();
                                        refreshWeather();
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Watch-time report: today per app, and the last 7 days. */
    private void openWatchTime() {
        View ret = getCurrentFocus();
        LinearLayout p = panel(Theme.ROLE_GLASS, dp(460), true);
        TextView title = text(28, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("Watch time");
        title.setPadding(dp(16), 0, 0, dp(2));
        p.addView(title);
        LinearLayout list = new LinearLayout(this);
        View first;
        if (!usageAllowed) {
            TextView t = text(16, "sans-serif", Typeface.NORMAL, Theme.text2);
            t.setSingleLine(false);
            t.setText("ORBÉ needs Usage access to show how long you watch each app. Nothing leaves your TV.");
            t.setPadding(dp(16), dp(8), dp(16), dp(12));
            list.addView(t);
            RowView allow = row(Icons.SHIELD, "Turn on Usage access", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeOverlay(false);
                    openSetting(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                }
            });
            list.addView(allow);
            first = allow;
        } else {
            long today = ScreenTime.sum(watchToday), week = ScreenTime.sum(watchWeek);
            TextView sub = text(15, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
            sub.setText("Today " + ScreenTime.fmt(today) + "   ·   Last 7 days " + ScreenTime.fmt(week)
                    + "   ·   Daily avg " + ScreenTime.fmt(week / 7));
            sub.setPadding(dp(16), dp(2), 0, dp(4));
            p.addView(sub);
            first = null;
            list.addView(label("Today"));
            first = addUsageRows(list, watchToday, 8);
            list.addView(label("Last 7 days"));
            View w = addUsageRows(list, watchWeek, 8);
            if (first == null) first = w;
            if (first == null) {
                RowView none = row(Icons.TV, "Nothing watched yet", null, null);
                list.addView(none);
                first = none;
            }
        }
        p.addView(scrollList(list));
        showOverlay(p, ret, first);
    }

    private View addUsageRows(LinearLayout list, java.util.Map<String, Long> m, int max) {
        long top = 0;
        for (long v : m.values()) top = Math.max(top, v);
        View first = null;
        int n = 0;
        for (java.util.Map.Entry<String, Long> e : m.entrySet()) {
            if (n++ >= max) break;
            final AppRepo.AppEntry a = find(e.getKey());
            RowView r = row(0, a != null ? a.label : e.getKey(), ScreenTime.fmt(e.getValue()), a == null ? null
                    : new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeOverlay(false);
                    launchApp(a);
                }
            });
            r.level = top > 0 ? e.getValue() / (float) top : 0f;
            list.addView(r);
            if (first == null) first = r;
        }
        return first;
    }

    private void scrollHomeTop() {
        if (homeScroll == null || homeScroll.getScrollY() == 0) return;
        // After the focus system's own scroll has run, bring the whole shelf back.
        homeScroll.post(new Runnable() {
            @Override
            public void run() {
                homeScroll.smoothScrollTo(0, 0);
            }
        });
    }

    private void focusFirstTile() {
        if (dockRow == null || dockRow.getChildCount() == 0) return;
        dockScroll.scrollTo(0, 0);
        dockRow.getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------------ rearranging My Apps

    private void startMove(TileView t) {
        endMove();
        movingTile = t;
        t.setMoving(true);
        setHero("Rearrange", t.app != null ? t.app.label : "", "◀ ▶ to move  ·  OK when done");
    }

    private void endMove() {
        if (movingTile == null) return;
        movingTile.setMoving(false);
        movingTile = null;
        if (dockRow == null) return;
        ArrayList<String> order = new ArrayList<>();
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View v = dockRow.getChildAt(i);
            if (v instanceof TileView && ((TileView) v).app != null) order.add(((TileView) v).app.pkg);
        }
        for (String p : favs) if (!order.contains(p)) order.add(p);
        favs.clear();
        favs.addAll(order);
        putList("fav", favs);
    }

    private boolean onMoveKey(TileView t, int code, KeyEvent e) {
        if (movingTile != t || dockRow == null) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok && e.getRepeatCount() > 0) return true;
        int i = dockRow.indexOfChild(t);
        int lastApp = dockRow.getChildCount() - 2;     // last child is "All Apps"
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && i > 0) {
            swapChild(dockRow, i, i - 1);
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i < lastApp) {
            swapChild(dockRow, i, i + 1);
        } else if (ok || code == KeyEvent.KEYCODE_BACK) {
            endMove();
            if (t.app != null) setHero(t.app.kind, t.app.label, "OK to open  ·  Hold OK for options");
        }
        return true;
    }

    private static void swapChild(LinearLayout row, int from, int to) {
        View v = row.getChildAt(from);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        row.removeViewAt(from);
        row.addView(v, to, lp);
        v.requestFocus();
    }

    // ------------------------------------------------------------------ apps page

    private View buildAppsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(40), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(padH, 0, padH, 0);
        TextView title = text(40, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("Apps");
        appsCount = text(16, "sans-serif", Typeface.NORMAL, Theme.text2);
        appsCount.setPadding(dp(16), dp(10), 0, 0);
        header.addView(title);
        header.addView(appsCount);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        sortPill = pill(sortLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = (sortMode + 1) % 3;
                prefs.edit().putInt("sort", sortMode).apply();
                sortPill.setText(sortLabel());
                refreshAppsView();
            }
        });
        hiddenPill = pill(showHidden ? "Hide hidden apps" : "Show hidden apps", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHidden = !showHidden;
                hiddenPill.setText(showHidden ? "Hide hidden apps" : "Show hidden apps");
                refreshAppsView();
            }
        });
        header.addView(sortPill);
        header.addView(hiddenPill);
        page.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setColumnWidth(tileW);
        grid.setStretchMode(GridView.NO_STRETCH);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(dp(10));
        grid.setPadding(padH, dp(28), padH, dp(48));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setSelector(new ColorDrawable(0));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setCacheColorHint(0);
        adapter = new AppsAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (gridSelected != null && gridSelected != view) gridSelected.setLook(false, true);
                gridSelected = (TileView) view;
                if (gridSelected != null && grid.hasFocus()) gridSelected.setLook(true, true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (gridSelected != null) gridSelected.setLook(false, true);
                gridSelected = null;
            }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (gridSelected != null) gridSelected.setLook(has, true);
            }
        });
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < appsView.size()) launchApp(appsView.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < appsView.size()) openAppMenu(appsView.get(pos), grid, false);
                return true;
            }
        });
        page.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        refreshAppsView();
        return page;
    }

    private String sortLabel() {
        return sortMode == 0 ? "Sort: A–Z" : sortMode == 1 ? "Sort: Recently used" : "Sort: Recently installed";
    }

    private void refreshAppsView() {
        appsView.clear();
        for (AppRepo.AppEntry a : apps) if (showHidden || !hidden.contains(a.pkg)) appsView.add(a);
        if (sortMode == 1) {
            Collections.sort(appsView, new Comparator<AppRepo.AppEntry>() {
                @Override
                public int compare(AppRepo.AppEntry x, AppRepo.AppEntry y) {
                    int ix = recents.indexOf(x.pkg), iy = recents.indexOf(y.pkg);
                    if (ix < 0) ix = Integer.MAX_VALUE;
                    if (iy < 0) iy = Integer.MAX_VALUE;
                    if (ix != iy) return ix < iy ? -1 : 1;
                    return x.label.compareToIgnoreCase(y.label);
                }
            });
        } else if (sortMode == 2) {
            Collections.sort(appsView, new Comparator<AppRepo.AppEntry>() {
                @Override
                public int compare(AppRepo.AppEntry x, AppRepo.AppEntry y) {
                    return Long.compare(y.installed, x.installed);
                }
            });
        }
        if (appsCount != null) {
            int nHidden = 0;
            for (AppRepo.AppEntry a : apps) if (hidden.contains(a.pkg)) nHidden++;
            appsCount.setText(apps.size() + " apps" + (nHidden > 0 ? "  ·  " + nHidden + " hidden" : ""));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return appsView.size(); }
        @Override public Object getItem(int i) { return appsView.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            TileView t = convert instanceof TileView ? (TileView) convert : null;
            if (t == null) {
                t = newTile(false);
                t.setLayoutParams(new AbsListView.LayoutParams(tileW, tileH + labelH));
            }
            AppRepo.AppEntry a = appsView.get(pos);
            t.dim = hidden.contains(a.pkg);
            t.bind(a);
            boolean sel = grid != null && grid.hasFocus() && grid.getSelectedItemPosition() == pos;
            t.setLook(sel, false);
            if (sel) gridSelected = t;
            return t;
        }
    }

    // ------------------------------------------------------------------ search page

    private View buildSearchPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(padH, dp(40), padH, 0);
        page.setClipChildren(false);

        TextView title = text(40, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("Search");
        title.setPadding(0, 0, 0, dp(20));
        page.addView(title);

        searchBox = new EditText(this);
        final Theme.Surface s = new Theme.Surface(Theme.ROLE_GLASS, dp(28), density);
        searchBox.setBackground(s);
        searchBox.setTextColor(Theme.text);
        searchBox.setHintTextColor(Theme.text2);
        searchBox.setHint("Apps, settings, films and shows");
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(dp(26), 0, dp(26), 0);
        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                s.setFocused(has);
                searchBox.setTextColor(has ? Theme.onFocus : Theme.text);
            }
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable e) {
                AppRepo.MAIN.removeCallbacks(searchRun);
                AppRepo.MAIN.postDelayed(searchRun, 150);
            }
        });
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                runSearch();
                resultsBox.requestFocus(View.FOCUS_DOWN);
                return true;
            }
        });
        page.addView(searchBox, new LinearLayout.LayoutParams(MATCH, dp(56)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setPadding(0, dp(8), 0, dp(32));
        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setClipChildren(false);
        sv.addView(resultsBox, new FrameLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        svlp.leftMargin = -padH;
        svlp.rightMargin = -padH;
        page.addView(sv, svlp);
        runSearch();
        return page;
    }

    private void runSearch() {
        if (resultsBox == null || searchBox == null) return;
        resultsBox.removeAllViews();
        final String raw = searchBox.getText().toString().trim();
        String q = raw.toLowerCase(Locale.getDefault());
        if (q.isEmpty()) {
            TextView hint = text(17, "sans-serif", Typeface.NORMAL, Theme.text2);
            hint.setSingleLine(false);
            hint.setText("Find any installed app or setting. Results appear as you type.");
            hint.setPadding(padH, dp(20), padH, 0);
            resultsBox.addView(hint);
            return;
        }

        ArrayList<AppRepo.AppEntry> starts = new ArrayList<>();
        ArrayList<AppRepo.AppEntry> contains = new ArrayList<>();
        for (AppRepo.AppEntry a : apps) {
            if (hidden.contains(a.pkg)) continue;
            String l = a.label.toLowerCase(Locale.getDefault());
            if (l.startsWith(q)) starts.add(a);
            else if (l.contains(q)) contains.add(a);
        }
        starts.addAll(contains);
        if (!starts.isEmpty()) {
            ArrayList<View> tiles = new ArrayList<>();
            for (int i = 0; i < starts.size() && i < 15; i++) tiles.add(appTile(starts.get(i), "search"));
            addSection(resultsBox, "Apps", tiles);
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(padH - dp(16), 0, padH - dp(16), 0);
        boolean header = false;
        for (final Shortcut sc : SHORTCUTS) {
            if (!sc.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (!header) {
                list.addView(label("Settings"));
                header = true;
            }
            list.addView(row(sc.icon, sc.label, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSetting(sc.action);
                }
            }));
        }
        list.addView(label("Films & shows"));
        list.addView(row(Icons.SEARCH, "Search “" + raw + "” in your streaming apps", null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        systemSearch(raw);
                    }
                }));
        resultsBox.addView(list, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ------------------------------------------------------------------ overlays

    private LinearLayout panel(int role, int width, boolean right) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(new Theme.Surface(role, dp(30), density));
        p.setPadding(dp(18), dp(24), dp(18), dp(18));
        FrameLayout.LayoutParams lp;
        if (right) {
            lp = new FrameLayout.LayoutParams(width, MATCH, Gravity.END);
            lp.setMargins(dp(24), dp(24), dp(24), dp(24));
        } else {
            lp = new FrameLayout.LayoutParams(width, WRAP, Gravity.CENTER);
        }
        p.setLayoutParams(lp);
        return p;
    }

    private TextView label(String s) {
        TextView t = text(13, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        t.setAllCaps(true);
        t.setLetterSpacing(0.14f);
        t.setText(s);
        t.setPadding(dp(16), dp(18), 0, dp(6));
        return t;
    }

    private RowView row(int icon, String label, String value, View.OnClickListener click) {
        RowView r = new RowView(this, icon, label, density, 56);
        r.value = value;
        if (click != null) r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(2);
        r.setLayoutParams(lp);
        return r;
    }

    private ScrollView scrollList(LinearLayout list) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setSmoothScrollingEnabled(true);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        sv.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return sv;
    }

    private void showOverlay(View content, View returnFocus, View firstFocus) {
        closeOverlay(false);
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(Theme.scrim);
        layer.setClickable(true);
        layer.addView(content);
        root.addView(layer, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = layer;
        overlayReturn = returnFocus;
        pages.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        int d = Theme.dur(180);
        if (d > 0) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(d).start();
            content.setTranslationX(content.getLayoutParams() instanceof FrameLayout.LayoutParams
                    && ((FrameLayout.LayoutParams) content.getLayoutParams()).gravity == Gravity.END ? dp(40) : 0);
            content.setScaleX(0.97f);
            content.setScaleY(0.97f);
            content.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(d).setInterpolator(TileView.EASE).start();
        }
        if (firstFocus != null) firstFocus.requestFocus();
    }

    private void closeOverlay(boolean restoreFocus) {
        if (overlay == null) return;
        root.removeView(overlay);
        overlay = null;
        movingSection = null;
        sectionList = null;
        pages.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = overlayReturn;
        overlayReturn = null;
        if (restoreFocus) {
            if (back != null && back.isAttachedToWindow() && back.requestFocus()) return;
            if (currentPage == PAGE_HOME) focusFirstTile();
        }
    }

    private void openAppMenu(final AppRepo.AppEntry a, final View origin, final boolean inMyApps) {
        LinearLayout p = panel(Theme.ROLE_ELEVATED, dp(440), false);
        TextView title = text(26, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText(a.label);
        title.setPadding(dp(16), 0, dp(16), dp(4));
        TextView sub = text(14, "sans-serif", Typeface.NORMAL, Theme.text2);
        sub.setText(a.kind + (a.system ? "  ·  Pre-installed" : ""));
        sub.setPadding(dp(16), 0, dp(16), dp(10));
        p.addView(title);
        p.addView(sub);

        final boolean fav = favs.contains(a.pkg);
        final boolean isHidden = hidden.contains(a.pkg);
        RowView open = row(Icons.OPEN, "Open", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(true);
                launchApp(a);
            }
        });
        p.addView(open);
        p.addView(row(Icons.STAR, fav ? "Remove from My Apps" : "Add to My Apps", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(true);
                toggleFav(a);
            }
        }));
        if (inMyApps && fav && origin instanceof TileView) {
            p.addView(row(Icons.MOVE, "Move", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeOverlay(true);
                    startMove((TileView) origin);
                }
            }));
        }
        p.addView(row(isHidden ? Icons.EYE : Icons.HIDE, isHidden ? "Unhide app" : "Hide app", null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeOverlay(true);
                        toggleHidden(a);
                    }
                }));
        p.addView(row(Icons.INFO, "App info, storage & permissions", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(true);
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
            }
        }));
        if (!a.system) {
            p.addView(row(Icons.TRASH, "Uninstall", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeOverlay(true);
                    launch(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                }
            }));
        }
        showOverlay(p, origin, open);
    }

    private PadView pad(int icon, String label, String state, View.OnClickListener click) {
        PadView p = new PadView(this, icon, label, state, density);
        p.setOnClickListener(click);
        return p;
    }

    private void addPadRow(LinearLayout list, PadView left, PadView right) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, WRAP, 1f);
        l.rightMargin = dp(6);
        r.addView(left, l);
        LinearLayout.LayoutParams rr = new LinearLayout.LayoutParams(0, WRAP, 1f);
        rr.leftMargin = dp(6);
        if (right != null) r.addView(right, rr);
        else r.addView(new View(this), rr);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        list.addView(r, lp);
    }

    private void openControlCenter() {
        View ret = overlayReturn != null ? overlayReturn : getCurrentFocus();
        LinearLayout p = panel(Theme.ROLE_GLASS, dp(420), true);

        TextView time = text(44, "sans-serif-light", Typeface.NORMAL, Theme.text);
        time.setText(DateFormat.getTimeFormat(this).format(new Date()));
        time.setPadding(dp(8), 0, 0, 0);
        TextView date = text(15, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        date.setText(dateFmt.format(new Date()));
        date.setPadding(dp(8), dp(4), 0, dp(4));
        p.addView(time);
        p.addView(date);

        LinearLayout list = new LinearLayout(this);
        final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        final RowView vol = row(Icons.SOUND, "Volume", null, null);
        final PadView mute = pad(Icons.MUTE, "Mute", "Off", null);
        updateVolume(vol, mute, am);
        vol.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (am == null || e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            code == KeyEvent.KEYCODE_DPAD_RIGHT ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                    updateVolume(vol, mute, am);
                    return true;
                }
                return false;
            }
        });
        mute.setOnClickListener(new View.OnClickListener() {
            @Override
            @SuppressWarnings("deprecation")
            public void onClick(View v) {
                if (am == null) return;
                if (Build.VERSION.SDK_INT >= 23) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                } else {
                    am.setStreamMute(AudioManager.STREAM_MUSIC, am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0);
                }
                updateVolume(vol, mute, am);
            }
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        vlp.topMargin = dp(10);
        list.addView(vol, vlp);

        int netIc = netLabel.equals("Ethernet") ? Icons.ETHERNET : netLabel.equals("Offline") ? Icons.OFFLINE : Icons.WIFI;
        PadView net = pad(netIc, "Network", netLabel, settingClick(Settings.ACTION_WIFI_SETTINGS));
        net.setState(netLabel, !netLabel.equals("Offline"));
        addPadRow(list, net, pad(Icons.BLUETOOTH, "Bluetooth", "Remotes & audio", settingClick(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addPadRow(list, mute, pad(Icons.CAST, "Mirroring", "Cast to TV", settingClick(Settings.ACTION_CAST_SETTINGS)));
        addPadRow(list, pad(Icons.DISPLAY, "Picture", "Display settings", settingClick(Settings.ACTION_DISPLAY_SETTINGS)),
                pad(Icons.SOUND, "Sound", "Audio output", settingClick(Settings.ACTION_SOUND_SETTINGS)));
        addPadRow(list, pad(Icons.MOON, "Screensaver", "When idle", settingClick(Settings.ACTION_DREAM_SETTINGS)),
                pad(Icons.ACCESS, "Accessibility", "Shortcuts", settingClick(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        if (!inputs.isEmpty()) {
            list.addView(label("Inputs"));
            for (final AppRepo.InputEntry in : inputs) {
                list.addView(row(Icons.INPUT, in.label, null, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeOverlay(false);
                        switchInput(in);
                    }
                }));
            }
        }

        list.addView(label("More"));
        list.addView(row(Icons.SHIELD, "Privacy", null, settingClick(Settings.ACTION_PRIVACY_SETTINGS)));
        list.addView(row(Icons.GEAR, "All system settings", null, settingClick(Settings.ACTION_SETTINGS)));
        list.addView(row(Icons.LAYERS, "ORBÉ settings", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrbeSettings();
            }
        }));

        p.addView(scrollList(list));
        showOverlay(p, ret, vol);
    }

    private void updateVolume(RowView vol, PadView mute, AudioManager am) {
        if (am == null) {
            vol.setValue("Unavailable");
            return;
        }
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = Build.VERSION.SDK_INT >= 23 ? am.isStreamMute(AudioManager.STREAM_MUSIC) : cur == 0;
        vol.setLevel(cur / (float) max);
        vol.setValue(cur + "  ◀ ▶");
        mute.setState(muted ? "On" : "Off", muted);
    }

    private View.OnClickListener settingClick(final String action) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(false);
                openSetting(action);
            }
        };
    }

    private void openOrbeSettings() {
        View ret = overlayReturn != null ? overlayReturn : getCurrentFocus();
        LinearLayout p = panel(Theme.ROLE_GLASS, dp(460), true);
        TextView title = text(28, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("ORBÉ Settings");
        title.setPadding(dp(16), 0, 0, dp(2));
        TextView sub = text(14, "sans-serif", Typeface.NORMAL, Theme.text2);
        sub.setText("Your TV. Your World. Your Rules.");
        sub.setPadding(dp(16), dp(2), 0, dp(6));
        p.addView(title);
        p.addView(sub);

        LinearLayout list = new LinearLayout(this);
        list.addView(label("Appearance"));
        final RowView theme = row(Icons.PALETTE, "Theme", Theme.light ? "Light" : "Dark", null);
        theme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean light = !Theme.light;
                prefs.edit().putBoolean("light", light).apply();
                Theme.apply(light);
                buildUi();
                openOrbeSettings();
            }
        });
        list.addView(theme);
        final RowView motion = row(Icons.MOTION, "Reduce motion", Theme.reduceMotion ? "On" : "Off", null);
        motion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !prefs.getBoolean("reduceMotion", false);
                prefs.edit().putBoolean("reduceMotion", on).apply();
                Theme.reduceMotion = on || systemAnimationsOff();
                motion.setValue(Theme.reduceMotion ? "On" : "Off");
            }
        });
        list.addView(motion);

        list.addView(label("Rows below the dock  ·  OK show/hide  ·  Hold OK reorder"));
        sectionList = new LinearLayout(this);
        sectionList.setOrientation(LinearLayout.VERTICAL);
        for (final String id : sectionOrder) {
            final RowView r = row(Icons.LAYERS, sectionName(id), sectionOff.contains(id) ? "Hidden" : "Shown", null);
            r.payload = id;
            r.accentValue = !sectionOff.contains(id);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (movingSection != null) return;
                    if (!sectionOff.remove(id)) sectionOff.add(id);
                    putList("sectionsOff", sectionOff);
                    r.accentValue = !sectionOff.contains(id);
                    r.setValue(sectionOff.contains(id) ? "Hidden" : "Shown");
                    buildSections();
                }
            });
            r.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    movingSection = r;
                    r.surface.setMoving(true);
                    return true;
                }
            });
            r.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    return onSectionMoveKey(r, code, e);
                }
            });
            sectionList.addView(r);
        }
        list.addView(sectionList);

        list.addView(label("Widgets"));
        list.addView(row(Icons.SUN, "Weather city", prefs.getString("wCity", "Aligarh"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(false);
                askCity();
            }
        }));
        list.addView(row(Icons.HOURGLASS, "Watch time", usageAllowed ? "On" : "Off", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(false);
                openWatchTime();
            }
        }));

        list.addView(label("Apps"));
        int nHidden = 0;
        for (String h : hidden) if (find(h) != null) nHidden++;
        list.addView(row(Icons.HIDE, "Hidden apps", String.valueOf(nHidden), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay(false);
                showHidden = true;
                showPage(PAGE_APPS);
                if (hiddenPill != null) hiddenPill.setText("Hide hidden apps");
                refreshAppsView();
            }
        }));
        list.addView(row(Icons.STAR, "Reset My Apps to default", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove("fav").apply();
                loadFavs();
                buildSections();
                Toast.makeText(MainActivity.this, "My Apps reset", Toast.LENGTH_SHORT).show();
            }
        }));

        list.addView(label("System"));
        final RowView mem = row(Icons.CHIP, "ORBÉ memory use", memoryText(), null);
        mem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mem.setValue(memoryText());
            }
        });
        list.addView(mem);
        list.addView(row(Icons.INFO, "About ORBÉ OS", "Version " + versionName(), null));

        p.addView(scrollList(list));
        showOverlay(p, ret, theme);
    }

    private boolean onSectionMoveKey(RowView r, int code, KeyEvent e) {
        if (movingSection != r || sectionList == null) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok && e.getRepeatCount() > 0) return true;
        int i = sectionList.indexOfChild(r);
        if (code == KeyEvent.KEYCODE_DPAD_UP && i > 0) {
            Collections.swap(sectionOrder, i, i - 1);
            swapChild(sectionList, i, i - 1);
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && i < sectionList.getChildCount() - 1) {
            Collections.swap(sectionOrder, i, i + 1);
            swapChild(sectionList, i, i + 1);
        } else if (ok || code == KeyEvent.KEYCODE_BACK) {
            movingSection = null;
            r.surface.setMoving(false);
            putList("sections", sectionOrder);
            buildSections();
        }
        return true;
    }

    private String memoryText() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
            return (mi[0].getTotalPss() / 1024) + " MB";
        } catch (Throwable t) {
            return "—";
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1";
        }
    }

    // ------------------------------------------------------------------ actions

    private void toggleFav(AppRepo.AppEntry a) {
        String msg;
        if (favs.remove(a.pkg)) {
            msg = a.label + " removed from My Apps";
        } else {
            favs.add(a.pkg);
            msg = a.label + " added to My Apps";
        }
        putList("fav", favs);
        buildSections();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void toggleHidden(AppRepo.AppEntry a) {
        String msg;
        if (hidden.remove(a.pkg)) {
            msg = a.label + " is visible again";
        } else {
            hidden.add(a.pkg);
            msg = a.label + " hidden. Find it under All Apps → Show hidden apps";
        }
        putList("hidden", hidden);
        buildSections();
        if (pageApps != null) refreshAppsView();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void launchApp(AppRepo.AppEntry a) {
        if (a == null) return;
        recents.remove(a.pkg);
        recents.add(0, a.pkg);
        while (recents.size() > 20) recents.remove(recents.size() - 1);
        putList("recent", recents);
        homeDirty = true;
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        launch(i);
    }

    private void switchInput(AppRepo.InputEntry in) {
        launch(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id)));
    }

    private void openSetting(String action) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            launch(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void launch(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "This TV can't open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void systemSearch(String query) {
        Intent[] tries = {
                new Intent("android.search.action.GLOBAL_SEARCH"),
                new Intent(Intent.ACTION_ASSIST),
                new Intent(Intent.ACTION_WEB_SEARCH),
        };
        for (Intent i : tries) {
            i.putExtra(SearchManager.QUERY, query);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        Toast.makeText(this, "No search app on this TV", Toast.LENGTH_SHORT).show();
    }
}
