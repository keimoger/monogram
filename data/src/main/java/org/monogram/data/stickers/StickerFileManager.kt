package org.monogram.data.stickers

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.monogram.core.DispatcherProvider
import org.monogram.data.core.coRunCatching
import org.monogram.data.datasource.FileDataSource
import org.monogram.data.datasource.cache.StickerLocalDataSource
import org.monogram.data.infra.FileDownloadQueue
import org.monogram.data.infra.FileUpdateHandler
import org.monogram.data.mapper.isValidFilePath
import org.monogram.domain.models.StickerModel
import org.monogram.domain.models.StickerSetModel
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

class StickerFileManager(
    private val localDataSource: StickerLocalDataSource,
    private val fileDataSource: FileDataSource,
    private val fileQueue: FileDownloadQueue,
    private val fileUpdateHandler: FileUpdateHandler,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope
) {
    private val tgsCache = mutableMapOf<String, String>()
    private val filePathsCache = ConcurrentHashMap<Long, String>()

    init {
        scope.launch(dispatchers.io) {
            localDataSource.clearPaths()
        }
    }

    fun getStickerFile(fileId: Long): Flow<String?> {
        return getFile(fileId, FileDownloadQueue.DownloadType.STICKER)
    }

    fun getGifFile(fileId: Long): Flow<String?> {
        return getFile(fileId, FileDownloadQueue.DownloadType.GIF)
    }

    fun getDefaultFile(fileId: Long): Flow<String?> {
        return getFile(fileId, FileDownloadQueue.DownloadType.DEFAULT)
    }

    fun getFile(
        fileId: Long,
        downloadType: FileDownloadQueue.DownloadType
    ): Flow<String?> = flow {
        resolveAvailablePath(fileId)?.let { path ->
            Log.d(TAG, "getFile.hit fileId=$fileId type=$downloadType path=$path")
            emit(path)
            return@flow
        }

        Log.d(TAG, "getFile.miss fileId=$fileId type=$downloadType enqueue=true")
        enqueueDownload(fileId, defaultPriority(downloadType), downloadType)

        val firstPath = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            fileUpdateHandler.fileDownloadCompleted
                .filter { it.first == fileId }
                .mapNotNull { (_, path) -> path.takeIf(::isValidFilePath) }
                .first()
        }

        val resultPath = firstPath ?: resolveAvailablePath(fileId)
        if (!resultPath.isNullOrEmpty()) {
            filePathsCache[fileId] = resultPath
            Log.d(
                TAG,
                "getFile.resolved fileId=$fileId type=$downloadType path=$resultPath firstPath=${firstPath != null}"
            )
            emit(resultPath)
        } else {
            Log.d(TAG, "getFile.unresolved fileId=$fileId type=$downloadType reenqueue=true")
            enqueueDownload(fileId, defaultPriority(downloadType), downloadType)
        }
    }

    fun prefetchStickers(stickers: List<StickerModel>) {
        scope.launch(dispatchers.default) {
            stickers.take(PREFETCH_COUNT).forEach { sticker ->
                if (!isStickerFileAvailable(sticker.id)) {
                    enqueueDownload(
                        sticker.id,
                        PREFETCH_PRIORITY,
                        FileDownloadQueue.DownloadType.STICKER
                    )
                }
            }
        }
    }

    suspend fun verifyStickerSet(set: StickerSetModel) {
        val missing = mutableListOf<Long>()
        for (sticker in set.stickers) {
            if (!isStickerFileAvailable(sticker.id)) {
                missing += sticker.id
            }
        }

        if (missing.isEmpty()) return

        Log.d(TAG, "verifyStickerSet(${set.id}): missing ${missing.size}/${set.stickers.size}")
        missing.forEach { stickerId ->
            enqueueDownload(stickerId, VERIFY_SET_PRIORITY, FileDownloadQueue.DownloadType.STICKER)
        }
    }

    suspend fun verifyInstalledStickerSets(sets: List<StickerSetModel>) {
        var requeued = 0

        for (set in sets) {
            for (sticker in set.stickers) {
                if (requeued >= MAX_VERIFY_PER_PASS) break
                if (isStickerFileAvailable(sticker.id)) continue

                enqueueDownload(
                    sticker.id,
                    VERIFY_PASS_PRIORITY,
                    FileDownloadQueue.DownloadType.STICKER
                )
                requeued++
            }
            if (requeued >= MAX_VERIFY_PER_PASS) break
        }

        if (requeued > 0) {
            Log.d(TAG, "verifyInstalledStickerSets: re-enqueued $requeued stickers")
        }
    }

    suspend fun getTgsJson(path: String): String? = withContext(dispatchers.io) {
        tgsCache[path]?.let { return@withContext it }
        coRunCatching {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return@withContext null

            GZIPInputStream(FileInputStream(file))
                .bufferedReader()
                .use { it.readText() }
                .also { tgsCache[path] = it }
        }.getOrNull()
    }

    fun clearCache() {
        tgsCache.clear()
        filePathsCache.clear()
        scope.launch {
            localDataSource.clearPaths()
        }
    }

    private suspend fun isStickerFileAvailable(stickerId: Long): Boolean {
        return resolveAvailablePath(stickerId) != null
    }

    private suspend fun resolveAvailablePath(fileId: Long): String? {
        val authoritativeFile = withContext(dispatchers.io) {
            fileDataSource.getFile(fileId.toInt())
        } ?: fileQueue.getCachedFile(fileId.toInt())

        val liveCompletedPath = authoritativeFile?.local?.path?.takeIf {
            authoritativeFile.local.isDownloadingCompleted && isValidFilePath(it)
        }
        val liveKnownPath = authoritativeFile?.local?.path?.takeUnless { it.isNullOrBlank() }
        val memoryPath = filePathsCache[fileId]
        val dbPath = localDataSource.getPath(fileId)
        val completedPath = fileUpdateHandler.fileDownloadCompleted
            .replayCache
            .firstOrNull { it.first == fileId && isValidFilePath(it.second) }
            ?.second

        val resolution = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                liveCompletedPath = liveCompletedPath,
                liveKnownPath = liveKnownPath,
                memoryPath = memoryPath,
                dbPath = dbPath,
                replayedPath = completedPath
            )
        )

        if (resolution.dropMemoryPath) {
            filePathsCache.remove(fileId)
        }
        if (resolution.dropDbPath) {
            localDataSource.deletePath(fileId)
        }
        Log.d(
            TAG,
            "resolve fileId=$fileId tdFileId=${authoritativeFile?.id} " +
                    "remoteId=${authoritativeFile?.remote?.id} remoteUniqueId=${authoritativeFile?.remote?.uniqueId} " +
                    "liveCompletedPath=$liveCompletedPath liveKnownPath=$liveKnownPath " +
                    "memoryPath=$memoryPath dbPath=$dbPath replayedPath=$completedPath " +
                    "selectedPath=${resolution.selectedPath} dropMemory=${resolution.dropMemoryPath} dropDb=${resolution.dropDbPath}"
        )
        val selectedPath = resolution.selectedPath
        if (!selectedPath.isNullOrEmpty()) {
            filePathsCache[fileId] = selectedPath
            return selectedPath
        }

        return null
    }

    private fun enqueueDownload(
        fileId: Long,
        priority: Int,
        downloadType: FileDownloadQueue.DownloadType
    ) {
        fileQueue.enqueue(fileId.toInt(), priority, downloadType)
    }

    private fun defaultPriority(downloadType: FileDownloadQueue.DownloadType): Int {
        return when (downloadType) {
            FileDownloadQueue.DownloadType.STICKER -> STICKER_DOWNLOAD_PRIORITY
            FileDownloadQueue.DownloadType.GIF -> GIF_DOWNLOAD_PRIORITY
            FileDownloadQueue.DownloadType.DEFAULT -> DEFAULT_DOWNLOAD_PRIORITY
            FileDownloadQueue.DownloadType.VIDEO -> DEFAULT_DOWNLOAD_PRIORITY
            FileDownloadQueue.DownloadType.VIDEO_NOTE -> DEFAULT_DOWNLOAD_PRIORITY
        }
    }

    companion object {
        private const val TAG = "StickerFileManager"
        private const val DOWNLOAD_TIMEOUT_MS = 90_000L
        private const val STICKER_DOWNLOAD_PRIORITY = 32
        private const val GIF_DOWNLOAD_PRIORITY = 24
        private const val DEFAULT_DOWNLOAD_PRIORITY = 16
        private const val PREFETCH_PRIORITY = 16
        private const val VERIFY_SET_PRIORITY = 32
        private const val VERIFY_PASS_PRIORITY = 8
        private const val PREFETCH_COUNT = 20
        private const val MAX_VERIFY_PER_PASS = 20
    }
}