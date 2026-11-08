package dev.jdtech.jellyfin.ce.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.presentation.ephyra.AvatarColors
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraAvatar
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.hasPin
import dev.jdtech.jellyfin.models.primaryImageUrl

/** A user's avatar in the TV theme. [baseUrl] is the server the user belongs to. */
@Composable
fun UserAvatar(
    user: User,
    baseUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    /** CE (#299): show the lock badge when the profile has a PIN. */
    showLock: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val highest = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
    val onHighest = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val high = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
    val colors =
        remember(scheme, highest, onHighest, high) {
            AvatarColors(
                containers =
                    listOf(
                        scheme.secondaryContainer to scheme.onSecondaryContainer,
                        scheme.tertiaryContainer to scheme.onTertiaryContainer,
                        scheme.primaryContainer to scheme.onPrimaryContainer,
                        highest to onHighest,
                    ),
                badgeContainer = scheme.primary,
                badgeContent = scheme.onPrimary,
                badgeRing = scheme.surface,
                lockContainer = high,
                lockContent = onHighest,
            )
        }
    EphyraAvatar(
        name = user.name,
        imageUrl = user.primaryImageUrl(baseUrl),
        seed = user.id,
        size = size,
        colors = colors,
        modifier = modifier,
        current = current,
        locked = showLock && user.hasPin,
    )
}
