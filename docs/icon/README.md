# RedForge icon assets

The launcher icon implementation uses the supplied RedForge SVG artwork as an Android VectorDrawable so the artwork stays resolution-independent inside the APK.

## Android launcher icon

- Adaptive launcher foreground: `app/src/main/res/drawable-nodpi/ic_launcher_foreground.xml`
- Adaptive background: `app/src/main/res/drawable/ic_launcher_background.xml` (solid `#0B0B0D`)
- Adaptive definitions: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
- Pre-Android 8 fallback: `app/src/main/res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`

The vector artwork is placed on a 1280 x 1280 internal canvas, keeping the important mark inside the approximate 72 x 72 dp adaptive safe area of the 108 dp icon canvas.

The application manifest already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, so no manifest change is required.

The 512 x 512 Play Store PNG is kept separately from the Android launcher resources because Play Store submission expects the exported bitmap asset.
