# Dopamine Diet — Unified UI Design Specification

Status: Design foundation, approved direction from product decisions  
Product: Dopamine Diet  
Platform: Android (Jetpack Compose)  
Visual direction: Dark-first, iOS-standard interaction quality, inspired by Opal

## 1. Product structure

Dopamine Diet is one Android application assembled from the existing Mindful and Dopamine Diet projects. It is not a launcher for two separate app experiences.

The unified app has exactly four primary destinations in a persistent bottom navigation bar:

1. Habits
2. Scroll
3. Screen time
4. Settings

Habits is the initial destination after launch.

The third project is not part of this design implementation until its processing is complete and it is explicitly added to the product.

## 2. Design principles

- Dark-first: use a deep charcoal/near-black canvas, not a flat grey screen.
- iOS-standard quality: strong typographic hierarchy, consistent spacing, clear touch targets, restrained separators, predictable navigation, polished sheets/dialogs, and deliberate motion.
- Opal-inspired visual language: calm dark surfaces, luminous violet accents, clear focus-oriented information hierarchy, purposeful progress visuals, refined cards, and low-noise layouts.
- Original Dopamine Diet identity: do not reuse Opal logos, proprietary illustrations, exact screen compositions, copy, or branded assets.
- Functionality before decoration: preserve existing Mindful and Dopamine Diet capabilities while redesigning their presentation.
- Truthful data: never display invented usage numbers, streaks, progress, or permission states in production UI.
- Accessibility: maintain readable contrast, scalable text, meaningful content descriptions, and touch targets suitable for Android.

## 3. Initial design tokens

These are starting tokens. They should be centralized in the Compose theme and refined during implementation.

| Token | Initial value | Purpose |
|---|---|---|
| App background | #0B0B10 | Main screen canvas |
| Surface | #15151D | Primary cards and navigation |
| Elevated surface | #20202A | Nested cards and selected controls |
| Primary text | #F5F5FA | Main labels and values |
| Secondary text | #A7A7B5 | Supporting information |
| Muted text | #777785 | Low-priority metadata |
| Accent violet | #A78BFA | Primary actions and active states |
| Accent violet bright | #C4B5FD | Highlights and progress |
| Positive | #62D6A3 | Successful progress/status |
| Warning | #F2C078 | Attention and permission states |
| Divider | #30303A | Subtle separation |

Use rounded shapes consistently, but avoid making every surface a separate card. Reserve gradients and glow for a small number of meaningful focus/progress moments.

## 4. Shared application shell

- One launcher activity and one application navigation state.
- Persistent bottom navigation: Habits, Scroll, Screen time, Settings.
- Preserve each destination's scroll position and relevant local state when switching tabs.
- Use a consistent top-level title/header pattern, safe-area handling, and system-bar treatment.
- Use native Android back behaviour for nested detail screens and sheets.
- Avoid nested bottom navigation bars. Existing project screens must be refactored into content destinations before being hosted by the unified shell.

## 5. Destination design briefs

### Habits — new design

Create this experience from scratch. Do not reuse Mindful's existing Habits UI or make a hybrid of the old screen.

The new page should support the habit capabilities actually present or explicitly approved for the product. The initial visual hierarchy should be:

- A concise greeting and date.
- A compact week/day selector where useful.
- A clear daily progress summary.
- A readable list of today's habits with completion state.
- A deliberate add/manage habit action.
- Habit detail, scheduling, reminder, and history states only where backed by implemented functionality.

Do not invent gamification, streak rules, XP, rewards, or habit types before their behaviour is defined.

### Scroll — existing Dopamine Diet functionality

Redesign its presentation in the shared visual system while preserving the existing scrolling-tracking functionality and data semantics.

Keep the existing feature behaviour, tracking status, accessibility-permission path, Reels/Shorts counts, period summaries, brain-status feature, and other verified capabilities. Do not remove or silently change working behaviour as part of the visual redesign.

### Screen time — existing Mindful functionality

Redesign its presentation in the shared visual system while preserving Mindful's existing usage data and analytics behaviour.

Retain the verified daily/weekly usage views, app-level usage details, metric selection, date navigation, and usage-access/empty/error states. Keep data sourced from the existing Mindful repositories and database.

### Settings — unified

Create one Settings destination using only relevant existing settings from both projects. Audit and group settings before implementation so duplicates do not appear.

Use clear groups such as app preferences, notifications/reminders, permissions, privacy/data, and about only when those options are supported by the existing product. Do not invent controls that have no backing behaviour.

## 6. Shared components

Build reusable Compose components for:
- Screen headers and section headings
- Primary and secondary buttons
- Quiet icon buttons
- Metric/progress cards
- Habit rows and status indicators
- Segmented selectors
- Charts and chart legends
- Settings rows and grouped settings panels
- Empty, loading, permission-required, and error states
- Confirmation dialogs and bottom sheets

Components must expose state and callbacks; they must not contain fake static production data.

## 7. Motion and interaction

- Use short, subtle transitions for tab changes and state updates.
- Avoid continuous decorative animation that harms battery, accessibility, or perceived performance.
- Use haptics only for meaningful completion or confirmation actions.
- Ensure scroll remains stable; do not introduce overlays or animated layout shifts that cause flicker.

## 8. Implementation sequence

1. Preserve the current project behaviour and map the actual screens, data owners, permission flows, and navigation.
2. Build the shared dark theme, typography, spacing, shapes, icons, and reusable components.
3. Replace the current two-choice launcher with one four-destination application shell.
4. Build the new Habits destination.
5. Refactor Dopamine Diet Scroll content into a destination without changing its tracking logic.
6. Refactor Mindful Screen Time content into a destination without changing its usage logic.
7. Consolidate Settings and remove duplicate navigation/settings only after auditing their behaviour.
8. Validate compilation, onboarding, permissions, database migration, all four destinations, back navigation, and APK installation.

## 9. Current architecture finding

At the time this specification was created, `UnifiedApp/app` still contains a `LauncherActivity` that presents separate buttons for Dopamine Diet and Mindful and starts their separate activities. The Mindful home screen also has its own internal navigation. This is not yet the requested single four-destination application.

The current module layout already includes `:dopamine` and `:mindful` as libraries, so the redesign should reuse their underlying functionality rather than launch them as separate app experiences. The current entry point and screen composition need a deliberate integration refactor.

## 10. Reference material

Use these sources to study product concepts and visual language, not to reproduce proprietary assets or exact layouts:

- Opal official product site: https://opalapp.com/
- Opal official brand kit and app screenshots: https://brandkit.opal.so/
- Opal product/help documentation: https://help.opalapp.com/

This specification is a design foundation, not a claim that the unified UI has already been implemented or tested.
