package com.redforge.app.data.repository

import com.redforge.app.data.local.dao.ExerciseDao
import com.redforge.app.data.local.entities.Exercise
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    fun observeByMuscleGroup(group: String): Flow<List<Exercise>> = dao.observeByMuscleGroup(group)
    fun observeById(id: Long): Flow<Exercise?> = dao.observeById(id)
    suspend fun getById(id: Long): Exercise? = dao.getById(id)
    suspend fun getByName(name: String): Exercise? = dao.getByName(name)
    suspend fun getAllOnce(): List<Exercise> = dao.getAllOnce()
    suspend fun save(exercise: Exercise): Long = dao.upsert(exercise)
    suspend fun saveAll(exercises: List<Exercise>) = dao.upsertAll(exercises)
    suspend fun delete(exercise: Exercise) = dao.archive(exercise.id)
}
