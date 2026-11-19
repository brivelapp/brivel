import SwiftUI
import Shared

/// Pulso (docs/DISENO_PULSO.md): tonos de bosque y piel, formas suaves; el color dice cómo estás. Mismos valores en Android.
enum Palette {
    static let bg = Color(light: 0xF3F0E9, dark: 0x0F1412)
    static let surface = Color(light: 0xFFFFFF, dark: 0x18201C)
    static let chip = Color(light: 0xECE7DC, dark: 0x243029)
    static let text = Color(light: 0x1B2420, dark: 0xEEF2EE)
    static let muted = Color(light: 0x5D6762, dark: 0xA3B0A8)
    static let ok = Color(light: 0x1F7A5C, dark: 0x5FD3A4)
    static let onOk = Color(light: 0xFFFFFF, dark: 0x0B1F16)
    static let okSoft = Color(light: 0xDCEFE5, dark: 0x16302A)
    static let band = Color(light: 0xCDE8DA, dark: 0x1E4A3B)
    static let caution = Color(light: 0x8C5A00, dark: 0xF2B95A)  // #9A6400 daba 4,1:1 sobre cautionSoft
    static let cautionSoft = Color(light: 0xF6E7C8, dark: 0x3A2E17)
    static let danger = Color(light: 0xB3402A, dark: 0xFF8A73)
    // Las zonas no cambian con el modo: son el mismo código de color en la app, el reloj y la documentación
    static let zones: [String: Color] = ["Z1": Color(hex: 0x4ECDC4), "Z2": Color(hex: 0x45B7D1), "Z3": Color(hex: 0xFFA500),
                                         "Z4": Color(hex: 0xFF6B35), "Z5": Color(hex: 0xFF4757), "Z6": Color(hex: 0xC0392B)]
    static let onZone = Color(hex: 0x0B1F22)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

/// Figtree para la interfaz e IBM Plex Mono para los números, empaquetadas (OFL); crecen con el tamaño de texto del sistema.
extension Font {
    static func ui(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("Figtree", size: size, relativeTo: style).weight(weight)
    }
    static func mono(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("IBM Plex Mono", size: size, relativeTo: style).weight(weight)
    }
    static let screenTitle = ui(26, .heavy, relativeTo: .largeTitle)
    static let cardTitle = ui(24, .heavy, relativeTo: .title)
    static let section = ui(15, .bold, relativeTo: .headline)
    static let body15 = ui(15, relativeTo: .subheadline)
    static let small = ui(14, relativeTo: .subheadline)
    static let caption12 = ui(12, relativeTo: .caption)
}

/// Registra las fuentes del paquete de la app al arrancar.
func registerFonts() {
    for url in Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [] {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
}

func sportLabel(_ sport: String) -> String { TextsKt.SPORT_LABEL[sport] ?? sport }

func sportIcon(_ sport: String) -> String {
    switch sport {
    case "running": return "sport_running"
    case "cycling": return "sport_cycling"
    case "swimming_pool", "swimming_open": return "sport_swimming"
    case "walking": return "sport_walking"
    default: return "sport_strength"
    }
}

/// Tarjeta base: superficie, radio 24 y relleno 18. `accent` la remarca (sesión hecha).
struct Module<Content: View>: View {
    var accent: Color? = nil
    var background: Color = Palette.surface
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(accent ?? .clear, lineWidth: 1.5))
    }
}

/// Título de pantalla con su subtítulo (fecha, periodo).
struct ScreenTitle: View {
    let text: String
    var subtitle: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let subtitle { Text(subtitle).font(.ui(14, .medium, relativeTo: .subheadline)).foregroundStyle(Palette.muted) }
            Text(text).font(.screenTitle)
        }.padding(.top, 8)
    }
}

struct Secondary: View {
    let text: String
    var color: Color = Palette.muted
    init(_ text: String, color: Color = Palette.muted) { self.text = text; self.color = color }
    var body: some View { Text(text).font(.small).foregroundStyle(color) }
}

struct Glyph: View {
    let name: String
    var size: CGFloat = 22
    var color: Color = Palette.text
    var body: some View { Image(name).renderingMode(.template).resizable().frame(width: size, height: size).foregroundStyle(color) }
}

/// Icono de deporte en su pastilla, como en la cabecera de la sesión.
struct SportBadge: View {
    let sport: String
    var size: CGFloat = 36
    var body: some View {
        Glyph(name: sportIcon(sport), size: size * 0.56)
            .frame(width: size, height: size)
            .background(Palette.chip)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .accessibilityLabel(sportLabel(sport))
    }
}

