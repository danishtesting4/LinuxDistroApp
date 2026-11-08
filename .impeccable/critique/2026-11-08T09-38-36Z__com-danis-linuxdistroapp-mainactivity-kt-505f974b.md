---
target: Andistro app UI (MainActivity.kt)
total_score: 32
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Users\\danis\\LinuxDistroApp\\app\\src\\main\\java\\com\\danis\\linuxdistroapp\\MainActivity.kt"
target_fingerprint: "sha256:7d93fd0f54c2f0fa98013aa381b4b88a4b18420ecab305fffc3f944803cacecd"
target_path: "C:\\Users\\danis\\LinuxDistroApp\\app\\src\\main\\java\\com\\danis\\linuxdistroapp\\MainActivity.kt"
timestamp: 2026-11-08T09-38-36Z
slug: com-danis-linuxdistroapp-mainactivity-kt-505f974b
---
Method: design review only (detector not applicable, native platform)

**Evidence**: 14 on-device captures (physical itel S666LN + emulator AVD matched at 720x1612 @320dpi, light & dark), pixel crops for defect verification, and full source review of `MainActivity.kt`, `themes.xml`, `DESIGN.md`, and the surface brief.

### Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Strong written status everywhere; loses location indicator on the Desktop screen (nav bar disappears) |
| 2 | Match System / Real World | 4 | Plain, honest copy throughout ("Nothing installed yet", "XFCE can currently be installed...") |
| 3 | User Control and Freedom | 3 | Back/Cancel/confirm exist everywhere; single-slot back stack; no user-initiated pause for an active install |
| 4 | Consistency and Standards | 3 | Desktop tab behaves like a pushed screen; brand-colored Install button deviates from DESIGN.md's evergreen primary |
| 5 | Error Prevention | 4 | Uninstall confirmation, battery education before install, Termux:X11 disabled with reason, service-side guards |
| 6 | Recognition Rather Than Recall | 4 | Every status visible as text; labeled nav; empty states name the next action |
| 7 | Flexibility and Efficiency | 3 | In-app VNC vs Termux:X11 choice, returning user reaches desktop in 1 tap; install path is linear |
| 8 | Aesthetic and Minimalist Design | 3 | "Open desktop" label wraps; triple-green label competition on cards; static green status bar in dark |
| 9 | Error Recovery | 3 | Paused -> Continue, Copy diagnostic log, confirm dialogs; not all runtime error strings verified live |
| 10 | Help and Documentation | 2 | Inline card descriptions only — no help path for app-level concepts (PRoot, VNC, sessions) |
| **Total** | | **32/40** | **Good (80%)** — solid foundation, craft-level fixes needed |

**Cognitive load**: low — 1 failed checklist item (current-location visibility lost on the Desktop screen). No decision point exceeds 4 visible options without grouping; the DE screen retains context ("Choose a desktop environment for Ubuntu") so no memory bridges.

### Design Specificity Verdict

**LLM assessment**: Authored. The evergreen/mint/coral system, white-silhouette logos on brand-color tiles, the branded hero band on detail screens, and the Andistro "A" mark in empty states could not be lifted onto an unrelated product unchanged. The structural skeleton (app bar -> card list -> detail) is conventional *by design* per the direction contract — specificity correctly comes from the status system and identity, not from exotic layout. The one place specificity backfires: the brand-colored Install button invents a one-off component rule that contradicts both DESIGN.md and WCAG.

**Deterministic scan**: not applicable (native platform).
**Visual overlays**: none (native platform; no web layer).

### Overall Impression

A genuinely careful app. The emotional arc works: an empty state that reassures instead of scolding -> a catalog that tells the truth about what's installable -> a detail screen that previews the exact desktop you'll get -> lifecycle states (paused, installed, uninstall-confirmed) that treat the user like an adult. The single biggest opportunity: help that keeps pace with the app's honesty — a beginner can read every word on screen and still not know what a "session" is.

### What's Working

1. **State-first status communication.** Every availability and runtime state pairs color with words — "Installable", "Catalog only", "Not installable in this build", "Paused", "Installed". DESIGN.md's hardest rule is honored on every card, both themes.
2. **Honest capacity messaging before commitment.** "XFCE is currently the supported desktop", Termux:X11 button disabled *with the reason shown*, battery-optimization education before the first install. This builds trust and prevents errors at the exact moments users would otherwise feel tricked.
3. **Lifecycle recovery craft.** Paused installs surface as "Setup paused -> Continue install", uninstall requires confirmation, and failures ship with "Copy diagnostic log". Rare care in a hobby app.

### Priority Issues

