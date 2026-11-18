package app.brivel.app

// El plan entero de un vistazo (docs/VISION.md §9, bloque D): cada semana hasta la carrera con su fase, sus minutos y su
// sesión más larga; sin fecha, las cuatro semanas del bloque. Sale del mismo planificador que Hoy y el Calendario.

import app.brivel.engine.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Una semana del plan. `phase`: la fase en llano (o «Semana 2 de 4» sin fecha); `key`: su sesión más larga que no es de
 * fuerza; `race`: la carrera que cae en esa semana.
 */
@Serializable
data class PlanWeek(val monday: String, val title: String, val phase: String?, val minutes: Int, val sessions: Int,
    val strengthDays: Int, val key: String?, val isCurrent: Boolean, val race: String? = null)

@Serializable
data class PlanOverview(val title: String, val subtitle: String, val weeks: List<PlanWeek>)

/** Semanas que se enseñan como mucho: un plan de carrera de un año. */
private const val MAX_WEEKS = 52

/** null sin objetivo. Las semanas pasadas no salen: el Calendario ya enseña lo que se hizo. */
fun buildPlanOverview(data: AppData, today: LocalDate): PlanOverview? {
    val goal = data.goal?.takeIf { it.kind != GoalKind.OWN } ?: return null  // sin plan no hay plan entero
    val planner = Planner(data, today)
    val race = planner.raceGoal()?.takeIf { it.date >= today }
    val first = mondayOf(today)
    val now = planner.week(first)
    val mondays = if (race != null) generateSequence(first) { it.plusDays(7) }.takeWhile { it <= race.date }.take(MAX_WEEKS).toList()
        else (0..3).map { first.plusDays(7 * (it - (now.weekInBlock ?: 0))) }.filter { it >= first }  // weekInBlock va de 0 a 3
    val races = if (race != null) listOf(race.name to race.date) + race.secondary.map { it.name to it.date } else emptyList()
    val weeks = mondays.map { monday ->
        val w = if (monday == first) now else planner.week(monday)
        val items = w.days.flatMap { it.items }
        val phase = w.days.firstNotNullOfOrNull { it.phase }?.let { Planner.PHASE_LABEL[it] ?: it }
            ?: w.weekInBlock?.let { "Semana ${it + 1} de 4" + if (it == 3) " (más suave)" else "" }
        PlanWeek(monday.toString(), "${monday.dayOfMonth} de ${MONTH_LABEL[monday.monthNumber - 1]}", phase,
            items.sumOf { it.durationMin }, items.size, w.strengthDays,
            items.filterNot { it.isStrength }.maxByOrNull { it.durationMin }?.let { "${it.title} · ${it.durationMin} min" },
            monday == first, races.filter { (_, d) -> d >= monday && d < monday.plusDays(7) }.joinToString(" y ") { it.first }.ifEmpty { null })
    }
    val subtitle = if (race != null) "${weeks.size} ${if (weeks.size == 1) "semana" else "semanas"} hasta ${race.name}"
        else "Bloque ${(now.block ?: 0) + 1}: lo que queda de sus cuatro semanas. Después empieza otro, ajustado a lo que hayas hecho."
    return PlanOverview(goalLabel(goal), subtitle, weeks)
}
