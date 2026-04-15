package org.monogram.data.datasource.remote

import org.drinkless.tdlib.TdApi
import org.monogram.data.core.coRunCatching
import org.monogram.data.gateway.TelegramGateway
import org.monogram.domain.models.GifModel

class TdGifRemoteSource(
    private val gateway: TelegramGateway
) : GifRemoteSource {
    @Volatile
    private var gifSearchBot: GifSearchBot? = null

    override suspend fun getSavedGifs(): List<GifModel> {
        return coRunCatching {
            val recentGifs = coRunCatching {
                gateway.execute(TdApi.GetSavedAnimations())
                    .animations
                    .map { animation ->
                        GifModel(
                            id = animation.animation.remote?.id?.takeIf { it.isNotEmpty() }
                                ?: animation.animation.id.toString(),
                            inlineQueryId = null,
                            fileId = animation.animation.id.toLong(),
                            thumbFileId = animation.thumbnail?.file?.id?.toLong(),
                            width = animation.width,
                            height = animation.height
                        )
                    }
            }.getOrDefault(emptyList())

            if (recentGifs.isNotEmpty()) {
                return@coRunCatching recentGifs
            }

            searchGifs("")
        }.getOrDefault(emptyList())
    }

    override suspend fun addSavedGif(path: String) {
        coRunCatching {
            gateway.execute(TdApi.AddSavedAnimation(TdApi.InputFileLocal(path)))
        }
    }

    override suspend fun searchGifs(query: String): List<GifModel> {
        return coRunCatching {
            val searchBot = resolveGifSearchBot() ?: return@coRunCatching emptyList()

            val results = gateway.execute(
                TdApi.GetInlineQueryResults(
                    searchBot.userId,
                    searchBot.chatId,
                    null,
                    query.trim(),
                    ""
                )
            )

            results.results
                .mapNotNull { item ->
                    if (item !is TdApi.InlineQueryResultAnimation) return@mapNotNull null

                    GifModel(
                        id = item.id,
                        inlineQueryId = results.inlineQueryId,
                        fileId = item.animation.animation.id.toLong(),
                        thumbFileId = item.animation.thumbnail?.file?.id?.toLong(),
                        width = item.animation.width,
                        height = item.animation.height
                    )
                }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private suspend fun resolveGifSearchBot(): GifSearchBot? {
        gifSearchBot?.let { return it }

        val chat = gateway.execute(TdApi.SearchPublicChat("gif"))
        val type = chat.type as? TdApi.ChatTypePrivate ?: return null
        return GifSearchBot(chatId = chat.id, userId = type.userId).also {
            gifSearchBot = it
        }
    }

    private data class GifSearchBot(
        val chatId: Long,
        val userId: Long
    )
}
