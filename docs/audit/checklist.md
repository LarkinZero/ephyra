# Feature checklist

One list of everything Ephyra does, for the quality audit (#361). This file is #352. The other
sub-issues fill in its cells and refer to its row IDs.

**Build under test:** v2.4.0 (`main` at `79d39f81`), which is 2.3.0 plus #346, #347, #348, #349, #362
and #364. Write the commit and build type (debug, staging,
release) you tested in your issue.

**Who fills what:**

| Issue | Fills |
|---|---|
| #353 | The Phone column of every section except Player, on `ce_phone` (API 34) |
| #354 | The Tablet column of every section except Player, at the five sizes of `docs/tablet/6-test-matrix.md` |
| #355 | The TV column of every section except Player, D-pad only, on `ce_tv_1080p` and `ce_tv_4k` |
| #356 | The Player section, all six columns |
| #357 | Its own table of API level (29, 30, 33, 36/37, old TV) against the smoke set at the end of this file, using these row IDs |
| #358 | Upgrade and migration |
| #359 | The rows marked **[device]**, on the OPPO phone and the Xiaomi box |

## Legend

Every cell ends as one of these:

- `works`
- `unpolished`, with a few words on what (for example `unpolished: title cut off at 4K`)
- `broken`, with the issue number (`broken #370`)
- `not tested`, with the reason (`not tested: no HDR10+ file`, `not tested: emulator`)

"Not tested" is a valid result. A guess is not.

`n/a` means the feature cannot apply on that form factor or backend. Each `n/a` was checked in the
code, not guessed: downloads and offline mode do not exist on TV (ADR 0007), gestures and PiP do
not exist in the TV player, D-pad behaviour does not apply on a phone, settings marked
phone-only or TV-only in `SettingsViewModel` (`supportedDeviceTypes`) are hidden on the other
variant. An empty cell is work still to do.

