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
            Flow(spacing: 8) {
                ForEach(TextsKt.PICKABLE_CARDIO, id: \.self) { sport in
                    let on = cardio.contains(sport)
                    Button { onChange(TextsKt.toggleCardio(cardio: cardio, sport: sport)) } label: {
                        HStack(spacing: 8) {
                            Glyph(name: sportIcon(sport), size: 20, color: on ? Palette.onOk : Palette.text)
                            Text(sportNames[sport] ?? sport).font(.ui(15, on ? .bold : .semibold))
                        }
                        .padding(.horizontal, 16).frame(minHeight: 48)
                        .foregroundStyle(on ? Palette.onOk : Palette.text)
                        .glass(on ? Palette.ok : Palette.chip, tint: on ? Palette.ok : nil, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }
}

/// La semana, un día por fila: deslizar de 15 en 15 o escribir los minutos (de 0 a 5 h; sin minutos, ese día queda
/// libre). En horizontal, desplazar la pantalla no mueve nada, y escribir la cifra evita afinar con el dedo (con las
/// barras verticales costaba acertar en 45). `onCommit` llega al soltar el deslizador o con cada cifra escrita.
struct WeekBars: View {
    let days: [String: Int]
    var onCommit: ([String: Int]) -> Void = { _ in }
    @State private var live: [String: Int]?
    private let top = Int(TextsKt.MAX_DAY_MINUTES)
    // Crecen con el texto del sistema: con 84 puntos fijos, «Miércoles» se partía con el texto grande
    @ScaledMetric(relativeTo: .body) private var nameWidth: CGFloat = 84
    @ScaledMetric(relativeTo: .body) private var fieldWidth: CGFloat = 64
    @ScaledMetric(relativeTo: .body) private var fieldHeight: CGFloat = 44

    var body: some View {
        let shown = live ?? days
        let count = shown.values.filter { $0 > 0 }.count
        VStack(alignment: .leading, spacing: 8) {
            TitleRow {
                Secondary(count == 1 ? "1 día" : "\(count) días")
            } trailing: {
                Text(TextsKt.weekTimeText(days: shown.mapValues { KotlinInt(int: Int32($0)) }))
                    .font(.mono(17, .semibold)).foregroundStyle(Palette.ok)
            }
            // Cada día en una fila si al deslizador le quedan 96 puntos; si no, el nombre va encima (todos a la vez)
            ViewThatFits(in: .horizontal) {
                rows(shown, stacked: false)
                rows(shown, stacked: true)
            }
            Secondary("Desliza o escribe los minutos de cada día, hasta 5 h. Sin minutos, ese día queda libre.")
        }
    }

    private func rows(_ shown: [String: Int], stacked: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(weekDays, id: \.self) { d in
                let m = shown[d] ?? 0
                let name = TextsKt.WEEKDAY_LABEL[d] ?? d
                let label = Text(name).font(.ui(14, .bold)).foregroundStyle(m == 0 ? Palette.muted : Palette.text)
                VStack(alignment: .leading, spacing: 2) {
                    if stacked { label }
                    HStack(spacing: 8) {
                        if !stacked { label.lineLimit(1).frame(width: nameWidth, alignment: .leading) }
                        // El del sistema: en iOS 26 lleva el cristal, como la barra inferior
                        Slider(value: Binding(get: { Double(m) }, set: { set(d, Int(($0 / 15).rounded()) * 15) }),
                               in: 0...Double(top), step: 15, onEditingChanged: { editing in if !editing { commit() } })
                            .tint(Palette.ok)
                            .frame(minWidth: 96, idealWidth: 96, maxWidth: .infinity)
                            .accessibilityLabel(name)
                            .accessibilityValue(m == 0 ? "libre" : "\(m) minutos")
                        TextField("—", text: Binding(get: { m == 0 ? "" : "\(m)" }, set: { t in
                            set(d, Int(String(t.filter(\.isNumber).prefix(3))) ?? 0)
                            commit()
                        }))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing).font(.mono(16, .semibold))
                        .padding(.horizontal, 8).frame(width: min(fieldWidth, 112), height: fieldHeight)
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Palette.muted, lineWidth: 1))
                        .accessibilityLabel("Minutos el \(name.lowercased())")
                        Secondary("min").fixedSize()
                    }
                }
                .sensoryFeedback(.selection, trigger: m)  // un toque suave en cada paso de 15 minutos
            }
        }
    }

    private func set(_ day: String, _ minutes: Int) {
        var next = live ?? days
        next[day] = min(top, max(0, minutes))
        live = next
    }

    private func commit() {
        if let live { onCommit(live) }
        live = nil
    }
}

/// Opciones cortas en una sola fila (fuerza y nivel en el Perfil). Si con el texto grande del sistema no caben enteras,
/// van una debajo de otra.
struct Segmented: View {
    let options: [(String, String)]
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 4) { buttons(oneLine: true) }
                .padding(4).glass(Palette.chip, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(spacing: 4) { buttons(oneLine: false) }
                .padding(4).glass(Palette.chip, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private func buttons(oneLine: Bool) -> some View {
        ForEach(options, id: \.0) { key, label in
            let on = key == selected
            Button { onSelect(key) } label: {
                Text(label).font(.ui(14, on ? .bold : .semibold)).fixedSize(horizontal: oneLine, vertical: false)
                    .padding(.horizontal, 8).frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundStyle(on ? Palette.onOk : Palette.text)
                    .glass(on ? Palette.ok : Color.clear, tint: Palette.ok, in: RoundedRectangle(cornerRadius: 12, style: .continuous), enabled: on)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(on ? .isSelected : [])
        }
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
