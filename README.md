<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="200" viewBox="0 0 800 200">
  <rect width="800" height="200" fill="#1B5E20"/>
  <rect x="0" y="180" width="800" height="20" fill="#2E7D32" opacity="0.5"/>
  <rect x="0" y="160" width="800" height="4" fill="#388E3C" opacity="0.3"/>
  <text x="400" y="90" font-family="Arial, sans-serif" font-size="64" font-weight="bold" fill="#FFFFFF" text-anchor="middle" letter-spacing="4">ZIPPERMC</text>
  <text x="400" y="130" font-family="Arial, sans-serif" font-size="20" fill="#81C784" text-anchor="middle" letter-spacing="8">INSTALL MINECRAFT CONTENT</text>
  <rect x="340" y="145" width="120" height="2" fill="#4CAF50"/>
</svg>

<p align="center">
  <strong>Android app to install .mcaddon, .mcpack, and ZIP content directly into Minecraft PE (Bedrock Edition).</strong><br>
  Auto-detects resource packs, behavior packs, worlds, and skin packs — extracts each to the right folder.
</p>

<p align="center">
  <a href="https://github.com/monkeyapes/ZipperMC/releases/latest">
    <img src="https://img.shields.io/badge/download-v1.1.0-4CAF50?style=for-the-badge&logo=android" alt="Download">
  </a>
  <a href="https://github.com/monkeyapes/ZipperMC/releases">
    <img src="https://img.shields.io/github/v/release/monkeyapes/ZipperMC?style=for-the-badge&label=release" alt="Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-MIT-4CAF50?style=for-the-badge" alt="License">
  </a>
</p>

---

## ✦ Features

- **Auto-detection** — Scans every ZIP/mcaddon/mcpack for `manifest.json` and identifies resource packs, behavior packs, worlds, and skin packs
- **Multi-pack support** — `.mcaddon` files with multiple packs inside are separated and installed to the correct directories automatically
- **File manager integration** — Open `.mcaddon`, `.mcpack`, or `.zip` files directly from any file manager
- **Material 3 UI** — Clean, modern interface with dark mode and dynamic colors (Android 12+)
- **Zero config** — Just pick a file, ZipperMC handles the rest
- **Lightweight** — ~1 MB APK, no ads, no permissions beyond storage access

## ✦ Quick start

| Step | Action |
|------|--------|
| **1** | [Download the latest APK](https://github.com/monkeyapes/ZipperMC/releases/latest) |
| **2** | Install on your Android device (enable *Install from unknown apps* if prompted) |
| **3** | Open ZipperMC and tap **Pick File** |
| **4** | Select any `.mcaddon`, `.mcpack`, or `.zip` file |

That's it. ZipperMC will analyze the file, detect the content type(s), and extract everything to the correct Minecraft folders under `games/com.mojang/`.

## ✦ Supported formats

| Format | Description |
|--------|-------------|
| `.mcaddon` | Addon containing both resource and behavior packs — auto-separated |
| `.mcpack` | Single resource pack or behavior pack |
| `.mcworld` | Minecraft world (ZIP with `level.dat`) |
| `.mctemplate` | World template |
| `.mcskin` | Skin pack |
| `.zip` | Any of the above bundled as a ZIP |

## ✦ How it works

```
You pick a file → ZipperMC scans for manifest.json in every subdirectory
                  ↓
          Detects: Resource Pack, Behavior Pack, World, or Skin Pack
                  ↓
          Extracts each pack to the correct folder:
            • games/com.mojang/resource_packs/
            • games/com.mojang/behavior_packs/
            • games/com.mojang/minecraftWorlds/
            • games/com.mojang/skin_packs/
                  ↓
          Done — tap "Open Minecraft" to see your content
```

## ✦ Building from source

```bash
# Clone the repo
git clone https://github.com/monkeyapes/ZipperMC.git
cd ZipperMC

# Generate the Gradle wrapper (requires Gradle 8.5+ installed)
gradle wrapper --gradle-version 8.5

# Build the APK
./gradlew assembleRelease

# The signed APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

**Prerequisites:**
- Android SDK (API 35)
- JDK 17
- Gradle 8.5 (or use the wrapper after generating it)

## ✦ Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + StateFlow |
| Build | Gradle + Android Gradle Plugin 8.2.2 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

## ✦ License

MIT — do whatever you want.
