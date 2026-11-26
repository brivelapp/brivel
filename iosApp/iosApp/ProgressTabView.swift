import SwiftUI
import Shared

private let bodyKeys = ["sleep", "sleepScore", "deepSleep", "remSleep", "hrv", "restingHR", "spo2", "respiration", "stress", "weight", "bodyFat", "steps"]

/// Tu evolución: cómo está tu cuerpo frente a tu rango normal, tu forma y tu actividad (punto 8 de la revisión).
struct ProgressTabView: View {
    @EnvironmentObject var model: AppModel
    let openProfile: () -> Void
    @State private var page: String? = nil  // Medallas y Tus medidas tienen su pantalla (2026-11-23)

    var body: some View {
        let _ = model.version
        let screen = model.core.progress()
        switch page {
        case "medals":
            if let badges = screen.badges { SubScreen(title: "Medallas", back: { page = nil }) { BadgesSection(badges: badges) } }
        case "measures": SubScreen(title: "Tus medidas", back: { page = nil }) { MeasuresSection(screen: screen) }
        default: progressList(screen)
        }
    }

    @ViewBuilder private func progressList(_ screen: ProgressScreen) -> some View {
        let bodyMetrics = screen.metrics.filter { bodyKeys.contains($0.key) }
        let training = screen.metrics.filter { !bodyKeys.contains($0.key) }
        let last = screen.weeklyMinutes.last?.value ?? 0
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Progreso", subtitle: "Últimos 90 días")
                // Arriba, los cuatro que elige cada uno (decidido el 2026-11-23); debajo, cada parte plegada en una línea
                ProgressTiles()
                if !screen.linked {
                    Module {
                        Secondary("Para ver tu sueño, tu VFC, tu pulso en reposo o tu VO2max, conecta Apple Salud (los datos de tu reloj o tu báscula en este iPhone) o tu cuenta de intervals.icu (gratis) en Perfil → Ajustes.")
                        Button("Ir a Ajustes", action: openProfile).buttonStyle(SecondaryStyle())
                    }
                }
                if let badges = screen.badges {
                    RowButton(title: "Medallas", value: badges.next.first.map { "La más cerca: \($0.title)" } ?? badges.nextLevel ?? "") { page = "medals" }
                }
                if screen.linked && !bodyMetrics.isEmpty {
                    Fold(title: "Tu cuerpo", line: bodyMetrics.prefix(3).map { $0.label }.joined(separator: ", ") + (bodyMetrics.count > 3 ? "…" : "")) {
                        Module {
                            TitleRow {
                                Text("Tu cuerpo").font(.section)
                            } trailing: {
                                Text("último dato · banda = tu rango").font(.caption12).foregroundStyle(Palette.muted)
                            }
                            ForEach(Array(bodyMetrics.enumerated()), id: \.offset) { i, m in MetricRow(metric: m, first: i == 0) }
                        }
                    }
                }
                if screen.linked && (screen.status != nil || !training.isEmpty) {
                    Fold(title: "Forma", line: screen.status?.label ?? training.prefix(3).map { $0.label }.joined(separator: ", ")) {
                        Module {
                            if let st = screen.status {
                                let warn = st.label == "Sobrecarga" || st.label == "Perdiendo forma"
                                Text("Forma: \(st.label.lowercased())").font(.section)
                                if let note = screen.garminNote { Source(text: note) }
                                if let ctl = screen.metrics.first(where: { $0.key == "ctl" }) {
                                    Sparkline(values: ctl.points.map { $0.value }, low: nil, high: nil, color: warn ? Palette.caution : Palette.ok)
                                        .frame(height: 56)
                                }
                                Secondary(st.text)
                            }
                            ForEach(Array(training.enumerated()), id: \.offset) { i, m in
                                MetricRow(metric: m, first: i == 0 && screen.status == nil)
                            }
                        }
                    }
                }
                if screen.linked {
                    Fold(title: "Minutos por semana", line: "Esta semana, \(Int(last.rounded())) de 150") {
                        Module {
                            TitleRow {
                                Text("Actividad semanal").font(.section)
                            } trailing: {
                                Text("\(Int(last.rounded())) / 150 min").font(.mono(15, .semibold))
                            }
                            ProgressLine(fraction: last / 150)
                            Secondary("La OMS recomienda al menos 150 minutos moderados (o 75 intensos) a la semana.")
                            Bars(points: screen.weeklyMinutes, target: 150)
                        }
                    }
                }
                if !screen.predictions.isEmpty {
                    Fold(title: "Predicciones de carrera", line: screen.predictions.prefix(2).map { "\($0.distance) \($0.time)" }.joined(separator: " · ")) {
                        Module {
                            Text("Tiempos previstos").font(.section)
                            if let note = screen.garminNote { Source(text: note) }
                            ForEach(screen.predictions, id: \.distance) { p in
                                HStack {
                                    Text(p.distance).font(.body15)
                                    Spacer()
                                    Text(p.time).font(.mono(15, .semibold))
                                }
                            }
                            if let source = screen.predictionSource { Secondary("Calculado con \(source) (fórmula de Daniels).") }
                        }
                    }
                }
                if !screen.recent.isEmpty {
                    Fold(title: "Actividades recientes", line: screen.recent.first?.title) {
                        Module {
                            Text("Tus últimos entrenamientos").font(.section)
                            ForEach(Array(screen.recent.enumerated()), id: \.offset) { _, r in RecentRow(activity: r) }
                        }
                    }
                }
                if !screen.curves.isEmpty {
                    Fold(title: "Curvas de ritmo y potencia", line: screen.curves.map { $0.title }.joined(separator: " · ")) { CurvesSection(curves: screen.curves) }
                }
                RowButton(title: "Tus medidas", value: screen.measures.isEmpty ? "Apunta tu peso o tus perímetros" : screen.measures.map { $0.label }.joined(separator: ", ")) { page = "measures" }
                // Sin récords no se abre una pantalla vacía: la fila lo dice y no lleva flecha
                if screen.strengthRecords.isEmpty && screen.milestones.isEmpty {
                    Module {
                        Text("Récords y marcas").font(.section)
                        Secondary("Saldrán al entrenar")
                    }
                } else {
                    Fold(title: "Récords y marcas", line: screen.strengthRecords.first?.title ?? screen.milestones.first?.title) { MilestonesSection(screen: screen) }
                }
            }.padding(.horizontal, 18).padding(.vertical, 16)
        }.background(Palette.bg)
        .keyboardDone()
    }
}

