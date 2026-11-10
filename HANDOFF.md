# Session handoff — Shelf launcher

_Last updated: 2026-11-09 19:21 (update at every milestone)_

## TV state right now
- Shelf (`app.shelf.launcher/.HomeActivity`) is installed and **set as the default home**.
- Projectivy is untouched. To go back:
  `adb shell cmd package set-home-activity com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity`
- If a session stops mid-work, the rule is: restore Projectivy as home (command above) unless Shelf is verified working.

## Done
- M1 toolchain (Homebrew `openjdk@17`, `android-commandlinetools`, SDK 36/37.0, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, tv-material 1.1.0). Build: `source env.sh && ./gradlew assembleRelease`.
- M2 grid, M3 focus effect, M4 Top Shelf/clock/background, M5 home behavior, M6 edit mode/hide/favorites/persistence (force-stop verified).

## In progress
- User asked mid-session (19:19) for a redesign: pseudo "liquid glass" panels, centered glass clock,
  modern Frutiger-Aero background, a horizontal **carousel** of **square icons** on a glass shelf.
  Plan: carousel becomes default layout; tvOS grid stays as Settings → Layout → Grid.

## Still to do
- Reboot persistence check (once), inputs launch test on hardware (M7), gfxinfo/meminfo/start-time pass (M8),
  README, final shots.

## Gotchas
- Installing a new HOME app makes Android forget the default home; `deploy.sh` restores whatever was default.
- `screenrecord` starves this SoC — input events get dropped and frame rate collapses; don't judge smoothness from it.
- zsh: no `timeout` command; `deploy.sh` uses a perl alarm.
