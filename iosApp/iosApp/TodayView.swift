import SwiftUI
import Shared

struct TodayView: View {
    @EnvironmentObject var model: AppModel
    let openGoal: () -> Void
    var openTrain: () -> Void = {}
    @State private var goalOpen = false

    var body: some View {
        let _ = model.version
        let screen = model.core.todayScreen()
        let day = screen.day
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Hoy", subtitle: screen.dateLabel)
                if let goal = screen.goalLine {
                    Secondary(goal)
                    Button("Tu objetivo") { goalOpen = true }.buttonStyle(TextActionStyle())
                }
                if screen.noPlan { Button("Quiero un plan de Brivel", action: openGoal).buttonStyle(TextActionStyle()) }
                if screen.goalFinished {
                    Module {
                        Text("Tu carrera ya pasó").font(.section)
                        Secondary("¿Qué quieres ahora? Otra carrera, mantener la forma o descansar unos días.")
                        Button("Elegir objetivo", action: openGoal).buttonStyle(PrimaryStyle())
                    }
                }
                // Tu cuerpo hoy en una frase: sale de la misma decisión que la sesión
                if !screen.stateTitle.isEmpty {
                    StateCard(tone: screen.tone, title: screen.stateTitle, detail: screen.stateDetail, source: screen.garminNote)
                }
                if let run = screen.rateRun { RateRunCard(run: run) }
                if screen.noPlan && day.items.isEmpty { NoRoutineToday(next: screen.nextRoutine, openTrain: openTrain) }
                DayContent(day: day, linked: model.core.watchRoute != nil, today: true)
                // Widgets (TodayWidgets.swift): las preguntas y la semana de serie, y los que se añadan
                TodayWidgets(screen: screen)
            }.padding(.horizontal, 18).padding(.vertical, 16)
        }.background(Palette.bg)
        .sheet(isPresented: $goalOpen) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(model.core.data.goal.map { TextsKt.goalLabel(goal: $0) } ?? "Sin objetivo").font(.screenTitle)
                    Secondary("Puedes cambiarlo o abandonarlo cuando quieras; el plan se rehace al momento.")
                    Button("Cambiar objetivo") { goalOpen = false; openGoal() }.buttonStyle(SecondaryStyle())
                    OtherRacesView()
                }.padding(20)
            }.background(Palette.surface)
        }
    }
}

/// Las sesiones de un día con sus acciones: hecha, cambiar de deporte, enviar al reloj, hoy no puedo.
struct DayContent: View {
    @EnvironmentObject var model: AppModel
    let day: DayView
    let linked: Bool
    let today: Bool

    var body: some View {
        let core = model.core
        VStack(alignment: .leading, spacing: 14) {
            // En Hoy la tarjeta de estado ya dice que descansas: aquí solo lo que se puede hacer
            let changeable = core.canChangeDay(date: day.date)
            if day.items.isEmpty && (!today || changeable) {
                Module {
                    if !today { Text("Descanso").font(.section) }
                    if !today, let note = day.note { Secondary(note) }
                    if changeable {
                        Button(today ? "Prefiero entrenar hoy" : "Prefiero entrenar este día") {
                            model.run { try core.changeDay(date: day.date, minutes: core.minutesToTrain(day: day)) }
                        }.buttonStyle(SecondaryStyle())
                    }
                }
            }
            ForEach(Array(day.items.enumerated()), id: \.offset) { _, item in
                SessionItem(day: day, item: item, linked: linked, today: today)
            }
            ForEach(Array(day.matches.enumerated()), id: \.offset) { _, m in
                ReasonRow(reason: Reason(text: m.text, evidence: nil), color: m.counts ? Palette.ok : Palette.caution)
            }
            // En Hoy, sin ajuste, los motivos ya van en la tarjeta de estado
            if !today || !day.adjustments.isEmpty {
                ForEach(Array(day.reasons.enumerated()), id: \.offset) { _, r in ReasonRow(reason: r, color: Palette.ok) }
            }
            if changeable && !day.items.isEmpty {
                Button(today ? "Hoy no puedo entrenar" : "Este día no puedo entrenar") { model.run { try core.changeDay(date: day.date, minutes: 0) } }.buttonStyle(TextActionStyle(color: Palette.muted))
                    .font(.ui(14, .semibold)).foregroundStyle(Palette.muted)
            }
        }
    }
}

