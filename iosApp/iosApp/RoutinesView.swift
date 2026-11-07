import SwiftUI
import Shared

// Rutinas propias (Routines.kt del núcleo), como Routines.kt de Android y web/src/Routines.tsx: esos días sustituyen a
// la fuerza del plan

private let days: [(String, String)] = [("MON", "L"), ("TUE", "M"), ("WED", "X"), ("THU", "J"), ("FRI", "V"), ("SAT", "S"), ("SUN", "D")]

private func daysText(_ selected: [String]) -> String {
    selected.isEmpty ? "sin días: no entra en el plan"
        : days.filter { selected.contains($0.0) }.map { (TextsKt.WEEKDAY_LABEL[$0.0] ?? $0.0).lowercased() }.joined(separator: ", ")
}

private struct DraftExercise: Identifiable {
    let id = UUID()
    var name: String
    var sets: Int
    var low: Int
    var high: Int
}

/// Un número de 1 a 3 cifras: vacío es 0 (el núcleo lo rechaza al guardar).
private struct NumberField: View {
    let label: String
    @Binding var value: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Secondary(label)
            TextField(label, text: Binding(get: { value == 0 ? "" : String(value) },
                                           set: { value = Int(String($0.filter(\.isNumber).prefix(3))) ?? 0 }))
                .keyboardType(.numberPad).textFieldStyle(.roundedBorder)
        }
    }
}

private struct RoutineEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let start: Routine?
    @State private var name = ""
    @State private var selected: [String] = []
    @State private var exercises: [DraftExercise] = []
    @State private var rir = 2
    @State private var picking = false
    @State private var confirmDelete = false
    @State private var loaded = false

    private var all: [ExerciseCard] { LibraryKt.library(query: "", pattern: nil, equipment: nil) }
    private func timed(_ name: String) -> Bool { all.first { $0.name == name }?.timed ?? false }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    TextField("Nombre", text: $name).textFieldStyle(.roundedBorder)
                    Text("Qué días la haces").font(.section)
                    HStack(spacing: 6) {
                        ForEach(days, id: \.0) { key, label in
                            Chip(label: label, selected: selected.contains(key)) {
                                if selected.contains(key) { selected.removeAll { $0 == key } } else { selected.append(key) }
                            }.accessibilityLabel(TextsKt.WEEKDAY_LABEL[key] ?? key)
                        }
                    }
                    Secondary("Esos días sustituye a la fuerza de tu plan. Un día solo puede tener una rutina.")
                    Text("Ejercicios").font(.section)
                    ForEach($exercises) { $e in
                        Module {
                            HStack {
                                Text(e.name).font(.ui(16, .bold))
                                Spacer()
                                Button("Quitar") { exercises.removeAll { $0.id == e.id } }
                            }
                            let unit = timed(e.name) ? "s" : "reps"
                            HStack(spacing: 8) {
                                NumberField(label: "Series", value: $e.sets)
                                NumberField(label: "Desde (\(unit))", value: $e.low)
                                NumberField(label: "Hasta (\(unit))", value: $e.high)
                            }
                        }
                    }
                    Button("Añadir ejercicio") { picking = true }.buttonStyle(SecondaryStyle())
                    Picker("Reserva al acabar cada serie", selection: $rir) {
                        ForEach(0...5, id: \.self) { n in Text(n == 0 ? "Hasta no poder más" : "\(n) repeticiones").tag(n) }
                    }
                    Secondary("Brivel te propone el peso con lo que apuntes, sin subir nunca más de un 10 % de una vez.")
                    Button("Guardar") { save() }.buttonStyle(PrimaryStyle())
                    if start != nil { Button("Borrar rutina", role: .destructive) { confirmDelete = true } }
                }.padding(18)
            }
            .background(Palette.bg)
            .navigationTitle(start == nil ? "Nueva rutina" : "Editar rutina").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } } }
            .onAppear {
                guard !loaded, let start else { loaded = true; return }
                name = start.name
                selected = start.days
                exercises = start.exercises.map { DraftExercise(name: $0.name, sets: Int($0.sets), low: Int($0.low), high: Int($0.high)) }
                rir = Int(start.rir)
                loaded = true
            }
            .sheet(isPresented: $picking) { picker }
            .confirmationDialog("¿Borrar la rutina «\(start?.name ?? "")»?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Borrar", role: .destructive) {
                    model.run("Rutina borrada") { model.core.deleteRoutine(id: start?.id ?? "") }
                    dismiss()
                }
            }
        }
    }

    private var picker: some View {
        NavigationStack {
            List {
                ForEach(Dictionary(grouping: all, by: \.patternLabel).sorted { $0.key < $1.key }, id: \.key) { group in
                    Section(group.key) {
                        ForEach(group.value, id: \.name) { c in
                            Button(c.name) {
                                exercises.append(c.timed ? DraftExercise(name: c.name, sets: 3, low: 20, high: 40)
                                                         : DraftExercise(name: c.name, sets: 3, low: 8, high: 12))
                                picking = false
                            }
                        }
                    }
                }
            }
            .navigationTitle("Añadir ejercicio").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { picking = false } } }
        }
    }

    private func save() {
        let routine = Routine(id: start?.id ?? "", name: name,
                              exercises: exercises.map { RoutineExercise(name: $0.name, sets: Int32($0.sets), low: Int32($0.low), high: Int32($0.high)) },
                              days: selected, rir: Int32(rir), since: "")
        var saved = false
        model.run("Rutina guardada") { _ = try model.core.saveRoutine(routine: routine); saved = true }
        if saved { dismiss() }
    }
}

/// En Perfil: las rutinas propias y el botón para crear otra.
struct RoutinesSection: View {
    @EnvironmentObject var model: AppModel
    @State private var editing: Routine?
    @State private var creating = false

    var body: some View {
        Module {
            Text("Tus rutinas").font(.section)
            Secondary("Crea tu propia rutina de fuerza y elige qué días la haces. Esos días sustituye a la fuerza del plan.")
            ForEach(model.core.routines, id: \.id) { r in
                Button { editing = r } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.name).font(.ui(16, .bold))
                        Secondary("\(r.exercises.count) ejercicios · \(daysText(r.days))")
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }.buttonStyle(.plain)
            }
            Button("Nueva rutina") { creating = true }.buttonStyle(SecondaryStyle())
        }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) { RoutineEditor(start: editing) }
        .sheet(isPresented: $creating) { RoutineEditor(start: nil) }
    }
}
