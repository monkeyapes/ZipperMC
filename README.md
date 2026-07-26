<p align="center">
  <strong>🌐 <a href="#english">English</a> · <a href="README.tr.md">Türkçe</a></strong>
</p>

---

<a id="english"></a>

<p align="center">
  <img src="https://img.shields.io/badge/ZipperMC-1.4.0-4CAF50?style=for-the-badge" alt="ZipperMC">
  <img src="https://img.shields.io/github/v/release/monkeyapes/ZipperMC?style=for-the-badge&label=release" alt="Release">
  <img src="https://img.shields.io/badge/license-MIT-4CAF50?style=for-the-badge" alt="License">
</p>

**ZipperMC** is an Android app that installs Minecraft addons (`.mcaddon`, `.mcpack`, `.zip`, `.mcworld`, etc.) into Minecraft PE (Bedrock Edition) **with zero user setup**. No permissions, no folder picking, no settings toggles. Just open the app and it works.

---

## Setup

**There is no setup.** That's the whole point.

1. [Download the latest APK](https://github.com/monkeyapes/ZipperMC/releases/latest)
2. Install it (enable *Install from unknown apps* if your device asks)
3. Open the app

**No permissions to grant. No folders to pick. No settings to change.**

---

## How to use

### Automatic mode (recommended)

1. Open ZipperMC
2. The app automatically scans your device's **Downloads**, **APKs**, and **Minecraft folders** for addon files
3. Found files are listed on the home screen
4. The first found file is **automatically analyzed**
5. ZipperMC detects your installed Minecraft version and **auto-adjusts** the addon's compatibility
6. Tap **Install to Minecraft** — it tries direct install first. If that fails, use **Send to Minecraft**

### From your file manager

1. Download any `.mcaddon`, `.mcpack`, or `.zip` file
2. Tap it in your file manager
3. Choose **ZipperMC** from the app picker
4. The file opens in ZipperMC, ready to install

### Version Editor

If an addon was made for a different Minecraft version:

1. After analysis, tap **Version** on any detected pack
2. Change **Min Engine Version** and/or **Pack Version**
3. Tap **Save & Continue**
4. ZipperMC automatically patches the addon before installing

> **Tip:** On Android 11+, direct file access is restricted. ZipperMC handles this automatically — use **Send to Minecraft** to let Minecraft import the file itself.

---

## What happens step by step

```
App opens → scans Downloads, APKs, Minecraft folders
     ↓ finds .mcaddon/.mcpack/.zip
     ↓ analyzes content (manifest.json detection)
     ↓ detects your Minecraft version (1.21.x etc.)
     ↓ adjusts addon version to match your Minecraft
     ↓ tries direct install to games/com.mojang/
     ↓ if blocked → tap "Send to Minecraft"
     ↓ Minecraft opens and imports the addon
```

---

## Supported file types

| Format | What it is |
|--------|-----------|
| `.mcaddon` | Addon with resource + behavior packs |
| `.mcpack` | Single resource or behavior pack |
| `.mcworld` | Minecraft world |
| `.mctemplate` | World template |
| `.mcskin` | Skin pack |
| `.zip` | Any of the above (or detection by content) |

---

## FAQ

**Q: Do I need to grant storage permission?**  
A: **No.** ZipperMC never asks for storage permissions. It writes to app cache or sends the file to Minecraft directly.

**Q: Why does "Send to Minecraft" open Minecraft?**  
A: That's how Android works — one app sends a file to another app. Minecraft imports it and you're done.

**Q: Does it work on Android 14/15?**  
A: Yes. ZipperMC works on Android 8.0 (API 26) and above, including Android 14 and 15.

**Q: Minecraft isn't detecting my addon after Install?**  
A: Try using **Send to Minecraft** instead. This sends the file directly to Minecraft which handles the import.

**Q: Can I edit the addon version?**  
A: Yes. Tap **Version** on any detected pack to change min_engine_version and pack_version.

---

## Building from source

```bash
git clone https://github.com/monkeyapes/ZipperMC.git
cd ZipperMC
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

**Requirements:** Android SDK 35, JDK 17

## License

MIT