/// Los cuatro de arriba; «Editar» abre la lista para elegir otros cuatro.
private struct ProgressTiles: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var editing: [String]? = nil

    var body: some View {
        let _ = model.version
        let tiles = model.core.progressTiles()
        VStack(alignment: .leading, spacing: 12) {
            HStack { Spacer(); Button("Editar") { editing = tiles.map { $0.key } }.buttonStyle(TextActionStyle()) }
            let columns = typeSize.isAccessibilitySize ? 1 : 2
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12, alignment: .top), count: columns), spacing: 12) {
                ForEach(tiles, id: \.key) { t in
                    Module {
                        Text(t.title).font(.section)
                        if let value = t.value {
                            (Text(value).font(.mono(24, .semibold, relativeTo: .title2))
                                + Text(t.unit.isEmpty ? "" : " \(t.unit)").font(.mono(13, relativeTo: .footnote)).foregroundColor(Palette.muted))
                            if let detail = t.detail { Secondary(detail) }
                        } else {
                            Secondary(t.empty ?? "")
                        }
                    }.frame(maxHeight: .infinity, alignment: .top)
                }
            }
        }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Secondary("Los cuatro de arriba en Progreso. Lo que no elijas sigue debajo. \(editing?.count ?? 0) de 4.")
                        Flow(spacing: 8) {
                            ForEach(model.core.tileChoices(), id: \.key) { c in
                                Chip(label: c.title, selected: editing?.contains(c.key) == true) {
                                    guard var e = editing else { return }
                                    if let i = e.firstIndex(of: c.key) { e.remove(at: i) } else if e.count < 4 { e.append(c.key) }
                                    editing = e
                                }
                            }
                        }
                    }.padding(20)
                }
                .background(Palette.surface)
                .navigationTitle("Elige 4").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Listo") {
                            let keys = editing ?? []
                            editing = nil
                            model.run { try model.core.setProgressTiles(keys: keys) }
                        }.disabled(editing?.count != 4)
                    }
                }
            }
        }
    }
}