struct ZoneChip: View {
    let zone: String
    var body: some View {
        // Mínimos y no medidas fijas: con el texto grande del sistema, «Z2» no cabía en 24 puntos de alto
        Text(zone).font(.mono(12, .semibold, relativeTo: .caption)).foregroundStyle(zone == "Z6" ? .white : Palette.onZone)  // el oscuro daba 3,1:1 en Z6.padding(.horizontal, 6)
            .frame(minWidth: 40, minHeight: 24).background(Palette.zones[zone] ?? Palette.chip).clipShape(Capsule())
    }
}

/// Como FlowRow en Android: en fila mientras quepan, y lo que no cabe baja a la línea siguiente. Cada pieza mide lo que
/// pide su contenido (como mucho, el ancho entero), así que ninguna se parte a media palabra.
struct Flow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal.width ?? .infinity, subviews)
        return CGSize(width: rows.map { $0.width }.max() ?? 0,
                      height: rows.map { $0.height }.reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(bounds.width, subviews) {
            var x = bounds.minX
            for (i, size) in row.items {
                subviews[i].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func arrange(_ width: CGFloat, _ subviews: Subviews) -> [(items: [(Int, CGSize)], width: CGFloat, height: CGFloat)] {
        var rows: [(items: [(Int, CGSize)], width: CGFloat, height: CGFloat)] = []
        for (i, view) in subviews.enumerated() {
            let ideal = view.sizeThatFits(.unspecified)
            let size = ideal.width <= width ? ideal : view.sizeThatFits(ProposedViewSize(width: width, height: nil))
            if let last = rows.last, last.width + spacing + size.width <= width {
                rows[rows.count - 1].items.append((i, size))
                rows[rows.count - 1].width += spacing + size.width
                rows[rows.count - 1].height = max(last.height, size.height)
            } else {
                rows.append(([(i, size)], size.width, size.height))
            }
        }
        return rows
    }
}

/// En fila si cabe; si no (texto grande del sistema, pantalla estrecha), uno debajo de otro. Para botones y opciones.
struct FitRow<Content: View>: View {
    var spacing: CGFloat = 8
    @ViewBuilder var content: Content
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: spacing) { content }
            VStack(alignment: .leading, spacing: spacing) { content }
        }
    }
}

/// Un título con su dato a la derecha; si con el texto grande no caben juntos, el dato baja a la línea siguiente.
struct TitleRow<Title: View, Trailing: View>: View {
    @ViewBuilder var title: Title
    @ViewBuilder var trailing: Trailing
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .lastTextBaseline) { title; Spacer(minLength: 10); trailing }
            VStack(alignment: .leading, spacing: 4) { title; trailing }
        }
    }
}

extension View {
    /// En iPad o en horizontal, una columna centrada de 640 puntos: a todo lo ancho, botones y barras medían un palmo.
    func readableWidth() -> some View { frame(maxWidth: 640).frame(maxWidth: .infinity).background(Palette.bg.ignoresSafeArea()) }
}

extension View {
    /// Controles en Liquid Glass en iOS 26, como la barra inferior del sistema; antes de iOS 26, el relleno plano de Pulso.
    /// `tint` colorea el cristal (lo elegido, la acción principal). Solo controles: las tarjetas de contenido siguen planas,
    /// como pide Apple para el cristal.
    @ViewBuilder func glass<S: Shape>(_ fill: Color, tint: Color? = nil, in shape: S, enabled: Bool = true) -> some View {
        if #available(iOS 26, *), enabled {
            glassEffect(tint.map { Glass.regular.tint($0).interactive() } ?? Glass.regular.interactive(), in: shape)
        } else {
            background(fill).clipShape(shape)
        }
    }
}

extension View {
    /// El teclado numérico de iOS no trae «Intro»: «Listo» encima del teclado y deslizar hacia abajo lo cierran.
    func keyboardDone() -> some View {
        scrollDismissesKeyboard(.interactively).toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Listo") {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }.font(.ui(16, .bold))
            }
        }
    }
}

struct PrimaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { Primary(configuration: configuration) }

    private struct Primary: View {
        let configuration: ButtonStyleConfiguration
        @Environment(\.isEnabled) private var enabled
        var body: some View {
            configuration.label.font(.ui(15, .bold)).padding(.horizontal, 20).frame(minHeight: 50)
                .foregroundStyle(enabled ? Palette.onOk : Palette.muted).glass(enabled ? Palette.ok : Palette.chip, tint: enabled ? Palette.ok : nil, in: Capsule())
                .opacity(configuration.isPressed ? 0.8 : 1).scaleEffect(configuration.isPressed ? 0.98 : 1)
        }
    }
}

