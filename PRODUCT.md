# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Android users who want to browse, install, and open Linux environments on their device without root access.

## Product Purpose

Andistro is a Linux environment manager with an in-app desktop viewer. Debian with minimal XFCE is the current supported installation; other distributions and desktop environments are catalog entries.

## Positioning

A native Android manager for Linux environments, with support status and runtime state kept explicit.

## Operating Context

Users browse on an Android phone, install with network access, and connect to a Linux desktop through the in-app VNC viewer. Installation runs as a foreground service and requires Android battery optimization to be disabled for the app.

## Capabilities and Constraints

- The app uses Kotlin, Jetpack Compose, and Material 3.
- Debian + XFCE is the only supported installation target; no distribution is preselected.
- The runtime uses PRoot without root access and supports 64-bit ARM and x86-64.
- Other distributions and desktop environments are informational catalog entries, not installable options.
- A terminal screen is not implemented.
- Successful Linux installation and XFCE operation have not yet been verified on-device.

## Brand Commitments

- The user selected **Andistro** as the app name and approved an original Andistro mark and visual identity.
- Use familiar Android manager conventions informed by ReVanced Manager, Andronix, and Vendetta Launcher; do not reproduce their exact screens, names, logos, or artwork.
- Keep Debian + XFCE available as an optional install target, not as the default home-screen content.

## Evidence on Hand

- The debug app has been installed and launched on an itel Android phone.
- No on-device Debian installation or XFCE session has been confirmed.

## Product Principles

- Start with actual installed environments; do not imply a default distro.
- Mark installability before a user reaches an install action.
- Show installation progress, errors, and recovery actions honestly.
- Keep desktop access within the Android app.
