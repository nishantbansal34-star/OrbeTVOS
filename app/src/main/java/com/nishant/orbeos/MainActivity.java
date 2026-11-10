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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * ORBÉ OS home. One Activity, everything built in code, pages and panels created
 * lazily and released when the TV needs memory.
 */
public final class MainActivity extends Activity implements ViewTreeObserver.OnGlobalFocusChangeListener {

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
    private static final String[] SECTION_IDS = {"apps", "recent", "inputs", "installed", "games", "music"};
    private static final String[] SECTION_NAMES = {"My Apps", "Recently Opened", "Inputs", "Recently Installed", "Games", "Music"};
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
    private int padH, gap, tileW, tileH, labelH, iconPx;

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

    // views
    private FrameLayout root;
    private LinearLayout shell;
    private LinearLayout navView;
    private TextView[] tabs;
    private Theme.Surface[] tabSurfaces;
    private TextView clock;
    private IconButton netIcon;
    private String netLabel = "Offline";
    private FrameLayout pages;
    private int currentPage = PAGE_HOME;

    private View pageHome;
    private TextView heroKind, heroTitle, heroLine;
    private ScrollView homeScroll;
    private LinearLayout sectionsBox;
    private LinearLayout myAppsRow;
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

    // ------------------------------------------------------------------ receivers

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
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
        // Rebuild "Recently Opened" while the home screen is hidden: no visible jump.
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
        // Home pressed while already home.
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
        }
        // On Home, Back does nothing: a launcher never "exits".
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
                if (searchBox != null) searchBox.requestFocus();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    /** Hand memory back to whatever the user is watching. */
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
        for (String s : SECTION_IDS) if (!sectionOrder.contains(s)) sectionOrder.add(s);
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
        padH = dp(48);
        gap = dp(20);
        int contentW = dm.widthPixels - 2 * padH;
        tileW = (contentW - 4 * gap) / 5;
        tileH = tileW * 9 / 16;
        labelH = dp(28);
        iconPx = Math.round(tileH * 0.56f);
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
        t.setPadding(dp(20), 0, dp(20), 0);
        final Theme.Surface s = new Theme.Surface(Theme.ROLE_CARD, dp(22), density);
        t.setBackground(s);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                s.setFocused(has);
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

    // ------------------------------------------------------------------ UI shell

    private void buildUi() {
        if (root != null) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
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
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(padH, dp(20), padH, 0);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        shell.addView(buildNav(), new LinearLayout.LayoutParams(MATCH, WRAP));

        pages = new FrameLayout(this);
        pages.setClipChildren(false);
        pageHome = buildHomePage();
        pages.addView(pageHome, new FrameLayout.LayoutParams(MATCH, MATCH));
        shell.addView(pages, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        root.addView(shell, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(this);

        currentPage = PAGE_HOME;
        updateTabs();
        updateClock();
        updateNet();
        if (favsLoaded) buildSections();
    }

    private View buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Theme.Surface glass = new Theme.Surface(Theme.ROLE_GLASS, dp(28), density);
        nav.setBackground(glass);
        Theme.track(nav, glass);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));

        TextView brand = text(20, "sans-serif", Typeface.BOLD, Theme.text);
        brand.setText("ORBÉ");
        brand.setLetterSpacing(0.16f);
        brand.setPadding(dp(14), 0, dp(14), 0);
        nav.addView(brand);

        String[] names = {"Home", "Apps", "Search"};
        tabs = new TextView[names.length];
        tabSurfaces = new Theme.Surface[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            final TextView t = text(17, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
            t.setText(names[i]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(18), 0, dp(18), 0);
            final Theme.Surface s = new Theme.Surface(Theme.ROLE_NONE, dp(22), density);
            t.setBackground(s);
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    s.setFocused(has);
                    updateTabs();
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPage(idx);
                    if (idx == PAGE_SEARCH && searchBox != null) searchBox.requestFocus();
                }
            });
            tabs[i] = t;
            tabSurfaces[i] = s;
            nav.addView(t, new LinearLayout.LayoutParams(WRAP, dp(44)));
        }

        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        netIcon = new IconButton(this, Icons.WIFI, "Network", density, false);
        nav.addView(netIcon);
        clock = text(18, "sans-serif-medium", Typeface.NORMAL, Theme.text);
        clock.setPadding(dp(4), 0, dp(14), 0);
        nav.addView(clock);

        IconButton cc = new IconButton(this, Icons.SLIDERS, "Control Center", density, true);
        cc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openControlCenter();
            }
        });
        nav.addView(cc);
        IconButton gear = new IconButton(this, Icons.GEAR, "ORBÉ settings", density, true);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrbeSettings();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(WRAP, WRAP);
        glp.leftMargin = dp(4);
        nav.addView(gear, glp);

        navView = nav;
        return nav;
    }

    private void updateTabs() {
        if (tabs == null) return;
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == currentPage || tabs[i].isFocused();
            tabs[i].setTextColor(on ? Theme.text : Theme.text2);
            tabSurfaces[i].setSelected(i == currentPage);
        }
    }

    private int tabIndex(View v) {
        if (tabs == null) return -1;
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == v) return i;
        return -1;
    }

    /**
     * Tabs switch pages when you move along the nav bar, but arriving at the nav bar
     * from below always lands on the current tab, so pressing Up never changes page.
     */
    @Override
    public void onGlobalFocusChanged(View old, View now) {
        int ti = tabIndex(now);
        if (ti >= 0) {
            if (old != null && isInside(old, navView)) {
                showPage(ti);
            } else if (ti != currentPage) {
                tabs[currentPage].requestFocus();
                return;
            }
        }
        updateTabs();
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
        if (changed && Theme.dur(150) > 0) {
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(Theme.dur(150)).start();
        }
        updateTabs();
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
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setClipChildren(false);

        // Hero: fixed sizes, so changing it only redraws text and never re-measures the screen.
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(6), dp(8), 0, 0);
        heroKind = text(14, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        heroKind.setAllCaps(true);
        heroKind.setLetterSpacing(0.18f);
        heroTitle = text(46, "sans-serif", Typeface.BOLD, Theme.text);
        heroLine = text(16, "sans-serif", Typeface.NORMAL, Theme.text2);
        hero.addView(heroKind, new LinearLayout.LayoutParams(MATCH, dp(22)));
        hero.addView(heroTitle, new LinearLayout.LayoutParams(MATCH, dp(62)));
        hero.addView(heroLine, new LinearLayout.LayoutParams(MATCH, dp(24)));
        home.addView(hero, new LinearLayout.LayoutParams(MATCH, dp(136)));

        homeScroll = new ScrollView(this);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        homeScroll.setSmoothScrollingEnabled(true);
        homeScroll.setClipToPadding(false);
        homeScroll.setPadding(0, 0, 0, dp(32));
        sectionsBox = new LinearLayout(this);
        sectionsBox.setOrientation(LinearLayout.VERTICAL);
        sectionsBox.setClipChildren(false);
        homeScroll.addView(sectionsBox, new FrameLayout.LayoutParams(MATCH, WRAP));
        home.addView(homeScroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setHero("Welcome", "ORBÉ OS", "Entertainment, without the clutter.");
        return home;
    }

    private void setHero(String kind, String title, String line) {
        if (heroTitle == null) return;
        heroKind.setText(kind);
        heroTitle.setText(title);
        heroLine.setText(line);
        int d = Theme.dur(160);
        if (d > 0) {
            heroTitle.setAlpha(0.4f);
            heroTitle.animate().alpha(1f).setDuration(d).start();
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
        boolean focusWasHere = f != null && isInside(f, sectionsBox);

        endMove();
        sectionsBox.removeAllViews();
        myAppsRow = null;
        for (String id : sectionOrder) {
            if (sectionOff.contains(id)) continue;
            List<View> tiles = tilesFor(id);
            if (tiles.isEmpty()) continue;
            LinearLayout row = addSection(sectionsBox, sectionName(id), tiles);
            if (id.equals("apps")) myAppsRow = row;
        }
        if (sectionsBox.getChildCount() == 0) {
            TextView empty = text(18, "sans-serif", Typeface.NORMAL, Theme.text2);
            empty.setText("All sections are turned off. Turn them on in ORBÉ settings.");
            sectionsBox.addView(empty);
        }

        if (overlay != null || currentPage != PAGE_HOME) return;
        View back = focusTag != null ? sectionsBox.findViewWithTag(focusTag) : null;
        if (back != null) {
            back.requestFocus();
        } else if (focusWasHere || !firstFocusDone || getCurrentFocus() == null) {
            focusFirstTile();
        }
        firstFocusDone = true;
    }

    private LinearLayout addSection(LinearLayout box, String title, List<View> tiles) {
        TextView t = text(17, "sans-serif-medium", Typeface.NORMAL, Theme.text2);
        t.setText(title);
        t.setPadding(dp(6), dp(10), 0, dp(2));
        box.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setSmoothScrollingEnabled(true);
        hsv.setClipToPadding(false);
        hsv.setPadding(dp(10), dp(10), dp(10), dp(6));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        for (View v : tiles) row.addView(v, tileParams());
        hsv.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.leftMargin = -dp(10);
        lp.rightMargin = -dp(10);
        box.addView(hsv, lp);
        return row;
    }

    private List<View> tilesFor(String id) {
        ArrayList<View> out = new ArrayList<>();
        switch (id) {
            case "apps":
                for (String pkg : favs) {
                    AppRepo.AppEntry a = find(pkg);
                    if (a != null && !hidden.contains(pkg)) out.add(appTile(a, "apps"));
                }
                out.add(iconTile("Add apps", Icons.PLUS, "apps:+", "Library", "Add apps",
                        "Pin any app from the Apps tab", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showPage(PAGE_APPS);
                                if (grid != null) grid.requestFocus();
                            }
                        }));
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
                if (has) setHero(a.kind, a.label, "OK to open  ·  Hold OK for options");
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
                if (has) setHero(heroK, heroT, heroL);
            }
        });
        return t;
    }

    private void focusFirstTile() {
        if (sectionsBox == null) return;
        for (int i = 0; i < sectionsBox.getChildCount(); i++) {
            View c = sectionsBox.getChildAt(i);
            if (c instanceof HorizontalScrollView) {
                ((HorizontalScrollView) c).scrollTo(0, 0);
                ViewGroup row = (ViewGroup) ((HorizontalScrollView) c).getChildAt(0);
                if (row != null && row.getChildCount() > 0) {
                    row.getChildAt(0).requestFocus();
                    return;
                }
            }
        }
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
        if (myAppsRow == null) return;
        ArrayList<String> order = new ArrayList<>();
        for (int i = 0; i < myAppsRow.getChildCount(); i++) {
            View v = myAppsRow.getChildAt(i);
            if (v instanceof TileView && ((TileView) v).app != null) order.add(((TileView) v).app.pkg);
        }
        for (String p : favs) if (!order.contains(p)) order.add(p);   // keep hidden pins
        favs.clear();
        favs.addAll(order);
        putList("fav", favs);
    }

    private boolean onMoveKey(TileView t, int code, KeyEvent e) {
        if (movingTile != t || myAppsRow == null) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok && e.getRepeatCount() > 0) return true;   // still holding from the long-press
        int i = myAppsRow.indexOfChild(t);
        int lastApp = myAppsRow.getChildCount() - 2;     // last child is "Add apps"
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && i > 0) {
            swapChild(myAppsRow, i, i - 1);
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i < lastApp) {
            swapChild(myAppsRow, i, i + 1);
        } else if (ok || code == KeyEvent.KEYCODE_BACK) {
            endMove();
            setHero(t.app != null ? t.app.kind : "", t.app != null ? t.app.label : "", "OK to open  ·  Hold OK for options");
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
        page.setPadding(0, dp(24), 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), 0, 0, 0);
        TextView title = text(34, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("Apps");
        appsCount = text(16, "sans-serif", Typeface.NORMAL, Theme.text2);
        appsCount.setPadding(dp(14), dp(8), 0, 0);
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
        grid.setVerticalSpacing(dp(14));
        grid.setPadding(dp(10), dp(20), dp(10), dp(40));
        grid.setClipToPadding(false);
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
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        glp.leftMargin = -dp(10);
        glp.rightMargin = -dp(10);
        page.addView(grid, glp);

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
        page.setPadding(0, dp(24), 0, 0);
        page.setClipChildren(false);

        searchBox = new EditText(this);
        final Theme.Surface s = new Theme.Surface(Theme.ROLE_CARD, dp(26), density);
        searchBox.setBackground(s);
        searchBox.setTextColor(Theme.text);
        searchBox.setHintTextColor(Theme.text2);
        searchBox.setHint("Search apps and settings");
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setPadding(dp(24), 0, dp(24), 0);
        searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                s.setFocused(has);
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
        sv.setPadding(0, dp(8), 0, dp(32));
        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setClipChildren(false);
        resultsBox.setFocusable(false);
        sv.addView(resultsBox, new FrameLayout.LayoutParams(MATCH, WRAP));
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
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
            hint.setPadding(dp(6), dp(20), 0, 0);
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

        boolean header = false;
        for (final Shortcut sc : SHORTCUTS) {
            if (!sc.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (!header) {
                resultsBox.addView(label("Settings"));
                header = true;
            }
            resultsBox.addView(row(sc.icon, sc.label, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSetting(sc.action);
                }
            }));
        }

        resultsBox.addView(label("Movies & shows"));
        resultsBox.addView(row(Icons.SEARCH, "Search “" + raw + "” in your streaming apps", null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        systemSearch(raw);
                    }
                }));
    }

    // ------------------------------------------------------------------ overlays

    private LinearLayout panel(int role, int width, boolean right) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        Theme.Surface s = new Theme.Surface(role, dp(20), density);
        p.setBackground(s);
        if (role == Theme.ROLE_GLASS) Theme.track(p, s);
        p.setPadding(dp(14), dp(20), dp(14), dp(14));
        FrameLayout.LayoutParams lp;
        if (right) {
            lp = new FrameLayout.LayoutParams(width, MATCH, Gravity.END);
            lp.setMargins(dp(20), dp(20), dp(20), dp(20));
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
        t.setPadding(dp(16), dp(16), 0, dp(6));
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
        shell.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // trap focus in the panel
        int d = Theme.dur(150);
        if (d > 0) {
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(d).start();
        }
        if (firstFocus != null) firstFocus.requestFocus();
    }

    private void closeOverlay(boolean restoreFocus) {
        if (overlay == null) return;
        root.removeView(overlay);
        overlay = null;
        movingSection = null;
        sectionList = null;
        shell.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View back = overlayReturn;
        overlayReturn = null;
        if (restoreFocus) {
            if (back != null && back.isAttachedToWindow() && back.requestFocus()) return;
            if (currentPage == PAGE_HOME) focusFirstTile();
        }
    }

    // App actions (Hold OK on any app)
    private void openAppMenu(final AppRepo.AppEntry a, final View origin, final boolean inMyApps) {
        LinearLayout p = panel(Theme.ROLE_ELEVATED, dp(440), false);
        TextView title = text(24, "sans-serif", Typeface.BOLD, Theme.text);
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

    private void openControlCenter() {
        LinearLayout p = panel(Theme.ROLE_GLASS, dp(420), true);
        TextView title = text(26, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("Control Center");
        title.setPadding(dp(16), 0, 0, dp(6));
        p.addView(title);

        LinearLayout list = new LinearLayout(this);
        final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        final RowView vol = row(Icons.SOUND, "Volume", null, null);
        final RowView mute = row(Icons.MUTE, "Mute", null, null);
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
        list.addView(vol);
        list.addView(mute);

        list.addView(label("Connections"));
        list.addView(row(netLabel.equals("Ethernet") ? Icons.ETHERNET : netLabel.equals("Offline") ? Icons.OFFLINE : Icons.WIFI,
                "Network", netLabel, settingClick(Settings.ACTION_WIFI_SETTINGS)));
        list.addView(row(Icons.BLUETOOTH, "Bluetooth & remotes", null, settingClick(Settings.ACTION_BLUETOOTH_SETTINGS)));
        list.addView(row(Icons.CAST, "Screen mirroring", null, settingClick(Settings.ACTION_CAST_SETTINGS)));

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

        list.addView(label("Picture & sound"));
        list.addView(row(Icons.DISPLAY, "Display & picture", null, settingClick(Settings.ACTION_DISPLAY_SETTINGS)));
        list.addView(row(Icons.SOUND, "Sound", null, settingClick(Settings.ACTION_SOUND_SETTINGS)));
        list.addView(row(Icons.MOON, "Screensaver", null, settingClick(Settings.ACTION_DREAM_SETTINGS)));

        list.addView(label("More"));
        list.addView(row(Icons.ACCESS, "Accessibility", null, settingClick(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        list.addView(row(Icons.SHIELD, "Privacy", null, settingClick(Settings.ACTION_PRIVACY_SETTINGS)));
        list.addView(row(Icons.GEAR, "All system settings", null, settingClick(Settings.ACTION_SETTINGS)));
        list.addView(row(Icons.LAYERS, "ORBÉ settings", null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrbeSettings();
            }
        }));

        p.addView(scrollList(list));
        View ret = overlayReturn != null ? overlayReturn : getCurrentFocus();
        showOverlay(p, ret, vol);
    }

    private void updateVolume(RowView vol, RowView mute, AudioManager am) {
        if (am == null) {
            vol.setValue("Unavailable");
            return;
        }
        int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = Build.VERSION.SDK_INT >= 23 ? am.isStreamMute(AudioManager.STREAM_MUSIC) : cur == 0;
        vol.setLevel(cur / (float) max);
        vol.setValue(cur + " / " + max + "  ◀ ▶");
        mute.setValue(muted ? "On" : "Off");
        mute.accentValue = muted;
        mute.invalidate();
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
        TextView title = text(26, "sans-serif", Typeface.BOLD, Theme.text);
        title.setText("ORBÉ Settings");
        title.setPadding(dp(16), 0, 0, dp(2));
        TextView sub = text(14, "sans-serif", Typeface.NORMAL, Theme.text2);
        sub.setText("Your TV. Your World. Your Rules.");
        sub.setPadding(dp(16), 0, 0, dp(6));
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

        list.addView(label("Home sections  ·  OK show/hide  ·  Hold OK reorder"));
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
                if (grid != null) grid.requestFocus();
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
            msg = a.label + " hidden. Find it under Apps → Show hidden apps";
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
