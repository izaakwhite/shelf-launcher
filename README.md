# Shelf

A home-screen launcher for an Android 9 TCL TV. Three looks:

- **Aero** (default; Aqua / Dusk / Night): liquid-glass panels, centred glass clock, procedural
  Frutiger-Aero backdrop, a carousel of square glossy icons, glass pills for inputs and settings.
- **Wave**: a console-dashboard-inspired cross bar (categories top-left, a wheel of items, an info panel on
  the right), a slowly colour-cycling background with a drifting ribbon of light, and UI sounds.
- **Classic grid**: tvOS-style Top Shelf + banner grid.

No network permission, no Google/analytics libraries, open-source deps only (AndroidX, Compose, androidx.tv),
Inter font (OFL, `licenses/Inter-OFL.txt`). All art and sounds are generated in code or by scripts here.

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
TV=192.168.1.50:5555 ./deploy.sh   # different TV address
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
