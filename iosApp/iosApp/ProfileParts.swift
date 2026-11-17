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

    var body: some View {
        let shown = live ?? days
        let count = shown.values.filter { $0 > 0 }.count
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .lastTextBaseline) {
                Secondary(count == 1 ? "1 día" : "\(count) días")
                Spacer()
                Text(TextsKt.weekTimeText(days: shown.mapValues { KotlinInt(int: Int32($0)) }))
                    .font(.mono(17, .semibold)).foregroundStyle(Palette.ok)
            }
            ForEach(weekDays, id: \.self) { d in
                let m = shown[d] ?? 0
                let name = TextsKt.WEEKDAY_LABEL[d] ?? d
                HStack(spacing: 8) {
                    Text(name).font(.ui(14, .bold)).foregroundStyle(m == 0 ? Palette.muted : Palette.text).frame(width: 84, alignment: .leading)
                    WeekSlider(minutes: m, top: top, set: { set(d, $0) }, commit: commit)
                        .accessibilityLabel(name)
                        .accessibilityValue(m == 0 ? "libre" : "\(m) minutos")
                    TextField("—", text: Binding(get: { m == 0 ? "" : "\(m)" }, set: { t in
                        set(d, Int(String(t.filter(\.isNumber).prefix(3))) ?? 0)
                        commit()
                    }))
                    .keyboardType(.numberPad).multilineTextAlignment(.trailing).font(.mono(16, .semibold))
                    .padding(.horizontal, 8).frame(width: 64, height: 44)
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Palette.muted, lineWidth: 1))
                    .accessibilityLabel("Minutos el \(name.lowercased())")
                    Secondary("min")
                }
                .sensoryFeedback(.selection, trigger: m)  // un toque suave en cada paso de 15 minutos
            }
            Secondary("Desliza o escribe los minutos de cada día, hasta 5 h. Sin minutos, ese día queda libre.")
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

/// Deslizador de la muestra A (2026-11-17), igual que en Android y la web: pista fina con el tramo lleno en verde y un punto
/// pequeño con aro. El `Slider` de SwiftUI no deja cambiar la pista. Pasos de 15 min; VoiceOver lo ajusta de 15 en 15.
private struct WeekSlider: View {
    let minutes: Int
    let top: Int
    let set: (Int) -> Void
    let commit: () -> Void

    private func move(_ x: CGFloat, _ usable: CGFloat) {
        let raw = Double(min(max(0, x - 10), usable) / usable) * Double(top)
        set(Int((raw / 15).rounded()) * 15)
    }

    var body: some View {
        GeometryReader { geo in
            let usable = max(1, geo.size.width - 20)
            let x = usable * CGFloat(minutes) / CGFloat(top)
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.chip).frame(height: 6)
                Capsule().fill(Palette.ok).frame(width: x + 10, height: 6)
                Circle().fill(Palette.surface).overlay(Circle().stroke(Palette.ok, lineWidth: 3))
                    .frame(width: 20, height: 20).offset(x: x)
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            // Tocar salta al punto; arrastrar desde 6 pt, para que desplazar la pantalla no mueva el día por error
            .onTapGesture(coordinateSpace: .local) { p in move(p.x, usable); commit() }
            .gesture(DragGesture(minimumDistance: 6)
                .onChanged { g in move(g.location.x, usable) }
                .onEnded { _ in commit() })
        }
        .frame(height: 44)
        .accessibilityElement()
        .accessibilityAdjustableAction { dir in
            switch dir {
            case .increment: set(min(top, minutes + 15)); commit()
            case .decrement: set(max(0, minutes - 15)); commit()
            @unknown default: break
            }
        }
    }
}
