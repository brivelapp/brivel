package app.brivel.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.BrivelApp
import app.brivel.app.FigureView
import app.brivel.app.Mark
import app.brivel.app.polylines

// Figuras G (FigureG.kt del núcleo): el núcleo decide las marcas; aquí solo se pintan con el color de su papel

@Composable
private fun roleColors(): Map<String, Color> {
    val dark = Palette.bg.luminance() < 0.5f
    fun c(light: Long, night: Long) = Color(if (dark) night else light)
    return mapOf("floor" to c(0xFFC9C2B3, 0xFF34423B), "equip" to c(0xFFC9C2B3, 0xFF34423B), "far" to c(0xFF8C968F, 0xFF7E8B84),
        "okFar" to c(0xFF5E9C84, 0xFF3C8A6B), "near" to Palette.text, "ok" to Palette.ok, "plate" to c(0xFF7D766A, 0xFF5C6B63),
        "hub" to Palette.surface, "sil" to c(0xFFE4DED2, 0xFF2A3630))
}

/** Las marcas sobre una rejilla de `gridW` × `gridH` (48 × 48 el muñeco; 40 × 80 el mapa). */
@Composable
fun FigureMarks(marks: List<Mark>, width: Dp, gridW: Float = 48f, gridH: Float = 48f) {
    val colors = roleColors()
    Canvas(Modifier.size(width, width * (gridH / gridW))) {
        val k = size.width / gridW
        for (m in marks) {
            val color = colors[m.role] ?: continue
            if (m.d.isEmpty()) { drawCircle(color, m.r * k, Offset(m.x * k, m.y * k)); continue }
            polylines(m.d).forEach { line ->
                val path = Path().apply {
                    moveTo(line[0].first * k, line[0].second * k)
                    line.drop(1).forEach { (x, y) -> lineTo(x * k, y * k) }
                    if (m.fill) close()
                }
                if (m.fill) drawPath(path, color)
                else drawPath(path, color, style = Stroke(m.w * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Icono de un ejercicio: su postura final. */
@Composable
fun ExerciseFigure(app: BrivelApp, name: String, size: Dp) {
    val figure = remember(name) { app.exerciseFigure(name) } ?: return
    FigureMarks(figure.end, size)
}

/** La figura entera: inicio → final y el mapa de lo que trabaja, delante y detrás. */
@Composable
fun ExerciseFigureFull(app: BrivelApp, name: String) {
    val f = remember(name) { app.exerciseFigure(name) } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { FigureMarks(f.start, 80.dp); Text(f.startLabel, fontSize = 12.sp, color = Palette.muted) }
        Text("→", color = Palette.muted)
        Column(horizontalAlignment = Alignment.CenterHorizontally) { FigureMarks(f.end, 80.dp); Text(f.endLabel, fontSize = 12.sp, color = Palette.muted) }
        FigureMarks(f.front, 26.dp, 40f, 80f)
        FigureMarks(f.back, 26.dp, 40f, 80f)
    }
}
