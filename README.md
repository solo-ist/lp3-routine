# Routine — a step-by-step routine timer for the Light Phone III

Routine walks you through a routine one step at a time, with a timer on each
step. It replaces Routinery, so the part of the day where routines matter
most doesn't have to happen on the iPhone.

- **Home** lists your routines. Tap one and the first step is already
  running. That's two taps from the toolbox.
- **The player** shows one step: its name, the time left, a thin progress
  line, and what's next. Nothing else of the routine is visible, on purpose.
  - **pause / resume**
  - **+1** (long-press for **+5**)
  - **skip**
  - **done**
  - **Back** asks whether to end the routine.
- **When a step runs out** the phone buzzes, whether the screen is on or
  not: eight firm pulses over about 5 s, cut short as soon as you open the
  player or tap anything in it. If the player isn't in front, a notification also
  appears. LightOS has no shade, but Controls shows it as a banner and wakes
  the screen: "morning · shower — time's up". Tapping it opens the player.
- **By default the step waits for you.** It counts into overtime ("+2:14")
  until you tap *done*, so nothing slips by while you weren't looking. Turn
  *auto-next* on to have timed steps advance by themselves.
- **Untimed steps** count up and advance only on *done*.
- **The summary** shows what actually happened next to what was planned, per
  step. It's not a score, just the real numbers.

## Why it isn't a light-sdk tool

It was meant to be one. The SDK can't do the two things a routine timer
exists for:

- **Tell you a step is over when you aren't looking.** A tool can't reach an
  Android `Context`, so it can't vibrate outside a tap. `AlarmManager`
  (`android.app.*`) and `getSystemService(` are blocked outright, and a
  coroutine `delay` stops when the CPU sleeps.
- **Remind you to start.** A push reaches `onPushNotification(ByteArray)`
  with no context at all. It can't vibrate, notify, or even read its own
  database.

So, like [Menu](https://github.com/solo-ist/lp3-menu) and
[Notifications](https://github.com/solo-ist/lp3-notifications), it's an
ordinary Android app that declares the LightOS toolbox marker. Its only
dependency is the platform.

## Timing you can trust

The timer is never a counter ticking in memory. Each step stores **anchors**:
when it started, how long it has been paused, and how much time was added.
Every screen recomputes from those, so the time left is right after the
screen was off, after LightOS pulled itself to the front, or after the
process was killed.

- Durations are measured on `elapsedRealtime`, which is monotonic, counts
  through sleep, and can't be moved by a clock change.
- After a reboot, which resets that clock, the wall-clock anchors carry it
  across. The time the phone was off counts, because it passed.
- The buzz is an exact `ELAPSED_REALTIME_WAKEUP` alarm, re-armed after every
  change, so there's only ever one and it always points at the current step.
- The player's tick and the alarm can both notice a step ending. Only the
  first one to claim it buzzes.
- A step advanced automatically records exactly its plan, and the next step
  starts when the previous one was *due*, not when the phone noticed. A late
  alarm can't steal time from the step after it.

## Honest history

Runs are append-only. Each run copies the routine's name and every step's
name and plan when it starts, so editing or deleting a routine can't rewrite
what already happened. Timestamps are UTC milliseconds.

A run counts as **complete** when enough of its steps were done. The default
threshold is all of them; set `threshold` per routine to change that. Ending
early marks the rest *not reached*, not *skipped*.

A run belongs to the day it **started** in. That day rolls over at 4:00 a.m.,
so a routine at 1:30 a.m. counts toward the day you think of as today.

## Making routines

**On the phone.** Tap **+ new routine**, or long-press a routine to edit it.

- Name it with the LightOS keyboard. Names are the only thing you type.
- **days**: toggle each weekday.
- **+ add step** asks for a name, then opens the step. Set its length with
  **−5 −1 +1 +5**. Going down to zero makes it *untimed*: it counts up and
  waits for *done*.
- On a step: **move up**, **move down**, **delete step**, and **+ next step**,
  which adds the next one straight away, so a routine can be entered start to
  finish from one screen.
- **delete routine** removes it from home. Its past runs are kept.

Every change saves as you make it; there's no save button to forget. Runs
already recorded keep their own copy of names and plans, so editing never
rewrites history.

**From the computer**, for bulk authoring, write a file and push it:

```sh
adb push examples/routines.json /sdcard/Android/data/ist.solo.routine/files/
```

The next time Routine opens, it previews the file and imports it on confirm.
It deletes the file either way. A routine with the same name as an existing
one is replaced, and its history stays attached. Since Android 11 no other
app can write into that directory. Anything that appears there came from
adb, which is why it's safe to act on.

The format (see [`examples/routines.json`](examples/routines.json)):

| Field | |
|---|---|
| `name` | ≤ 40 characters |
| `weekdays` | `["mon", …]`, or omit for unscheduled |
| `target` | `"07:30"`, display only for now |
| `threshold` | percent of steps that must be done, default 100 |
| `steps[].name` | ≤ 40 characters |
| `steps[].min` / `steps[].sec` | 0–180 min; omit both for an untimed step |

A file that fails any check is rejected whole.

## Permissions

| Permission | Why |
|---|---|
| `VIBRATE` | The step-end buzz, and the touch tick matched to LightOS |
| `USE_EXACT_ALARM` | The buzz on time with the screen off. Granted at install. |
| `RECEIVE_BOOT_COMPLETED` | Re-arms a run in progress after a reboot |
| `POST_NOTIFICATIONS` | "Time's up" when the player isn't on screen. Asked once; if refused, the buzz still works. |

No `INTERNET`: nothing Routine knows ever leaves the phone. `allowBackup` is
off, and data-extraction rules exclude everything from both cloud backup and
device transfer.

## Build

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug   # "Routine (debug)", its own appId and data
```

Release:

```sh
ROUTINE_SIGNING_PASSWORD=$(op read "op://Project/Routine signing key/password") \
  ./scripts/release.sh
```

Release builds sign with a private identity at
`~/.android-keys/soloist-routine.jks`. `scripts/release.sh` refuses a dirty
tree, then builds, runs `scripts/verify-release.sh` (pinned signer,
non-debuggable, backups off) and installs only what it verified.

Debug builds carry a **spike panel**: long-press the "routine" title for
short test runs and a test notification. They have their own application id,
so test runs never land in real history.

## Status

Built: the player and the on-device editor. Not built yet:

- reminders (Phase 2): local exact alarms, now that there's no sender
- streaks, rest days and the calendar (Phase 3)
