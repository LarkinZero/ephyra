---
name: fdroid-update
description: >-
  Keep Ephyra's F-Droid inclusion merge request (fdroid/fdroiddata!56048) pointing at the latest
  release: version name, version code and tag commit in the build recipe. Use this right after
  every stable release (the `release` skill calls it as its last step), and whenever the user
  mentions F-Droid, fdroiddata, "the F-Droid MR", or asks whether F-Droid is up to date, how its
  pipeline is doing, or what the reviewers said.
---

# F-Droid merge request

Ephyra is applying for inclusion in F-Droid. Until the merge request is merged, F-Droid's
reviewers want the recipe to carry **only the latest release**, so every `vX.Y.Z` has to be
written into it by hand. This skill does that.

## Background

- **Merge request:** <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56048>, from the
  branch `add-ephyra` of the user's fork (`midas-group7222845/fdroiddata`). The one file in it is
  `metadata/nl.midasvo.findroid.ce.yml`.
- **What changes per release:** `versionName`, `versionCode` and `commit` of the single entry
  under `Builds:`, plus `CurrentVersion` and `CurrentVersionCode`. `commit` is the full hash the
  tag points to (never the tag name; fdroiddata's rules). The code is the phone's
  `EphyraVersion.CODE`; the TV build (`CODE + 1`) is not on F-Droid.
- **Reproducible build:** F-Droid builds the tag itself and compares the result with
  `ephyra-vX.Y.Z-libre-arm64-v8a.apk` from the GitHub release (`Binaries:`), signed with our key
  (`AllowedAPKSigningKeys`). So the release and its APKs must exist before the recipe is updated,
  and a change to the build (Gradle, NDK, signing, ABI splits) can break the match.
- **Tools:** `glab` and `gh`, both logged in (`glab auth status`). The script goes through the
  GitLab API; don't clone fdroiddata, it is huge.
- **Listing text:** F-Droid reads the summary, description, images and changelogs from
  `fastlane/metadata/android/` at the built commit, not from the merge request.

## Procedure

### 1. Update the recipe

After the release is published and `release.yml` has attached the APKs:

```bash
python3 .claude/skills/fdroid-update/scripts/update-mr.py --dry-run   # shows the diff
python3 .claude/skills/fdroid-update/scripts/update-mr.py             # one commit on add-ephyra
```

Without an argument it takes the version from `EphyraVersion.kt`; pass `X.Y.Z` to pick another
release. It is safe to run twice: when the recipe already matches it says so and pushes nothing.

It stops by itself when:

- the merge request is **merged**: see "After the merge" below;
- the merge request is **closed**: don't reopen or recreate it, ask the user;
- the release or its arm64 APK doesn't exist yet: wait for `release.yml`, then run it again;
- the file no longer has exactly one build entry: someone (probably a reviewer) reshaped it. Read
  the file and the merge request's comments, and edit by hand through `glab api` rather than
  forcing the script.

### 2. Check the pipeline and the reviewers

The push starts a pipeline in the fork that lints the recipe and builds the app; it takes a
while (half an hour or more), so don't sit and wait for it at the end of a release.

```bash
python3 .claude/skills/fdroid-update/scripts/update-mr.py --status
glab mr view 56048 --repo fdroid/fdroiddata --comments
```

If a job failed, read its log (`glab ci view` / `glab api projects/<fork id>/jobs/<id>/trace`).
A lint failure is fixed in the recipe. A build or reproducibility failure is usually fixed in
this repo and needs a **new patch release**; the tag never moves.

Reviewer comments are for the user: summarise them and propose a change, but never reply on the
merge request, resolve a thread, or edit its description without the user's say-so. The same
goes for any edit to the recipe beyond the five version fields.

### 3. Report

Tell the user which version the merge request is at now, the pipeline's state with its link, and
any open reviewer comment.

## After the merge

The recipe has `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`: once merged, F-Droid's
checkupdates bot finds new `vX.Y.Z` tags, reads `CODE` and `NAME` from `EphyraVersion.kt` and adds
the builds itself. From then on this skill has nothing to update: the script says so. Tell the
user, and suggest removing the call from the `release` skill.

## Hard rules

- Only stable releases (`vX.Y.Z`). Betas never go to F-Droid.
- Never force-push or rebase `add-ephyra` unless a reviewer asks for it or it conflicts with
  `master`; add commits.
- The file must keep LF line endings and stay formatted as `fdroid rewritemeta` leaves it (lines
  folded at 80 characters). The script changes values in place and keeps both.
- Don't put a summary, description or changelog in the recipe; those live in `fastlane/` here.
