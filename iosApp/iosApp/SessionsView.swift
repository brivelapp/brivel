import SwiftUI
import Shared

// Sesiones propias de resistencia (OwnSessions.kt del núcleo), como Sessions.kt de Android y web/src/Sessions.tsx: los
// días elegidos sustituyen a la sesión de ese deporte del plan; también se puede hacer hoy, solo esta vez

private let weekDays: [(String, String)] = [("MON", "L"), ("TUE", "M"), ("WED", "X"), ("THU", "J"), ("FRI", "V"), ("SAT", "S"), ("SUN", "D")]

private func decimal(_ text: String) -> Double { Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0 }

/// Un paso mientras se edita: los números, como texto, para poder teclear la coma y dejar el campo vacío.
private struct DraftStep: Identifiable {
    let id = UUID()
    var name = ""
    var minutes = "30"
    var zone = "Z2"
    var reps = "1"
    var rest = "0"

    var repeats: Int { Int(reps) ?? 0 }
    var total: Double { Double(repeats) * decimal(minutes) + (repeats > 1 ? Double(repeats) * decimal(rest) : 0) }
}

private func totalMinutes(_ s: OwnSession) -> Int {
    var sum = 0.0
    for step in s.steps {
        sum += Double(step.reps) * step.minutes
        if step.reps > 1 { sum += Double(step.reps) * step.restMinutes }
    }
    return Int(sum.rounded())
}

private func summary(_ s: OwnSession) -> String {
    let days = s.days.isEmpty ? "sin días fijos"
        : weekDays.filter { s.days.contains($0.0) }.map { (TextsKt.WEEKDAY_LABEL[$0.0] ?? $0.0).lowercased() }.joined(separator: ", ")
    return "\(sportLabel(s.sport)) · \(totalMinutes(s)) min · \(days)"
}

private func zoneLabel(_ zone: String) -> String { "\(OwnSessionsKt.OWN_ZONES[zone] ?? zone) (\(zone))" }

private struct NumberBox: View {
    let label: String
    @Binding var text: String
    let decimalPad: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Secondary(label)
            TextField(label, text: $text).keyboardType(decimalPad ? .decimalPad : .numberPad).textFieldStyle(.roundedBorder)
        }
    }
}

