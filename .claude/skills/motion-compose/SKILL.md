---
name: motion-compose
description: Use when building or tuning animations in Project XO - the XO splash that becomes the water ring, progress rings, counters, springs, timings, reduced-motion support.
---

# Motion language (expensive = precise, not loud)

## Principles
- One idea per animation. Motion explains change (a value grew, a screen opened). Never decorate.
- Springs for things the user touches or that settle (`spring(dampingRatio = 0.8f..0.9f, stiffness = Spring.StiffnessMediumLow)`); tweens with easing for scripted sequences. Little or no overshoot, except one deliberate overshoot on the splash.
- Durations: micro 100-180 ms, state change 220-320 ms, screen transition 350-450 ms, splash 2.0-2.5 s first launch, 0.8 s afterwards.
- Stagger lists by 30-50 ms, max 6 items.
- Animate transform and alpha (`graphicsLayer`) rather than layout where possible.

## Tools
- `Animatable` for sequenced values; `animateFloatAsState` / `animateDpAsState` for simple state changes; `updateTransition` for multi-property.
- Rings: `Canvas` with `drawArc` (start -90f, `StrokeCap.Round`), sweep driven by an animated fraction. Counters: animate the number with `Animatable` and show with tabular figures.
- Splash: Android 12+ system splash via `androidx.core:core-splashscreen` (static icon only), then a Compose splash screen that runs the XO sequence. Keep the system splash visible until the first frame is ready so there is no white flash.
- Shared element splash to home: the O ring is the same element; use `SharedTransitionLayout` + `sharedElement`/`sharedBounds`, or animate one `Canvas` ring through shared `Animatable` values (center, radius, stroke, sweep).
- The loading bar is real: the ring's sweep follows actual init steps (database open, settings read), with a minimum visible time so it never flashes.

## Accessibility and performance
- Respect reduced motion: read `Settings.Global.ANIMATOR_DURATION_SCALE`; if 0, skip to end state. Offer a short fade instead of motion.
- No allocation in `onDraw`/`drawBehind`. Hoist `Path`, `Brush`, `Stroke` with `remember`.
- Verify with a recording on a mid-range device or emulator with GPU profiling; target steady 60 fps, no dropped frames during the splash-to-home transition.

## Spec first
Before coding an animation, write in `docs/DESIGN.md`: trigger, elements, durations, easing/spring values, end state. Opus decides these; the worker implements them exactly.
