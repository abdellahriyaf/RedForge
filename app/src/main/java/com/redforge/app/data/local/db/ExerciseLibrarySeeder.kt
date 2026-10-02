package com.redforge.app.data.local.db

import com.redforge.app.data.local.entities.Exercise

/**
 * Curated v0.6 catalog. The content is intentionally metadata-first; media
 * demonstrations remain empty until v0.7 Strong Ember is implemented.
 */
object ExerciseLibrarySeeder {

    suspend fun ensureSeeded(database: RedForgeDatabase) {
        val dao = database.exerciseDao()
        val existing = dao.getAllOnce().associateBy { it.name.lowercase() }

        val additions = mutableListOf<Exercise>()
        val enrichments = mutableListOf<Exercise>()

        catalog().forEach { seed ->
            val old = existing[seed.name.lowercase()]
            when {
                old == null -> additions += seed
                !old.isCustom && needsMetadataEnrichment(old, seed) -> {
                    enrichments += seed.copy(
                        id = old.id,
                        imageUri = old.imageUri,
                        referenceLink = old.referenceLink,
                        notes = old.notes,
                        createdAt = old.createdAt,
                        isCustom = false,
                        isArchived = old.isArchived
                    )
                }
                !old.isCustom && old.seedKey != seed.seedKey -> {
                    enrichments += old.copy(seedKey = seed.seedKey)
                }
            }
        }

        if (additions.isNotEmpty()) {
            dao.upsertAll(additions)
        }
        enrichments.forEach { dao.upsert(it) }
    }

    private fun needsMetadataEnrichment(old: Exercise, seed: Exercise): Boolean {
        return old.aliases.isBlank() ||
            old.primaryMuscles.isBlank() ||
            old.secondaryMuscles.isBlank() ||
            old.movementPattern.isBlank() ||
            old.instructions.isBlank() ||
            old.keyCues.isBlank() ||
            old.commonMistakes.isBlank()
    }

    private fun ex(
        name: String,
        group: String,
        equipment: String,
        primary: String,
        secondary: String,
        pattern: String,
        difficulty: String = "Intermediate",
        reps: String = "8-12",
        aliases: String = ""
    ): Exercise = Exercise(
        name = name,
        muscleGroup = group,
        equipment = equipment,
        seedKey = "builtin:" + name.lowercase().replace(" ", "_").replace("-", "_"),
        aliases = aliases,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern,
        difficulty = difficulty,
        defaultRepRange = reps,
        instructions = defaultInstructions(pattern),
        keyCues = defaultCues(pattern),
        commonMistakes = defaultMistakes(pattern),
        isCustom = false,
        sourceLicense = "RedForge curated catalog"
    )

    private fun defaultInstructions(pattern: String): String = when (pattern) {
        "Horizontal Push" -> "Set your body position, brace your trunk, lower the load under control, then press through the intended path."
        "Vertical Push" -> "Brace your trunk, press the load overhead without losing control, then return smoothly to the start."
        "Horizontal Pull" -> "Set the torso, pull by driving the elbows, pause briefly, then return the load under control."
        "Vertical Pull" -> "Brace your torso, drive the elbows toward the floor, then control the return without swinging."
        "Squat" -> "Set your stance, brace, descend under control through a comfortable range, then drive back to standing."
        "Hinge" -> "Brace, push the hips back while keeping the torso controlled, then drive the hips forward to stand tall."
        "Lunge" -> "Step or move into the prescribed stance, lower under control, then drive through the working leg to return."
        "Isolation" -> "Set the target joint, move through a controlled range, pause at the point of peak tension, then return slowly."
        "Core" -> "Brace the trunk, keep the intended body position, perform the movement without momentum, and control every rep."
        "Carry" -> "Stand tall, brace the trunk, keep the load controlled, and walk with short stable steps."
        "Calf Raise" -> "Maintain balance, lower into a comfortable stretch, then drive through the forefoot and pause at the top."
        else -> "Set a stable starting position, brace as needed, move with control, and return to the starting position."
    }

    private fun defaultCues(pattern: String): String = when (pattern) {
        "Horizontal Push" -> "Stable upper back; controlled descent; smooth press."
        "Vertical Push" -> "Brace first; keep the path controlled; avoid excessive low-back arch."
        "Horizontal Pull" -> "Lead with elbows; keep torso stable; control the eccentric."
        "Vertical Pull" -> "Drive elbows down; avoid swinging; finish with control."
        "Squat" -> "Brace; knees track naturally; control the bottom position."
        "Hinge" -> "Hips back; neutral controlled torso; finish tall without overextending."
        "Lunge" -> "Stay balanced; controlled depth; push through the working leg."
        "Isolation" -> "Minimize momentum; own the range; pause at peak tension."
        "Core" -> "Brace; breathe behind the brace; avoid momentum."
        "Carry" -> "Tall posture; steady steps; keep the load close and controlled."
        "Calf Raise" -> "Full controlled range; pause at the top; avoid bouncing."
        else -> "Controlled reps; stable setup; consistent range of motion."
    }

