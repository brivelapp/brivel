import SwiftUI
import AuthenticationServices
import Shared

/// Ajustes (2026-11-17): se abre con la rueda del Perfil. Una lista corta, una fila por cosa con su estado a la derecha; lo
/// largo vive dentro de cada fila o tras «?». Con la navegación del sistema, la barra y la flecha de volver llevan el
/// cristal de iOS 26, como la barra inferior.
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @State private var confirmDelete = false

    var body: some View {
        let _ = model.version
        let core = model.core
        List {
            Section("Conexiones") {
                NavigationLink { HealthPage() } label: {
                    SettingsRow(name: "Apple Salud", state: core.data.health != nil ? "Conectado" : "Sin conectar", on: core.data.health != nil)
                }
                NavigationLink { ScrollPage(title: "Garmin Connect™") { GarminSection() } } label: {
                    SettingsRow(name: "Garmin Connect™", state: core.garminConnected ? "Conectado" : "Sin conectar", on: core.garminConnected)
                }
                NavigationLink { IntervalsPage() } label: {
                    SettingsRow(name: "intervals.icu", state: core.linkedAthlete != nil ? "Conectado" : "Sin conectar", on: core.linkedAthlete != nil)
                }
            }
            Section("Avisos") { ReminderRow() }
            Section("Tus datos") {
                NavigationLink { ScrollPage(title: "Otros dispositivos") { SyncSection() } } label: {
                    SettingsRow(name: "Otros dispositivos",
                                state: core.syncNeedsConsent ? "En pausa" : core.syncCode != nil ? "Sincronizado" : "Solo aquí",
                                on: core.syncCode != nil && !core.syncNeedsConsent)
                }
                ShareLink("Exportar mis datos", item: core.exportJson()).foregroundStyle(Palette.text)
                Button("Borrar todo", role: .destructive) { confirmDelete = true }
            }
            Section("Sobre Brivel") {
                NavigationLink("Aviso de salud") {
                    ScrollPage(title: "Aviso de salud") { Module { Secondary(TextsKt.HEALTH_NOTICE) } }
                }
                NavigationLink("Privacidad, términos y código") { AboutPage() }
            }
        }
        .font(.body15)
        .scrollContentBackground(.hidden)
        .background(Palette.bg)
        .navigationTitle("Ajustes")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("¿Borrar todo?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) { model.run { core.deleteAll() } }
        } message: {
            Text("Se borran tu perfil, tu objetivo, tu historial y la clave de intervals.icu de este móvil. No se puede deshacer. Tus datos en intervals.icu no se tocan.")
        }
    }
}

/// Una fila de Ajustes: punto de estado, nombre y estado en una palabra.
private struct SettingsRow: View {
    let name: String
    let state: String
    let on: Bool
    var body: some View {
        HStack(spacing: 10) {
            Circle().fill(on ? Palette.ok : Palette.chip).frame(width: 8, height: 8).accessibilityHidden(true)
            Text(name).font(.ui(15, .semibold))
            Spacer()
            Text(state).font(.small).foregroundStyle(on ? Palette.ok : Palette.muted)
        }
    }
}

/// Lo largo, plegado: una línea basta para la mayoría y quien quiera el detalle lo abre.
struct MoreInfo: View {
    let title: String
    let text: String
    @State private var open = false
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(open ? "Ocultar" : title) { withAnimation { open.toggle() } }
                .buttonStyle(TextActionStyle()).font(.ui(14, .semibold))
                .accessibilityValue(open ? "abierto" : "cerrado")
            if open { Secondary(text) }
        }
    }
}

/// Pantalla de detalle con el fondo y el margen de la app.
private struct ScrollPage<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 12) { content }.padding(16) }
            .background(Palette.bg)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }
}

/// Recordatorio: interruptor y hora en la misma fila.
private struct ReminderRow: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let core = model.core
        Toggle(isOn: Binding(get: { core.data.reminderHour != nil }, set: { on in
            if on {
                model.runAsync {
                    guard try await Reminders.requestAccess() else { throw ReminderError.denied }
                    try core.setReminder(hour: KotlinInt(int: 8))
                }
            } else {
                model.run { try core.setReminder(hour: nil) }
            }
        })) { Text("Recordatorio diario").font(.ui(15, .semibold)) }
        .tint(Palette.ok)
        if let hour = core.data.reminderHour?.intValue {
            Stepper(String(format: "A las %02d:00", hour), value: Binding(get: { hour }, set: { h in
                model.run { try core.setReminder(hour: KotlinInt(int: Int32((h + 24) % 24))) }
            }), in: -1...24)
            .font(.mono(16))
            MoreInfo(title: "¿Cuándo avisa?", text: "Un aviso con la sesión del día. Los días de descanso o con todo hecho no avisa.")
        }
    }
}

