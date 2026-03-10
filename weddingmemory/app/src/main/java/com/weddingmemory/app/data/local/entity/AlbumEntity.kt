package com.weddingmemory.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AlbumEntity — Room representation of an Album.
 * Lives entirely in the data layer; the domain layer never sees this class.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val code: String,
    val name: String,
    val coverImageUrl: String,
    /** Serialised status string — e.g. "READY", "LOCKED", "INITIALIZING". */
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val totalFrames: Int,
)
