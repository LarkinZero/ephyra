package dev.jdtech.jellyfin.presentation.setup.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.presentation.ephyra.errorShake
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.profilelock.ProfilePinHasher
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.pin.PinError
import dev.jdtech.jellyfin.setup.presentation.pin.PinPrompt
import java.util.UUID

/** The message for a rejected PIN. */
@Composable
fun PinError.message(): String =
    when (this) {
        PinError.WRONG -> stringResource(SetupR.string.ce_profile_pin_wrong)
        PinError.MISMATCH -> stringResource(SetupR.string.ce_profile_pin_mismatch)
    }

/**
 * The digits typed into a [PinField] for [prompt]. A rejection ([PinPrompt.errorCount] going up)
 * clears them, and so does a new step of the same prompt ([key]).
 */
@Composable
fun rememberPinState(prompt: PinPrompt, key: Any? = null): MutableState<String> {
    val pin = rememberSaveable(prompt.userId, key) { mutableStateOf("") }
    LaunchedEffect(prompt.errorCount) { if (prompt.errorCount > 0) pin.value = "" }
    return pin
}

/**
 * CE (#299): the PIN field, on the system's numeric keyboard. Digits only, at most six, shown as
 * dots. A rejection shakes it, with the reason under it.
 */
@Composable
fun PinField(
    prompt: PinPrompt,
    pin: MutableState<String>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = pin.value,
        onValueChange = { value ->
            pin.value = value.filter { it in '0'..'9' }.take(ProfilePinHasher.MAX_LENGTH)
        },
        label = { Text(stringResource(SetupR.string.ce_profile_pin_label)) },
        singleLine = true,
        isError = prompt.error != null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = { if (ProfilePinHasher.isValid(pin.value)) onSubmit(pin.value) }
            ),
        supportingText = prompt.error?.let { error -> { Text(error.message()) } },
        modifier =
            modifier
                .focusRequester(focusRequester)
                .errorShake(prompt.errorCount)
                .testTag("ce_pin_field"),
    )
}

/**
 * CE (#299): asks for a PIN in a dialog. [message] says which PIN; [footnote] is the way out for a
 * forgotten PIN. Cancel and Back call [onDismiss]. [stepKey] starts the field over for a new step.
 */
@Composable
fun PinDialog(
    prompt: PinPrompt,
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    footnote: String? = null,
    stepKey: Any? = null,
) {
    val pin = rememberPinState(prompt, stepKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                if (message != null) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
                PinField(
                    prompt = prompt,
                    pin = pin,
                    onSubmit = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (footnote != null) {
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin.value) },
                enabled = ProfilePinHasher.isValid(pin.value),
            ) {
                Text(stringResource(SetupR.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(SetupR.string.cancel)) }
        },
    )
}

@Preview
@Composable
private fun PinDialogPreview() {
    FindroidTheme {
        PinDialog(
            prompt = PinPrompt(UUID.randomUUID(), "Midas", PinError.WRONG, 1),
            title = "Enter the PIN for Midas",
            footnote = "Forgot the PIN? Remove this user and sign in again.",
            onSubmit = {},
            onDismiss = {},
        )
    }
}
