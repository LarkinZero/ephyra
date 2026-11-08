package dev.jdtech.jellyfin.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "users",
    foreignKeys =
        [
            ForeignKey(
                entity = Server::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("serverId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class User(
    @PrimaryKey val id: UUID,
    val name: String,
    @ColumnInfo(index = true) val serverId: String,
    val accessToken: String? = null,
    /** Tag of the user's Jellyfin profile picture; null when they have none. */
    val primaryImageTag: String? = null,
    /**
     * CE (#299): the profile PIN, as a PBKDF2 hash with [pinSalt]. Local only, never sent to the
     * server; null when the profile has no PIN.
     */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    /** Whether a profile with a PIN also asks for it when the app starts in it (not only on a switch). */
    @ColumnInfo(defaultValue = "1") val pinOnStart: Boolean = true,
)

/** CE (#299): the profile is locked behind a local PIN. */
val User.hasPin: Boolean
    get() = pinHash != null
