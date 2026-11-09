import SwiftUI
import Shared

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

    private enum Step { case idle, create, join }
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
            if let code = core.syncCode {
                Text("Sincronización activada").font(.section)
                Secondary(TextsKt.SYNC_ACTIVE)
                if showCode {
                    Text(grouped(code)).font(.mono(20, .semibold)).textSelection(.enabled)
                    Secondary("Escríbelo en el otro dispositivo, en «Usar en otros dispositivos» → «Tengo un código».")
                }
                HStack {
                    Button("Sincronizar ahora") { model.syncNow() }.buttonStyle(PrimaryStyle())
                    Button(showCode ? "Ocultar código" : "Ver código") { showCode.toggle() }.buttonStyle(SecondaryStyle())
                }
                Button("Dejar de sincronizar aquí") {
                    model.run("Este móvil ya no sincroniza. La copia sigue en el servidor") { core.forgetSync() }
                }.font(.small).buttonStyle(TextActionStyle())
                Button("Borrar la copia del servidor", role: .destructive) { confirmDelete = true }.font(.small).buttonStyle(TextActionStyle())
            } else if step == .idle {
                Text(allowCreate ? "Usar en otros dispositivos" : "¿Ya usas Brivel en otro dispositivo?").font(.section)
                Secondary(allowCreate ? TextsKt.SYNC_INTRO : "Únete con su código y tendrás aquí los mismos datos.")
                HStack {
                    if allowCreate { Button("Activar") { step = .create }.buttonStyle(PrimaryStyle()) }
                    Button("Tengo un código") { step = .join }.buttonStyle(SecondaryStyle())
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
                HStack {
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