/// Acción de texto: siempre subrayada, para que no se confunda con una etiqueta.
struct TextActionStyle: ButtonStyle {
    var color: Color = Palette.ok
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.underline().foregroundStyle(configuration.role == .destructive ? Palette.danger : color)
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// Flecha de una fila que se despliega o, con `open` nulo, chevron de una fila que abre algo.
struct RowChevron: View {
    var open: Bool? = nil
    var body: some View {
        Glyph(name: "chevron_right", size: 18, color: Palette.muted)
            .rotationEffect(.degrees(open == nil ? 0 : open! ? 270 : 90))
            .accessibilityHidden(true)
    }
}

struct SecondaryStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.ui(15, .semibold)).padding(.horizontal, 18).frame(minHeight: 50)
            .foregroundStyle(Palette.text).glass(Palette.chip, in: Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1).scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

/// Cómo estás hoy: el color y el icono dicen bien, con cuidado o descanso; debajo, el porqué.
struct StateCard: View {
    let tone: Tone
    let title: String
    let detail: [Reason]
    var source: String? = nil

    var body: some View {
        let (bg, fg, icon): (Color, Color, String) =
            tone == .caution ? (Palette.cautionSoft, Palette.caution, "state_caution")
            : tone == .rest ? (Palette.chip, Palette.muted, "state_rest")
            : (Palette.okSoft, Palette.ok, "state_ok")
        HStack(alignment: detail.isEmpty ? .center : .top, spacing: 12) {
            Glyph(name: icon, color: tone == .ok ? Palette.onOk : Palette.bg)
                .frame(width: 44, height: 44).background(fg).clipShape(Circle())
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.ui(16, .bold, relativeTo: .headline))
                if let source { Source(text: source) }
                ForEach(Array(detail.enumerated()), id: \.offset) { _, r in ReasonRow(reason: r, color: fg) }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// Motivo con su etiqueta de evidencia: se toca para ver qué significa.
struct ReasonRow: View {
    let reason: Reason
    var color: Color = Palette.caution
    @State private var open = false

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 3, height: 16).padding(.top, 3)
            VStack(alignment: .leading, spacing: 4) {
                Text(reason.text).font(.small)
                if let tag = reason.evidence {
                    Button(tag) { open.toggle() }
                        .font(.ui(11, .semibold, relativeTo: .caption2)).foregroundStyle(Palette.muted)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(Palette.chip).clipShape(RoundedRectangle(cornerRadius: 8))
                    if open {
                        Secondary("\(TextsKt.EVIDENCE_MEANING[tag] ?? "") Fuentes en la documentación del motor.")
                    }
                }
            }
        }
    }
}

func minutesText(_ m: Double) -> String {
    if m < 1 { return "\(Int(m * 60))″" }
    if m.truncatingRemainder(dividingBy: 1) == 0 { return "\(Int(m))′" }
    return "\(Int(m))′\(String(format: "%02d", Int(m.truncatingRemainder(dividingBy: 1) * 60)))″"
}

/// Una sesión: deporte y duración, nombre llano, cómo saber si vas bien y sus pasos con su zona; el nombre técnico al tocar.
struct SessionCard<Actions: View>: View {
    let item: PlanItem
    let done: Bool
    @ViewBuilder var actions: Actions
    @State private var details = false

