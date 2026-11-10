---
name: play-release
description: Use when preparing Project XO for Google Play - signing, app bundle, store listing, privacy policy, Data safety form, testing track, and pre-launch checklist.
---

# Google Play release checklist

Policies change. Before each step, check the current official Play Console requirements; do not rely on this list alone.

## Identity and build
- Choose the final `applicationId` early (cannot change after publishing), e.g. `com.<owner>.projectxo`.
- Build an Android App Bundle (`./gradlew bundleRelease`). Use Play App Signing; keep the upload keystore and passwords backed up outside the repo. Never commit them.
- `targetSdk` must meet Play's current requirement. Enable R8 minification for release and test the release build, not only debug.
- Version: increment `versionCode` for every upload.

## Account
- Google Play developer account (one-time fee). New personal accounts may be required to run a closed test with a minimum number of testers for a minimum period before production access. Check the current rule and plan time for it.

## Store assets
- App icon 512x512, feature graphic 1024x500, at least 2 phone screenshots (take real ones from the emulator), short and full description in Russian (add English later).

## Compliance
- Privacy policy at a public URL (required even for local-only apps if they handle health-adjacent data; state clearly that data stays on the device).
- Data safety form: declare what is collected. In v1 local-only, answer truthfully; revisit when cloud is added.
- Content rating questionnaire. Permissions justified in listing: `ACTIVITY_RECOGNITION` only for steps. If Health Connect is added, complete the health apps declaration.
- Do not make medical claims. Add a short "not medical advice" note for nutrition and activity features.

## Rollout
- Internal testing, then closed testing, then production with staged rollout (start at 10-20%).
- Pre-launch report: fix crashes and accessibility warnings it reports.
- Keep a rollback plan: previous bundle stays available; staged rollout can be halted.
