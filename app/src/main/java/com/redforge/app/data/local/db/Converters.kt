package com.redforge.app.data.local.db

import androidx.room.TypeConverter
import com.redforge.app.data.local.entities.PhotoAngle
import com.redforge.app.data.local.entities.WorkoutSessionStatus

class Converters {
    @TypeConverter
    fun fromPhotoAngle(angle: PhotoAngle): String = angle.name

    @TypeConverter
    fun toPhotoAngle(value: String): PhotoAngle = PhotoAngle.valueOf(value)

    @TypeConverter
    fun fromWorkoutSessionStatus(status: WorkoutSessionStatus): String = status.name

    @TypeConverter
    fun toWorkoutSessionStatus(value: String): WorkoutSessionStatus =
        runCatching { WorkoutSessionStatus.valueOf(value) }
            .getOrDefault(WorkoutSessionStatus.PARTIAL)
}
