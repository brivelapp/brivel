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
                    ChipChoice(title: "Prioridad", options: [("B", "Importante (B)"), ("C", "De preparación (C)")], selection: $priority)
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
    @Binding var settings: Bool  // la rueda del Perfil abre Ajustes
    var openGoal: () -> Void = {}
    @State private var screen = UserDefaults.standard.string(forKey: "page")  // `-page week` en el CI; Tu semana, Objetivo y el material tienen su pantalla (2026-11-23)

    /// «3 días · 2 h 30 min»: los días con minutos y el total de la semana.
    private func weekLine(_ days: [String: Int]) -> String {
        let on = days.values.filter { $0 > 0 }
        if on.isEmpty { return "Sin días puestos" }
        let total = on.reduce(0, +)
        return "\(on.count) \(on.count == 1 ? "día" : "días") · \(total / 60) h" + (total % 60 == 0 ? "" : " \(total % 60) min")
    }

    private func save() {
        let p = draft.core
        model.run { model.core.saveProfile(profile: p) }
    }

    var body: some View {
        let _ = model.version
        switch screen {
        case "week":
            SubScreen(title: "Tu semana", back: { screen = nil }) {
                WeekBars(days: draft.days) { draft.days = $0; save() }
                Secondary("Sin minutos, ese día es de descanso.")
            }
        case "goal":
            SubScreen(title: "Objetivo", back: { screen = nil }) {
                Module {
                    Text(model.core.data.goal.map { TextsKt.goalLabel(goal: $0) } ?? "Sin objetivo").font(.cardTitle)
                    Secondary("El plan se rehace al momento si lo cambias.")
                }
                Button { openGoal() } label: { Text("Cambiar objetivo").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
                OtherRacesView()
            }
        case "gear":
            SubScreen(title: "Zapatillas y bicis", back: { screen = nil }) { GearSection() }
        default:
            profileList
        }
    }

    private var profileList: some View {
        NavigationStack {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Perfil").font(.screenTitle)
                        Spacer()
                        Button { settings = true } label: {
                            Image(systemName: "gearshape").font(.system(size: 20, weight: .semibold)).foregroundStyle(Palette.text)
                                .frame(width: 44, height: 44).glass(Palette.chip, in: Circle())
                        }
                        .buttonStyle(.plain).accessibilityLabel("Ajustes")
                    }
                    // Se guarda solo: cada cambio rehace el plan al momento (lienzo de Pulso, opción B). Sin plan, deportes,
                    // semana, fuerza y nivel no cambian nada: se preguntan si un día pide un plan
                    // Por secciones (2026-11-23): una fila por dato con su valor; al tocarla se cambia ahí mismo
                    let ciFuerza = UserDefaults.standard.string(forKey: "scrollTo") == "fuerza"  // capturas del CI
                    if model.core.data.goal?.kind != .own {
                        // Revisión de los desplegables (2026-11-23): lo que se elige, aquí mismo con ✓; Tu semana, en su pantalla
                        let cardio = draft.cardio.filter { TextsKt.PICKABLE_CARDIO.contains($0) }
                        Fold(title: "Deportes", line: cardio.isEmpty ? "Solo caminar" : cardio.map { sportLabel($0) }.joined(separator: ", ")) {
                            CheckList(options: TextsKt.PICKABLE_CARDIO.map { ($0, sportLabel($0), nil) }, selected: draft.cardio) {
                                draft.cardio = TextsKt.toggleCardio(cardio: draft.cardio, sport: $0); save()
                            }
                        }
                        RowButton(title: "Tu semana", value: weekLine(draft.days)) { screen = "week" }
                        Fold(title: "Fuerza", line: strengthChoices.first { $0.0 == draft.strength }?.1, startOpen: ciFuerza) {
                            CheckList(options: strengthChoices.map { ($0.0, $0.1, $0.2) }, selected: [draft.strength]) { draft.strength = $0; save() }
                        }.id("fuerza")
                        Fold(title: "Tu nivel", line: levelChoices.first { $0.0 == draft.level }?.1, startOpen: ciFuerza) {
                            CheckList(options: levelChoices.map { ($0.0, $0.1, $0.2) }, selected: [draft.level]) { draft.level = $0; save() }
                        }
                    }
                    Fold(title: "Año de nacimiento", line: draft.birthYear.map(String.init) ?? "Sin poner") {
                        Module {
                            Secondary("Para calcular tus zonas de pulso")
                            TextField("1990", text: Binding(get: { draft.birthYear.map(String.init) ?? "" }, set: { v in
                                draft.birthYear = Int(v.filter(\.isNumber).prefix(4))
                                if ageOk(draft.birthYear) { save() }
                            })).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                            if (draft.birthYear ?? 0) >= 1000 && !ageOk(draft.birthYear) {
                                Secondary("Hace falta tener al menos \(TextsKt.MIN_AGE) años.", color: Palette.caution)
                            }
                        }
                    }
                    // «Cambiar objetivo» vive aquí desde el 2026-11-23 (antes, «Tu objetivo» en Hoy)
                    if let goal = model.core.data.goal {
                        RowButton(title: "Objetivo", value: TextsKt.goalLabel(goal: goal)) { screen = "goal" }
                    }
                    let gear = model.core.gear().rows.count
                    RowButton(title: "Zapatillas y bicis", value: gear == 0 ? "Ninguna todavía" : gear == 1 ? "1 en tu material" : "\(gear) en tu material") { screen = "gear" }
            }.padding(16)
        }
        // `-scrollTo fuerza` al lanzar: las capturas del CI ven la fuerza y el nivel sin poder desplazar
        .onAppear { if let id = UserDefaults.standard.string(forKey: "scrollTo") { proxy.scrollTo(id, anchor: .top) } }
        }
        .background(Palette.bg)
        .keyboardDone()
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $settings) { SettingsView() }
        }
        .onAppear {
            if !loaded { draft = ProfileDraft(model.core.data.profile); loaded = true }
        }
    }
}
