package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.settings.domain.AppPreferences

/**
 * CE (#300): the mpv options that carry the app's subtitle settings. mpv draws subtitles itself,
 * so the style applied to ExoPlayer's SubtitleView never reaches it.
 *
 * `sub-ass-override=force` makes mpv apply its `sub-*` style options to ASS/SSA tracks as well,
 * replacing the styles they bring; inline tags such as `{\pos}` keep working. `scale`, mpv's
 * default, leaves those tracks as they are.
 */
fun mpvSubtitleOptions(overrideEmbeddedStyles: Boolean): Map<String, String> =
    mapOf("sub-ass-override" to if (overrideEmbeddedStyles) "force" else "scale")

fun AppPreferences.mpvSubtitleOptions(): Map<String, String> =
    mpvSubtitleOptions(overrideEmbeddedStyles = getValue(subtitleOverrideEmbeddedStyles))
