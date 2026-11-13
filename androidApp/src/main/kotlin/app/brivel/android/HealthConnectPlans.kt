package app.brivel.android

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseCompletionGoal
import androidx.health.connect.client.records.ExercisePerformanceTarget
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.PlannedExerciseBlock
import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import androidx.health.connect.client.records.PlannedExerciseStep
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import app.brivel.app.BrivelApp
import app.brivel.app.WatchStep
import app.brivel.app.WatchWorkout
import java.time.Duration
import java.time.LocalDate

/**
 * Las sesiones de hoy, a Health Connect como entrenamientos planificados (Android 14 o posterior): el reloj o la app que
 * los lea allí los enseña. Sin servidor ni cuenta. Parte del formato del reloj del núcleo (Watch.kt), el mismo que va a Garmin.
 */
class HealthConnectPlanner(private val context: Context) {
    val permission: String = HealthPermission.getWritePermission(PlannedExerciseSessionRecord::class)

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** Health Connect está y admite entrenamientos planificados. */
    val available: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_PLANNED_EXERCISE) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun granted(): Boolean = permission in client.permissionController.getGrantedPermissions()

    /**
     * Escribe las sesiones en su día. Cada una lleva un identificador propio (día y deporte): reenviarla sustituye la
     * anterior y no toca las de otros deportes de ese día.
     */
    suspend fun send(app: BrivelApp, workouts: List<WatchWorkout>) {
        client.insertRecords(workouts.map { record(app, it) })
    }

    private fun record(app: BrivelApp, w: WatchWorkout): PlannedExerciseSessionRecord {
        val (session, segment) = SPORTS[w.sport] ?: (ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT to ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT)
        val blocks = w.blocks.map { b ->
            PlannedExerciseBlock(b.times, b.steps.map { step(app, it, segment) }, null)
        }
        // Lo que dura si cada paso abierto o por repeticiones lleva un minuto: Health Connect pide una duración
        val seconds = w.blocks.sumOf { b -> b.times * b.steps.sumOf { it.seconds ?: 60 } }.coerceAtLeast(60)
        // La versión crece con el tiempo: Health Connect se queda con la última escritura de ese identificador
        val metadata = Metadata.manualEntry("brivel-${w.date}-${w.sport}", System.currentTimeMillis())
        return PlannedExerciseSessionRecord(metadata, LocalDate.parse(w.date), Duration.ofSeconds(seconds.toLong()),
            blocks, session, w.name, w.description.take(1000))
    }

    private fun step(app: BrivelApp, s: WatchStep, sport: Int): PlannedExerciseStep {
        val rest = s.kind == "rest"
        val type = when {
            rest -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
            s.exercise != null -> exerciseType(s.exercise!!, app.watchPattern(s.exercise!!))
            else -> sport
        }
        val phase = when (s.kind) {
            "warmup" -> PlannedExerciseStep.EXERCISE_PHASE_WARMUP
            "cooldown" -> PlannedExerciseStep.EXERCISE_PHASE_COOLDOWN
            "recovery" -> PlannedExerciseStep.EXERCISE_PHASE_RECOVERY
            "rest" -> PlannedExerciseStep.EXERCISE_PHASE_REST
            else -> PlannedExerciseStep.EXERCISE_PHASE_ACTIVE
        }
        val goal = when {
            s.seconds != null -> ExerciseCompletionGoal.DurationGoal(Duration.ofSeconds(s.seconds!!.toLong()))
            s.meters != null -> ExerciseCompletionGoal.DistanceGoal(Length.meters(s.meters!!.toDouble()))
            s.reps != null -> ExerciseCompletionGoal.RepetitionsGoal(s.reps!!)
            else -> ExerciseCompletionGoal.ManualCompletion
        }
        val targets = buildList {
            s.target?.takeIf { it.low < it.high }?.let { t ->
                when (t.metric) {
                    "hr" -> add(ExercisePerformanceTarget.HeartRateTarget(t.low, t.high))
                    "power" -> add(ExercisePerformanceTarget.PowerTarget(Power.watts(t.low), Power.watts(t.high)))
                    // s/km → m/s: el ritmo más lento es la velocidad más baja
                    "pace" -> add(ExercisePerformanceTarget.SpeedTarget(Velocity.metersPerSecond(1000 / t.high), Velocity.metersPerSecond(1000 / t.low)))
                }
            }
            s.weightKg?.takeIf { it > 0 }?.let { add(ExercisePerformanceTarget.WeightTarget(Mass.kilograms(it))) }
        }
        return PlannedExerciseStep(type, phase, goal, targets, listOfNotNull(s.exercise, s.note).joinToString(" · ").take(500).ifEmpty { null })
    }

    companion object {
        private val SPORTS = mapOf(
            "running" to (ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING),
            "cycling" to (ExerciseSessionRecord.EXERCISE_TYPE_BIKING to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING),
            "swimming" to (ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL),
            "walking" to (ExerciseSessionRecord.EXERCISE_TYPE_WALKING to ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING),
            "strength" to (ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING),
        )

        // Por nombre, los pocos ejercicios que Health Connect distingue; el orden importa (el primero que encaja)
        private val BY_NAME = listOf(
            "crunch" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
            "curl-up" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
            "curl nórdico" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
            "burpee" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE,
            "escaladores" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER,
            "saltos de tijera" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK,
            "comba" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE,
            "swing" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING,
            "golpe de balón" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM,
            "empuje de cadera" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
            "puente de glúteo" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
            "hiperextensión" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
            "extensión de espalda" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
            "prensa de piernas" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
            "extensión de cuádriceps" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION,
            "curl femoral" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
            "elevación de piernas" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
            "plancha" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
            "abdominal" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP,
            "giro ruso" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
            "giro de torso" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
            "jalón" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
            "elevación lateral" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
            "elevación frontal" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE,
            "extensión de tríceps" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
            "patada de tríceps" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
            "press francés" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
            "curl" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
            "pilates" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PILATES,
            "elíptica" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ELLIPTICAL,
            "remo en máquina (remoergómetro)" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE,
            "bicicleta" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY,
            "cinta de correr" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL,
            "escaladora" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE,
            "subir escaleras" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING,
            "boxeo" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PUNCH,
        )

        // Si el nombre no dice más, el tipo más cercano al patrón del ejercicio
        private val BY_PATTERN = mapOf(
            "sentadilla" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
            "bisagra" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
            "zancada" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
            "empuje horizontal" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
            "empuje vertical" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
            "tracción horizontal" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
            "tracción vertical" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
            "core" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
            "brazos" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
            "hombros" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
            "acondicionamiento" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            "movilidad" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING,
            "yoga" to ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA,
        )

        /** El tipo de ejercicio de Health Connect más cercano; el nombre exacto va siempre en la descripción del paso. */
        internal fun exerciseType(name: String, pattern: String?): Int {
            val lower = name.lowercase()
            return BY_NAME.firstOrNull { lower.contains(it.first) }?.second ?: BY_PATTERN[pattern]
                ?: ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        }
    }
}