private struct HealthPage: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        let _ = model.version
        let core = model.core
        ScrollPage(title: "Apple Salud") {
            Module {
                if let health = core.data.health {
                    StateLine(label: "Estado", value: "Conectado", ok: true)
                    StateLine(label: "Leído", value: "\(health.days.count) días · \(health.workouts.count) entrenos")
                    StateLine(label: "Al día", value: health.syncedAt)
                } else {
                    Text("Brivel lee tu sueño, pulso y entrenos de Apple Salud. Solo en este iPhone.").font(.body15)
                }
                MoreInfo(title: "¿Qué lee exactamente?", text: "Tu sueño y sus fases, tu variabilidad cardíaca, tu pulso en reposo, tu oxígeno en sangre, tu respiración, tu peso, tu grasa corporal, tus pasos y tus entrenamientos, de cualquier reloj o app. Solo lectura. Los permisos se cambian en Ajustes → Salud → Acceso a datos.")
            }
            if core.data.health != nil {
                Button("Actualizar ahora") {
                    model.runAsync("Datos actualizados") {
                        try await HealthKitReader.shared.requestAccess()  // pide solo los permisos que falten (los datos nuevos)
                        try await HealthKitReader.shared.sync(core)
                    }
                }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
                Button("Desconectar") { model.run { core.clearHealth() } }.buttonStyle(SecondaryStyle()).frame(maxWidth: .infinity)
            } else if HealthKitReader.shared.available {
                Button("Conectar Apple Salud") {
                    model.runAsync("Datos de Apple Salud leídos") {
                        try await HealthKitReader.shared.requestAccess()
                        try await HealthKitReader.shared.sync(core)
                    }
                }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
            } else {
                Secondary("Apple Salud no está disponible en este dispositivo.")
            }
        }
    }
}

private struct IntervalsPage: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.webAuthenticationSession) private var webAuth
    var body: some View {
        let _ = model.version
        let core = model.core
        ScrollPage(title: "intervals.icu") {
            Module {
                if let linked = core.linkedAthlete {
                    StateLine(label: "Estado", value: "Conectado", ok: true)
                    StateLine(label: "Cuenta", value: linked)
                    if let cache = core.data.cache { StateLine(label: "Al día", value: cache.syncedAt) }
                } else {
                    Text("Gratis; recoge los datos de cualquier reloj.").font(.body15)
                }
                MoreInfo(title: "¿Qué relojes?", text: "intervals.icu recoge los datos de Garmin, Polar, Coros, Suunto, Wahoo y otros. Inicia sesión allí y da permiso a Brivel.")
                Toggle("Renombrar mis actividades", isOn: Binding(
                    get: { core.data.profile?.renameActivities ?? true },
                    set: { v in
                        var d = ProfileDraft(core.data.profile)
                        d.renameActivities = v
                        let p = d.core
                        model.run { core.saveProfile(profile: p) }
                    }))
                    .font(.ui(15, .semibold)).tint(Palette.ok)
                Secondary("Les pone un título que dice lo que fueron.")
            }
            if core.linkedAthlete != nil {
                Button("Actualizar ahora") { model.runAsync("Datos actualizados") { try await core.sync() } }
                    .buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
                Button("Desconectar") { model.runAsync("intervals.icu desvinculado") { try await core.disconnectIntervals() } }
                    .buttonStyle(SecondaryStyle()).frame(maxWidth: .infinity)
            } else {
                // La pantalla de permisos de intervals.icu dentro de la app; se cierra sola al volver a brivel://intervals
                Button("Conectar con intervals.icu") {
                    model.runAsync("intervals.icu conectado") {
                        let url = try await core.startIntervals(redirect: "brivel://intervals")
                        guard let start = URL(string: url) else { return }
                        let back = try await webAuth.authenticate(using: start, callbackURLScheme: "brivel")
                        try await core.finishIntervals(callback: back.absoluteString)
                    }
                }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
            }
        }
    }
}

private struct AboutPage: View {
    var body: some View {
        ScrollPage(title: "Sobre Brivel") {
            Module {
                Text("Gratis y de código abierto (AGPL-3.0). Sin pagos, sin anuncios.").font(.body15)
                Secondary(TextsKt.PRIVACY_NOTE)
                Link("Política de privacidad", destination: URL(string: TextsKt.PRIVACY_URL)!)
                Link("Términos", destination: URL(string: TextsKt.TERMS_URL)!)
                Link("Código fuente", destination: URL(string: TextsKt.SOURCE_URL)!)
            }
        }
    }
}

/// «Estado · Conectado»: etiqueta a la izquierda, dato a la derecha.
private struct StateLine: View {
    let label: String
    let value: String
    var ok = false
    var body: some View {
        HStack {
            Text(label).foregroundStyle(Palette.muted)
            Spacer()
            Text(value).font(ok ? .ui(15, .bold) : .mono(14)).foregroundStyle(ok ? Palette.ok : Palette.text)
        }.font(.body15)
    }
}
