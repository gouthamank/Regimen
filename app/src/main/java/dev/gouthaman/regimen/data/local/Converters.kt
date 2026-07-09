package dev.gouthaman.regimen.data.local

import androidx.room.TypeConverter
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** Stores enums as their name string. Dates are stored directly as epoch-millis Longs. */
class Converters {
    @TypeConverter fun exerciseTypeToString(v: ExerciseType): String = v.name
    @TypeConverter fun stringToExerciseType(v: String): ExerciseType = ExerciseType.valueOf(v)

    @TypeConverter fun muscleGroupToString(v: MuscleGroup): String = v.name
    @TypeConverter fun stringToMuscleGroup(v: String): MuscleGroup = MuscleGroup.valueOf(v)

    @TypeConverter fun equipmentToString(v: Equipment): String = v.name
    @TypeConverter fun stringToEquipment(v: String): Equipment = Equipment.valueOf(v)
}
