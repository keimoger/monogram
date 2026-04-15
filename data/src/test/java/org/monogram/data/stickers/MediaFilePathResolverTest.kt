package org.monogram.data.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaFilePathResolverTest {

    @Test
    fun `live completed path wins over cached paths`() {
        val liveFile = createTempFile()
        val cachedFile = createTempFile()

        val result = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                liveCompletedPath = liveFile.absolutePath,
                memoryPath = cachedFile.absolutePath,
                dbPath = cachedFile.absolutePath
            )
        )

        assertEquals(liveFile.absolutePath, result.selectedPath)
    }

    @Test
    fun `stale cached paths are dropped when live path differs`() {
        val staleFile = createTempFile()

        val result = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                liveKnownPath = "Z:/live/new-path.mp4",
                memoryPath = staleFile.absolutePath,
                dbPath = staleFile.absolutePath
            )
        )

        assertEquals(null, result.selectedPath)
        assertTrue(result.dropMemoryPath)
        assertTrue(result.dropDbPath)
    }

    @Test
    fun `db path is ignored when live path is absent`() {
        val dbFile = createTempFile()

        val result = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                dbPath = dbFile.absolutePath
            )
        )

        assertEquals(null, result.selectedPath)
        assertTrue(result.dropDbPath)
    }

    @Test
    fun `replayed path is used when live path is absent`() {
        val replayedFile = createTempFile()

        val result = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                replayedPath = replayedFile.absolutePath
            )
        )

        assertEquals(replayedFile.absolutePath, result.selectedPath)
        assertFalse(result.dropDbPath)
    }

    @Test
    fun `replayed path is ignored when live path differs`() {
        val replayedFile = createTempFile()

        val result = MediaFilePathResolver.resolve(
            MediaFilePathResolver.CandidatePaths(
                liveKnownPath = "Z:/live/another-path.mp4",
                replayedPath = replayedFile.absolutePath
            )
        )

        assertEquals(null, result.selectedPath)
    }

    private fun createTempFile(): File {
        return kotlin.io.path.createTempFile().toFile().apply {
            deleteOnExit()
        }
    }
}