private struct SessionItem: View {
    @EnvironmentObject var model: AppModel
    let day: DayView
    let item: PlanItem
    let linked: Bool
    let today: Bool
    @State private var open = false
    @State private var moving = false
    @State private var choosingWatch = false

    private func sendToRoute() {
        model.runAsync(model.core.watchSentText) { _ = try await model.core.sendToday(sport: item.sport) }
    }

    private func sendToAppleWatch() {
        model.runAsync("Enviada al Apple Watch: la tienes en la app Entreno del reloj") {
            _ = try await AppleWatchSender.send(try model.core.watchWorkouts(sport: item.sport))
        }
    }

    var body: some View {
        let core = model.core
        let done = day.doneSports.contains(item.slot) || day.doneSports.contains(item.sport)
        SessionCard(item: item, done: done) {
            FitRow(spacing: 10) {
                if item.isStrength && (day.isPast || today) {
                    // Apuntar las series ya la cuenta como hecha
                    Button { model.strengthDate = day.date } label: {
                        Text(done ? "Ver las series" : "Empezar").frame(maxWidth: .infinity)
                    }.buttonStyle(PrimaryStyle())
                    if !done { Button("La he hecho") { model.run { try core.markDone(date: day.date, sport: item.sport) } }.buttonStyle(SecondaryStyle()) }
                } else if !done && (day.isPast || today) {
                    Button { model.run { try core.markDone(date: day.date, sport: item.sport) } } label: {
                        Text("La he hecho").frame(maxWidth: .infinity)
                    }.buttonStyle(PrimaryStyle())
                }
                if !done && !day.isPast && !item.alternatives.isEmpty {
                    Button(open ? "Cerrar" : "Cambiar") { open.toggle() }.buttonStyle(SecondaryStyle())
                }
            }
            if open {
                ForEach(Array(item.alternatives.enumerated()), id: \.offset) { _, alt in
                    HStack(spacing: 10) {
                        SportBadge(sport: alt.sport, size: 32)
                        Text("\(alt.title) · \(alt.durationMin)′").font(.small)
                        Spacer()
                        Button("Haré esta") {
                            model.run { try core.chooseAlternative(date: day.date, plannedSport: item.slot, chosenSport: alt.sport) }
                        }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
                    }
                }
            }
            // Un solo botón: si hay más de un destino (Apple Watch y Garmin Connect o intervals.icu), pregunta adónde
            if today && (linked || AppleWatchSender.supported) {
                Button {
                    if linked && AppleWatchSender.supported { choosingWatch = true } else if linked { sendToRoute() } else { sendToAppleWatch() }
                } label: {
                    HStack(spacing: 6) { Glyph(name: "watch", size: 18, color: Palette.ok); Text("Enviar al reloj") }
                }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
                .confirmationDialog("¿Adónde la envías?", isPresented: $choosingWatch, titleVisibility: .visible) {
                    Button("Apple Watch") { sendToAppleWatch() }
                    Button(core.watchRoute ?? "Reloj") { sendToRoute() }
                    Button("Cancelar", role: .cancel) {}
                }
            }
            if let kind = item.testKind, today || day.isPast { TestResultButton(date: day.date, kind: kind) }
            if item.chosen { Button("Volver a la planificada") { model.run { core.clearChoice(date: day.date) } }.font(.ui(14, .bold)).buttonStyle(TextActionStyle()) }
            if let from = item.movedFrom { Secondary("La has movido aquí desde el \(ScreensKt.dateLabel(date: from).lowercased()).") }
            let targets = done || day.isPast ? [] : core.moveTargets(date: day.date, slot: item.slot)
            if !targets.isEmpty {
                Button(moving ? "No moverla" : "Moverla a otro día") { moving.toggle() }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
            }
            if moving {
                ForEach(targets, id: \.self) { t in
                    Button(ScreensKt.dateLabel(date: t) + (t == item.movedFrom ? " (su día)" : "")) {
                        moving = false
                        model.run { try core.moveSession(date: day.date, slot: item.slot, to: t) }
                    }.buttonStyle(SecondaryStyle())
                }
            }
        }
    }
}

