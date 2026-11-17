package app.brivel.android

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brivel.app.HEALTH_NOTICE
import app.brivel.app.PRIVACY_NOTE
import app.brivel.app.PRIVACY_URL
import app.brivel.app.SOURCE_URL
import app.brivel.app.TERMS_URL
import androidx.compose.ui.platform.LocalUriHandler
import app.brivel.app.BrivelApp
import app.brivel.app.UserProfile
import app.brivel.app.MIN_AGE
import app.brivel.app.ageAllowed
import app.brivel.app.systemToday
import app.brivel.app.goalLabel
import app.brivel.app.GoalKind
import app.brivel.app.RaceInfo
import app.brivel.app.dateLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(app: BrivelApp, version: Int, act: Act, settings: Boolean, openSettings: (Boolean) -> Unit) {
    val context = LocalContext.current
    var draft by remember(version) { mutableStateOf(app.data.profile ?: UserProfile()) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Dentro de Ajustes, cada fila abre su página: health, intervals, garmin, sync, notice, about
    var page by rememberSaveable(settings) { mutableStateOf<String?>(null) }
    BackHandler(settings) { if (page != null) page = null else openSettings(false) }
    // Se guarda solo: cada cambio rehace el plan al momento (lienzo de Pulso, opción B)
    fun save(p: UserProfile) {
        draft = p
        act.run { app.saveProfile(p) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!settings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { openSettings(true) }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Palette.chip)
                    .semantics { contentDescription = "Ajustes" }) { Glyph(R.drawable.ic_settings, 22.dp) }
            }
            Module {
                SectionTitle("Tus deportes")
                SportPicker(draft.cardio) { save(draft.copy(cardio = it)) }
            }
            Module {
                SectionTitle("Tu semana")
                WeekBars(draft.days) { save(draft.copy(days = it)) }
            }
            Module {
                SectionTitle("Fuerza")
                Segmented(STRENGTH_CHOICES.map { it.first to it.second }, draft.strength) { save(draft.copy(strength = it)) }
            }
            Module {
                SectionTitle("Tu nivel")
                Segmented(LEVEL_CHOICES.map { it.first to it.second }, draft.level) { save(draft.copy(level = it)) }
            }
            Module {
                var year by remember(version) { mutableStateOf(draft.birthYear) }
                SectionTitle("Año de nacimiento")
                BirthYearField(year, label = null) { y ->
                    year = y
                    if (y != null && ageAllowed(y, systemToday().year)) save(draft.copy(birthYear = y))
                }
                if (year != null && year!! >= 1000 && !ageAllowed(year!!, systemToday().year)) Secondary("Hace falta tener al menos $MIN_AGE años.", Palette.caution)
            }
            GearCard(app, act, version)
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        val title = when (page) {
            "health" -> "Health Connect"; "intervals" -> "intervals.icu"; "garmin" -> "Garmin Connect™"
            "sync" -> "Otros dispositivos"; "notice" -> "Aviso de salud"; "about" -> "Sobre Brivel"; else -> "Ajustes"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page != null) page = null else openSettings(false) },
                modifier = Modifier.semantics { contentDescription = if (page != null) "Volver a Ajustes" else "Volver a Perfil" }) {
                Glyph(R.drawable.ic_chevron_left, 24.dp)
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        when (page) {
            "health" -> HealthCard(app, act, version)
            "intervals" -> {
                IntervalsCard(app, act, version)
                Module {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionTitle("Renombrar mis actividades")
                            Secondary("Les pone un título que dice lo que fueron.")
                        }
                        Switch(checked = draft.renameActivities, onCheckedChange = { save(draft.copy(renameActivities = it)) })
                    }
                }
            }
            "garmin" -> GarminCard(app, act, version)
            "sync" -> SyncCard(app, act, version)
            "notice" -> Module { Secondary(HEALTH_NOTICE) }
            "about" -> Module {
                Text("Gratis y de código abierto (AGPL-3.0). Sin pagos, sin anuncios.", fontSize = 15.sp)
                Secondary(PRIVACY_NOTE)
                val links = LocalUriHandler.current
                TextButton(onClick = { links.openUri(PRIVACY_URL) }) { LinkText("Política de privacidad") }
                TextButton(onClick = { links.openUri(TERMS_URL) }) { LinkText("Términos") }
                TextButton(onClick = { links.openUri(SOURCE_URL) }) { LinkText("Código fuente") }
            }
            else -> {
                val health = app.data.health != null
                SettingsGroup("Conexiones") {
                    SettingsRow("Health Connect", if (health) "Conectado" else "Sin conectar", health) { page = "health" }
                    SettingsRow("Garmin Connect™", if (app.garminConnected) "Conectado" else "Sin conectar", app.garminConnected) { page = "garmin" }
                    val linked = app.linkedAthlete != null
                    SettingsRow("intervals.icu", if (linked) "Conectado" else "Sin conectar", linked) { page = "intervals" }
                }
                SettingsGroup("Avisos") { ReminderCard(app, act, version) }
                SettingsGroup("Tus datos") {
                    val synced = app.syncCode != null && !app.syncNeedsConsent
                    SettingsRow("Otros dispositivos",
                        if (app.syncNeedsConsent) "En pausa" else if (app.syncCode != null) "Sincronizado" else "Solo aquí", synced) { page = "sync" }
                    SettingsRow("Exportar mis datos") {
                        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, app.exportJson())
                        context.startActivity(Intent.createChooser(send, "Exportar mis datos"))
                    }
                    SettingsRow("Borrar todo", color = Palette.danger) { confirmDelete = true }
                }
                SettingsGroup("Sobre Brivel") {
                    SettingsRow("Aviso de salud") { page = "notice" }
                    SettingsRow("Privacidad, términos y código") { page = "about" }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se borran tu perfil, tu objetivo, tu historial y la clave de intervals.icu de este móvil. No se puede deshacer. Tus datos en intervals.icu no se tocan.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; act.run { app.deleteAll() } }) { Text("Borrar", color = Palette.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    }
}

