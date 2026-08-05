# Wear OS companion app: live biometrics + remote control (not started)

Active Workout already runs behind a foreground service with a persistent notification exposing a
Pause/Resume action (see `docs/architecture.md`'s "Active Workout - detailed spec"). A paired
Samsung Galaxy Watch or Pixel Watch (both run stock Wear OS) could extend that same session with a
second, live control surface, plus live heart-rate/calorie data the phone alone can't get.

## The idea

A new Wear OS app/module (e.g. `:wear`, alongside the existing `:app`/`:core:*`/`:feature:*`
modules per `docs/architecture.md`'s "Module structure") that:

- Runs Health Services' `ExerciseClient` on-watch to read live heart rate/calories during an
  Active Workout, streamed to the phone via the Data Layer API's `DataClient` for a live chart on
  the Active Workout screen.
- Sends control messages (`MessageClient`) for pause/resume/end-rest, wired to invoke the *same*
  domain use-cases the phone's persistent notification's Pause/Resume action already calls
  (owned today by `:app`'s `service/` package) - the watch becomes a second input surface onto
  existing logic, not a parallel/duplicated control path.
- Reflects phone-side session state (current exercise, elapsed/rest timer, paused state) back down
  to the watch via `DataClient`, so a pause initiated from either side stays consistent on both.

## Why this is live where Health Connect import isn't

Health Connect (`todo-health-connect-import.md`) only ever gets data after some other app syncs it

- there's no way to get a live chart or a control channel out of it. A real app running on the
  watch, talking to the phone directly over the Data Layer API, is the only way to get both live
  biometrics and a remote pause/resume during the workout itself.

## Scope boundary

Samsung/Pixel Wear OS watches only. Fitbit hardware can't run a third-party companion app at all
(Fitbit shut down third-party Fitbit OS development) - Fitbit's only path to Regimen is the
Health Connect one, and gets no live data or control channel either way.

## Open design question (unresolved - pin down before real design work starts)

Is the watch app strictly a **remote for a phone-driven session** (phone owns all state, watch is
thin display + control), or should the watch be able to **independently start/drive a session**
too? This materially changes how much state ownership and sync logic is needed - the "remote
only" version has one source of truth (the phone); a watch-initiated session needs real
reconciliation logic for whichever side started it.

## Scale of the change

Flagged explicitly as a large addition, not a quick add-on:

- New Gradle module + Wear Compose UI.
- `BODY_SENSORS`/Health Services permissions on the watch.
- Two concurrent session lifecycles to keep synchronized - the watch's Health Services exercise
  session and the phone's foreground-service workout session - across pause/resume/end from either
  side, plus reconnect/resync after a Bluetooth disconnect.

## Status

- [ ] Not started.
