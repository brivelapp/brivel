import SwiftUI
import UniformTypeIdentifiers
import Shared

// Actividades propias (grabar con el GPS, importar un archivo FIT, GPX o TCX y ver su detalle), material con sus kilómetros y
// medallas, como Activities.kt de Android y web/src/Activities.tsx: lo calcula el núcleo y aquí solo se pinta.

/// Una línea del núcleo (puntos dentro de un cuadrado de lado 1) estirada al tamaño que se le dé.
struct LineShape: Shape {
    let points: [XY]
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for (i, p) in points.enumerated() {
            let c = CGPoint(x: rect.minX + CGFloat(p.x) * rect.width, y: rect.minY + CGFloat(p.y) * rect.height)
            if i == 0 { path.move(to: c) } else { path.addLine(to: c) }
        }
        return path
    }
}

private struct LineChart: View {
    let points: [XY]
    let label: String
    var body: some View {
        LineShape(points: points).stroke(Palette.ok, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            .accessibilityLabel(label)
    }
}

private struct TableRow: View {
    let cells: [String]
    var header = false
    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, c in
                Text(c).font(header ? .caption12 : .mono(14)).foregroundStyle(header ? Palette.muted : Palette.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

private struct ZonesBar: View {
    let zones: [Int]
    var body: some View {
        let total = zones.reduce(0, +)
        GeometryReader { geo in
            HStack(spacing: 0) {
                ForEach(Array(zones.enumerated()), id: \.offset) { i, m in
                    Rectangle().fill(Palette.zones["Z\(min(i + 1, 6))"] ?? Palette.ok)
                        .frame(width: geo.size.width * CGFloat(m) / CGFloat(max(total, 1)))
                }
            }
        }.frame(height: 10).clipShape(Capsule())
    }
}

/// El detalle de una actividad propia, en una hoja.
struct ActivitySheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var confirmDelete = false

    private func zonesText(_ zones: [Int]) -> String {
        zones.enumerated().filter { $0.element > 0 }.map { "Z\($0.offset + 1) \($0.element)′" }.joined(separator: " · ")
    }

    var body: some View {
        ScrollView {
            if let a = model.core.activity(id: id) {
                content(a)
            } else {
                Secondary("Esta actividad ya no está.").padding(20)
            }
        }
        .background(Palette.surface)
        .confirmationDialog("¿Borrar esta actividad?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) {
                model.run("Actividad borrada") { model.core.deleteActivity(id: id) }
                dismiss()
            }
        } message: { Text("Dejará de contar en tu plan y en Progreso.") }
    }

