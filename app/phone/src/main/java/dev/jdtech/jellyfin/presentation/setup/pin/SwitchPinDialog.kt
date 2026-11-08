package dev.jdtech.jellyfin.presentation.setup.pin

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.users.UsersAction
import dev.jdtech.jellyfin.setup.presentation.users.UsersState

/** CE (#299): the PIN asked before switching to a locked profile, from the sheet or the user list. */
@Composable
fun SwitchPinDialog(state: UsersState, onAction: (UsersAction) -> Unit) {
    val prompt = state.pinPrompt ?: return
    PinDialog(
        prompt = prompt,
        title = stringResource(SetupR.string.ce_profile_pin_enter, prompt.userName),
        footnote = stringResource(SetupR.string.ce_profile_pin_forgot),
        onSubmit = { onAction(UsersAction.OnPinSubmit(it)) },
        onDismiss = { onAction(UsersAction.OnPinDismiss) },
    )
}
