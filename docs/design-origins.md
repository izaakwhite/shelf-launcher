# Design origins

Shelf's three styles are **inspired by** familiar TV and console home screens. Nothing from those products was copied.
This note records what that means in practice. It's a practical summary, not legal advice.

## What is borrowed: ideas only

- **Wave**: the general interaction idea of a horizontal row of categories with a vertical list of items under the
  selected one, plus a calm animated background. This layout pattern is a common, functional way to navigate with
  a D-pad.
- **Aero**: the general aesthetic of glossy, frosted-glass interfaces from the late 2000s.
- **Classic grid**: the common TV-launcher pattern of a large hero banner above a grid of apps.

Layout ideas and functional arrangements like these are generally not protected by copyright. Copyright protects
specific creative expression: actual artwork, icons, animations, sounds, music and code.

## What is original

- **Code**: all of it was written for this project (Kotlin/Jetpack Compose).
- **Category and system icons**: simple line drawings made in code (`WaveScreen.kt`, `ImageLoader.kt`).
- **Wave ribbon**: a procedurally generated sine-line bundle (`WaveArt.kt`). It is not traced from or modelled on
  any specific product's animation.
- **Backgrounds**: procedural gradients, blooms and bubbles (`AeroArt.kt`). Wave's background hue cycles slowly. It
  deliberately does **not** change colour by month.
- **Sounds and music**: synthesized from sine waves and noise by `tools/gen_sounds.py`. The Wave music is an
  original chord progression, not a transcription or imitation of any existing track.
- **App icon and banners**: drawn by `tools/AppIcon.java` and `tools/Banner.java`.
- **Font**: Inter, under the SIL Open Font License.

## What is deliberately not included

- No artwork, icons, sounds, music, fonts or code taken from Sony, Apple or any other company.
- No product names or trademarks (for example "PlayStation", "PS3", "XMB", "tvOS", "Apple TV") in the app's UI,
  its name, its icons or its marketing.

## Guidance for contributors and users

- Don't add third-party assets (rips of console sounds or music, official icons, logos) to this repository.
- Users may point Wave at their **own** music file on their own TV (`wave_music.mp3` in the app's files folder).
  That file stays on the device and is never part of this project.
- Describe Shelf by what it is ("a category-bar launcher", "a frosted-glass launcher"), not as a copy of another
  product.
- If Shelf is ever sold or distributed commercially, get proper legal advice first, in particular on trade dress
  and any design patents.