/**
 * «Enviar al reloj», un solo botón: si hay más de un destino (Garmin Connect o intervals.icu, y Health Connect), pregunta
 * adónde. Health Connect pide el permiso de escribir entrenamientos planificados la primera vez.
 */
@Composable
fun SendToWatch(app: BrivelApp, act: Act, sport: String) {
    val context = LocalContext.current
    val planner = remember { HealthConnectPlanner(context) }
    val healthConnect = remember { runCatching { planner.available }.getOrDefault(false) }
    val route = app.watchRoute
    if (route == null && !healthConnect) return
    var choosing by remember { mutableStateOf(false) }
    fun toRoute() = act.suspend(app.watchSentText) { app.sendToday(sport) }
    fun toHealthConnect() = act.suspend("Enviada a Health Connect: la verás en el reloj o la app que lea allí los entrenamientos planificados") {
        planner.send(app, app.watchWorkouts(sport))
    }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (planner.permission in granted) toHealthConnect()
    }
    fun askHealthConnect() = act.suspend(null) { if (planner.granted()) toHealthConnect() else request.launch(setOf(planner.permission)) }
    TextButton(onClick = { if (route != null && healthConnect) choosing = true else if (route != null) toRoute() else askHealthConnect() }) {
        Glyph(R.drawable.ic_watch, 18.dp, Palette.ok)
        Spacer(Modifier.width(6.dp))
        LinkText("Enviar al reloj")
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, containerColor = Palette.surface,
        title = { Text("¿Adónde la envías?") },
        text = {
            Column {
                TextButton(onClick = { choosing = false; toRoute() }) { LinkText(route ?: "") }
                TextButton(onClick = { choosing = false; askHealthConnect() }) { LinkText("Health Connect") }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancelar") } })
}
