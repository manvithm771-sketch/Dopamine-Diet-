# Dopamine Diet

Dopamine Diet is one unified native Android application combining the existing Dopamine Diet and Mindful capabilities.

## Project structure

- `:app` — the single Android application, launcher activity and shared navigation.
- `:feature-scroll` — Dopamine Diet scroll tracking, brain status and related UI.
- `:feature-mindful` — Mindful screen-time, focus, restrictions, accessibility and persistence.
- `:service`, `:core-data`, `:core-detect`, `:core-model` — Dopamine Diet's supporting services and data/detection layers.

The app has one launcher and four destinations: Habits, Scroll, Screen time and Settings.

## Build

Use Java 17 and run:

```bash
./gradlew test
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The release APK is generated at `app/build/outputs/apk/release/app-release.apk`.
