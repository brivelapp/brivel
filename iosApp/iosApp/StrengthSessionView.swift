import SwiftUI
import Shared

// Sesión de fuerza a pantalla completa: Serie (una cada vez, números grandes) y Sesión (toda la sesión en tabla)

/// Figura del patrón de movimiento: cuerpo en el color del texto y, en verde, lo que trabaja.
struct ExerciseFigure: View {
    let pattern: String
    var size: CGFloat = 52

    var body: some View {
        if let figure = FiguresKt.FIGURES[pattern] {
            Canvas { context, canvas in
                let k = canvas.width / 48
                func draw(_ d: String, _ color: Color, _ width: CGFloat) {
                    for line in FiguresKt.polylines(path: d) {
                        var path = Path()
                        for (i, p) in line.enumerated() {
                            let point = CGPoint(x: CGFloat(truncating: p.first!) * k, y: CGFloat(truncating: p.second!) * k)
                            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
                        }
                        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width * k, lineCap: .round, lineJoin: .round))
                    }
                }
                draw(figure.body, Palette.text, 2.2)
                draw(figure.work, Palette.ok, 3)
                let head = CGRect(x: (CGFloat(figure.headX) - 3) * k, y: (CGFloat(figure.headY) - 3) * k, width: 6 * k, height: 6 * k)
                context.stroke(Path(ellipseIn: head), with: .color(Palette.text), lineWidth: 2.2 * k)
            }
            .frame(width: size, height: size)
            .accessibilityHidden(true)
        }
    }
}

private func kgText(_ w: Double?) -> String { w.map { LiftsKt.kg(v: $0).replacingOccurrences(of: " kg", with: "") } ?? "Tu peso" }
private func amount(_ reps: Int, timed: Bool) -> String { timed ? "\(reps)″" : "\(reps)" }
private func setText(_ s: LoggedSet, timed: Bool) -> String {
    (s.weightKg.map { "\(kgText($0.doubleValue)) × " } ?? "") + amount(Int(s.reps), timed: timed)
}
private func effort(_ rir: Int) -> String { rir == 0 ? "Hasta no poder más" : "Termina con \(rir) en reserva" }
private func clock(_ s: Int) -> String { String(format: "%d:%02d", s / 60, s % 60) }

struct StrengthSessionView: View {
    @EnvironmentObject var model: AppModel
    let date: String
    let close: () -> Void
    @State private var mode = "set"
    @State private var current = -1  // -1: aún sin colocar en la primera serie que falta
    @State private var setNo = 1
    @State private var restEnd: Date? = nil
    @State private var restTotal = 0
    @State private var logged: (StrengthExercise, LoggedSet)? = nil  // la serie recién apuntada, en el descanso
    @State private var restDone = 0  // sube al terminar cada descanso: vibración
    // Tras "Serie hecha", el descanso a pantalla; al abrir una serie de la tabla, la serie y el descanso abajo
    @State private var restView = false

