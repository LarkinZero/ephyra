# Phase 4: decisions and follow-ups

### Follow-up issues

One issue per fix, grouped where the fix is the same pass over the code. Every finding in [3-findings.md](3-findings.md) is either in this table or has its reason below.

| Issue | Findings | Where | Size | Status of the evidence |
|---|---|---|---|---|
| [#303](https://github.com/midasvo/ephyra/issues/303) Resume and quality switch never reach `STATE_READY` (stuck loader) | 1 | CE (the `start=` path of #267); the robust version is worth offering upstream | S | reproduced 6/6, two devices |
| [#304](https://github.com/midasvo/ephyra/issues/304) Media session gets no queue and no position | 2 | upstream | S | symptom reproduced, cause hypothesis |
| [#305](https://github.com/midasvo/ephyra/issues/305) State machine drift: `playWhenReady`, duplicate callbacks, `ENDED` → `IDLE`, whole-second positions | 6, 7, 8 | upstream, plus the CE quality-switch call site | M | quality-switch half reproduced, rest hypothesis |
| [#306](https://github.com/midasvo/ephyra/issues/306) Errors never surface, `release()` leaves posted events, stubs reachable from controllers | 3, 4, 5, 9 | upstream | M | hypotheses |
| [#307](https://github.com/midasvo/ephyra/issues/307) Normalise CRLF line endings | 10 | CE | S | confirmed; closed, already done by [#311](https://github.com/midasvo/ephyra/pull/311) |
| [#308](https://github.com/midasvo/ephyra/issues/308) Subtitle style preferences don't reach mpv | 11 | CE | M | hypothesis |
| [#309](https://github.com/midasvo/ephyra/issues/309) Finish the test pass on the box and the TV AVD | the blank rows of [2-test-pass.md](2-test-pass.md) | n/a | | |

Not filed: finding 12 (mpv is never transcoded) is by design and is dealt with in the verdict below; finding 13 is ExoPlayer behaving as #283 intended.

The stuck loader (#303, fix in [#312](https://github.com/midasvo/ephyra/pull/312)) is the one to fix first: it masks the queue, segment and trickplay rows for mpv, so the test pass in #309 is only worth running after it.

### The mpv defaults

Current defaults: `hwdec=mediacodec`, `vo=gpu-next`, `ao=audiotrack`, and hard-coded `cache=yes`, `cache-pause-initial=yes`, `demuxer-max-bytes=64MiB`, `demuxer-max-back-bytes=32MiB`, `profile=fast`, `keep-open=always`.

**Recommendation: keep them.** Nothing in this pass argued against any of them, and the measurements that could (hwdec against `mediacodec-copy` and `no` on the box, `gpu` against `gpu-next`, `audiotrack` against `aaudio` for passthrough) have not been taken: the box rows are blank, and the emulator renders through an OpenGL ES translator, so its frame timing says nothing about a real GPU. Those measurements are part of #309. Two observations are worth keeping in mind when they are taken:

- The cache settings do what they should on a bad link: on the emulated 3G link mpv sat in `BUFFERING` once for 19 s and then played, while ExoPlayer flapped between `BUFFERING` and `READY` five times in ten seconds. Don't trade that away for a faster first frame.
- `keep-open=always` is why the state goes `ENDED` and then quietly `IDLE` after the last item (finding 7); it should stay until the end-of-file handling in #305 and #306 is in, because without it mpv would advance or close on its own.

### Verdict: how far is mpv from a first-class backend?

**Not close yet, and it should stay opt-in.** The picture and sound side was not the problem in this pass; the `Player` contract side was:

- The state machine reaches `READY` by accident. Before #267 every start happened to produce the second restart that sets `READY`; #267 removed the accident for the most common start (a resume) and nothing noticed, because nothing checks mpv. That is a one-line fix (#303), but it says the design has no owner: `READY` should be a decision, not a side effect of a second seek.
- The media session is half-connected (#304), errors never reach the user (#306), and the player reports paused while playing after a quality switch (#305). Each of these is small, and each would be a release blocker on ExoPlayer.
- mpv is never transcoded: it always gets the direct-play profile, so the Dolby Vision switch and the hardware-probed profile don't apply, and only a bitrate cap gets a transcode (finding 12). That is the right default for the formats mpv exists for, but it means "offer mpv automatically for what ExoPlayer can't play" needs a profile decision per item, not a backend switch, and that decision has not been designed.
- The upstream situation makes every fix more expensive than it looks: `MPVPlayer.kt` is CRLF (#307), so the real diff against Findroid is hidden, and every upstream change to it conflicts line by line. Fix that before touching the state machine.

What "first-class" would take, in order: #303 and #307 now; #304, #305 and #306 as one upstream-facing pass over `MPVPlayer.kt` with the `docs/mpv-checklist.md` run on both devices; then the box rows of #309 to settle the defaults and the formats story; and only then a design for offering mpv automatically on formats ExoPlayer can't direct-play. Until #303 lands, mpv on a phone is broken for the most common start, so the settings entry should not move or gain prominence.

### Done when, from the plan

- [x] `docs/mpv-research/` has the inventory, the test-pass table and the findings.
- [x] Every finding has a follow-up issue or a stated reason it won't be fixed (this page).
- [x] The stuck loader is reproduced and has its fix issue (#303).
- [x] `docs/` has the mpv checklist for player PRs (`docs/mpv-checklist.md`).
