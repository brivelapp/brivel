import SwiftUI
import Shared
import AuthenticationServices

/// Continuar con Google o Apple, con los colores y el logo de sus guías de marca (como web/src/Sync.tsx).
private struct AccountButton: View {
    let provider: String
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let google = provider == "google"
        let dark = scheme == .dark
        let bg = google ? (dark ? Color(red: 0x13 / 255, green: 0x13 / 255, blue: 0x14 / 255) : .white) : (dark ? .white : .black)
        let fg = google ? (dark ? Color(red: 0xE3 / 255, green: 0xE3 / 255, blue: 0xE3 / 255) : Color(red: 0x1F / 255, green: 0x1F / 255, blue: 0x1F / 255)) : (dark ? .black : .white)
        let border = google ? (dark ? Color(red: 0x8E / 255, green: 0x91 / 255, blue: 0x8F / 255) : Color(red: 0x74 / 255, green: 0x77 / 255, blue: 0x75 / 255)) : bg
        Button(action: action) {
            HStack(spacing: 10) {
                if google { Image("google_g").resizable().frame(width: 18, height: 18) } else { Image(systemName: "apple.logo") }
                Text("Continuar con \(TextsKt.accountName(provider: provider) ?? "")").font(.ui(15, .medium))
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(fg)
            .background(Capsule().fill(bg))
            .overlay(Capsule().stroke(border, lineWidth: 1))
        }.buttonStyle(.plain)
    }
}

/// El código en grupos de 3 para leerlo y copiarlo sin errores.
private func grouped(_ code: String) -> String {
    let chars = Array(code)
    return stride(from: 0, to: chars.count, by: 3).map { String(chars[$0..<min($0 + 3, chars.count)]) }.joined(separator: " ")
}

/// Usar en otros dispositivos (docs/SINCRONIZACION.md), como web/src/Sync.tsx y SyncCard.kt en Android. Activar pide el
/// consentimiento explícito para datos de salud (RGPD art. 9.2.a) y la edad mínima, sin marcar. `allowCreate`: false
/// en la bienvenida, donde solo tiene sentido unirse a una copia que ya existe.
struct SyncSection: View {
    @EnvironmentObject var model: AppModel
    var allowCreate = true

    private enum Step { case idle, create, join, google, apple }
    @Environment(\.webAuthenticationSession) private var webAuth
    @State private var step = Step.idle
    @State private var consent = false
    @State private var adult = false
    @State private var typed = ""
    @State private var showCode = false
    @State private var confirmDelete = false

