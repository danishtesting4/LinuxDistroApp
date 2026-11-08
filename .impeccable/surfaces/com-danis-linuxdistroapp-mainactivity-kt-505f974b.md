---
version: 1
slug: "com-danis-linuxdistroapp-mainactivity-kt-505f974b"
primary_target: "app/src/main/java/com/danis/linuxdistroapp/MainActivity.kt"
related_targets: []
---

Scope: Andistro home and distribution launcher. Mode: Operate.
Audience: Android users browsing Linux environments and managing installed sessions.
Job: Show installed environments and make the next supported action obvious without preselecting a distribution.
Constraints: Debian with XFCE remains the only installable target in this build; other catalog entries must not imply install support. Use native Material 3 controls and adapt navigation to available width.

## Direction contract

THESIS: Andistro is a neutral launcher for Linux environments, not a Debian setup wizard. The home screen starts with the user's real installed state and refuses to feature Debian before the user chooses it.

OWN-WORLD: An original Andistro identity uses a dark evergreen mark, mint route nodes, and a restrained coral secondary accent. Material 3 owns navigation and controls; status labels stay legible in both light and dark schemes.

STORY: A new user sees that nothing is installed and can browse distributions. A returning user sees the installed environment and can manage it or open its desktop. Availability is explicit before any install action.

FIRST VIEWPORT: An Andistro app bar leads into “Your environments.” The empty state reads “Nothing installed yet” with a single “Browse distributions” action. After setup, that same area becomes a compact Debian/XFCE environment row with Manage and Open desktop actions. Compact screens use a bottom navigation bar; expanded screens use a navigation rail.

FORM: User-pinned, familiar Android manager conventions, informed by ReVanced Manager, Andronix, and Vendetta Launcher without reproducing their exact screens or assets. Canon/category-standard direction; seed: 7704ac81b9ab2c41.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
