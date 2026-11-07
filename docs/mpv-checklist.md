# mpv checklist for player PRs

Run this on every PR that touches `player/local`, `PlayerViewModel`, the phone or TV player screens, or the device profile. It exists because #267 passed every ExoPlayer test and left the mpv backend in `BUFFERING` forever (#284). Ten minutes on one device; the backend is switched under Settings → Player, or with the app force-stopped through `pref_player_backend` in the debug app's shared prefs.

Same item, both backends, and write down what differed. A shared failure is not an mpv bug; a difference is.

### Every PR

- [ ] **Resume.** Play an item from Continue Watching. No spinner after the first frame, the quality, speed, audio and subtitle buttons enabled, trickplay on the seek bar, Next enabled on an episode. Log: `Changed player state to ExoPlayer.STATE_READY` within a few seconds of `file-loaded`.
- [ ] **From 0.** Play an episode from its page. Same checks.
- [ ] **Seek.** Seek bar, the 5 s and 15 s buttons, a double tap. Position lands where it was dropped, the spinner goes away, no second seek on the way back.
- [ ] **Media session.** `adb shell dumpsys media_session` while playing: state `PLAYING`, a position, the right title.
- [ ] **Back out.** During loading and while playing. No crash in `adb logcat -b events | grep am_crash`, a `reportStopped` with the right position.

### When the PR touches the playlist, segments or the end of an item

- [ ] **Next and previous** buttons on an episode, and the outro skip into the next episode (fake segments in a debug build: `files/fake_segments.txt`, see `DebugSegments.kt`).
- [ ] **Autoplay at the end** once, and `NavigateBack` once in the log, not twice.

### When the PR touches loading, the profile or the quality switch

- [ ] **Quality switch** to a cap below the file's bitrate: reloads at the same position, keeps playing, the play/pause button shows Pause, the media session says `PLAYING`.
- [ ] **Slow link** (emulator at 3G): the state settles, no `BUFFERING` ↔ `READY` flapping for mpv.

### What not to do

- Don't fix mpv by touching the ExoPlayer path. Phone playback on ExoPlayer must not change in an mpv PR.
- Don't trust a screenshot over the log: the picture can move while `playbackState` is still `BUFFERING`.
- Don't explain a difference you haven't reproduced; label it as a hypothesis in the PR.
- Say for every fix whether it belongs to CE, to upstream Findroid (`MPVPlayer.kt` as they ship it) or to libmpv.

The reading behind this list is in `docs/mpv-research/`.