/** Carreras preparatorias (B o C) antes de la principal: el plan afina unos días antes y recupera después. */
@Composable
internal fun OtherRaces(app: BrivelApp, act: Act) {
    val goal = app.data.goal ?: return
    val main = goal.race?.takeIf { goal.kind == GoalKind.RACE } ?: return
    var adding by remember { mutableStateOf(false) }
    var extra by remember { mutableStateOf(RaceInfo("", main.sport, "", "C")) }
    SectionTitle("Otras carreras antes de ${main.name}")
    goal.otherRaces.forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.Medium)
                Secondary("${dateLabel(r.date)} · ${if (r.priority == "B") "importante" else "de preparación"}")
            }
            TextButton(onClick = { act.run { app.removeRace(r.date) } }) { LinkText("Quitar") }
        }
    }
    if (adding) {
        RaceForm(extra) { extra = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act.run("Carrera añadida") { app.addRace(extra); adding = false; extra = RaceInfo("", main.sport, "", "C") } }) { Text("Añadir") }
            TextButton(onClick = { adding = false }) { LinkText("Cancelar") }
        }
    } else {
        Secondary("El plan afina unos días antes de cada una y te deja recuperar después. Elige importante (B) o de preparación (C).")
        OutlinedButton(onClick = { adding = true }) { Text("Añadir otra carrera") }
    }
}

/** Recordatorio diario opcional: apagado por defecto, a la hora que elijas, solo con lo que toca ese día. */
@Composable
private fun ReminderCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    val context = LocalContext.current
    fun set(hour: Int?) = act.run { app.setReminder(hour); scheduleReminder(context, hour) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) set(8) else act.run("Sin permiso para avisarte: actívalo en Ajustes → Notificaciones") {}
    }
    val hour = app.data.reminderHour
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {  // fila dentro del grupo «Avisos»
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recordatorio diario")
            Spacer(Modifier.weight(1f))
            Switch(checked = hour != null, onCheckedChange = { on ->
                when {
                    !on -> set(null)
                    Build.VERSION.SDK_INT >= 33 && !canNotify(context) -> request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> set(8)
                }
            })
        }
        if (hour != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A las", fontSize = 14.sp)
            TextButton(onClick = { set((hour + 23) % 24) }) { Text("−") }
            Text("${hour.toString().padStart(2, '0')}:00", fontFamily = Mono)
            TextButton(onClick = { set((hour + 1) % 24) }) { Text("+") }
        }
        if (hour != null) MoreInfo("¿Cuándo avisa?", "Un aviso con la sesión del día. Los días de descanso o con todo hecho no avisa.")
    }
}