    var body: some View {
        Module(accent: done ? Palette.ok : nil) {
            HStack(spacing: 10) {
                SportBadge(sport: item.sport)
                Text(item.chosen ? "\(sportLabel(item.sport)) · elegida por ti" : sportLabel(item.sport))
                    .font(.ui(13, .semibold, relativeTo: .footnote)).foregroundStyle(Palette.muted)
                Spacer()
                if done {
                    Glyph(name: "check", size: 18, color: Palette.ok)
                    Text("Hecha").font(.ui(13, .bold, relativeTo: .footnote)).foregroundStyle(Palette.ok)
                }
                (Text("\(item.durationMin)").font(.mono(22, .semibold, relativeTo: .title2))
                    + Text(" min").font(.mono(13, relativeTo: .footnote)).foregroundColor(Palette.muted))
            }
            Text(item.title).font(.cardTitle)
            // La cifra manda (2026-11-18): ritmo, vatios o pulso en grande; sin datos aún, cómo saber si vas bien
            if let target = item.target {
                VStack(alignment: .leading, spacing: 2) {
                    Text(target).font(.mono(28, .semibold, relativeTo: .title)).lineLimit(1).minimumScaleFactor(0.6)
                    if let note = item.targetNote { Text(note).font(.ui(15, .bold, relativeTo: .subheadline)) }
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 16).padding(.vertical, 12)
                .background(Palette.bg).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            } else {
                Secondary(item.hint)
            }
            ForEach(Array(item.steps.enumerated()), id: \.offset) { _, s in
                HStack(spacing: 10) {
                    ZoneChip(zone: s.intensityZone)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(s.name.prefix(1).uppercased() + s.name.dropFirst()).font(.ui(15, .semibold, relativeTo: .subheadline))
                        if let target = s.target { Text(target).font(.mono(12, relativeTo: .caption)).foregroundStyle(Palette.muted) }
                        if item.testKind != nil, let detail = s.detail { Secondary(detail) }  // en un test, cómo se hace cada tramo
                    }
                    Spacer()
                    Text("\(s.reps > 1 ? "\(s.reps)×" : "")\(s.meters.map { "\($0.intValue) m" } ?? minutesText(s.minutes))")
                        .font(.mono(14, relativeTo: .subheadline)).foregroundStyle(Palette.muted)
                }
            }
            ForEach(item.exercises, id: \.self) { Text($0).font(.body15) }
            actions
            Button(details ? "Menos detalles" : "Más detalles") { details.toggle() }.font(.ui(13, .bold, relativeTo: .footnote))
            if details {
                Secondary("Nombre técnico: \(item.technicalName)")
                if let notes = item.notes { Secondary(notes) }
            }
        }
    }
}

/// La semana en círculos: relleno si entrenaste, anillo de color hoy.
struct WeekDots: View {
    let days: [WeekDot]
    var body: some View {
        HStack {
            ForEach(Array(days.enumerated()), id: \.offset) { i, d in
                if i > 0 { Spacer(minLength: 0) }
                VStack(spacing: 6) {
                    ZStack {
                        Circle().fill(d.done ? Palette.ok : Color.clear)
                        Circle().stroke(d.isToday ? Palette.ok : Palette.chip, lineWidth: 2)
                        if d.planned && !d.done { Circle().fill(Palette.muted).frame(width: 6, height: 6) }
                    }.frame(width: 34, height: 34)
                    Text(d.letter).font(.ui(12, .semibold, relativeTo: .caption)).foregroundStyle(Palette.muted)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(d.letter): \(d.done ? "hecho" : d.planned ? "planificado" : "libre")")
            }
        }
    }
}

/// Un valor frente a tu rango normal: banda verde = tu rango, marca = el valor (ámbar si se sale hacia el lado malo).
struct RangeBar: View {
    let value: Double, low: Double, high: Double, domainMin: Double, domainMax: Double, good: Bool
    var body: some View {
        GeometryReader { geo in
            let span = domainMax - domainMin > 0 ? domainMax - domainMin : 1
            let f = { (v: Double) in CGFloat(min(max((v - domainMin) / span, 0), 1)) * geo.size.width }
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.chip).frame(height: 8)
                Capsule().fill(Palette.band).frame(width: f(high) - f(low), height: 8).offset(x: f(low))
                RoundedRectangle(cornerRadius: 2).fill(good ? Palette.ok : Palette.caution).frame(width: 4, height: 16).offset(x: f(value) - 2)
            }.frame(height: 16)
        }.frame(height: 16)
    }
}

/// Barra de progreso fina hacia un objetivo (minutos OMS).
struct ProgressLine: View {
    let fraction: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.chip)
                Capsule().fill(Palette.ok).frame(width: geo.size.width * min(max(fraction, 0), 1))
            }
        }.frame(height: 6)
    }
}

struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.ui(14, .semibold, relativeTo: .subheadline)).padding(.horizontal, 14).padding(.vertical, 8)
                .foregroundStyle(Palette.text)  // verde sobre verde suave daba 4,4:1: el borde verde marca la elección
                .glass(selected ? Palette.okSoft : Palette.chip, tint: selected ? Palette.okSoft : nil, in: Capsule())
                .overlay(Capsule().stroke(selected ? Palette.ok : .clear, lineWidth: 1.5))
        }.buttonStyle(.plain)
    }
}

/// De dónde sale un dato («Garmin Forerunner 965»): las normas de marca de Garmin lo piden bajo el título, siempre a la vista.
struct Source: View {
    let text: String
    var body: some View { Text(text).font(.caption12).foregroundStyle(Palette.muted) }
}
