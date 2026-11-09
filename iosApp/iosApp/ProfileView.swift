import SwiftUI
import AuthenticationServices
import Shared


enum ReminderError: LocalizedError {
    case denied
    var errorDescription: String? { "Sin permiso para avisarte: actívalo en Ajustes → Notificaciones → Brivel" }
}

/// Borrador editable del perfil en tipos de Swift; se convierte al del núcleo al guardar.
struct ProfileDraft {
    var birthYear: Int?
    var level = "beginner"
    var strength = "bodyweight"
    var cardio: [String] = ["walking"]
    var days: [String: Int] = ["MON": 45, "WED": 45, "SAT": 60]
    var renameActivities = true
    var screening: Screening?  // cuestionario del ACSM: las apps no lo preguntan (decisión 2026-10-21); se conserva si existe

    init(_ p: UserProfile?) {
        guard let p else { return }
        birthYear = p.birthYear?.intValue
        level = p.level
        strength = p.strength
        cardio = p.cardio
        days = p.days.mapValues { $0.intValue }
        renameActivities = p.renameActivities
        screening = p.screening
    }

    var core: UserProfile {
        UserProfile(birthYear: birthYear.map { KotlinInt(int: Int32($0)) }, level: level, strength: strength, cardio: cardio,
                    days: days.mapValues { KotlinInt(int: Int32($0)) }, renameActivities: renameActivities,
                    screening: screening)
    }
}

/// Carreras preparatorias (B o C) antes de la principal: el plan afina unos días antes y recupera después.
struct OtherRacesView: View {
    @EnvironmentObject var model: AppModel
    @State private var adding = false
    @State private var name = ""
    @State private var date = Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date()
    @State private var priority = "C"

    var body: some View {
        let core = model.core
        if let goal = core.data.goal, goal.kind == .race, let main = goal.race {
            VStack(alignment: .leading, spacing: 8) {
                Secondary("Otras carreras antes de \(main.name)")
                ForEach(goal.otherRaces, id: \.date) { r in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(r.name).font(.ui(15, .bold, relativeTo: .subheadline))
                            Secondary(ScreensKt.dateLabel(date: r.date) + (r.priority == "B" ? " · importante" : " · de preparación"))
                        }
                        Spacer()
                        Button("Quitar") { model.run { core.removeRace(date: r.date) } }.buttonStyle(TextActionStyle())
                    }
                }
                if adding {
                    TextField("Nombre de la carrera", text: $name).textFieldStyle(.roundedBorder)
                    DatePicker("Fecha", selection: $date, displayedComponents: .date)
                    Picker("Prioridad", selection: $priority) {
                        Text("Importante (B)").tag("B")
                        Text("De preparación (C)").tag("C")
                    }.pickerStyle(.segmented)
                    HStack {
                        Button("Añadir") {
                            let f = DateFormatter()
                            f.dateFormat = "yyyy-MM-dd"
                            f.locale = Locale(identifier: "en_US_POSIX")
                            let race = RaceInfo(name: name.trimmingCharacters(in: .whitespaces), sport: main.sport, date: f.string(from: date),
                                                priority: priority, distanceKm: nil, trail: false)
                            model.run("Carrera añadida") { try core.addRace(race: race); adding = false; name = "" }
                        }.buttonStyle(PrimaryStyle())
                        Button("Cancelar") { adding = false }.buttonStyle(TextActionStyle())
                    }
                } else {
                    Secondary("El plan afina unos días antes de cada una y te deja recuperar después.")
                    Button("Añadir otra carrera") { adding = true }.buttonStyle(SecondaryStyle())
                }
            }
        }
    }
}

struct FlowChips: View {
    let options: [(String, String)]
    let isSelected: (String) -> Bool
    let toggle: (String) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), alignment: .leading)], alignment: .leading, spacing: 8) {
            ForEach(options, id: \.0) { key, label in Chip(label: label, selected: isSelected(key)) { toggle(key) } }
        }
    }
}

