package app.brivel.android

import androidx.health.connect.client.records.ExerciseSegment
import kotlin.test.Test
import kotlin.test.assertEquals

/** El tipo de ejercicio de Health Connect: el nombre manda sobre el patrón y el orden de las reglas importa. */
class HealthConnectTypeTest {
    private fun type(name: String, pattern: String?) = HealthConnectPlanner.exerciseType(name, pattern)

    @Test fun nameWinsOverPattern() {
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH, type("Crunch de bicicleta", "core"))  // no «bicicleta»
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL, type("Curl femoral tumbado", "pierna aislada"))  // no curl de brazo
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL, type("Curl nórdico", "bisagra"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH, type("Curl-up de McGill", "core"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL, type("Curl martillo", "brazos"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST, type("Empuje de cadera con barra (hip thrust)", "bisagra"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK, type("Plancha lateral", "core"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN, type("Jalón al pecho", "tracción vertical"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE, type("Burpee con dominada", "acondicionamiento"))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS, type("Press de hombros con giro", "empuje vertical"))  // no «giro»
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST, type("Giro ruso", "core"))
    }

    @Test fun patternDecidesWhenTheNameSaysNothing() {
        mapOf("sentadilla" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, "bisagra" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
            "zancada" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE, "empuje horizontal" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
            "tracción vertical" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP, "movilidad" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING,
            "yoga" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA).forEach { (pattern, expected) -> assertEquals(expected, type("Ejercicio propio", pattern), pattern) }
    }

    @Test fun unknownExercisesFallBackToWeightlifting() {
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING, type("Ejercicio propio", null))
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING, type("Ejercicio propio", "gemelo"))
    }
}