- **Phone** is the phone APK on a phone-sized window (412×915 dp, both orientations).
- **Tablet** is the same APK at the tablet sizes (8", 10.5", foldable, 13"). A row that only
  exists on wide (expanded) windows is `n/a` for Phone.
- **TV** is the TV APK (`app/tv`), driven with the remote.
- **[device]** marks a row an emulator cannot show (display output, refresh rate, passthrough,
  Bluetooth, battery). Emulator runs write `not tested: emulator` and leave it to #359.
- **Source** is where the feature comes from: an issue or PR number, a changelog version, or a file.

---

## 1. Setup and login

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SUP-01 | Fresh install opens the Welcome screen; Continue goes to the permission screen (Android 17+) or Servers. The phone's jellyfish logo swims and reacts to tap and drag | #227, `WelcomeScreen` | | | |
| SUP-02 | Add server: servers on the local network are discovered and listed; picking one connects | `AddServerViewModel` | | | |
| SUP-03 | Add server by hand: a bare host (no scheme, no port) and an address with spaces around it both connect | ce.26, ce.1, `SetupRepositoryImpl` | | | |
| SUP-04 | Add server by hand: an unreachable or non-Jellyfin address shows an error in the screen, no crash | ce.1 | | | |
| SUP-05 | Adding a server that is already saved, through another address, adds the address and does not create a second server | `SetupRepositoryImpl` | | | |
| SUP-06 | Login with username and password lands on Home; the server's login disclaimer, if set, is shown | `LoginViewModel` | | | |
| SUP-07 | Wrong password shows "wrong username or password", not a generic error | ce.1 | | | |
| SUP-08 | Quick Connect: the code shows, approving it on the server logs in; pressing it again cancels. The button is hidden when the server has Quick Connect off | ce.1, `LoginViewModel` | | | |
| SUP-09 | Public users of the server are listed on the Users screen; picking one opens Login with the name filled in | `UsersViewModel` | | | |
| SUP-10 | Login on TV with the on-screen keyboard and the remote only | #355 | n/a | n/a | |
| SUP-11 | The keyboard does not cover the text field; the screen resizes around it | 1.2.0-ce.0 | | | n/a |
| SUP-12 | A second server: add it, switch between the two from the Servers screen, each keeps its own users | `ServersScreen` | | | |
| SUP-13 | Server addresses (long-press a server, Addresses): add an address of the same server, delete one; the address in use cannot be deleted | `ServerAddressesScreen` | | | n/a |
| SUP-14 | Remove a server (long-press): it disappears, and its logins are signed out on the server (dashboard) | #334 | | | |
| SUP-15 | Remove a user (long-press or the remove button): the session ends on the server; removing the current user returns to setup | #336 | | | |
| SUP-16 | The app's session leaves the server's dashboard when the app goes to the background or is closed, and comes back on return | #336, `ServerSessionKeeper` | | | |
| SUP-17 | Revoked token: sign the device out from the dashboard; the app shows a toast and goes to user selection instead of failing silently | #334 | | | |
| SUP-18 | The dashboard lists the client as Ephyra (Ephyra Beta for staging) with the device name | #172 | | | |
| SUP-19 | Killing the app halfway through setup and starting it again opens the right step (Servers or Users) | `NavigationRoot` | | | |
| SUP-20 | Stable and beta installed side by side: logging in on one does not log out the other | #98 | | | |

## 2. Profiles

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| PRF-01 | The current user's face (profile picture, or initials) shows in the chrome: Home header on phone, the drawer on TV | #248 | | | |
| PRF-02 | Default avatar: a user without a picture gets initials on a colour that stays the same for that user | #248, `EphyraAvatar` | | | |
| PRF-03 | Profile sheet (tap the face on Home): current user, server name, the other users, public users, Add user, Settings | #248, `ProfileBottomSheet` | | | n/a |
| PRF-04 | One-tap switch from the profile sheet loads Home with the other user's rows, favourites and progress | #248 | | | n/a |
| PRF-05 | Switch user from the Users screen (Settings → Users; on TV the face in the drawer) | `UsersScreen` | | | |
| PRF-06 | "Ask who is watching" on: with more than one saved user, the user list shows once per app start, not again on rotation | #245, `askUserOnStart` | | | |
| PRF-07 | Set a 4 to 6 digit PIN (Settings → Profile lock / Profile PIN); the lock badge shows on the face | #299 | | | |
| PRF-08 | Change the PIN: the new one works, the old one no longer does | #299, #329 | | | |
| PRF-09 | Remove the PIN: no prompt any more | #299 | | | |
| PRF-10 | Set a pattern of at least 4 dots instead of a PIN; a mismatch on confirm is reported; the pattern replaces the PIN | #330 | | | n/a |
| PRF-11 | Lock at start on: a cold start into the locked profile asks the PIN or pattern before Home | #299, `ProfilePinGateScreen` | | | |
| PRF-12 | Wrong PIN or pattern shakes and clears; "Other profile" on the gate goes to the user list | #299 | | | |
| PRF-13 | Switching to a locked profile asks its PIN or pattern; Cancel stays on the current profile | #299, `UsersViewModel` | | | |
| PRF-14 | An unlocked profile stays unlocked until another profile is picked or the process ends (`am kill` locks it again) | `ProfileLock` | | | |
| PRF-15 | The PIN prompt stays usable with the keyboard up | #329 | | | n/a |
| PRF-16 | TV PIN pad: digits, delete and OK with the D-pad, the remote's number keys type directly, OK needs at least 4 digits | #299, `TvPinPad` | n/a | n/a | |
| PRF-17 | The PIN is stored on the device only: the same user on another device or app is not asked | #299 | | | |

## 3. Home

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| HOM-01 | Suggestions carousel: pages through items, pauses while scrolling, opens the item on tap | #266 | | | n/a |
| HOM-02 | TV hero: the focused card's backdrop, logo or title, info line and overview | 1.2.0-ce.5 (#132) | n/a | n/a | |
| HOM-03 | TV hero title: a long title is fitted, not cut off | #346 | n/a | n/a | |
| HOM-04 | TV hero backdrop for an episode: falls back to the show's, the season's or the episode's picture instead of staying empty | #348 | n/a | n/a | |
| HOM-05 | Suggestions row (TV) | 1.2.0-ce.5 | n/a | n/a | |
| HOM-06 | Continue Watching row: the right items, with progress; opens the item | | | | |
| HOM-07 | Next Up row: the next episode of shows in progress | | | | |
| HOM-08 | Latest row per library | | | | |
| HOM-09 | The order and visibility set in Home rows (SET-08) show on Home, and are kept per server and user | #142 | | | |
| HOM-10 | Home cache on: a cold start shows the rows of the last visit at once, then updates them | #251, #270 | | | |
| HOM-11 | Pull to refresh reloads the rows; the spinner stays until the reload is done | ce.1 | | | n/a |
| HOM-12 | TV rows refresh in the background without reshuffling; focus comes back to the same card | #132 | n/a | n/a | |
| HOM-13 | When one row fails to load, the others still show | 1.2.0-ce.4 (#114) | | | |
| HOM-14 | Home reflects what was just watched after leaving the player (progress, Next Up) | #95 | | | |
| HOM-15 | Empty Home and a failed load show a message with Retry | #95 | | | |
| HOM-16 | Home header: server name opens the server switcher with "Manage servers"; Search opens search | `HomeHeader` | | | n/a |
| HOM-17 | Long-press on a card copies the item's name | ce.33, `ClipboardUtils` | | | n/a |
| HOM-18 | Home does not open partly scrolled when the carousel loads last | #95 | | | n/a |
| HOM-19 | Moving along a TV row: the hero backdrop changes without waiting on the network | #155 | n/a | n/a | |
| HOM-20 | OK on an episode card on TV opens its season with the episode selected | #130 | n/a | n/a | |

## 4. Browsing

Libraries, search, favourites and the item pages.

### Navigation

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| BRW-01 | Bottom bar with Home, Favorites, Media and Downloads; on an expanded window a navigation rail instead | #188, #288 | | | n/a |
| BRW-02 | TV drawer: face, Search, Home, Favorites, one entry per library, Settings; Back goes Home, then opens the drawer, then leaves | ADR 0002, `MainScreen` | n/a | n/a | |
| BRW-03 | TV: switching drawer destinations does not shift the layout or make focus jump | #190 | n/a | n/a | |
| BRW-04 | TV: Back from a page puts focus on the card that opened it (library, show, season) | #124, ADR 0003 | n/a | n/a | |
| BRW-05 | Screen transitions and the predictive back preview animate without a snap at the end | #234, 1.1.0-ce.4 | | | n/a |

### Libraries, collections, search and favourites

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| BRW-06 | Media tab shows the libraries as a grid (TV: the libraries are in the drawer) | `MediaScreen` | | | n/a |
| BRW-07 | Library grid: first-load spinner, keeps loading pages while scrolling a large library | #265 | | | |
| BRW-08 | Sort: each sort option and ascending/descending give the expected order, and the choice is kept | `SortByDialog` | | | |
| BRW-09 | Hide watched filter: hides watched items in every library, is kept, and "Show watched" appears when everything is hidden | ce.33, #95 | | | |
| BRW-10 | TV library: A–Z jump bar when sorted by name, position counter, year under each title, a title card for items without artwork | #135 | n/a | n/a | |
| BRW-11 | Collections (box sets) open and list their items; an episode in a collection opens in its season on TV | #129 | | | |
| BRW-12 | A folder library can be browsed folder by folder | `navigateToItem` | | | |
| BRW-13 | Search: results appear after typing stops, "No results" does not flash while typing, a failed search shows Retry | 1.2.0-ce.4, #95 | | | |
| BRW-14 | TV search with the on-screen keyboard; results grouped as movies, shows, episodes, collections | #128 | n/a | n/a | |
| BRW-15 | Ctrl+F on a hardware keyboard opens search on the Media tab | #310 | | | n/a |
| BRW-16 | Favorites tab lists favourite movies and shows; an empty state when there are none | #188 | | | |
| BRW-17 | Downloaded items show the downloaded badge in search, favourites and the Home rows | ce.1 | | | n/a |

### Item pages

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| BRW-18 | Movie page: Play or Resume, restart from the beginning (phone and tablet; the TV movie page has none), trailer, watched, favourite | `ItemButtonsBar`, TV `MovieScreen` | | | |
| BRW-19 | Version picker: an item with more than one version asks which one to play, for Play and for play from the beginning | #28, #93 | | | |
| BRW-20 | Trailer: one plays or opens; several give a menu; hidden when offline | `ItemButtonsBar` | | | |
| BRW-21 | Show page: seasons, Play, trailer, watched, favourite | `ShowScreen` | | | |
| BRW-22 | Show page Shuffle starts a random episode, specials left out | #141 | n/a | n/a | |
| BRW-23 | Season page: episodes with titles on two lines, progress, a play button on the artwork; TV adds season chips and the selected episode's details | #94, #130 | | | |
| BRW-24 | Episode page with play, restart, watched, favourite and links to its season and show (TV: the episode inside its season page) | `EpisodeScreen`, #130 | | | |
| BRW-25 | Person page lists the person's movies and shows, which open | #136 | | | |
| BRW-26 | Cast and crew row (actors with roles, directors, writers; an actor with several roles is one card) opens the person | #64, #136 | | | |
| BRW-27 | Critic score, tagline, studios or networks | #332 | | | |
| BRW-28 | Air schedule of a running show ("Airs Thu 21:00", "Airs daily") | #332 | | | |
| BRW-29 | Watch count and last played date | #332 | | | |
| BRW-30 | Links to IMDb, TMDB, TVDB and Trakt open in the browser; hidden offline | #332 | | | n/a |
| BRW-31 | Extras row: an extra plays as a single video | #332 | | | |
| BRW-32 | Collections row and "More like this" row open the item | #332 | | | |
| BRW-33 | Media chips: resolution, codec, HDR, Dolby Vision, HDR10+ wordmark, Atmos, 3D, with the right values for the file | #282, #332 | | | |
| BRW-34 | Watched toggle updates the page and the server (check another client) | | | | |
| BRW-35 | Favourite toggle updates the page, the Favorites tab and the server | | | | |
| BRW-36 | Long-press on title, overview, genres, people and similar text copies it | ce.33 (#42) | | | n/a |
| BRW-37 | A page whose load fails (server off mid-load) shows an error with Retry instead of spinning | #95 | | | |
| BRW-38 | Movie, show, season and episode pages show new progress after leaving the player | #95 | | | |
| BRW-39 | Status-bar icons stay readable over the backdrop | #291, #297 | | | n/a |
| BRW-40 | A failing image moves on to the next one; a season without a poster uses the show's | #94 | | | |
| BRW-41 | Dates follow the app language; premiere dates are not a day early in timezones west of UTC | #141, ce.1 | | | |
| BRW-42 | Theme music on the show page (setting on): plays softly, fades out on leaving, on Play and when the screensaver starts, stays quiet when another app plays audio | #141 | n/a | n/a | |
| BRW-43 | The Bioluminescence look: Ephyra palette, Outfit for titles, violet for favourite, watched and rating, pill buttons; TV pages fade to the surface colour and the focus ring is cyan | #224 to #242 | | | |

### Wide windows

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| BRW-44 | Movie, show and episode pages use the hero layout: poster beside the text over a full-width backdrop | #291 | n/a | | n/a |
| BRW-45 | Title and text of the hero sit under the backdrop, readable over bright artwork | #339 | n/a | | n/a |
| BRW-46 | Season as list and detail side by side; rotating keeps the selected episode and the scroll position | #297 | n/a | | n/a |
| BRW-47 | Rows, grids and search use wider cards and more columns; text columns are capped at a readable width | #290 | n/a | | n/a |
| BRW-48 | Tab on a hardware keyboard reaches every control on Home, Media and an item page | #310, test matrix | | | n/a |

## 5. Downloads

Downloads exist on phone and tablet only: the TV app has no download UI and hides every
download setting (ADR 0007, `SettingsViewModel`).

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| DLD-01 | Download a movie: the quality dialog offers Original and bitrates from 4K 120 Mbps to 360p; the file arrives and plays | 1.2.0-ce.0 (#75) | | | n/a |
| DLD-02 | Download one episode from its page or the season list | | | | n/a |
| DLD-03 | Download a season or a whole show: the season dialog shows what is already downloaded per season | `SeasonSelectionDialog` | | | n/a |
| DLD-04 | A partly downloaded season or show offers both download (the rest) and delete | #94 | | | n/a |
| DLD-05 | A lower-quality download shows progress against the converted size, not the original's | #76 | | | n/a |
| DLD-06 | The download goes to the storage location from Settings, without asking; an SD card works too **[device]** | `downloadStorageIndex` | | | n/a |
| DLD-07 | The progress ring on the item page; tapping it stops the download | 1.2.0-ce.1 | | | n/a |
| DLD-08 | The "downloads in progress" notification shows, and downloads carry on with the screen off or the app in the background **[device]** | `DownloadPumpService` | | | n/a |
| DLD-09 | No more downloads run at once than "Maximum concurrent downloads" | `DownloadQueue` | | | n/a |
| DLD-10 | A failed download shows the reason and a retry icon, retries by itself, and keeps its chosen quality | #82, #94 | | | n/a |
| DLD-11 | A download in progress survives `am kill` and an app restart and resumes where it was | ce.33 (#26) | | | n/a |
| DLD-12 | With mobile data off, a download on mobile data waits and says "Waiting for network" **[device]** | 1.2.0-ce.1 | | | n/a |
| DLD-13 | Subtitles are saved as separate files, image-based ones (PGS, VobSub) included, and show when the download plays offline | #60, #75 | | | n/a |
| DLD-14 | Downloads screen: storage summary bar (movies, shows, other, free), red under 10 % free, tap opens the download settings | 1.2.0-ce.1, #246 | | | n/a |
| DLD-15 | Downloading section: one status line per row (size, time left), the header shows count and speed, the estimate settles | #111, 1.2.0-ce.1 | | | n/a |
| DLD-16 | A finished download gets a check mark, a haptic tick, and moves into Movies or TV Shows | 1.2.0-ce.1 | | | n/a |
| DLD-17 | Sort by recently downloaded, name and size | 1.2.0-ce.1 | | | n/a |
| DLD-18 | Multi-select ("Select" or long-press), remove all selected; Back leaves selection | 1.2.0-ce.1 | | | n/a |
| DLD-19 | The play button on a movie or episode's artwork resumes from the downloaded copy | 1.2.0-ce.1 | | | n/a |
| DLD-20 | Downloaded show page: episodes by season with sizes, swipe to remove, "Remove all downloads" | #81, #84 | | | n/a |
| DLD-21 | Cancel or remove on the Downloads screen shows Undo; undoing a cancel loses no progress; the removal finishes if the screen is left | #82, #84, #94 | | | n/a |
| DLD-22 | Delete from an item page asks to confirm; the check mark on an episode no longer deletes on one tap | #94 | | | n/a |
| DLD-23 | Empty Downloads screen explains what goes there and links to the library | 1.2.0-ce.1 | | | n/a |
| DLD-24 | Smart Downloads on: after watching an episode, the set number of next unwatched episodes are downloaded, across seasons | #116, #119 | | | n/a |
| DLD-25 | Smart Downloads removes the episodes it downloaded itself once watched, and leaves the ones downloaded by hand | #116, #119 | | | n/a |
| DLD-26 | A Dolby Vision file is downloaded as a transcode when "Transcode Dolby Vision" is on, and plays offline | ce.30, #60 | | | n/a |
| DLD-27 | Downloads screen in two columns on an expanded window | #288 | n/a | | n/a |

## 6. Offline mode

Phone and tablet only, except OFF-12: TV has no offline mode (ADR 0007).

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| OFF-01 | Offline mode on (Settings): the app restarts, shows only Home and Downloads, and an "Offline" label | `SettingsViewModel`, `NavigationRoot` | | | n/a |
| OFF-02 | Starting the app with no network turns offline mode on by itself, without a crash | ce.27, ce.28 | | | n/a |
| OFF-03 | Turning the network off while the app is open: the app stays usable and says what failed, no crash | #353 | | | n/a |
| OFF-04 | Offline Home and item pages show the downloaded items | | | | n/a |
| OFF-05 | A downloaded item plays offline, and its position is kept locally | | | | n/a |
| OFF-06 | Back online: the position, watched state and favourites changed offline reach the server (at the next app start with network) | `SyncWorker` | | | n/a |
| OFF-07 | Online with the server down or slower than 3 s: a downloaded movie or episode still opens and plays from the local copy | #281 | | | n/a |
| OFF-08 | Offline, the quality button, trailers and external links are hidden | `PlayerActivity`, #332 | | | n/a |
| OFF-09 | Turning offline mode off brings back all tabs and the server's data | | | | n/a |
| OFF-10 | Downloads keep working across a switch between online and offline | ce.29 | | | n/a |
| OFF-11 | A download started online keeps its state when the app goes offline and back | ce.29 | | | n/a |
| OFF-12 | TV started without network: no crash, a message or an empty screen instead of a hang | `RepositoryModule` | n/a | n/a | |

## 7. Settings

One row per preference. A row passes when the setting takes effect and survives an app restart.
Where the effect is in the player, the Player row is named; the settings row checks that the value
is shown, saved and kept.

### General

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-01 | Every setting changed below is still set after `am force-stop` and a new start | | | | |
| SET-02 | TV settings: one list layout on every page, a details pane for selects, numbers changed with steps (+1, −1, +10, −10) | #347, 31b087ae | n/a | n/a | |
| SET-03 | Settings that make no sense on TV (gestures, PiP, downloads, theme) are not shown on TV | #133 | n/a | n/a | |

### Offline and language

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-04 | Offline mode (see OFF-01) | `offlineMode` | | | n/a |
| SET-05 | App language (Android 13+, disabled below): the app switches language. TV renders no row for this setting | `PreferenceAppLanguage` | | | n/a |
| SET-06 | Preferred audio language (player effect: PLY-36) | `preferredAudioLanguage` | | | |
| SET-07 | Preferred subtitle language (player effect: PLY-37) | `preferredSubtitleLanguage` | | | |

### Interface

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-08 | Home rows: show or hide each row and move it up or down; Home follows (HOM-09) | #142 | | | |
| SET-09 | Theme: System, Light, Dark; applies at once | `theme` | | | n/a |
| SET-10 | Dynamic colours (Android 12+, off by default): wallpaper colours replace the Ephyra palette | #225 | | | n/a |
| SET-11 | Theme music on the show page (off by default; effect: BRW-42) | #141 | n/a | n/a | |
| SET-12 | Max days in Next Up: with a limit, older shows leave the Next Up row | #114 | | | |
| SET-13 | Watch Next row on the Android TV home screen (on by default; effect: TVS-01) | #138 | n/a | n/a | |
| SET-14 | Show the Favorites tab: off hides the tab (phone) or drawer entry (TV) | #188 | | | |

### Interface → Detail pages

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-15 | Critic rating on or off | #332 | | | |
| SET-16 | Tagline on or off | #332 | | | |
| SET-17 | Studios and networks on or off | #332 | | | |
| SET-18 | Air schedule on or off | #332 | | | |
| SET-19 | Watch history on or off | #332 | | | |
| SET-20 | External links on or off | #332 | | | n/a |
| SET-21 | Cast and crew on or off | #104, #332 | | | |
| SET-22 | Extra info on or off: detailed audio, video and subtitle information on item pages | `displayExtraInfo` | | | |
| SET-23 | Extras row on or off | #332 | | | |
| SET-24 | Collections row on or off | #332 | | | |
| SET-25 | More like this row on or off | #332 | | | |

### Player → Subtitle style

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-26 | Subtitle style screen with a live preview (phone); a plain list on TV | #24, `SubtitleStyleScreen` | | | |
| SET-27 | Text colour (player effect: PLY-44) | #24 | | | |
| SET-28 | Background colour | #24 | | | |
| SET-29 | Edge type (outline, shadow, none...) | #24 | | | |
| SET-30 | Edge colour | #24 | | | |
| SET-31 | Font | #24 | | | |
| SET-32 | Font size, kept within its limits when a value out of range is entered | #24, ce.1 | | | |
| SET-33 | Override embedded styles (off by default; effect: PLY-45) | #300 | | | |

### Player

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-34 | Player backend: ExoPlayer or mpv; the next playback uses it | `playerBackend` | | | |
| SET-35 | Direct play Dolby Vision (on by default; ExoPlayer only; effect: PLY-116, PLY-118) | #96 | | | |
| SET-36 | Match display refresh rate (TV, off by default; effect: PLY-123) | #139 | n/a | n/a | |
| SET-37 | mpv: hardware decoding | `playerMpvHwdec` | | | |
| SET-38 | mpv: video output | `playerMpvVo` | | | |
| SET-39 | mpv: audio output, `audiotrack` by default | #52, `playerMpvAo` | | | |
| SET-40 | mpv.conf editor: open, edit, save; Back with changes asks to discard. Not rendered on TV | ce.34, `SettingsFileEditScreen` | | | n/a |
| SET-41 | input.conf editor, the same | ce.34 | | | n/a |
| SET-42 | Player gestures (master switch; the gesture rows below depend on it) | `playerGestures` | | | n/a |
| SET-43 | Volume and brightness gestures | `playerGesturesVB` | | | n/a |
| SET-44 | Seek gesture | `playerGesturesSeek` | | | n/a |
| SET-45 | Zoom gesture | `playerGesturesZoom` | | | n/a |
| SET-46 | Long-press action: Chapter, 2× speed or Disabled | #50 | | | n/a |
| SET-47 | Remember brightness (needs the volume and brightness gestures) | `playerGesturesBrightnessRemember` | | | n/a |
| SET-48 | Start maximized | `playerGesturesStartMaximized` | | | n/a |
| SET-49 | Seek back increment (ms) | `playerSeekBackInc` | | | n/a |
| SET-50 | Seek forward increment (ms) | `playerSeekForwardInc` | | | n/a |
| SET-51 | TV seek back step | #126 | n/a | n/a | |
| SET-52 | TV seek forward step | #126 | n/a | n/a | |
| SET-53 | OK pauses (TV) | #126 | n/a | n/a | |
| SET-54 | Chapter markers on the seek bar | `playerChapterMarkers` | | | |
| SET-55 | Media segments: Intro action (Skip, Ask, Ignore) | #271 | | | |
| SET-56 | Media segments: Outro action | #271 | | | |
| SET-57 | Media segments: Recap action | #271 | | | |
| SET-58 | Media segments: Preview action | #271 | | | |
| SET-59 | Media segments: Commercial action | #271 | | | |
| SET-60 | Skip button duration (seconds) | #271 | | | |
| SET-61 | Auto-skip: always or only in picture-in-picture | #271 | | | n/a |
| SET-62 | Next episode threshold (ms) | `playerMediaSegmentsNextEpisodeThreshold` | | | |
| SET-63 | Trickplay | #134 | | | |
| SET-64 | Trickplay while seeking with the gesture (needs Trickplay) | `playerGesturesSeekTrickplay` | | | n/a |
| SET-65 | Play next episode automatically: server setting, always, never | #66 | | | |
| SET-66 | Still watching prompt on or off (off by default) | #349 | | | |
| SET-67 | Still watching: after N episodes (needs the prompt on) | #22 | | | |
| SET-68 | Still watching: after N minutes without input | #22 | | | |
| SET-69 | Still watching: prompt timeout (seconds) | #22 | | | |
| SET-70 | Picture-in-picture gesture (go Home to enter PiP) | `playerPipGesture` | | | n/a |
| SET-71 | Background playback (audio carries on in the background) | #36 | | | n/a |

### Users, servers, downloads, network, cache, permissions

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-72 | Users opens the user list (PRF-05) | | | | |
| SET-73 | Ask who is watching on start (effect: PRF-06) | #245 | | | |
| SET-74 | Profile lock (phone) / Profile PIN (TV) opens the lock settings: set, change, remove, lock at start | #299, #330 | | | |
| SET-75 | Servers opens the server list (SUP-12) | | | | |
| SET-76 | Download over mobile data | `downloadOverMobileData` | | | n/a |
| SET-77 | Download when roaming (needs mobile data on) | `downloadWhenRoaming` | | | n/a |
| SET-78 | Storage location: internal storage and each SD card, with free space | `PreferenceDynamicSelect` | | | n/a |
| SET-79 | Maximum concurrent downloads | `maxConcurrentDownloads` | | | n/a |
| SET-80 | Smart Downloads | #116 | | | n/a |
| SET-81 | Episodes to keep ready (at least 1) | #116 | | | n/a |
| SET-82 | Transcode Dolby Vision downloads | ce.30 | | | n/a |
| SET-83 | Request timeout | `requestTimeout` | | | |
| SET-84 | Connect timeout | `connectTimeout` | | | |
| SET-85 | Socket timeout | `socketTimeout` | | | |
| SET-86 | Image cache on or off | `imageCache` | | | |
| SET-87 | Image cache size (MB; TV defaults to 100) | #155 | | | |
| SET-88 | Load artwork at display size (on by default) | #254, #294 | | | |
| SET-89 | Remember Home rows (Home cache) | #251, #270 | | | |
| SET-90 | Clear cache empties the image caches and the remembered Home rows, with a toast | #270 | | | |
| SET-91 | Permissions section (Android 17+ only): local network permission state and request | 1.2.0-ce.0 | | | |

### About

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| SET-92 | About shows the app version and install, server and device information | #223, #211 | | | |
| SET-93 | Licences list and each licence's text (TV scrolls with the D-pad) | #211, #364 | | | |
| SET-94 | Export device profile: share or copy the JSON | ce.31 (#23) | | | n/a |
| SET-95 | Device profile screen: what the device can decode | #211 | n/a | n/a | |
| SET-96 | Report a problem and source code: links on phone, a QR code on TV | #223 | | | |

## 8. Player

Each row has a cell per form factor and backend. mpv crashes host-GPU emulators: run mpv with
software rendering or on a device, and say which in the cell. Load test media over http, not
`file://`. `docs/mpv-checklist.md` and `docs/track-test-series.md` describe several rows in more
detail.

### Playback

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-01 | Resume from Continue Watching: starts at the saved position, no spinner after the first frame, buttons enabled | mpv-checklist, #303 | | | | | | |
| PLY-02 | Play from 0 from the item page, and restart from the beginning on an item with progress | mpv-checklist | | | | | | |
| PLY-03 | Play and pause; the button shows the right state, including after loading and after an item change | #305 | | | | | | |
| PLY-04 | Seek bar: phone drags and lands where dropped; TV focuses the bar, Left/Right pick a time, OK commits | mpv-checklist, `TvSeekBar` | | | | | | |
| PLY-05 | Skip back and forward buttons use the seek increments | mpv-checklist | | | | | n/a | n/a |
| PLY-06 | Previous and next on an episode; the server sees the previous episode stop (dashboard, Trakt) | #65 | | | | | | |
| PLY-07 | The last episode of a season continues into the next season; specials do not roll into season 1 | #29, #92 | | | | | | |
| PLY-08 | Next or previous to an episode with a saved position: write down where it starts (the code starts queued episodes at 0) | `PlaylistManager` | | | | | | |
| PLY-09 | Play on a show starts the next-up episode, or the first episode | `PlaylistManager` | | | | | | |
| PLY-10 | Shuffle from the TV show page plays random episodes without specials | #141 | n/a | n/a | n/a | n/a | | |
| PLY-11 | Version picker: the chosen version plays | #28 | | | | | | |
| PLY-12 | Speed dialog, 0.5× to 2×; speed applies and audio stays in sync | `SpeedSelectionDialogFragment` | | | | | | |
| PLY-13 | Quality dialog (HD): a cap below the file's bitrate reloads at the same position and keeps playing; Original goes back to direct play; the choice is remembered | #61, mpv-checklist | | | | | n/a | n/a |
| PLY-14 | Slow link (emulator at 3G): the state settles, no buffering and ready flapping | mpv-checklist | | | | | | |
| PLY-15 | Chapters dialog with thumbnails seeks to the chapter; the button only shows when there are chapters | ce.31 (#25) | | | | | | |
| PLY-16 | Chapter markers on the seek bar | ce.31 | | | | | | |
| PLY-17 | Trickplay thumbnails while dragging the seek bar (phone) or seeking with Left/Right (TV); on TV they don't cover "Ends at" | #134 | | | | | | |
| PLY-18 | Extras and local trailers play as a single video | #332, #129 | | | | | | |
| PLY-19 | A downloaded item plays from the local copy | 1.2.0-ce.1 | | | | | n/a | n/a |
| PLY-20 | Back out during loading and while playing: no crash, the stop is reported with the right position | #318, mpv-checklist | | | | | | |
| PLY-21 | Progress reaches the server while playing (dashboard shows the position); after `am force-stop` mid-episode the resume position is close to where it was | `PlayerViewModel` | | | | | | |
| PLY-22 | At stop: past 90 % marks watched, under 10 % resets the position, in between saves it | `JellyfinRepositoryImpl` | | | | | | |
| PLY-23 | Media session: `dumpsys media_session` shows PLAYING, a position and the right title; the queue is there | #304, mpv-checklist | | | | | | |
| PLY-24 | A file the backend cannot play shows an error (phone: message, TV: dialog with a way out) instead of buffering forever | #306, #131 | | | | | | |
| PLY-25 | The screen stays on while playing | | | | | | | |
| PLY-26 | Time to the first frame from pressing Play: write down the seconds | #267, #296 | | | | | | |
| PLY-27 | Title line shows the series, S1:E2 and the episode name | | | | | | | |
| PLY-28 | TV overlay: hides after a few seconds while playing, stays while paused or a dialog is open | #124 | n/a | n/a | n/a | n/a | | |
| PLY-29 | TV clock and "Ends at" (follows the speed) | #124 | n/a | n/a | n/a | n/a | | |
| PLY-30 | TV "Up next" queue row (Down from the buttons) plays the picked episode | #131 | n/a | n/a | n/a | n/a | | |
| PLY-31 | The player closes when playback ends after the last episode, or at the end with autoplay off | 1.2.0-ce.0 | | | | | | |
| PLY-32 | mpv: video does not freeze after a seek, an audio track switch, the next episode, or screen off and on | #52 | n/a | | n/a | | n/a | |

### Tracks and subtitles

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-33 | Audio track dialog lists the tracks and switches | | | | | | | |
| PLY-34 | Subtitle track dialog lists the tracks and None, and switches | | | | | | | |
| PLY-35 | The selected option in player dialogs is tinted with the accent | #244 | | | | | | |
| PLY-36 | Preferred audio language is picked at start | `preferredAudioLanguage` | | | | | | |
| PLY-37 | Preferred subtitle language is picked at start | `preferredSubtitleLanguage` | | | | | | |
| PLY-38 | Per-series track choice carries over on auto-advance (Track Test E01 to E04) | #115, track-test-series | | | | | | |
| PLY-39 | Per-series choice when starting an episode by hand, and "subtitles off" is remembered | #115, #120 | | | | | | |
| PLY-40 | The choice stays within its series (Other Show) and within the user | #120, track-test-series | | | | | | |
| PLY-41 | Per-series choice with downloaded episodes | #120 | | | | | n/a | n/a |
| PLY-42 | External SRT and WebVTT subtitles show at the right time | ce.1 | | | | | | |
| PLY-43 | PGS, VobSub and DVB subtitles play without a transcode | #19 | | | | | | |
| PLY-44 | Subtitle style (colour, edge, background, font, size) applies to text subtitles | #24, #308 | | | | | | |
| PLY-45 | Override embedded styles: off keeps the ASS track's own look, on applies the app's style; `{\an8}` stays at the top | #300, #308 | | | | | | |
| PLY-46 | A subtitle style change while a video is open (for example from PiP) applies: write down whether at once or on the next start | `MpvSubtitleOptions` | | | | | n/a | n/a |
| PLY-47 | A direct stream with burned-in (transcoded) subtitles | #356 | | | | | | |

### Gestures, lock and orientation

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-48 | Single tap shows and hides the controls | `PlayerGestureHelper` | | | | | n/a | n/a |
| PLY-49 | Double tap: left rewinds, middle plays or pauses, right forwards | `PlayerGestureHelper` | | | | | n/a | n/a |
| PLY-50 | Brightness swipe (left) | | | | | | n/a | n/a |
| PLY-51 | Volume swipe (right) | | | | | | n/a | n/a |
| PLY-52 | Remember brightness: the next playback starts at the last brightness; leaving the player restores the system brightness | | | | | | n/a | n/a |
| PLY-53 | Horizontal seek swipe with the time and a trickplay thumbnail; seeks on release | | | | | | n/a | n/a |
| PLY-54 | Pinch to zoom (crop to fill) and back; mpv moves subtitles into the margins | | | | | | n/a | n/a |
| PLY-55 | Start maximized opens zoomed | | | | | | n/a | n/a |
| PLY-56 | Long-press Chapter: left goes to the previous chapter, right to the next | #50 | | | | | n/a | n/a |
| PLY-57 | Long-press 2× speed while held | #50 | | | | | n/a | n/a |
| PLY-58 | On an expanded window, volume and brightness swipes only work within 240 dp of an edge | #289 | n/a | n/a | | | n/a | n/a |
| PLY-59 | Swipes do not fire in the system gesture area, also in split-screen and freeform | #55, #91 | | | | | n/a | n/a |
| PLY-60 | Lock: blocks gestures and controls, keeps the orientation, unlock button works | `PlayerActivity` | | | | | n/a | n/a |
| PLY-61 | Orientation: phone-sized windows play in landscape; tablets keep the orientation they are held in | #289 | | | | | n/a | n/a |
| PLY-62 | Controls in a narrow window (split-screen): Next and Back stay visible, the rest scrolls | #101 | | | | | n/a | n/a |
| PLY-63 | Rotating during playback keeps position and play state | | | | | | n/a | n/a |

### Keys

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-64 | Volume keys during playback change the volume, no crash (Android 10 and 11 especially) | #350 | | | | | | |
| PLY-65 | Media keys (play/pause, play, pause, next, previous, fast forward, rewind) via `adb shell input keyevent` | #310, `TvPlayerKeyHandler` | | | | | | |
| PLY-66 | Bluetooth headset: play/pause, next, volume, disconnect mid-playback pauses **[device]** | #350, #359 | | | | | | |
| PLY-67 | Hardware keyboard: Space plays and pauses, Left/Right seek, Up/Down volume, M mutes, L locks, Esc goes back | #310 | | | | | n/a | n/a |
| PLY-68 | Hardware keyboard: Tab focuses the controls, then arrows and Space act on the focused one | #310 | | | | | n/a | n/a |
| PLY-69 | Mouse: moving shows the controls, the wheel seeks | #310 | | | | | n/a | n/a |
| PLY-70 | TV, controls hidden: Left/Right seek at once with an indicator; holding speeds up | #124, `TvPlayerKeyHandler` | n/a | n/a | n/a | n/a | | |
| PLY-71 | TV: OK shows the controls; with "OK pauses" on it also pauses | #126 | n/a | n/a | n/a | n/a | | |
| PLY-72 | TV: Back hides the controls (closes the queue row first), then leaves the player | #124 | n/a | n/a | n/a | n/a | | |
| PLY-73 | TV remote keys Stop (leaves), Captions (subtitle dialog), Audio track (audio dialog) | `TvPlayerKeyHandler` | n/a | n/a | n/a | n/a | | |
| PLY-74 | TV: the skip button and the Up next card take focus while the controls are hidden; OK presses, Back cancels the card | #271, #272 | n/a | n/a | n/a | n/a | | |

### Media segments

Use a debug build with `files/fake_segments.txt` where the server has no segments (`DebugSegments.kt`).

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-75 | Skip automatically: the intro is skipped without input | #20, #271 | | | | | | |
| PLY-76 | Ask: the skip button shows for the set duration after the segment starts; after that only with the controls (phone) or not at all (TV) | #271, #283 | | | | | | |
| PLY-77 | Ignore: no button, no skip | #271 | | | | | | |
| PLY-78 | Outro, recap, preview and commercial each follow their own setting | #271 | | | | | | |
| PLY-79 | Seeking back into a segment shows the skip button again | `SegmentTracker` | | | | | | |
| PLY-80 | An outro ending within the threshold of the end shows "Next episode" and goes to the next episode | #65, `SegmentTracker` | | | | | | |
| PLY-81 | Skipping an outro past 90 % marks the episode watched | #65 | | | | | | |
| PLY-82 | Auto-skip "only in PiP": in PiP the segment is skipped, outside PiP it asks | #271 | | | | | n/a | n/a |
| PLY-83 | The skip button is hidden in PiP and while the Up next card shows | `SegmentTracker` | | | | | | |

### Sleep timer

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-84 | Set 15 minutes (or 1 minute in a debug build): playback pauses on time and the controls show | #298 | | | | | | |
| PLY-85 | "End of episode": pauses at the end instead of going to the next episode | #298 | | | | | | |
| PLY-86 | The timer button shows that it runs and the time left; Off cancels it | #298 | | | | | | |
| PLY-87 | After the timer fired, autoplay waits until Play is pressed | #298 | | | | | | |

### Up next, autoplay and still watching

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-88 | Up next card with a countdown 15 s before the end of an episode | #272 | | | | | | |
| PLY-89 | Up next card at the start of the outro when that comes first | #272 | | | | | | |
| PLY-90 | Play now starts the next episode; Cancel stops at the end of this one | #272 | | | | | | |
| PLY-91 | The countdown stops while paused and follows the playback speed | #272 | | | | | | |
| PLY-92 | Autoplay "server setting" follows the Jellyfin user's "Play next episode automatically" | #66 | | | | | | |
| PLY-93 | Autoplay "always" and "never" override it; with never, the episode is marked watched and the player closes | #66 | | | | | | |
| PLY-94 | Offline, "server setting" keeps autoplaying | #66 | | | | | n/a | n/a |
| PLY-95 | A short episode at the end of autoplay does not stall | #238 | | | | | | |
| PLY-96 | Still watching on: after the set number of auto-advanced episodes the prompt shows | #22, #349 | | | | | | |
| PLY-97 | Still watching: after the set minutes without input the prompt shows; seeking counts as input | #22, #70 | | | | | | |
| PLY-98 | Still watching: Continue goes on; the timeout leaves it paused | #22 | | | | | | |
| PLY-99 | Still watching off (the default): no prompt, whatever the thresholds | #349 | | | | | | |

### Picture-in-picture and background audio

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-100 | PiP button: the video goes into a PiP window and keeps playing | | | | | | n/a | n/a |
| PLY-101 | PiP gesture on: going Home while playing enters PiP | `playerPipGesture` | | | | | n/a | n/a |
| PLY-102 | In PiP the controls, skip button and Up next card are hidden; zoom is off and comes back after | `PlayerActivity` | | | | | n/a | n/a |
| PLY-103 | Closing the PiP window stops playback (with background audio on, audio goes on) | #79 | | | | | n/a | n/a |
| PLY-104 | Background audio on: audio goes on with the screen off and the app in the background; lock-screen artwork and controls work | #36, #80 | | | | | n/a | n/a |
| PLY-105 | Background audio: swiping the app away keeps the audio; the notification brings the player back | #118 | | | | | n/a | n/a |
| PLY-106 | Background audio off: leaving the app pauses; coming back resumes at the same position | `BasePlayerActivity` | | | | | n/a | n/a |

### Formats

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-107 | H.264 direct play | #356 | | | | | | |
| PLY-108 | HEVC direct play | #356 | | | | | | |
| PLY-109 | AV1 | #356 | | | | | | |
| PLY-110 | HDR10 shows as HDR on an HDR display; brightness is normal after leaving the player **[device]** | #359 | | | | | | |
| PLY-111 | HDR10+ **[device]** | #282, #359 | | | | | | |
| PLY-112 | A transcode (HLS), for example with a quality cap | #61 | | | | | | |
| PLY-113 | Atmos (E-AC-3 JOC) on a phone: plays with sound, no crash | #59, #97 | | | | | n/a | n/a |
| PLY-114 | Atmos passthrough to a soundbar or receiver **[device]** | #59, #359 | n/a | n/a | n/a | n/a | | |
| PLY-115 | Audio decoder fallback: when the platform decoder fails, playback goes on with ffmpeg at the same position | #97, #99 | | n/a | | n/a | | n/a |
| PLY-116 | Dolby Vision direct play on a capable device **[device]** | #96, #100 | | n/a | | n/a | | n/a |
| PLY-117 | Dolby Vision on mpv (always direct-played) **[device]** | `DeviceProfileBuilder` | n/a | | n/a | | n/a | |
| PLY-118 | Dolby Vision with "Direct play Dolby Vision" off, or on a device without DV: transcoded, no green and purple picture | #96, ce.30 | | n/a | | n/a | | n/a |
| PLY-119 | Display refresh rate vote: `dumpsys SurfaceFlinger` shows a 23.98 Hz vote for a 24 fps video | #338, mpv-checklist | | | | | | |
| PLY-120 | 24 fps playback on a 120 Hz phone runs at a matching rate, not 120 Hz **[device]** | #338, #359 | | | n/a | n/a | n/a | n/a |
| PLY-121 | Battery and heat over a 30-minute episode **[device]** | #359 | | | n/a | n/a | n/a | n/a |
| PLY-122 | Downloaded file playback (offline and online) | #356 | | | | | n/a | n/a |
| PLY-123 | Match display refresh rate on TV switches the display mode to the video's frame rate, and back after **[device]** | #139 | n/a | n/a | n/a | n/a | | |

### Interruptions and lifecycle

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-124 | Audio focus loss (incoming call, another app plays audio) pauses; write down whether it resumes after | `MPVPlayer`, `PlayerViewModel` | | | | | | |
| PLY-125 | Unplugging headphones pauses | 1.2.0-ce.4 (#114) | | | | | | |
| PLY-126 | Screen off and on during playback | #52 | | | | | | |
| PLY-127 | Network lost mid-playback, then back: what happens, and does it recover | #356 | | | | | | |
| PLY-128 | TV Home button during playback pauses; returning resumes | `TvPlayerScreen` | n/a | n/a | n/a | n/a | | |
| PLY-129 | Process death in the player with a dialog open (tracks, speed, chapters, still watching): no crash on restore | ce.1 | | | | | n/a | n/a |
| PLY-130 | Starting another item while the player is open (new intent, Watch Next) replaces playback cleanly | #187, #231 | | | | | | |

### Wide windows and foldables

| ID | Feature: what to do, and what "works" means | Source | Phone Exo | Phone mpv | Tablet Exo | Tablet mpv | TV Exo | TV mpv |
|---|---|---|---|---|---|---|---|---|
| PLY-131 | Roomier controls on an expanded window; they resize when the window does | #301, #313 | n/a | n/a | | | n/a | n/a |
| PLY-132 | Episode side sheet: lists the season's episodes, switches episode without leaving the player; Back closes it | #314 | n/a | n/a | | | n/a | n/a |
| PLY-133 | Tabletop posture on a foldable: video above the fold, controls below | #313 | n/a | n/a | | | n/a | n/a |
| PLY-134 | Playback survives a fold, an unfold and a window resize | test matrix | n/a | n/a | | | n/a | n/a |

## 9. TV system integration

All rows are TV only.

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| TVS-01 | Watch Next row on the Android TV home screen shows Continue Watching and Next Up items | #138 | n/a | n/a | |
| TVS-02 | Picking a Watch Next program resumes it in the player, with its page underneath; Back goes to the page, then Home | #138 | n/a | n/a | |
| TVS-03 | Starting a Watch Next program while the player is open does not crash | #187 | n/a | n/a | |
| TVS-04 | A program removed from the row stays away until it is watched again | #138 | n/a | n/a | |
| TVS-05 | The row updates after watching something | #138 | n/a | n/a | |
| TVS-06 | Watch Next setting off: the app's programs leave the row | #138 | n/a | n/a | |
| TVS-07 | After a user switch, a program of the other user does not play as the current user | `WatchNextLink` | n/a | n/a | |
| TVS-08 | A locked profile asks its PIN before a Watch Next program plays | #299 | n/a | n/a | |
| TVS-09 | Screensaver shows random backdrops from the library, with logo and clock | #140, #235 | n/a | n/a | |
| TVS-10 | Screensaver settings (from the system's screensaver settings): source, interval, clock | #140 | n/a | n/a | |
| TVS-11 | Screensaver with no user logged in: a plain screen, no crash | `ScreensaverService` | n/a | n/a | |
| TVS-12 | The Ephyra banner and app name in the Android TV launcher | #171, #172 | n/a | n/a | |
| TVS-13 | Every screen keeps clear of the TV safe area; nothing clipped by overscan at 1080p and 4K; text readable from the couch | #127, #355 | n/a | n/a | |
| TVS-14 | The 32-bit (`armeabi-v7a`) APK installs and starts on the Xiaomi box **[device]** | ADR 0006, #359 | n/a | n/a | |
| TVS-15 | Pressing Home and coming back to the app returns to the same screen and focus | | n/a | n/a | |
| TVS-16 | D-pad latency on Home and in the player feels immediate **[device]** | #359 | n/a | n/a | |

## 10. Lifecycle and system

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| LIF-01 | Rotation on every screen keeps the screen, its input and its scroll position | #353 | | | n/a |
| LIF-02 | Background and return keeps the screen and scroll position; pages reload only when stale | #268 | | | |
| LIF-03 | Process death (`am kill` in the background) restores the screen without a crash | #353 | | | |
| LIF-04 | Split-screen at 600 dp: every screen stays usable | #288, test matrix | | | n/a |
| LIF-05 | Free-form window resize: layouts follow the width | #288 | n/a | | n/a |
| LIF-06 | Fold and unfold keeps the screen and state | test matrix | n/a | | n/a |
| LIF-07 | Font scale 1.3: no text cut off or overlapping | #353 | | | |
| LIF-08 | A right-to-left language (Arabic or Hebrew): layouts mirror, nothing overlaps | #353 | | | |
| LIF-09 | CE screens are translated: no English left in a non-English language | #106, #110 | | | |
| LIF-10 | Edge-to-edge: no content under the status bar, navigation bar or floating headers | #94, #357 | | | n/a |
| LIF-11 | Predictive back on Android 16 and 17 | #357 | | | n/a |
| LIF-12 | Notification permission asked on Android 13+; downloads work without it | ce.32, `MainActivity` | | | n/a |
| LIF-13 | Local network permission on Android 17: asked in setup, a one-time prompt for existing users, the app works after a denial and explains why it needs it | 1.2.0-ce.0 | | | |
| LIF-14 | The debug and beta builds are labelled "Ephyra Debug" and "Ephyra Beta" | #172 | | | |
| LIF-15 | Screen reader (TalkBack) reads the back, sort and main buttons | ce.26 | | | |
| LIF-16 | Cold start time and scrolling feel against `docs/performance.md` **[device]** | #259, #269 | | | |
| LIF-17 | No crash or ANR in `logcat` during the whole run | #361 | | | |

## 11. Upgrade and migration

Install the older release, set it up (login, non-default settings, two downloaded episodes, a
resume position), then install the build under test over it.

| ID | Feature: what to do, and what "works" means | Source | Phone | Tablet | TV |
|---|---|---|---|---|---|
| UPG-01 | 2.0.0 to current: still logged in, same server and users | #358 | | | |
| UPG-02 | 2.0.0 to current: the non-default settings are kept | #358 | | | |
| UPG-03 | 2.0.0 to current: downloads are listed and play | #358 | | | n/a |
| UPG-04 | 2.0.0 to current: resume positions are kept | #358 | | | |
| UPG-05 | Step by step 2.0.0, 2.1, 2.2, 2.3, current: the same four checks at each step | #358 | | | |
| UPG-06 | 2.3.0 to current | #358 | | | |
| UPG-07 | Findroid CE (1.2.0-ce.6) to current: installs over it, login kept | 2.0.0 | | | |
| UPG-08 | Room migrations to schema 17 and 18 (profile lock columns): a profile can be locked after the upgrade | #299, #330 | | | |
| UPG-09 | Old media segment switches become per-type actions: an install with auto-skip on for intros shows Intro = Skip | #271 | | | |
| UPG-10 | mpv audio output stored as `aaudio` is moved to `audiotrack` once; picking `aaudio` again afterwards sticks | ce.3 | | | |
| UPG-11 | Home rows start from the old Display switches until the layout is changed | #142 | | | |
| UPG-12 | Still watching after the upgrade: off, with the thresholds kept | #349 | | | |
| UPG-13 | Phone APK replaced by the TV APK on a box: login and settings kept, a higher versionCode | ADR 0005, #173 | n/a | n/a | |
| UPG-14 | Stable and beta installed side by side, each with its own data | #83, #98 | | | |
| UPG-15 | Downgrade to the previous release: write down what is lost (the login is known to go) | #358 | | | |
| UPG-16 | Every Room schema version has an exported schema or a migration test to compare against | `data/schemas` | | | |

## Smoke set for the Android version matrix (#357)

The rows #357 runs on each API level: SUP-06, SUP-08, HOM-06, BRW-07, PLY-01 (both backends),
PLY-04, PLY-64, PLY-65, PLY-100, DLD-02, OFF-05, LIF-01, LIF-12, SET-05, and on 36/37 also LIF-10,
LIF-11 and LIF-13.