    var body: some View {
        let _ = model.version
        if let screen = model.core.strengthScreen(date: date) {
            let exercises = screen.exercises
            VStack(spacing: 0) {
                VStack(spacing: 12) {
                    HStack {
                        Button(action: close) { Glyph(name: "chevron_left", size: 24) }
                            .frame(minWidth: 44, minHeight: 44).accessibilityLabel("Cerrar la sesión")
                        Text(screen.title).font(.ui(18, .heavy, relativeTo: .headline))
                        Spacer()
                        Text("\(exercises.reduce(0) { $0 + $1.done.count })/\(exercises.reduce(0) { $0 + Int($1.sets) }) series")
                            .font(.mono(13, relativeTo: .caption)).foregroundStyle(Palette.muted)
                    }
                    Segmented(options: [("set", "Serie"), ("session", "Sesión")], selected: mode) { mode = $0 }
                }.padding(.horizontal, 18).padding(.top, 12)
                TimelineView(.periodic(from: .now, by: 0.25)) { timeline in
                    let left = restEnd.map { max(0, Int(ceil($0.timeIntervalSince(timeline.date)))) } ?? 0
                    VStack(spacing: 0) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 14) {
                                content(exercises, other: screen.other, left: left)
                            }.padding(.horizontal, 18).padding(.vertical, 14)
                        }
                        if restEnd != nil && (mode == "session" || !restView) { RestBar(left: left, total: restTotal) }
                    }
                    .onChange(of: left) { _, l in if l == 0 && restEnd != nil { restEnd = nil; restDone += 1 } }
                }
            }
            .background(Palette.bg.ignoresSafeArea())
            .sensoryFeedback(.success, trigger: restDone)
            // La pantalla no se apaga entre series
            .onAppear {
                UIApplication.shared.isIdleTimerDisabled = true
                if current < 0 {
                    current = max(0, exercises.firstIndex { $0.done.count < Int($0.sets) } ?? 0)
                    setNo = (exercises.indices.contains(current) ? exercises[current].done.count : 0) + 1
                }
            }
            .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        } else {
            Color.clear.onAppear(perform: close)
        }
    }

    @ViewBuilder
    private func content(_ exercises: [StrengthExercise], other: [String], left: Int) -> some View {
        let e = exercises.indices.contains(current) ? exercises[current] : nil
        let pending = exercises.firstIndex { $0.done.count < Int($0.sets) }
        if mode == "session" {
            ForEach(Array(exercises.enumerated()), id: \.offset) { i, ex in
                SessionRows(e: ex, isCurrent: i == current, currentSet: setNo) { set in current = i; setNo = set; mode = "set"; restView = false }
            }
        } else if restEnd != nil && restView {
            RestView(left: left, total: restTotal, e: e, setNo: setNo, logged: logged,
                     adjust: { s in restEnd = restEnd.map { $0.addingTimeInterval(TimeInterval(s)) }; if let r = restEnd, r <= .now { restEnd = nil } },
                     skip: { restEnd = nil },
                     rate: { rir in
                         guard case let (ex, s)? = logged else { return }
                         model.run { try model.core.logSet(date: date, exercise: ex.name, set: s.set, weightKg: s.weightKg, reps: s.reps, rir: KotlinInt(int: Int32(rir))) }
                         logged = (ex, LoggedSet(set: s.set, weightKg: s.weightKg, reps: s.reps, rir: KotlinInt(int: Int32(rir))))
                     })
        } else if let e, !(setNo > Int(e.sets) && pending == nil) {
            SetView(e: e, setNo: setNo, next: exercises.indices.contains(current + 1) ? exercises[current + 1] : nil,
                    swap: model.core.canSwapExercises(date: date) && e.done.isEmpty ? { (r: String) in
                        model.run("Ejercicio cambiado") { try model.core.swapExercise(date: date, current: e.name, replacement: r) }
                    } : nil) { weight, reps in
                save(e, weight: weight, reps: reps)
            }.id("\(e.name)-\(setNo)")
        } else {
            Finished(other: other, close: close)
        }
    }

    private func save(_ e: StrengthExercise, weight: Double?, reps: Int) {
        let previousRir = e.done.first { Int($0.set) == setNo }?.rir
        let editing = e.done.contains { Int($0.set) == setNo }
        model.run {
            try model.core.logSet(date: date, exercise: e.name, set: Int32(setNo), weightKg: weight.map { KotlinDouble(double: $0) }, reps: Int32(reps), rir: previousRir)
        }
        logged = (e, LoggedSet(set: Int32(setNo), weightKg: weight.map { KotlinDouble(double: $0) }, reps: Int32(reps), rir: previousRir))
        // Siguiente: otra serie de este ejercicio o el primero que falte; sin ninguno, sesión completa
        let fresh = model.core.strengthScreen(date: date)?.exercises ?? []
        let pending = fresh.firstIndex { $0.done.count < Int($0.sets) }
        if !editing && setNo < Int(e.sets) && fresh.indices.contains(current) && !fresh[current].done.contains(where: { Int($0.set) == setNo + 1 }) {
            setNo += 1
        } else if let p = pending {
            current = p; setNo = fresh[p].done.count + 1
        } else {
            setNo = Int(e.sets) + 1
        }
        if !editing && pending != nil {
            restTotal = Int(e.restSeconds)
            restEnd = Date().addingTimeInterval(TimeInterval(e.restSeconds))
            restView = true
        }
    }
}

