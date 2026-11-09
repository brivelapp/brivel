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

/// En Perfil: todos los ejercicios, con búsqueda y filtros de patrón y de material.
struct LibrarySection: View {
    @EnvironmentObject var model: AppModel
    @State private var open = false
    @State private var query = ""
    @State private var pattern: String?
    @State private var equipment: String?
    @State private var shown: String?

    var body: some View {
        Module {
            Text("Biblioteca de ejercicios").font(.section)
            Secondary("Busca entre todos los ejercicios por tipo o material.")
            if !open {
                Button("Ver los ejercicios") { open = true }.buttonStyle(SecondaryStyle())
            } else {
                TextField("Buscar", text: $query).textFieldStyle(.roundedBorder)
                Picker("Patrón", selection: $pattern) {
                    Text("Todos los patrones").tag(String?.none)
                    ForEach(LibraryKt.PATTERN_LABEL.sorted { $0.value < $1.value }, id: \.key) { Text($0.value).tag(String?.some($0.key)) }
                }
                Picker("Material", selection: $equipment) {
                    Text("Cualquier material").tag(String?.none)
                    ForEach(LibraryKt.EQUIPMENT, id: \.self) { Text($0).tag(String?.some($0)) }
                }
                let list = model.core.library(query: query, pattern: pattern, equipment: equipment)
                if list.isEmpty { Secondary("Ningún ejercicio con esos filtros.") }
                ForEach(list, id: \.name) { c in ExerciseRow(card: c, detail: c.patternLabel) { shown = c.name } }
            }
        }
        .sheet(isPresented: Binding(get: { shown != nil }, set: { if !$0 { shown = nil } })) {
            if let shown { ExerciseSheet(name: shown, original: nil, onSwap: nil) }
        }
    }
}
