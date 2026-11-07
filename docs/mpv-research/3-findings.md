# Phase 3: findings

One entry per difference from ExoPlayer. Impact: **broken** (the feature does not work), **wrong** (works but not as ExoPlayer does), **cosmetic**. Where: CE (a CE file or CE change), upstream (`MPVPlayer.kt` as Findroid ships it), libmpv. Size: S (one function), M (one file), L (more than one file or a design change). Entries marked *hypothesis* come from the phase 1 code reading and still need the phase 2 reproduction before they get an issue.

### 1. Resuming at a saved position leaves the player in `BUFFERING` forever

- **Status:** reproduced, 6 of 6 runs on the `main` code: phone emulator (four items, at 3G and at full speed) and the OPPO on Wi-Fi (two items), see [1-inventory.md](1-inventory.md) and [2-test-pass.md](2-test-pass.md). The quality switch (CE) reloads through the same `start=` path and sticks the same way.
- **Impact:** broken on phone. The spinner stays over the picture, the control buttons that wait for `fileLoaded` stay disabled (quality, speed, audio, subtitles, PiP, lock), trickplay never loads, the previous and next episodes are never added to the playlist, the media session says buffering. Seen as a consequence: the "Skip outro" button is shown but tapping it does nothing, because `seekToNextMediaItem()` has nothing to go to; the Next button is disabled for the same reason. A user seek (seek bar, the 5 s and 15 s buttons, a double tap) unsticks it. Invisible on TV, which has no spinner, but the playlist and trickplay effects are the same. Progress reporting is not affected.
- **Cause:** `MPVPlayer.kt:534-555`. `STATE_READY` is only set by a second `MPV_EVENT_PLAYBACK_RESTART` or a `paused-for-cache` flip after the first restart. Before #267 the unconditional initial seek (even to 0) always produced that second restart. #267 passes the resume position to `loadfile` as `start=` and skips the seek when it matches `initialSeekTo`, so the first item with a resume position never gets a second restart. The same path is taken by the quality switch at index 0 with a position (hypothesis).
- **Fix:** in the `!isPlayerReady` branch, after `pause=false`, call `setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)` when no seek was issued. The robust version makes `applyInitialSeek()` report whether it seeked, skips the seek when the target is 0, and sets `READY` on the first restart whenever no seek is pending.
- **Where:** CE for the minimal fix (the `start=` path is #267). The robust version is worth offering upstream, because the "`READY` only on the second restart" design is upstream's.
- **Size:** S.

### 2. The media session gets no queue and no position from mpv

- **Status:** reproduced (emulator and OPPO, three items, `dumpsys media_session`: `size=0`, `position=-1` also when `READY` and playing for minutes). Cause is a hypothesis.
- **Impact:** wrong. Media controls on headsets, Android Auto and the lock screen can't show progress or the queue. The earlier phone tests worked around it.
- **Cause (hypothesis):** `getAvailableCommands()` (`MPVPlayer.kt:903-927`, `1745-1756`) advertises no `COMMAND_GET_TIMELINE`, so Media3 publishes no queue, and the position may be gated the same way. `getCurrentPosition()` is also `TIME_UNSET` before the first `time-pos`.
- **Fix:** advertise the commands the player actually implements (`GET_TIMELINE`, `PREPARE`, `STOP`, the volume getters) and fire `onAvailableCommandsChanged`.
- **Where:** upstream. **Size:** S.

### 3. Stubs reachable from outside controllers *(hypothesis)*

- **Impact:** broken (crash). 14 `TODO()` stubs; `COMMAND_CHANGE_MEDIA_ITEMS` and `COMMAND_SET_VIDEO_SURFACE` are advertised, so a controller's remove, move, replace or clear, or a `TextureView` surface, reaches a stub and throws `NotImplementedError`, which the app's `catch (e: Exception)` blocks don't catch. Detail in [1a-player-contract.md](1a-player-contract.md).
- **Fix:** implement remove and clear on mpv's playlist (`playlist-remove`, `playlist-clear`), make the rest no-ops, and stop advertising what isn't implemented.
- **Where:** upstream. **Size:** M.

### 4. `addMediaItem(item)` without an index throws *(hypothesis)*

- **Impact:** wrong. `BasePlayer.addMediaItem(item)` passes `Int.MAX_VALUE`; `addMediaItems` doesn't clamp, so it throws `IndexOutOfBoundsException`. The app uses the indexed form, so it's unreached today. Related: `setMediaItems` keeps the caller's list, and `playQueueItem` passes an immutable `listOf(x)`, so the next `addMediaItem` throws and is swallowed, which would mean no previous or next episode after "play from queue" on mpv.
- **Where:** upstream. **Size:** S.

### 5. Errors never surface *(hypothesis)*

- **Impact:** broken. `MPV_EVENT_END_FILE` is not handled and `getPlayerError()` is always null, so a stream that fails to open spins forever, with no toast on phone and no `playbackError` on TV. The audio-decoder fallback can't trigger on mpv.
- **Fix:** handle `END_FILE` with an error reason, set a `PlaybackException`, go `IDLE`, fire `onPlayerError`.
- **Where:** upstream. **Size:** S.

### 6. `playWhenReady` drifts from what mpv does

- **Status:** the quality-switch half is reproduced (emulator, Lady II to 480p: the play/pause button shows "Play", the media session says `PAUSED`, the position advances and the HLS segments keep loading). The audio-focus and early-pause halves are still hypotheses.
- **Impact:** wrong. `resetInternalState()` sets `playWhenReady` false without an event on every `prepareMediaItem`; the quality switch calls `prepare()` without `play()`, so the player reports paused while mpv plays. Pausing before the first restart doesn't pause mpv. After an audio-focus loss, gain unpauses mpv without restoring `playWhenReady`. `onIsPlayingChanged(false)` is missed when jumping to another item while playing.
- **Where:** upstream, except the quality-switch call site (CE). **Size:** M.

### 7. Duplicate state callbacks and `ENDED` becoming `IDLE` *(hypothesis)*

- **Impact:** cosmetic to wrong. `onPlaybackStateChanged` fires twice per change and once per play or pause; `ENDED` is sent twice, so `NavigateBack` goes out twice (the phone's handler is idempotent, TV untested). After the last item the state silently becomes `IDLE`.
- **Where:** upstream. **Size:** S.

### 8. Whole-second positions *(hypothesis for impact)*

- **Impact:** cosmetic to wrong. `time-pos`, `duration` and `demuxer-cache-time` are observed as `INT64`, so position, duration and buffered position move in 1 s steps. Segment edges can be hit up to 1 s late, the seek bar steps.
- **Fix:** observe as `MPV_FORMAT_DOUBLE` and convert to ms.
- **Where:** upstream. **Size:** S.

### 9. `release()` leaves posted events behind *(hypothesis)*

- **Status:** one back-out 0.3 s after Play on the OPPO did not crash. Not enough to drop the hypothesis: the window is between a posted event and `destroy()`, and one run does not hit it.
- **Impact:** possible crash when the player is released during loading: a `FILE_LOADED` or `PLAYBACK_RESTART` already posted to the main looper runs after `MPVLib.destroy()` and throws `IllegalStateException`.
- **Fix:** `handler.removeCallbacksAndMessages(null)` and `listeners.release()` in `release()`.
- **Where:** upstream. **Size:** S.

### 10. CRLF line endings in `MPVPlayer.kt` and seven other files

- **Status:** confirmed by `git ls-files --eol`.
- **Impact:** not a playback bug, but every upstream change to `MPVPlayer.kt` and `PlayerViewModel.kt` will conflict on every line in `upstream-sync`.
- **Fix:** normalise to LF in one commit, with `.gitattributes` if the repo has none.
- **Where:** CE. **Size:** S.

### 11. Subtitle style preferences don't reach mpv *(hypothesis)*

- **Impact:** wrong. `getCurrentCues()` returns an empty group; mpv renders subtitles itself, so the CE subtitle style settings (font scale, colours) don't apply, and the ASS override toggle in `SubtitleStyle.kt` is still a TODO.
- **Fix:** map the style preferences to `sub-font-size`, `sub-color`, `sub-ass-override` on the mpv side.
- **Where:** CE. **Size:** M.

### 12. mpv is never transcoded

- **Status:** confirmed by reading `JellyfinRepositoryImpl.kt:477`.
- **Impact:** by design, but undocumented: mpv always gets the direct-play profile, so the Dolby Vision direct-play switch and the hardware-probed profile don't apply to mpv, and a bitrate cap is the only way to get a transcode. Confirmed on the emulator: a cap above the file's bitrate returns the same static URL, a cap below it returns an HLS transcode that mpv plays. Belongs in the phase 4 verdict rather than in a fix.

### 13. Both backends: the outro button hides before a slow tap lands *(not an mpv finding)*

- **Status:** seen once on the OPPO with ExoPlayer: the outro button was shown at 60 s and had hidden by the time the automation tapped it a few seconds later; it comes back with the controls overlay (#283). Expected behaviour, noted so nobody reads the stuck-state "Skip outro does nothing" as the same thing. On mpv in the stuck state the button stays and the tap is a no-op (finding 1).
