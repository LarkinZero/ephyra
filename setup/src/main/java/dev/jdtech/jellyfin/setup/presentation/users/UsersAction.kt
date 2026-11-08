package dev.jdtech.jellyfin.setup.presentation.users

import java.util.UUID

sealed interface UsersAction {
    data class OnUserClick(val userId: UUID) : UsersAction

    data class OnPublicUserClick(val username: String) : UsersAction

    data class OnDeleteUser(val userId: UUID) : UsersAction

    data object OnChangeServerClick : UsersAction

    data object OnAddClick : UsersAction

    data object OnBackClick : UsersAction

    // CE (#299)
    data class OnPinSubmit(val pin: String) : UsersAction

    /** CE (#330): the pattern drawn for a profile locked with one. */
    data class OnPatternSubmit(val dots: List<Int>) : UsersAction

    data object OnPinDismiss : UsersAction
}
