# Phase 2: test pass

One row per test, both backends, same item where it was possible. "mpv" and "ExoPlayer" columns hold what happened; a difference is a finding, a shared failure is not an mpv bug. Devices: `emu` is the `emulator-5558` phone AVD (Android 14, x86_64; emulated 3G for the first runs, full speed from the quality-switch test on), `OPPO` is the CPH2359 phone (Android 14, arm64, Wi-Fi), `box` is the Xiaomi TV box, `tv` is the `ce_tv` AVD. Blank rows have not run yet, and the last section says why.

Builds: the first emulator runs used the `7e21c0de` debug build; everything from the Pol de Piratenmuis runs on used `95aa0a5b` (this branch with `main` at `106cf3dc` merged in, so it carries #267, #283 and the fake media segments). One OPPO run used another session's build with temporary instrumentation and is marked as such. Procedure: backend switched through `pref_player_backend` in the app's shared prefs with the app force-stopped, states read from the `PlayerViewModel` "Changed player state" lines in logcat, queue and position from `dumpsys media_session`, segments faked with `files/fake_segments.txt` (`INTRO,10,40` and `OUTRO,60,9999`).

### Start and stop

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Fresh start, position 0 | Santiago en de zee S01E06; Pol de Piratenmuis S01E11 | OK. `READY` 3.5 s (Santiago) and 6 s (Pol) after `file-loaded`, through the app's seek to 0 and a second `playback-restart` | OK, `READY` 1 s after start (Pol, OPPO) | emu, OPPO |
| Resume at a saved position | Santiago S01E06; Lady en de Vagebond II (×3); Bluey S3E2; Julia is te nieuwsgierig | **Stuck loader**, 6 of 6 runs on the `main` code. Plays, state stays `BUFFERING`, spinner in the middle, `fileLoaded` false, no trickplay, the quality, speed, audio, subtitle, PiP and lock buttons stay disabled, media session `BUFFERING` with `position=-1`. Same on the emulator at 3G and at full speed and on the OPPO on Wi-Fi ([oppo-mpv-resume-spinner-60s.png](screenshots/oppo-mpv-resume-spinner-60s.png)). One OPPO run on another session's instrumented build *did* reach `READY`, because there the app's own seek ran after the first restart; not reproduced on the `main` build, so it counts as noise | OK. `READY` after 17 s on 3G, 2 s on the OPPO, no spinner, media session `PLAYING` with position | emu, OPPO |
| A seek while stuck | Lady II at 30:51 | The seek bar, the 15 s button and a double tap all unstick it: `seek` → `playback-restart` → `READY`, spinner gone, buttons enabled, trickplay loaded. Needs a real seek; a `KEYCODE_MEDIA_FAST_FORWARD` through the media session did nothing (not followed up, may be the key routing rather than mpv) | n/a | emu |
| No frame at 0 before the resume position (#255) | | Not checked frame by frame. In the stuck runs the first picture was already at the resume position (the `start=` seek is done before the first restart: `file-loaded` → `queuing seek` → `seek` → `playback-restart`). Left for the #255 check | | emu, OPPO |
| Start while the server is slow | Lady II | mpv at 3G: stayed `BUFFERING` 19 s between `file-loaded` and the first restart, no state flapping (the 64 MiB demuxer cache and `cache-pause-initial` hide it) | Flapped `BUFFERING` ↔ `READY` five times in the first 10 s on the emulated 3G link | emu |
| Back out during loading | Julia is te nieuwsgierig | Back 0.3 s after Play: player closed, `BUFFERING` ×4 then the activity went away, no crash, no `am_crash`. Two later attempts failed to start (navigation, not the player), so this is one run, not three | not run | OPPO |
| Back out while playing | Lady II, Bluey S3E2 | OK: two app seeks to the current position on the way out (`queuing seek` ×2, `BUFFERING`), `reportStopped` with the right position | OK | emu |
| Release while buffering | | not run (needs a scripted release; the back-out run above covers the user path) | | |

### Seeking

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Gestures (double tap) | Lady II | Works once `READY`; two `input tap` commands are too slow for the double-tap detector, so this was done with the 15 s button instead | not run | emu |
| Seek bar and the 5 s / 15 s buttons | Lady II | OK, seek → restart → `READY` within 6 s on 3G | not run | emu |
| Remote D-pad steps and hold | | not run | | tv |
| Trickplay while seeking | Lady II | Loads only once `READY` (stuck: no preview) | OK | emu, OPPO |
| Seek past the end | | not run | | |
| Seek while paused | | not run | | |

### Media segments (#271, fake segments)

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Button window | Pol S01E11 from 0 | "Skip intro" shown; the `Asking to skip INTRO` lines started 40 s after `READY` instead of 10 s, but the emulator was overloaded at that point (ANR dialogs for the app, see below), so the delay is not attributed to mpv | "Intro overslaan" shown 1 s after `READY`, at the right position | emu, OPPO |
| Tap to skip | Pol S01E11 | not reached (emulator ANR) | OK: position 26 s → 43 s (segment end 40 s plus the elapsed time) | OPPO |
| Auto-skip | | not run (prefs are `ASK`) | | |
| Outro to next episode | Bluey S3E2 at 58 s | **Stuck state:** "Skip outro" is shown over the spinner ([phone-mpv-stuck-skip-outro.png](screenshots/phone-mpv-stuck-skip-outro.png)) and tapping it does nothing: `Asking to skip OUTRO` every second, no transition, because the next episode was never added to the playlist (it is added on `READY`). **`READY` state:** not reached in the from-0 run (ANR) | Lady II (a film): "Skip outro" jumped to the end, `STOPPED` at 69:04. Pol S01E11: outro button shown at 60 s; the tap on it was not caught because the button had already hidden after its window (#283) | emu, OPPO |
| Auto outro skip with autoplay off | | not run | | |
| Seeking back into a segment | | not run | | |

### Queue

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Next and previous episode | Bluey S3E2 (stuck) | `exo_next` disabled while stuck: nothing in the playlist after the current item | Next enabled | emu |
| Autoplay at the end | | not run | | |
| Still-watching prompt (#70) | | not run | | |
| Playlist index in `prepareMediaItem` | | not run | | |
| What the media session shows | Santiago S01E06, Lady II, Pol S01E11 | `PLAYING`, `size=0`, `position=-1` also when `READY` and playing (three items, two devices) | `PLAYING`, `size=0`, position and buffered position filled, `active item id=0` | emu, OPPO |

### Tracks

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Audio and subtitle dialogs | Lady II | Buttons disabled while stuck; open once `READY` (not exercised further) | | emu |
| Remembered per-series choice (#115) | | not run | | |
| External subtitles | | not run | | |
| ASS styling and the override toggle | | not run | | |
| Track switch mid-play | | not run | | |

### Quality switch (CE)

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Switch to a cap above the file's bitrate | Lady II, 8 Mbps | Same URL from `PlaybackInfo`, nothing reloaded, keeps playing | same path | emu |
| Switch to a cap below it | Lady II, 1.5 Mbps | Reloads the HLS transcode at the right position (`loadfile ... start=2121`), **then sticks again** (`BUFFERING`, spinner, buttons disabled: the reload takes the same `start=` path as a resume) **and reports paused while playing**: the play/pause button shows "Play", the media session says `PAUSED`, the position keeps advancing and the HLS segments keep loading | not run (CE feature, tested when it was built) | emu |

### Formats on the box

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Dolby Vision profile 5 (#96) | | not run | | box |
| Dolby Vision profile 8 (#96) | | not run | | box |
| HDR10 | | not run | | box |
| TrueHD and Atmos passthrough | | not run | | box |
| 24p with refresh-rate switching (#139) | | not run | | box |
| A file ExoPlayer transcodes but mpv direct-plays | | not run | | box |

### Lifecycle

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Picture-in-picture | | not run | | |
| Background audio and return (#79, #80, #90) | | not run | | |
| Rotation | | not run (the player forces landscape; Home came back in landscape on the OPPO, which is the OS, not the app) | | |
| Headphone unplug | | not run | | |
| Audio focus loss | | not run | | |
| A phone call | | not run | | |

### Reporting

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| Progress reaches the server | all runs | Reported every 5 s (`Sessions/Playing/Progress`) also in the stuck `BUFFERING` state, with the right position; Continue Watching on the other device followed | same | emu, OPPO |
| Played and stopped with the right positions | Lady II | `reportStopped ... at 1991000 / 4144000 ms` after backing out at 33:11 | same | emu |

### Settings

| Test | Item | mpv | ExoPlayer | Device |
|---|---|---|---|---|
| hwdec: no, mediacodec, mediacodec-copy | | not run | n/a | |
| vo: gpu, gpu-next | | not run | n/a | |
| ao: audiotrack, opensles, aaudio | | not run | n/a | |

### Why the blank rows are blank

- The OPPO was in use by the performance measurements of #259 for the first hour (its session reinstalled the app under my first run, so that run is discarded), and was free for the last 25 minutes.
- The emulator started throwing ANR dialogs for the app ("Input dispatching timed out" on `MainActivity`, load average 3.1, after the quality-switch transcode and with the OPPO runs in parallel) during the from-0 segment run, so timing-sensitive rows from there on are not trustworthy on it. The ANR is the emulator, not mpv: the earlier runs on the same build had no ANR, and the OPPO had none.
- The Xiaomi box was not touched: the format, passthrough and refresh-rate rows need it, and nothing above changes what those rows would show.
- Home-screen automation is flaky because the hero carousel keeps `uiautomator` from reaching an idle state; the item pages and the player dump fine. Later runs tapped the Continue Watching cards by position.
- Rows that need a scripted release, audio focus, a phone call or PiP were left for a session with the phone in hand; they are hypotheses 6 and 9 in [3-findings.md](3-findings.md).
