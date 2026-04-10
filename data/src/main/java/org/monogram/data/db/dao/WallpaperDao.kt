package org.monogram.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.monogram.data.db.model.WallpaperEntity

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY isDefault DESC, id ASC")
    fun observeWallpapers(): Flow<List<WallpaperEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallpapers(wallpapers: List<WallpaperEntity>)

    @Query("DELETE FROM wallpapers WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<Long>)

    @Query("DELETE FROM wallpapers")
    suspend fun clearAll()
}
