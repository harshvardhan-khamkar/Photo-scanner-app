package com.weddingmemory.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FrameEntity — Room representation of a Frame.
 *
 * Foreign-keyed to [AlbumEntity]: deleting an album cascade-deletes its frames.
 * Indexed on [albumId] so getFrames(albumId) is an O(1) index scan.
 */
@Entity(
    tableName = "frames",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["albumId"])],
)
data class FrameEntity(
    @PrimaryKey
    val id: String,
    val albumId: String,
    val index: Int,
    /** Opaque image signature — base64 or hash used by the recognition engine. */
    val imageSignature: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val startTimeMs: Long = 0,
)
