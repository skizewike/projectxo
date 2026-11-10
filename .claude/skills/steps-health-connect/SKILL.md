---
name: steps-health-connect
description: Use when implementing step counting in Project XO - the step counter sensor, Health Connect, permissions, daily baseline, reboot handling, and Play policy implications.
---

# Steps

## Choose the source (decision recorded in docs/DECISIONS.md)
- **v1 default: hardware step counter** (`Sensor.TYPE_STEP_COUNTER`). Needs runtime permission `ACTIVITY_RECOGNITION` (Android 10+). No Play health declaration needed. Value is cumulative since last boot.
- **Later option: Health Connect** (`androidx.health.connect:connect-client`, `StepsRecord`, permission `android.permission.health.READ_STEPS`). Gives history from other apps and watches, but needs the Health Connect availability check, a permission rationale screen, and a Play Console health apps declaration. Verify current policy before adding.

## Sensor approach
- Register a listener while the app is open, and read the latest value on start. For background accuracy, use a small `WorkManager` periodic job or `PendingIntent`-free reads on app open; avoid a permanent foreground service in v1.
- Daily steps = `current counter - baselineAtMidnight`. Store `baselineAtMidnight` in `StepsDay`.
- Reboot: if `current counter < last seen counter`, the device rebooted; set baseline to 0 for the new session and add the previous day's progress, do not go negative.
- New day: on first read of a new local date, store a fresh baseline.
- If permission is denied, show a calm explanation screen with a button to settings. Never loop the prompt.

## UI contract
- Expose `Flow<Int>` of today's steps from a `StepsRepository` interface; the sensor implementation is one of two swappable implementations (sensor, Health Connect).
- Test the day/reboot logic with plain unit tests on fake counter readings (no Android needed).
