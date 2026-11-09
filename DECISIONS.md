# DECISIONS

## Device facts (measured 2026-11-09)
| Property | Value |
|---|---|
| Model | TCL "Smart TV", fingerprint `TCL/C02_4K_US/BeyondTV:9/PPR1.180610.011/AP12` |
| Android | 9 (API 28) |
| SoC | Realtek `rtd285o` |
| ABI | `armeabi-v7a` (32-bit only) |
| Screen | 1920x1080 px, density 320 → 960x540 dp |
| RAM | 2 GB total, ~600 MB available at idle |
| Home before install | `com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity` (Projectivy) |

Restore Projectivy as home:
```
adb shell cmd package set-home-activity com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity
```

## Choices
