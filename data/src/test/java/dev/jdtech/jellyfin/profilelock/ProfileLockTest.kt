package dev.jdtech.jellyfin.profilelock

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.User
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLockTest {
    private val alice = User(id = UUID.randomUUID(), name = "Alice", serverId = "s")
    private val bob = User(id = UUID.randomUUID(), name = "Bob", serverId = "s")
    private val users = mutableMapOf(alice.id to alice, bob.id to bob)
    private val lock = ProfileLock(fakeDao(users))

    @Test
    fun `set, verify and clear a PIN`() = runBlocking {
        assertFalse(lock.hasPin(alice.id))

        lock.setPin(alice.id, "1234")

        assertTrue(lock.hasPin(alice.id))
        assertNotNull(users.getValue(alice.id).pinSalt)
        assertFalse(users.getValue(alice.id).pinHash!!.contains("1234"))
        assertTrue(lock.verifyPin(alice.id, "1234"))
        assertFalse(lock.verifyPin(alice.id, "4321"))

        lock.clearPin(alice.id)

        assertFalse(lock.hasPin(alice.id))
        assertNull(users.getValue(alice.id).pinHash)
        assertNull(users.getValue(alice.id).pinSalt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a PIN of three digits is refused`() = runBlocking { lock.setPin(alice.id, "123") }

    @Test
    fun `setting a PIN unlocks that profile`() = runBlocking {
        lock.setPin(alice.id, "1234")

        assertEquals(setOf(alice.id), lock.unlockedUserIds.value)
        assertFalse(lock.needsPinToSwitch(alice.id))
    }

    @Test
    fun `switching to another profile locks the first one again`() = runBlocking {
        lock.setPin(alice.id, "1234")
        lock.unlock(bob.id)

        assertTrue(lock.needsPinToSwitch(alice.id))
        assertFalse(lock.needsPinToSwitch(bob.id))
        // Bob is current now, and his profile is open.
        assertFalse(ProfileLock.isLockedOnStart(users[bob.id], lock.unlockedUserIds.value))
    }

    @Test
    fun `a new process starts locked`() = runBlocking {
        lock.setPin(alice.id, "1234")
        val restarted = ProfileLock(fakeDao(users))

        assertTrue(restarted.needsPinToSwitch(alice.id))
        assertTrue(ProfileLock.isLockedOnStart(users[alice.id], restarted.unlockedUserIds.value))
    }

    @Test
    fun `the start lock follows the profile's setting`() = runBlocking {
        lock.setPin(alice.id, "1234")
        lock.setPinOnStart(alice.id, false)

        assertFalse(ProfileLock.isLockedOnStart(users[alice.id], emptySet()))
        assertFalse(ProfileLock.isLockedOnStart(users[bob.id], emptySet()))
        assertFalse(ProfileLock.isLockedOnStart(null, emptySet()))
    }

    @Test
    fun `a profile without a PIN verifies anything`() = runBlocking {
        assertTrue(lock.verifyPin(bob.id, "0000"))
        assertFalse(lock.verifyPin(UUID.randomUUID(), "0000"))
    }

    companion object {
        /** Just the user calls [ProfileLock] makes, on an in-memory table. */
        fun fakeDao(users: MutableMap<UUID, User>): ServerDatabaseDao =
            Proxy.newProxyInstance(
                ServerDatabaseDao::class.java.classLoader,
                arrayOf(ServerDatabaseDao::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getUser" -> users[args[0] as UUID]
                    "updateUserPin" -> {
                        val id = args[0] as UUID
                        users[id]?.let {
                            users[id] = it.copy(pinHash = args[1] as String?, pinSalt = args[2] as String?)
                        }
                        Unit
                    }
                    "updateUserPinOnStart" -> {
                        val id = args[0] as UUID
                        users[id]?.let { users[id] = it.copy(pinOnStart = args[1] as Boolean) }
                        Unit
                    }
                    "toString" -> "FakeServerDatabaseDao"
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as ServerDatabaseDao
    }
}
