// Forma del JSON de TodayScreen (shared/.../app/Screens.kt y Planner.kt): solo los campos que pinta la web
export type Tone = "OK" | "CAUTION" | "REST"
export interface Reason { text: string; evidence: string | null }
export interface Step { name: string; reps: number; minutes: number; intensityZone: string; target: string | null; meters: number | null }
export interface PlanItem { sport: string; title: string; hint: string; durationMin: number; steps: Step[]; exercises: string[]; chosen: boolean; slot: string
  alternatives: PlanItem[] }
export interface DayView { date: string; isPast: boolean; items: PlanItem[]; reasons: Reason[]; adjustments: Reason[]; doneSports: string[] }
export interface WeekDot { letter: string; date: string; isToday: boolean; planned: boolean; done: boolean }
export interface TodayScreen {
  dateLabel: string; goalLine: string | null; stateTitle: string; stateDetail: Reason[]; tone: Tone; day: DayView
  meetsWho: boolean; weekMinutes: number; strengthDays: number; weekDots: WeekDot[]; warnings: string[]; info: string[]
  askReadiness: boolean; goalFinished: boolean
}
// Sesión de fuerza serie a serie (Workout.kt) y figura de su movimiento (Figures.kt); reps son segundos si timed
export interface LoggedSet { set: number; weightKg: number | null; reps: number }
export interface StrengthExercise { name: string; pattern: string; sets: number; repsLow: number; repsHigh: number; timed: boolean
  restSeconds: number; suggestion: string | null; weightKg: number | null; previous: LoggedSet[]; done: LoggedSet[] }
export interface StrengthScreen { date: string; title: string; exercises: StrengthExercise[]; other: string[] }
export interface FigureLines { head: [number, number]; body: number[][]; work: number[][] }

// Calendario y Progreso (Screens.kt): solo lo que pinta la web
export interface DoneActivity { title: string; sport: string; minutes: number; start: string | null }
export interface CalendarDay { date: string; dayOfMonth: number; inMonth: boolean; isToday: boolean; planned: PlanItem[]; done: DoneActivity[]; manual: string[] }
export interface MonthScreen { title: string; year: number; month: number; weeks: CalendarDay[][] }
export interface Point { date: string; value: number }
export interface Metric { key: string; label: string; unit: string; explanation: string; points: Point[]; latest: number | null
  normalLow: number | null; normalHigh: number | null; higherIsBetter: boolean | null }
export interface RecentActivity { date: string; title: string; sport: string; minutes: number; detail: string | null; zoneMinutes: number[] }
export interface ProgressScreen { linked: boolean; metrics: Metric[]; weeklyMinutes: Point[]; status: { label: string; text: string } | null
  predictions: { distance: string; time: string }[]; predictionSource: string | null; recent: RecentActivity[] }
