package dev.jdtech.jellyfin.ce.presentation.pin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.pin.PinPrompt
import dev.jdtech.jellyfin.setup.presentation.users.UsersAction
import dev.jdtech.jellyfin.setup.presentation.users.UsersState

/**
 * CE (#299): asks for a PIN in a dialog on TV. Back cancels. [stepKey] starts the pad over for a
 * new step of the same prompt.
 */
@Composable
fun TvPinDialog(
    prompt: PinPrompt,
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    footnote: String? = null,
    stepKey: Any? = null,
) {
    val pin = rememberTvPinState(prompt, stepKey)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(MaterialTheme.spacings.default).widthIn(max = 420.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (message != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                TvPinPad(prompt = prompt, pin = pin, onSubmit = onSubmit)
                if (footnote != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** CE (#299): the PIN asked before switching to a locked profile from the user list. */
@Composable
fun TvSwitchPinDialog(state: UsersState, onAction: (UsersAction) -> Unit) {
    val prompt = state.pinPrompt ?: return
    TvPinDialog(
        prompt = prompt,
        title = stringResource(SetupR.string.ce_profile_pin_enter, prompt.userName),
        footnote = stringResource(SetupR.string.ce_profile_pin_forgot),
        onSubmit = { onAction(UsersAction.OnPinSubmit(it)) },
        onDismiss = { onAction(UsersAction.OnPinDismiss) },
    )
}
