package dev.jdtech.jellyfin.setup.presentation.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.profilelock.ProfileLock
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilePinGateState(val user: User? = null, val prompt: PinPrompt? = null)

sealed interface ProfilePinGateAction {
    data class OnPinSubmit(val pin: String) : ProfilePinGateAction

    /** Back or "Other profile": leave the profile closed and pick another one. */
    data object OnCancel : ProfilePinGateAction
}

/**
 * CE (#299): the PIN asked when the app starts in a profile that has one, before Home loads. A
 * correct PIN unlocks the profile, and the app's main state lets Home through. Cancelling closes
 * the profile (the server keeps no current user), so the app shows the user selection instead of
 * exiting.
 */
@HiltViewModel
class ProfilePinGateViewModel
@Inject
constructor(private val repository: SetupRepository, private val profileLock: ProfileLock) :
    ViewModel() {
    private val _state = MutableStateFlow(ProfilePinGateState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = repository.getCurrentUser() ?: return@launch
            _state.update { ProfilePinGateState(user = user, prompt = PinPrompt(user.id, user.name)) }
        }
    }

    fun onAction(action: ProfilePinGateAction) {
        when (action) {
            is ProfilePinGateAction.OnPinSubmit -> submit(action.pin)
            is ProfilePinGateAction.OnCancel ->
                viewModelScope.launch { repository.clearCurrentUser() }
        }
    }

    private fun submit(pin: String) {
        val prompt = _state.value.prompt ?: return
        viewModelScope.launch {
            if (profileLock.verifyPin(prompt.userId, pin)) {
                profileLock.unlock(prompt.userId)
            } else {
                _state.update { it.copy(prompt = prompt.rejected(PinError.WRONG)) }
            }
        }
    }
}
