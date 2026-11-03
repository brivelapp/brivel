import SwiftUI
import Shared

// Piezas del perfil (lienzo de Pulso, opción B, 2026-11-03), compartidas por el Perfil y la bienvenida: deportes sin
// «Caminar» (siempre cuenta), la semana como barras que se suben y bajan con el dedo, fuerza, nivel y año.

private let sportNames = ["running": "Correr", "cycling": "Bici", "swimming_pool": "Nadar"]
private let weekDays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
let strengthChoices = [("gym", "Gimnasio", "Con máquinas, barras y mancuernas"),
                       ("bodyweight", "En casa", "Con tu peso y poco material"),
                       ("none", "Sin fuerza", "Solo resistencia")]
let levelChoices = [("beginner", "Empiezo", "Hace meses que no entreno"),
                    ("intermediate", "A veces", "Una o dos veces por semana"),
                    ("advanced", "A menudo", "Tres o más veces por semana")]

/// Correr, bici y nadar como pastillas con icono. Caminar no aparece: siempre cuenta.
struct SportPicker: View {
    let cardio: [String]
    let onChange: ([String]) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 8, alignment: .leading)], alignment: .leading, spacing: 8) {
                ForEach(TextsKt.PICKABLE_CARDIO, id: \.self) { sport in
                    let on = cardio.contains(sport)
                    Button { onChange(TextsKt.toggleCardio(cardio: cardio, sport: sport)) } label: {
                        HStack(spacing: 8) {
                            Glyph(name: sportIcon(sport), size: 20, color: on ? Palette.onOk : Palette.text)
                            Text(sportNames[sport] ?? sport).font(.ui(15, on ? .bold : .semibold))
                        }
                        .padding(.horizontal, 16).frame(minHeight: 48)
                        .background(on ? Palette.ok : Palette.chip).foregroundStyle(on ? Palette.onOk : Palette.text)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            Secondary("Caminar siempre cuenta: no hace falta marcarlo.")
        }
    }
}

/// La semana como barras: tocar o arrastrar a una altura pone esos minutos, de 15 en 15; abajo del todo, ese día queda
/// libre. Para arrastrar hay que mantener pulsado (si no, deslizar la pantalla movería las barras). `onCommit` llega al
/// soltar. Con VoiceOver cada barra se ajusta deslizando arriba o abajo.
struct WeekBars: View {
    let days: [String: Int]
    var editable = true  // en el Perfil, solo se ve: se cambia en una hoja (si no, desplazar la pantalla movería las barras)
    var onCommit: ([String: Int]) -> Void = { _ in }
    @State private var live: [String: Int]?
    private let top = Int(TextsKt.MAX_DAY_MINUTES)

    var body: some View {
        let shown = live ?? days
        let count = shown.values.filter { $0 > 0 }.count
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .lastTextBaseline) {
                Secondary(count == 1 ? "1 día" : "\(count) días")
                Spacer()
                Text(TextsKt.weekTimeText(days: shown.mapValues { KotlinInt(int: Int32($0)) }))
                    .font(.mono(17, .semibold)).foregroundStyle(Palette.ok)
            }
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(weekDays, id: \.self) { d in
                    let m = shown[d] ?? 0
                    VStack(spacing: 6) {
                        GeometryReader { geo in
                            ZStack(alignment: .bottom) {
                                Color.clear
                                // Los minutos, justo encima de la barra
                                VStack(spacing: 4) {
                                    Text(m == 0 ? "—" : "\(m)").font(.mono(12, .semibold)).foregroundStyle(m == 0 ? Palette.muted : Palette.text)
                                    if m == 0 {
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .strokeBorder(Palette.chip, style: StrokeStyle(lineWidth: 2, dash: [4, 3])).frame(height: 12)
                                    } else {
                                        RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Palette.ok)
                                            .frame(height: (geo.size.height - 22) * CGFloat(m) / CGFloat(top))
                                    }
                                }
                                // Toda la columna responde al dedo
                                if editable {
                                    Color.clear.contentShape(Rectangle())
                                        .highPriorityGesture(DragGesture(minimumDistance: 0, coordinateSpace: .named(d))
                                            .onChanged { set(d, minutes(at: $0.location.y, height: geo.size.height)) }
                                            .onEnded { _ in commit() })
                                }
                            }
                            .coordinateSpace(name: d)
                        }
                        .frame(height: 262)  // 240 de barra (5 h: cada paso de 15 son 12 pt) y 22 de minutos
                        .sensoryFeedback(.selection, trigger: m)  // un toque suave en cada paso de 15 minutos
                        .accessibilityElement()
                        .accessibilityLabel(TextsKt.WEEKDAY_LABEL[d] ?? d)
                        .accessibilityValue(m == 0 ? "libre" : "\(m) minutos")
                        .accessibilityAdjustableAction { direction in
                            guard editable else { return }
                            set(d, min(top, max(0, m + (direction == .increment ? 15 : -15))))
                            commit()
                        }
                        Text(TextsKt.WEEKDAY_SHORT[d] ?? d).font(.ui(13, .bold)).foregroundStyle(m == 0 ? Palette.muted : Palette.text)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            if editable { Secondary("Toca o arrastra cada barra hasta los minutos que tienes, hasta 5 h. Abajo del todo, ese día queda libre.") }
        }
    }

    // La barra mide el alto menos los 22 pt de los minutos: el dedo marca su tope
    private func minutes(at y: CGFloat, height: CGFloat) -> Int {
        min(top, max(0, Int(((height - y) / (height - 22) * CGFloat(top) / 15).rounded()) * 15))
    }

    private func set(_ day: String, _ minutes: Int) {
        var next = live ?? days
        next[day] = minutes
        live = next
    }

    private func commit() {
        if let live { onCommit(live) }
        live = nil
    }
}

/// Opciones cortas en una sola fila (fuerza y nivel en el Perfil).
struct Segmented: View {
    let options: [(String, String)]
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.0) { key, label in
                let on = key == selected
                Button { onSelect(key) } label: {
                    Text(label).font(.ui(14, on ? .bold : .semibold)).frame(maxWidth: .infinity, minHeight: 48)
                        .background(on ? Palette.ok : Color.clear).foregroundStyle(on ? Palette.onOk : Palette.text)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(4).background(Palette.chip).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Opciones con explicación, una por tarjeta (fuerza y nivel en la bienvenida).
struct ChoiceCards: View {
    let options: [(String, String, String)]
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        VStack(spacing: 10) {
            ForEach(options, id: \.0) { key, title, detail in
                let on = key == selected
                Button { onSelect(key) } label: {
                    Module(accent: on ? Palette.ok : nil, background: on ? Palette.okSoft : Palette.surface) {
                        Text(title).font(.section).foregroundStyle(Palette.text)
                        Secondary(detail)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
    }
}

func ageOk(_ year: Int?) -> Bool {
    guard let year else { return false }
    return TextsKt.ageAllowed(birthYear: Int32(year), currentYear: Int32(Calendar.current.component(.year, from: Date())))
}
