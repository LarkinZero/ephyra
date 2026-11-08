package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

// The font size at 100 %. mpv's default of 38 draws text about a fifth smaller than ExoPlayer's
// SubtitleView does on a 16:9 screen; 47 matches it there.
private const val FONT_SIZE = 47
// mpv's default outline.
private const val MPV_OUTLINE_SIZE = "1.65"
private const val SHADOW_OFFSET = "2"

private const val DEFAULT_FOREGROUND = "#FFFFFFFF"
private const val DEFAULT_EDGE = "#FF000000"
private const val TRANSPARENT = "#00000000"

/**
 * CE (#300, #308): the mpv options that carry the app's subtitle settings. mpv draws subtitles
 * itself, so the style applied to ExoPlayer's SubtitleView never reaches it.
 *
 * The style options apply to text subtitles such as SubRip. `sub-ass-override=force` makes mpv
 * apply them to ASS/SSA tracks as well, replacing the styles they bring; inline tags such as
 * `{\pos}` keep working. `scale`, mpv's default, leaves those tracks as they are.
 *
 * Every option is in the map whatever the settings, so applying it again while mpv plays also
 * undoes a setting turned back to its default. Colours are passed as `#AARRGGBB`, which is how the
 * settings store them and how mpv reads them.
 */
fun mpvSubtitleOptions(
    overrideEmbeddedStyles: Boolean,
    foregroundColor: String? = null,
    backgroundColor: String? = null,
    edgeColor: String? = null,
    edgeType: String? = null,
    fontFamily: String? = null,
    fontScalePercent: Int = Constants.SubtitleStyle.FONT_SCALE_DEFAULT,
): Map<String, String> {
    val background = mpvColor(backgroundColor, TRANSPARENT)
    val edge = mpvColor(edgeColor, DEFAULT_EDGE)
    val hasBackground = !background.startsWith("#00")
    // mpv draws the shadow in the background colour, so a shadow edge gets the edge colour there
    // unless a background box needs it. That box then stands in for the shadow.
    val shadow =
        when (edgeType) {
            Constants.SubtitleStyle.EDGE_DROP_SHADOW,
            Constants.SubtitleStyle.EDGE_RAISED,
            Constants.SubtitleStyle.EDGE_DEPRESSED -> !hasBackground
            else -> false
        }
    val outline =
        edgeType == null ||
            edgeType == Constants.SubtitleStyle.EDGE_OUTLINE ||
            edgeType !in KNOWN_EDGE_TYPES
    val scale =
        fontScalePercent.coerceIn(
            Constants.SubtitleStyle.FONT_SCALE_MIN,
            Constants.SubtitleStyle.FONT_SCALE_MAX,
        )

    return linkedMapOf(
        "sub-ass-override" to if (overrideEmbeddedStyles) "force" else "scale",
        "sub-font" to
            when (fontFamily) {
                Constants.SubtitleStyle.FONT_SERIF -> "serif"
                Constants.SubtitleStyle.FONT_MONOSPACE -> "monospace"
                else -> "sans-serif"
            },
        "sub-font-size" to (FONT_SIZE * scale / 100f).roundToInt().toString(),
        "sub-color" to mpvColor(foregroundColor, DEFAULT_FOREGROUND),
        "sub-border-style" to if (hasBackground) "background-box" else "outline-and-shadow",
        "sub-back-color" to if (shadow) edge else background,
        "sub-outline-color" to edge,
        "sub-outline-size" to if (outline) MPV_OUTLINE_SIZE else "0",
        "sub-shadow-offset" to if (shadow) SHADOW_OFFSET else "0",
    )
}

private val KNOWN_EDGE_TYPES =
    setOf(
        Constants.SubtitleStyle.EDGE_NONE,
        Constants.SubtitleStyle.EDGE_OUTLINE,
        Constants.SubtitleStyle.EDGE_DROP_SHADOW,
        Constants.SubtitleStyle.EDGE_RAISED,
        Constants.SubtitleStyle.EDGE_DEPRESSED,
    )

/** Normalises a stored `#RRGGBB` or `#AARRGGBB` colour to mpv's `#AARRGGBB`, else [fallback]. */
internal fun mpvColor(value: String?, fallback: String): String {
    val hex = value?.trim()?.removePrefix("#") ?: return fallback
    if (hex.length != 6 && hex.length != 8) return fallback
    if (hex.any { Character.digit(it, 16) < 0 }) return fallback
    return "#" + (if (hex.length == 6) "FF$hex" else hex).uppercase()
}

fun AppPreferences.mpvSubtitleOptions(): Map<String, String> =
    mpvSubtitleOptions(
        overrideEmbeddedStyles = getValue(subtitleOverrideEmbeddedStyles),
        foregroundColor = getValue(subtitleForegroundColor),
        backgroundColor = getValue(subtitleBackgroundColor),
        edgeColor = getValue(subtitleEdgeColor),
        edgeType = getValue(subtitleEdgeType),
        fontFamily = getValue(subtitleFontFamily),
        fontScalePercent = getValue(subtitleFontScale),
    )

/** The options from [mpvSubtitleOptions] each time one of the subtitle settings changes. */
fun AppPreferences.mpvSubtitleOptionChanges(): Flow<Map<String, String>> =
    merge(
            observe(subtitleOverrideEmbeddedStyles).drop(1),
            observe(subtitleForegroundColor).drop(1),
            observe(subtitleBackgroundColor).drop(1),
            observe(subtitleEdgeColor).drop(1),
            observe(subtitleEdgeType).drop(1),
            observe(subtitleFontFamily).drop(1),
            observe(subtitleFontScale).drop(1),
        )
        .map { mpvSubtitleOptions() }
