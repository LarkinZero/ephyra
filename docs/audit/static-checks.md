# Audit 1/10: static checks

Sub-issue #351 of the quality audit (#361). Report only: nothing in the code was changed.

Build under test: `main` at `bf205893` (after #362 and #364), `MIN_SDK = 29`, `TARGET_SDK = 37`, `COMPILE_SDK = 37`. Run on Windows 11, JDK 21, Gradle 9.9.0, with a warm daemon shared with another session.

## Summary

- **Lint** ran on all nine Android modules (`lintLibreDebug` for `app:phone`, `app:tv` and `core`, `lintDebug` for the rest; `baselineprofile` is a test module and has no lint task). 179 findings: **65 errors**, 112 warnings, 2 hints. Four tasks fail on errors: `app:phone` (23), `core` (36), `app:tv` (4), `player:local` (2).
- **NewApi: 0. InlinedApi: 0.** No call above API 29 is left unguarded. The #350 call (`KeyEvent.isMediaSessionKey`, API 31) was removed in #362; it is the kind of call `NewApi` reports as an error. Lint was not re-run on the commit before the fix, so "lint would have caught #350" is an assumption, though a safe one.
- **Unit tests:** 513 tests in 7 modules, **0 failures, 0 skipped, 0 ignored**. `app:phone`, `player:core` and `baselineprofile` have no unit tests. No module has instrumented tests.
- **SDK_INT review:** 29 checks read (plus one plain read of `SDK_INT` for a report). None has a wrong boundary or a wrong else-branch on API 29 to 37. 5 are dead code given min SDK 29. No `@RequiresApi`, `@TargetApi` or `@ChecksSdkIntAtLeast` exists in the source.
- **Manifests:** the permissions match API 29 to 37 (POST_NOTIFICATIONS, FGS types and their permissions, ACCESS_LOCAL_NETWORK). There are some unneeded permissions and one exported receiver with no permission, listed below. None is a bug.
- **Real bugs found: 1 verified by reading the code, 1 suspected** that needs a device (see "Proposed issues"):
  1. The still-watching countdown on phone starts a new loop on every `onStart`, so after the app comes back it restarts from the full count and runs twice.
  2. Suspected: `DownloadPumpService` is `START_STICKY` and calls `startForeground` in `onCreate`. When the system restarts it in the background on Android 12+, that call may throw `ForegroundServiceStartNotAllowedException`, and nothing catches it there.
- **CI answer: unit tests yes, lint no (not as it is).** Lint aborts on 65 existing errors, because `abortOnError` is at its default and there is no baseline. Lint took 3 min 23 s on this machine (warm daemon, some compile outputs cached). The unit tests took 33 s, or 20 s for the test tasks alone when forced to re-run.

## 1. Lint

Command: `./gradlew lintLibreDebug lintDebug --continue`. Exit 1, `BUILD FAILED in 3m 21s` (203 s wall clock). Reports: `<module>/build/reports/lint-results-{debug,libreDebug}.{xml,html,txt}`.

### Lint configuration

- `lint.xml` (root): `MissingTranslation` set to ignore.
- `app/phone/lint.xml`, `data/lint.xml`, `player/core/lint.xml` and `player/local/lint.xml` each ignore `UnsafeOptInUsageError` for `@OptIn(markerClass = UnstableApi::class)`. `core` and `app/tv` have no such file, which is why their media3 opt-in usages show up as errors (16 of the 65).
- `player/core/lint.xml` itself triggers `UnknownIssueId` 3 times (`Unknown issue id "UnsafeOptInUsageError"`): the module has no media3 lint checks on its classpath.
- No `lint {}` block in any `build.gradle.kts` or in `buildSrc`. No baseline file. `abortOnError`, `warningsAsErrors` and `checkDependencies` are at their defaults (true, false, false). Each library is linted on its own.
- No CI workflow runs lint. `.github/workflows/build.yaml` runs the translation check and `./gradlew assembleDebug`, and `format.yaml` runs ktfmt.

### NewApi and InlinedApi against min SDK 29

**None.** Lint reported no `NewApi` and no `InlinedApi` finding in any module.

Only one suppression exists: `@SuppressLint("InlinedApi")` on `settings/.../domain/models/PermissionDefinitions.kt:9`. It covers `Manifest.permission.ACCESS_LOCAL_NETWORK`, a string constant that the compiler inlines. Every use of it is gated by `minSdk = Build.VERSION_CODES.CINNAMON_BUN` (`SettingsViewModel.kt:1195` and `:1274`, `PermissionViewModel.kt:59`; the `&&` short-circuits before `isPermissionGranted` runs). Verdict: guarded. I assumed CINNAMON_BUN is API 37, as #351 says, and did not check the value in `android.jar`.

`ObsoleteSdkInt` (the checks that are dead because of min SDK 29) is covered in section 3.

### All findings by module

| Module | Errors | Warnings | Hints | Findings (ID × count) |
|---|---|---|---|---|
| app:phone | 23 | 25 | 0 | E: LocalContextGetResourceValueCall ×21, GestureBackNavigation ×1, RepeatOnLifecycleWrongUsage ×1. W: UnusedResources ×8, LocalContextResourcesRead ×7, UnusedAttribute ×2, PrivateResource ×2, Overdraw ×2, DefaultLocale ×1, NewerVersionAvailable ×1, ModifierParameter ×1, MergeRootFrame ×1 |
| app:tv | 4 | 7 | 2 | E: RestrictedApi ×2, RememberInComposition ×1, UnsafeOptInUsageError ×1. W: ConfigurationScreenWidthHeight ×2, ModifierParameter ×2, LocalContextResourcesRead ×1, UseOfNonLambdaOffsetOverload ×1, UseKtx ×1. H: AutoboxingStateCreation ×2 |
| core | 36 | 49 | 0 | E: UnsafeOptInUsageError ×15, MissingQuantity ×15, MissingPermission ×6. W: MissingQuantity ×24, PluralsCandidate ×8, PrivateResource ×6, ObsoleteSdkInt ×5, Typos ×2, UseKtx ×2, EmptySuperCall ×1, VectorRaster ×1 |
| data | 0 | 0 | 0 | none |
| modes:film | 0 | 2 | 0 | W: EmptySuperCall ×2 |
| player:core | 0 | 3 | 0 | W: UnknownIssueId ×3 |
| player:local | 2 | 9 | 0 | E: MissingQuantity ×2. W: MissingQuantity ×8, EmptySuperCall ×1 |
| settings | 0 | 15 | 0 | W: Typos ×14, UseKtx ×1 |
| setup | 0 | 2 | 0 | W: TypographyDashes ×2 |
| **Total** | **65** | **112** | **2** | |

### Every error, with a verdict from reading the code

| ID (count) | Where | Lint's message | Verdict |
|---|---|---|---|
| RepeatOnLifecycleWrongUsage (1) | `app/phone/.../presentation/player/StillWatchingDialogFragment.kt:61` | "Wrong usage of repeatOnLifecycle from StillWatchingDialogFragment.onStart." | **Real bug** (verified by reading, not reproduced). Each `onStart` launches another `repeatOnLifecycle(STARTED)` loop, and every earlier loop restarts its block on each STARTED as well. After a stop and start, the message counts down from the full timeout again, while the ViewModel's own timer (the one that actually pauses) keeps running, and two or more loops write the message. See Proposed issue 1. |
| GestureBackNavigation (1) | `app/phone/.../PlayerActivity.kt:957` | "If intercepting back events, this should be handled through the registration of callbacks" | False positive. Line 957 is `KeyEvent.KEYCODE_BACK` in the `isSystemKey` list, which hands Back to the system (`return false` at :859). Back itself goes through `onBackPressedDispatcher` (:497, :855). |
| LocalContextGetResourceValueCall (21) | `app/phone`: `AboutScreen.kt:99,107`, `DownloadedShowScreen.kt:103`, `DownloadsScreen.kt:202,209`, `EpisodeScreen.kt:122`, `ItemButtonsBar.kt:263,265,266`, `MovieScreen.kt:122`, `SeasonScreen.kt:112,114,120,126,223`, `SeasonWideLayout.kt:227,648`, `ShowScreen.kt:113,115,121,127` | "Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes" | Not a bug today. `MainActivity` declares no `configChanges`, so a configuration change recreates it and the values are read again. This would become a bug only if `MainActivity` started handling configuration changes itself. |
| RememberInComposition (1) | `app/tv/.../presentation/setup/users/UsersScreen.kt:153` | "Calling a @RememberInComposition annotated declaration inside composition without using `remember`" | False positive in effect. `FocusRequester()` is created in `userRequesters.getOrPut(...)`, and `userRequesters` is `remember { mutableMapOf() }` (:92), so each user gets one instance. |
| RestrictedApi (2) | `app/tv/.../ce/watchnext/WatchNextPublisher.kt:100` | "BasePreviewProgram.getLastPlaybackPositionMillis can only be called from within the same library (androidx.tvprovider:tvprovider)" | Not a runtime bug. The getter is public in bytecode but `@RestrictTo`, so a tvprovider update could break it. It sits in a `try` that returns null. |
| UnsafeOptInUsageError (16) | `core/.../utils/SubtitleStyle.kt:25,50,55,68–73,95–98,103,104`; `app/tv/.../ui/PlayerScreen.kt:196` | "This declaration is opt-in and its usage should be marked with `@androidx.media3.common.util.UnstableApi` …" | Not a runtime bug. These are media3 `@UnstableApi` usages, which the other modules' `lint.xml` already ignores. Configuration gap only. |
| MissingPermission (6) | `core/.../utils/NetworkConnectivityImpl.kt:20,22,29,31,38,40` | "Missing permissions required by ConnectivityManager.getActiveNetwork: android.permission.ACCESS_NETWORK_STATE" (and `getNetworkCapabilities`) | Not a runtime bug. Both merged app manifests contain `ACCESS_NETWORK_STATE`. The manifest-merger report says media3-exoplayer/media3-common add it, and WorkManager also needs it. The app depends on a transitive permission for its own offline detection. Hardening: declare it in `core` or in the app manifests. |
| MissingQuantity (17) | `core/src/main/res/values-{az,tr}/strings_ce.xml:66,69,72` (`one`), `values-iw/strings_ce.xml:66,70,74` (`two`), `values-{cs-rCZ,sk}/strings_ce.xml:66,71,76` (`many`); `player/local/src/main/res/values-az/strings_ce.xml:18,24` (`one`) | e.g. "For locale "az" (Azerbaijani) the following quantity should also be defined: `one`" | Not user-visible for these plurals (`downloaded_episode_count`, `downloads_in_progress_title`, `downloads_removed`, `sleep_timer_minutes`, `player_up_next_starts_in`), which all take an integer `%d`. Czech and Slovak `many` covers fractions only. Hebrew without `two` falls back to `other`, which is grammatical. Turkish and Azerbaijani do not inflect a noun after a numeral, so `other` reads correctly for 1. The CE translation checker (`check-translations.py`, `PLURAL_REQUIRED_QUANTITIES`) chose these rules on purpose. The grammar is my reading and was not checked with native speakers. |

### Warnings worth a note (none is a bug)

- `DefaultLocale`, `app/phone/.../utils/PlayerGestureHelper.kt:533`: "Implicitly using the default locale…". The seek timestamp uses `String.format("%s%02d:%02d:%02d", …)`, so a locale with native digits shows them. Cosmetic.
- `UnusedAttribute`, `app/phone/src/main/AndroidManifest.xml:20,25`: `enableOnBackInvokedCallback` and `localeConfig` "only used in API level 33 and higher". Expected; ignored below 33.
- `ConfigurationScreenWidthHeight`, `app/tv/.../screensaver/ScreensaverScreen.kt:74,75`: the screensaver uses `Configuration.screenWidthDp/HeightDp`. Fine on a full-screen TV.
- `PrivateResource` (8): `exo_player_control_view`, `exo_player_view` and six `exo_styled_controls_*` drawables override media3-ui private resources. A media3 update can break them without warning.
- `UnusedResources` (8, app:phone), `PluralsCandidate` (8, core, upstream strings), `Typos` (16), `EmptySuperCall` (4), `UseKtx` (4), `NewerVersionAvailable` (ktfmt plugin 0.26.0 is pinned on purpose, see `gradle/libs.versions.toml:34`).

## 2. Unit tests

Command: `./gradlew testDebugUnitTest testLibreDebugUnitTest --continue`. Exit 0, 33 s (`BUILD SUCCESSFUL`). Several test tasks came from the build cache, so I forced each test task to run again (`:<module>:test…UnitTest --rerun`): exit 0, 20 s.

| Module | Test classes | Tests | Failures | Skipped |
|---|---|---|---|---|
| app:phone | 0 | 0 (`NO-SOURCE`, no `src/test`) | – | – |
| app:tv | 14 | 105 | 0 | 0 |
| core | 11 | 97 | 0 | 0 |
| data | 14 | 99 | 0 | 0 |
| modes:film | 13 | 72 | 0 | 0 |
| player:core | 0 | 0 (`NO-SOURCE`) | – | – |
| player:local | 16 | 120 | 0 | 0 |
| settings | 1 | 7 | 0 | 0 |
| setup | 1 | 13 | 0 | 0 |
| baselineprofile | n/a (macrobenchmark test module, needs a device) | | | |
| **Total** | **70** | **513** | **0** | **0** |

No `@Ignore`, `@Disabled` or `Assume.*` in any test source. No `src/androidTest` directory exists anywhere.

## 3. SDK_INT review

All 29 checks in CE and upstream source (searched for `Build.VERSION.SDK_INT`, `VERSION_CODES`, `@RequiresApi`, `@ChecksSdkIntAtLeast`, `@TargetApi`, `@SuppressLint("NewApi"/"InlinedApi")`; build directories excluded). There is no `@RequiresApi`, `@TargetApi` or `@ChecksSdkIntAtLeast` at all, so no unguarded `@RequiresApi` callers can exist. `app/tv` has no version check.

| File:line | Check | If true / else | Verdict on 29–37 |
|---|---|---|---|
| `app/phone/.../BaseApplication.kt:68` | `< S` | Applies the theme preference through `AppCompatDelegate.setDefaultNightMode` / on 31+ nothing, because the system keeps the `UiModeManager.setApplicationNightMode` value | Right. 29–30 use AppCompat (`MainActivity` is an `AppCompatActivity`), 31+ the system. |
| `app/phone/.../MainActivity.kt:39` | `>= TIRAMISU` | Asks for POST_NOTIFICATIONS / nothing | Right. The permission is runtime from 33 and granted at install before that. |
| `app/phone/.../PlayerActivity.kt:1071` | `< S` | Enters PiP manually in `onUserLeaveHint` / 31+ relies on `setAutoEnterEnabled` (:1140) | Right. |
| `app/phone/.../PlayerActivity.kt:1140` | `>= S` | `setAutoEnterEnabled` (API 31) / skipped | Right. |
| `app/phone/.../utils/PlayerGestureHelper.kt:578` | `>= R` | `getInsetsIgnoringVisibility(systemGestures())` (API 30) / fixed dp exclusion zones | Right. API 29 gets the fallback. |
| `app/phone/.../presentation/theme/Theme.kt:24` | `>= S` | Dynamic colour (API 31) / static scheme | Right. |
| `app/phone/.../presentation/settings/SettingsScreen.kt:93,97,101,105,110` | `>= S` | `UiModeManager.MODE_NIGHT_*` + `setApplicationNightMode` (API 31) / `AppCompatDelegate` modes | Right, and it matches BaseApplication:68. |
| `app/phone/.../settings/components/SettingsAppLanguageCard.kt:37` | `>= TIRAMISU` | Opens `ACTION_APP_LOCALE_SETTINGS` (API 33) / does nothing | Right. The card is also disabled below 33 (`SettingsViewModel.kt:122`). |
| `settings/.../SettingsViewModel.kt:122` | `>= TIRAMISU` | App language preference enabled / disabled | Right. |
| `settings/.../SettingsViewModel.kt:199` | `>= S` | Dynamic colours switch enabled / disabled | Right. |
| `settings/.../SettingsViewModel.kt:1195` | `>= it.minSdk` | Shows the Permissions category / hides it | Right. Only ACCESS_LOCAL_NETWORK (37) is in it, so it is hidden on 29–36. |
| `settings/.../SettingsViewModel.kt:1274` | `>= sdk` | Filters each preference by its `minSdk` | Right. |
| `setup/.../permission/PermissionViewModel.kt:59` | `>= request.permission.minSdk` | Prompts for the permission / skips it | Right, and it short-circuits before `checkSelfPermission`. |
| `core/.../utils/ClipboardUtils.kt:37` | `<= S_V2` | Shows a toast after copying / 33+ shows the system clipboard confirmation | Right boundary (Android 13 added the system confirmation). |
| `core/.../about/AboutInfoReader.kt:40` | `>= R` | `getInstallSourceInfo` (API 30) / deprecated `getInstallerPackageName` | Right. Both are in a `try`. |
| `core/.../downloader/DownloadQueue.kt:776` | `>= O` | Creates the failure channel | **Dead**: always true (`ObsoleteSdkInt`). |
| `core/.../downloader/DownloadPumpService.kt:81` | `>= Q` | `startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` / untyped call | **Dead** else-branch (Q = 29). The typed call is the one 34+ requires, so it is right. |
| `core/.../downloader/DownloadPumpService.kt:126` | `< O` return | | **Dead**. |
| `core/.../downloader/DownloadPumpService.kt:141` | `>= N` | `stopForeground(STOP_FOREGROUND_REMOVE)` | **Dead** else-branch. |
| `core/.../downloader/DownloadPumpService.kt:169` | `>= O` | `startForegroundService` | **Dead** else-branch. |
| `data/.../player/DeviceCapabilityReport.kt:138` | value only | `sdkInt` in the report | n/a |
| `data/.../player/DeviceCapabilityReport.kt:197` | `>= R` | `Context.display` (API 30) / deprecated `WindowManager.defaultDisplay` | Right. |
| `player/local/.../mpv/MPVPlayer.kt:1886` | `>= R` | Surface frame-rate hint / no hint on 29 (no API exists) | Right. |
| `player/local/.../mpv/MPVPlayer.kt:1888` | `>= S` | `setFrameRate` with change strategy (API 31) / two-argument `setFrameRate` (API 30) | Right. |
| `player/local/.../domain/Extensions.kt:15` | `>= BAKLAVA` | `Locale.of` (API 36) / deprecated `Locale(String)` | Right boundary, same behaviour on both sides. Aside, unrelated to the API level: `it.split("-").last()` takes the *region* of a tag such as `pt-BR`, so the label would read "BR" as a language. Unverified against real Jellyfin data, which mostly sends ISO 639-2 codes such as `eng`. |
| `player/local/.../domain/TrickplayRegionLoader.kt:148` | `>= S` | `BitmapRegionDecoder.newInstance(bytes, off, len)` (API 31) / deprecated 4-argument form | Right. |

### Uses that lint cannot see

- **Reflection and class names:** no `Class.forName`, `getDeclaredMethod`, `getMethod` or `getDeclaredField` in the source. One class is named by a string: `Intent().setClassName(packageName, "dev.jdtech.jellyfin.PlayerActivity")` at `player/local/.../PlayerViewModel.kt:516`. It is explicit, and the class exists in `app/phone` only, but it is used only when background audio is on, a setting offered on phone only (`PlayerViewModel.kt:129`).
- **Java APIs above API 29:** `Locale.of` is guarded (above). `removeLast` in `DownloadsViewModel.kt:492` is on `kotlin.collections.ArrayDeque`, not `java.util.List.removeLast` (API 35), so it is safe.
- **Edge-to-edge (enforced at target 35+):** phone `MainActivity` calls `enableEdgeToEdge()` (:45) and `BasePlayerActivity` calls `setDecorFitsSystemWindows(false)` (:28). TV `MainActivity` does neither, which does not matter on a TV without visible system bars. `android:statusBarColor`/`navigationBarColor` in `core/src/main/res/values/themes.xml:4,5,37,50` are ignored on 35+. They are harmless.
- **Predictive back (default on at target 36+):** phone sets `enableOnBackInvokedCallback="true"`, TV gets the default. No `onBackPressed()` override anywhere. Back goes through `OnBackPressedDispatcher` and `BackHandler`. Fine.
- **Exported dynamic receivers (target 34+):** both dynamic receivers are not exported: `ThemeMusic.kt:71` (`ContextCompat.RECEIVER_NOT_EXPORTED`) and `MPVPlayer.kt:661` (`Util.registerReceiverNotExported`).
- **Foreground service types (34+):** `DownloadPumpService` is `dataSync` with `FOREGROUND_SERVICE_DATA_SYNC`, and `PlaybackService` is `mediaPlayback` with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Both are declared. The 15+ dataSync time limit is handled in `onTimeout` (`DownloadPumpService.kt:148`).
- **FGS start from the background (31+):** `DownloadPumpService.start` (`:166`) wraps `startForegroundService` in `try`/`catch`, so a background start (for example `restoreAll()` from `BaseApplication.kt:116` when WorkManager starts the process) is logged and not fatal. On 29–30 the same start succeeds, so behaviour differs by version: 29–30 show a download notification after a background start, 31+ pump without one. `onCreate` → `startForeground` (`:46`) has no `try`, which matters for a `START_STICKY` restart (`:52`). See Proposed issue 2.
- **Exact alarms:** none used.
- **Local network permission (37):** declared on both apps and requested at runtime only on 37+ (see above).
- **Media playback service:** `PlaybackService` is started with `startService` from `PlayerViewModel.kt:521` while the player activity is in the foreground, and media3's `MediaSessionService` promotes itself. Not started from boot or the background.
- **PendingIntent mutability:** both `PendingIntent.getActivity` calls (`DownloadPumpService.kt:102`, `PlayerViewModel.kt:512`) use `FLAG_IMMUTABLE`.

## 4. Manifests

Source manifests: `app/phone/src/main/AndroidManifest.xml`, `app/tv/src/main/AndroidManifest.xml`, and `baselineprofile/src/main/AndroidManifest.xml` (empty). No library module has a manifest of its own. Merged debug manifests read from `app/{phone,tv}/build/intermediates/merged_manifests/libreDebug/processLibreDebugManifest/arm64-v8a/AndroidManifest.xml` (minSdk 29, targetSdk 37). The phone merger report was written by a build in another worktree (its paths point there), but the merged entries match this source.

### Permissions

| Permission | Phone | TV | From | Verdict on 29–37 |
|---|---|---|---|---|
| INTERNET | yes | yes | app | Fine. |
| POST_NOTIFICATIONS | yes | yes | app | Phone: runtime request on 33+ (`MainActivity.kt:39`); before 33 granted. TV: declared but never requested, and TV posts no notification of its own (no download pump, no `PlaybackService`). **Unneeded on TV.** |
| DOWNLOAD_WITHOUT_NOTIFICATION | yes | – | app | **Unneeded.** It was for `android.app.DownloadManager`, which the app no longer uses (`DownloadStatus.kt:5` says the migration is done). No effect, but it is a leftover. |
| FOREGROUND_SERVICE | yes | yes (merged) | app; WorkManager on TV | Fine. |
| FOREGROUND_SERVICE_DATA_SYNC | yes | – | app | Required on 34+ for `DownloadPumpService`. Fine. |
| FOREGROUND_SERVICE_MEDIA_PLAYBACK | yes | – | app | Required on 34+ for `PlaybackService`. Fine. |
| ACCESS_LOCAL_NETWORK | yes | yes | app | Unknown to the system and ignored on 29–36, runtime on 37. No `minSdkVersion` needed. Fine. |
| ACCESS_NETWORK_STATE | merged | merged | media3, WorkManager | The app's own `NetworkConnectivityImpl` depends on it (lint `MissingPermission`), but only through the dependencies. |
| WAKE_LOCK, RECEIVE_BOOT_COMPLETED | merged | merged | media3, WorkManager | WorkManager's own needs (reschedule after boot). Fine. |
| READ_/WRITE_EPG_DATA | – | merged | `androidx.tvprovider` | Needed for Watch Next. Fine. |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | merged | merged | AndroidX core | Normal. |

No storage permission is declared and none is needed: downloads go to `getExternalFilesDirs()` (app-specific, `SettingsViewModel.kt:1218`), which needs no permission on 29+. No `requestLegacyExternalStorage`. No `maxSdkVersion` attribute appears anywhere, and none is needed.

### Components with an intent filter, and other exported components

| Component | Exported | Note |
|---|---|---|
| phone `MainActivity` | true | Launcher. Fine. |
| phone `PlaybackService` | false | `MediaSessionService` filter; not exported. Media3 controllers connect in-process. Fine. |
| phone `DownloadPumpService` | false | Fine. |
| phone `PlayerActivity` | default (false, no filter) | Fine. |
| TV `MainActivity` | true | Leanback launcher. Fine. |
| TV `WatchNextRemovedReceiver` | true, **no permission** | Must be exported to get `WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED` from the launcher. Any app on the device can send it with a program ID, and the item is then stored as dismissed (`WatchNextRemovedReceiver.kt:24`). Low-impact hardening, not a bug. |
| TV `ScreensaverService` | true | Protected by `BIND_DREAM_SERVICE`. Fine. |
| TV `ScreensaverSettingsActivity` | true | `MAIN` filter for the system screensaver settings. Fine. |
| `androidx.media3.session.BluetoothValidationActivity` | true | Added by media3-session (both apps; TV does not use a media session). Harmless. |
| WorkManager `SystemJobService` (BIND_JOB_SERVICE), `DiagnosticsReceiver` (DUMP), `ProfileInstallReceiver` (DUMP) | true | Library defaults, permission-protected. |
| `androidx.compose.ui.tooling.PreviewActivity` (both), `androidx.activity.ComponentActivity` (TV) | true | From `debugImplementation` of `ui-tooling` and `ui-test-manifest` (`app/tv/build.gradle.kts:209,214`, `app/phone/build.gradle.kts:181`). Debug only, not in release. |

## 5. Stale comments

- `core/src/main/java/dev/jdtech/jellyfin/utils/NetworkConnectivity.kt:20`: "Requires API 28+ which matches minSdk." `minSdk` is 29.
- The five dead `SDK_INT` checks in section 3 (`DownloadPumpService.kt:81,126,141,169`, `DownloadQueue.kt:776`) are stale code rather than stale comments.
- "Fix for hiding the system bars on API < 30" (`ChapterListDialogFragment.kt:64`, `QualitySelectionDialogFragment.kt:41`, `SleepTimerDialogFragment.kt:37`, `SpeedSelectionDialogFragment.kt:38`, `StillWatchingDialogFragment.kt:74`, `TrackSelectionDialogFragment.kt:53`) is still accurate: API 29 is still supported.
- `player/core/lint.xml` is stale configuration: the issue ID it names does not exist in that module (`UnknownIssueId`).

## 6. Can lint run in CI as it is today?

**No.** What stands in the way:

1. **65 existing errors fail four lint tasks** (`app:phone` 23, `core` 36, `app:tv` 4, `player:local` 2), and `abortOnError` is at its default `true`. By the verdicts above, none of the 65 is a crash. One (RepeatOnLifecycleWrongUsage) is a real bug.
2. **No baseline.** The quickest way to a useful gate is `lint { baseline = file("lint-baseline.xml") }` per module, so that only new findings fail. `NewApi` stays an error and is the check that matters for #350-type crashes. Configuration alone would also remove 33 of the 65: the `UnstableApi` ignore in `core/lint.xml` and `app/tv/lint.xml` (16), and `MissingQuantity` set to warning or ignore to follow the CE translation checker's rules (17).
3. **Runtime:** 3 min 23 s on this machine with a warm daemon and some compile outputs cached. A cold CI runner also compiles all variants first. I did not measure that, but expect it to add several minutes to the current `assembleDebug` job. Running lint in the same job, after `assembleDebug`, reuses the compiled classes.

**Unit tests: yes, today.** 513 tests pass with nothing skipped, in 33 s here (20 s for the test tasks alone). `./gradlew testDebugUnitTest testLibreDebugUnitTest` can be added to `build.yaml` as it is.

## Proposed issues

Not filed. For the audit lead to file after review.

1. **`fix(player): the still-watching countdown restarts and runs twice after the app returns`**
   - Affected: phone and tablet, Android 10 to 17 (API 29–37). TV uses its own player screens.
   - Reasoning (verified by reading, not reproduced): `StillWatchingDialogFragment.onStart` (`app/phone/src/main/java/dev/jdtech/jellyfin/presentation/player/StillWatchingDialogFragment.kt:60-61`) launches `lifecycleScope.launch { repeatOnLifecycle(STARTED) { for (remaining in totalSeconds downTo 1) … } }` on every start. After the prompt shows, leaving the app and coming back (Home, lock screen, PiP, recents) starts a second loop, and the first loop's block restarts too. The message counts down from the full timeout again and two loops write it, while the ViewModel's timer that actually pauses playback is not reset. The prompt can pause earlier than it says. Lint: `RepeatOnLifecycleWrongUsage`.
   - Steps to try: enable the still-watching prompt with a short timeout, let it appear, press Home, return after a few seconds, and compare the shown seconds with when playback actually pauses.

2. **`fix(downloads): the download service may crash when Android restarts it in the background`** (suspected, verify on a device first, for example in #357)
   - Affected: phone and tablet, Android 12 to 17 (API 31–37). Not TV, which does not declare the service.
   - Reasoning (assumption, not verified): `DownloadPumpService` returns `START_STICKY` (`core/src/main/java/dev/jdtech/jellyfin/core/presentation/downloader/DownloadPumpService.kt:52`) and calls `startForeground(…, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` in `onCreate` (`:46` → `:82`) without a `try`. If the process is killed while downloads run, the system restarts a sticky service in the background, and on Android 12+ `startForeground` from the background throws `ForegroundServiceStartNotAllowedException`. The explicit `start()` path (`:166-176`) catches this exception; the restart path does not.
   - Steps to try: start a long download, kill the process with `adb shell am kill` (or let low memory kill it), keep the app in the background, and watch logcat for the sticky restart and a `ForegroundServiceStartNotAllowedException`.

Hygiene items, not bugs. One issue or none, at the audit lead's discretion: declare `ACCESS_NETWORK_STATE` in the app manifests (`MissingPermission`); drop `DOWNLOAD_WITHOUT_NOTIFICATION` from phone and `POST_NOTIFICATIONS` from TV; add the `UnstableApi` lint ignore to `core` and `app/tv`; remove the five dead `SDK_INT` checks; fix the `NetworkConnectivity.kt:20` comment; consider a permission or a sender check on `WatchNextRemovedReceiver`.
