import SwiftUI
import AuthenticationServices
import Shared

/// Garmin Connect™ (política de privacidad, apartado 5), como GarminCard.kt y web/src/Garmin.tsx. Conectar pide el
/// consentimiento explícito para datos de salud y la edad mínima, sin marcar; después los datos llegan solos
/// (RootView: al abrir, al volver a la app y al deslizar hacia abajo). Garmin vuelve a brivel://garmin.
struct GarminSection: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.webAuthenticationSession) private var webAuth
    @State private var asking = false
    @State private var consent = false
    @State private var adult = false
    @State private var confirm = false

    var body: some View {
        let _ = model.version
        let core = model.core
        Module {
            Text(TextsKt.GARMIN_TITLE).font(.section)
            if core.garminConnected {
                Secondary(TextsKt.garminStatus(syncedAt: core.garminSyncedAt()))
                Button("Desconectar Garmin", role: .destructive) { confirm = true }.font(.small)
            } else if !asking {
                Secondary(TextsKt.GARMIN_INTRO)
                Button("Conectar Garmin Connect™") { asking = true }.buttonStyle(SecondaryStyle())
            } else {
                Secondary(TextsKt.GARMIN_HOW)
                Toggle(TextsKt.GARMIN_CONSENT, isOn: $consent).font(.small)
                Toggle(TextsKt.SYNC_AGE, isOn: $adult).font(.small)
                HStack {
                    Button("Ir a Garmin") { connect() }.buttonStyle(PrimaryStyle()).disabled(!consent || !adult)
                    Button("Cancelar") { asking = false }.buttonStyle(SecondaryStyle())
                }
            }
        }
        .alert("¿Desconectar Garmin?", isPresented: $confirm) {
            Button("Desconectar", role: .destructive) {
                model.runAsync("Garmin desconectado") { try await model.core.disconnectGarmin() }
            }
            Button("Cancelar", role: .cancel) {}
        } message: { Text(TextsKt.GARMIN_DISCONNECT) }
    }

    /// La pantalla de permisos de Garmin dentro de la app; se cierra sola al volver a brivel://garmin.
    private func connect() {
        model.runAsync("Garmin conectado: los datos llegan cuando sincronices el reloj") {
            let url = try await model.core.startGarmin()
            guard let start = URL(string: url) else { return }
            _ = try await webAuth.authenticate(using: start, callbackURLScheme: "brivel")
            _ = try await model.core.finishGarmin()
            asking = false
        }
    }
}
