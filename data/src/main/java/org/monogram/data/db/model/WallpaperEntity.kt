package org.monogram.data.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey val id: Long,
    val slug: String,
    val title: String,
    val type: String,
    val pattern: Boolean,
    val documentId: Long,
    val thumbnailFileId: Int?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val thumbnailLocalPath: String?,
    val backgroundColor: Int?,
    val secondBackgroundColor: Int?,
    val thirdBackgroundColor: Int?,
    val fourthBackgroundColor: Int?,
    val intensity: Int?,
    val rotation: Int?,
    val isInverted: Boolean?,
    val settingsIsMoving: Boolean?,
    val settingsIsBlurred: Boolean?,
    val themeName: String?,
    val isDownloaded: Boolean,
    val localPath: String?,
    val isDefault: Boolean
)
