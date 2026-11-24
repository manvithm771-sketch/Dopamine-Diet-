# Mindful — Phase 2: Deep Dependency and Reference Investigation

## Execution instruction
When this phase file is presented or referenced as the active task, first read the completed Phase 1 inventory and inspect the latest `main` branch. Execute the dependency investigation; do not merely restate the plan. Update this file's **Results** section with evidence.

## Objective
Determine how every suspicious or potentially unused file is connected to Android runtime, Gradle, tests, CI, resources, and documentation.

## Required work
1. Trace Kotlin/Java imports and references, resource IDs, XML references, manifest declarations, services, receivers, providers, widgets, deep links, shortcuts, reflection, and runtime registration.
2. Trace Gradle module inclusion, project dependencies, plugin usage, source sets, build variants, generated code, Room schemas/migrations, and resource merging.
3. Trace CI workflow references, scripts, signing/build configuration, APK artifact paths, and automation dependencies.
4. Trace all images, icons, layouts, themes, XML resources, assets, and dynamically referenced resources.
5. Trace tests, fixtures, database schemas, migration safeguards, and developer tooling.
6. For each suspicious file, record exact path, all discovered references, search method, and whether its use is production, build-only, debug/profile-only, test-only, or unresolved.
7. Explicitly investigate indirect Android/Gradle behavior before treating a file as unused. Record uncertainty rather than guessing.

## Deliverable
Update the Results section below with a per-file dependency table containing:
- exact path;
- file role;
- direct and indirect references;
- evidence/source locations;
- impact if removed;
- confidence and unresolved questions.

## Safety constraints
- Investigation only. Do not edit source, Gradle files, manifests, workflows, resources, or app behavior.
- Do not delete, move, rename, or modify any files.
- A lack of search results is not sufficient proof that a file is unused.
- Preserve manifests, resources, schemas, migrations, tests, and build support files unless evidence and the owner explicitly authorize a later change.

## Results
_Not yet executed. Replace this line with the verified Phase 2 dependency map when this phase is run._
