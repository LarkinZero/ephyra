package dev.jdtech.jellyfin.presentation.components

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.utils.LoadErrorText
import org.jellyfin.sdk.api.client.exception.InvalidStatusException

/**
 * Why a screen failed to load, in plain words (#377). The stack trace stays out of sight: Share
 * sends it, with tokens blanked out, for a bug report. [onRetry] adds Retry, which closes the
 * dialog and does what the screen's own retry does.
 */
@Composable
fun ErrorDialog(
    exception: Throwable,
    onDismissRequest: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reason = LoadErrorText.reason(exception).asString()
    val text =
        if (LoadErrorText.isConnectionError(exception)) {
            reason + "\n\n" + stringResource(CoreR.string.error_loading_data_body)
        } else {
            reason
        }

    val share = {
        val sendIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, LoadErrorText.shareText(exception))
                type = "text/plain"
            }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.error_loading_data)) },
        text = { Text(text = text) },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            if (onRetry != null) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onRetry()
                    }
                ) {
                    Text(stringResource(CoreR.string.retry))
                }
            } else {
                TextButton(onClick = onDismissRequest) { Text(stringResource(CoreR.string.close)) }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = share) { Text(stringResource(CoreR.string.share)) }
                if (onRetry != null) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(CoreR.string.close))
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun ErrorDialogPreview() {
    FindroidTheme {
        ErrorDialog(
            exception = InvalidStatusException(503, null),
            onDismissRequest = {},
            onRetry = {},
        )
    }
}
