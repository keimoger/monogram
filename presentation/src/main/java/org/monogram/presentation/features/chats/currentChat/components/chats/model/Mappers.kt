package org.monogram.presentation.features.chats.currentChat.components.chats.model

import org.monogram.domain.models.MessageEntity
import org.monogram.domain.models.MessageEntityType

/**
 * Gets text part for current [MessageEntity]
 **/
internal infix fun String.blockFor(entity: MessageEntity): String =
    safeSubstring(entity.offset, entity.offset.toLong() + entity.length.toLong())

private fun String.safeSubstring(start: Int, end: Long): String {
    if (isEmpty()) return ""
    val safeStart = start.coerceIn(0, length)
    val safeEnd = end.coerceIn(safeStart.toLong(), length.toLong()).toInt()
    return substring(safeStart, safeEnd)
}

/**
 * Checks if [MessageEntityType] is block element
 **/
internal fun MessageEntityType.isBlockElement(): Boolean {
    return when (this) {
        is MessageEntityType.Pre,
        is MessageEntityType.BlockQuote,
        is MessageEntityType.BlockQuoteExpandable -> true
        else -> false
    }
}