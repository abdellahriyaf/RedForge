package com.redforge.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: PR, deload and superset additions. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE set_entries ADD COLUMN isPersonalRecord INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE splits ADD COLUMN isDeloadCycle INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE split_day_exercises ADD COLUMN supersetGroup INTEGER DEFAULT NULL")
    }
}

/**
 * v2 -> v3: expands exercise definitions. All fields are additive with safe
 * defaults, so existing workout history and custom exercises are preserved.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN aliases TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN primaryMuscles TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN secondaryMuscles TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN movementPattern TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'Intermediate'")
        db.execSQL("ALTER TABLE exercises ADD COLUMN defaultRepRange TEXT NOT NULL DEFAULT '8-12'")
        db.execSQL("ALTER TABLE exercises ADD COLUMN instructions TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN keyCues TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN commonMistakes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercises ADD COLUMN demoAsset TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE exercises ADD COLUMN sourceLicense TEXT NOT NULL DEFAULT 'RedForge curated catalog'")
        db.execSQL("ALTER TABLE exercises ADD COLUMN sourceAttribution TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 -> v4: replaces the ambiguous completed boolean with an explicit
 * workout lifecycle. Existing unfinished sessions remain ACTIVE until the
 * first lifecycle read archives any session that has crossed a calendar day.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_sessions_new (
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
            INSERT INTO workout_sessions_new (
                id, splitDayId, splitDayNameSnapshot,
                startedAt, endedAt, status, notes
            )
            SELECT
                id, splitDayId, splitDayNameSnapshot,
                startedAt, endedAt,
                CASE
                    WHEN completed = 1 THEN 'COMPLETED'
                    ELSE 'ACTIVE'
                END,
                notes
            FROM workout_sessions
            """.trimIndent()
        )

        db.execSQL("DROP TABLE workout_sessions")
        db.execSQL("ALTER TABLE workout_sessions_new RENAME TO workout_sessions")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workout_sessions_status_startedAt " +
                "ON workout_sessions(status, startedAt)"
        )
    }
}


/**
 * v4 -> v5: repairs legacy orphan rows, introduces stable exercise identity/
 * archive state, and rebuilds the relational tables with foreign keys and
 * supporting indexes. The migration intentionally drops only data that cannot
 * be attached to a valid parent; workout history itself is retained.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exercises_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                muscleGroup TEXT NOT NULL,
                equipment TEXT NOT NULL,
                imageUri TEXT,
                referenceLink TEXT,
                notes TEXT NOT NULL,
                isCustom INTEGER NOT NULL,
                isArchived INTEGER NOT NULL DEFAULT 0,
                seedKey TEXT,
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
            INSERT INTO exercises_new (
                id, name, muscleGroup, equipment, imageUri, referenceLink, notes,
                isCustom, isArchived, seedKey, createdAt, aliases, primaryMuscles,
                secondaryMuscles, movementPattern, difficulty, defaultRepRange,
                instructions, keyCues, commonMistakes, demoAsset, sourceLicense,
                sourceAttribution
            )
            SELECT id, name, muscleGroup, equipment, imageUri, referenceLink, notes,
                isCustom, 0, NULL, createdAt, aliases, primaryMuscles,
                secondaryMuscles, movementPattern, difficulty, defaultRepRange,
                instructions, keyCues, commonMistakes, demoAsset, sourceLicense,
                sourceAttribution
            FROM exercises
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE splits_new (
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
        db.execSQL("INSERT INTO splits_new SELECT id, name, isActive, daysPerCycle, isDeloadCycle, createdAt, updatedAt FROM splits")

        db.execSQL(
            """
            CREATE TABLE split_days_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitId INTEGER NOT NULL,
                name TEXT NOT NULL,
                dayOrder INTEGER NOT NULL,
                isRestDay INTEGER NOT NULL,
                FOREIGN KEY(splitId) REFERENCES splits_new(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO split_days_new (id, splitId, name, dayOrder, isRestDay)
            SELECT sd.id, sd.splitId, sd.name,
                (
                    SELECT COUNT(*)
                    FROM split_days prior
                    WHERE prior.splitId = sd.splitId
                      AND (
                          prior.dayOrder < sd.dayOrder OR
                          (prior.dayOrder = sd.dayOrder AND prior.id <= sd.id)
                      )
                ),
                sd.isRestDay
            FROM split_days sd
            INNER JOIN splits s ON s.id = sd.splitId
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE split_day_exercises_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitDayId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                targetSets INTEGER NOT NULL,
                targetRepsLow INTEGER NOT NULL,
                targetRepsHigh INTEGER NOT NULL,
                targetRestSeconds INTEGER NOT NULL,
                supersetGroup INTEGER,
                FOREIGN KEY(splitDayId) REFERENCES split_days_new(id) ON DELETE CASCADE,
                FOREIGN KEY(exerciseId) REFERENCES exercises_new(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO split_day_exercises_new (
                id, splitDayId, exerciseId, orderIndex, targetSets,
                targetRepsLow, targetRepsHigh, targetRestSeconds, supersetGroup
            )
            SELECT sde.id, sde.splitDayId, sde.exerciseId, sde.orderIndex,
                sde.targetSets, sde.targetRepsLow, sde.targetRepsHigh,
                sde.targetRestSeconds, sde.supersetGroup
            FROM split_day_exercises sde
            INNER JOIN split_days_new sd ON sd.id = sde.splitDayId
            INNER JOIN exercises_new e ON e.id = sde.exerciseId
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE workout_sessions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitDayId INTEGER,
                splitDayNameSnapshot TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                FOREIGN KEY(splitDayId) REFERENCES split_days_new(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO workout_sessions_new (
                id, splitDayId, splitDayNameSnapshot, startedAt, endedAt, status, notes
            )
            SELECT ws.id,
                CASE WHEN sd.id IS NULL THEN NULL ELSE ws.splitDayId END,
                ws.splitDayNameSnapshot, ws.startedAt, ws.endedAt, ws.status, ws.notes
            FROM workout_sessions ws
            LEFT JOIN split_days_new sd ON sd.id = ws.splitDayId
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE set_entries_new (
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
                loggedAt INTEGER NOT NULL,
                FOREIGN KEY(workoutSessionId) REFERENCES workout_sessions_new(id) ON DELETE CASCADE,
                FOREIGN KEY(exerciseId) REFERENCES exercises_new(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO set_entries_new (
                id, workoutSessionId, exerciseId, setIndex, weight, reps,
                isWarmup, rpe, completed, isPersonalRecord, loggedAt
            )
            SELECT se.id, se.workoutSessionId, se.exerciseId, se.setIndex, se.weight,
                se.reps, se.isWarmup, se.rpe, se.completed, se.isPersonalRecord,
                se.loggedAt
            FROM set_entries se
            INNER JOIN workout_sessions_new ws ON ws.id = se.workoutSessionId
            INNER JOIN exercises_new e ON e.id = se.exerciseId
            """.trimIndent()
        )

        db.execSQL("DROP TABLE set_entries")
        db.execSQL("DROP TABLE workout_sessions")
        db.execSQL("DROP TABLE split_day_exercises")
        db.execSQL("DROP TABLE split_days")
        db.execSQL("DROP TABLE splits")
        db.execSQL("DROP TABLE exercises")

        db.execSQL("ALTER TABLE exercises_new RENAME TO exercises")
        db.execSQL("ALTER TABLE splits_new RENAME TO splits")
        db.execSQL("ALTER TABLE split_days_new RENAME TO split_days")
        db.execSQL("ALTER TABLE split_day_exercises_new RENAME TO split_day_exercises")
        db.execSQL("ALTER TABLE workout_sessions_new RENAME TO workout_sessions")
        db.execSQL("ALTER TABLE set_entries_new RENAME TO set_entries")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_seedKey ON exercises(seedKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_split_days_splitId ON split_days(splitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_split_days_splitId_dayOrder ON split_days(splitId, dayOrder)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_split_day_exercises_splitDayId ON split_day_exercises(splitDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_split_day_exercises_exerciseId ON split_day_exercises(exerciseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_status_startedAt ON workout_sessions(status, startedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_splitDayId ON workout_sessions(splitDayId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_set_entries_workoutSessionId ON set_entries(workoutSessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_set_entries_exerciseId ON set_entries(exerciseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_set_entries_workoutSessionId_exerciseId_setIndex ON set_entries(workoutSessionId, exerciseId, setIndex)")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