private struct SetView: View {
    let e: StrengthExercise
    let setNo: Int
    let next: StrengthExercise?
    let swap: ((String) -> Void)?
    let done: (Double?, Int) -> Void
    @State private var sheet: String?  // "card" o "swap"
    @State private var weight: Double?
    @State private var reps: Int
    @State private var typing = false
    @State private var typed = ""

    init(e: StrengthExercise, setNo: Int, next: StrengthExercise?, swap: ((String) -> Void)?, done: @escaping (Double?, Int) -> Void) {
        self.e = e; self.setNo = setNo; self.next = next; self.swap = swap; self.done = done
        let today = e.done.first { Int($0.set) == setNo }
        let previous = e.previous.first { Int($0.set) == setNo }
        _weight = State(initialValue: today?.weightKg?.doubleValue ?? e.weightKg?.doubleValue ?? previous?.weightKg?.doubleValue)
        // Con el mismo peso, las repeticiones de la última vez; si cambia el peso, el mínimo del rango (progresión doble)
        let samePrevious = previous.flatMap { $0.weightKg?.doubleValue == e.weightKg?.doubleValue ? Int($0.reps) : nil }
        _reps = State(initialValue: today.map { Int($0.reps) } ?? samePrevious ?? Int(e.repsLow))
    }

    var body: some View {
        let today = e.done.first { Int($0.set) == setNo }
        let previous = e.previous.first { Int($0.set) == setNo }
        HStack(spacing: 6) {
            ForEach(1...max(Int(e.sets), e.done.count), id: \.self) { n in
                Capsule().fill(e.done.contains { Int($0.set) == n } ? Palette.ok : Palette.chip).frame(height: 5)
            }
        }
        HStack(spacing: 12) {
            ExerciseFigure(pattern: e.pattern).frame(width: 64, height: 64)
                .background(Palette.band).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(e.name).font(.ui(22, .heavy, relativeTo: .title2))
                Secondary("Serie \(setNo) de \(e.sets) · \(e.repsLow)–\(e.repsHigh) \(e.timed ? "segundos" : "repeticiones")")
                if let rir = e.rir { Text(effort(Int(rir.int32Value))).font(.ui(14, .bold)) }
                if let original = e.original { Secondary("En lugar de \(original)") }
            }
        }
        HStack(spacing: 16) {
            Button("Cómo se hace") { sheet = "card" }.font(.ui(14, .bold))
            if swap != nil { Button("Cambiar ejercicio") { sheet = "swap" }.font(.ui(14, .bold)) }
        }
        .sheet(isPresented: Binding(get: { sheet != nil }, set: { if !$0 { sheet = nil } })) {
            ExerciseSheet(name: e.name, original: e.original, onSwap: sheet == "swap" ? swap : nil)
        }
        if let s = e.suggestion {
            Text(s).font(.small).frame(maxWidth: .infinity, alignment: .leading).padding(12)
                .background(Palette.okSoft).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        HStack(spacing: 12) {
            Stepper(label: "kg", value: kgText(weight), onTap: { typed = weight.map { kgText($0) } ?? ""; typing = true },
                    minus: { weight = weight.flatMap { $0 - 2.5 > 0 ? $0 - 2.5 : nil } }, plus: { weight = (weight ?? 0) + 2.5 })
            Stepper(label: e.timed ? "segundos" : "repeticiones", value: amount(reps, timed: e.timed),
                    minus: { reps = max(1, reps - (e.timed ? 5 : 1)) }, plus: { reps = min(600, reps + (e.timed ? 5 : 1)) })
        }
        .alert("Peso", isPresented: $typing) {
            TextField("kg", text: $typed).keyboardType(.decimalPad)
            Button("Guardar") {
                let v = Double(typed.replacingOccurrences(of: ",", with: "."))
                weight = v.flatMap { (0.5...500).contains($0) ? $0 : nil }
            }
            Button("Cancelar", role: .cancel) {}
        } message: { Text("Vacío: con tu peso corporal.") }
        Button { done(weight, reps) } label: { Text(today != nil ? "Guardar cambio" : "Serie hecha").frame(maxWidth: .infinity) }
            .buttonStyle(PrimaryStyle())
        if today == nil, let previous { Secondary("La última vez: \(setText(previous, timed: e.timed))") }
        if let next {
            (Text("Después: ").foregroundColor(Palette.muted) + Text(next.name).bold()).font(.small).padding(.top, 6)
        }
    }
}