- **[P1] What**: The home card's "Open desktop" button label wraps to two lines, giving the button pair unequal heights (verified on-device, 720px).
  **Why it matters**: This is the returning user's primary action — the most-seen button in the app looks broken, and the taller outlined button invites mis-taps next to its shorter neighbor.
  **Fix**: Two `weight(1f)` buttons get ~134dp each; M3's default 24dp horizontal content padding leaves ~86dp for ~90dp of text. Set `contentPadding = PaddingValues(horizontal = 12.dp)` on both buttons (or give the label `maxLines = 1` with the weight rebalanced).
  **Suggested command**: `/impeccable polish`

- **[P1] What**: Brand-colored Install buttons fail text contrast — Ubuntu orange `#E95420` with white 14sp label ~ **3.7:1**, Arch blue `#1793D1` ~ **3.4:1** (AA requires 4.5:1). It also contradicts DESIGN.md's `button-primary: evergreen`.
  **Why it matters**: White-on-brand is the button a user must find and read to accomplish anything; failing contrast is a real accessibility defect, and a one-off component rule quietly erodes the design system.
  **Fix**: Use evergreen primary fill per DESIGN.md (the brand color already owns the hero band — it doesn't need the button too), or derive a darkened brand shade >=4.5:1 for the button surface only.
  **Suggested command**: `/impeccable colorize`

- **[P2] What**: `themes.xml` hardcodes `statusBarColor = #216B59` for both themes — in dark mode a bright evergreen strip sits directly above the near-black app bar (confirmed on-device).
  **Why it matters**: The one element you can't scroll away from disagrees with the theme below it; dark mode loses its "one coherent world" feel at the very top.
  **Fix**: Add `values-night` (dark background `#101713` + light icons) or move to edge-to-edge with a transparent status bar so Compose owns it.
  **Suggested command**: `/impeccable colorize`

- **[P2] What**: Desktop is a bottom-nav destination that *removes* the bottom nav and shows a floating back arrow instead — a pushed screen wearing a tab's clothes.
  **Why it matters**: Tap "Desktop", and your navigation disappears; you've lost your location and one-tap routes to Home/Distros. Tab and push semantics fight each other (same in both themes).
  **Fix**: Either keep the nav bar visible on the desktop viewer (content above it), or remove Desktop from the bar and reach sessions only from the home card's "Open desktop" — one model, not a hybrid.
  **Suggested command**: `/impeccable layout`

- **[P2] What**: No help path for beginners — the app's own concepts (desktop session, in-app VNC, Termux:X11, PRoot) appear only as one-line card descriptions; heuristic 10 scores 2/4.
  **Why it matters**: The stated audience is people learning Linux; the app explains distros beautifully but never explains itself at the moment of need.
  **Fix**: An info action in the app bar opening a glossary bottom sheet, plus one contextual line on the desktop viewer ("A session is a running desktop — start one from your installed environment").
  **Suggested command**: `/impeccable onboard`

### Persona Red Flags

**Jordan (First-Timer)**: The empty state is a win ("Nothing installed yet -> Browse distributions" is unambiguous). But on the detail screen he hits "Init System: systemd / Package Manager: apt / dpkg" with zero context and will hesitate — is this something he must choose? (It's reference data; it doesn't read like one.) No help icon anywhere to resolve the question. At the desktop screen, "No desktop session is available yet" introduces *session* as vocabulary without defining it.

**Casey (Distracted Mobile)**: Interrupted mid-install? Paused -> Continue handles it — excellent. Returning user reaches the desktop in one thumb-zone tap — excellent. What breaks: the wrapped "Open desktop" label crowds its hit target beside "Manage", and entering Desktop via the nav bar drops the nav bar, so returning from an interruption inside the viewer costs an extra back-arrow hunt.

**Sam (A11y)**: Brand Install buttons at 3.4–3.7:1 fail AA — the highest-priority defect on the screen that matters most. Wins: every status color carries written text (color-blind safe), nav items are labeled, distro icons carry `contentDescription = distro.name`, both themes keep status-bar icons legible on green.

### Minor Observations

- Cards show **three** competing greens: "Installable" (status), category label ("Beginner Friendly"), and section header. Status should own the green — demote category to `onSurfaceVariant`.
- Theme switching flashes a black frame (window background isn't night-themed) — visible as a full-black frame on recreation.
- Desktop viewer breaks the top-bar pattern (floating circular back, no title) while every other screen uses an app-bar-with-title.
- `previousScreen` is a single slot, not a stack — back from deeper combinations can surprise.
- The white-silhouette logo treatment on brand tiles is consistent and reads premium — keep it.

### Questions to Consider

- What if Desktop were a sessions dashboard — the app's centerpiece — instead of an empty screen that hides the navigation?
- If only XFCE is installable today, could the catalog teach that difference once, up top, instead of repeating "Catalog only" five times per screen?
- Does the battery-permission ask earn trust *before* the install starts, or would progress-first, permission-later convert better?
