import SwiftUI
import Shared

// Biblioteca de ejercicios (Library.kt del núcleo), como Library.kt de Android y web/src/Library.tsx

private struct ExerciseRow: View {
    let card: ExerciseCard
    let detail: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ExerciseFigure(pattern: card.figure, size: 36).frame(width: 44, height: 44)
                    .background(Palette.band).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(card.name).font(.ui(16, .bold)).multilineTextAlignment(.leading)
                    Secondary(detail)
                }
                Spacer()
                RowChevron()
            }.contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

/// La ficha: patrón, material y claves de técnica. Con `onSwap`, también por cuáles se puede cambiar (mismo patrón) y,
/// si ya es un cambio (`original`), volver al del plan.
struct ExerciseSheet: View {
    @EnvironmentObject var model: AppModel
    let name: String
    let original: String?
    let onSwap: ((String) -> Void)?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                if let card = model.core.exerciseCard(name: name) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 12) {
                            ExerciseFigure(pattern: card.figure).frame(width: 64, height: 64)
                                .background(Palette.band).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            Secondary([card.patternLabel, card.equipment.joined(separator: ", "), card.timed ? "por tiempo" : "por repeticiones"].filter { !$0.isEmpty }.joined(separator: " · "))
                        }
                        if !card.muscles.isEmpty { Secondary("Zona: \(card.zone) · Músculos: \(card.muscles.joined(separator: ", "))") }
                        if !card.description.isEmpty { Text(card.description).font(.small) }
                        ForEach(card.cues, id: \.self) { Text("• \($0)").font(.small) }
                        Source(text: card.cueSource)
                        if let onSwap {
                            Text("Cambiar por").font(.section)
                            if let original {
                                Button("Volver a \(original)") { onSwap(original); dismiss() }.buttonStyle(SecondaryStyle())
                            }
                            ForEach(model.core.swapOptions(name: original ?? name).filter { $0.name != name }, id: \.name) { o in
                                ExerciseRow(card: o, detail: o.equipment.joined(separator: ", ")) { onSwap(o.name); dismiss() }
                            }
                            Secondary("El cambio vale desde esta sesión; las anteriores siguen con el que hiciste.")
                        }
                    }.padding(18)
                }
            }
            .background(Palette.bg)
            .navigationTitle(name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Cerrar") { dismiss() } } }
        }
    }
}

/// En Perfil: todos los ejercicios, con búsqueda y filtros de zona y de material, y el material que tienes.
struct LibrarySection: View {
    @EnvironmentObject var model: AppModel
    @State private var open = false
    @State private var query = ""
    @State private var zone: String?
    @State private var equipment: String?
    @State private var mine = false
    @State private var limit = 60
    @State private var shown: String?

    var body: some View {
        let _ = model.version
        let owned = model.core.equipment
        Module {
            Text("Biblioteca de ejercicios").font(.section)
            Secondary("Busca entre todos los ejercicios por zona del cuerpo o material.")
            if !open {
                Button("Ver los ejercicios") { open = true }.buttonStyle(SecondaryStyle())
            } else {
                Secondary("Tu material (el peso corporal siempre cuenta). Brivel solo te propone variantes que puedas hacer con él.")
                ForEach(LibraryKt.EQUIPMENT.filter { $0 != "peso corporal" }, id: \.self) { tag in
                    Toggle(tag, isOn: Binding(get: { owned.contains(tag) }, set: { on in
                        model.run { try model.core.setEquipment(tags: on ? owned + [tag] : owned.filter { $0 != tag }) }
                    })).font(.small)
                }
                TextField("Buscar", text: $query).textFieldStyle(.roundedBorder)
                Picker("Zona", selection: $zone) {
                    Text("Todas las zonas").tag(String?.none)
                    ForEach(LibraryKt.ZONES, id: \.self) { Text($0).tag(String?.some($0)) }
                }
                Picker("Material", selection: $equipment) {
                    Text("Cualquier material").tag(String?.none)
                    ForEach(LibraryKt.EQUIPMENT, id: \.self) { Text($0).tag(String?.some($0)) }
                }
                if !owned.isEmpty { Toggle("Solo con mi material", isOn: $mine).font(.small) }
                let list = model.core.libraryByZone(query: query, zone: zone, equipment: equipment, mine: mine)
                Secondary("\(list.count) ejercicios")
                ForEach(Array(list.prefix(limit)), id: \.name) { c in ExerciseRow(card: c, detail: c.patternLabel) { shown = c.name } }
                if list.count > limit { Button("Ver más") { limit += 60 }.buttonStyle(SecondaryStyle()) }
            }
        }
        .onChange(of: query) { _, _ in limit = 60 }
        .onChange(of: zone) { _, _ in limit = 60 }
        .onChange(of: equipment) { _, _ in limit = 60 }
        .onChange(of: mine) { _, _ in limit = 60 }
        .sheet(isPresented: Binding(get: { shown != nil }, set: { if !$0 { shown = nil } })) {
            if let shown { ExerciseSheet(name: shown, original: nil, onSwap: nil) }
        }
    }
}
