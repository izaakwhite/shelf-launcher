# Legal

## Originality statement

Shelf is an original work. **No designs, artwork, icons, animations, sounds, music, fonts or source code were
copied from any other product**, including the home screens of Sony, Apple, Google or TCL devices.

- The visual styles (Wave, Aero, Classic grid) are **inspired by** general, widely used interface conventions:
  a category bar with a list of items, glossy frosted-glass panels, and a hero banner above an app grid.
  Only these general ideas were used. Every element on screen was designed and implemented independently for this
  project.
- All graphics are drawn by code in this repository (`AeroArt.kt`, `WaveArt.kt`, `ImageLoader.kt`,
  `tools/AppIcon.java`, `tools/Banner.java`). No image was traced, sampled or extracted from another product.
- All sounds and music are synthesized from scratch by `tools/gen_sounds.py`. They are not recordings,
  transcriptions or imitations of any existing sound effect or musical work.
- The only third-party creative asset is the Inter typeface, used under the SIL Open Font License 1.1.

See [docs/design-origins.md](docs/design-origins.md) for a fuller account of what was borrowed (ideas only) and what
is original.

## Trademarks

Shelf is an independent project. It is not affiliated with, sponsored by or endorsed by Sony Interactive
Entertainment, Apple Inc., Google LLC, TCL, or any app or service shown in the screenshots. PlayStation, Apple TV,
tvOS, Android TV and all other product names and logos are trademarks of their respective owners. They are mentioned
here only to identify the products, and are not used in the app's name, icons or user interface.

App icons and banners that appear while Shelf is running belong to the installed apps. Shelf displays them the
same way any Android launcher does, and does not distribute them.

## User-supplied content

Shelf can play a music file and show a wallpaper image that the user places on their own TV. These files stay on
the user's device and are not part of this project. Users are responsible for having the right to use them.

## Licenses

- Shelf's own code and assets: Apache License 2.0 ([LICENSE](LICENSE)).
- Third-party components: see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The Inter typeface is under the
  SIL Open Font License 1.1 ([licenses/Inter-OFL.txt](licenses/Inter-OFL.txt)).
- The app bundles these texts and shows them in Shelf settings → Open-source licenses.

_This statement describes how the project was made. It is not legal advice._

## Personal sound overrides

Shelf can play audio files that a user places on their own device (`scripts/private-sounds.sh`). The project bundles, links to and fetches no third-party audio; that folder is git-ignored. Anyone who uses it is responsible for having the right to use the files they supply.
