package com.redforge.app.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.redforge.app.data.local.db.RedForgeDatabase
import com.redforge.app.data.local.entities.Exercise
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.repository.ExerciseRepository
import com.redforge.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDetailViewModelTest {

    private lateinit var db: RedForgeDatabase
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var workoutRepository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RedForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseRepository = ExerciseRepository(db.exerciseDao())
        workoutRepository = WorkoutRepository(db.workoutDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun completedSessionIsMappedIntoHistoryDetailState() = runBlocking {
        val exerciseId = exerciseRepository.save(
            Exercise(name = "Bench Press", muscleGroup = "Chest")
        )
        val sessionId = workoutRepository.startSession(null, "Freeform")
        assertTrue(
            workoutRepository.logSet(
                SetEntry(
                    workoutSessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = 1,
                    weight = 60.0,
                    reps = 8
                )
            )
        )
        assertTrue(
            workoutRepository.completeSession(
                sessionId,
                nowMillis = System.currentTimeMillis()
            )
        )

        val viewModel = HistoryDetailViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            sessionId = sessionId
        )

        val state = viewModel.uiState.first { !it.loading }

        assertEquals(sessionId, state.session?.id)
        assertEquals(1, state.exercises.size)
        assertEquals("Bench Press", state.exercises.single().name)
        assertEquals(1, state.exercises.single().sets.size)
        assertEquals(480, state.totalVolume)
        assertFalse(state.error != null)
    }

    @Test
    fun missingOrActiveSessionProducesAnExplicitErrorState() = runBlocking {
        val sessionId = workoutRepository.startSession(null, "Freeform")

        val viewModel = HistoryDetailViewModel(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            sessionId = sessionId
        )

        val state = viewModel.uiState.first { !it.loading }

        assertTrue(state.session == null)
        assertTrue(state.exercises.isEmpty())
        assertEquals("That workout could not be found.", state.error)
    }
}
