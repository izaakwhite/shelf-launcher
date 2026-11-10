# Shelf

A home-screen launcher for an Android 9 TCL TV. Three looks:

- **Aero** (default; Aqua / Dusk / Night): frosted-glass panels, centred glass clock, a glossy aqua backdrop
  drawn in code, a carousel of square glossy icons, glass pills for inputs and settings.
- **Wave**: categories top-left, a wheel of items, an info panel on the right, a slowly colour-cycling background
  with a drifting ribbon of light, soft click sounds and optional background music.
- **Classic grid**: a large hero banner above a grid of app banners.

No network permission, no Google/analytics libraries, open-source deps only (AndroidX, Compose, androidx.tv),
Inter font (OFL, `licenses/Inter-OFL.txt`). All art and sounds are generated in code or by the scripts in `tools/`.
Licensed under Apache-2.0 (`LICENSE`); see `THIRD_PARTY_NOTICES.md`.

> Shelf is an independent project. It is not affiliated with or endorsed by Apple, Sony, Google, TCL, or any app
> shown in the screenshots. All trademarks belong to their respective owners.

## Regenerating assets

```sh
python3 tools/gen_sounds.py                     # click sounds + Wave ambient loop → res/raw
java tools/Banner.java app/src/main/res/font/inter_light.ttf app/src/main/res/drawable-xhdpi   # theme banners
```

## Build

Needs Homebrew `openjdk@17` and `android-commandlinetools` (SDK platform 37.0, build-tools 37.0.0).

```sh
source env.sh
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk (R8, debug-signed)
```

## Deploy

```sh
./deploy.sh              # build, push, pm install (90 s limit + hang check), AOT-compile, launch
./deploy.sh --no-build   # reinstall the last build
echo 192.168.1.50:5555 > .tv-address   # your TV's address (git-ignored); or TV=ip:5555 ./deploy.sh
```

`deploy.sh` puts back whatever home app was the default if the install resets it, and won't launch
anything if someone is using another app on the TV.

`tv.sh DPAD_RIGHT DPAD_CENTER …` sends remote keys for testing, but only while Shelf is in the foreground.

## Using it

- **OK** opens · **hold OK** enters edit mode (D-pad moves, OK drops, Back cancels) · **hold OK again** or
  **Menu** opens: Move to/Remove from Favorites, Hide, App Info, Cancel.
- **Back** on the home screen does nothing; **Home** jumps back to the start.
- **Shelf settings** (the grid icon pill in Aero, Settings category in Wave): Style, icon size / grid columns,
  clock, inputs row, sounds, reduce motion, hidden apps.
- **Theme shortcuts**: "Shelf Aero", "Shelf Wave" and "Shelf Grid" appear in the TV's app list (e.g. in Projectivy);
  opening one switches Shelf to that style.
- **Hidden apps**: Shelf settings → Hidden apps (or the eye pill / Settings category); OK unhides.

## Wave music

Wave plays an original ambient loop by default. To use your own music instead:
```sh
adb push my-music.mp3 /sdcard/Android/data/app.shelf.launcher/files/wave_music.mp3
```
(`.ogg`, `.m4a`, `.wav`, `.flac` work too.) It only plays while Wave is on screen, never over another app's audio,
and can be turned off in Shelf settings → Wave music.

## Wallpaper (Aero and Grid styles)

```sh
adb push my-picture.jpg /sdcard/Android/data/app.shelf.launcher/files/wallpaper.jpg
```
`.jpg`, `.png` or `.webp`. It's centre-cropped to 16:9 and blurred once for the glass; press Home to reload.
Delete the file to go back to the generated backdrop.

## Switch back to Projectivy

```sh
adb shell cmd package set-home-activity com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity
```
(or on the remote: Settings → Apps → Default apps → Home app, if TCL's settings show it).

## Uninstall

```sh
adb shell pm uninstall app.shelf.launcher
```
Then set Projectivy as home with the command above if Android asks which home app to use.
