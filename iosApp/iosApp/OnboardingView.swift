import SwiftUI
import Shared

private let goals: [(GoalKind, String, String)] = [
    (.race, "Preparar una carrera", "Un plan con fecha hacia tu prueba: carrera, bici, natación, triatlón…"),
    (.fitness, "Ponerme en forma", "Más resistencia y fuerza para el día a día, sin fecha."),
    (.strength, "Ganar fuerza y músculo", "Fuerza primero, con algo de cardio para la salud."),
    (.health, "Mejorar mi salud a largo plazo", "Cumplir lo que recomienda la OMS, con constancia."),
]
private let raceSports = [("running", "Carrera a pie"), ("cycling", "Bici"), ("swimming_pool", "Natación"),
                          ("triathlon", "Triatlón"), ("duathlon", "Duatlón")]

private let questions = 7  // objetivo, deportes, semana, fuerza, nivel, año y aviso de salud

/// Una pregunta por pantalla (lienzo de Pulso, 2026-11-03): atrás, progreso, la pregunta y «Siguiente» abajo. Sin atajos.
private struct Question<Content: View>: View {
    let number: Int
    let title: String
    var subtitle: String?
    let back: (() -> Void)?
    var next = "Siguiente"
    var nextEnabled = true
    let onNext: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                if let back {
                    Button(action: back) {
                        Image(systemName: "chevron.left").font(.ui(17, .semibold)).frame(width: 44, height: 44)
                            .background(Palette.surface).clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Atrás")
                }
                if number > 0 {
                    HStack(spacing: 4) {
                        ForEach(0..<questions, id: \.self) { i in Capsule().fill(i < number ? Palette.ok : Palette.chip).frame(height: 6) }
                    }
                    .accessibilityElement()
                    .accessibilityLabel("Paso \(number) de \(questions)")
                }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(title).font(.ui(30, .heavy, relativeTo: .largeTitle))
                    if let subtitle { Secondary(subtitle) }
                    content
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button(next, action: onNext).buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity).disabled(!nextEnabled)
        }
        .padding(20)
        .background(Palette.bg)
    }
}

/// Primera vez: bienvenida y una pregunta por pantalla hasta el aviso de salud. Con perfil, solo el objetivo.
struct OnboardingView: View {
    @EnvironmentObject var model: AppModel
    @State private var step = -1
    @State private var goal: GoalKind?
    @State private var raceName = ""
    @State private var raceSport = "running"
    @State private var raceDate = Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date()
    @State private var distance = ""
    @State private var trail = false
    @State private var priority = "A"
    @State private var draft = ProfileDraft(nil)
    @State private var accepted = false

    private var hasProfile: Bool { model.core.data.profile != nil }

    private var race: RaceInfo? {
        guard goal == .race else { return nil }
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        let km = Double(distance.replacingOccurrences(of: ",", with: "."))
        return RaceInfo(name: raceName.trimmingCharacters(in: .whitespaces), sport: raceSport, date: f.string(from: raceDate),
                        priority: priority, distanceKm: km.map { KotlinDouble(double: $0) }, trail: trail)
    }

    private func save() {
        guard let goal else { return }
        let profile = draft.core
        let race = self.race
        let firstTime = !hasProfile
        model.run {
            if firstTime { model.core.saveProfile(profile: profile) }
            try model.core.setGoal(kind: goal, race: race)
        }
    }

