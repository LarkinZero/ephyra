package dev.jdtech.jellyfin.ce.presentation.pin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedBorder
import dev.jdtech.jellyfin.ce.ui.focus.ephyraFocusedGlow
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.pin.PinStep
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsAction
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsEvent
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsState
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents

/** CE (#299): Settings → Profile PIN on TV, for the signed-in profile. */
@Composable
fun TvProfilePinScreen(viewModel: ProfilePinSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        val message =
            when (event) {
                ProfilePinSettingsEvent.PinSet -> SetupR.string.ce_profile_pin_saved
                ProfilePinSettingsEvent.PinRemoved -> SetupR.string.ce_profile_pin_removed
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    TvProfilePinLayout(state = state, onAction = viewModel::onAction)
}

@Composable
private fun TvProfilePinLayout(
    state: ProfilePinSettingsState,
    onAction: (ProfilePinSettingsAction) -> Unit,
) {
    val firstItem = remember { FocusRequester() }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(
                    start = TvSafeArea.horizontal,
                    top = TvSafeArea.vertical,
                    end = TvSafeArea.horizontal,
                )
                .testTag("ce_profile_pin_settings")
    ) {
        Text(
            text = stringResource(SetupR.string.ce_profile_pin),
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text =
                listOfNotNull(
                        state.user?.name,
                        stringResource(
                            if (state.hasPin) SetupR.string.ce_profile_pin_status_on
                            else SetupR.string.ce_profile_pin_status_off
                        ),
                    )
                    .joinToString(" · "),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        Text(
            text = stringResource(SetupR.string.ce_profile_pin_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            modifier =
                Modifier.widthIn(max = 720.dp).padding(vertical = MaterialTheme.spacings.large),
        ) {
            if (state.user == null) return@Column
            if (state.hasPin) {
                PinRow(
                    title = stringResource(SetupR.string.ce_profile_pin_change),
                    onClick = { onAction(ProfilePinSettingsAction.OnChangeClick) },
                    modifier = Modifier.focusRequester(firstItem),
                )
                PinRow(
                    title = stringResource(SetupR.string.ce_profile_pin_ask_on_start),
                    summary = stringResource(SetupR.string.ce_profile_pin_ask_on_start_summary),
                    checked = state.pinOnStart,
                    onClick = {
                        onAction(ProfilePinSettingsAction.OnPinOnStartChange(!state.pinOnStart))
                    },
                )
                PinRow(
                    title = stringResource(SetupR.string.ce_profile_pin_remove),
                    destructive = true,
                    onClick = { onAction(ProfilePinSettingsAction.OnRemoveClick) },
                )
            } else {
                PinRow(
                    title = stringResource(SetupR.string.ce_profile_pin_set),
                    onClick = { onAction(ProfilePinSettingsAction.OnSetClick) },
                    modifier = Modifier.focusRequester(firstItem),
                )
            }
            LaunchedEffect(state.hasPin) { runCatching { firstItem.requestFocus() } }
        }
    }

    val prompt = state.prompt
    val step = state.step
    if (prompt != null && step != null) {
        TvPinDialog(
            prompt = prompt,
            title = stringResource(SetupR.string.ce_profile_pin),
            message =
                stringResource(
                    when (step) {
                        is PinStep.Current -> SetupR.string.ce_profile_pin_enter_current
                        is PinStep.New -> SetupR.string.ce_profile_pin_choose
                        is PinStep.Confirm -> SetupR.string.ce_profile_pin_repeat
                    }
                ),
            footnote =
                if (step is PinStep.Current) stringResource(SetupR.string.ce_profile_pin_forgot)
                else null,
            stepKey = step,
            onSubmit = { onAction(ProfilePinSettingsAction.OnPinSubmit(it)) },
            onDismiss = { onAction(ProfilePinSettingsAction.OnPinDismiss) },
        )
    }
}

@Composable
private fun PinRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    checked: Boolean? = null,
    destructive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.Unspecified,
                focusedContentColor = Color.Unspecified,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = ephyraFocusedBorder()),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = ephyraFocusedGlow()),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (checked != null) Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TvProfilePinLayoutPreview() {
    FindroidTheme {
        TvProfilePinLayout(
            state = ProfilePinSettingsState(user = dummyUser.copy(pinHash = "x", pinSalt = "y")),
            onAction = {},
        )
    }
}
