package dev.jdtech.jellyfin.setup.presentation.users

import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.setup.presentation.pin.PinPrompt
import java.util.UUID

data class UsersState(
    val users: List<User> = emptyList(),
    val publicUsers: List<User> = emptyList(),
    val serverName: String? = null,
    val currentUserId: UUID? = null,
    /** CE (#299): the PIN asked before switching to a locked profile. */
    val pinPrompt: PinPrompt? = null,
)