    private fun defaultMistakes(pattern: String): String = when (pattern) {
        "Horizontal Push" -> "Bouncing the load; unstable setup; losing control on the descent."
        "Vertical Push" -> "Overarching the back; rushing reps; losing the intended bar path."
        "Horizontal Pull" -> "Using body swing; shrugging excessively; dropping the weight."
        "Vertical Pull" -> "Swinging; pulling only with the hands; uncontrolled return."
        "Squat" -> "Losing brace; collapsing under fatigue; rushing the bottom."
        "Hinge" -> "Squatting the movement; rounding under load; overextending at lockout."
        "Lunge" -> "Losing balance; collapsing the knee inward; bouncing the bottom."
        "Isolation" -> "Swinging; shortening range; letting other joints take over."
        "Core" -> "Holding breath indefinitely; using momentum; losing trunk position."
        "Carry" -> "Leaning; rushing steps; letting the load pull the torso around."
        "Calf Raise" -> "Bouncing; half reps; shifting weight away from the working foot."
        else -> "Rushing reps; excessive momentum; inconsistent range."
    }

    private fun catalog(): List<Exercise> = listOf(
        // Chest
        ex("Barbell Bench Press", "Chest", "Barbell", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Intermediate", "5-8", "Bench Press;Barbell Press"),
        ex("Incline Barbell Bench Press", "Chest", "Barbell", "Chest;Anterior Deltoid", "Triceps", "Horizontal Push", "Intermediate", "6-10", "Incline Bench"),
        ex("Decline Barbell Bench Press", "Chest", "Barbell", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Intermediate", "6-10"),
        ex("Dumbbell Bench Press", "Chest", "Dumbbell", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Beginner", "6-12", "DB Bench Press"),
        ex("Incline Dumbbell Press", "Chest", "Dumbbell", "Upper Chest;Anterior Deltoid", "Triceps", "Horizontal Push", "Beginner", "8-12", "Incline DB Press"),
        ex("Decline Dumbbell Press", "Chest", "Dumbbell", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Intermediate", "8-12"),
        ex("Chest Press Machine", "Chest", "Machine", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Beginner", "8-15"),
        ex("Cable Chest Press", "Chest", "Cable", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Beginner", "8-15"),
        ex("Cable Fly", "Chest", "Cable", "Chest", "Anterior Deltoid", "Isolation", "Beginner", "10-15"),
        ex("Dumbbell Fly", "Chest", "Dumbbell", "Chest", "Anterior Deltoid", "Isolation", "Intermediate", "10-15"),
        ex("Pec Deck", "Chest", "Machine", "Chest", "Anterior Deltoid", "Isolation", "Beginner", "10-15"),
        ex("Push-Up", "Chest", "Bodyweight", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Beginner", "8-20"),
        ex("Weighted Push-Up", "Chest", "Bodyweight", "Chest", "Triceps;Anterior Deltoid", "Horizontal Push", "Intermediate", "6-15"),
        ex("Parallel Bar Dip", "Chest", "Bodyweight", "Chest;Triceps", "Anterior Deltoid", "Horizontal Push", "Intermediate", "6-12", "Chest Dips"),

        // Back
        ex("Conventional Deadlift", "Back", "Barbell", "Glutes;Hamstrings;Erectors", "Lats;Traps;Forearms", "Hinge", "Advanced", "3-6", "Deadlift"),
        ex("Romanian Deadlift", "Back", "Barbell", "Hamstrings;Glutes", "Erectors;Lats", "Hinge", "Intermediate", "6-10", "RDL"),
        ex("Stiff-Leg Deadlift", "Back", "Barbell", "Hamstrings", "Glutes;Erectors", "Hinge", "Intermediate", "6-10"),
        ex("Trap Bar Deadlift", "Back", "Trap Bar", "Glutes;Quads", "Hamstrings;Erectors;Traps", "Hinge", "Intermediate", "4-8"),
        ex("Barbell Row", "Back", "Barbell", "Lats;Rhomboids", "Rear Delts;Biceps;Erectors", "Horizontal Pull", "Intermediate", "6-10", "Bent-Over Row"),
        ex("Pendlay Row", "Back", "Barbell", "Lats;Rhomboids", "Rear Delts;Biceps", "Horizontal Pull", "Advanced", "5-8"),
        ex("Chest-Supported Row", "Back", "Dumbbell", "Rhomboids;Lats", "Rear Delts;Biceps", "Horizontal Pull", "Beginner", "8-12"),
        ex("Seated Cable Row", "Back", "Cable", "Lats;Rhomboids", "Biceps;Rear Delts", "Horizontal Pull", "Beginner", "8-12", "Cable Row"),
        ex("One-Arm Dumbbell Row", "Back", "Dumbbell", "Lats", "Rhomboids;Biceps;Rear Delts", "Horizontal Pull", "Beginner", "8-12"),
        ex("T-Bar Row", "Back", "Machine", "Lats;Rhomboids", "Biceps;Rear Delts", "Horizontal Pull", "Intermediate", "6-10"),
        ex("Pull-Up", "Back", "Bodyweight", "Lats", "Biceps;Rhomboids", "Vertical Pull", "Intermediate", "5-12"),
        ex("Chin-Up", "Back", "Bodyweight", "Lats;Biceps", "Rhomboids;Rear Delts", "Vertical Pull", "Intermediate", "5-12"),
        ex("Lat Pulldown", "Back", "Cable", "Lats", "Biceps;Rhomboids", "Vertical Pull", "Beginner", "8-12", "Pulldown"),
        ex("Neutral-Grip Lat Pulldown", "Back", "Cable", "Lats", "Biceps;Rhomboids", "Vertical Pull", "Beginner", "8-12"),
        ex("Straight-Arm Pulldown", "Back", "Cable", "Lats", "Teres Major;Long Head Triceps", "Isolation", "Beginner", "10-15"),
        ex("Machine High Row", "Back", "Machine", "Lats;Upper Back", "Biceps;Rear Delts", "Horizontal Pull", "Beginner", "8-15"),
        ex("Face Pull", "Back", "Cable", "Rear Delts;Upper Back", "Traps;Rotator Cuff", "Horizontal Pull", "Beginner", "10-15"),

        // Shoulders
        ex("Overhead Press", "Shoulders", "Barbell", "Anterior Deltoid", "Lateral Deltoid;Triceps", "Vertical Push", "Intermediate", "5-10", "Military Press"),
        ex("Seated Dumbbell Shoulder Press", "Shoulders", "Dumbbell", "Anterior Deltoid", "Lateral Deltoid;Triceps", "Vertical Push", "Beginner", "6-12"),
        ex("Arnold Press", "Shoulders", "Dumbbell", "Anterior Deltoid", "Lateral Deltoid;Triceps", "Vertical Push", "Intermediate", "8-12"),
        ex("Machine Shoulder Press", "Shoulders", "Machine", "Anterior Deltoid", "Lateral Deltoid;Triceps", "Vertical Push", "Beginner", "8-15"),
        ex("Dumbbell Lateral Raise", "Shoulders", "Dumbbell", "Lateral Deltoid", "Upper Traps;Anterior Deltoid", "Isolation", "Beginner", "10-20", "Lateral Raise"),
        ex("Cable Lateral Raise", "Shoulders", "Cable", "Lateral Deltoid", "Upper Traps", "Isolation", "Beginner", "10-20"),
        ex("Rear Delt Fly", "Shoulders", "Dumbbell", "Rear Deltoid", "Rhomboids;Traps", "Isolation", "Beginner", "10-20"),
        ex("Reverse Pec Deck", "Shoulders", "Machine", "Rear Deltoid", "Rhomboids;Traps", "Isolation", "Beginner", "10-20"),
        ex("Cable Y-Raise", "Shoulders", "Cable", "Anterior Deltoid;Lower Traps", "Serratus;Rotator Cuff", "Isolation", "Beginner", "10-15"),
        ex("Front Raise", "Shoulders", "Dumbbell", "Anterior Deltoid", "Upper Chest", "Isolation", "Beginner", "10-15"),

        // Biceps
        ex("Barbell Curl", "Arms", "Barbell", "Biceps", "Brachialis;Forearms", "Isolation", "Beginner", "8-12", "Standing Curl"),
        ex("EZ-Bar Curl", "Arms", "EZ Bar", "Biceps", "Brachialis;Forearms", "Isolation", "Beginner", "8-12"),
        ex("Dumbbell Curl", "Arms", "Dumbbell", "Biceps", "Brachialis;Forearms", "Isolation", "Beginner", "8-15"),
        ex("Incline Dumbbell Curl", "Arms", "Dumbbell", "Biceps", "Brachialis", "Isolation", "Intermediate", "8-15"),
        ex("Hammer Curl", "Arms", "Dumbbell", "Brachialis;Brachioradialis", "Biceps", "Isolation", "Beginner", "8-15"),
        ex("Cable Curl", "Arms", "Cable", "Biceps", "Brachialis;Forearms", "Isolation", "Beginner", "10-15"),
        ex("Preacher Curl", "Arms", "Machine", "Biceps", "Brachialis", "Isolation", "Beginner", "8-15"),
        ex("Concentration Curl", "Arms", "Dumbbell", "Biceps", "Brachialis", "Isolation", "Beginner", "10-15"),
        ex("Reverse Curl", "Arms", "EZ Bar", "Brachioradialis;Forearms", "Biceps", "Isolation", "Beginner", "10-15"),

        // Triceps
        ex("Triceps Pushdown", "Arms", "Cable", "Triceps", "", "Isolation", "Beginner", "8-15", "Cable Pushdown"),
        ex("Rope Triceps Pushdown", "Arms", "Cable", "Triceps", "", "Isolation", "Beginner", "10-15"),
        ex("Overhead Cable Triceps Extension", "Arms", "Cable", "Triceps", "", "Isolation", "Beginner", "10-15"),
        ex("Dumbbell Overhead Triceps Extension", "Arms", "Dumbbell", "Triceps", "", "Isolation", "Beginner", "8-15"),
        ex("Skull Crusher", "Arms", "EZ Bar", "Triceps", "", "Isolation", "Intermediate", "8-12", "Lying Triceps Extension"),
        ex("Close-Grip Bench Press", "Arms", "Barbell", "Triceps", "Chest;Anterior Deltoid", "Horizontal Push", "Intermediate", "5-10"),
        ex("Bench Dip", "Arms", "Bodyweight", "Triceps", "Anterior Deltoid;Chest", "Horizontal Push", "Beginner", "8-15"),

        // Quads
        ex("Barbell Back Squat", "Legs", "Barbell", "Quads;Glutes", "Hamstrings;Core", "Squat", "Intermediate", "5-10", "Back Squat"),
        ex("Front Squat", "Legs", "Barbell", "Quads", "Glutes;Core", "Squat", "Advanced", "4-8"),
        ex("Hack Squat", "Legs", "Machine", "Quads", "Glutes", "Squat", "Intermediate", "6-12"),
        ex("Leg Press", "Legs", "Machine", "Quads", "Glutes;Hamstrings", "Squat", "Beginner", "8-15"),
        ex("Belt Squat", "Legs", "Machine", "Quads;Glutes", "Adductors", "Squat", "Intermediate", "6-12"),
        ex("Goblet Squat", "Legs", "Dumbbell", "Quads;Glutes", "Core", "Squat", "Beginner", "8-15"),
        ex("Bulgarian Split Squat", "Legs", "Dumbbell", "Quads;Glutes", "Hamstrings;Core", "Lunge", "Intermediate", "8-12", "Rear-Foot Elevated Split Squat"),
        ex("Walking Lunge", "Legs", "Dumbbell", "Quads;Glutes", "Hamstrings;Core", "Lunge", "Beginner", "10-16"),
        ex("Reverse Lunge", "Legs", "Dumbbell", "Glutes;Quads", "Hamstrings;Core", "Lunge", "Beginner", "8-15"),
        ex("Leg Extension", "Legs", "Machine", "Quads", "", "Isolation", "Beginner", "10-20"),
        ex("Step-Up", "Legs", "Dumbbell", "Quads;Glutes", "Hamstrings;Calves", "Lunge", "Beginner", "8-15"),

        // Hamstrings / glutes
        ex("Lying Leg Curl", "Legs", "Machine", "Hamstrings", "Calves", "Isolation", "Beginner", "10-15"),
        ex("Seated Leg Curl", "Legs", "Machine", "Hamstrings", "Calves", "Isolation", "Beginner", "10-15"),
        ex("Nordic Hamstring Curl", "Legs", "Bodyweight", "Hamstrings", "Glutes;Calves", "Isolation", "Advanced", "4-10"),
        ex("Good Morning", "Legs", "Barbell", "Hamstrings;Erectors", "Glutes;Core", "Hinge", "Advanced", "6-10"),
        ex("Barbell Hip Thrust", "Legs", "Barbell", "Glutes", "Hamstrings", "Hinge", "Intermediate", "6-12"),
        ex("Glute Bridge", "Legs", "Bodyweight", "Glutes", "Hamstrings", "Hinge", "Beginner", "10-20"),
        ex("Cable Pull-Through", "Legs", "Cable", "Glutes", "Hamstrings", "Hinge", "Beginner", "10-15"),
        ex("Cable Kickback", "Legs", "Cable", "Glutes", "", "Isolation", "Beginner", "10-20"),
        ex("Hip Abduction Machine", "Legs", "Machine", "Glute Medius", "Glutes", "Isolation", "Beginner", "12-20"),
        ex("Hip Adduction Machine", "Legs", "Machine", "Adductors", "", "Isolation", "Beginner", "12-20"),

        // Calves
        ex("Standing Calf Raise", "Legs", "Machine", "Gastrocnemius", "Soleus", "Calf Raise", "Beginner", "10-20"),
        ex("Seated Calf Raise", "Legs", "Machine", "Soleus", "Gastrocnemius", "Calf Raise", "Beginner", "10-20"),
        ex("Leg Press Calf Raise", "Legs", "Machine", "Gastrocnemius", "Soleus", "Calf Raise", "Beginner", "10-20"),
        ex("Single-Leg Calf Raise", "Legs", "Bodyweight", "Gastrocnemius;Soleus", "", "Calf Raise", "Beginner", "10-20"),

        // Core
        ex("Plank", "Core", "Bodyweight", "Abs", "Obliques;Erectors", "Core", "Beginner", "20-60s"),
        ex("Side Plank", "Core", "Bodyweight", "Obliques", "Abs;Glute Medius", "Core", "Beginner", "20-45s"),
        ex("Hanging Leg Raise", "Core", "Bodyweight", "Abs", "Hip Flexors;Forearms", "Core", "Intermediate", "8-15"),
        ex("Hanging Knee Raise", "Core", "Bodyweight", "Abs", "Hip Flexors", "Core", "Beginner", "10-15"),
        ex("Cable Crunch", "Core", "Cable", "Abs", "Obliques", "Core", "Beginner", "10-20"),
        ex("Ab Wheel Rollout", "Core", "Ab Wheel", "Abs", "Lats;Erectors;Shoulders", "Core", "Advanced", "6-15"),
        ex("Dead Bug", "Core", "Bodyweight", "Abs", "Hip Flexors;Obliques", "Core", "Beginner", "8-12"),
        ex("Pallof Press", "Core", "Cable", "Obliques", "Abs;Glutes", "Core", "Beginner", "8-15"),
        ex("Russian Twist", "Core", "Bodyweight", "Obliques", "Abs;Hip Flexors", "Core", "Beginner", "10-20"),
        ex("Reverse Crunch", "Core", "Bodyweight", "Abs", "Hip Flexors", "Core", "Beginner", "10-20"),

        // Full-body / carries / conditioning strength
        ex("Farmer Carry", "Full Body", "Dumbbell", "Forearms;Traps", "Core;Glutes", "Carry", "Beginner", "20-40m", "Farmer's Walk"),
        ex("Suitcase Carry", "Full Body", "Dumbbell", "Obliques;Forearms", "Traps;Core", "Carry", "Beginner", "20-40m"),
        ex("Front Rack Carry", "Full Body", "Barbell", "Core;Upper Back", "Forearms;Quads", "Carry", "Intermediate", "20-30m"),
        ex("Turkish Get-Up", "Full Body", "Kettlebell", "Shoulders;Core", "Glutes;Quads", "Full Body", "Advanced", "2-6"),
        ex("Kettlebell Swing", "Full Body", "Kettlebell", "Glutes;Hamstrings", "Lats;Core", "Hinge", "Intermediate", "10-20"),
        ex("Kettlebell Goblet Squat", "Full Body", "Kettlebell", "Quads;Glutes", "Core", "Squat", "Beginner", "8-15"),
        ex("Barbell Thruster", "Full Body", "Barbell", "Quads;Anterior Deltoid", "Glutes;Triceps;Core", "Full Body", "Advanced", "6-12"),
        ex("Dumbbell Thruster", "Full Body", "Dumbbell", "Quads;Anterior Deltoid", "Glutes;Triceps;Core", "Full Body", "Intermediate", "8-15"),
        ex("Dumbbell Clean", "Full Body", "Dumbbell", "Glutes;Traps", "Quads;Core;Shoulders", "Full Body", "Advanced", "3-8"),
        ex("Barbell Clean Pull", "Full Body", "Barbell", "Traps;Glutes", "Hamstrings;Quads", "Hinge", "Advanced", "3-8"),
    )
}
