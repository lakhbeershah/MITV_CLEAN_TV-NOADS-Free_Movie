# Clean Xiaomi TV — Ultra-Debloated & Custom Apple TV-Style Launcher

A lightweight, ad-free, high-performance transformation for **Xiaomi Android TVs** (tested on Xiaomi MiTV-AXSO0 / Mi TV 4A & 4C Pro, Android TV 9 Pie, Amlogic SoC).

Replaces laggy PatchWall, removes preloaded bloatware and telemetry, frees up over **40% RAM**, and installs a privacy-friendly, zero-network custom Apple TV-style launcher (`com.gathin.tvhome`) with designer typography and smart remote shortcuts.

---

## 🚀 Key Features Implemented

### 1. 🎨 Minimalist Apple TV-Style UI & Designer Typography
* **Circular Icon-Only Top Pills:** Frosted glass circular buttons for **Inputs**, **Wi-Fi**, **Bluetooth**, and **Settings** (no text clutter).
* **Outfit Semi-Bold (Weight 600) Typography:** Bold, crisp modern clock (`h:mm`) and dock app titles for a clean Swiss/Apple aesthetic.
* **Frosted Glass Dock:** Translucent dock band matching the top pills so wallpapers and ambient light bleed through cleanly.
* **Dynamic Horizon Glow:** Soft atmospheric gradient washing down from the top edge, framing the clock and status bar.

### 2. 🕉️ Hindu Festival & Personalized Greeting Engine
* **Everyday Greeting:** Welcomes you with **`RADHE RADHE, BUNTY JI`**.
* **Automatic Calendar Detection:** Pre-calculated Hindu lunisolar calendar dates (2024–2035):
  * **Diwali Days (Dhanteras to Bhai Dooj):** Automatically switches to **`RADHE RADHE, BUNTY JI | HAPPY DIWALI`** with warm diya gold accent.
  * **Holi Days (Holika Dahan to Rangwali Holi):** Automatically switches to **`RADHE RADHE, BUNTY JI | HAPPY HOLI`**.
* **Zero Hijri/Shamsi Calendars:** All Islamic date engines (`shamsi_date`) remain permanently disabled.

### 3. 📊 Live System Performance Monitor
* Real-time device health directly beneath the clock:
  * **CPU %** load
  * **Free RAM** (e.g. `Free 412 MB / 1 GB`)
  * **Free internal storage**
* Updates every 10 seconds via background thread with 0 GPU/UI frame drops.

### 4. 🎮 Remote Button Remapping (Xiaomi Bluetooth Remote)
* **Tap "Mi" Button:** Instantly opens **SmartTube** (Ad-free 4K YouTube client).
* **Hold "Mi" Button (~0.5s):** Automatically opens **TV Bro** with Google search for **`hdmovie2`** ready on screen.

### 5. 📺 Ad-Free Default YouTube Experience
* Disabled stock YouTube (`com.google.android.youtube.tv` & `youtube.tvmusic`).
* Re-routed system `MEDIA_PLAY_FROM_SEARCH` and `vnd.youtube` intents directly to **SmartTube**.
* Voice commands to Google Assistant (*"Play Arijit Singh on YouTube"*) launch and play seamlessly in SmartTube with zero ads.

### 6. 🛡️ System-Wide Ad & Tracker Blocking
* Configured native Android Private DNS with **AdGuard DNS** (`dns.adguard.com`).
* Blocks popups, redirects, and banner ads on free streaming websites like `hdmovie2` inside TV Bro.

### 7. 🧹 RAM & Storage Optimizations (1 GB RAM TV)
* **Background Process Limit:** Restricted to 3 processes (`background_process_limit = 3`) to auto-kill memory leaks on app exit.
* **Xiaomi Bloat Auto-Restart Blocked:** Applied AppOps restrictions (`RUN_IN_BACKGROUND ignore`, `AUTO_START ignore`) on Xiaomi analytics and partner services.
* **Ethernet Sleep Disconnect Fix:** Set `wifi_sleep_policy = 2` so network stays awake for instant connection on wake.
* **Storage Cleaner:** Automated purge of hidden `.thumbnails`, app caches, and orphaned browser temp video files.

