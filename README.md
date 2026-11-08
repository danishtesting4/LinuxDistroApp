# Andistro

Android app for installing and running real Linux distributions on-device — terminal and GUI desktop — with a Material 3 (Material You) interface.

> Status: **alpha**. Debian + XFCE is the only installable target in this build; other distributions and desktop environments are catalog entries. A terminal-only (no desktop environment) option is not yet implemented. VNC desktop sessions have not been verified on-device.

## Features
- Browse a catalog of Linux distributions and desktop environments
- Install Debian + XFCE on-device via PRoot (no root required)
- In-app desktop viewer (VNC) with on-screen keyboard, plus Termux:X11 handoff
- Uninstall and resume interrupted installs
- Material 3 UI with light/dark themes

## Build
- Android Studio, Gradle 9.0.0, AGP 8.8.2, Kotlin 2.1.0, Java 17
- compileSdk 35, minSdk 24, package `com.danis.linuxdistroapp`

```
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## License
TBD