/** Health Connect: los datos del reloj y la báscula sin intervals.icu (solo lectura, en el propio móvil). */
@Composable
private fun HealthCard(app: BrivelApp, act: Act, version: Int) {  // version: redibuja tras cada acción
    val context = LocalContext.current
    val reader = remember { HealthConnectReader(context) }
    // Permisos que faltan: los de los datos añadidos después de conectar (oxígeno, respiración, grasa corporal…)
    var missing by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(version) { missing = if (reader.available) reader.permissions - reader.granted() else emptySet() }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isNotEmpty()) act.suspend("Datos de Health Connect leídos") { reader.sync(app) }
    }
    Module {
        val health = app.data.health
        if (health != null) {
            StateLine("Estado", "Conectado", ok = true)
            StateLine("Leído", "${health.days.size} días · ${health.workouts.size} entrenos")
            StateLine("Al día", health.syncedAt)
            MoreInfo("¿Qué lee exactamente?", HEALTH_READS)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { act.suspend("Datos actualizados") { reader.sync(app) } }) { Text("Actualizar ahora") }
                OutlinedButton(onClick = { act.run { app.clearHealth() } }) { Text("Desconectar") }
            }
            if (missing.isNotEmpty()) {
                Secondary("Brivel ya puede leer también las fases del sueño, el oxígeno en sangre, la respiración y la grasa corporal. Necesita tu permiso.")
                OutlinedButton(onClick = { request.launch(missing) }) { Text("Dar permiso a los datos nuevos") }
            }
        } else if (reader.available) {
            Text("Brivel lee tu sueño, pulso y entrenos de Health Connect. Solo en este móvil.", fontSize = 15.sp)
            MoreInfo("¿Qué lee exactamente?", HEALTH_READS)
            Button(onClick = { request.launch(reader.permissions) }) { Text("Conectar Health Connect") }
        } else {
            Secondary("Health Connect no está en este móvil. Viene con Android 14 o posterior; en versiones anteriores " +
                "se instala desde Google Play.")
        }
    }
}

@Composable
private fun IntervalsCard(app: BrivelApp, act: Act, @Suppress("UNUSED_PARAMETER") version: Int) {
    val context = LocalContext.current
    Module {
        val linked = app.linkedAthlete
        if (linked != null) {
            StateLine("Estado", "Conectado", ok = true)
            StateLine("Cuenta", linked)
            app.data.cache?.let { StateLine("Al día", it.syncedAt) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { act.suspend("Datos actualizados") { app.sync() } }) { Text("Actualizar ahora") }
                OutlinedButton(onClick = { act.suspend("intervals.icu desvinculado") { app.disconnectIntervals() } }) { Text("Desconectar") }
            }
        } else {
            Text("Gratis; recoge los datos de cualquier reloj.", fontSize = 15.sp)
            MoreInfo("¿Qué relojes?", "intervals.icu recoge los datos de Garmin, Polar, Coros, Suunto, Wahoo y otros. Inicia sesión allí y da permiso a Brivel.")
            Button(onClick = {
                act.suspend(null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.startIntervals("brivel://intervals"))))
                }
            }) { Text("Conectar con intervals.icu") }
        }
    }
}

private const val HEALTH_READS = "Tu sueño y sus fases, tu VFC, tu pulso en reposo, tu oxígeno en sangre, tu respiración, tu peso, " +
    "tu grasa corporal, tus pasos y tus entrenamientos, de cualquier reloj o app que escriba allí. Solo lectura. " +
    "Los permisos se cambian en Ajustes → Health Connect."

/** Un grupo de Ajustes: título pequeño y una tarjeta con sus filas. */
@Composable
private fun SettingsGroup(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = Palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.surface), content = rows)
}

/** Una fila de Ajustes: punto de estado, nombre y estado en una palabra; se toca entera. */
@Composable
private fun SettingsRow(name: String, state: String? = null, on: Boolean = false, color: Color = Palette.text, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Palette.ok else Palette.chip))
        Text(name, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (state != null) Text(state, color = if (on) Palette.ok else Palette.muted, fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
        Glyph(R.drawable.ic_chevron_right, 18.dp, Palette.muted)
    }
}

/** «Estado · Conectado»: etiqueta a la izquierda, dato a la derecha. */
@Composable
private fun StateLine(label: String, value: String, ok: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (ok) Palette.ok else Palette.text, fontSize = 14.sp,
            fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal, fontFamily = if (ok) null else Mono)
    }
}

/** Lo largo, plegado: una línea basta para la mayoría y quien quiera el detalle lo abre. */
@Composable
private fun MoreInfo(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { LinkText(if (open) "Ocultar" else title, Palette.ok) }
    if (open) Secondary(text)
}