---

## 📱 Dock & Grid Layout

* **Top Dock (Row 1):**
  1. SmartTube (`org.smarttube.stable`)
  2. TV Bro Browser (`com.phlox.tvwebbrowser`)
  3. JioHotstar (`in.startv.hotstar`)
  4. Xiaomi TV+ (`com.mitv.tvhome.mitvplus`)
* **Next Line (Grid Row 2):**
  1. Send Files to TV (`com.yablio.sendfilestotv`)
  2. Miracast Screen Mirroring (`com.xiaomi.mitv.smartshare`)
  3. Google Play Store (`com.android.vending`)

---

## 🗑️ Debloated & Disabled Packages

Every package was safely disabled using `pm disable-user --user 0` (never uninstalled, 100% reversible via `tools/restore.ps1`):
* **PatchWall & Ads:** `com.mitv.tvhome.atv`, `com.glance.tv`, `co.sensara.tv.mitv`, `com.xm.webcontent`, `com.xiaomi.floatingframe`
* **Xiaomi & Google Telemetry:** `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`
* **Unused Bloat Apps:** `com.amazon.amazonvideo.livingroom` (Prime Video), `com.mitv.dream` (Wallpaper app), `com.xiaomi.mitv.mediaexplorer` (Media player), `com.mitv.milinkservice` (Mi Link), `com.google.android.apps.mediashell` (Chromecast built-in)
* **Partner Bloat:** `com.fancode.tv`, `com.suntv.sunnxt.mitv`, `com.eros.mitv`, `com.discoveryplus.mitv`, `com.ddnational.prasarbharati`, `net.aljazeera.english`

---

## 🛠️ How to Deploy on Your TV

### 1. Connect over ADB
```powershell
$env:TV_HOST = "<tv ip>"
python tools/tv.py --probe
```

### 2. Apply Debloat List
```powershell
powershell -ExecutionPolicy Bypass -File tools/disable.ps1 -TvHost <tv ip> examples/xiaomi-mitv-axso0/debloat.txt
```

### 3. Build & Install Custom Launcher
```powershell
powershell -ExecutionPolicy Bypass -File launcher/build-and-install.ps1 -TvHost <tv ip> -StockLauncher none -InputKeyService
```

### 4. Apply System Optimizations (AdGuard DNS, Process Limit, Audio DRC)
```powershell
# Private DNS AdBlock
python tools/tv.py "settings put global private_dns_mode hostname && settings put global private_dns_specifier dns.adguard.com"

# Background RAM Auto-Clean
python tools/tv.py "settings put global background_process_limit 3"

# Audio Clear Dialogue & Night Mode
python tools/tv.py "settings put system sound_effect_night_mode 1 && settings put global drc_mode 1"

# Network Sleep Fix
python tools/tv.py "settings put global wifi_sleep_policy 2"
```

---

## 💡 Top Ideas People Can Add Next
1. **Live Cricket / IPL Score Ticker:** Companion card add-on in `addons/cricket` fetching live scores from public cricket APIs.
2. **Weather Card Widget:** Build and install `addons/weather` for real-time temperature and 3-day forecast on Home.
3. **Screen-Off Audio Mode:** Play YouTube audio/bhajans with the TV LED display turned completely black to save electricity.
4. **Long-Press "Back" to Kill App:** Kill stuck video players instantly by holding the Back button.
5. **Night Sleep Timer Shortcut:** 30m / 60m / 90m auto-standby toggle directly on the top bar.

---

## 📜 License
Released under the MIT License. Based on the [CleanYourTV](https://github.com/Gathin23/cleanyourtv) architecture.
