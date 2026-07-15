package dev.gouthaman.regimen.data.local.seed

import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Equipment.BARBELL
import dev.gouthaman.regimen.domain.model.Equipment.BODYWEIGHT
import dev.gouthaman.regimen.domain.model.Equipment.CABLE
import dev.gouthaman.regimen.domain.model.Equipment.CARDIO_MACHINE
import dev.gouthaman.regimen.domain.model.Equipment.DUMBBELL
import dev.gouthaman.regimen.domain.model.Equipment.KETTLEBELL
import dev.gouthaman.regimen.domain.model.Equipment.MACHINE
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.ExerciseType.CARDIO
import dev.gouthaman.regimen.domain.model.ExerciseType.STRENGTH
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.MuscleGroup.ARMS
import dev.gouthaman.regimen.domain.model.MuscleGroup.BACK
import dev.gouthaman.regimen.domain.model.MuscleGroup.CHEST
import dev.gouthaman.regimen.domain.model.MuscleGroup.CORE
import dev.gouthaman.regimen.domain.model.MuscleGroup.FULL_BODY
import dev.gouthaman.regimen.domain.model.MuscleGroup.LEGS
import dev.gouthaman.regimen.domain.model.MuscleGroup.SHOULDERS

/** Curated built-in library (~60 movements) shipped with the app. */
object BuiltInData {

    private fun ex(name: String, mg: MuscleGroup, eq: Equipment, type: ExerciseType = STRENGTH) =
        ExerciseEntity(name = name, type = type, muscleGroup = mg, equipment = eq, isCustom = false)

    val exercises: List<ExerciseEntity> = listOf(
        ex("Barbell Bench Press", CHEST, BARBELL),
        ex("Incline Barbell Bench Press", CHEST, BARBELL),
        ex("Dumbbell Bench Press", CHEST, DUMBBELL),
        ex("Incline Dumbbell Press", CHEST, DUMBBELL),
        ex("Dumbbell Fly", CHEST, DUMBBELL),
        ex("Cable Crossover", CHEST, CABLE),
        ex("Cable Fly", CHEST, CABLE),
        ex("Chest Press Machine", CHEST, MACHINE),
        ex("Pec Deck", CHEST, MACHINE),
        ex("Push-Up", CHEST, BODYWEIGHT),
        ex("Dip", CHEST, BODYWEIGHT),
        ex("Deadlift", BACK, BARBELL),
        ex("Barbell Row", BACK, BARBELL),
        ex("Pull-Up", BACK, BODYWEIGHT),
        ex("Chin-Up", BACK, BODYWEIGHT),
        ex("Lat Pulldown", BACK, CABLE),
        ex("Seated Cable Row", BACK, CABLE),
        ex("Dumbbell Row", BACK, DUMBBELL),
        ex("T-Bar Row", BACK, BARBELL),
        ex("Face Pull", BACK, CABLE),
        ex("Back Extension", BACK, BODYWEIGHT),
        ex("Overhead Press", SHOULDERS, BARBELL),
        ex("Dumbbell Shoulder Press", SHOULDERS, DUMBBELL),
        ex("Arnold Press", SHOULDERS, DUMBBELL),
        ex("Lateral Raise", SHOULDERS, DUMBBELL),
        ex("Front Raise", SHOULDERS, DUMBBELL),
        ex("Rear Delt Fly", SHOULDERS, DUMBBELL),
        ex("Shoulder Press Machine", SHOULDERS, MACHINE),
        ex("Upright Row", SHOULDERS, BARBELL),
        ex("Barbell Shrug", SHOULDERS, BARBELL),
        ex("Dumbbell Shrug", SHOULDERS, DUMBBELL),
        ex("Cable Shrug", SHOULDERS, CABLE),
        ex("Barbell Curl", ARMS, BARBELL),
        ex("Dumbbell Curl", ARMS, DUMBBELL),
        ex("Hammer Curl", ARMS, DUMBBELL),
        ex("Preacher Curl", ARMS, BARBELL),
        ex("Cable Curl", ARMS, CABLE),
        ex("Triceps Pushdown", ARMS, CABLE),
        ex("Overhead Triceps Extension", ARMS, DUMBBELL),
        ex("Skull Crusher", ARMS, BARBELL),
        ex("Close-Grip Bench Press", ARMS, BARBELL),
        ex("Triceps Dip", ARMS, BODYWEIGHT),
        ex("Back Squat", LEGS, BARBELL),
        ex("Front Squat", LEGS, BARBELL),
        ex("Leg Press", LEGS, MACHINE),
        ex("Romanian Deadlift", LEGS, BARBELL),
        ex("Leg Extension", LEGS, MACHINE),
        ex("Leg Curl", LEGS, MACHINE),
        ex("Walking Lunge", LEGS, DUMBBELL),
        ex("Bulgarian Split Squat", LEGS, DUMBBELL),
        ex("Calf Raise", LEGS, MACHINE),
        ex("Hip Thrust", LEGS, BARBELL),
        ex("Goblet Squat", LEGS, KETTLEBELL),
        ex("Plank", CORE, BODYWEIGHT),
        ex("Hanging Leg Raise", CORE, BODYWEIGHT),
        ex("Cable Crunch", CORE, CABLE),
        ex("Russian Twist", CORE, BODYWEIGHT),
        ex("Ab Wheel Rollout", CORE, BODYWEIGHT),
        ex("Sit-Up", CORE, BODYWEIGHT),
        ex("Kettlebell Swing", FULL_BODY, KETTLEBELL),
        ex("Clean and Press", FULL_BODY, BARBELL),
        ex("Burpee", FULL_BODY, BODYWEIGHT),
        ex("Suitcase Carry", FULL_BODY, KETTLEBELL),
        ex("Treadmill Run", MuscleGroup.CARDIO, CARDIO_MACHINE, CARDIO),
        ex("Outdoor Run", MuscleGroup.CARDIO, BODYWEIGHT, CARDIO),
        ex("Cycling", MuscleGroup.CARDIO, CARDIO_MACHINE, CARDIO),
        ex("Rowing Machine", MuscleGroup.CARDIO, CARDIO_MACHINE, CARDIO),
        ex("Elliptical", MuscleGroup.CARDIO, CARDIO_MACHINE, CARDIO),
        ex("Stair Climber", MuscleGroup.CARDIO, CARDIO_MACHINE, CARDIO),
        ex("Jump Rope", MuscleGroup.CARDIO, BODYWEIGHT, CARDIO),
        ex("Swimming", MuscleGroup.CARDIO, BODYWEIGHT, CARDIO),
    )

    /** Built-in measurement type. Bodyweight is stored in kg canonically. */
    val measurementTypes: List<MeasurementTypeEntity> = listOf(
        MeasurementTypeEntity(name = "Bodyweight", unit = "kg", isBuiltIn = true),
    )
}
