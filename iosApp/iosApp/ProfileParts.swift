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

/// La semana con − y + de 15 en 15 minutos (decidido el 2026-11-23: solo botones, sin deslizador); la cifra también se
/// escribe. Sin minutos, ese día es de descanso. `onCommit` llega con cada cambio.
struct WeekBars: View {
    let days: [String: Int]
    var onCommit: ([String: Int]) -> Void = { _ in }
    @State private var live: [String: Int]?
    private let top = Int(TextsKt.MAX_DAY_MINUTES)
    private let order = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
    @ScaledMetric(relativeTo: .body) private var fieldWidth: CGFloat = 56

    private func put(_ day: String, _ minutes: Int) {
        var next = live ?? days
        next[day] = min(top, max(0, minutes))
        live = next
        onCommit(next)
    }

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
            VStack(spacing: 0) {
                ForEach(Array(order.enumerated()), id: \.offset) { i, d in
                    let m = shown[d] ?? 0
                    let name = TextsKt.WEEKDAY_LABEL[d] ?? d
                    if i > 0 { Divider() }
                    HStack(spacing: 8) {
                        Text(name).font(.ui(15, m > 0 ? .bold : .regular)).foregroundStyle(m == 0 ? Palette.muted : Palette.text)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        step("−", "Menos minutos el \(name.lowercased())", enabled: m > 0) { put(d, m - 15) }
                        TextField("—", text: Binding(get: { m == 0 ? "" : "\(m)" }, set: { put(d, Int($0.filter(\.isNumber).prefix(3)) ?? 0) }))
                            .keyboardType(.numberPad).multilineTextAlignment(.center).font(.mono(17, .semibold))
                            .frame(width: fieldWidth).accessibilityLabel("Minutos el \(name.lowercased())")
                        step("+", "Más minutos el \(name.lowercased())", enabled: m < top) { put(d, m + 15) }
                    }.padding(.horizontal, 14).frame(minHeight: 56)
                }
            }
            .background(Palette.surface).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .onChange(of: days) { _, _ in live = nil }
    }

    private func step(_ sign: String, _ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(sign).font(.ui(20, .bold)).frame(width: 40, height: 40)
                .foregroundStyle(enabled ? Palette.text : Palette.muted)
                .background(Palette.chip.opacity(enabled ? 1 : 0.4)).clipShape(Circle())
        }.buttonStyle(.plain).disabled(!enabled).accessibilityLabel(label)
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