private struct SessionEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let start: OwnSession?
    @State private var name = ""
    @State private var sport = "running"
    @State private var steps: [DraftStep] = [DraftStep()]
    @State private var selected: [String] = []
    @State private var saveError: String?
    @State private var confirmDelete = false
    @State private var loaded = false

    private func number(of step: DraftStep) -> Int { (steps.firstIndex { $0.id == step.id } ?? 0) + 1 }
    private var total: Int { Int(steps.reduce(0.0) { $0 + $1.total }.rounded()) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    TextField("Nombre", text: $name).textFieldStyle(.roundedBorder)
                    ChipChoice(title: "Deporte", options: OwnSessionsKt.OWN_SPORTS.map { ($0, sportLabel($0)) }, selection: $sport)
                    Text("Pasos").font(.section)
                    ForEach($steps) { $step in
                        Module {
                            HStack {
                                Text("Paso \(number(of: step))").font(.ui(16, .bold))
                                Spacer()
                                if steps.count > 1 {
                                    Button("Quitar") { steps.removeAll { $0.id == step.id } }.buttonStyle(TextActionStyle())
                                        .accessibilityLabel("Quitar el paso \(number(of: step))")
                                }
                            }
                            TextField("Nombre del paso (opcional)", text: $step.name).textFieldStyle(.roundedBorder)
                            ChipChoice(title: "Intensidad", options: OwnSessionsKt.OWN_ZONE_KEYS.map { ($0, zoneLabel($0)) }, selection: $step.zone)
                            HStack(spacing: 8) {
                                NumberBox(label: "Minutos", text: $step.minutes, decimalPad: true)
                                NumberBox(label: "Veces", text: $step.reps, decimalPad: false)
                                if step.repeats > 1 { NumberBox(label: "Descanso (min)", text: $step.rest, decimalPad: true) }
                            }
                        }
                    }
                    Button("Añadir paso") { steps.append(DraftStep(minutes: "10")) }.buttonStyle(SecondaryStyle())
                    Secondary("Total: \(total) min. El pulso o el ritmo de cada paso salen de tus zonas, como en las sesiones del plan.")
                    Text("Qué días la haces").font(.section)
                    HStack(spacing: 6) {
                        ForEach(weekDays, id: \.0) { key, label in
                            Chip(label: label, selected: selected.contains(key)) {
                                if selected.contains(key) { selected.removeAll { $0 == key } } else { selected.append(key) }
                            }.accessibilityLabel(TextsKt.WEEKDAY_LABEL[key] ?? key)
                        }
                    }
                    Secondary("Esos días sustituye a la sesión de ese deporte de tu plan. Sin días, queda guardada para hacerla cuando quieras.")
                    if let saveError { Text(saveError).foregroundStyle(Palette.danger) }
                    Button("Guardar") { save() }.buttonStyle(PrimaryStyle())
                    if start != nil { Button("Borrar sesión", role: .destructive) { confirmDelete = true }.buttonStyle(TextActionStyle()) }
                }.padding(18)
            }
            .background(Palette.bg)
            .navigationTitle(start == nil ? "Nueva sesión" : "Editar sesión").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } } }
            .onAppear {
                guard !loaded, let start else { loaded = true; return }
                name = start.name
                sport = start.sport
                selected = start.days
                steps = start.steps.map {
                    DraftStep(name: $0.name, minutes: oneDecimal($0.minutes), zone: $0.intensityZone, reps: String($0.reps), rest: oneDecimal($0.restMinutes))
                }
                loaded = true
            }
            .confirmationDialog("¿Borrar la sesión «\(start?.name ?? "")»?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Borrar", role: .destructive) {
                    model.run("Sesión borrada") { model.core.deleteOwnSession(id: start?.id ?? "") }
                    dismiss()
                }
            }
        }
    }

    private func save() {
        let session = OwnSession(id: start?.id ?? "", name: name, sport: sport,
                                 steps: steps.map { OwnStep(name: $0.name, minutes: decimal($0.minutes), intensityZone: $0.zone, reps: Int32($0.repeats), restMinutes: decimal($0.rest)) },
                                 days: selected, dates: start?.dates ?? [], since: "")
        // El aviso general no se ve con la hoja abierta: el error se enseña dentro de ella
        do { _ = try model.core.saveOwnSession(session: session) } catch { saveError = error.localizedDescription; return }
        model.run("Sesión guardada") {}
        dismiss()
    }
}

/// En Entrenar: las sesiones propias, hacer una hoy y crear otra.
struct SessionsSection: View {
    @EnvironmentObject var model: AppModel
    @State private var editing: OwnSession?
    @State private var creating = false

    var body: some View {
        Module {
            Button { creating = true } label: { Text("Nueva sesión").frame(maxWidth: .infinity) }.buttonStyle(PrimaryStyle())
            if model.core.ownSessions.isEmpty { Secondary("Aún no tienes ninguna. Móntala paso a paso y va al calendario y al reloj como las del plan.") }
            ForEach(model.core.ownSessions, id: \.id) { s in
                HStack {
                    Button { editing = s } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(s.name).font(.ui(16, .bold))
                            Secondary(summary(s))
                        }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                    }.buttonStyle(.plain)
                    Button("Hoy") { model.run("Puesta hoy: la tienes en Hoy") { try model.core.doOwnSessionToday(id: s.id) } }
                        .font(.ui(14, .bold)).buttonStyle(TextActionStyle()).accessibilityLabel("Hacer \(s.name) hoy")
                }
            }
        }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) { SessionEditor(start: editing) }
        .sheet(isPresented: $creating) { SessionEditor(start: nil) }
    }
}
