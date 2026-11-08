package dev.jdtech.jellyfin.setup.presentation.pin

import dev.jdtech.jellyfin.models.ProfileLockType
import java.util.UUID

/** CE (#299): why the last PIN (or, #330, pattern) was not taken. */
enum class PinError {
    /** Not the profile's PIN. */
    WRONG,
    /** The second entry of a new PIN did not match the first. */
    MISMATCH,
}

/**
 * CE (#299): a PIN prompt on screen. [errorCount] goes up with every rejected entry, so the dialog
 * can shake and clear its digits even when the same [error] repeats.
 *
 * [lockType] (#330) is what to ask for: digits, or a pattern on the 3×3 grid. TV only ever asks for
 * a PIN.
 */
data class PinPrompt(
    val userId: UUID,
    val userName: String,
    val error: PinError? = null,
    val errorCount: Int = 0,
    val lockType: ProfileLockType = ProfileLockType.PIN,
) {
    fun rejected(error: PinError): PinPrompt = copy(error = error, errorCount = errorCount + 1)
}
