import SwiftUI
import Shared

// Hoy a gusto de cada uno (decidido el 2026-11-23), como Widgets.tsx y TodayWidgets.kt: debajo de la sesión, widgets
// medios (dos por fila) o anchos. Mantener pulsado (menú contextual del sistema) o «⋯» abren el menú: Medio, Ancho,
// Ordenar los widgets y Quitar de Hoy (opción B). «Añadir a Hoy» enseña los dos tamaños antes de ponerlo (opción C).
// El orden es de este iPhone.

struct TodayWidgets: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dynamicTypeSize) private var typeSize
    let screen: TodayScreen
    @State private var reordering = false
    @State private var adding = false

    /// Filas: un ancho solo; dos medios seguidos, juntos. Con texto de accesibilidad, cada uno a lo ancho.
    private func rows(_ widgets: [WidgetView]) -> [[WidgetView]] {
        var rows: [[WidgetView]] = []
        var i = 0
        while i < widgets.count {
            let w = widgets[i]
            if !w.wide && !typeSize.isAccessibilitySize, i + 1 < widgets.count, !widgets[i + 1].wide {
                rows.append([w, widgets[i + 1]]); i += 2
            } else {
                rows.append([w]); i += 1
            }
        }
        return rows
    }

    var body: some View {
        let _ = model.version
        let widgets = model.core.todayWidgets()
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Spacer()
                if reordering { Button("Listo") { reordering = false }.buttonStyle(TextActionStyle()) }
                else { Button("Añadir a Hoy") { adding = true }.buttonStyle(TextActionStyle()) }
            }
            if widgets.isEmpty { Secondary("Sin widgets. Con «Añadir a Hoy» pones los que quieras.") }
            ForEach(Array(rows(widgets).enumerated()), id: \.offset) { _, row in
                HStack(alignment: .top, spacing: 12) {
                    ForEach(row, id: \.key) { w in
                        WidgetCard(widget: w, index: widgets.firstIndex { $0.key == w.key } ?? 0, count: widgets.count,
                                   screen: screen, reordering: $reordering)
                    }
                    // Un medio solo ocupa media fila, como en Android y la web
                    if row.count == 1, !row[0].wide, !typeSize.isAccessibilitySize { Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                }
            }
        }
        .sheet(isPresented: $adding) { AddWidgetSheet() }
    }
}

private struct WidgetCard: View {
    @EnvironmentObject var model: AppModel
    let widget: WidgetView
    let index: Int
    let count: Int
    let screen: TodayScreen
    @Binding var reordering: Bool

    @ViewBuilder private var menuItems: some View {
        Button { model.run { model.core.setWidgetWide(key: widget.key, wide: false) } } label: {
            Label(widget.wide ? "Medio" : "Medio ✓", systemImage: "square")
        }
        Button { model.run { model.core.setWidgetWide(key: widget.key, wide: true) } } label: {
            Label(widget.wide ? "Ancho ✓" : "Ancho", systemImage: "rectangle")
        }
        Button { reordering = true } label: { Label("Ordenar los widgets", systemImage: "arrow.up.arrow.down") }
        Button(role: .destructive) { model.run("\(widget.title): quitado de Hoy") { model.core.removeWidget(key: widget.key) } } label: {
            Label("Quitar de Hoy", systemImage: "minus.circle")
        }
    }

    var body: some View {
        Module {
            HStack {
                Text(widget.title).font(.section).frame(maxWidth: .infinity, alignment: .leading)
                if reordering {
                    Button { model.run { model.core.moveWidget(key: widget.key, to: Int32(index - 1)) } } label: { Text("↑").font(.ui(18, .bold)) }
                        .disabled(index == 0).accessibilityLabel("Subir \(widget.title)")
                    Button { model.run { model.core.moveWidget(key: widget.key, to: Int32(index + 1)) } } label: { Text("↓").font(.ui(18, .bold)) }
                        .disabled(index == count - 1).accessibilityLabel("Bajar \(widget.title)")
                } else {
                    Menu { menuItems } label: {
                        Text("⋯").font(.ui(18, .bold)).foregroundStyle(Palette.muted).frame(minWidth: 40, minHeight: 40)
                    }.accessibilityLabel("Opciones de \(widget.title)")
                }
            }
            switch widget.key {
            case "readiness": ReadinessWidget(ask: screen.askReadiness)
            case "week": WeekWidget(screen: screen, wide: widget.wide)
            default: ValueWidget(widget: widget)
            }
        }
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(reordering ? Palette.muted : .clear, style: StrokeStyle(lineWidth: 2, dash: [6, 4])))
        .contentShape(.contextMenuPreview, RoundedRectangle(cornerRadius: 24, style: .continuous))
        .contextMenu { if !reordering { menuItems } }
        .frame(maxWidth: .infinity)
    }
}