    @ViewBuilder
    private func content(_ a: ActivityScreen) -> some View {
        let zones = a.zoneMinutes.map { $0.intValue }
        VStack(alignment: .leading, spacing: 12) {
            Text(a.title).font(.screenTitle)
            Secondary("\(a.dateLabel) · \(a.source)")
            LazyVGrid(columns: [GridItem(.flexible(), alignment: .leading), GridItem(.flexible(), alignment: .leading)], alignment: .leading, spacing: 10) {
                ForEach(a.stats, id: \.label) { s in
                    VStack(alignment: .leading) {
                        Text(s.label).font(.caption12).foregroundStyle(Palette.muted)
                        Text(s.value).font(.mono(17, .semibold))
                    }
                }
            }
            if !a.track.isEmpty {
                LineChart(points: a.track, label: "Recorrido").aspectRatio(1, contentMode: .fit).frame(maxWidth: 280).frame(maxWidth: .infinity)
                Source(text: "El recorrido, sin mapa de fondo. Solo está en este iPhone.")
            }
            ForEach(a.charts, id: \.label) { c in
                HStack {
                    Text(c.label).font(.body15)
                    Spacer()
                    Text("\(c.low) – \(c.high)").font(.mono(12)).foregroundStyle(Palette.muted)
                }
                LineChart(points: c.line, label: "\(c.label): de \(c.low) a \(c.high)").frame(height: 56)
            }
            if a.charts.isEmpty {
                Secondary("Las gráficas y el recorrido se quedan en el dispositivo que importó o grabó la actividad; aquí está su resumen.")
            }
            if zones.reduce(0, +) > 0 {
                ZonesBar(zones: zones)
                Secondary(zonesText(zones))
            }
            if !a.laps.isEmpty {
                TableRow(cells: ["Vuelta", "Tiempo", "Distancia", a.sport == "cycling" ? "Velocidad" : "Ritmo", "Pulso"], header: true)
                ForEach(a.laps, id: \.n) { l in TableRow(cells: ["\(l.n)", l.time, l.distance, l.pace, l.hr]) }
            }
            if !a.bests.isEmpty {
                Secondary("Tu mejor media en cada duración")
                ForEach(a.bests, id: \.label) { b in
                    HStack { Text(b.label).font(.body15); Spacer(); Text(b.value).font(.mono(15)) }
                }
            }
            if let gear = a.gear { Secondary("Material: \(gear)") }
            Button { dismiss() } label: { Text("Cerrar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            Button("Borrar la actividad") { confirmDelete = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
        }.padding(20)
    }
}

/// Entrenar: las actividades propias y el botón de importar un archivo de actividad.
struct ActivitiesSection: View {
    @EnvironmentObject var model: AppModel
    @State private var open: String?
    @State private var all = false
    @State private var importing = false

    private func load(_ url: URL) {
        model.run("Actividad importada") {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            open = try model.core.importFitBase64(text: data.base64EncodedString())
        }
    }

    var body: some View {
        let _ = model.version
        let rows = model.core.activities()
        Module {
            Button { importing = true } label: { Text("Importar un archivo FIT, GPX o TCX").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
            if rows.isEmpty { Secondary("Aún no tienes ninguna. Cuentan en tu plan y en Progreso como las del reloj.") }
            ForEach(all ? rows : Array(rows.prefix(5)), id: \.id) { r in
                Button { open = r.id } label: {
                    HStack(spacing: 10) {
                        SportBadge(sport: r.sport, size: 32)
                        VStack(alignment: .leading) {
                            Text(r.title).font(.ui(15, .semibold, relativeTo: .subheadline))
                            Text([ScreensKt.dateLabel(date: r.date), r.detail].compactMap { $0 }.joined(separator: " · "))
                                .font(.caption12).foregroundStyle(Palette.muted).multilineTextAlignment(.leading)
                        }
                        Spacer()
                        RowChevron()
                    }
                }.buttonStyle(.plain)
            }
            if !all && rows.count > 5 { Button("Ver las \(rows.count)") { all = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle()) }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            if case .success(let url) = result { load(url) }
        }
        .sheet(isPresented: Binding(get: { open != nil }, set: { if !$0 { open = nil } })) {
            if let id = open { ActivitySheet(id: id) }
        }
    }
}

/// Entrenar: grabar una carrera, una salida en bici o una caminata con el GPS del iPhone.
struct RecordSection: View {
    @EnvironmentObject var model: AppModel
    @State private var sport = RecorderKt.RECORD_SPORTS.first ?? "running"
    @State private var confirmDiscard = false
    @State private var opened: String?

    private func now() -> Int64 { Int64(Date().timeIntervalSince1970) }

    var body: some View {
        let _ = model.version
        Module {
            if model.core.recordingView(time: now()) == nil {
                Secondary("La ubicación solo se usa mientras grabas y el recorrido se queda en este iPhone.")
                HStack {
                    ForEach(RecorderKt.RECORD_SPORTS, id: \.self) { s in Chip(label: sportLabel(s), selected: sport == s) { sport = s } }
                }
                Button {
                    model.run {
                        try model.core.startRecording(sport: sport, time: now())
                        Recorder.shared.start(model.core)
                    }
                } label: { Text("Empezar a grabar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            } else {
                // El tiempo corre cada segundo; los puntos llegan del GPS aunque la pantalla se apague
                TimelineView(.periodic(from: .now, by: 1)) { _ in
                    if let view = model.core.recordingView(time: now()) { live(view) }
                }
                .onAppear { Recorder.shared.start(model.core) }  // la app se cerró a mitad: se sigue grabando
            }
        }
        .confirmationDialog("¿Descartar la grabación?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Descartar", role: .destructive) {
                model.run("Grabación descartada") { model.core.discardRecording(); Recorder.shared.stop() }
            }
        } message: { Text("Se borra lo grabado hasta ahora.") }
        .sheet(isPresented: Binding(get: { opened != nil }, set: { if !$0 { opened = nil } })) {
            if let id = opened { ActivitySheet(id: id) }
        }
    }

    @ViewBuilder
    private func live(_ view: RecordingView) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(sportLabel(view.sport) + (view.paused ? " · en pausa" : "")).font(.ui(16, .bold))
            HStack {
                figure("Tiempo", view.time)
                Spacer()
                figure("Distancia", view.distance)
                Spacer()
                figure(view.sport == "cycling" ? "Velocidad" : "Ritmo", view.pace)
            }
            if Recorder.shared.denied {
                Secondary("Brivel no tiene permiso de ubicación: actívalo en Ajustes → Privacidad → Localización para grabar el recorrido.", color: Palette.caution)
            } else if !view.hasSignal {
                Secondary("Buscando la señal del GPS: sal a cielo abierto. La distancia empieza a contar con el primer punto.")
            }
            HStack(spacing: 10) {
                Button {
                    model.run { if view.paused { model.core.resumeRecording(time: now()) } else { model.core.pauseRecording(time: now()) } }
                } label: { Text(view.paused ? "Seguir" : "Pausar").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
                Button {
                    model.run("Actividad guardada") {
                        opened = try model.core.finishRecording()
                        Recorder.shared.stop()
                    }
                } label: { Text("Terminar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            }
            Button("Descartar la grabación") { confirmDiscard = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
        }
    }

    private func figure(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading) {
            Text(label).font(.caption12).foregroundStyle(Palette.muted)
            Text(value).font(.mono(20, .semibold))
        }
    }
}

/// Perfil: zapatillas y bicis con sus kilómetros.
struct GearSection: View {
    @EnvironmentObject var model: AppModel
    @State private var editing: GearRow?
    @State private var adding = UserDefaults.standard.string(forKey: "sheet") == "new"  // `-sheet new`: el CI abre el formulario
    @State private var deleting: GearRow?

    var body: some View {
        let _ = model.version
        let screen = model.core.gear()
        Module {
            Button { adding = true } label: { Text("Añadir zapatillas o bici").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            if let note = screen.garminNote { Source(text: note) }
            ForEach(screen.rows, id: \.id) { g in row(g, others: screen.rows.filter { $0.id != g.id && $0.sport == g.sport }) }
            Secondary(screen.rows.isEmpty ? "Aún no tienes ninguno." : "Brivel suma los km de cada actividad al material en uso de ese deporte.")
        }
        .sheet(isPresented: $adding) { GearEditor(old: nil) }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) { GearEditor(old: editing) }
        .confirmationDialog("¿Borrar «\(deleting?.name ?? "")»?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible) {
            Button("Borrar", role: .destructive) {
                if let g = deleting { model.run("Material borrado") { model.core.deleteGear(id: g.id) } }
            }
        } message: { Text("Se borran también sus kilómetros apuntados.") }
    }

    private func state(_ g: GearRow) -> String { g.label + (g.retired ? " · retirado" : g.inUse ? " · en uso" : "") }

    @ViewBuilder
    private func row(_ g: GearRow, others: [GearRow]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                VStack(alignment: .leading) {
                    Text(g.name).font(.ui(15, .semibold, relativeTo: .subheadline))
                    Text(state(g)).font(.caption12).foregroundStyle(Palette.muted)
                }
                Spacer()
                Text(g.km).font(.mono(15, .semibold))
            }
            Text(g.detail).font(.caption12).foregroundStyle(Palette.muted)
            if let warning = g.warning { Secondary(warning, color: Palette.caution) }
            // Pasar una de las últimas actividades a otro material del mismo deporte
            if !others.isEmpty {
                ForEach(g.uses, id: \.activity) { u in
                    HStack {
                        Text(u.text).font(.ui(13))
                        Spacer()
                        Menu("Pasar a…") {
                            ForEach(others, id: \.id) { o in
                                Button(o.name) { model.run("Actividad cambiada de material") { try model.core.moveGearUse(activity: u.activity, gearId: o.id) } }
                            }
                        }.font(.ui(13, .bold))
                    }
                }
            }
            HStack(spacing: 14) {
                if !g.inUse { Button("Poner en uso") { model.run("\(g.name) está en uso") { model.core.useGear(id: g.id) } } }
                if !g.retired { Button("Retirar") { model.run("\(g.name) está retirado: conserva sus kilómetros") { model.core.retireGear(id: g.id) } } }
                Button("Cambiar") { editing = g }
                Button("Borrar") { deleting = g }
            }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
        }
    }
}

private struct GearEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let old: GearRow?
    @State private var name = ""
    @State private var sport = GearKt.GEAR_SPORTS.first ?? "running"
    @State private var since = Date()
    @State private var startKm = ""
    @State private var limitKm = ""
    @State private var error: String?

    private static let day: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    private func number(_ text: String) -> Double? { Double(text.replacingOccurrences(of: ",", with: ".")) }

    private func save() {
        let start = startKm.isEmpty ? 0 : number(startKm)
        let limit = limitKm.isEmpty ? nil : number(limitKm)
        guard let start, limitKm.isEmpty || limit != nil else { error = "Escribe los kilómetros con números"; return }
        do {
            try model.core.saveGear(id: old?.id, name: name, sport: sport, since: Self.day.string(from: since), startKm: start,
                                    limitKm: limit.map { KotlinDouble(double: $0) })
            model.run("Material guardado") {}
            dismiss()
        } catch { self.error = error.localizedDescription }  // dentro de la hoja: un aviso de abajo quedaría tapado
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(old == nil ? "Añadir material" : "Cambiar material").font(.screenTitle)
                TextField("Nombre", text: $name).textFieldStyle(.roundedBorder)
                if old == nil {
                    ChipChoice(title: "Qué es", options: GearKt.GEAR_SPORTS.map { ($0, GearKt.GEAR_LABEL[$0] ?? $0) }, selection: $sport)
                }
                DatePicker("Cuenta las actividades desde", selection: $since, in: ...Date(), displayedComponents: .date)
                TextField("Kilómetros que ya llevaba", text: $startKm).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                TextField("Avisar al pasar de (km, opcional)", text: $limitKm).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                if let error { Secondary(error, color: Palette.caution) }
                Button { save() } label: { Text("Guardar").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
                Button("Cancelar") { dismiss() }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
            }.padding(20)
        }
        .keyboardDone()
        .background(Palette.surface)
        .onAppear {
            guard let old else { return }
            name = old.name
            sport = old.sport
            since = Self.day.date(from: old.since) ?? Date()
            startKm = old.startKm > 0 ? oneDecimal(old.startKm) : ""
            limitKm = old.limitKm.map { oneDecimal($0.doubleValue) } ?? ""
        }
    }
}

private struct BadgeRow: View {
    let badge: BadgeView
    var body: some View {
        let earned = badge.earned != nil
        HStack(alignment: .top, spacing: 10) {
            Text("\(badge.points)").font(.ui(15, .bold)).foregroundStyle(earned ? Palette.surface : Palette.muted)
                .frame(width: 40, height: 40)
                .background(Circle().fill(earned ? Palette.ok : Color.clear))
                .overlay(Circle().stroke(earned ? Palette.ok : Palette.muted, lineWidth: 2))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(badge.title).font(.ui(15, .semibold, relativeTo: .subheadline))
                Text(earned ? "\(badge.text) Ganada el \(badge.earned ?? "")." : badge.text).font(.caption12).foregroundStyle(Palette.muted)
                if !earned, let progress = badge.progress {
                    ProgressLine(fraction: badge.fraction)
                    Text(progress).font(.mono(12)).foregroundStyle(Palette.muted)
                }
            }
        }
    }
}

/// Progreso: nivel, puntos, medallas ganadas y las siguientes más cercanas.
struct BadgesSection: View {
    let badges: BadgesScreen
    @State private var shown: String? = nil

