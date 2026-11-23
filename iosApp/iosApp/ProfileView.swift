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

    private func save() {
        let p = draft.core
        model.run { model.core.saveProfile(profile: p) }
    }

    var body: some View {
        let _ = model.version
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
                    if model.core.data.goal?.kind != .own {
                    Module {
                        Text("Tus deportes").font(.section)
                        SportPicker(cardio: draft.cardio) { draft.cardio = $0; save() }
                    }
                    Module {
                        Text("Tu semana").font(.section)
                        WeekBars(days: draft.days) { draft.days = $0; save() }
                    }
                    Module {
                        Text("Fuerza").font(.section)
                        Segmented(options: strengthChoices.map { ($0.0, $0.1) }, selected: draft.strength) { draft.strength = $0; save() }
                    }.id("fuerza")
                    Module {
                        Text("Tu nivel").font(.section)
                        Segmented(options: levelChoices.map { ($0.0, $0.1) }, selected: draft.level) { draft.level = $0; save() }
                    }
                    }
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
                    GearSection()
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