    var body: some View {
        let _ = model.version
        let core = model.core
        Module {
            if core.syncNeedsConsent {
                Text("Sincronización en pausa").font(.section)
                Secondary(TextsKt.SYNC_POLICY_CHANGED)
                Button(TextsKt.SYNC_ACCEPT) { core.acceptSyncPolicy(); model.syncNow() }.buttonStyle(PrimaryStyle())
                Button("Dejar de sincronizar aquí") {
                    model.run("Este móvil ya no sincroniza. La copia sigue en el servidor") { core.forgetSync() }
                }.font(.small).buttonStyle(TextActionStyle())
                Button("Borrar la copia del servidor", role: .destructive) { confirmDelete = true }.font(.small).buttonStyle(TextActionStyle())
            } else if let code = core.syncCode {
                Text("Sincronización activada").font(.section)
                let account = TextsKt.accountName(provider: core.accountProvider)
                Secondary(account.map { "Entras con \($0): en otro dispositivo, entra con la misma cuenta." } ?? TextsKt.SYNC_ACTIVE)
                if showCode {
                    Text(grouped(code)).font(.mono(20, .semibold)).textSelection(.enabled)
                    Secondary("Escríbelo en el otro dispositivo, en «Usar en otros dispositivos» → «Tengo un código».")
                }
                FitRow {
                    Button("Sincronizar ahora") { model.syncNow() }.buttonStyle(PrimaryStyle())
                    Button(showCode ? "Ocultar código" : "Ver código") { showCode.toggle() }.buttonStyle(SecondaryStyle())
                }
                if let account {
                    Button("Dejar de entrar con \(account)") {
                        model.runAsync("Ya no entras con \(account) en ningún dispositivo. La sincronización con el código sigue") {
                            try await core.signOutAccount()
                        }
                    }.font(.small).buttonStyle(TextActionStyle())
                }
                Button("Dejar de sincronizar aquí") {
                    model.run("Este móvil ya no sincroniza. La copia sigue en el servidor") { core.forgetSync() }
                }.font(.small).buttonStyle(TextActionStyle())
                Button("Borrar la copia del servidor", role: .destructive) { confirmDelete = true }.font(.small).buttonStyle(TextActionStyle())
            } else if step == .idle {
                Text(allowCreate ? "Usar en otros dispositivos" : "¿Ya usas Brivel en otro dispositivo?").font(.section)
                Secondary(TextsKt.ACCOUNT_INTRO + (allowCreate ? " " + TextsKt.SYNC_INTRO : ""))
                VStack(spacing: 10) {
                    AccountButton(provider: "google") { step = .google }
                    AccountButton(provider: "apple") { step = .apple }
                }.padding(.vertical, 4)
                Secondary("O con un código, si prefieres que solo tus dispositivos puedan leer la copia:")
                FitRow {
                    if allowCreate { Button("Activar con un código") { step = .create }.buttonStyle(SecondaryStyle()) }
                    Button("Tengo un código") { step = .join }.buttonStyle(SecondaryStyle())
                }
            } else if step == .google || step == .apple {
                let provider = step == .google ? "google" : "apple"
                let name = TextsKt.accountName(provider: provider) ?? ""
                Text("Continuar con \(name)").font(.section)
                Toggle(TextsKt.ACCOUNT_CONSENT.replacingOccurrences(of: "Google o Apple", with: name), isOn: $consent).font(.small)
                Toggle(TextsKt.SYNC_AGE, isOn: $adult).font(.small)
                Secondary(TextsKt.ACCOUNT_CODE_NOTE)
                FitRow {
                    // La pantalla de Google o Apple dentro de la app; se cierra sola al volver a brivel://cuenta
                    Button("Continuar") {
                        model.runAsync("Has entrado con \(name): tus datos se sincronizan en tus dispositivos") {
                            guard let start = URL(string: core.signInUrl(provider: provider)) else { return }
                            let back = try await webAuth.authenticate(using: start, callbackURLScheme: "brivel")
                            let query = URLComponents(url: back, resolvingAgainstBaseURL: false)?.queryItems ?? []
                            guard let ticket = query.first(where: { $0.name == "cuenta" })?.value else {
                                let error = query.first(where: { $0.name == "cuenta-error" })?.value ?? "No se pudo entrar: vuelve a intentarlo"
                                throw NSError(domain: "brivel", code: 0, userInfo: [NSLocalizedDescriptionKey: error])
                            }
                            try await core.signIn(provider: provider, ticket: ticket)
                            step = .idle
                        }
                    }.buttonStyle(PrimaryStyle()).disabled(!consent || !adult)
                    Button("Cancelar") { step = .idle }.buttonStyle(SecondaryStyle())
                }
            } else {
                let joining = step == .join
                Text(joining ? "Unirse con un código" : "Activar la sincronización").font(.section)
                if joining {
                    TextField("Código del otro dispositivo", text: $typed).textFieldStyle(.roundedBorder).font(.mono(18))
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                    Secondary(TextsKt.SYNC_JOIN_NOTE)
                }
                Toggle(TextsKt.SYNC_CONSENT, isOn: $consent).font(.small)
                Toggle(TextsKt.SYNC_AGE, isOn: $adult).font(.small)
                if !joining { Secondary(TextsKt.SYNC_NO_RECOVERY) }
                FitRow {
                    Button(joining ? "Unirse" : "Activar") {
                        let text = typed
                        if joining {
                            model.runAsync("Sincronizado: se han juntado los datos de este móvil con los de la copia") {
                                try await core.joinSync(text: text)
                            }
                        } else {
                            model.runAsync("Activada. Apunta el código: lo necesitas para añadir otro dispositivo") {
                                _ = try await core.createSync()
                            }
                            showCode = true
                        }
                        step = .idle
                    }.buttonStyle(PrimaryStyle()).disabled(!consent || !adult || (joining && typed.isEmpty))
                    Button("Cancelar") { step = .idle }.buttonStyle(SecondaryStyle())
                }
            }
        }
        .confirmationDialog("¿Borrar la copia del servidor?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) {
                model.runAsync("Copia borrada del servidor. Tus datos siguen en este móvil") { try await core.deleteSync() }
            }
        } message: {
            Text("Los demás dispositivos dejarán de sincronizar. Los datos de este móvil se quedan.")
        }
    }
}