    var body: some View {
        switch step == -1 ? (hasProfile ? 1 : 0) : step {
        case 0:
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Brivel").font(.ui(36, .heavy, relativeTo: .largeTitle)).padding(.top, 40)
                    Text("El sistema operativo de tu cuerpo").font(.ui(20, .semibold, relativeTo: .title3)).foregroundStyle(Palette.ok)
                    Secondary("Te dice qué hacer hoy y por qué, con tus datos y según la evidencia. Gratis, sin anuncios y de código abierto.")
                    Secondary(TextsKt.PRIVACY_NOTE)
                    Link("Política de privacidad", destination: URL(string: TextsKt.PRIVACY_URL)!).font(.small)
                    Button("Empezar") { step = 1 }.buttonStyle(PrimaryStyle()).frame(maxWidth: .infinity)
                    SyncSection(allowCreate: false)
                }
                .padding(20)
            }
            .background(Palette.bg)
        case 1:
            let raceOk = goal != .race || !raceName.trimmingCharacters(in: .whitespaces).isEmpty
            Question(number: hasProfile ? 0 : 1, title: "¿Qué quieres conseguir?", subtitle: "Podrás cambiarlo o abandonarlo cuando quieras.",
                     back: hasProfile ? nil : ({ step = 0 } as () -> Void), next: hasProfile ? "Guardar objetivo" : "Siguiente",
                     nextEnabled: goal != nil && raceOk, onNext: { if hasProfile { save() } else { step = 2 } }) {
                ForEach(goals, id: \.1) { kind, title, text in
                    Button { goal = kind } label: {
                        Module(accent: goal == kind ? Palette.ok : nil, background: goal == kind ? Palette.okSoft : Palette.surface) {
                            Text(title).font(.section).foregroundStyle(Palette.text)
                            Secondary(text)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(goal == kind ? .isSelected : [])
                }
                if goal == .race {
                    Module {
                        TextField("Nombre de la carrera", text: $raceName).textFieldStyle(.roundedBorder)
                        FlowChips(options: raceSports, isSelected: { raceSport == $0 }) { raceSport = $0 }
                        DatePicker("Fecha", selection: $raceDate, in: Date()..., displayedComponents: .date)
                        TextField("Distancia en km (opcional)", text: $distance).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                        if raceSport == "running" { Toggle("Es por montaña (trail)", isOn: $trail) }
                        FlowChips(options: [("A", "Principal"), ("B", "Importante"), ("C", "Preparatoria")], isSelected: { priority == $0 }) { priority = $0 }
                    }
                }
            }
        case 2:
            Question(number: 2, title: "¿Qué haces además de caminar?", subtitle: "Si no marcas nada, el plan es de caminar.",
                     back: { step = 1 }, onNext: { step = 3 }) {
                SportPicker(cardio: draft.cardio) { draft.cardio = $0 }
            }
        case 3:
            Question(number: 3, title: "¿Cuánto tiempo tienes cada día?", subtitle: "Lo podrás cambiar cuando quieras.",
                     back: { step = 2 }, nextEnabled: draft.days.values.contains { $0 > 0 }, onNext: { step = 4 }) {
                Module { WeekBars(days: draft.days) { draft.days = $0 } }
            }
        case 4:
            Question(number: 4, title: "¿Dónde haces fuerza?", subtitle: "La fuerza ayuda a cualquier deporte y a la salud.",
                     back: { step = 3 }, onNext: { step = 5 }) {
                ChoiceCards(options: strengthChoices, selected: draft.strength) { draft.strength = $0 }
            }
        case 5:
            Question(number: 5, title: "¿Cuánto entrenas ahora?", subtitle: "Para empezar a tu medida.",
                     back: { step = 4 }, onNext: { step = 6 }) {
                ChoiceCards(options: levelChoices, selected: draft.level) { draft.level = $0 }
            }
        case 6:
            Question(number: 6, title: "¿En qué año naciste?", subtitle: "Para calcular tus zonas de pulso.",
                     back: { step = 5 }, nextEnabled: ageOk(draft.birthYear), onNext: { step = 7 }) {
                TextField("1990", text: Binding(get: { draft.birthYear.map(String.init) ?? "" },
                                                set: { draft.birthYear = Int($0.filter(\.isNumber).prefix(4)) }))
                    .keyboardType(.numberPad).textFieldStyle(.roundedBorder).font(.mono(20))
                if (draft.birthYear ?? 0) >= 1000 && !ageOk(draft.birthYear) {
                    Secondary("Hace falta tener al menos \(TextsKt.MIN_AGE) años.", color: Palette.caution)
                }
            }
        default:
            Question(number: 7, title: "Antes de empezar", back: { step = 6 }, next: "Ver mi plan", nextEnabled: accepted, onNext: save) {
                Module(background: Palette.cautionSoft) { Secondary(TextsKt.HEALTH_NOTICE, color: Palette.text) }
                Toggle("Lo he leído y entreno bajo mi responsabilidad", isOn: $accepted).font(.small)
            }
        }
    }
}
