package com.redforge.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reusable exercise definition. v0.6 expands the library metadata so the same
 * exercise record can power search, filtering, detail pages, progress, and the
 * future Strong Ember demonstration system in v0.7.
 *
 * Multi-value fields are stored as semicolon-separated strings to keep the
 * schema simple and migration-safe.
 */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String = "",
    val imageUri: String? = null,
    val referenceLink: String? = null,
    val notes: String = "",
    val isCustom: Boolean = true,
    val isArchived: Boolean = false,
    val seedKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // v0.6 library metadata
    val aliases: String = "",
    val primaryMuscles: String = "",
    val secondaryMuscles: String = "",
    val movementPattern: String = "",
    val difficulty: String = "Intermediate",
    val defaultRepRange: String = "8-12",
    val instructions: String = "",
    val keyCues: String = "",
    val commonMistakes: String = "",
    // Reserved for v0.7 Strong Ember demonstrations. Empty in v0.6.
    val demoAsset: String? = null,
    val sourceLicense: String = "RedForge curated catalog",
    val sourceAttribution: String = ""
)

fun String.toLibraryItems(): List<String> =
    split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
