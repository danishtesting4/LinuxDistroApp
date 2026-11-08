---
name: Andistro
description: Native Android manager for Linux environments
colors:
  evergreen-primary: "#216B59"
  mint-container: "#C8EFDF"
  coral-tertiary: "#9C4F3A"
  coral-container: "#FFDAD0"
  evergreen-mark: "#103D34"
  light-background: "#F7F9F5"
  light-surface-variant: "#E1E9E3"
  light-text: "#171D19"
  light-secondary-text: "#414B45"
  light-outline: "#717B74"
  dark-background: "#101713"
  dark-surface-variant: "#404B44"
  dark-selected-container: "#15513F"
  dark-text: "#E0E9E2"
  dark-secondary-text: "#C0CCC4"
  dark-outline: "#8A978E"
typography:
  headline:
    fontFamily: "Roboto"
    fontSize: "28sp"
    fontWeight: 400
  title:
    fontFamily: "Roboto"
    fontSize: "22sp"
    fontWeight: 400
  body:
    fontFamily: "Roboto"
    fontSize: "16sp"
    fontWeight: 400
  label:
    fontFamily: "Roboto"
    fontSize: "14sp"
    fontWeight: 500
rounded:
  card: "16dp"
  icon-tile: "14dp"
  button: "20dp"
spacing:
  list-gap: "8dp"
  control-gap: "12dp"
  card-inset: "20dp"
  screen-inset: "24dp"
  section-gap: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.evergreen-primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.button}"
    height: "40dp"
  button-tonal:
    backgroundColor: "{colors.mint-container}"
    textColor: "{colors.light-text}"
    rounded: "{rounded.button}"
    height: "40dp"
  environment-card:
    backgroundColor: "{colors.light-surface-variant}"
    rounded: "{rounded.card}"
    padding: "20dp"
  input-outlined:
    backgroundColor: "{colors.light-background}"
    textColor: "{colors.light-text}"
    rounded: "4dp"
    height: "48dp"
  navigation:
    backgroundColor: "{colors.light-background}"
    textColor: "{colors.light-text}"
    height: "80dp"
---

# Design System: Andistro

## Overview

**Creative North Star: "Clear Paths, Clear Status"**

Andistro is a native Android manager for browsing and operating Linux environments. Its identity is an evergreen field with a mint route-shaped A and a small coral node. The interface stays recognizably Material 3; the brand comes from its own name, mark, and restrained color roles rather than borrowed artwork.

The structure follows familiar Android manager conventions: an app bar, scannable catalog rows, a focused primary action, and navigation that adapts to width. ReVanced Manager, Andronix, and Vendetta Launcher are high-level references only; Andistro does not copy their exact screens or assets.

**Key Characteristics:**
- Original evergreen, mint, and coral identity
- State-first environment management
- Native Material 3 controls and responsive navigation

## Colors

Evergreen marks the primary path, mint carries selected and primary-container states, coral is reserved for secondary emphasis, and neutral greens support readable surfaces in both themes.

### Primary
- **Deep Evergreen**: The main action and app identity.
- **Soft Mint**: Primary containers and selected navigation.

### Secondary
- **Warm Coral**: Secondary emphasis and the node in the Andistro mark.

### Neutral
- **Leaf-Tinted Surfaces**: Backgrounds, cards, and dividers remain green-neutral rather than purple-gray.
- **Ink and Mist**: Primary and secondary text retain clear contrast in light and dark schemes.

**The State-First Color Rule.** Color reinforces a written availability or runtime label; it never carries status alone.

## Typography

**Display Font:** Roboto (Android system sans-serif)  
**Body Font:** Roboto (Android system sans-serif)  
**Label/Mono Font:** Roboto (Android system sans-serif)

**Character:** Familiar Android typography keeps names, installation state, and actions easy to scan. The default Material 3 type scale is used rather than a decorative display face.

### Hierarchy
- **Headline** (regular, 28sp): Main screen titles such as “Your environments.”
- **Title** (regular, 22sp): Environment names and state panels.
- **Body** (regular, 16sp): Explanations and catalog descriptions.
- **Label** (medium, 14sp): Navigation and compact availability details.

## Layout

Screens use 24dp horizontal insets, 20dp content insets for grouped environment rows, 8dp list gaps, and 24dp between major sections. Content is capped at 1000dp. Compact windows use a bottom navigation bar; windows at 600dp and wider use a navigation rail. Setup and desktop screens use the full content area with back navigation.

## Elevation & Depth

The system is flat at rest. Tonal surface changes and dividers separate grouped content; cards use zero elevation and controls retain Material 3 state feedback. No decorative shadows are used.

**The Tonal-Only Depth Rule.** Do not add a shadow to a resting list row or environment container.

## Shapes

Environment and catalog rows use 16dp corners; icon tiles use 14dp corners. Buttons retain the rounded Material 3 form. The Andistro mark is a crisp vector, not a shaded illustration.

## Components

### Buttons
- **Shape:** Rounded Material 3 button (20dp visual radius).
- **Primary:** Evergreen fill with a high-contrast label; use for the next supported action.
- **Secondary:** Tonal or outlined Material 3 button for management and navigation actions.
- **Focus / Disabled:** Keep Material 3 interaction states and text labels; never rely on color alone.

### Cards / Containers
- **Corner Style:** Gently rounded (16dp).
- **Background:** Leaf-tinted surface variant against the page surface.
- **Shadow Strategy:** Flat; separation comes from tonal contrast.
- **Internal Padding:** 20dp for an environment row or recovery state.

### Inputs / Fields
- **Style:** Material 3 outlined text field for desktop keyboard entry.
- **Focus:** Use the primary theme role and native focus state.
- **Error / Disabled:** Preserve Material 3 error and disabled treatments.

### Navigation
- **Style:** Material 3 bottom bar on compact screens and navigation rail on expanded screens.
- **State:** Selected destination uses a tonal green container and remains paired with a label.

### Availability Row
- Show the distribution or environment name, a written status such as “Installable” or “Catalog only,” and the action or detail route. Keep status text visible without relying on the icon or color.

## Do's and Don'ts

### Do:
- **Do** use Material 3 navigation and controls appropriate to the available width.
- **Do** pair every availability color with a written status.
- **Do** keep Andistro's name, vector mark, and color identity original.

### Don't:
- **Don't** copy another launcher's exact layout, logo, or artwork.
- **Don't** indicate installation support only through color.
- **Don't** add decorative shadows to flat environment rows.