private struct Stepper: View {
    let label: String
    let value: String
    var onTap: (() -> Void)? = nil
    let minus: () -> Void
    let plus: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            Secondary(label)
            // Misma altura con "Tu peso" que con un número, para que las dos tarjetas queden parejas
            Text(value).font(.mono(value.count > 5 ? 26 : 38, .semibold, relativeTo: .largeTitle)).frame(height: 48)
                .onTapGesture { onTap?() }
                .accessibilityAddTraits(onTap != nil ? .isButton : [])
                .accessibilityHint(onTap != nil ? "Escribir el peso" : "")
            HStack(spacing: 8) {
                StepButton(text: "−", label: "Menos \(label)", action: minus)
                StepButton(text: "+", label: "Más \(label)", action: plus)
            }
        }
        .frame(maxWidth: .infinity).padding(.vertical, 14).padding(.horizontal, 10)
        .background(Palette.surface).clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct StepButton: View {
    let text: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(.ui(22, .bold)).frame(width: 56, height: 48).background(Palette.chip).clipShape(Capsule())
        }.buttonStyle(.plain).accessibilityLabel(label)
    }
}

private struct RestView: View {
    let left: Int
    let total: Int
    let e: StrengthExercise?
    let setNo: Int
    let logged: (StrengthExercise, LoggedSet)?
    let adjust: (Int) -> Void
    let skip: () -> Void
    let rate: (Int) -> Void

    var body: some View {
        ZStack {
            Circle().stroke(Palette.chip, lineWidth: 14)
            Circle().trim(from: 0, to: CGFloat(left) / CGFloat(max(total, 1)))
                .stroke(Palette.ok, style: StrokeStyle(lineWidth: 14, lineCap: .round)).rotationEffect(.degrees(-90))
            VStack(spacing: 2) {
                Text(clock(left)).font(.mono(44, .semibold, relativeTo: .largeTitle))
                Secondary("descanso de \(clock(total))")
            }
        }
        .frame(width: 220, height: 220).frame(maxWidth: .infinity).padding(.top, 8)
        .accessibilityElement(children: .combine)
        HStack(spacing: 10) {
            Button("−15 s") { adjust(-15) }.buttonStyle(SecondaryStyle())
            Button("+15 s") { adjust(15) }.buttonStyle(SecondaryStyle())
            Button("Saltar", action: skip).buttonStyle(SecondaryStyle())
        }.frame(maxWidth: .infinity)
        if case let (ex, s)? = logged {
            Module(background: Palette.okSoft) {
                Secondary("\(ex.name) · serie \(s.set) apuntada")
                Text(setText(s, timed: ex.timed)).font(.ui(16, .bold))
                // Opcional: con lo que te quedaba, Brivel no sube el peso si fuiste más cerca del fallo de lo indicado
                if ex.rir != nil {
                    Secondary("¿Cuántas más podías hacer?")
                    HStack(spacing: 8) {
                        ForEach(0...4, id: \.self) { n in
                            let on = s.rir?.intValue == n
                            Button { rate(n) } label: {
                                Text(n == 4 ? "4+" : "\(n)").font(.mono(16, .semibold)).frame(maxWidth: .infinity, minHeight: 44)
                                    .foregroundStyle(on ? Palette.onOk : Palette.text)
                                    .background(on ? Palette.ok : Palette.surface).clipShape(Capsule())
                            }
                            .buttonStyle(.plain).accessibilityLabel("Me quedaban \(n == 4 ? "4 o más" : "\(n)")")
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }
            }
        }
        if let e {
            Module {
                Secondary("Ahora")
                Text("\(e.name) · serie \(setNo)").font(.ui(16, .bold))
                if let s = e.done.first(where: { Int($0.set) == setNo }) ?? e.previous.first(where: { Int($0.set) == setNo }) {
                    Secondary("La última vez: \(setText(s, timed: e.timed))")
                }
            }
        }
    }
}

private struct RestBar: View {
    let left: Int
    let total: Int
    var body: some View {
        HStack(spacing: 12) {
            Text("Descanso").font(.ui(16, .bold)).foregroundStyle(Palette.bg)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Palette.muted)
                    Capsule().fill(Palette.ok).frame(width: g.size.width * CGFloat(left) / CGFloat(max(total, 1)))
                }
            }.frame(height: 6)
            Text(clock(left)).font(.mono(17, .semibold)).foregroundStyle(Palette.bg)
        }
        .padding(.horizontal, 18).padding(.vertical, 12).background(Palette.text).clipShape(Capsule())
        .padding(.horizontal, 18).padding(.vertical, 12)
    }
}