private struct ValueWidget: View {
    let widget: WidgetView
    var body: some View {
        if let value = widget.value {
            (Text(value).font(.mono(24, .semibold, relativeTo: .title2))
                + Text(widget.unit.isEmpty ? "" : " \(widget.unit)").font(.mono(13, relativeTo: .footnote)).foregroundColor(Palette.muted))
            if let detail = widget.detail { Secondary(detail) }
        } else {
            Secondary(widget.empty ?? "")
        }
    }
}

private struct WeekWidget: View {
    let screen: TodayScreen
    let wide: Bool
    var body: some View {
        WeekDots(days: screen.weekDots)
        // Sin plan, la fuerza hecha frente a los 2 días; con plan, los minutos de cardio y la fuerza hecha
        if screen.noPlan {
            Secondary("\(screen.strengthDone) de 2 días de fuerza")
            if screen.strengthDone >= 2 {
                Text("Cumples los 2 días de fuerza").font(.ui(14, .semibold, relativeTo: .subheadline)).foregroundStyle(Palette.ok)
            }
        } else {
            Secondary("Cardio \(screen.weekDone) de \(screen.weekTarget) min")
            Secondary(screen.strengthLine)
        }
        if wide {
            ForEach(screen.warnings, id: \.self) { ReasonRow(reason: Reason(text: $0, evidence: nil)) }
            ForEach(screen.info, id: \.self) { Secondary($0) }
        } else if !screen.warnings.isEmpty {
            Secondary(screen.warnings.count == 1 ? "1 aviso: ábrelo en ancho" : "\(screen.warnings.count) avisos: ábrelo en ancho", color: Palette.caution)
        }
    }
}

/// Sueño, energía y agujetas de 1 a 5: sin reloj, ajustan la sesión de hoy. Se abren al tocar «Responder».
private struct ReadinessWidget: View {
    @EnvironmentObject var model: AppModel
    let ask: Bool
    @State private var open = false
    @State private var sleep = 3
    @State private var energy = 3
    @State private var soreness = 3
    private let ratings = (1...5).map { n in ("\(n)", n == 1 ? "1 mal" : n == 5 ? "5 bien" : "\(n)") }

    var body: some View {
        if !ask {
            Secondary("Hoy ya está ajustado con tus respuestas o con los datos de tu reloj.")
        } else if !open {
            Button { open = true } label: { Text("Responder 3 preguntas").frame(maxWidth: .infinity) }.buttonStyle(SecondaryStyle())
        } else {
            Secondary("Si dormiste mal o estás cargado, la sesión de hoy será más suave.")
            ForEach([("Sueño", $sleep), ("Energía", $energy), ("Agujetas o molestias", $soreness)], id: \.0) { label, value in
                Text(label).font(.small)
                Segmented(options: ratings, selected: "\(value.wrappedValue)") { value.wrappedValue = Int($0) ?? 3 }
            }
            Button("Ajustar hoy") {
                model.run { try model.core.answerReadiness(sleep: Int32(sleep), energy: Int32(energy), soreness: Int32(soreness)) }
            }.buttonStyle(PrimaryStyle())
        }
    }
}

/// Añadir a Hoy: la lista de los que faltan y, al tocar uno, sus dos tamaños para elegir.
private struct AddWidgetSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var picked: WidgetChoice? = nil

    var body: some View {
        let choices = model.core.widgetChoices().filter { !$0.inUse }
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let p = picked {
                        Secondary("\(p.description_). Elige el tamaño:")
                        HStack(alignment: .top, spacing: 10) {
                            ForEach([(false, "Medio · dos por fila"), (true, "Ancho · toda la fila")], id: \.0) { wide, label in
                                Button {
                                    dismiss()
                                    model.run("\(p.title): añadido a Hoy") { try model.core.addWidget(key: p.key, wide: wide) }
                                } label: {
                                    VStack(alignment: .leading) {
                                        Text(p.title).font(.ui(15, .bold))
                                        Spacer(minLength: 16)
                                        Secondary(label)
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading).padding(12)
                                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.chip, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                .frame(maxWidth: wide ? .infinity : 140)
                            }
                        }
                        Button("Elegir otro") { picked = nil }.buttonStyle(TextActionStyle())
                    } else if choices.isEmpty {
                        Secondary("Ya tienes todos en Hoy.")
                    } else {
                        ForEach(choices, id: \.key) { c in
                            Button { picked = c } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.title).font(.ui(16, .semibold))
                                        Secondary(c.description_)
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                    RowChevron()
                                }.padding(.vertical, 6).contentShape(Rectangle())
                            }.buttonStyle(.plain)
                        }
                    }
                }.padding(20)
            }
            .background(Palette.surface)
            .navigationTitle(picked?.title ?? "Añadir a Hoy").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cerrar") { dismiss() } } }
        }
    }
}