/// Sin plan y sin rutina hoy: la próxima, tus rutinas para hacer una hoy o un entreno libre; sin rutinas, crear la primera.
private struct NoRoutineToday: View {
    @EnvironmentObject var model: AppModel
    let next: String?
    let openTrain: () -> Void

    private func start(_ routineId: String?) {
        model.run { model.strengthDate = try model.core.startWorkout(routineId: routineId).date }
    }

    var body: some View {
        let routines = model.core.routines
        Module {
            if routines.isEmpty {
                Text("Empieza por tu rutina").font(.cardTitle)
                Secondary("Apunta sus ejercicios y los días que la haces. Esos días te saldrá aquí, lista para empezar, y Brivel guardará cada serie.")
                Button(action: openTrain) { Text("Crear mi rutina").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
                Button { start(nil) } label: { Text("Entrenar sin rutina").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
            } else {
                Text("Hoy no te toca rutina").font(.cardTitle)
                if let next { Secondary("\(next). Si te apetece entrenar hoy, elige una o empieza en blanco.") }
                ForEach(routines, id: \.id) { r in
                    Button { start(r.id) } label: { Text("Hacer «\(r.name)» hoy").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
                }
                Button { start(nil) } label: { Text("Entreno libre").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
            }
        }
    }
}

/// El resultado de un test de campo (TEST_FIELDS): con él, cada sesión lleva su ritmo, sus vatios o su pulso.
private struct TestResultButton: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let kind: String
    @State private var open = false
    @State private var main = ""
    @State private var hr = ""

    var body: some View {
        let fields = TextsKt.TEST_FIELDS[kind]
        let mainLabel = (fields?.first as? String) ?? "Resultado"
        let hrLabel = fields?.second as? String
        Button { main = ""; hr = ""; open = true } label: { Text("Apuntar el resultado").frame(maxWidth: .infinity) }
            .buttonStyle(PrimaryStyle())
            .alert("Resultado del test", isPresented: $open) {
                TextField(mainLabel, text: $main).keyboardType(.decimalPad)
                if let hrLabel { TextField(hrLabel, text: $hr).keyboardType(.numberPad) }
                Button("Guardar") {
                    let (m, h) = (main, hr)
                    model.run("Hecho: tus sesiones ya llevan cifras") {
                        try model.core.saveTestResult(date: date, kind: kind, main: m, hr: h.isEmpty ? nil : h)
                    }
                }
                Button("Cancelar", role: .cancel) {}
            } message: { Text(mainLabel) }
    }
}

/// Quien empieza: cómo fue su última carrera. Con las fáciles, Brivel aprende su ritmo suave (sin test).
private struct RateRunCard: View {
    @EnvironmentObject var model: AppModel
    let run: RunToRate

    var body: some View {
        Module {
            Text(run.question).font(.section)
            if let source = run.source { Source(text: source) }  // «Garmin [modelo]» bajo el título (normas de marca de Garmin)
            Text(run.detail).font(.mono(16, relativeTo: .body))
            Flow(spacing: 8) {
                ForEach(FieldTestsKt.EFFORTS, id: \.self) { e in
                    Button(TextsKt.EFFORT_LABEL[e] ?? e) {
                        model.run { model.message = try model.core.rateRun(id: run.id, effort: e) }
                    }.buttonStyle(SecondaryStyle())
                }
            }
            Secondary(run.note)
        }
    }
}
