package org.monogram.data.repository

import androidx.media3.datasource.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.monogram.data.datasource.FileDataSource
import org.monogram.data.datasource.TelegramStreamingDataSource
import org.monogram.data.infra.FileObserverHub
import org.monogram.domain.repository.PlayerDataSourceFactory
import org.monogram.domain.repository.StreamingRepository

class StreamingRepositoryImpl(
    private val fileDataSource: FileDataSource,
    private val fileObserverHub: FileObserverHub
) : StreamingRepository, PlayerDataSourceFactory {

    override fun createPayload(fileId: Int): DataSource.Factory {
        return TelegramStreamingDataSource.Factory(fileDataSource, fileId)
    }

    override fun getDownloadProgress(fileId: Int): Flow<Float> {
        val cachedProgress = fileObserverHub.getCachedFile(fileId)?.let { file ->
            if (file.size > 0) file.local.downloadedSize.toFloat() / file.size.toFloat() else 0f
        } ?: 0f

        return fileObserverHub.observeFile(fileId)
            .map { it.downloadProgress.coerceIn(0f, 1f) }
            .onStart { emit(cachedProgress) }
            .distinctUntilChanged()
    }
}