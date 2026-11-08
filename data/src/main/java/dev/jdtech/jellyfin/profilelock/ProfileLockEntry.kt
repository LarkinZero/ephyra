package dev.jdtech.jellyfin.profilelock

import dev.jdtech.jellyfin.models.ProfileLockType

/** CE (#330): what someone entered to unlock a profile: digits, or a drawn pattern. */
sealed interface ProfileLockEntry {
    val type: ProfileLockType

    /** Whether the entry has the shape of its type: 4 to 6 digits, or 4 or more distinct dots. */
    val isValid: Boolean

    /** The text that is hashed: the digits, or the [ProfilePattern.serialize]d dots. */
    val secret: String

    data class Pin(val pin: String) : ProfileLockEntry {
        override val type: ProfileLockType
            get() = ProfileLockType.PIN

        override val isValid: Boolean
            get() = ProfilePinHasher.isValid(pin)

        override val secret: String
            get() = pin
    }

    data class Pattern(val dots: List<Int>) : ProfileLockEntry {
        override val type: ProfileLockType
            get() = ProfileLockType.PATTERN

        override val isValid: Boolean
            get() = ProfilePattern.isValid(dots)

        override val secret: String
            get() = ProfilePattern.serialize(dots)
    }
}
