package dev.codex.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageGenerationDao {
    @Query("SELECT * FROM image_generations ORDER BY createdAt DESC, id DESC")
    fun observeGenerations(): Flow<List<ImageGenerationEntity>>

    @Query("SELECT * FROM image_generations WHERE id = :id LIMIT 1")
    suspend fun getGeneration(id: Long): ImageGenerationEntity?

    @Query("SELECT * FROM image_generations WHERE status IN ('queued', 'running') ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingGenerations(): List<ImageGenerationEntity>

    @Insert
    suspend fun insertGeneration(entity: ImageGenerationEntity): Long

    @Update
    suspend fun updateGeneration(entity: ImageGenerationEntity)

    @Query("DELETE FROM image_generations WHERE id = :id")
    suspend fun deleteGeneration(id: Long)
}
