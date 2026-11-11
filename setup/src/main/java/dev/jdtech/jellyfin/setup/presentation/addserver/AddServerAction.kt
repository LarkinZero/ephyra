package dev.jdtech.jellyfin.setup.presentation.addserver

sealed interface AddServerAction {
    data class OnConnectClick(val address: String) : AddServerAction

    // CE (#443): the address field changed, so an error about the address that was tried no
    // longer applies.
    data object OnAddressChange : AddServerAction

    data object OnBackClick : AddServerAction
}
