package dev.jdtech.jellyfin.setup.presentation.pin

import java.util.UUID

/** CE (#299): why the last PIN was not taken. */
enum class PinError {
    /** Not the profile's PIN. */
    WRONG,
    /** The second entry of a new PIN did not match the first. */
    MISMATCH,
}

/**
 * CE (#299): a PIN prompt on screen. [errorCount] goes up with every rejected entry, so the dialog
 * can shake and clear its digits even when the same [error] repeats.
 */
data class PinPrompt(
    val userId: UUID,
    val userName: String,
    val error: PinError? = null,
    val errorCount: Int = 0,
) {
    fun rejected(error: PinError): PinPrompt = copy(error = error, errorCount = errorCount + 1)
}
