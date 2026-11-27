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

    @State private var page = UserDefaults.standard.string(forKey: "page")  // `-page sessions` en el CI; cada parte, su pantalla (2026-11-23)

    private func count(_ n: Int, _ one: String, _ many: String) -> String { n == 0 ? "Ninguna todavía" : n == 1 ? "1 \(one)" : "\(n) \(many)" }

    private func groupTitle(_ text: String) -> some View {
        Text(text.uppercased()).font(.ui(12, .bold, relativeTo: .caption)).foregroundStyle(Palette.muted).padding(.leading, 4).padding(.top, 4)
    }

    var body: some View {
        let _ = model.version
        switch page {
        case "record": SubScreen(title: "Grabar con GPS", back: { page = nil }) { RecordSection() }
        case "sessions": SubScreen(title: "Sesiones", back: { page = nil }) { SessionsSection() }
        case "routines":
            SubScreen(title: "Rutinas de fuerza", back: { page = nil }) {
                if model.core.workoutToday == nil {
                    ForEach(model.core.routines, id: \.id) { r in
                        Button { start(r.id) } label: { Text("Empezar «\(r.name)» hoy").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
                    }
                }
                RoutinesSection()
            }
        case "activities": SubScreen(title: "Actividades", back: { page = nil }) { ActivitiesSection() }
        case "library": SubScreen(title: "Biblioteca de ejercicios", back: { page = nil }) { LibrarySection() }
        default: trainList
        }
    }

    @ViewBuilder private var trainList: some View {
        let workout = model.core.workoutToday
        let routines = model.core.routines
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(text: "Entrenar", subtitle: "Por tu cuenta")
                // Por secciones (2026-11-23): arriba, las dos píldoras de lo que se hace ya; debajo, lo tuyo plegado
                FitRow(spacing: 10) {
                    Button { if let workout { model.strengthDate = workout.date } else { start(nil) } } label: {
                        Text(workout != nil ? "Seguir con la fuerza" : "Entrenar fuerza").frame(maxWidth: .infinity)
                    }.buttonStyle(PrimaryStyle())
                    Button { page = "record" } label: { Text("Grabar con GPS").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
                }
                if let workout {
                    Module {
                        Text("Hoy: \(workout.name)").font(.ui(16, .bold))
                        Secondary(workout.exercises.count == 1 ? "1 ejercicio" : "\(workout.exercises.count) ejercicios")
                        Button("Descartar el entreno") { confirmDiscard = true }.font(.ui(14, .bold)).buttonStyle(TextActionStyle())
                    }
                }
                groupTitle("Lo tuyo")
                RowButton(title: "Grabar con GPS", value: "Carrera, bici o caminata, desde el iPhone") { page = "record" }
                RowButton(title: "Sesiones", value: count(model.core.ownSessions.count, "sesión", "sesiones") + " · carrera, bici, natación") { page = "sessions" }
                RowButton(title: "Rutinas de fuerza", value: count(routines.count, "rutina", "rutinas") + " · sustituyen a la del plan") { page = "routines" }
                RowButton(title: "Actividades", value: count(model.core.activities().count, "actividad", "actividades") + " · subir FIT, GPX o TCX") { page = "activities" }
                groupTitle("Consultar")
                RowButton(title: "Biblioteca de ejercicios", value: "Busca por zona del cuerpo o material") { page = "library" }
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
