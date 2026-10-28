package com.blackfalcon.quakewatch;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int RC_PERM = 100;
    private static final int RC_BG = 101;
    private static final int ACC = 0xFFFF6A00;

    private interface Pick {
        void on(int i);
    }

    private static class Holder {
        TextView badge;
        TextView place;
        TextView meta;
        TextView dist;
    }

    private LinearLayout root;
    private FrameLayout content;
    private final TextView[] tabViews = new TextView[4];
    private int cur = 0;
    private int bg, card, chipBg, txt, sub;

    // Nearby tab state
    private String nearPeriod = "week";
    private int nearRadius = 300;
    private List<Quake> nearData = null;
    private String nearKey = "";
    private long nearTime = 0;
    private long nearCacheTime = 0;
    private boolean nearLoading = false;
    private String nearLoadKey = "";
    private int nearReq = 0;
    private TextView nearInfo;
    private TextView locTv;
    private QuakeAdapter nearAd;

    // World tab state
    private String worldMag = "2.5";
    private String worldPeriod = "day";
    private String worldSort = "new";
    private String worldQuery = "";
    private List<Quake> worldData = null;
    private String worldKey = "";
    private long worldTime = 0;
    private long worldCacheTime = 0;
    private boolean worldLoading = false;
    private String worldLoadKey = "";
    private int worldReq = 0;
    private TextView worldInfo;
    private BarsView bars;
    private QuakeAdapter worldAd;

    // Settings (values chosen but not saved yet)
    private String pLang, pTheme, pSource;
    private boolean pNotif, pUseLoc;
    private int pRadius, pInterval;
    private double pMinNear, pWorld;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nearRadius = Store.radius(this);
        Notifier.ensureChannels(this);
        build();
        if (!Store.permAsked(this)) {
            Store.setPermAsked(this);
            ensurePermsAndStart();
        } else {
            afterPerms(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.useLoc(this) && LocHelper.hasPerm(this) && LocHelper.updateFromLast(this)) {
            updateLocLabel();
            applyNearby();
        }
        reload(false);
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERM) afterPerms(true);
    }

    // ------------------------------------------------------------------ small helpers

    private String tr(String en, String ur, String ar) {
        return L.t(this, en, ur, ar);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private TextView button(String label, int bgColor, int fg, View.OnClickListener l) {
        TextView t = tv(label, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(12), dp(10), dp(12));
        t.setBackground(rounded(bgColor, 10));
        t.setOnClickListener(l);
        return t;
    }

    /** Adds an equal-width button to a horizontal row. The row itself must be added with MATCH_PARENT width. */
    private void addBtn(LinearLayout row, String label, int bgColor, int fg, View.OnClickListener l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button(label, bgColor, fg, l), p);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void setInfo(TextView t, String s, int color) {
        if (t != null) {
            t.setText(s);
            t.setTextColor(color);
        }
    }

    private int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    private int indexOfInt(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private int indexOfDouble(double[] a, double v) {
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - v) < 0.001) return i;
        return -1;
    }

    private String fmtTime(long t, boolean utc) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        if (utc) f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }

    private void applyTheme() {
        boolean dark = !"light".equals(Store.theme(this));
        if (dark) {
            bg = 0xFF000000;
            card = 0xFF161616;
            chipBg = 0xFF2A2A2A;
            txt = 0xFFFFFFFF;
            sub = 0xFF9E9E9E;
        } else {
            bg = 0xFFF2F2F2;
            card = 0xFFFFFFFF;
            chipBg = 0xFFDEDEDE;
            txt = 0xFF111111;
            sub = 0xFF5F5F5F;
        }
    }

    private void styleChip(TextView t, boolean sel) {
        t.setBackground(rounded(sel ? ACC : chipBg, 10));
        t.setTextColor(sel ? Color.WHITE : txt);
    }

    private LinearLayout chips(String[] labels, int sel, final Pick cb) {
        final LinearLayout row = hbox();
        final int n = labels.length;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(12);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(4), dp(9), dp(4), dp(9));
            styleChip(t, i == sel);
            t.setOnClickListener(v -> {
                for (int k = 0; k < n; k++) styleChip((TextView) row.getChildAt(k), k == idx);
                cb.on(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(t, lp);
        }
        return row;
    }

    private void addChips(LinearLayout parent, String[] labels, int sel, Pick cb) {
        parent.addView(chips(labels, sel, cb), new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private LinearLayout section(LinearLayout box, String title) {
        LinearLayout c = vbox();
        c.setBackground(rounded(card, 12));
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.addView(tv(title, 14, ACC, true));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.setMargins(0, 0, 0, dp(10));
        box.addView(c, p);
        return c;
    }

    private void note(LinearLayout parent, String text) {
        TextView t = tv(text, 12, sub, false);
        t.setPadding(0, dp(6), 0, 0);
        parent.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private ListView newList() {
        ListView lv = new ListView(this);
        lv.setBackgroundColor(bg);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        return lv;
    }

    // ------------------------------------------------------------------ main layout

    private void build() {
        applyTheme();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setLayoutDirection(L.rtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(10), dp(14), dp(6));
        TextView title = tv("\uD83C\uDF0D " + tr("QuakeWatch", "زلزلہ واچ", "رصد الزلازل"), 20, txt, true);
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView ref = tv("\u21BB", 26, ACC, true);
        ref.setPadding(dp(12), 0, dp(4), 0);
        ref.setOnClickListener(v -> reload(true));
        head.addView(ref, new LinearLayout.LayoutParams(WRAP, WRAP));
        root.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bar = hbox();
        bar.setBackgroundColor(card);
        String[] names = {
                tr("Nearby", "قریبی", "القريبة"),
                tr("World", "دنیا", "العالم"),
                tr("Info", "معلومات", "معلومات"),
                tr("Settings", "سیٹنگز", "الإعدادات")};
        String[] icons = {"\uD83D\uDCCD", "\uD83C\uDF0D", "\u2139\uFE0F", "\u2699\uFE0F"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = tv(icons[i] + "\n" + names[i], 12, sub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(8), 0, dp(8));
            t.setOnClickListener(v -> showTab(idx));
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView by = tv("By: Black Falcon \uD83E\uDD85", 12, sub, false);
        by.setGravity(Gravity.CENTER);
        by.setBackgroundColor(card);
        by.setPadding(0, dp(4), 0, dp(8));
        root.addView(by, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
        showTab(cur);
    }

    private void showTab(int i) {
        cur = i;
        for (int k = 0; k < 4; k++) {
            tabViews[k].setTextColor(k == i ? ACC : sub);
            tabViews[k].setTypeface(null, k == i ? Typeface.BOLD : Typeface.NORMAL);
        }
        content.removeAllViews();
        View v;
        if (i == 0) v = buildNearby();
        else if (i == 1) v = buildWorld();
        else if (i == 2) v = buildInfo();
        else v = buildSettings();
        content.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (i == 0) {
            updateLocLabel();
            loadNearby(false);
        } else if (i == 1) {
            loadWorld(false);
        }
    }

    private void reload(boolean force) {
        if (cur == 0) {
            updateLocLabel();
            loadNearby(force);
        } else if (cur == 1) {
            loadWorld(force);
        }
    }

    private String errText() {
        return tr("Could not load data. Check your internet connection and tap \u21BB.",
                "ڈیٹا لوڈ نہیں ہو سکا۔ انٹرنیٹ چیک کریں اور \u21BB دبائیں۔",
                "تعذّر تحميل البيانات. تحقق من الإنترنت واضغط \u21BB.");
    }

    private String loadingText() {
        return tr("Loading\u2026", "لوڈ ہو رہا ہے\u2026", "جارٍ التحميل\u2026");
    }

    private String offlineNote(long cacheTime) {
        return "\n\u26A0 " + String.format(Locale.US,
                tr("Offline: showing saved data (%s)", "آف لائن: محفوظ شدہ ڈیٹا (%s)", "غير متصل: بيانات محفوظة (%s)"),
                L.ago(this, System.currentTimeMillis() - cacheTime));
    }

    // ------------------------------------------------------------------ Nearby tab

    private View buildNearby() {
        ListView lv = newList();
        LinearLayout h = vbox();
        h.setPadding(dp(12), dp(4), dp(12), dp(8));

        LinearLayout lc = vbox();
        lc.setBackground(rounded(card, 12));
        lc.setPadding(dp(12), dp(10), dp(12), dp(10));
        locTv = tv("", 13, txt, false);
        lc.addView(locTv);
        TextView gb = button(tr("Update my location", "میرا مقام اپ ڈیٹ کریں", "تحديث موقعي"), ACC, Color.WHITE, v -> getLocation());
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(MATCH, WRAP);
        gp.setMargins(0, dp(8), 0, 0);
        lc.addView(gb, gp);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lcp.setMargins(0, 0, 0, dp(6));
        h.addView(lc, lcp);

        final String[] pv = {"day", "week", "month"};
        String[] pl = {tr("24 h", "24 گھنٹے", "24 ساعة"), tr("7 days", "7 دن", "7 أيام"), tr("30 days", "30 دن", "30 يومًا")};
        addChips(h, pl, indexOf(pv, nearPeriod), i -> {
            nearPeriod = pv[i];
            loadNearby(false);
        });

        final int[] rv = {100, 300, 500, 1000};
        String km = tr("km", "کلومیٹر", "كم");
        String[] rl = new String[rv.length];
        for (int i = 0; i < rv.length; i++) rl[i] = rv[i] + " " + km;
        addChips(h, rl, indexOfInt(rv, nearRadius), i -> {
            nearRadius = rv[i];
            applyNearby();
        });

        nearInfo = tv("", 13, sub, false);
        nearInfo.setPadding(dp(4), dp(8), dp(4), dp(2));
        h.addView(nearInfo, new LinearLayout.LayoutParams(MATCH, WRAP));

        lv.addHeaderView(h, null, false);
        nearAd = new QuakeAdapter();
        lv.setAdapter(nearAd);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = parent.getItemAtPosition(pos);
            if (o instanceof Quake) showDetail((Quake) o);
        });
        return lv;
    }

    private void updateLocLabel() {
        if (locTv == null) return;
        if (!Store.useLoc(this)) {
            locTv.setText(tr("Location use is turned off in Settings.",
                    "سیٹنگز میں مقام کا استعمال بند ہے۔",
                    "استخدام الموقع متوقف في الإعدادات."));
        } else if (Store.hasLoc(this)) {
            String c = String.format(Locale.US, "%.3f, %.3f", Store.lat(this), Store.lon(this));
            long age = System.currentTimeMillis() - Store.locTime(this);
            locTv.setText("\uD83D\uDCCD " + tr("Your location: ", "آپ کا مقام: ", "موقعك: ") + c + "\n"
                    + tr("Updated ", "اپ ڈیٹ: ", "آخر تحديث: ") + L.ago(this, age));
        } else {
            locTv.setText(tr("Location is not available yet. Tap the button below, or enable location in Settings.",
                    "مقام ابھی دستیاب نہیں۔ نیچے بٹن دبائیں یا سیٹنگز میں لوکیشن آن کریں۔",
                    "الموقع غير متاح بعد. اضغط الزر أدناه أو فعّل الموقع من الإعدادات."));
        }
    }

    private void getLocation() {
        if (!Store.useLoc(this)) {
            toast(tr("Turn on \"Use my location\" in Settings first.",
                    "پہلے سیٹنگز میں \"میرا مقام استعمال کریں\" آن کریں۔",
                    "فعّل «استخدام موقعي» من الإعدادات أولًا."));
            return;
        }
        if (!LocHelper.hasPerm(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, RC_PERM);
            return;
        }
        toast(tr("Finding your location\u2026", "آپ کا مقام تلاش کیا جا رہا ہے\u2026", "جارٍ تحديد موقعك\u2026"));
        LocHelper.updateFromLast(this);
        updateLocLabel();
        applyNearby();
        LocHelper.requestFresh(this, () -> {
            if (!isFinishing()) {
                updateLocLabel();
                applyNearby();
            }
        });
    }

    private void loadNearby(final boolean force) {
        final String src = Store.source(this);
        final String per = nearPeriod;
        final String key = src + "|" + per;
        if (nearData != null && key.equals(nearKey)) {
            applyNearby();
            if (!force && System.currentTimeMillis() - nearTime < 120000L) return;
        }
        if (nearLoading && !force && key.equals(nearLoadKey)) return;
        final int req = ++nearReq;
        nearLoading = true;
        nearLoadKey = key;
        if (nearData == null || !key.equals(nearKey)) {
            nearData = null;
            if (nearAd != null) nearAd.set(new ArrayList<Quake>());
            setInfo(nearInfo, loadingText(), sub);
        }
        new Thread(() -> {
            try {
                final Feed.Result r = Feed.fetch(MainActivity.this, src, "all", per, true);
                runOnUiThread(() -> {
                    if (req != nearReq) return;
                    nearLoading = false;
                    nearData = r.list;
                    nearKey = key;
                    nearTime = System.currentTimeMillis();
                    nearCacheTime = r.cacheTime;
                    if (!isFinishing()) applyNearby();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (req != nearReq) return;
                    nearLoading = false;
                    nearData = null;
                    nearKey = "";
                    if (!isFinishing() && cur == 0) {
                        if (nearAd != null) nearAd.set(new ArrayList<Quake>());
                        setInfo(nearInfo, errText(), 0xFFE53935);
                    }
                });
            }
        }).start();
    }

    private void applyNearby() {
        if (cur != 0 || nearAd == null) return;
        boolean ok = Store.useLoc(this) && Store.hasLoc(this);
        if (!ok) {
            nearAd.set(new ArrayList<Quake>());
            setInfo(nearInfo, tr("A location is needed to show nearby earthquakes. Tap \"Update my location\", or use the World tab.",
                    "قریبی زلزلے دکھانے کے لیے مقام ضروری ہے۔ \"میرا مقام اپ ڈیٹ کریں\" دبائیں یا دنیا والا ٹیب دیکھیں۔",
                    "يلزم تحديد الموقع لعرض الزلازل القريبة. اضغط «تحديث موقعي» أو استخدم تبويب العالم."), sub);
            return;
        }
        if (nearData == null) {
            nearAd.set(new ArrayList<Quake>());
            return;
        }
        double lat = Store.lat(this);
        double lon = Store.lon(this);
        ArrayList<Quake> out = new ArrayList<Quake>();
        double strongest = 0;
        for (Quake q : nearData) {
            if (Geo.km(lat, lon, q.lat, q.lon) <= nearRadius) {
                out.add(q);
                if (q.mag > strongest) strongest = q.mag;
            }
        }
        nearAd.set(out);
        String s;
        if (out.isEmpty()) {
            s = String.format(Locale.US, tr(
                    "No earthquakes recorded within %d km in this period. Small quakes may be missing; try a longer period or the EMSC source in Settings.",
                    "اس مدت میں %d کلومیٹر کے اندر کوئی زلزلہ ریکارڈ نہیں ہوا۔ چھوٹے زلزلے شامل نہ ہوں تو لمبی مدت یا سیٹنگز میں EMSC ماخذ آزمائیں۔",
                    "لم يُسجَّل أي زلزال ضمن %d كم في هذه الفترة. قد تغيب الزلازل الصغيرة؛ جرّب فترة أطول أو مصدر EMSC من الإعدادات."),
                    nearRadius);
        } else {
            s = String.format(Locale.US, tr(
                    "%1$d earthquakes within %2$d km \u00B7 strongest M%3$s",
                    "%2$d کلومیٹر کے اندر %1$d زلزلے \u00B7 سب سے شدید M%3$s",
                    "%1$d زلزال ضمن %2$d كم \u00B7 الأقوى M%3$s"),
                    out.size(), nearRadius, Geo.fmt1(strongest));
        }
        if (nearCacheTime > 0) s += offlineNote(nearCacheTime);
        setInfo(nearInfo, s, sub);
    }

    // ------------------------------------------------------------------ World tab

    private View buildWorld() {
        ListView lv = newList();
        LinearLayout h = vbox();
        h.setPadding(dp(12), dp(4), dp(12), dp(8));

        EditText et = new EditText(this);
        et.setHint(tr("Search place\u2026", "جگہ تلاش کریں\u2026", "ابحث عن مكان\u2026"));
        et.setSingleLine(true);
        et.setTextSize(15);
        et.setTextColor(Color.BLACK);
        et.setHintTextColor(0xFF777777);
        et.setBackground(rounded(Color.WHITE, 10));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setText(worldQuery);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                worldQuery = s.toString().trim().toLowerCase(Locale.US);
                applyWorld();
            }
        });
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(MATCH, WRAP);
        ep.setMargins(dp(3), dp(3), dp(3), dp(4));
        h.addView(et, ep);

        final String[] mv = {"2.5", "4.5", "all"};
        String[] ml = {"M2.5+", "M4.5+", tr("All", "سب", "الكل")};
        addChips(h, ml, indexOf(mv, worldMag), i -> {
            worldMag = mv[i];
            loadWorld(false);
        });

        final String[] pv = {"hour", "day", "week", "month"};
        String[] pl = {tr("Hour", "گھنٹہ", "ساعة"), tr("Day", "دن", "يوم"), tr("Week", "ہفتہ", "أسبوع"), tr("Month", "مہینہ", "شهر")};
        addChips(h, pl, indexOf(pv, worldPeriod), i -> {
            worldPeriod = pv[i];
            loadWorld(false);
        });

        final String[] sv = {"new", "mag"};
        String[] sl = {tr("Newest", "تازہ ترین", "الأحدث"), tr("Strongest", "سب سے شدید", "الأقوى")};
        addChips(h, sl, indexOf(sv, worldSort), i -> {
            worldSort = sv[i];
            applyWorld();
        });

        worldInfo = tv("", 13, sub, false);
        worldInfo.setPadding(dp(4), dp(8), dp(4), dp(4));
        h.addView(worldInfo, new LinearLayout.LayoutParams(MATCH, WRAP));

        bars = new BarsView();
        h.addView(bars, new LinearLayout.LayoutParams(MATCH, dp(110)));

        lv.addHeaderView(h, null, false);
        worldAd = new QuakeAdapter();
        lv.setAdapter(worldAd);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = parent.getItemAtPosition(pos);
            if (o instanceof Quake) showDetail((Quake) o);
        });
        return lv;
    }

    private void loadWorld(final boolean force) {
        final String src = Store.source(this);
        final String mag = worldMag;
        final String per = worldPeriod;
        final String key = src + "|" + mag + "|" + per;
        if (worldData != null && key.equals(worldKey)) {
            applyWorld();
            if (!force && System.currentTimeMillis() - worldTime < 120000L) return;
        }
        if (worldLoading && !force && key.equals(worldLoadKey)) return;
        final int req = ++worldReq;
        worldLoading = true;
        worldLoadKey = key;
        if (worldData == null || !key.equals(worldKey)) {
            worldData = null;
            if (worldAd != null) worldAd.set(new ArrayList<Quake>());
            if (bars != null) bars.setCounts(new int[5]);
            setInfo(worldInfo, loadingText(), sub);
        }
        new Thread(() -> {
            try {
                final Feed.Result r = Feed.fetch(MainActivity.this, src, mag, per, true);
                runOnUiThread(() -> {
                    if (req != worldReq) return;
                    worldLoading = false;
                    worldData = r.list;
                    worldKey = key;
                    worldTime = System.currentTimeMillis();
                    worldCacheTime = r.cacheTime;
                    if (!isFinishing()) applyWorld();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (req != worldReq) return;
                    worldLoading = false;
                    worldData = null;
                    worldKey = "";
                    if (!isFinishing() && cur == 1) {
                        if (worldAd != null) worldAd.set(new ArrayList<Quake>());
                        if (bars != null) bars.setCounts(new int[5]);
                        setInfo(worldInfo, errText(), 0xFFE53935);
                    }
                });
            }
        }).start();
    }

    private void applyWorld() {
        if (cur != 1 || worldAd == null) return;
        if (worldData == null) {
            worldAd.set(new ArrayList<Quake>());
            return;
        }
        ArrayList<Quake> out = new ArrayList<Quake>();
        for (Quake q : worldData) {
            if (worldQuery.length() == 0 || q.place.toLowerCase(Locale.US).contains(worldQuery)) out.add(q);
        }
        int[] counts = new int[5];
        double strongest = 0;
        for (Quake q : out) {
            counts[Geo.bucket5(q.mag)]++;
            if (q.mag > strongest) strongest = q.mag;
        }
        if ("mag".equals(worldSort)) {
            Collections.sort(out, (a, b) -> {
                int c = Double.compare(b.mag, a.mag);
                return c != 0 ? c : Long.compare(b.time, a.time);
            });
        }
        int total = out.size();
        List<Quake> shown = total > 500 ? new ArrayList<Quake>(out.subList(0, 500)) : out;
        worldAd.set(shown);
        if (bars != null) bars.setCounts(counts);
        String s;
        if (total == 0) {
            s = tr("No earthquakes match.", "کوئی زلزلہ نہیں ملا۔", "لا توجد زلازل مطابقة.");
        } else {
            s = String.format(Locale.US, tr("%1$d earthquakes \u00B7 strongest M%2$s",
                    "%1$d زلزلے \u00B7 سب سے شدید M%2$s",
                    "%1$d زلزال \u00B7 الأقوى M%2$s"), total, Geo.fmt1(strongest));
            if (total > 500) {
                s += " (" + tr("showing first 500", "پہلے 500 دکھائے گئے", "عرض أول 500") + ")";
            }
        }
        if ("all".equals(worldMag) && "month".equals(worldPeriod)) {
            s += "\n" + tr("Month with \"All\" shows M2.5+ only.",
                    "مہینہ کے ساتھ \"سب\" میں صرف M2.5+ دکھائے جاتے ہیں۔",
                    "عند اختيار شهر مع «الكل» تُعرض الزلازل M2.5+ فقط.");
        }
        if (worldCacheTime > 0) s += offlineNote(worldCacheTime);
        setInfo(worldInfo, s, sub);
    }

    /** Small bar chart: number of earthquakes per magnitude group. */
    private class BarsView extends View {
        private int[] counts = new int[5];
        private final String[] labels = {"<2", "2\u20133.9", "4\u20134.9", "5\u20135.9", "6+"};
        private final int[] cols = {0xFF607D8B, 0xFF43A047, 0xFFFB8C00, 0xFFE53935, 0xFF8E24AA};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BarsView() {
            super(MainActivity.this);
        }

        void setCounts(int[] c) {
            counts = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();
            float rowH = h / 5f;
            int max = 1;
            for (int c : counts) if (c > max) max = c;
            float labelW = dp(52);
            float numW = dp(48);
            float barMax = w - labelW - numW;
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < 5; i++) {
                float top = i * rowH;
                paint.setColor(sub);
                cv.drawText(labels[i], 0, top + rowH * 0.68f, paint);
                float bw = Math.max(barMax * counts[i] / max, dp(2));
                paint.setColor(cols[i]);
                cv.drawRoundRect(new RectF(labelW, top + rowH * 0.15f, labelW + bw, top + rowH * 0.85f), dp(4), dp(4), paint);
                paint.setColor(txt);
                cv.drawText(String.valueOf(counts[i]), labelW + bw + dp(6), top + rowH * 0.68f, paint);
            }
        }
    }

    // ------------------------------------------------------------------ list adapter

    private class QuakeAdapter extends BaseAdapter {
        private List<Quake> items = new ArrayList<Quake>();
        private boolean hasLoc = false;
        private double ulat = 0;
        private double ulon = 0;

        void set(List<Quake> l) {
            items = l;
            hasLoc = Store.useLoc(MainActivity.this) && Store.hasLoc(MainActivity.this);
            ulat = Store.lat(MainActivity.this);
            ulon = Store.lon(MainActivity.this);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Holder h;
            if (cv == null) {
                h = new Holder();
                LinearLayout outer = new LinearLayout(MainActivity.this);
                outer.setLayoutParams(new AbsListView.LayoutParams(MATCH, WRAP));
                outer.setPadding(dp(12), dp(5), dp(12), dp(5));
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                row.setBackground(rounded(card, 12));
                outer.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
                h.badge = tv("", 17, Color.WHITE, true);
                h.badge.setGravity(Gravity.CENTER);
                row.addView(h.badge, new LinearLayout.LayoutParams(dp(54), dp(54)));
                LinearLayout col = vbox();
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.setMarginStart(dp(12));
                row.addView(col, cp);
                h.place = tv("", 15, txt, true);
                h.meta = tv("", 12, sub, false);
                h.dist = tv("", 12, ACC, false);
                col.addView(h.place);
                col.addView(h.meta);
                col.addView(h.dist);
                outer.setTag(h);
                cv = outer;
            } else {
                h = (Holder) cv.getTag();
            }
            Quake q = items.get(pos);
            h.badge.setText(Geo.fmt1(q.mag));
            h.badge.setBackground(rounded(Geo.magColor(q.mag), 14));
            h.place.setText(q.place.length() > 0 ? q.place : tr("Unknown location", "نامعلوم مقام", "مكان غير معروف"));
            h.meta.setText(L.ago(MainActivity.this, System.currentTimeMillis() - q.time) + " \u00B7 "
                    + String.format(Locale.US, tr("Depth %s km", "گہرائی %s کلومیٹر", "العمق %s كم"), Geo.fmt1(q.depth)));
            if (hasLoc) {
                double d = Geo.km(ulat, ulon, q.lat, q.lon);
                int sec = Geo.sector(Geo.bearing(ulat, ulon, q.lat, q.lon));
                h.dist.setText(String.format(Locale.US,
                        tr("\uD83D\uDCCD %1$s km away, %2$s of you",
                                "\uD83D\uDCCD آپ سے %1$s کلومیٹر دور، سمت: %2$s",
                                "\uD83D\uDCCD يبعد %1$s كم، الاتجاه: %2$s"),
                        Geo.fmtDist(d), L.dir(MainActivity.this, sec)));
                h.dist.setVisibility(View.VISIBLE);
            } else {
                h.dist.setVisibility(View.GONE);
            }
            return cv;
        }
    }

    // ------------------------------------------------------------------ dialogs

    private Dialog makeDialog(View contentView) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        contentView.setLayoutDirection(L.rtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        d.setContentView(contentView);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private void showWide(Dialog d) {
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), WRAP);
        }
    }

    private void kv(LinearLayout box, String k, String v) {
        TextView kt = tv(k, 12, sub, false);
        kt.setPadding(0, dp(10), 0, 0);
        box.addView(kt);
        box.addView(tv(v, 15, txt, false));
    }

    private void showDetail(final Quake q) {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(rounded(card, 16));
        LinearLayout box = vbox();
        box.setPadding(dp(18), dp(18), dp(18), dp(14));
        sv.addView(box);

        LinearLayout hd = hbox();
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = tv(Geo.fmt1(q.mag), 22, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Geo.magColor(q.mag), 16));
        hd.addView(badge, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView ttl = tv(q.place.length() > 0 ? q.place : "M" + Geo.fmt1(q.mag), 16, txt, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tp.setMarginStart(dp(12));
        hd.addView(ttl, tp);
        box.addView(hd, new LinearLayout.LayoutParams(MATCH, WRAP));

        long now = System.currentTimeMillis();
        String km = tr("km", "کلومیٹر", "كم");
        kv(box, tr("Magnitude", "شدت", "القوة"),
                Geo.fmt1(q.mag) + (q.magType.length() > 0 ? " (" + q.magType + ")" : "")
                        + " \u00B7 " + L.magClass(this, Geo.magClass(q.mag)));
        kv(box, tr("Local time", "مقامی وقت", "الوقت المحلي"),
                fmtTime(q.time, false) + "  (" + L.ago(this, now - q.time) + ")");
        kv(box, tr("UTC time", "یو ٹی سی وقت", "توقيت UTC"), fmtTime(q.time, true));
        kv(box, tr("Depth", "گہرائی", "العمق"),
                Geo.fmt1(q.depth) + " " + km + " \u00B7 " + L.depthClass(this, Geo.depthClass(q.depth)));
        kv(box, tr("Epicenter", "مرکز (ایپی سینٹر)", "مركز الزلزال"),
                String.format(Locale.US, "%.4f, %.4f", q.lat, q.lon));
        if (Store.useLoc(this) && Store.hasLoc(this)) {
            double ulat = Store.lat(this);
            double ulon = Store.lon(this);
            double d = Geo.km(ulat, ulon, q.lat, q.lon);
            int sec = Geo.sector(Geo.bearing(ulat, ulon, q.lat, q.lon));
            kv(box, tr("Distance from you", "آپ سے فاصلہ", "المسافة عنك"),
                    Geo.fmtDist(d) + " " + km + " \u00B7 " + L.dir(this, sec));
        }
        if (q.felt >= 0) {
            kv(box, tr("Felt reports", "محسوس کرنے کی رپورٹیں", "بلاغات الشعور"),
                    String.format(Locale.US, tr("%d people reported feeling it",
                            "%d لوگوں نے محسوس کرنے کی رپورٹ دی",
                            "أبلغ %d شخصًا عن شعورهم به"), q.felt));
        }
        if (q.alert.length() > 0) {
            kv(box, tr("Alert level (PAGER)", "الرٹ لیول (PAGER)", "مستوى التنبيه (PAGER)"), q.alert);
        }
        if (q.tsunami == 1) {
            kv(box, tr("Tsunami", "سونامی", "تسونامي"),
                    tr("Oceanic-region event. This is not a warning; follow official tsunami alerts.",
                            "سمندری علاقے کا واقعہ۔ یہ وارننگ نہیں؛ سرکاری سونامی الرٹ دیکھیں۔",
                            "حدث في منطقة بحرية. هذا ليس تحذيرًا؛ تابع تنبيهات التسونامي الرسمية."));
        }
        if (q.sig > 0) {
            kv(box, tr("Significance", "اہمیت (sig)", "الأهمية (sig)"), String.valueOf(q.sig));
        }
        if (q.status.length() > 0) {
            String st = q.status;
            if ("reviewed".equals(st)) {
                st = tr("Reviewed by seismologists", "ماہرین نے جانچ لیا", "روجع من قبل الخبراء");
            } else if ("automatic".equals(st)) {
                st = tr("Automatic (may change)", "خودکار (بدل سکتا ہے)", "تلقائي (قد يتغير)");
            }
            kv(box, tr("Status", "اسٹیٹس", "الحالة"), st);
        }
        kv(box, tr("Source", "ماخذ", "المصدر"), q.source);

        LinearLayout br = hbox();
        final String pageUrl = q.url;
        if (pageUrl.length() > 0) {
            addBtn(br, tr("Open page", "صفحہ کھولیں", "فتح الصفحة"), ACC, Color.WHITE, v -> openUrl(pageUrl));
        }
        addBtn(br, tr("Map", "نقشہ", "الخريطة"), ACC, Color.WHITE, v -> openMap(q));
        addBtn(br, tr("Share", "شیئر", "مشاركة"), ACC, Color.WHITE, v -> shareQuake(q));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, WRAP);
        bp.setMargins(0, dp(16), 0, 0);
        box.addView(br, bp);

        final Dialog d = makeDialog(sv);
        TextView close = button(tr("Close", "بند کریں", "إغلاق"), chipBg, txt, v -> d.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cp.setMargins(dp(3), dp(8), dp(3), 0);
        box.addView(close, cp);
        showWide(d);
    }

    private void openUrl(String u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            toast(tr("No app found to open this.", "اسے کھولنے کے لیے کوئی ایپ نہیں ملی۔", "لا يوجد تطبيق لفتح هذا."));
        }
    }

    private void openMap(Quake q) {
        try {
            String pos = String.format(Locale.US, "%.5f,%.5f", q.lat, q.lon);
            String uri = "geo:" + pos + "?q=" + pos + "(" + Uri.encode("M" + Geo.fmt1(q.mag)) + ")";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) {
            toast(tr("No map app found.", "کوئی نقشہ ایپ نہیں ملی۔", "لا يوجد تطبيق خرائط."));
        }
    }

    private void shareQuake(Quake q) {
        try {
            String text = "M" + Geo.fmt1(q.mag) + " - " + q.place + "\n" + fmtTime(q.time, false) + "\n"
                    + tr("Depth", "گہرائی", "العمق") + ": " + Geo.fmt1(q.depth) + " "
                    + tr("km", "کلومیٹر", "كم")
                    + (q.url.length() > 0 ? "\n" + q.url : "");
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, tr("Share", "شیئر", "مشاركة")));
        } catch (Exception e) {
            toast(tr("Could not share.", "شیئر نہیں ہو سکا۔", "تعذّرت المشاركة."));
        }
    }

    private void confirmExit() {
        LinearLayout box = vbox();
        box.setBackground(rounded(card, 16));
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.addView(tv(tr("Exit?", "باہر نکلیں؟", "الخروج؟"), 18, txt, true));
        String msg = tr("Close QuakeWatch?", "زلزلہ واچ بند کریں؟", "إغلاق رصد الزلازل؟");
        if (Store.notif(this)) {
            msg += "\n" + tr("Earthquake alerts keep running in the background.",
                    "زلزلے کے الرٹس پس منظر میں چلتے رہیں گے۔",
                    "ستستمر تنبيهات الزلازل في الخلفية.");
        }
        TextView m = tv(msg, 14, sub, false);
        m.setPadding(0, dp(8), 0, dp(14));
        box.addView(m);
        LinearLayout row = hbox();
        final Dialog d = makeDialog(box);
        addBtn(row, tr("No", "نہیں", "لا"), chipBg, txt, v -> d.dismiss());
        addBtn(row, tr("Yes", "ہاں", "نعم"), ACC, Color.WHITE, v -> {
            d.dismiss();
            finish();
        });
        box.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        showWide(d);
    }

    // ------------------------------------------------------------------ Info tab

    private void infoCard(LinearLayout box, String title, String body) {
        LinearLayout c = section(box, title);
        TextView t = tv(body, 14, txt, false);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private View buildInfo() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(8), dp(12), dp(16));
        sv.addView(box);

        String scaleEn = "Below 2.0 \u2013 Micro: usually not felt.\n"
                + "2.0\u20133.9 \u2013 Minor: often felt slightly; rarely causes damage.\n"
                + "4.0\u20134.9 \u2013 Light: noticeable shaking; rarely serious damage.\n"
                + "5.0\u20135.9 \u2013 Moderate: can damage weak buildings.\n"
                + "6.0\u20136.9 \u2013 Strong: damaging in populated areas.\n"
                + "7.0\u20137.9 \u2013 Major: serious damage over large areas.\n"
                + "8.0 and above \u2013 Great: severe destruction, may be felt hundreds of km away.\n\n"
                + "Each step up of 1.0 means about 32 times more energy. Shallow quakes (depth under 70 km) usually shake the ground more strongly.";
        String scaleUr = "2.0 سے کم \u2013 خورد: عموماً محسوس نہیں ہوتا۔\n"
                + "2.0\u20133.9 \u2013 معمولی: اکثر ہلکا سا محسوس ہوتا ہے؛ شاذ ہی نقصان۔\n"
                + "4.0\u20134.9 \u2013 ہلکا: جھٹکے محسوس ہوتے ہیں؛ شاذ ہی شدید نقصان۔\n"
                + "5.0\u20135.9 \u2013 درمیانہ: کمزور عمارتوں کو نقصان پہنچ سکتا ہے۔\n"
                + "6.0\u20136.9 \u2013 شدید: آبادی والے علاقوں میں نقصان۔\n"
                + "7.0\u20137.9 \u2013 بہت شدید: وسیع علاقے میں بڑا نقصان۔\n"
                + "8.0 اور اوپر \u2013 انتہائی شدید: بڑی تباہی، سینکڑوں کلومیٹر دور تک محسوس ہو سکتا ہے۔\n\n"
                + "ہر 1.0 اضافے کا مطلب تقریباً 32 گنا زیادہ توانائی ہے۔ کم گہرائی (70 کلومیٹر سے کم) والے زلزلے عموماً زمین کو زیادہ زور سے ہلاتے ہیں۔";
        String scaleAr = "أقل من 2.0 \u2013 طفيف جدًا: لا يُشعر به عادةً.\n"
                + "2.0\u20133.9 \u2013 صغير: يُشعر به أحيانًا بشكل خفيف؛ نادرًا ما يسبب ضررًا.\n"
                + "4.0\u20134.9 \u2013 خفيف: اهتزاز ملحوظ؛ نادرًا ما يسبب ضررًا كبيرًا.\n"
                + "5.0\u20135.9 \u2013 متوسط: قد يضر بالمباني الضعيفة.\n"
                + "6.0\u20136.9 \u2013 قوي: يسبب أضرارًا في المناطق المأهولة.\n"
                + "7.0\u20137.9 \u2013 كبير: أضرار جسيمة على مساحات واسعة.\n"
                + "8.0 فأكثر \u2013 هائل: دمار شديد وقد يُشعر به على بعد مئات الكيلومترات.\n\n"
                + "كل زيادة بمقدار 1.0 تعني طاقة أكبر بنحو 32 مرة. الزلازل الضحلة (عمق أقل من 70 كم) تهز الأرض عادةً بقوة أكبر.";
        infoCard(box, tr("How strong is it?", "شدت کا پیمانہ", "سلّم القوة"), tr(scaleEn, scaleUr, scaleAr));

        String safeEn = "\u2022 Drop to the ground, take Cover under a sturdy table, and Hold On until shaking stops.\n"
                + "\u2022 Stay away from windows, shelves and heavy objects that can fall.\n"
                + "\u2022 Outdoors: move to an open area away from buildings, walls and power lines.\n"
                + "\u2022 In a vehicle: stop in a clear spot, away from bridges and trees, and stay inside.\n"
                + "\u2022 Do not use elevators. Do not run outside while the shaking is going on.\n"
                + "\u2022 After: expect aftershocks, check for injuries, and if you smell gas, avoid flames and switch off the supply if safe.\n"
                + "\u2022 Follow official instructions from your local disaster authority.";
        String safeUr = "\u2022 زمین پر جھک جائیں، مضبوط میز کے نیچے پناہ لیں اور جھٹکے رکنے تک اسے پکڑے رکھیں۔\n"
                + "\u2022 کھڑکیوں، شیلفوں اور گرنے والی بھاری چیزوں سے دور رہیں۔\n"
                + "\u2022 کھلے میں ہوں تو عمارتوں، دیواروں اور بجلی کی تاروں سے دور کھلی جگہ چلے جائیں۔\n"
                + "\u2022 گاڑی میں ہوں تو پلوں اور درختوں سے دور کھلی جگہ رک جائیں اور اندر ہی رہیں۔\n"
                + "\u2022 لفٹ استعمال نہ کریں۔ جھٹکوں کے دوران باہر نہ بھاگیں۔\n"
                + "\u2022 بعد میں: آفٹر شاکس کے لیے تیار رہیں، زخمیوں کو دیکھیں، اور گیس کی بو آئے تو آگ سے بچیں اور ممکن ہو تو سپلائی بند کر دیں۔\n"
                + "\u2022 اپنے مقامی ادارۂ آفات کی ہدایات پر عمل کریں۔";
        String safeAr = "\u2022 اخفض جسمك إلى الأرض، واحتمِ تحت طاولة متينة، وتمسّك بها حتى يتوقف الاهتزاز.\n"
                + "\u2022 ابتعد عن النوافذ والرفوف والأشياء الثقيلة التي قد تسقط.\n"
                + "\u2022 في الخارج: انتقل إلى مكان مفتوح بعيدًا عن المباني والجدران وأسلاك الكهرباء.\n"
                + "\u2022 في السيارة: توقف في مكان مفتوح بعيدًا عن الجسور والأشجار وابقَ داخلها.\n"
                + "\u2022 لا تستخدم المصعد، ولا تركض إلى الخارج أثناء الاهتزاز.\n"
                + "\u2022 بعد الزلزال: توقّع هزات ارتدادية، وتفقّد الإصابات، وإذا شممت رائحة غاز فابتعد عن اللهب وأغلق الإمداد إن كان ذلك آمنًا.\n"
                + "\u2022 اتبع تعليمات الجهة المحلية المسؤولة عن الكوارث.";
        infoCard(box, tr("What to do in an earthquake", "زلزلے کے دوران کیا کریں", "ماذا تفعل أثناء الزلزال"),
                tr(safeEn, safeUr, safeAr));

        String goodEn = "This is not an early-warning system. Reports from USGS/EMSC arrive several minutes after a quake. "
                + "Small quakes outside well-monitored regions may be missing or listed late, and magnitudes can be revised. "
                + "Place names come from the data source in English. Data: USGS Earthquake Hazards Program and EMSC.";
        String goodUr = "یہ قبل از وقت وارننگ کا نظام نہیں ہے۔ USGS/EMSC کی رپورٹیں زلزلے کے چند منٹ بعد آتی ہیں۔ "
                + "جن علاقوں میں نگرانی کم ہے وہاں چھوٹے زلزلے شامل نہ ہوں یا دیر سے آئیں، اور شدت بعد میں تبدیل ہو سکتی ہے۔ "
                + "جگہوں کے نام ڈیٹا ماخذ سے انگریزی میں آتے ہیں۔ ڈیٹا: USGS اور EMSC۔";
        String goodAr = "هذا ليس نظام إنذار مبكر. تصل تقارير USGS/EMSC بعد الزلزال بدقائق. "
                + "قد تغيب الزلازل الصغيرة في المناطق قليلة الرصد أو تظهر متأخرة، وقد تُعدَّل القوة لاحقًا. "
                + "أسماء الأماكن تأتي من المصدر بالإنجليزية. البيانات: USGS وEMSC.";
        infoCard(box, tr("Good to know", "جاننے کی باتیں", "معلومات مهمة"), tr(goodEn, goodUr, goodAr));
        return sv;
    }

    // ------------------------------------------------------------------ Settings tab

    private View buildSettings() {
        pLang = Store.lang(this);
        pTheme = Store.theme(this);
        pSource = Store.source(this);
        pNotif = Store.notif(this);
        pUseLoc = Store.useLoc(this);
        pRadius = Store.radius(this);
        pInterval = Store.interval(this);
        pMinNear = Store.minNear(this);
        pWorld = Store.worldMin(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(8), dp(12), dp(16));
        sv.addView(box);

        final String[] langV = {"en", "ur", "ar"};
        LinearLayout s1 = section(box, tr("Language", "زبان", "اللغة"));
        addChips(s1, new String[]{"English", "اردو", "العربية"}, indexOf(langV, pLang), i -> pLang = langV[i]);

        final String[] themeV = {"dark", "light"};
        LinearLayout s2 = section(box, tr("Theme", "تھیم", "المظهر"));
        addChips(s2, new String[]{tr("Dark", "ڈارک", "داكن"), tr("Light", "لائٹ", "فاتح")},
                indexOf(themeV, pTheme), i -> pTheme = themeV[i]);

        final String[] srcV = {"usgs", "emsc"};
        LinearLayout s3 = section(box, tr("Data source", "ڈیٹا ماخذ", "مصدر البيانات"));
        addChips(s3, new String[]{"USGS", "EMSC"}, indexOf(srcV, pSource), i -> pSource = srcV[i]);
        note(s3, tr("USGS: worldwide, well tested. EMSC: may list more regional events.",
                "USGS: پوری دنیا، آزمودہ۔ EMSC: ممکن ہے مزید علاقائی واقعات دکھائے۔",
                "USGS: عالمي وموثوق. EMSC: قد يعرض أحداثًا إقليمية أكثر."));

        String on = tr("On", "آن", "تشغيل");
        String off = tr("Off", "آف", "إيقاف");
        LinearLayout s4 = section(box, tr("Notifications", "نوٹیفیکیشنز", "الإشعارات"));
        addChips(s4, new String[]{on, off}, pNotif ? 0 : 1, i -> pNotif = (i == 0));

        LinearLayout s5 = section(box, tr("Use my location", "میرا مقام استعمال کریں", "استخدام موقعي"));
        addChips(s5, new String[]{on, off}, pUseLoc ? 0 : 1, i -> pUseLoc = (i == 0));

        String km = tr("km", "کلومیٹر", "كم");
        final int[] rv = {100, 300, 500, 1000};
        String[] rl = new String[rv.length];
        for (int i = 0; i < rv.length; i++) rl[i] = rv[i] + " " + km;
        LinearLayout s6 = section(box, tr("Alert radius", "الرٹ کا دائرہ", "نطاق التنبيه"));
        addChips(s6, rl, indexOfInt(rv, pRadius), i -> pRadius = rv[i]);

        final double[] nv = {1.0, 2.5, 3.5, 4.5};
        String[] nl = new String[nv.length];
        for (int i = 0; i < nv.length; i++) nl[i] = "M" + Geo.fmt1(nv[i]) + "+";
        LinearLayout s7 = section(box, tr("Minimum magnitude nearby", "قریب کے لیے کم از کم شدت", "أدنى قوة للقريب"));
        addChips(s7, nl, indexOfDouble(nv, pMinNear), i -> pMinNear = nv[i]);

        final double[] wv = {0, 5.0, 6.0, 7.0};
        String[] wl = {off, "M5.0+", "M6.0+", "M7.0+"};
        LinearLayout s8 = section(box, tr("Worldwide alerts (big quakes)", "عالمی الرٹ (بڑے زلزلے)", "تنبيهات عالمية (الكبيرة)"));
        addChips(s8, wl, indexOfDouble(wv, pWorld), i -> pWorld = wv[i]);

        final int[] iv = {5, 10, 15, 30};
        String min = tr("min", "منٹ", "د");
        String[] il = new String[iv.length];
        for (int i = 0; i < iv.length; i++) il[i] = iv[i] + " " + min;
        LinearLayout s9 = section(box, tr("Check every", "چیک کرنے کا وقفہ", "الفحص كل"));
        addChips(s9, il, indexOfInt(iv, pInterval), i -> pInterval = iv[i]);

        TextView save = button(tr("Save", "محفوظ کریں", "حفظ"), ACC, Color.WHITE, v -> saveSettings());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.setMargins(0, dp(2), 0, dp(12));
        box.addView(save, sp);

        LinearLayout s10 = section(box, tr("Background alerts", "پس منظر کے الرٹس", "التنبيهات في الخلفية"));
        note(s10, tr("For alerts while the app is closed: keep notifications on, and on Xiaomi/Oppo/Vivo/Huawei phones enable Autostart and remove battery restrictions. \"Allow all the time\" lets the app refresh your position in the background; otherwise the position from the last time you opened the app is used.",
                "ایپ بند ہونے پر بھی الرٹ کے لیے: نوٹیفیکیشن آن رکھیں، اور Xiaomi/Oppo/Vivo/Huawei فونز میں Autostart آن کریں اور بیٹری کی پابندی ہٹائیں۔ \"ہر وقت اجازت\" دینے سے ایپ پس منظر میں آپ کا مقام تازہ کر سکتی ہے؛ ورنہ ایپ آخری بار کھولنے کے وقت کا مقام استعمال ہوتا ہے۔",
                "لتصلك التنبيهات والتطبيق مغلق: أبقِ الإشعارات مفعّلة، وفي هواتف Xiaomi/Oppo/Vivo/Huawei فعّل التشغيل التلقائي (Autostart) وأزل قيود البطارية. خيار «السماح طوال الوقت» يتيح للتطبيق تحديث موقعك في الخلفية، وإلا يُستخدم موقعك عند آخر فتح للتطبيق."));
        addUtil(s10, tr("Allow location all the time", "لوکیشن ہر وقت کی اجازت", "السماح بالموقع طوال الوقت"), v -> requestBackgroundLocation());
        addUtil(s10, tr("Battery settings", "بیٹری سیٹنگز", "إعدادات البطارية"), v -> openBatterySettings());
        addUtil(s10, tr("App permissions", "ایپ کی اجازتیں", "أذونات التطبيق"), v -> openAppSettings());
        addUtil(s10, tr("Send test notification", "ٹیسٹ نوٹیفیکیشن بھیجیں", "إرسال إشعار تجريبي"), v -> {
            Notifier.test(this);
            toast(tr("If you do not see it, allow notifications in App permissions.",
                    "اگر نظر نہ آئے تو ایپ کی اجازتوں میں نوٹیفیکیشن کی اجازت دیں۔",
                    "إن لم يظهر، اسمح بالإشعارات من أذونات التطبيق."));
        });

        LinearLayout s11 = section(box, tr("Privacy", "رازداری", "الخصوصية"));
        note(s11, tr("Your location never leaves this phone. It is only used here to measure the distance to earthquakes. The app downloads public earthquake lists from USGS or EMSC (their servers see only your IP address, not your location). Turn off \"Use my location\" to stop location use completely.",
                "آپ کا مقام فون سے باہر نہیں جاتا۔ وہ صرف یہاں زلزلوں سے فاصلہ ناپنے کے لیے استعمال ہوتا ہے۔ ایپ USGS یا EMSC سے زلزلوں کی عوامی فہرست ڈاؤن لوڈ کرتی ہے (ان کے سرورز کو صرف آپ کا IP ایڈریس نظر آتا ہے، مقام نہیں)۔ مقام کا استعمال مکمل بند کرنے کے لیے \"میرا مقام استعمال کریں\" آف کریں۔",
                "لا يغادر موقعك هاتفك؛ يُستخدم هنا فقط لقياس المسافة إلى الزلازل. ينزّل التطبيق قوائم الزلازل العامة من USGS أو EMSC (ترى خوادمهم عنوان IP الخاص بك فقط وليس موقعك). أوقف «استخدام موقعي» لإيقاف استخدام الموقع كليًا."));
        return sv;
    }

    private void addUtil(LinearLayout parent, String label, View.OnClickListener l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.setMargins(0, dp(8), 0, 0);
        parent.addView(button(label, chipBg, txt, l), p);
    }

    private void saveSettings() {
        Store.setLang(this, pLang);
        Store.setTheme(this, pTheme);
        Store.setSource(this, pSource);
        Store.setNotif(this, pNotif);
        Store.setUseLoc(this, pUseLoc);
        Store.setRadius(this, pRadius);
        Store.setInterval(this, pInterval);
        Store.setMinNear(this, pMinNear);
        Store.setWorldMin(this, pWorld);
        Notifier.ensureChannels(this);
        nearRadius = pRadius;
        nearKey = "";
        worldKey = "";
        nearData = null;
        worldData = null;
        if (!pNotif) {
            stopService(new Intent(this, QuakeService.class));
            Poller.cancel(this);
        }
        cur = 3;
        build();
        toast(tr("Saved \u2713", "محفوظ ہو گیا \u2713", "تم الحفظ \u2713"));
        if (pNotif || pUseLoc) ensurePermsAndStart();
    }

    // ------------------------------------------------------------------ permissions and service

    @TargetApi(33)
    private boolean notifPermMissing() {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private void ensurePermsAndStart() {
        ArrayList<String> need = new ArrayList<String>();
        if (Store.useLoc(this) && !LocHelper.hasPerm(this)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && Store.notif(this) && notifPermMissing()) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (need.isEmpty()) {
            afterPerms(true);
        } else {
            requestPermissions(need.toArray(new String[0]), RC_PERM);
        }
    }

    private void afterPerms(boolean refresh) {
        if (Store.useLoc(this) && LocHelper.hasPerm(this)) {
            LocHelper.updateFromLast(this);
            LocHelper.requestFresh(this, () -> {
                if (!isFinishing()) {
                    updateLocLabel();
                    applyNearby();
                }
            });
        }
        if (Store.notif(this)) startQuakeService();
        updateLocLabel();
        if (refresh) reload(false);
    }

    private void startQuakeService() {
        try {
            Intent i = new Intent(this, QuakeService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startFg(i);
            } else {
                startService(i);
            }
        } catch (Exception ignore) {
            // the alarm-based checks still work without the service
            Poller.schedule(this);
        }
    }

    @TargetApi(26)
    private void startFg(Intent i) {
        startForegroundService(i);
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < 29) {
            toast(tr("Not needed on this Android version.", "اس اینڈرائیڈ ورژن پر ضرورت نہیں۔", "غير مطلوب في هذا الإصدار."));
            return;
        }
        if (!LocHelper.hasPerm(this)) {
            toast(tr("Allow location first.", "پہلے لوکیشن کی اجازت دیں۔", "اسمح بالموقع أولًا."));
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, RC_PERM);
            return;
        }
        reqBg29();
    }

    @TargetApi(29)
    private void reqBg29() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, RC_BG);
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        } catch (Exception e) {
            toast(tr("Could not open settings.", "سیٹنگز نہیں کھل سکیں۔", "تعذّر فتح الإعدادات."));
        }
    }
}
