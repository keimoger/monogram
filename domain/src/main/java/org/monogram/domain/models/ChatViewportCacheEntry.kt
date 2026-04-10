package org.monogram.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatViewportCacheEntry(
    val anchorMessageId: Long? = null,
    val anchorOffsetPx: Int = 0,
    val atBottom: Boolean = true
)
