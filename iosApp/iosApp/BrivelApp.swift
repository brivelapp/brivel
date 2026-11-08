import SwiftUI
import Shared

/// Envuelve el núcleo compartido: cada acción sube `version` y las pantallas se recalculan.
@MainActor
final class AppModel: ObservableObject {
    let core: Shared.BrivelApp
    @Published var version = 0
    @Published var message: String?
    @Published var strengthDate: String?  // sesión de fuerza abierta, a pantalla completa

    init() {
        core = Shared.BrivelApp(store: DefaultsStore(), secrets: KeychainStore(),
                                   today: { BrivelAppKt.systemToday() }, httpEngine: nil)
    }

    func refresh() {
        version += 1
        Reminders.reschedule(core)  // el texto de cada aviso depende del plan: se rehace tras cada cambio
    }

    /// Ejecuta una acción del núcleo y enseña el error si lo hay.
    func run(_ success: String? = nil, _ block: () throws -> Void) {
        do {
            try block()
            if let success { message = success }
            syncSoon()
        } catch {
            message = error.localizedDescription
        }
        refresh()
    }

    func runAsync(_ success: String? = nil, _ block: @escaping () async throws -> Void) {
        // Al salir de la app, iOS corta enseguida la red (NSURLError -1005): así deja terminar lo que ya está en marcha
        var background = UIBackgroundTaskIdentifier.invalid
        func endBackground() {
            guard background != .invalid else { return }
            UIApplication.shared.endBackgroundTask(background)
            background = .invalid
        }
        background = UIApplication.shared.beginBackgroundTask(expirationHandler: endBackground)
        Task {
            defer { endBackground() }
            do {
                try await block()
                if let success { message = success }
                syncSoon()
            } catch {
                message = readable(error)
            }
            refresh()
        }
    }

    /// El error para enseñarlo; los de red llegan de Ktor como un volcado de NSError.
    private func readable(_ error: Error) -> String {
        let text = error.localizedDescription
        return text.contains("NSURLErrorDomain") ? "Se ha cortado la conexión: inténtalo de nuevo" : text
    }

    private var pending: Task<Void, Never>?

    /// Con la sincronización activada, sube los cambios poco después (varios seguidos, de una vez). Sin red, calla.
    func syncSoon() {
        guard core.syncCode != nil else { return }
        pending?.cancel()
        pending = Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if Task.isCancelled { return }
            do {
                if let conflict = try await core.syncDevices() { message = conflict }
            } catch {
                // Solo lo que responde el servidor (copia borrada, otra versión); sin conexión se sube más tarde
                if (error as NSError).userInfo["KotlinException"] is SyncException { message = error.localizedDescription }
            }
            refresh()
        }
    }

    /// Datos frescos sin pulsar nada: intervals.icu, Apple Salud y Garmin si están conectados, y después lo que cambiaron
    /// los otros dispositivos (también sube lo recién llegado). Sin red, la copia guardada.
    func refreshAll() async {
        if core.linkedAthlete != nil { try? await core.sync() }
        if core.data.health != nil { try? await HealthKitReader.shared.sync(core) }
        do { _ = try await core.pullGarmin() } catch {
            // Solo lo que responde el relevo (desconectado desde Garmin Connect); sin conexión, en la próxima vez
            if (error as NSError).userInfo["KotlinException"] is GarminException { message = error.localizedDescription }
        }
        if let conflict = try? await core.syncDevices() { message = conflict }
        refresh()
    }

    func syncNow() {
        pending?.cancel()
        Task {
            do {
                message = try await core.syncDevices() ?? "Sincronizado"
            } catch {
                message = readable(error)
            }
            refresh()
        }
    }
}

@main
struct BrivelMain: App {
    @StateObject private var model = AppModel()

    init() { registerFonts() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(Palette.ok)
                .font(.ui(17))
                .foregroundStyle(Palette.text)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @State private var tab = UserDefaults.standard.integer(forKey: "tab")  // `-tab N` al lanzar: capturas del CI
    @Environment(\.scenePhase) private var phase

    var body: some View {
        let _ = model.version
        Group {
            if model.core.needsOnboarding {
                OnboardingView()
            } else {
                TabView(selection: $tab) {
                    TodayView(openGoal: { model.run { model.core.abandonGoal() } })
                        .tabItem { Label { Text("Hoy") } icon: { Image("tab_today").renderingMode(.template) } }.tag(0)
                    CalendarTabView()
                        .tabItem { Label { Text("Calendario") } icon: { Image("tab_calendar").renderingMode(.template) } }.tag(1)
                    ProgressTabView(openProfile: { tab = 3 })
                        .tabItem { Label { Text("Progreso") } icon: { Image("tab_progress").renderingMode(.template) } }.tag(2)
                    ProfileView()
                        .tabItem { Label { Text("Perfil") } icon: { Image("tab_profile").renderingMode(.template) } }.tag(3)
                }
            }
        }
        .background(Palette.bg.ignoresSafeArea())
        // Al abrir, al volver a la app y al deslizar hacia abajo en cualquier pantalla
        .task { await model.refreshAll() }
        .onChange(of: phase) { _, now in if now == .active { Task { await model.refreshAll() } } }
        .refreshable { await model.refreshAll() }
        .fullScreenCover(isPresented: Binding(get: { model.strengthDate != nil }, set: { if !$0 { model.strengthDate = nil } })) {
            if let date = model.strengthDate {
                StrengthSessionView(date: date) { model.strengthDate = nil }.environmentObject(model)
            }
        }
        .onAppear {
            // `-strength auto` al lanzar: la última sesión de fuerza de esta semana abierta, para las capturas del CI
            if UserDefaults.standard.string(forKey: "strength") == "auto" {
                model.strengthDate = model.core.lastStrengthDate()
            }
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("Vale", role: .cancel) {}
        }
    }
}
