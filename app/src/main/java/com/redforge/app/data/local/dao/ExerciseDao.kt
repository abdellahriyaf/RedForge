package com.redforge.app.data.local.dao

import androidx.room.*
import com.redforge.app.data.local.entities.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup AND isArchived = 0 ORDER BY name ASC")
    fun observeByMuscleGroup(muscleGroup: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Exercise?

    @Upsert
    suspend fun upsert(exercise: Exercise)

    @Upsert
    suspend fun upsertAll(exercises: List<Exercise>)

    @Query("UPDATE exercises SET isArchived = 1 WHERE id = :exerciseId")
    suspend fun archive(exerciseId: Long)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllOnce(): List<Exercise>
}
