# Track test series

Most libraries have few episodes with more than one audio or subtitle track, and almost none with
untagged tracks. `scripts/make-track-test-series.sh` generates two short series that do, for
testing track selection in the player, such as the per-series audio and subtitle choice (#115),
and a third with styled subtitles for the subtitle style settings (#300).

The files are not checked in: they are binary and the script makes them in a few seconds.

## Generating

The script needs `ffmpeg` on the PATH.

```bash
scripts/make-track-test-series.sh ~/track-test-series
```

Copy both series folders into a TV shows library on a Jellyfin server and scan the library. Keep
the folder and file names, so Jellyfin recognises the episodes. There is no online metadata, so the
series have no poster.

## Contents

Every episode is 20 seconds. Each subtitle track shows one cue naming the episode and the track,
such as "E02 - English SDH", so the active track can be read off the screen. The audio tracks are
tones at different pitches, so check the audio dialog for those.

| Episode | Audio | Subtitles |
|---|---|---|
| Track Test E01, E04 | Nederlands, English, English Commentary | Nederlands, English, English SDH, Signs |
| Track Test E02 | Nederlands, English Commentary, English | Signs, English SDH, English, Nederlands |
| Track Test E03 | Nederlands | Nederlands |
| Other Show E01, E02 | Nederlands, English | Nederlands, English |

- **Signs** has no language tag, so it can only be recognised by its title.
- **E02** has the same tracks as E01 in another order, so a title match has to beat the position.
- **E03** lacks the English and Signs tracks, so a stored choice falls back to the global defaults.
  E04 has them again.
- **Other Show** checks that a choice stays within its own series.

**Subtitle Style Test** has one episode with two subtitle tracks:

- **ASS styled**, the default: two cues in magenta bold italic Noto Serif with a green outline, one
  at the bottom and one moved to the top with `{\an8}`, whose second half is yellow through an inline
  `{\c}` tag.
- **SRT an8**: a cue moved to the top with `{\an8}`, then one at the bottom.

## Checking the per-series choice

1. Play Track Test E01 and pick audio and subtitles by hand.
2. Let it auto-advance. E02 should show the same tracks, E03 the defaults, and E04 the picks again.
3. Turn subtitles off, then start E01 by hand. It should start without subtitles.
4. Play Other Show. It should show the defaults.
5. Repeat with mpv (Settings → Player) and with downloaded episodes.

## Checking the subtitle style override

1. Play Subtitle Style Test with Settings → Player → Subtitle style → Override embedded styles off.
   The ASS track shows in its own colours (ExoPlayer leaves out the font and the inline yellow).
2. Turn the switch on and play it again. The ASS cues show in the app's style, still one at the top
   and one at the bottom. On mpv the inline yellow stays.
3. Pick SRT an8. Its first cue is at the top with the switch on and off.
4. Repeat with mpv. mpv takes the switch while it plays; ExoPlayer on the next playback.
