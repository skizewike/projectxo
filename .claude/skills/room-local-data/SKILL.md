---
name: room-local-data
description: Use when designing or changing local storage for Project XO - Room entities, DAOs, migrations, repositories, daily aggregates for water, food and steps.
---

# Local data with Room

## Principles
- All data stays on device in v1. Access only through repository interfaces in `core/data` so cloud sync can be added later without touching UI.
- Store timestamps as UTC epoch millis plus the local date (`yyyy-MM-dd`) the user saw, so day totals survive timezone changes and travel.
- Units stored in base units: water in ml, energy in kcal, macros in grams, steps as count.

## Entities (start small)
- `WaterEntry(id, ts, localDate, ml)`
- `FoodEntry(id, ts, localDate, name, kcal, proteinG, fatG, carbG)`
- `FoodTemplate(id, name, kcal, proteinG, fatG, carbG)` for quick add
- `StepsDay(localDate, steps, baselineAtMidnight)`
- `Goal(id, type, target)` (water ml, kcal, steps)

## Rules
- DAOs return `Flow` for observed data; write operations are `suspend`.
- Day summary comes from a query (`SUM ... GROUP BY localDate`), not from recomputing in UI.
- Export schemas (`room.schemaLocation`) and commit them. Every schema change gets a written `Migration` and a migration test. Never use `fallbackToDestructiveMigration` in release.
- Undo for deletes: soft approach in UI layer (hold item for a few seconds) rather than extra tables.
- Test DAOs with an in-memory database.

## Privacy
- Health-adjacent data. No logging of entries. Include the data in a backup decision explicitly (`allowBackup`/`dataExtractionRules`) and record it in `docs/DECISIONS.md`.