    var body: some View {
        let all = badges.earned + badges.next
        Module {
            HStack(spacing: 12) {
                Text("\(badges.level)").font(.ui(16, .bold)).foregroundStyle(Palette.surface).frame(width: 40, height: 40)
                    .background(Palette.ok).clipShape(Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Nivel \(badges.level) · \(badges.points) \(badges.points == 1 ? "punto" : "puntos")").font(.ui(16, .bold))
                    if let next = badges.nextLevel { Secondary(next) }
                }
            }
            if let note = badges.garminNote { Source(text: note) }
            if let next = badges.next.first {
                Text("La más cerca").font(.section)
                BadgeRow(badge: next)
            }
            Text(badges.earned.isEmpty ? "Todas" : "Tienes \(badges.earned.count) de \(all.count)").font(.section)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8, alignment: .top), count: 3), spacing: 8) {
                ForEach(all, id: \.id) { b in
                    Button { shown = shown == b.id ? nil : b.id } label: {
                        VStack(spacing: 6) {
                            Text("\(b.points)").font(.ui(13, .bold)).foregroundStyle(b.earned != nil ? Palette.surface : Palette.muted)
                                .frame(width: 34, height: 34).background(b.earned != nil ? Palette.ok : .clear).clipShape(Circle())
                                .overlay(Circle().stroke(b.earned != nil ? Palette.ok : Palette.muted, style: StrokeStyle(lineWidth: 2, dash: b.earned != nil ? [] : [4, 3])))
                            Text(b.title).font(.ui(12, .semibold)).multilineTextAlignment(.center).foregroundStyle(Palette.text)
                        }
                        .frame(maxWidth: .infinity, minHeight: 84).padding(6).background(Palette.bg)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(shown == b.id ? Palette.ok : .clear, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
            }
            if let b = all.first(where: { $0.id == shown }) { BadgeRow(badge: b) }
        }
    }
}

/// Progreso: curvas de potencia y de ritmo de las actividades propias, como tabla.
struct CurvesSection: View {
    let curves: [Curve]
    var body: some View {
        ForEach(curves, id: \.title) { c in
            Module {
                Text(c.title).font(.section)
                Secondary("Tu mejor media en cada duración, con las actividades que has importado o grabado.")
                TableRow(cells: ["Duración", "Últimos 90 días", "Tu mejor"], header: true)
                ForEach(c.rows, id: \.duration) { r in TableRow(cells: [r.duration, r.recent, r.best]) }
                if let note = c.note { Secondary(note) }
            }
        }
    }
}
