# 📺 Clean Acer TV — Ultra-Debloated, Ad-Free & Free Movie Setup

> **Transform your Acer H Pro Google TV into a blazing-fast, Apple TV-style home screen with zero ads, 1-click movie streaming, and zero black-screen risk.**

Customized for **Acer H Pro Google TV** (Indkal Technologies / MediaTek SoC), running Google TV (Android 11 / 14).

---

## 🌟 What This Project Does For Your Acer TV

| Before (Stock Acer Google TV) | After (This Setup) |
|---|---|
| ❌ Laggy Google TV launcher with sponsored video trailers | ✅ **Apple TV-style minimalist frosted glass home screen** |
| ❌ Popups and malicious redirect tabs on movie sites | ✅ **AdGuard DNS + TV Bro ad-filtering** |
| ❌ Video ads on YouTube | ✅ **100% Ad-Free YouTube (SmartTube) mapped to YouTube button** |
| ❌ Heavy Google recommendation engines eating RAM | ✅ **Over 400 MB free RAM (smooth 60fps animations)** |
| ❌ Random generic greetings | ✅ **Personalized "RADHE RADHE, BUNTY JI" + Automatic Diwali & Holi festive themes** |
| ❌ Unused Netflix button | ✅ **Netflix button: 1-click opens TV Bro searching hdmovie2 for free movies** |
| ❌ Risk of breaking casting / cleaner | ✅ **FastCast, Acer TV Manager, System Upgrade & Google Mic 100% preserved** |

---

## 📦 Essential Apps Included in This Repo (`apps/`)

1. **SmartTube (`SmartTube.apk`):**
   * Ad-free YouTube for TV with SponsorBlock.
   * Auto-mapped to the physical **YouTube** button on your remote.
2. **TV Bro (`TVBro.apk`):**
   * TV browser with built-in popup blocker.
   * Auto-mapped to the physical **Netflix** button on your remote for instant **`hdmovie2`** streaming.
3. **Send Files to TV (`SendFilesToTV.apk`):**
   * Send APKs, videos, and files directly from your phone to your TV.
4. **TVFileBox (`TVFileBox.apk`):**
   * Built for TV remotes with photo view pane, D-pad navigation, 90-degree photo rotation, and auto-save.
5. **Audio Setup (`AudioSetup.apk`):**
   * Built specifically for TV speakers with clean D-Pad remote navigation, 6 Sound Profiles, and Custom 5-Band Equalizer.

---

## 🎮 Remote Control Shortcuts (Acer Remote)

* **YouTube Button:** Instantly launches **SmartTube** (YouTube without ads).
* **Netflix Button:** Instantly opens **TV Bro** searching for **`hdmovie2`** for free movies.
* **Mic Button:** Voice search with **Google Assistant / Gemini** (routes directly into SmartTube).
* **Home Button:** Snaps directly to the Apple TV launcher with **0.0-second delay**.
* **Settings & Inputs Buttons:** Native Acer hardware menus stay 100% functional.

---

## 🚀 Easy 2-Phase Safe Setup Guide

### **Step 1: Enable Network Debugging on your Acer TV**
1. On your Acer TV, go to **Settings** $\rightarrow$ **Device Preferences / System** $\rightarrow$ **About**.
2. Scroll to **Android TV OS Build** and press the remote **OK** button **7 times** until it says *"You are now a developer!"*.
3. Go back to **Developer Options**:
   * Turn **ON** **USB Debugging**.
   * Turn **ON** **Wireless / Network Debugging**.
4. Check your TV's IP address under **Settings** $\rightarrow$ **Network & Internet** (e.g., `192.168.1.55`).

---

### **Step 2: Connect and Install (Run from PC)**

```powershell
# 1. Set your TV IP address
$env:TV_HOST = "<your-tv-ip>"

# 2. Install the Apple TV Launcher (with RADHE RADHE, BUNTY JI & System Stats)
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost <your-tv-ip> -StockLauncher com.google.android.apps.tv.launcherx -InputKeyService -HomeKeyService

# 3. Install essential apps (SmartTube, TV Bro, Send Files to TV)
powershell -ExecutionPolicy Bypass -File tools\install-apps.ps1 -TvHost <your-tv-ip>

# 4. Debloat Google TV ads (Keeps FastCast, TV Manager, OTA & Voice Assistant intact)
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost <your-tv-ip> examples\acer-hpro\debloat.txt

# 5. Turn on AdGuard Ad-Blocking & RAM Optimization
powershell -ExecutionPolicy Bypass -File tools\toggle-adguard.ps1 -TvHost <your-tv-ip> -Mode on
python tools\tv.py "settings put global background_process_limit 3"
```

---

## 🛡️ 1-Click Restore to Today's State
To instantly revert your Acer TV back to its exact state today without losing any apps or data:
```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost <your-tv-ip> examples\acer-hpro\debloat.txt
```
