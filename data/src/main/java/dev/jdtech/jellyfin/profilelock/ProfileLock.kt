package dev.jdtech.jellyfin.profilelock

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.hasPin
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * CE (#299): the local PIN of a profile, and which profile this process has unlocked.
 *
 * The PIN is a salted hash on the user's row ([User.pinHash]); it never leaves the device. A profile
 * stays unlocked until the app switches to another profile or the process ends. Removing the user or
 * its server, or signing in again (which rewrites the row), removes the PIN.
 *
 * One instance per process: `core` provides it as a singleton.
 */
class ProfileLock(
    private val database: ServerDatabaseDao,
    /** Where the deliberately slow hashing runs. */
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _unlockedUserIds = MutableStateFlow<Set<UUID>>(emptySet())

    /**
     * The profiles unlocked, or switched to without a PIN, in this process. Only the last one counts
     * for a switch ([needsPinToSwitch]); the earlier ones stay in the set so the start lock does not
     * flash on the old profile while the database catches up with a switch.
     */
    val unlockedUserIds: StateFlow<Set<UUID>> = _unlockedUserIds.asStateFlow()

    @Volatile private var lastUnlockedUserId: UUID? = null

    suspend fun hasPin(userId: UUID): Boolean = database.getUser(userId)?.hasPin == true

    /**
     * Sets or replaces the PIN. The profile counts as unlocked (before the row changes, so the start
     * lock never shows): its owner just typed the PIN.
     */
    suspend fun setPin(userId: UUID, pin: String) {
        require(ProfilePinHasher.isValid(pin)) { "A PIN is 4 to 6 digits" }
        val salt = ProfilePinHasher.newSalt()
        val hash = withContext(hashDispatcher) { ProfilePinHasher.hash(pin, salt) }
        unlock(userId)
        database.updateUserPin(userId, hash, salt)
    }

    suspend fun clearPin(userId: UUID) {
        database.updateUserPin(userId, null, null)
    }

    /** Changed from inside the profile, so it stays open when the start lock is turned on. */
    suspend fun setPinOnStart(userId: UUID, pinOnStart: Boolean) {
        unlock(userId)
        database.updateUserPinOnStart(userId, pinOnStart)
    }

    /** True when [pin] is the profile's PIN, or the profile has none. */
    suspend fun verifyPin(userId: UUID, pin: String): Boolean {
        val user = database.getUser(userId) ?: return false
        val hash = user.pinHash ?: return true
        val salt = user.pinSalt ?: return false
        return withContext(hashDispatcher) { ProfilePinHasher.verify(pin, salt, hash) }
    }

    /** Whether switching to [userId] has to ask for its PIN first. */
    suspend fun needsPinToSwitch(userId: UUID): Boolean =
        userId != lastUnlockedUserId && hasPin(userId)

    /** Marks [userId] as the unlocked profile; switching to any other profile asks again. */
    fun unlock(userId: UUID) {
        lastUnlockedUserId = userId
        _unlockedUserIds.value += userId
    }

    companion object {
        /** Whether [user], the profile the app is in, has to be unlocked before Home shows. */
        fun isLockedOnStart(user: User?, unlockedUserIds: Set<UUID>): Boolean =
            user != null && user.hasPin && user.pinOnStart && user.id !in unlockedUserIds
    }
}
