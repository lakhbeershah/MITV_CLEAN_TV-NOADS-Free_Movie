# 📺 Clean Xiaomi TV — Ultra-Debloated, Ad-Free & Free Movie Setup

> **Transform your slow, ad-ridden Xiaomi Android TV into a blazing-fast, Apple TV-style home screen with zero ads, free live TV, and 1-click movie streaming!**

Tested on **Xiaomi Mi TV (MiTV-AXSO0 / Mi TV 4A & 4C Pro)**, Android TV 9 (Pie), Amlogic SoC. Works on all modern Xiaomi / Redmi Android TVs.

---

## 🌟 What This Project Does For Your TV

| Before (Stock Xiaomi TV) | After (This Setup) |
|---|---|
| ❌ Slow, laggy PatchWall launcher with banner ads | ✅ **Apple TV-style minimalist glass home screen** |
| ❌ Video ads on YouTube and popups on movie sites | ✅ **100% Ad-Free YouTube (SmartTube) + AdGuard DNS** |
| ❌ Only ~180 MB free RAM (stuttering & app crashes) | ✅ **Over 410 MB free RAM (smooth animations, no lag)** |
| ❌ 25+ hidden background bloatware services | ✅ **All bloat disabled safely (100% reversible)** |
| ❌ Useless "Mi" button opening laggy PatchWall | ✅ **Mi button: Tap = SmartTube, Hold = Free Movies (hdmovie2)** |
| ❌ Random generic greetings | ✅ **Custom "Radhe Radhe" + Automatic Diwali & Holi festive themes** |

---

## 📦 Essential Apps Included in This Repo (`apps/`)

All APKs are bundled directly inside the [`apps/`](apps/) folder so anyone can install them on their TV in one click:

1. **SmartTube (`SmartTube.apk`):**
   * The ultimate ad-free YouTube app for TV.
   * Auto-skips sponsors, intros, and ads.
   * Configured as your TV's **100% default YouTube** (voice search routes directly into SmartTube).
2. **TV Bro (`TVBro.apk`):**
   * Built-in ad-blocker designed specifically for TV remotes.
   * Seamless movie streaming on sites like `hdmovie2` without spam redirects.
3. **Xiaomi TV+ (`XiaomiTVPlus.apk`):**
   * 100+ free live streaming channels (news, movies, lifestyle) with zero subscription.
4. **Send Files to TV (`SendFilesToTV.apk`):**
   * Easily send APKs, wallpapers, and videos directly from your smartphone or PC to your TV.

---

## 🎮 Remote Control Superpowers

Your Xiaomi Bluetooth Remote is supercharged:
* **Tap "Mi" Button:** Instantly launches **SmartTube** (YouTube without ads).
* **Hold "Mi" Button (~0.5 second):** Automatically opens **TV Bro** with Google search for **`hdmovie2`** ready to watch free movies.
* **Mic Button:** Say *"Play Arijit Singh songs on YouTube"* $\rightarrow$ Plays directly in SmartTube!

---

## 🎨 Clean & Modern Minimalist Look

* **Designer Typography:** Bold Outfit Semi-Bold (weight 600) clock and dock app titles.
* **Top Status Icons:** Circular frosted glass icon buttons for **Inputs**, **Wi-Fi**, **Bluetooth**, and **Settings** (no text clutter).
* **Live System Health:** Real-time **CPU %**, **Free RAM**, and **Free Storage** displayed cleanly under the clock.
* **Hindu Festival Engine:**
  * Daily: **`RADHE RADHE, BUNTY JI`**
  * Diwali season (Dhanteras through Bhai Dooj): **`RADHE RADHE, BUNTY JI | HAPPY DIWALI`** with festive diya gold accents.
  * Holi days: **`RADHE RADHE, BUNTY JI | HAPPY HOLI`**.
  * Zero Islamic or Hijri calendar clutter (`shamsi_date = false`).

---

## 🚀 Easy 3-Step Setup Guide (For Anyone)

### **Step 1: Turn on Network Debugging on your TV**
1. On your TV, go to **Settings** $\rightarrow$ **Device Preferences** $\rightarrow$ **About**.
2. Scroll to **Build** and click the remote **OK** button **7 times** until it says *"You are now a developer!"*.
3. Go back to **Device Preferences** $\rightarrow$ **Developer Options**:
   * Turn **ON** **USB Debugging**.
   * Turn **ON** **Network/ADB Debugging**.
4. Check your TV's IP address under **Settings** $\rightarrow$ **Network & Internet** (e.g. `192.168.1.45`).

---

### **Step 2: Run the Setup on your Computer**
Open PowerShell on your Windows PC in this repository folder:

```powershell
# 1. Connect to your TV
$env:TV_HOST = "<your-tv-ip>"

# 2. Debloat the TV (Disable PatchWall, Prime Video & telemetry)
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost <your-tv-ip> examples\xiaomi-mitv-axso0\debloat.txt

# 3. Install the Custom Apple TV Launcher
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <your-tv-ip> -StockLauncher none -InputKeyService

# 4. Install all Included Apps (SmartTube, TV Bro, Xiaomi TV+, Send Files to TV)
powershell -ExecutionPolicy Bypass -File tools\install-apps.ps1 -TvHost <your-tv-ip>
```

---

### **Step 3: Enable System Ad-Blocking & Audio Boost**
Run these 3 commands to enable AdGuard DNS, dialogue boost, and RAM auto-cleaning:

```powershell
# Block all website & app ads system-wide via AdGuard DNS:
python tools\tv.py "settings put global private_dns_mode hostname && settings put global private_dns_specifier dns.adguard.com"

# Prevent background apps from hoarding your RAM:
python tools\tv.py "settings put global background_process_limit 3"

# Boost movie dialogue clarity and balance loud sound effects:
python tools\tv.py "settings put system sound_effect_night_mode 1 && settings put global drc_mode 1"
```

**That's it!** Grab your TV remote, press **Home**, and enjoy your brand new, lightning-fast TV!

---

## ❓ Frequently Asked Questions (FAQ)

#### **Is this safe? Does it void my warranty?**
* **100% Safe.** We **never uninstall** or delete system files, and we **never root** the TV. 
* Everything is disabled using Android's native `pm disable-user` command.
* To restore your TV back to original factory condition at any time, simply run:
  ```powershell
  powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost <your-tv-ip> examples\xiaomi-mitv-axso0\debloat.txt
  ```

#### **Will Miracast and Screen Mirroring still work?**
* **Yes!** Standard Miracast (Windows `Win + K`, Samsung Smart View, and Android casting) is preserved via `com.xiaomi.mitv.smartshare`.

#### **Why does hdmovie2 play better now?**
* AdGuard Private DNS blocks the aggressive popup servers and malicious redirect tabs automatically.

---

## 👤 Author
Customized and maintained by **[Lakhbeer Shah (Bunty Ji)](https://github.com/lakhbeershah)**.
Based on the open-source CleanYourTV framework. Released under the MIT License.
