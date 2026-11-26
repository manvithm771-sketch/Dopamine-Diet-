# Dopamine Diet

One native Android application combining Reels/short-form tracking, digital-wellbeing tools, focus sessions, app restrictions, screen-time reporting, and the unified four-destination interface.

## Structure

- `app/` — the only Android application, launcher activity, and unified navigation.
- `reels/` — Reels/short-form tracking UI and feature integration.
- `wellbeing/` — focus sessions, usage statistics, restrictions, and wellbeing services.
- `service/` — accessibility and overlay services for Reels tracking.
- `core-model/`, `core-detect/`, `core-data/` — shared tracking model, detection, and persistence.
- `docs/` — project documentation.

The repository has one Gradle root project named **Dopamine Diet** and one installable application ID: `com.dopaminediet`.

## Build

Requires JDK 17 and Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## APK delivery

APK builds are published only as GitHub Actions workflow artifacts. Open the latest successful **Build Dopamine Diet APK** workflow run and download the **Dopamine-Diet-debug-apk** artifact, then extract `app-debug.apk`. The build workflow does not publish APKs as GitHub release assets.
