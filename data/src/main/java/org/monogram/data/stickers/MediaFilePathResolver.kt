package org.monogram.data.stickers

import org.monogram.data.mapper.isValidFilePath

internal object MediaFilePathResolver {
    data class CandidatePaths(
        val liveCompletedPath: String? = null,
        val liveKnownPath: String? = null,
        val memoryPath: String? = null,
        val dbPath: String? = null,
        val replayedPath: String? = null
    )

    data class Resolution(
        val selectedPath: String? = null,
        val dropMemoryPath: Boolean = false,
        val dropDbPath: Boolean = false
    )

    fun resolve(candidates: CandidatePaths): Resolution {
        val liveCompletedPath = candidates.liveCompletedPath.takeIf(::isValidFilePath)
        if (liveCompletedPath != null) {
            return Resolution(
                selectedPath = liveCompletedPath,
                dropMemoryPath = shouldDrop(candidates.memoryPath, candidates.liveKnownPath),
                dropDbPath = shouldDrop(candidates.dbPath, candidates.liveKnownPath)
            )
        }

        val liveKnownPath = candidates.liveKnownPath.takeUnless { it.isNullOrBlank() }
        val memoryPath = candidates.memoryPath.takeIf { isAllowedCandidate(it, liveKnownPath) }
        val replayedPath = candidates.replayedPath.takeIf { isAllowedCandidate(it, liveKnownPath) }

        return Resolution(
            selectedPath = memoryPath ?: replayedPath,
            dropMemoryPath = shouldDrop(candidates.memoryPath, liveKnownPath),
            dropDbPath = candidates.dbPath != null
        )
    }

    private fun isAllowedCandidate(path: String?, liveKnownPath: String?): Boolean {
        if (!isValidFilePath(path)) return false
        return liveKnownPath == null || liveKnownPath == path
    }

    private fun shouldDrop(path: String?, liveKnownPath: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (!isValidFilePath(path)) return true
        return liveKnownPath != null && liveKnownPath != path
    }
}
