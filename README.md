# QuakeWatch

Android app (Java only, no AndroidX, no external libraries) that shows earthquake details
and sends a notification when an earthquake happens near your position.
Package: `com.blackfalcon.quakewatch`. UI languages: English (default), Urdu, Arabic.

## What the app does
- **Nearby tab**: earthquakes within 100 / 300 / 500 / 1000 km of your saved position
  (last 24 hours, 7 days or 30 days), with distance and direction from you.
- **World tab**: worldwide list with filters (M2.5+, M4.5+, all; hour / day / week / month),
  place search, sort by newest or strongest, and a small magnitude chart.
- **Details** (tap an earthquake): magnitude and type, local and UTC time, depth, coordinates,
  distance from you, felt reports, PAGER alert level, tsunami flag (USGS), status, source;
  buttons to open the source page, open a map app, and share.
- **Info tab**: magnitude scale, what to do during an earthquake, and limits of the data.
- **Notifications**: a foreground service plus a repeating alarm checks for new earthquakes every
  5 / 10 / 15 / 30 minutes (default 10). You get an alert for an earthquake inside your chosen radius
  with at least your chosen magnitude, and optionally for big earthquakes anywhere (M5+/M6+/M7+).
  The checks restart after the phone reboots.
- **Settings**: language (English default), dark/light theme (dark default), data source (USGS or EMSC),
  notifications on/off, use of location on/off, radius, minimum magnitude, check interval.
- Exit confirmation on Back; "By: Black Falcon" on the main screen; Splash screen first.

## How to build
1. Upload the contents of this folder to a GitHub repository (the folder with `build.gradle` and `.github` must be the repository root).
2. Open the **Actions** tab, run **Build APK** (it also runs on every push).
3. Download the `QuakeWatch-APK` artifact and install the APK.

## Data and privacy
- Earthquake lists come from the USGS GeoJSON feeds (default) or the EMSC web service.
- **Your location never leaves the phone.** It is used only to compute distances. The feeds are global,
  so the servers receive only a normal web request (your IP address), not your position.
  Turn off "Use my location" in Settings to stop location use completely.
- Internet is needed for fresh data. Without it the app shows the last downloaded list (marked as offline).

## Limitations (honest list)
- **Not an early-warning system.** Data arrives minutes after the earthquake. Follow official instructions in an emergency.
- USGS lists most earthquakes of M4.5+ worldwide, but small ones outside well-monitored regions
  (for example parts of Asia) can be missing or late. EMSC may show more regional events.
- Magnitudes are often revised after the first report. An earthquake is notified once, using its first matching report.
- Position in the background: Android only allows background location with "Allow all the time".
  Without it, the position from the last time you opened the app (or pressed "Update my location") is used,
  so after traveling far open the app once.
- Android can delay background checks when the phone is idle (Doze). For best results turn notifications on,
  remove the battery restriction (Settings > Battery settings), and on **Xiaomi / Oppo / Vivo / Huawei** phones
  enable **Autostart** and set battery usage to "No restrictions".
- The foreground service shows a permanent low-priority notification; Android requires this.
- Place names come from the data source in English.
- EMSC support was written from its public documentation and has not been tested against the live server;
  if EMSC shows an error, switch back to USGS in Settings.
- Android 13+ asks permission for notifications; Android 10+ asks separately for background location.

## اردو (مختصر)
- یہ ایپ دنیا بھر کے زلزلوں کی تفصیل دکھاتی ہے اور آپ کے مقام کے قریب زلزلہ آنے پر نوٹیفیکیشن بھیجتی ہے۔
- آپ کا مقام فون سے باہر نہیں جاتا؛ صرف فاصلہ ناپنے کے لیے استعمال ہوتا ہے۔ سیٹنگز میں مقام کا استعمال بند کیا جا سکتا ہے۔
- تازہ ڈیٹا کے لیے انٹرنیٹ ضروری ہے؛ انٹرنیٹ نہ ہو تو آخری محفوظ فہرست دکھائی جاتی ہے۔
- یہ قبل از وقت وارننگ کا نظام نہیں؛ رپورٹیں زلزلے کے چند منٹ بعد آتی ہیں۔ چھوٹے زلزلے بعض علاقوں میں شامل نہیں ہوتے۔
- ایپ بند ہونے پر الرٹس کے لیے: نوٹیفیکیشن آن رکھیں، بیٹری کی پابندی ہٹائیں، اور Xiaomi/Oppo/Vivo/Huawei میں Autostart آن کریں۔
- پس منظر میں مقام تازہ رکھنے کے لیے "Allow all the time" دینا پڑتا ہے؛ ورنہ ایپ آخری بار کھولنے کا مقام استعمال کرتی ہے۔
