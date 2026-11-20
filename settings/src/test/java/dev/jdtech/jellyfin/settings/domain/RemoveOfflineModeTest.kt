package dev.jdtech.jellyfin.settings.domain

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

/** #281: the stored value of the removed Offline Mode setting goes, and nothing else does. */
class RemoveOfflineModeTest {
    private val stored = mutableMapOf<String, Any?>()

    /** SharedPreferences over [stored]: contains, and an editor that removes and applies. */
    private val sharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) {
            _,
            method,
            args ->
            when (method.name) {
                "contains" -> stored.containsKey(args!![0])
                "edit" -> editor()
                else -> error("not used: ${method.name}")
            }
        } as SharedPreferences

    private fun editor(): SharedPreferences.Editor {
        val removed = mutableSetOf<String>()
        lateinit var editor: SharedPreferences.Editor
        editor =
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "remove" -> {
                        removed += args!![0] as String
                        editor
                    }
                    "apply" -> removed.forEach { stored.remove(it) }
                    else -> error("not used: ${method.name}")
                }
            } as SharedPreferences.Editor
        return editor
    }

    @Test
    fun `Offline Mode switched on is removed`() {
        stored["pref_offline_mode"] = true
        stored["pref_player_backend"] = "mpv"

        AppPreferences(sharedPreferences).removeOfflineMode()

        assertFalse("pref_offline_mode" in stored, message = "the setting's value is still stored")
        assertEquals(mapOf<String, Any?>("pref_player_backend" to "mpv"), stored)
    }

    @Test
    fun `without a stored value nothing is written`() {
        stored["pref_player_backend"] = "mpv"

        // The editor would fail on anything but remove and apply; edit() must not even be asked.
        AppPreferences(
                Proxy.newProxyInstance(
                    javaClass.classLoader,
                    arrayOf(SharedPreferences::class.java),
                ) { _, method, args ->
                    when (method.name) {
                        "contains" -> stored.containsKey(args!![0])
                        else -> error("not expected: ${method.name}")
                    }
                } as SharedPreferences
            )
            .removeOfflineMode()

        assertEquals(mapOf<String, Any?>("pref_player_backend" to "mpv"), stored)
    }
}
