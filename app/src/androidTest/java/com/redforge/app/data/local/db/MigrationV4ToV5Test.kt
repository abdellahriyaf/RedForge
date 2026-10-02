package com.redforge.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationV4ToV5Test {

    private lateinit var databaseFile: File
    private lateinit var database: SQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File.createTempFile("redforge-migration-v4-", ".db", context.cacheDir)
        database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        database.version = 4
        createV4Schema(database)
    }

    @After
    fun tearDown() {
        database.close()
        databaseFile.delete()
    }

    @Test
    fun migrationRepairsOrphansAndNormalizesDayOrder() {
        seedLegacyData(database)

        MIGRATION_4_5.migrate(
            Class.forName("androidx.sqlite.db.framework.FrameworkSQLiteDatabase")
                .getDeclaredConstructor(SQLiteDatabase::class.java)
                .apply { isAccessible = true }
                .newInstance(database) as SupportSQLiteDatabase
        )

        database.rawQuery(
            "SELECT COUNT(*) FROM exercises WHERE id = 1",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        database.rawQuery(
            "SELECT dayOrder FROM split_days WHERE splitId = 1 ORDER BY id",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertTrue(it.moveToNext())
            assertEquals(2, it.getInt(0))
        }

        database.rawQuery(
            "SELECT COUNT(*) FROM split_day_exercises",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        database.rawQuery(
            "SELECT splitDayId FROM workout_sessions WHERE id = 2",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertNull(it.getString(0))
        }

        database.rawQuery(
            "SELECT COUNT(*) FROM set_entries",
            null
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        database.rawQuery("PRAGMA foreign_key_list(split_days)", null).use {
            assertTrue(it.moveToFirst())
        }
        database.rawQuery("PRAGMA foreign_key_list(set_entries)", null).use {
            assertTrue(it.moveToFirst())
        }
        database.rawQuery("PRAGMA integrity_check", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("ok", it.getString(0))
        }
    }

    private fun createV4Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                muscleGroup TEXT NOT NULL,
                equipment TEXT NOT NULL,
                imageUri TEXT,
                referenceLink TEXT,
                notes TEXT NOT NULL,
                isCustom INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                aliases TEXT NOT NULL,
                primaryMuscles TEXT NOT NULL,
                secondaryMuscles TEXT NOT NULL,
                movementPattern TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                defaultRepRange TEXT NOT NULL,
                instructions TEXT NOT NULL,
                keyCues TEXT NOT NULL,
                commonMistakes TEXT NOT NULL,
                demoAsset TEXT,
                sourceLicense TEXT NOT NULL,
                sourceAttribution TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE splits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                daysPerCycle INTEGER NOT NULL,
                isDeloadCycle INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE split_days (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitId INTEGER NOT NULL,
                name TEXT NOT NULL,
                dayOrder INTEGER NOT NULL,
                isRestDay INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE split_day_exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitDayId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                targetSets INTEGER NOT NULL,
                targetRepsLow INTEGER NOT NULL,
                targetRepsHigh INTEGER NOT NULL,
                targetRestSeconds INTEGER NOT NULL,
                supersetGroup INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE workout_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitDayId INTEGER,
                splitDayNameSnapshot TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE set_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutSessionId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setIndex INTEGER NOT NULL,
                weight REAL NOT NULL,
                reps INTEGER NOT NULL,
                isWarmup INTEGER NOT NULL,
                rpe REAL,
                completed INTEGER NOT NULL,
                isPersonalRecord INTEGER NOT NULL,
                loggedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun seedLegacyData(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO exercises (
                id, name, muscleGroup, equipment, imageUri, referenceLink, notes,
                isCustom, createdAt, aliases, primaryMuscles, secondaryMuscles,
                movementPattern, difficulty, defaultRepRange, instructions,
                keyCues, commonMistakes, demoAsset, sourceLicense, sourceAttribution
            ) VALUES (
                1, 'Bench Press', 'Chest', '', NULL, NULL, '', 0, 1000,
                '', '', '', '', 'Intermediate', '8-12', '', '', '', NULL,
                'test', ''
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO splits VALUES (1, 'Push', 1, 2, 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO split_days VALUES (1, 1, 'Push A', 5, 0)"
        )
        db.execSQL(
            "INSERT INTO split_days VALUES (2, 1, 'Push B', 5, 0)"
        )
        db.execSQL(
            "INSERT INTO split_day_exercises VALUES (1, 1, 1, 0, 3, 8, 12, 120, NULL)"
        )
        db.execSQL(
            "INSERT INTO split_day_exercises VALUES (2, 1, 999, 1, 3, 8, 12, 120, NULL)"
        )
        db.execSQL(
            "INSERT INTO workout_sessions VALUES (1, 1, 'Push A', 1000, NULL, 'COMPLETED', '')"
        )
        db.execSQL(
            "INSERT INTO workout_sessions VALUES (2, 999, 'Legacy', 1000, NULL, 'COMPLETED', '')"
        )
        db.execSQL(
            "INSERT INTO set_entries VALUES (1, 1, 1, 1, 60.0, 8, 0, NULL, 1, 0, 1000)"
        )
        db.execSQL(
            "INSERT INTO set_entries VALUES (2, 999, 1, 1, 60.0, 8, 0, NULL, 1, 0, 1000)"
        )
        db.execSQL(
            "INSERT INTO set_entries VALUES (3, 1, 999, 1, 60.0, 8, 0, NULL, 1, 0, 1000)"
        )
    }
}
