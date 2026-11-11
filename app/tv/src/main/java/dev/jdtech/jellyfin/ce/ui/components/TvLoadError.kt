package dev.jdtech.jellyfin.ce.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.ce.ui.focus.RestoreFocus
import dev.jdtech.jellyfin.ce.ui.focus.focusPosition
import dev.jdtech.jellyfin.ce.ui.focus.rememberFocusPosition
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.LoadErrorText

internal const val TV_LOAD_ERROR_TAG = "ce_tv_load_error"
internal const val TV_LOAD_ERROR_RETRY_TAG = "ce_tv_load_error_retry"

/**
 * CE (#405): a page that failed to load, with the reason in plain words (#377) and Retry. Retry
 * takes focus each time the error appears, also after a Retry that failed again.
 */
@Composable
fun TvLoadError(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val focusPosition = rememberFocusPosition()
    val reason = LoadErrorText.reason(error).asString()
    val text =
        if (LoadErrorText.isConnectionError(error)) {
            reason + "\n" + stringResource(CoreR.string.error_loading_data_body)
        } else {
            reason
        }
    Column(
        modifier = modifier.padding(TvSafeArea.horizontal).testTag(TV_LOAD_ERROR_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            modifier =
                Modifier.focusPosition(focusPosition, 0, 0).testTag(TV_LOAD_ERROR_RETRY_TAG),
        ) {
            Text(text = stringResource(CoreR.string.retry))
        }
    }
    RestoreFocus(position = focusPosition, ready = true)
}
