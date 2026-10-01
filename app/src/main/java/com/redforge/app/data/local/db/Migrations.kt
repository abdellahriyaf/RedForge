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
                id INTEGER NOT NULL,
                splitDayId INTEGER,
                splitDayNameSnapshot TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                PRIMARY KEY(id)
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


val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
