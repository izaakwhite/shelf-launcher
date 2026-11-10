# Session handoff — Shelf launcher

_Last updated: 2026-11-09 20:40_

Repo: https://github.com/izaakwhite/shelf-launcher (**private**). Remote uses HTTPS with the `gh` credential helper
(configured in this repo's local git config only; SSH keys aren't set up on this Mac).

## TV state right now
- Shelf is installed and **is the default home**. Current style on the TV: **Wave** . Wave category bar shrunk and wheel moved down (20:31).
- Projectivy is untouched. To go back:
  `adb shell cmd package set-home-activity com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity`
- If a session has to stop mid-work: restore Projectivy (command above) unless Shelf is verified working.

## Done
All 8 milestones, plus the mid-session redesign: Aero (Aqua/Dusk/Night) glass carousel, Wave (console-dashboard style)
with sounds, Classic grid; theme shortcuts in the TV app list. Measurements are in DECISIONS.md.

## Next ideas
1. Wave performance: draw the ribbon on a SurfaceView/TextureView at 30 fps or half resolution so navigation frames stay light.
2. Fix the occasionally-swallowed first key after returning home.
3. A proper theme picker (list) instead of cycling the Style row.

## Gotchas
- Never send keys while another app is in front: use `./tv.sh` (it checks). `deploy.sh` won't launch over another app.
- Installing a new HOME app resets the default home; `deploy.sh` restores it.
- `screenrecord` and `input keyevent` both distort performance on this SoC; use `sendevent /dev/input/event2` to measure.

## 20:40
- Legal fixes applied (LICENSE, notices, neutral wording, screenshots pruned, history rewritten to noreply email).
- Wave: softer clicks, gapless original ambient loop with fades, label no longer jumps.
