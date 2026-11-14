// Forma del JSON de TodayScreen (shared/.../app/Screens.kt y Planner.kt): solo los campos que pinta la web
export type Tone = "OK" | "CAUTION" | "REST"
export interface Reason { text: string; evidence: string | null }
export interface Step { name: string; reps: number; minutes: number; intensityZone: string; target: string | null; meters: number | null }
export interface PlanItem { sport: string; title: string; hint: string; durationMin: number; steps: Step[]; exercises: string[]; notes: string | null; chosen: boolean; slot: string
  alternatives: PlanItem[]; movedFrom: string | null }
export interface DayView { date: string; isToday: boolean; isPast: boolean; items: PlanItem[]; note: string | null; reasons: Reason[]; adjustments: Reason[]; doneSports: string[] }
export interface WeekDot { letter: string; date: string; isToday: boolean; planned: boolean; done: boolean }
export interface TodayScreen {
  dateLabel: string; goalLine: string | null; stateTitle: string; stateDetail: Reason[]; tone: Tone; day: DayView
  meetsWho: boolean; weekMinutes: number; strengthDays: number; weekDots: WeekDot[]; warnings: string[]; info: string[]
  askReadiness: boolean; goalFinished: boolean; garminNote: string | null
}
// Sesión de fuerza serie a serie (Workout.kt) y figura de su movimiento (Figures.kt); reps son segundos si timed
export interface LoggedSet { set: number; weightKg: number | null; reps: number; rir: number | null; kind: string | null }
export interface StrengthExercise { name: string; pattern: string; sets: number; repsLow: number; repsHigh: number; timed: boolean
  restSeconds: number; rir: number | null; suggestion: string | null; weightKg: number | null; previous: LoggedSet[]; done: LoggedSet[]
  original: string | null; superset: string | null; barbell: boolean }
export interface ExerciseCard { name: string; pattern: string; patternLabel: string; equipment: string[]; timed: boolean
  cues: string[]; cueSource: string; figure: string; zone: string; muscles: string[]; description: string }
export interface StrengthScreen { date: string; title: string; exercises: StrengthExercise[]; other: string[]; notes: string | null; free: boolean }
export interface CustomExercise { name: string; pattern: string; timed: boolean }
export interface RoutineExercise { name: string; sets: number; low: number; high: number; linked: boolean }
// Entrenar (Training.kt): entreno libre de hoy, la serie que toca y el historial de un ejercicio
export interface FreeWorkout { date: string; name: string; exercises: RoutineExercise[]; rir: number }
export interface NextSet { exercise: number; set: number }
export interface ExerciseDay { date: string; best: string; sets: number; volumeKg: number; e1rm: number | null }
export interface ExerciseHistory { name: string; days: ExerciseDay[]; chart: Metric | null; records: Milestone[] }
export interface Routine { id: string; name: string; exercises: RoutineExercise[]; days: string[]; rir: number; since: string }
export interface FigureLines { head: [number, number]; body: number[][]; work: number[][] }

// Calendario y Progreso (Screens.kt): solo lo que pinta la web
export interface DoneActivity { title: string; sport: string; minutes: number; start: string | null; source: string | null }
export interface CalendarDay { date: string; dayOfMonth: number; inMonth: boolean; isToday: boolean; planned: PlanItem[]; done: DoneActivity[]; manual: string[] }
export interface MonthScreen { title: string; year: number; month: number; weeks: CalendarDay[][] }
export interface Point { date: string; value: number }
export interface Metric { key: string; label: string; unit: string; explanation: string; points: Point[]; latest: number | null
  normalLow: number | null; normalHigh: number | null; higherIsBetter: boolean | null; source: string | null }
export interface RecentActivity { date: string; title: string; sport: string; minutes: number; detail: string | null; zoneMinutes: number[]; source: string | null }
export interface ProgressScreen { linked: boolean; metrics: Metric[]; weeklyMinutes: Point[]; status: { label: string; text: string } | null
  predictions: { distance: string; time: string }[]; predictionSource: string | null; recent: RecentActivity[]; garminNote: string | null
  milestones: Milestone[]; strengthRecords: Milestone[]; thresholds: { label: string; value: string; explanation: string }[]
  measures: Metric[] }
export interface Milestone { title: string; value: string; date: string | null; source: string | null }
