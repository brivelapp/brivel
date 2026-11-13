import Foundation
import HealthKit
import WorkoutKit
import Shared

/// Las sesiones de hoy, a la app Entreno del Apple Watch con WorkoutKit (iOS 17): sin servidor ni cuenta, de iPhone a reloj.
/// Parte del formato del reloj del núcleo (Watch.kt), el mismo que va a Garmin.
enum AppleWatchSender {
    static var supported: Bool { WorkoutScheduler.isSupported }

    // Pulsaciones por minuto como unidad de frecuencia (1 ppm = 1/60 Hz)
    private static let bpm = UnitFrequency(symbol: "bpm", converter: UnitConverterLinear(coefficient: 1.0 / 60.0))

    /// Programa las sesiones para su día y quita lo que Brivel hubiera programado antes ese día. Devuelve cuántas ha mandado.
    static func send(_ workouts: [WatchWorkout]) async throws -> Int {
        guard supported else { throw failure("Este iPhone no puede programar entrenos en un Apple Watch") }
        let scheduler = WorkoutScheduler.shared
        var state = await scheduler.authorizationState
        if state == .notDetermined { state = await scheduler.requestAuthorization() }
        guard state == .authorized else {
            throw failure("Brivel no tiene permiso para programar entrenos. Dáselo en Ajustes → Privacidad y seguridad → Salud")
        }
        let plans = workouts.compactMap { w in plan(w).map { (w, $0) } }
        guard !plans.isEmpty else { throw failure("El Apple Watch no admite este tipo de sesión como entreno personalizado") }
        for (w, _) in plans {
            guard let day = day(w.date) else { continue }
            // Solo lo de ese día y ese deporte: lo demás que Brivel haya mandado sigue en el reloj
            for old in await scheduler.scheduledWorkouts where old.date.year == day.year && old.date.month == day.month
                && old.date.day == day.day && activity(of: old.plan) == kind(w.sport).0 {
                await scheduler.remove(old.plan, at: old.date)
            }
        }
        for (w, plan) in plans {
            guard let day = day(w.date) else { continue }
            await scheduler.schedule(plan, at: day)
        }
        return plans.count
    }

    private static func failure(_ text: String) -> NSError {
        NSError(domain: "Brivel", code: 1, userInfo: [NSLocalizedDescriptionKey: text])
    }

    private static func day(_ iso: String) -> DateComponents? {
        let p = iso.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3 else { return nil }
        // Hoy, dentro de un minuto: una hora ya pasada podría no salir en el reloj; otro día, a primera hora
        let soon = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: Date().addingTimeInterval(60))
        if soon.year == p[0], soon.month == p[1], soon.day == p[2] { return soon }
        return DateComponents(year: p[0], month: p[1], day: p[2], hour: 6)
    }

    private static func activity(of plan: WorkoutPlan) -> HKWorkoutActivityType? {
        if case .custom(let c) = plan.workout { return c.activity }
        return nil
    }

    private static func kind(_ sport: String) -> (HKWorkoutActivityType, HKWorkoutSessionLocationType) {
        switch sport {
        case "running": return (.running, .outdoor)
        case "cycling": return (.cycling, .outdoor)
        case "swimming": return (.swimming, .indoor)
        case "walking": return (.walking, .outdoor)
        case "strength": return (.traditionalStrengthTraining, .indoor)
        default: return (.other, .unknown)
        }
    }

    /// nil: el reloj no admite ese deporte como entreno personalizado.
    private static func plan(_ w: WatchWorkout) -> WorkoutPlan? {
        let (activity, location) = kind(w.sport)
        guard CustomWorkout.supportsActivity(activity) else { return nil }
        var warmup: WorkoutStep?
        var cooldown: WorkoutStep?
        var blocks: [IntervalBlock] = []
        for (i, b) in w.blocks.enumerated() {
            let steps = b.steps
            // El calentamiento y la vuelta a la calma tienen su sitio propio si van solos al principio y al final
            if steps.count == 1, b.times == 1, let s = steps.first {
                if i == 0, s.kind == "warmup" { warmup = step(s, activity, location); continue }
                if i == w.blocks.count - 1, s.kind == "cooldown" { cooldown = step(s, activity, location); continue }
            }
            let interval = steps.map { s in
                IntervalStep(s.kind == "rest" || s.kind == "recovery" ? .recovery : .work, step: step(s, activity, location))
            }
            blocks.append(IntervalBlock(steps: interval, iterations: Int(b.times)))
        }
        let custom = CustomWorkout(activity: activity, location: location, displayName: w.name, warmup: warmup, blocks: blocks,
                                   cooldown: cooldown)
        return WorkoutPlan(.custom(custom), id: UUID())
    }

    private static func step(_ s: WatchStep, _ activity: HKWorkoutActivityType, _ location: HKWorkoutSessionLocationType) -> WorkoutStep {
        var goal: WorkoutGoal = .open  // las repeticiones no existen como meta: el paso se cierra en el reloj
        if let seconds = s.seconds { goal = .time(Double(truncating: seconds), .seconds) }
        else if let meters = s.meters { goal = .distance(Double(truncating: meters), .meters) }
        if !CustomWorkout.supportsGoal(goal, activity: activity, location: location) { goal = .open }

        var alert: (any WorkoutAlert)?
        if let t = s.target, t.low < t.high {
            switch t.metric {
            case "hr":
                alert = HeartRateRangeAlert(target: Measurement(value: t.low, unit: bpm)...Measurement(value: t.high, unit: bpm))
            case "power":
                alert = PowerRangeAlert(target: Measurement(value: t.low, unit: UnitPower.watts)...Measurement(value: t.high, unit: UnitPower.watts))
            case "pace":  // s/km → m/s: el ritmo más lento es la velocidad más baja
                alert = SpeedRangeAlert(target: Measurement(value: 1000 / t.high, unit: UnitSpeed.metersPerSecond)...Measurement(value: 1000 / t.low, unit: UnitSpeed.metersPerSecond),
                                        metric: .current)
            default: break
            }
        }
        if let a = alert, !CustomWorkout.supportsAlert(a, activity: activity, location: location) { alert = nil }

        var out = WorkoutStep(goal: goal, alert: alert)
        if #available(iOS 18.0, *), let name = label(s) { out.displayName = name }
        return out
    }

    /// Lo que se lee en el reloj en un paso de fuerza: «Sentadilla con barra · 10 × 60 kg».
    private static func label(_ s: WatchStep) -> String? {
        guard let exercise = s.exercise else { return s.kind == "rest" ? "Descanso" : nil }
        var dose: [String] = []
        if let reps = s.reps { dose.append("\(reps.intValue)") }
        if let kg = s.weightKg { dose.append("\(kg.doubleValue.formatted(.number.precision(.fractionLength(0...1)))) kg") }
        return dose.isEmpty ? exercise : "\(exercise) · \(dose.joined(separator: " × "))"
    }
}
