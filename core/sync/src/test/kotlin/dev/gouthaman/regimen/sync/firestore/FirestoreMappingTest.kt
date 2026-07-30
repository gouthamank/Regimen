package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MaxWorkoutDuration
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.model.WorkoutEndReason
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreMappingTest {

    @Test
    fun `ExerciseEntity maps every field except id and isDirty`() {
        val entity = ExerciseEntity(
            id = "e1",
            name = "Bench Press",
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isCustom = true,
            isDirty = true,
            lastModifiedAt = 1000L,
        )

        assertEquals(
            ExerciseDto(
                name = "Bench Press",
                type = "STRENGTH",
                muscleGroup = "CHEST",
                equipment = "BARBELL",
                isCustom = true,
                lastModifiedAt = 1000L,
            ),
            entity.toDto(),
        )
    }

    @Test
    fun `RoutineEntity and RoutineExerciseEntity map correctly, routineId omitted`() {
        val routine =
            RoutineEntity(id = "r1", name = "Push Day", position = 0, lastModifiedAt = 2000L)
        assertEquals(
            RoutineDto(name = "Push Day", position = 0, lastModifiedAt = 2000L),
            routine.toDto()
        )

        val routineExercise = RoutineExerciseEntity(
            id = "re1",
            routineId = "r1",
            exerciseId = "e1",
            position = 0,
            targetSets = 3,
            targetReps = 8,
            targetRestSec = 90,
            supersetGroupId = null,
            lastModifiedAt = 3000L,
        )
        assertEquals(
            RoutineExerciseDto(
                exerciseId = "e1",
                position = 0,
                targetSets = 3,
                targetReps = 8,
                targetRestSec = 90,
                supersetGroupId = null,
                lastModifiedAt = 3000L,
            ),
            routineExercise.toDto(),
        )
    }

    @Test
    fun `WorkoutEntity maps workoutStatus and nullable endReason correctly`() {
        val workout = WorkoutEntity(
            id = "w1",
            startTime = 1000L,
            endTime = 2000L,
            note = "Felt strong",
            routineId = "r1",
            workoutStatus = WorkoutStatus.COMPLETE,
            endReason = WorkoutEndReason.MANUAL,
            pausedAt = null,
            accumulatedPausedMs = 500L,
            restTimeEndAt = null,
            restTotalSec = null,
            restWorkoutExerciseId = null,
            lastModifiedAt = 4000L,
        )
        assertEquals(
            WorkoutDto(
                startTime = 1000L,
                endTime = 2000L,
                note = "Felt strong",
                routineId = "r1",
                workoutStatus = "COMPLETE",
                endReason = "MANUAL",
                pausedAt = null,
                accumulatedPausedMs = 500L,
                restTimeEndAt = null,
                restTotalSec = null,
                restWorkoutExerciseId = null,
                lastModifiedAt = 4000L,
            ),
            workout.toDto(),
        )
    }

    @Test
    fun `WorkoutEntity maps a null endReason to a null string, not a crash`() {
        val workout = WorkoutEntity(id = "w1", startTime = 1000L, endReason = null)
        assertEquals(null, workout.toDto().endReason)
    }

    @Test
    fun `WorkoutExerciseEntity, SetEntryEntity, CardioEntryEntity map correctly, parent ids omitted`() {
        val workoutExercise = WorkoutExerciseEntity(
            id = "we1",
            workoutId = "w1",
            exerciseId = "e1",
            position = 0,
            isSkipped = false,
            isDone = true,
            supersetGroupId = null,
            lastModifiedAt = 5000L,
        )
        assertEquals(
            WorkoutExerciseDto(
                exerciseId = "e1",
                position = 0,
                isSkipped = false,
                isDone = true,
                supersetGroupId = null,
                lastModifiedAt = 5000L,
            ),
            workoutExercise.toDto(),
        )

        val setEntry = SetEntryEntity(
            id = "s1",
            workoutExerciseId = "we1",
            setNumber = 1,
            weightKg = 100.0,
            reps = 5,
            isComplete = true,
            lastModifiedAt = 6000L,
        )
        assertEquals(
            SetEntryDto(
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                isComplete = true,
                lastModifiedAt = 6000L
            ),
            setEntry.toDto(),
        )

        val cardioEntry = CardioEntryEntity(
            id = "c1",
            workoutExerciseId = "we1",
            durationSec = 600L,
            distanceMeters = 2000.0,
            lastModifiedAt = 7000L,
        )
        assertEquals(
            CardioEntryDto(durationSec = 600L, distanceMeters = 2000.0, lastModifiedAt = 7000L),
            cardioEntry.toDto(),
        )
    }

    @Test
    fun `MeasurementTypeEntity and BodyMetricEntity map correctly`() {
        val type = MeasurementTypeEntity(
            id = "t1",
            name = "Waist",
            unit = "cm",
            isBuiltIn = false,
            lastModifiedAt = 8000L,
        )
        assertEquals(
            MeasurementTypeDto(
                name = "Waist",
                unit = "cm",
                isBuiltIn = false,
                lastModifiedAt = 8000L
            ),
            type.toDto(),
        )

        val metric = BodyMetricEntity(
            id = "m1",
            measurementTypeId = "t1",
            date = 9000L,
            value = 80.0,
            lastModifiedAt = 9000L,
        )
        assertEquals(
            BodyMetricDto(
                measurementTypeId = "t1",
                date = 9000L,
                value = 80.0,
                lastModifiedAt = 9000L
            ),
            metric.toDto(),
        )
    }

    @Test
    fun `UserPreferences maps every field except onboarded, and takes lastModifiedAt as a param`() {
        val preferences = UserPreferences(
            weightUnit = UnitSystem.IMPERIAL,
            distanceUnit = UnitSystem.METRIC,
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            restDefaultSec = 120,
            restChimeEnabled = false,
            maxWorkoutDuration = MaxWorkoutDuration.SIX_HOURS,
            onboarded = true,
        )

        assertEquals(
            PreferencesDto(
                weightUnit = "IMPERIAL",
                distanceUnit = "METRIC",
                themeMode = "DARK",
                dynamicColor = false,
                restDefaultSec = 120,
                restChimeEnabled = false,
                maxWorkoutDuration = "SIX_HOURS",
                lastModifiedAt = 10000L,
            ),
            preferences.toDto(lastModifiedAt = 10000L),
        )
    }
}
