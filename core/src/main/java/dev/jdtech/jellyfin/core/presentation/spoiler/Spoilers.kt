package dev.jdtech.jellyfin.core.presentation.spoiler

import android.os.Build
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import java.util.UUID

/**
 * CE (#618): the episodes revealed by a press and hold while "Hide spoilers" is on. They stay
 * revealed until the app's process ends, on every screen and in the player's episode list, so the
 * episode page of a revealed card is not hidden again.
 */
internal object SpoilerReveals {
    private val revealed = mutableStateMapOf<UUID, Unit>()

    fun isRevealed(id: UUID): Boolean = id in revealed

    fun reveal(id: UUID) {
        revealed[id] = Unit
    }

    /** Another user's reveals are not this one's: the same episode has the same id for both. */
    fun clear() {
        revealed.clear()
    }
}

/**
 * CE (#618): what "Hide spoilers" hides. An episode that is not played gives away its still, its
 * title and its overview; those are hidden until it is watched or revealed. Movies, shows and
 * seasons are never hidden, and neither is the player: what plays is what the user chose to watch.
 */
@Immutable
data class Spoilers(val enabled: Boolean) {
    /** Whether [item]'s still, title and overview are hidden. Reads snapshot state. */
    fun hides(item: FindroidItem?): Boolean =
        enabled && item is FindroidEpisode && !item.played && !SpoilerReveals.isRevealed(item.id)

    fun reveal(item: FindroidItem) = SpoilerReveals.reveal(item.id)

    companion object {
        val Off = Spoilers(enabled = false)
    }
}

/** The app's [Spoilers]; off where nothing provides it (previews, tests). */
val LocalSpoilers = staticCompositionLocalOf { Spoilers.Off }

/** Whether this device can blur a hidden image (Modifier.blur needs Android 12). */
val canBlurSpoilers: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Hides the image this modifies when [hidden]: blurred past recognition on Android 12 and up, and
 * covered by [fallback] below that, where Modifier.blur does nothing. An eye with a line through it
 * says it is hidden on purpose, and is not an image that failed to load.
 */
@Composable
fun Modifier.spoilerImage(hidden: Boolean, fallback: Color, iconTint: Color): Modifier {
    if (!hidden) return this
    val icon = painterResource(R.drawable.ic_eye_off)
    val tint = ColorFilter.tint(iconTint)
    val image =
        if (canBlurSpoilers) {
            Modifier.clipToBounds().blur(SpoilerBlur, BlurredEdgeTreatment.Rectangle)
        } else {
            Modifier.drawWithContent { drawRect(fallback) }
        }
    // The icon is drawn over the blur, not under it, so it stays sharp.
    return this.drawWithContent {
            drawContent()
            val iconSize = minOf(size.minDimension / 3f, 32.dp.toPx())
            translate(left = (size.width - iconSize) / 2f, top = (size.height - iconSize) / 2f) {
                with(icon) { draw(Size(iconSize, iconSize), colorFilter = tint) }
            }
        }
        .then(image)
}

private val SpoilerBlur = 32.dp

/** The title [item] is shown with: its name, or "Episode 5" while it is hidden. */
@Composable
fun spoilerFreeName(item: FindroidItem, hidden: Boolean): String =
    if (hidden && item is FindroidEpisode) {
        stringResource(R.string.episode_number, item.indexNumber)
    } else item.name

/**
 * Clicks with [onClick], and reveals [item] on a press and hold. A hidden item takes this in place
 * of a long press that copies its text, which would give the hidden title away.
 */
@Composable
fun Modifier.revealOnLongClick(item: FindroidItem, onClick: (() -> Unit)? = null): Modifier {
    val spoilers = LocalSpoilers.current
    return this.combinedClickable(
        onClick = onClick ?: {},
        onLongClickLabel = stringResource(R.string.ce_spoiler_reveal),
        onLongClick = { spoilers.reveal(item) },
    )
}

/** "S1:E5 - Title", or "S1 E5" while [episode] is hidden. */
@Composable
fun spoilerFreeEpisodeLine(episode: FindroidEpisode, hidden: Boolean): String =
    if (hidden) {
        stringResource(
            R.string.season_episode_number,
            episode.parentIndexNumber,
            episode.indexNumber,
        )
    } else {
        stringResource(
            R.string.episode_name_extended,
            episode.parentIndexNumber,
            episode.indexNumber,
            episode.name,
        )
    }

/** [overview], or the line that says it is hidden. */
@Composable
fun spoilerFreeOverview(overview: String, hidden: Boolean): String =
    if (hidden && overview.isNotBlank()) stringResource(R.string.ce_spoiler_overview_hidden)
    else overview
