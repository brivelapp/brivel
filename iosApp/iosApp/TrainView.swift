import SwiftUI
import Shared

/// Entrenar (docs/VISION.md §5), como Train.kt de Android y web/src/Train.tsx: empezar un entreno sin plan, tus rutinas
/// y la biblioteca.
struct TrainView: View {
    @EnvironmentObject var model: AppModel
    @State private var confirmDiscard = false

    private func start(_ routineId: String?) {
        model.run { model.strengthDate = try model.core.startWorkout(routineId: routineId).date }
    }

    var body: some View {
        let _ = model.version
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Entrenar", subtitle: "Por tu cuenta")
                Module {
                    Text("Entreno libre").font(.section)
                    if let workout = model.core.workoutToday {
                        Text("Hoy: \(workout.name)").font(.ui(16, .bold))
                        Secondary(workout.exercises.count == 1 ? "1 ejercicio" : "\(workout.exercises.count) ejercicios")
                        Button { model.strengthDate = workout.date } label: { Text("Continuar").frame(maxWidth: .infinity) }
                            .buttonStyle(PrimaryStyle())
                        Button("Descartar el entreno") { confirmDiscard = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
                    } else {
                        Secondary("Entrena sin plan: elige los ejercicios sobre la marcha y apunta cada serie. Hoy sustituye a la fuerza de tu plan.")
                        Button { start(nil) } label: { Text("Empezar entreno vacío").frame(maxWidth: .infinity) }
                            .buttonStyle(PrimaryStyle())
                        ForEach(model.core.routines, id: \.id) { r in
                            Button("Empezar «\(r.name)» hoy") { start(r.id) }.buttonStyle(SecondaryStyle())
                        }
                    }
                }
                RoutinesSection()
                LibrarySection()
            }.padding(.horizontal, 18).padding(.vertical, 16)
        }
        .background(Palette.bg)
        // Descartar borra también las series apuntadas hoy en ese entreno: se pregunta antes
        .confirmationDialog("¿Descartar el entreno de hoy?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Descartar", role: .destructive) {
                model.run("Entreno descartado: hoy vuelve a tu plan") { model.core.discardWorkout() }
            }
        } message: { Text("Se borran también las series que has apuntado en él.") }
    }
}
