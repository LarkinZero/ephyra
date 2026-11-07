# mpv research

Research for [issue #284](https://github.com/midasvo/ephyra/issues/284): how healthy is the mpv backend? Research only. It ends in these documents and in follow-up issues, not in code.

| File | Phase | Status |
|---|---|---|
| [0-plan.md](0-plan.md) | Copy of the plan in the issue | done |
| [1-inventory.md](1-inventory.md) | Phase 1: how mpv is wired, summary and the stuck-loader reproduction | done |
| [1a-player-contract.md](1a-player-contract.md) | Phase 1: `MPVPlayer` against the Media3 `Player` contract | done |
| [1b-state-machine.md](1b-state-machine.md) | Phase 1: the state machine and its transitions | done |
| [1c-consumers-and-settings.md](1c-consumers-and-settings.md) | Phase 1: who consumes the player, settings, migrations, upstream | done |
| [2-test-pass.md](2-test-pass.md) | Phase 2: results table, both backends | done for the phone emulator and the OPPO; the box, TV and in-hand rows are [#309](https://github.com/midasvo/ephyra/issues/309) |
| [3-findings.md](3-findings.md) | Phase 3: one entry per difference from ExoPlayer | done: 1, 2, 6 (half), 10, 12 reproduced or confirmed, the rest labelled as hypotheses |
| [4-decisions.md](4-decisions.md) | Phase 4: follow-up issues, defaults, checklist, verdict | done; the checklist is [`docs/mpv-checklist.md`](../mpv-checklist.md) |

Code was read on `main` at `7e21c0de`. Device runs: the phone debug build on the `emulator-5558` AVD (Android 14, x86_64) and on the OPPO CPH2359 (Android 14, arm64). The Xiaomi box and the TV AVD have not run (#309).
