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

/** v3 -> v4: preserve expired in-progress sessions as partial history. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN abandoned INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
