# Screenshots

Every screenshot in the README, on the website and in the Play listing comes from the
**Jellyfin demo server** (`https://demo.jellyfin.org/stable`, user `demo`, empty password): a
public-domain library (Sahara, King Lear, Pioneer One, Caminandes…), so there is no third-party
artwork and no personal server name in the repo. The one exception is the four download screens
(§Phone): the demo account can't download (`EnableContentDownloading` is off in its policy), so
those come from your own server on your own phone.

Where they live:

| Use | Folder | Format |
|---|---|---|
| README and website | `images/screenshots/` (copy to `site/images/screenshots/` after a change: GitHub Pages deploys only `site/`) | webp, phone 540 wide, tablet and TV 1280 wide |
| Play listing | `fastlane/metadata/android/en-US/images/{phone,sevenInch,tenInch,tv}Screenshots/` | PNG, native size, `N_en-US.png` |

General rules: dark theme, English UI (`adb shell cmd locale set-app-locales
nl.midasvo.findroid.ce.debug --locales en-US`; note the old value and restore it), status bar in
demo mode, no open drawer on TV (it shows the user and server name). Look at every file before
committing. Playing something on the demo server leaves a resume position; it resets daily, so
that is fine.

Demo-mode status bar (12:00, wifi, full battery), and `exit` when done:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

Capture: save on the device, then pull. Redirecting `screencap` in PowerShell corrupts the PNG,
and Git Bash rewrites `/sdcard/...` unless `MSYS_NO_PATHCONV=1` is set.

```bash
MSYS_NO_PATHCONV=1 adb -s <serial> shell screencap -p /sdcard/s.png
MSYS_NO_PATHCONV=1 adb -s <serial> pull /sdcard/s.png out.png
```

## Emulators

`scripts/tablet-avds.sh` creates and starts the throwaway AVDs (`create`, `start`, `stop`,
`delete`, `portrait`, `landscape`, `shot`); see [tablet/6-test-matrix.md](tablet/6-test-matrix.md).

| AVD | Port | Window | Used for |
|---|---|---|---|
| `ce_phone_tmp` (Pixel 6) | 5580 | 1080×2400 px, 412×915 dp | phone README and listing |
| `ce_tablet_8` (small_tablet, set to 2048×1280 at 320 dpi) | 5582 | 1024×640 dp | 7-inch listing |
| `ce_tablet_10` (Pixel Tablet) | 5584 | 2560×1600 px, 1280×800 dp | tablet README and 10-inch listing |
| `ce_tv_tmp` (`tv_1080p`, `android-tv;x86` image) | 5588 | 1920×1080 | TV README and listing |

Install the debug build of `main` (`./gradlew :app:phone:assembleLibreDebug`, the `x86_64` APK;
`:app:tv:assembleLibreDebug`, the `x86` APK) and sign in: Add server → Users → tap demo → Login.
Delete the AVDs afterwards; they are 3 to 6 GB each.

## Phone

Portrait shots lose the status bar and the navigation bar. On the Pixel 6 emulator (gesture
navigation, 2400 px tall) that is `crop=1080:2190:0:130`; on a phone with three-button navigation
the bar is taller, use `crop=1080:2166:0:100` (OPPO CPH2359).

```bash
# Portrait
ffmpeg -i in.png -vf "crop=1080:2190:0:130,scale=540:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp

# Landscape player
ffmpeg -i in.png -vf "scale=1080:-1:flags=lanczos" \
  -c:v libwebp -quality 84 -compression_level 6 images/screenshots/name.webp
```

Keep each file under about 200 KB. The README shows the portrait shots in a table of three columns,
so add or remove them in threes.

| File | Screen | Source |
|---|---|---|
| `home.webp` | Home with the carousel and Continue Watching | demo |
| `movie.webp` | Sahara's page from the top | demo |
| `episode.webp` | Pioneer One S1E2 from the top | demo |
| `downloads.webp` | Downloads with the storage bar, active rings and a finished show | **own server** |
| `season.webp` | A season with downloaded, downloading and not downloaded episodes | **own server** |
| `downloaded-show.webp` | A downloaded show page with episode sizes | **own server** |
| `download-quality.webp` | The download quality dialog over a movie page | **own server** |
| `subtitle-style.webp` | Settings → Player → Subtitle style with a non-default colour | demo |
| `home-rows.webp` | Settings → Interface → Home rows | demo |
| `player-quality.webp` | Landscape: the video quality dialog over the player | demo |

For the four own-server shots: start a large movie and a few episodes together to get active rings
and finished items in one shot, and remove them afterwards with Select on the Downloads screen
(wait for the undo snackbar). Check them for user names and server addresses.

## Tablet

`ce_tablet_10` in landscape (`scripts/tablet-avds.sh landscape ce_tablet_10`). The launcher's
taskbar sits at the bottom of every screen except the player: crop it (it starts at y=1480 in
landscape, 2440 in portrait).

```bash
# Landscape, taskbar cropped
ffmpeg -i in.png -vf "crop=2560:1480:0:0,scale=1280:-1:flags=lanczos" \
  -c:v libwebp -quality 80 -compression_level 6 images/screenshots/tablet-name.webp
# Player (no taskbar)
ffmpeg -i in.png -vf "scale=1280:-1:flags=lanczos" -c:v libwebp -quality 80 -compression_level 6 out.webp
```

| File | Screen |
|---|---|
| `tablet-home.webp` | Home: rail, three carousel pages, Continue Watching |
| `tablet-movie.webp` | Sahara's hero page with the poster beside the text |
| `tablet-season.webp` | Pioneer One Season 1 as list-detail, E2 selected |
| `tablet-player.webp` | The player with the controls and the episode panel open |
| `tablet-library.webp` | The Movies grid |
| `tablet-home-portrait.webp` | Home in portrait (not in the README today) |

## Android TV

`ce_tv_tmp`. `adb shell input tap` doesn't reach the TV app: drive it with
`input keyevent DPAD_UP|DPAD_DOWN|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|BACK`, one key at a time with a
screenshot before the next, because focus restore can put you somewhere unexpected. `input text`
works in the search and login fields.

```bash
ffmpeg -i in.png -vf "scale=1280:-1:flags=lanczos" \
  -c:v libwebp -quality 80 -compression_level 6 images/screenshots/tv-name.webp
```

| File | Screen | Issue |
|---|---|---|
| `tv-home.webp` | Home with the hero, focus on the first card | #132 |
| `tv-library.webp` | Movies grid, focus on a card so the A–Z bar and counter show | #135 |
| `tv-season.webp` | Pioneer One Season 1 with an episode's details | #130 |
| `tv-movie.webp` | Sahara's page, Play focused | |
| `tv-player.webp` | Player overlay, shown with Down | #131 |
| `tv-trickplay.webp` | Right a few times with the controls hidden | #134 |

## Play listing

Native PNGs, same subjects per form factor (Home, a movie page, the Movies library, Pioneer One
Season 1, the player with controls). Google Play rejects a side longer than twice the other, so the
phone shots are cropped to 1080×2160 (`crop=1080:2160:0:0`; the landscape player to
`crop=2160:1080:120:0`). The tablet sets crop the taskbar (`2560×1480` on 10-inch, `2048×1160` on
7-inch); the player shots are full frames. Details per set in [play-store.md](play-store.md).
