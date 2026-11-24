# Mindful — Phase 1: Complete Repository Inventory

## Results

- Inspected branch: `main`
- Inspected commit: `cc73bca3049f27ab3bd2dd0885697ba45fad5da3`
- Commit date: `2026-11-24T16:44:05Z`
- Recursive Git tree: complete; GitHub reported `truncated=false`.
- Total tracked files: **172**
- Directories: **81**

### File counts by extension

| Type | Count |
|---|---:|
| Kotlin (`.kt`) | 103 |
| XML | 32 |
| PNG | 25 |
| Gradle (`.gradle`) | 3 |
| Markdown (`.md`) | 3 |
| Git ignore (`.gitignore`) | 2 |
| Properties | 2 |
| YAML (`.yml`) | 1 |
| No extension | 1 |
| **Total** | **172** |

### Top-level inventory

| Top-level location | Tracked files |
|---|---:|
| `android/` | 166 |
| `.github/` | 1 |
| Repository root files | 5 |
| **Total** | **172** |

The Android subtree contains the single Gradle project and its app module. The repository root contains `.gitignore`, `LICENSE`, and the three audit phase documents. There is no root README or separate docs directory in the current tree.

### Android module and source-set inventory

- Gradle project: `android/`
- Included module: `:app` only
- Production source set: `android/app/src/main/`
- Debug source set: `android/app/src/debug/`
- Unit-test source set: `android/app/src/test/`
- Kotlin files: **82 production** and **21 test**.
- Of the 82 production Kotlin files, **24** are under `com.mindful.android.nativeapp`; **58** are outside that package tree. The latter are not presumed obsolete: the manifest and native entry point still reference several shared service/helper classes, and every candidate needs reference tracing.
- No Java or Dart source files appear in the current recursive tree.

### Build configuration

- `android/settings.gradle` includes only `:app`.
- Android Gradle Plugin: `8.10.1`.
- Kotlin Android plugin: `1.8.22`.
- CI provisions Gradle `8.14.2`; wrapper properties also specify Gradle `8.14.2`.
- Compile SDK / target SDK: `35`; minimum SDK: `24`.
- JVM target: `17`.
- Compose and View Binding are both enabled.
- Room uses KAPT and configures `android/app/schemas` as its schema output location.
- Dependencies include AndroidX Core, AppCompat, Activity Compose, Lifecycle Runtime, WorkManager, Compose, Room, DataStore, JUnit, and Robolectric.
- The tree contains Gradle wrapper properties but no tracked `gradlew`, `gradlew.bat`, or wrapper JAR. CI does not use the wrapper; it installs Gradle directly.

### Manifest/runtime registrations

The main manifest declares the native Compose `MainActivity` as launcher and registers:
- Aggregated usage and screen usage app widgets;
- Device boot and midnight reset receivers;
- Focus quick-settings tile;
- Accessibility service;
- Foreground tracking service;
- Focus session foreground service;
- App shortcuts metadata and two deep-link intent filters.

The debug manifest adds INTERNET permission only to debug builds. These declarations make their referenced classes/resources high-priority items for Phase 2 tracing.

### CI workflow

`.github/workflows/build_apk.yml` runs on pushes to `main` and manual dispatch. It performs release lint, debug unit/migration tests, release APK assembly, and uploads the APK, generated Room schema (warning if absent), and lint reports.

### Resource inventory

The app contains 32 XML files across manifests, drawable/layout/mipmap/value/XML resource directories, and 25 PNG files across notification and launcher density directories. The manifest references launcher icons, shortcuts, widgets, accessibility configuration, quick tile resources, and app themes. Exact resource-to-code/XML usage must be traced in Phase 2.

### Structural observations requiring Phase 2

1. There are 58 production Kotlin files outside the `nativeapp` package tree. Some are explicitly registered by the manifest or imported by `MainActivity`; none can be removed based on package location alone.
2. Compose and View Binding are both enabled. Determine whether View Binding is still used before considering its build feature or related layouts.
3. The root and Android-level `.gitignore` files overlap. Compare their patterns and scope before classifying either.
4. The project configures Room schema output, while no schema JSON file is present in the tracked tree. Determine whether this is intentional, generated only in CI, or a migration/versioning gap.
5. There is no tracked Gradle wrapper script/JAR; CI installs Gradle 8.14.2 directly. Determine whether local build support is intentionally external.
6. The native entry point and Compose home screen coexist with service/helper packages outside `nativeapp`. Trace each dependency, manifest registration, dynamic invocation, and test use.
7. The root contains no README/docs directory, so there is no repository documentation entry point to validate against implementation.

## Safety statement

Phase 1 was an inventory only. No application source, UI, feature, build configuration, resource, workflow, or other project file was changed or deleted. Phase 2 must trace references and runtime/build behavior before any candidate can be classified as potentially removable.