/// Un ejercicio en la vista Sesión: una fila por serie con la última vez, lo de hoy y la serie actual marcada.
private struct SessionRows: View {
    let e: StrengthExercise
    let isCurrent: Bool
    let currentSet: Int
    let open: (Int) -> Void

    var body: some View {
        Module {
            HStack {
                Text(e.name).font(.ui(15, .bold))
                Spacer()
                Text("\(e.done.count)/\(e.sets)").font(.mono(13)).foregroundStyle(Palette.muted)
            }
            Grid(horizontalSpacing: 6, verticalSpacing: 8) {
                GridRow {
                    ForEach(["SERIE", "ANTERIOR", e.timed ? "PESO" : "KG", e.timed ? "SEG" : "REPS", ""], id: \.self) {
                        Text($0).font(.ui(11, .semibold)).foregroundStyle(Palette.muted).gridColumnAlignment(.leading)
                    }
                }
                ForEach(1...max(Int(e.sets), e.done.count), id: \.self) { n in
                    let today = e.done.first { Int($0.set) == n }
                    let before = e.previous.first { Int($0.set) == n }
                    let now = isCurrent && n == currentSet && today == nil
                    GridRow {
                        Text("\(n)").font(.mono(15))
                        Text(before.map { setText($0, timed: e.timed) } ?? "—").font(.mono(13)).foregroundStyle(Palette.muted)
                        Cell(text: today.map { kgText($0.weightKg?.doubleValue) } ?? "—", done: today != nil, now: now)
                        Cell(text: today.map { amount(Int($0.reps), timed: e.timed) } ?? "—", done: today != nil, now: now)
                        Glyph(name: "check", size: 16, color: today != nil ? Palette.onOk : Palette.muted)
                            .frame(width: 30, height: 30).background(today != nil ? Palette.ok : Palette.chip)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { open(n) }
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint("Abrir la serie \(n)")
                }
            }
        }
    }
}

private struct Cell: View {
    let text: String
    let done: Bool
    let now: Bool
    var body: some View {
        Text(text).font(.mono(text.count > 5 ? 11 : 13)).foregroundStyle(done ? Palette.text : Palette.muted)
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(done ? Palette.okSoft : now ? Palette.surface : Palette.chip)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(now ? Palette.ok : .clear, lineWidth: 2))
    }
}

private struct Finished: View {
    let other: [String]
    let close: () -> Void
    var body: some View {
        Module(background: Palette.okSoft) {
            HStack(spacing: 8) { Glyph(name: "check", size: 22, color: Palette.ok); Text("Sesión completa").font(.section) }
            Secondary("Queda apuntada: la próxima vez Brivel te propone el peso con estas series.")
            ForEach(other, id: \.self) { Secondary($0) }
            Button(action: close) { Text("Terminar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
        }
    }
}
