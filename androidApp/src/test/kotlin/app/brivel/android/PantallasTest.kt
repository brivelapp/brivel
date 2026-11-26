package app.brivel.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import app.brivel.app.BrivelApp
import app.brivel.app.GoalKind
import app.brivel.app.KeyValueStore
import app.brivel.app.UserProfile
import com.github.takahirom.roborazzi.captureRoboImage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

// Verificación de las pantallas de Android: la app de verdad (Root) dibujada en la JVM con Robolectric, sin emulador,
// en cuatro tamaños (móvil pequeño, móvil, móvil con la letra al doble —el peor caso— y tableta). Saca una captura de cada
// pantalla en build/verificacion/<tamaño>/ y falla si un texto se corta, si una palabra se parte por la mitad o si algo
// que se pulsa mide menos de 48 dp y choca con otro. Lo que ya incumplía está en verificacion-pendientes/<tamaño>.txt (trinquete: lo
// nuevo falla, lo arreglado obliga a quitarlo; PENDIENTES=1 la reescribe). Lo pidió Gabriel el 2026-11-18: «la app
// parece pensada para un único tamaño», «el icono de Entrenar está más pequeño que el resto».

private class Memoria : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun read(key: String) = map[key]
    override fun write(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Mapa de pantallas: nombre, archivo y cómo llegar desde Hoy. */
private val PANTALLAS: List<Triple<String, String, PantallasTest.() -> Unit>> = listOf(
    Triple("hoy", "TodayScreen.kt", { }),
    Triple("calendario", "CalendarScreen.kt", { pestana("Calendario") }),
    Triple("entrenar", "Train.kt", { pestana("Entrenar") }),
    Triple("progreso", "ProgressScreen.kt", { pestana("Progreso") }),
    Triple("perfil", "ProfileScreen.kt", { pestana("Perfil") }),
    Triple("ajustes", "ProfileScreen.kt", { pestana("Perfil"); rule.onNodeWithContentDescription("Ajustes").performClick() }),
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PantallasTest {
    @get:Rule val rule = createComposeRule()

    fun pestana(nombre: String) = rule.onNodeWithText(nombre).performClick()

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun movilPequeno() = recorrer("320dp")
    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun movil() = recorrer("360dp")
    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi") fun movilLetraGrande() { RuntimeEnvironment.setFontScale(2f); recorrer("360dp-letra-2") }
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tableta() = recorrer("800dp")

    private fun recorrer(tamano: String) {
        val app = BrivelApp(Memoria(), Memoria(), { LocalDate(2026, 11, 16) }, MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        app.saveProfile(UserProfile(birthYear = 1990, level = "intermediate", strength = "gym", cardio = listOf("running"),
            days = mapOf("MON" to 60, "WED" to 45, "SAT" to 90)))
        app.setGoal(GoalKind.FITNESS)
        var pantalla by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent { BrivelTheme { androidx.compose.runtime.key(pantalla) { Root(app) } } }

        val carpeta = File("build/verificacion/$tamano").apply { deleteRecursively(); mkdirs() }
        val informe = StringBuilder("# Android a $tamano\n\n")
        val vistos = linkedMapOf<String, MutableList<String>>()
        PANTALLAS.forEachIndexed { i, (id, archivo, llegar) ->
            pantalla = i  // Root de nuevo: cada pantalla empieza desde Hoy
            rule.waitForIdle()
            llegar()
            rule.waitForIdle()
            rule.onRoot().captureRoboImage(File(carpeta, "$id.png").path)
            val hallazgos = revisar()
            informe.append("## $id · $archivo\n\n").append(if (hallazgos.isEmpty()) "- sin hallazgos\n\n" else hallazgos.joinToString("\n", postfix = "\n\n") { "- $it" })
            hallazgos.forEach { vistos.getOrPut(it.substringBefore(" (")) { mutableListOf() } += id }
        }
        File(carpeta, "informe.md").writeText(informe.toString())

        val lista = File("verificacion-pendientes/$tamano.txt")
        val actuales = vistos.keys.sorted()
        if (System.getenv("PENDIENTES") != null) { lista.parentFile.mkdirs(); lista.writeText(actuales.joinToString("\n", postfix = "\n")); return }
        val pendientes = if (lista.exists()) lista.readLines().filter { it.isNotBlank() } else emptyList()
        val nuevos = actuales.filter { it !in pendientes }.map { "$it (en ${vistos[it]!!.joinToString()})" }
        val arreglados = pendientes.filter { it !in actuales }
        assertEquals(emptyList(), nuevos, "Nuevo a $tamano (captura en androidApp/build/verificacion/$tamano/): que el texto quepa " +
            "(que baje de fila con FlowRow/TitleRow, no que se corte ni parta palabras) y que lo que se pulsa mida 48 dp")
        assertEquals(emptyList(), arreglados, "Ya arreglado: quítalo de androidApp/verificacion-pendientes/$tamano.txt (o PENDIENTES=1)")
    }

    /** Textos que se cortan o parten palabras y controles de menos de 48 dp en lo que está dibujado ahora. */
    private fun revisar(): List<String> {
        val fuera = mutableListOf<String>()
        val densidad = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val textos = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        for (n in textos) {
            val r = mutableListOf<TextLayoutResult>().also { n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.firstOrNull() ?: continue
            val texto = r.layoutInput.text.text
            // Cortado de verdad: la última línea acaba en «…», queda texto sin dibujar o no cabe de alto. (didOverflowWidth
            // no sirve: en un texto que mide lo que su contenido compara con el ancho máximo y siempre da true)
            val ultima = r.lineCount - 1
            if (r.didOverflowHeight || (ultima >= 0 && (r.isLineEllipsized(ultima) || r.getLineEnd(ultima, visibleEnd = true) < texto.length)))
                fuera += "se corta: «${texto.take(50)}»"
            for (l in 0 until r.lineCount - 1) {
                val fin = r.getLineEnd(l)
                if (fin in 1 until texto.length && texto[fin - 1].isLetter() && texto[fin].isLetter()) fuera += "palabra partida: «${texto.take(50)}»"
            }
        }
        // Compose amplía a 48 dp la zona de toque de lo que mide menos (touchBoundsInRoot), así que un botón de 40 dp
        // se pulsa bien. Solo es pequeño de verdad si esa zona ampliada choca con la de otro control (los días del
        // calendario, pegados): ahí el dedo cae en el vecino. Un control dentro de otro (botón en una tarjeta) no cuenta.
        val clics = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        fun dentro(a: Rect, b: Rect) = a.intersect(b) == a
        for (n in clics) {
            val b = n.boundsInRoot
            val (an, al) = (b.width / densidad) to (b.height / densidad)
            // Días del calendario: 7 columnas no caben a 48 dp en un móvil pequeño; Gabriel aceptó el mínimo de WCAG 2.5.8 (2026-11-26)
            val min = if (n.config.getOrNull(SemanticsProperties.TestTag) == DAY_CELL) 23.5f else 47.5f
            if (an <= 0 || al <= 0 || (an >= min && al >= min)) continue
            val choca = clics.any { o ->
                o.id != n.id && !dentro(b, o.boundsInRoot) && !dentro(o.boundsInRoot, b) && o.touchBoundsInRoot.overlaps(n.touchBoundsInRoot)
            }
            if (choca) fuera += "toque pequeño: ${nombre(n)} (${an.toInt()}×${al.toInt()} dp)"
        }
        return fuera.distinct()
    }

    private fun nombre(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
            ?: n.children.flatMap { c -> c.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList() })
            ?.joinToString(" ")?.take(40)?.ifBlank { null } ?: "sin nombre"
}
