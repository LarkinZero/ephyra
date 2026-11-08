package dev.jdtech.jellyfin.presentation.setup.pin

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.pin.PinStep
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsAction
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsEvent
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsState
import dev.jdtech.jellyfin.setup.presentation.pin.ProfilePinSettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents

/** CE (#299): Settings → Profile PIN for the signed-in profile. */
@Composable
fun ProfilePinScreen(
    navigateBack: () -> Unit,
    viewModel: ProfilePinSettingsViewModel = hiltViewModel(),
) {
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

    ProfilePinLayout(
        state = state,
        onAction = { action ->
            if (action is ProfilePinSettingsAction.OnBackClick) navigateBack()
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePinLayout(
    state: ProfilePinSettingsState,
    onAction: (ProfilePinSettingsAction) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(SetupR.string.ce_profile_pin)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(ProfilePinSettingsAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .testTag("ce_profile_pin_settings")
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (state.hasPin) SetupR.string.ce_profile_pin_status_on
                            else SetupR.string.ce_profile_pin_status_off
                        )
                    )
                },
                supportingContent = { Text(stringResource(SetupR.string.ce_profile_pin_summary)) },
                overlineContent = state.user?.let { user -> { Text(user.name) } },
                leadingContent = {
                    Icon(
                        painter =
                            painterResource(
                                if (state.hasPin) CoreR.drawable.ic_lock
                                else CoreR.drawable.ic_unlock
                            ),
                        contentDescription = null,
                    )
                },
            )
            if (state.hasPin) {
                PinActionItem(SetupR.string.ce_profile_pin_change) {
                    onAction(ProfilePinSettingsAction.OnChangeClick)
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(SetupR.string.ce_profile_pin_ask_on_start))
                    },
                    supportingContent = {
                        Text(stringResource(SetupR.string.ce_profile_pin_ask_on_start_summary))
                    },
                    trailingContent = { Switch(checked = state.pinOnStart, onCheckedChange = null) },
                    modifier =
                        Modifier.clickable {
                            onAction(ProfilePinSettingsAction.OnPinOnStartChange(!state.pinOnStart))
                        },
                )
                PinActionItem(SetupR.string.ce_profile_pin_remove, destructive = true) {
                    onAction(ProfilePinSettingsAction.OnRemoveClick)
                }
            } else {
                PinActionItem(SetupR.string.ce_profile_pin_set) {
                    onAction(ProfilePinSettingsAction.OnSetClick)
                }
            }
        }
    }

    val prompt = state.prompt
    val step = state.step
    if (prompt != null && step != null) {
        PinDialog(
            prompt = prompt,
            title = stringResource(SetupR.string.ce_profile_pin),
            message = stringResource(step.messageRes()),
            footnote =
                if (step is PinStep.Current) stringResource(SetupR.string.ce_profile_pin_forgot)
                else null,
            stepKey = step,
            onSubmit = { onAction(ProfilePinSettingsAction.OnPinSubmit(it)) },
            onDismiss = { onAction(ProfilePinSettingsAction.OnPinDismiss) },
        )
    }
}

fun PinStep.messageRes(): Int =
    when (this) {
        is PinStep.Current -> SetupR.string.ce_profile_pin_enter_current
        is PinStep.New -> SetupR.string.ce_profile_pin_choose
        is PinStep.Confirm -> SetupR.string.ce_profile_pin_repeat
    }

@Composable
private fun PinActionItem(text: Int, destructive: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(text)) },
        colors =
            if (destructive) {
                ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error)
            } else {
                ListItemDefaults.colors()
            },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview
@Composable
private fun ProfilePinLayoutPreview() {
    FindroidTheme {
        ProfilePinLayout(
            state = ProfilePinSettingsState(user = dummyUser.copy(pinHash = "x", pinSalt = "y")),
            onAction = {},
        )
    }
}