private func format(_ v: Double?) -> String {
    guard let v else { return "—" }
    return v >= 20 ? String(Int(v.rounded())) : String(format: "%.1f", v).replacingOccurrences(of: ".", with: ",")
}

/// Con un decimal: en una medida o un 1RM, medio centímetro o medio kilo cuentan.
func oneDecimal(_ v: Double?) -> String {
    guard let v else { return "—" }
    let r = (v * 10).rounded() / 10
    return (r == r.rounded() ? String(Int(r)) : String(format: "%.1f", r)).replacingOccurrences(of: ".", with: ",")
}

/// Medidas corporales apuntadas a mano (Training.kt): su evolución y el formulario para apuntar la de hoy.
private struct MeasuresSection: View {
    @EnvironmentObject var model: AppModel
    let screen: ProgressScreen
    @State private var kind = TrainingKt.MEASURE_KINDS.first ?? "weight"
    @State private var value = ""

    var body: some View {
        Module {
            ChipChoice(title: "Medida", options: TrainingKt.MEASURE_KINDS.map { ($0, TrainingKt.MEASURES[$0]?.label ?? $0) }, selection: $kind)
            TextField("Valor (\(TrainingKt.MEASURES[kind]?.unit ?? ""))", text: $value).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                .font(.mono(22, .semibold))
            Button {
                guard let v = Double(value.replacingOccurrences(of: ",", with: ".")) else { model.message = "Escribe un número"; return }
                model.run("Medida apuntada") {
                    try model.core.logMeasure(kind: kind, value: v)
                    value = ""
                }
            } label: { Text("Apuntar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            if !screen.measures.isEmpty { Text("Últimas").font(.section) }
            ForEach(Array(screen.measures.enumerated()), id: \.offset) { i, m in MetricRow(metric: m, first: i == 0) }
        }
    }
}

/// Mejores marcas, semanas seguidas cumpliendo la OMS y récords de fuerza; los umbrales, aparte (como web/src/Progress.tsx).
private struct MilestonesSection: View {
    let screen: ProgressScreen

    private func line(_ title: String, _ detail: String?, _ value: String) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body15)
                if let detail { Text(detail).font(.caption12).foregroundStyle(Palette.muted) }
            }
            Spacer()
            Text(value).font(.mono(15, .semibold)).fixedSize()
        }
    }

    var body: some View {
        if !screen.milestones.isEmpty || !screen.strengthRecords.isEmpty {
            Module {
                Text("Tus hitos").font(.section)
                ForEach(screen.milestones, id: \.title) { m in
                    line(m.title, [m.date.map { ScreensKt.dateLabel(date: $0) }, m.source].compactMap { $0 }.joined(separator: " · ").nilIfEmpty, m.value)
                }
                if !screen.strengthRecords.isEmpty { Secondary("Lo más pesado que has levantado en cada ejercicio") }
                ForEach(screen.strengthRecords, id: \.title) { m in line(m.title, m.date.map { ScreensKt.dateLabel(date: $0) }, m.value) }
            }
        }
        if !screen.thresholds.isEmpty {
            Module {
                Text("Tus umbrales").font(.section)
                Secondary("De intervals.icu: con ellos salen tus zonas. Cámbialos allí si haces un test.")
                ForEach(screen.thresholds, id: \.label) { t in line(t.label, t.explanation, t.value) }
            }
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

/// Un entrenamiento: al tocarlo, lo medido y los minutos en cada zona.
private struct RecentRow: View {
    let activity: RecentActivity
    @State private var open = false
    @State private var detail = false

    var body: some View {
        let zones = activity.zoneMinutes.map { $0.intValue }
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 10) {
                SportBadge(sport: activity.sport, size: 32)
                VStack(alignment: .leading) {
                    Text(activity.title).font(.ui(15, .semibold, relativeTo: .subheadline))
                    if let source = activity.source { Source(text: source) }
                    Text(ScreensKt.dateLabel(date: activity.date)).font(.caption12).foregroundStyle(Palette.muted)
                }
                Spacer()
                RowChevron(open: open)
                Text("\(activity.minutes)′").font(.mono(15))
            }
            if open {
                if activity.own != nil { Button("Ver la actividad") { detail = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle()) }
                if let text = activity.detail { Secondary(text) }
                if zones.reduce(0, +) > 0 {
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            ForEach(Array(zones.enumerated()), id: \.offset) { i, m in
                                Rectangle().fill(Palette.zones["Z\(min(i + 1, 6))"] ?? Palette.ok)
                                    .frame(width: geo.size.width * CGFloat(m) / CGFloat(zones.reduce(0, +)))
                            }
                        }
                    }.frame(height: 10).clipShape(Capsule())
                    Secondary(zones.enumerated().filter { $0.element > 0 }.map { "Z\($0.offset + 1) \($0.element)′" }.joined(separator: " · "))
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { open.toggle() }
        .sheet(isPresented: $detail) { if let id = activity.own { ActivitySheet(id: id) } }
    }
}

/// Un indicador: valor, barra frente a tu rango y una frase; al tocarlo, su evolución y qué significa.
struct MetricRow: View {
    let metric: Metric
    let first: Bool
    @State private var open = false

    var body: some View {
        let latest = metric.latest?.doubleValue
        let low = metric.normalLow?.doubleValue
        let high = metric.normalHigh?.doubleValue
        VStack(alignment: .leading, spacing: 6) {
            if !first { Rectangle().fill(Palette.chip).frame(height: 1).padding(.bottom, 6) }
            HStack(alignment: .bottom) {
                Text(metric.label).font(.ui(15, .semibold, relativeTo: .subheadline))
                Spacer()
                Text((metric.key.hasPrefix("measure-") ? oneDecimal(latest) : format(latest)) + (metric.unit.isEmpty ? "" : " \(metric.unit)")).font(.mono(15, .semibold))
                RowChevron(open: open)
            }
            if let source = metric.source { Source(text: source) }
            if let low, let high, let latest, format(low) != format(high) {
                let values = metric.points.map { $0.value }
                let lo = min(low, values.min() ?? low)
                let hi = max(high, values.max() ?? high)
                let pad = (hi - lo) * 0.15
                let inRange = latest >= low && latest <= high
                let better = metric.higherIsBetter?.boolValue
                // Fuera de rango hacia el lado bueno (más VO2max, menos pulso en reposo) no es un aviso
                let good = inRange || (better == true && latest > high) || (better == false && latest < low)
                let range = low < 0 ? "\(format(low)) a \(format(high))" : "\(format(low))–\(format(high))"
                RangeBar(value: latest, low: low, high: high, domainMin: lo - pad, domainMax: hi + pad, good: good)
                Text(inRange ? "Dentro de tu rango habitual (\(range))" : latest > high ? "Por encima de tu rango habitual (\(range))" : "Por debajo de tu rango habitual (\(range))")
                    .font(.caption12).foregroundStyle(good ? Palette.muted : Palette.caution)
            }
            if open {
                Sparkline(values: metric.points.map { $0.value }, low: low, high: high, color: Palette.ok).frame(height: 64)
                Secondary(metric.explanation)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { open.toggle() }
    }
}

struct Sparkline: View {
    let values: [Double]
    let low: Double?
    let high: Double?
    let color: Color

    var body: some View {
        GeometryReader { geo in
            let lo = min(values.min() ?? 0, low ?? .infinity)
            let hi = max(values.max() ?? 1, high ?? -.infinity)
            let span = max(hi - lo, 1)
            let y = { (v: Double) in geo.size.height * (1 - (v - lo) / span) }
            ZStack {
                if let low, let high {
                    Rectangle().fill(Palette.band)
                        .frame(height: y(low) - y(high)).position(x: geo.size.width / 2, y: (y(low) + y(high)) / 2)
                }
                Path { p in
                    for (i, v) in values.enumerated() {
                        let pt = CGPoint(x: geo.size.width * Double(i) / Double(max(values.count - 1, 1)), y: y(v))
                        if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
                    }
                }.stroke(color, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            }
        }
    }
}

private struct Bars: View {
    let points: [Shared.Point]
    let target: Double

    var body: some View {
        let top = max(points.map { $0.value }.max() ?? 0, target * 1.2)
        GeometryReader { geo in
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                    RoundedRectangle(cornerRadius: 4).fill(p.value >= target ? Palette.ok : Palette.chip)
                        .frame(height: geo.size.height * p.value / top)
                }
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
            .overlay(alignment: .top) {
                Rectangle().fill(Palette.muted).frame(height: 1).offset(y: geo.size.height * (1 - target / top))
            }
        }.frame(height: 96)
    }
}
