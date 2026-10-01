package com.redforge.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.redforge.app.data.local.db.RedForgeDatabase
import com.redforge.app.data.local.entities.Exercise
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.Split
import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.SplitDayExercise
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.data.repository.WorkoutRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomIntegrityTest {

    private lateinit var db: RedForgeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RedForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }


    @Test
    fun roomEnablesForeignKeysAndDatabaseIntegrityCheckPasses() = runBlocking {
        db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
    }

    @Test
    fun splitDeletionCascadesDaysAndAssignments() = runBlocking {
        val splitId = db.splitDao().upsertSplit(Split(name = "Test Split"))
        val dayId = db.splitDao().upsertDay(
            SplitDay(splitId = splitId, name = "Push", dayOrder = 1)
        )
        val exerciseId = db.exerciseDao().upsert(
            Exercise(name = "Bench", muscleGroup = "Chest")
        )
        db.splitDao().upsertDayExercise(
            SplitDayExercise(splitDayId = dayId, exerciseId = exerciseId, orderIndex = 0)
        )

        db.splitDao().deleteSplit(Split(id = splitId, name = "Test Split"))

        assertTrue(db.splitDao().getDaysOnce(splitId).isEmpty())
        assertTrue(db.exerciseDao().getById(exerciseId)?.let { false } ?: true)
    }

    @Test
    fun archivedExerciseRemainsAvailableForExistingAssignments() = runBlocking {
        val splitId = db.splitDao().upsertSplit(Split(name = "Test Split"))
        val dayId = db.splitDao().upsertDay(
            SplitDay(splitId = splitId, name = "Push", dayOrder = 1)
        )
        val exerciseId = db.exerciseDao().upsert(
            Exercise(name = "Bench", muscleGroup = "Chest")
        )
        db.splitDao().upsertDayExercise(
            SplitDayExercise(splitDayId = dayId, exerciseId = exerciseId, orderIndex = 0)
        )

        db.exerciseDao().archive(exerciseId)

        assertTrue(db.exerciseDao().getById(exerciseId)?.isArchived == true)
        assertEquals(1, db.splitDao().getExercisesForDayOnce(dayId).size)
    }

    @Test
    fun deletingSessionCascadesItsLoggedSets() = runBlocking {
        val exerciseId = db.exerciseDao().upsert(
            Exercise(name = "Bench", muscleGroup = "Chest")
        )
        val sessionId = db.workoutDao().upsertSession(
            com.redforge.app.data.local.entities.WorkoutSession(
                splitDayId = null,
                splitDayNameSnapshot = "Freeform"
            )
        )
        db.workoutDao().upsertSet(
            SetEntry(
                workoutSessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = 1,
                weight = 60.0,
                reps = 8
            )
        )

        db.workoutDao().deleteSessionAndSets(
            db.workoutDao().getSession(sessionId)!!
        )

        assertEquals(0, db.workoutDao().getSetCountForSession(sessionId))
        assertEquals(null, db.workoutDao().getSession(sessionId))
    }

    @Test
    fun lifecycleGuardsSetWritesAndPreservesLoggedSetsOnAbandon() = runBlocking {
        val exerciseId = db.exerciseDao().upsert(
            Exercise(name = "Bench", muscleGroup = "Chest")
        )
        val repository = WorkoutRepository(db.workoutDao())
        val sessionId = repository.startSession(null, "Freeform")

        assertTrue(
            repository.logSet(
                SetEntry(
                    workoutSessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = 999,
                    weight = 60.0,
                    reps = 8
                )
            )
        )

        assertTrue(repository.abandonSession(repository.getSession(sessionId)!!))
        assertEquals(
            WorkoutSessionStatus.ABANDONED,
            repository.getSession(sessionId)?.status
        )
        assertEquals(1, repository.getSetsOnce(sessionId).size)

        assertFalse(
            repository.logSet(
                SetEntry(
                    workoutSessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = 999,
                    weight = 70.0,
                    reps = 6
                )
            )
        )
        assertEquals(1, repository.getSetsOnce(sessionId).size)
    }

    @Test
    fun activatingSplitLeavesExactlyOneActiveSplit() = runBlocking {
        val firstId = db.splitDao().upsertSplit(Split(name = "First", isActive = true))
        val secondId = db.splitDao().upsertSplit(Split(name = "Second"))

        val repository = com.redforge.app.data.repository.SplitRepository(db.splitDao())
        assertTrue(repository.setActiveSplit(secondId))

        assertEquals(false, db.splitDao().getSplit(firstId)?.isActive)
        assertEquals(true, db.splitDao().getSplit(secondId)?.isActive)
        assertEquals(1, db.splitDao().observeAllSplits().first().count { it.isActive })
    }

    @Test
    fun replacingDayExercisesLeavesOneOrderedSetOfAssignments() = runBlocking {
        val splitId = db.splitDao().upsertSplit(Split(name = "Test Split"))
        val dayId = db.splitDao().upsertDay(
            SplitDay(splitId = splitId, name = "Push", dayOrder = 1)
        )
        val firstExercise = db.exerciseDao().upsert(
            Exercise(name = "Bench", muscleGroup = "Chest")
        )
        val secondExercise = db.exerciseDao().upsert(
            Exercise(name = "Row", muscleGroup = "Back")
        )

        db.splitDao().replaceDayExercises(
            dayId,
            listOf(
                SplitDayExercise(splitDayId = dayId, exerciseId = secondExercise, orderIndex = 99),
                SplitDayExercise(splitDayId = dayId, exerciseId = firstExercise, orderIndex = 99)
            )
        )

        val result = db.splitDao().getExercisesForDayOnce(dayId)
        assertEquals(listOf(secondExercise, firstExercise), result.map { it.exerciseId })
        assertEquals(listOf(0, 1), result.map { it.orderIndex })
    }
}
