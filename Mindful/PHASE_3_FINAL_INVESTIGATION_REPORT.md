# Mindful — Phase 3: Verification and Final Investigation Report

## Execution instruction
When this phase file is presented or referenced as the active task, read the completed Phase 1 and Phase 2 results, refresh the relevant repository state, and finish the verification. Do not perform deletions. Update this file's **Final report** section with the evidence-backed result.

## Objective
Convert the inventory and dependency investigation into a safe, reviewable classification of repository files.

## Required work
1. Re-check every candidate against the latest `main` branch and confirm the exact current path.
2. Verify references across Kotlin source, XML/resources, manifests, Gradle modules and source sets, CI, tests, schemas/migrations, scripts, and documentation.
3. Assign exactly one classification:
   - **Keep** — active, required, or not safe to remove;
   - **Potentially removable — approval required** — no active dependency found after documented checks;
   - **Unresolved — keep** — evidence is incomplete or ambiguous.
4. For each potentially removable file, provide exact path, evidence, reason, removal risk, and a verification plan.
5. Separate broken references or stale documentation from unused files; do not recommend deleting a target that is missing.
6. Provide a proposed removal batch ordered from lowest to highest risk, but do not execute it.
7. State what Gradle build, lint, unit tests, and APK checks must pass after any separately approved removal.

## Deliverable
A final report in this file with:
- summary and repository commit SHA;
- inventory totals;
- classification table covering all investigated candidates;
- exact approval-required removal list;
- unresolved items;
- post-removal validation plan;
- explicit statement that no files were deleted during the audit.

## Safety constraints
- This phase is still an audit, not permission to delete.
- Never delete, move, rename, or edit a candidate without the user's explicit approval of the exact paths.
- Do not change UI, features, architecture, or application behavior.
- If any evidence is uncertain, classify the file as **Unresolved — keep**.

## Final report
_Not yet executed. Replace this line with the verified Phase 3 final report when this phase is run._