struct ProfileView: View {
    @EnvironmentObject var model: AppModel
    @State private var draft = ProfileDraft(nil)
    @State private var loaded = false
    @Environment(\.webAuthenticationSession) private var webAuth
    @State private var confirmDelete = false
    @State private var editingWeek = false

    private func save() {
        let p = draft.core
        model.run { model.core.saveProfile(profile: p) }
    }

    var body: some View {
        let _ = model.version
        let core = model.core
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Perfil").font(.screenTitle)
                // Se guarda solo: cada cambio rehace el plan al momento (lienzo de Pulso, opción B)
                Module {
                    Text("Qué haces además de caminar").font(.section)
                    SportPicker(cardio: draft.cardio) { draft.cardio = $0; save() }
                }
                Module {
                    Text("Tu semana").font(.section)
                    WeekBars(days: draft.days, editable: false).contentShape(Rectangle()).onTapGesture { editingWeek = true }
                    Button("Cambiar tu semana") { editingWeek = true }.buttonStyle(SecondaryStyle())
                }
                // En una hoja aparte: dentro del Perfil, desplazar la pantalla movería las barras
                .sheet(isPresented: $editingWeek) {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Tu semana").font(.screenTitle)
                        WeekBars(days: draft.days) { draft.days = $0; save() }
                        Button("Hecho") { editingWeek = false }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
                    }
                    .padding(20)
                    .presentationDetents([.large])
                    .interactiveDismissDisabled()
                    .background(Palette.surface)
                }
                Module {
                    Text("Fuerza").font(.section)
                    Segmented(options: strengthChoices.map { ($0.0, $0.1) }, selected: draft.strength) { draft.strength = $0; save() }
                }
                Module {
                    Text("Tu nivel").font(.section)
                    Segmented(options: levelChoices.map { ($0.0, $0.1) }, selected: draft.level) { draft.level = $0; save() }
                }
                RoutinesSection()
                LibrarySection()
                Module {
                    Text("Año de nacimiento").font(.section)
                    TextField("1990", text: Binding(get: { draft.birthYear.map(String.init) ?? "" }, set: { v in
                        draft.birthYear = Int(v.filter(\.isNumber).prefix(4))
                        if ageOk(draft.birthYear) { save() }
                    })).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                    if (draft.birthYear ?? 0) >= 1000 && !ageOk(draft.birthYear) {
                        Secondary("Hace falta tener al menos \(TextsKt.MIN_AGE) años.", color: Palette.caution)
                    }
                }
                Module {
                    Toggle(isOn: Binding(get: { core.data.reminderHour != nil }, set: { on in
                        if on {
                            model.runAsync {
                                guard try await Reminders.requestAccess() else {
                                    throw ReminderError.denied
                                }
                                try core.setReminder(hour: KotlinInt(int: 8))
                            }
                        } else {
                            model.run { try core.setReminder(hour: nil) }
                        }
                    })) {
                        VStack(alignment: .leading, spacing: 4) {
                            Secondary("Recordatorio diario")
                            Secondary("Un aviso con la sesión del día. Los días de descanso o con todo hecho no avisa.")
                        }
                    }
                    if let hour = core.data.reminderHour?.intValue {
                        Stepper(String(format: "A las %02d:00", hour), value: Binding(get: { hour }, set: { h in
                            model.run { try core.setReminder(hour: KotlinInt(int: Int32((h + 24) % 24))) }
                        }), in: -1...24)
                        .font(.mono(16))
                    }
                }
                Module {
                    Secondary("Datos de salud del iPhone (Apple Salud)")
                    if let health = core.data.health {
                        Text("Conectado: \(health.days.count) días y \(health.workouts.count) entrenamientos").font(.section)
                        Secondary("Datos al día del \(health.syncedAt). Los permisos se cambian en Ajustes → Salud → Acceso a datos.")
                        HStack {
                            Button("Actualizar ahora") { model.runAsync("Datos actualizados") { try await HealthKitReader.shared.sync(core) } }
                                .buttonStyle(PrimaryStyle())
                            Button("Olvidar") { model.run { core.clearHealth() } }.buttonStyle(SecondaryStyle())
                        }
                    } else if HealthKitReader.shared.available {
                        Secondary("Lee de Apple Salud tu sueño, tu variabilidad cardíaca, tu pulso en reposo, tu peso, tus pasos y tus entrenamientos de cualquier reloj o app. Solo lectura y solo en este iPhone.")
                        Button("Conectar Apple Salud") {
                            model.runAsync("Datos de Apple Salud leídos") {
                                try await HealthKitReader.shared.requestAccess()
                                try await HealthKitReader.shared.sync(core)
                            }
                        }.buttonStyle(PrimaryStyle())
                    } else {
                        Secondary("Apple Salud no está disponible en este dispositivo.")
                    }
                }
                Module {
                    Secondary("Reloj (intervals.icu)")
                    if let linked = core.linkedAthlete {
                        Text("Vinculado: \(linked)").font(.section)
                        if let cache = core.data.cache { Secondary("Datos al día del \(cache.syncedAt)") }
                        HStack {
                            Button("Actualizar ahora") { model.runAsync("Datos actualizados") { try await core.sync() } }.buttonStyle(PrimaryStyle())
                            Button("Desvincular") {
                                model.runAsync("intervals.icu desvinculado") { try await core.disconnectIntervals() }
                            }.buttonStyle(SecondaryStyle())
                        }
                    } else {
                        Secondary("intervals.icu es gratis y recoge los datos de tu reloj (Garmin, Polar, Coros, Suunto, Wahoo…). Inicia sesión allí y da permiso a Brivel.")
                        // La pantalla de permisos de intervals.icu dentro de la app; se cierra sola al volver a brivel://intervals
                        Button("Conectar con intervals.icu") {
                            model.runAsync("intervals.icu conectado") {
                                let url = try await core.startIntervals(redirect: "brivel://intervals")
                                guard let start = URL(string: url) else { return }
                                let back = try await webAuth.authenticate(using: start, callbackURLScheme: "brivel")
                                try await core.finishIntervals(callback: back.absoluteString)
                            }
                        }.buttonStyle(PrimaryStyle())
                    }
                }
                GarminSection()
                SyncSection()
                Module {
                    Secondary("Tus datos")
                    Secondary(TextsKt.PRIVACY_NOTE)
                    Toggle("Poner a tus actividades de intervals.icu un título que diga lo que fueron", isOn: Binding(
                        get: { draft.renameActivities },
                        set: { draft.renameActivities = $0; let p = draft.core; model.run { core.saveProfile(profile: p) } }))
                        .font(.small)
                    ShareLink("Exportar todos mis datos (JSON)", item: core.exportJson())
                    Button("Borrar todo de este móvil", role: .destructive) { confirmDelete = true }
                }
                Module {
                    Secondary("Aviso de salud")
                    Secondary(TextsKt.HEALTH_NOTICE)
                }
                Secondary("Brivel es gratis y de código abierto (AGPL-3.0). Sin niveles de pago, sin anuncios y sin notificaciones comerciales.")
                HStack(spacing: 16) {
                    Link("Privacidad", destination: URL(string: TextsKt.PRIVACY_URL)!)
                    Link("Términos", destination: URL(string: TextsKt.TERMS_URL)!)
                    Link("Código fuente", destination: URL(string: TextsKt.SOURCE_URL)!)
                }.font(.small)
            }.padding(16)
        }
        .background(Palette.bg)
        .onAppear {
            if !loaded { draft = ProfileDraft(core.data.profile); loaded = true }
        }
        .confirmationDialog("¿Borrar todo?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Borrar", role: .destructive) { model.run { core.deleteAll() } }
        } message: {
            Text("Se borran tu perfil, tu objetivo, tu historial y la clave de intervals.icu de este móvil. No se puede deshacer. Tus datos en intervals.icu no se tocan.")
        }
    }
}
