package dev.jdtech.jellyfin.setup.presentation.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.hasPin
import dev.jdtech.jellyfin.profilelock.ProfileLock
import dev.jdtech.jellyfin.profilelock.ProfilePinHasher
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** CE (#299): which PIN the Profile PIN screen is asking for. */
sealed interface PinStep {
    /** The current PIN, before it can be changed ([forRemoval] false) or removed. */
    data class Current(val forRemoval: Boolean) : PinStep

    data object New : PinStep

    data class Confirm(val firstEntry: String) : PinStep
}

data class ProfilePinSettingsState(
    val user: User? = null,
    val step: PinStep? = null,
    val prompt: PinPrompt? = null,
) {
    val hasPin: Boolean
        get() = user?.hasPin == true

    val pinOnStart: Boolean
        get() = user?.pinOnStart != false
}

sealed interface ProfilePinSettingsAction {
    data object OnSetClick : ProfilePinSettingsAction

    data object OnChangeClick : ProfilePinSettingsAction

    data object OnRemoveClick : ProfilePinSettingsAction

    data class OnPinOnStartChange(val pinOnStart: Boolean) : ProfilePinSettingsAction

    data class OnPinSubmit(val pin: String) : ProfilePinSettingsAction

    data object OnPinDismiss : ProfilePinSettingsAction

    data object OnBackClick : ProfilePinSettingsAction
}

sealed interface ProfilePinSettingsEvent {
    data object PinSet : ProfilePinSettingsEvent

    data object PinRemoved : ProfilePinSettingsEvent
}

/**
 * CE (#299): Settings → Profile PIN for the signed-in profile. A new PIN is typed twice; changing
 * or removing it asks for the current one first.
 */
@HiltViewModel
class ProfilePinSettingsViewModel
@Inject
constructor(private val repository: SetupRepository, private val profileLock: ProfileLock) :
    ViewModel() {
    private val _state = MutableStateFlow(ProfilePinSettingsState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ProfilePinSettingsEvent>()
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val user = repository.getCurrentUser()
        _state.update { it.copy(user = user) }
    }

    fun onAction(action: ProfilePinSettingsAction) {
        when (action) {
            is ProfilePinSettingsAction.OnSetClick -> ask(PinStep.New)
            is ProfilePinSettingsAction.OnChangeClick -> ask(PinStep.Current(forRemoval = false))
            is ProfilePinSettingsAction.OnRemoveClick -> ask(PinStep.Current(forRemoval = true))
            is ProfilePinSettingsAction.OnPinOnStartChange -> {
                val user = _state.value.user ?: return
                viewModelScope.launch {
                    profileLock.setPinOnStart(user.id, action.pinOnStart)
                    reload()
                }
            }
            is ProfilePinSettingsAction.OnPinSubmit -> submit(action.pin)
            is ProfilePinSettingsAction.OnPinDismiss ->
                _state.update { it.copy(step = null, prompt = null) }
            is ProfilePinSettingsAction.OnBackClick -> Unit
        }
    }

    private fun ask(step: PinStep, error: PinError? = null) {
        val user = _state.value.user ?: return
        val previous = _state.value.prompt
        val prompt =
            if (error != null && previous != null) previous.rejected(error)
            else PinPrompt(user.id, user.name)
        _state.update { it.copy(step = step, prompt = prompt) }
    }

    private fun submit(pin: String) {
        val user = _state.value.user ?: return
        val step = _state.value.step ?: return
        viewModelScope.launch {
            when (step) {
                is PinStep.Current -> {
                    if (!profileLock.verifyPin(user.id, pin)) {
                        _state.update { it.copy(prompt = it.prompt?.rejected(PinError.WRONG)) }
                    } else if (step.forRemoval) {
                        profileLock.clearPin(user.id)
                        _state.update { it.copy(step = null, prompt = null) }
                        reload()
                        eventsChannel.send(ProfilePinSettingsEvent.PinRemoved)
                    } else {
                        _state.update {
                            it.copy(step = PinStep.New, prompt = PinPrompt(user.id, user.name))
                        }
                    }
                }
                is PinStep.New -> {
                    if (!ProfilePinHasher.isValid(pin)) return@launch
                    _state.update {
                        it.copy(step = PinStep.Confirm(pin), prompt = PinPrompt(user.id, user.name))
                    }
                }
                is PinStep.Confirm -> {
                    if (pin != step.firstEntry) {
                        ask(PinStep.New, PinError.MISMATCH)
                    } else {
                        profileLock.setPin(user.id, pin)
                        _state.update { it.copy(step = null, prompt = null) }
                        reload()
                        eventsChannel.send(ProfilePinSettingsEvent.PinSet)
                    }
                }
            }
        }
    }
}
