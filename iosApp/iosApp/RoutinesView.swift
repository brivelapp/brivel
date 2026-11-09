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
    @State private var query = ""
    @State private var confirmDelete = false
    @State private var loaded = false

    private var all: [ExerciseCard] { model.core.library(query: "", pattern: nil, equipment: nil) }
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
                                Button("Quitar") { exercises.removeAll { $0.id == e.id } }.buttonStyle(TextActionStyle())
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
                    if start != nil { Button("Borrar rutina", role: .destructive) { confirmDelete = true }.buttonStyle(TextActionStyle()) }
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
                ForEach(Dictionary(grouping: model.core.library(query: query, pattern: nil, equipment: nil), by: \.patternLabel).sorted { $0.key < $1.key }, id: \.key) { group in
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
            .searchable(text: $query, prompt: "Buscar")
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

private struct NewExercise: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var pattern = "sentadilla"
    @State private var timed = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                TextField("Nombre", text: $name).textFieldStyle(.roundedBorder)
                Picker("Tipo", selection: $pattern) {
                    ForEach(LibraryKt.PATTERN_LABEL.sorted { $0.value < $1.value }, id: \.key) { Text($0.value).tag($0.key) }
                }
                Toggle("Va por tiempo, no por repeticiones", isOn: $timed)
                Button("Guardar") { save() }.buttonStyle(PrimaryStyle())
                Spacer()
            }
            .padding(18).background(Palette.bg)
            .navigationTitle("Nuevo ejercicio").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } } }
        }
    }

    private func save() {
        var saved = false
        model.run("Ejercicio guardado") { _ = try model.core.saveCustomExercise(exercise: CustomExercise(name: name, pattern: pattern, timed: timed)); saved = true }
        if saved { dismiss() }
    }
}

/// En Perfil: las rutinas propias y el botón para crear otra.
struct RoutinesSection: View {
    @EnvironmentObject var model: AppModel
    @State private var editing: Routine?
    @State private var creating = false
    @State private var adding = false

    var body: some View {
        Module {
            Text("Tus rutinas").font(.section)
            Secondary("Crea tu propia rutina de fuerza y elige qué días la haces. Esos días sustituye a la fuerza del plan.")
            ForEach(model.core.routines, id: \.id) { r in
                Button { editing = r } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.name).font(.ui(16, .bold))
                            Secondary("\(r.exercises.count) ejercicios · \(daysText(r.days))")
                        }
                        Spacer()
                        RowChevron()
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain)
            }
            Button("Nueva rutina") { creating = true }.buttonStyle(SecondaryStyle())
            Text("Tus ejercicios").font(.section)
            Secondary("Si falta alguno en la biblioteca, créalo y úsalo en tus rutinas.")
            ForEach(model.core.customExercises, id: \.name) { e in
                HStack {
                    Text(e.name)
                    Spacer()
                    Button("Borrar") { model.run("Ejercicio borrado") { try model.core.deleteCustomExercise(name: e.name) } }.buttonStyle(TextActionStyle())
                }
            }
            Button("Nuevo ejercicio") { adding = true }.buttonStyle(SecondaryStyle())
        }
        .sheet(isPresented: $adding) { NewExercise() }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) { RoutineEditor(start: editing) }
        .sheet(isPresented: $creating) { RoutineEditor(start: nil) }
    }
}
