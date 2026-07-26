# Xuan - Android App Manager

An elegant iOS-style Android application manager with Root support.

## Features

- **Freeze / Unfreeze** — Disable or enable apps via `pm disable/enable`
- **Force Stop** — Kill running apps via `am force-stop`
- **Clear Data** — Wipe app data via `pm clear`
- **Uninstall** — Remove user and system apps
- **Extract APK** — Save app APKs to Download/Xuan folder
- **Batch Operations** — Select multiple apps and act in bulk
- **Grant Root** — Auto-grant root permission to apps via Magisk

## Requirements

- **Root access** (Magisk / KernelSU / APatch) for most features
- Android 5.0 (API 21) and above

## Permissions

- `QUERY_ALL_PACKAGES` — List all installed apps
- `REQUEST_DELETE_PACKAGES` — Uninstall apps
- `KILL_BACKGROUND_PROCESSES` — Force stop non-root
- `MANAGE_EXTERNAL_STORAGE` — Save extracted APKs

## Build

```bash
bash build.sh
```

Output: `xuan.apk` in the project root and `/sdcard/Download/xuan.apk`

### Build Requirements
- Android SDK with `android.jar` (API 23+)
- `aapt`, `javac`, `dx`, `zipalign`, `apksigner`
- Java 8

## Architecture

```
xuan_app/
├── src/com/xuan/app/
│   ├── MainActivity.java    # Main UI, app list, actions
│   ├── RootHelper.java      # Root command execution
│   └── AppInfo.java         # App data model
├── res/
│   ├── layout/              # XML layouts (iOS Cupertino style)
│   ├── drawable/            # Custom backgrounds and ripples
│   ├── values/              # Colors, strings, styles
│   └── mipmap/              # App icon
├── AndroidManifest.xml
└── build.sh                 # Build script
```

## License

MIT License — free to use, modify, and distribute.

## Screenshot

![Xuan App](https://raw.githubusercontent.com/whxwhxwhx114514/Xuan/main/screenshot.png)
