---
name: android-compose
description: Use when writing Kotlin and Jetpack Compose code for Project XO - project structure, state, theming with tokens, navigation, previews, build and run on emulator.
---

# Android + Compose rules for Project XO

## Structure
- Single Gradle module `app` at first. Packages by feature: `feature/water`, `feature/food`, `feature/steps`, `feature/home`, `core/ui` (theme, tokens, shared components), `core/data` (Room, repositories), `core/model`.
- Keep domain logic (models, calculations, repository interfaces) free of `android.*` imports so it can move to Compose Multiplatform later.
- `minSdk 26`, `targetSdk` = latest stable required by Play. Kotlin DSL, version catalog (`libs.versions.toml`).

## State
- One `ViewModel` per screen, `StateFlow<UiState>` (immutable data class), events as functions. Collect with `collectAsStateWithLifecycle()`.
- Composables take state and lambdas. No ViewModel inside leaf components. Hoist state.
- Use `remember`/`derivedStateOf` only when needed. Keep lambdas stable.

## Theming (no hardcoding)
- All colors, spacing, radii, type sizes live in `core/ui/theme` as tokens (`XoColors`, `XoSpacing`, `XoType`) provided via `CompositionLocal`. Light and dark from `isSystemInDarkTheme()`.
- One accent color, one font family. No raw `Color(0xFF...)` or `dp`/`sp` literals in feature code except 1-2 layout constants named in the token file.
- All user-visible text in `strings.xml` (Russian default).

## Quality
- Every screen has `@Preview` for light, dark and large font scale (1.3).
- Touch targets >= 48dp, content descriptions on non-text controls, contrast >= 4.5:1.
- Lists: `LazyColumn` with stable `key`s.
- Avoid heavy work in composition. Never block the main thread.

## Run and verify
- Build: `./gradlew assembleDebug`. Tests: `./gradlew testDebugUnitTest`. Lint: `./gradlew lint`.
- Prove UI works with an emulator screenshot or recording, not a statement.
