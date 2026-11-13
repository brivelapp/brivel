# Integraciones con relojes, ciclocomputadores y apps (estudio del 2026-11-11)

Qué hace falta para conectar Brivel con cada marca: vía de acceso, si exige aprobación, qué datos da y si deja
**mandar sesiones al reloj**. Etiquetas como en el resto de `docs/`: [Oficial] documentación de la marca;
[Secundaria] foros, prensa o agregadores (Terra, Sahha, Open Wearables…); [Inferencia] deducción nuestra.
Nada de esto está probado con cuentas reales salvo Garmin, intervals.icu, Apple Salud y Health Connect.

## 1. Resumen

Ya conectado: **Garmin** (directo, Starter), **intervals.icu** (OAuth), **Apple Salud** y **Health Connect**.

Idea clave: casi todas las marcas **ya escriben en Apple Salud o Health Connect**, así que sus actividades, sueño y
pulso ya llegan a Brivel sin integración propia. La conexión directa solo compensa para **mandar sesiones al
dispositivo** o para datos que no pasan por Salud/Health Connect (VFC de Garmin, recuperación de Whoop, etc.).

| Marca | Vía | Aprobación | Leer datos | Mandar sesiones | Decisión |
|---|---|---|---|---|---|
| Apple Watch | HealthKit + **WorkoutKit** | No (solo App Store) | Ya (Salud) | **Sí**, `WorkoutScheduler` | **Hacer ya** (código, sin pedir nada) |
| Google / Wear OS / Pixel / Fitbit | Health Connect (+ Google Health API) | Health Connect: no; Google Health API: verificación OAuth de Google | Ya (Health Connect) | `PlannedExerciseSessionRecord` (si la app del reloj lo lee) | **Hacer ya** los planes en Health Connect; Google Health API después |
| COROS | Partner API | **Sí** (revisión) | Sí | **Sí** (sincroniza en los dos sentidos) | **Solicitar ya** |
| Wahoo | Cloud API | **Sí** (sandbox → producción) | Sí | **Sí** (planes / FIT) | **Descartado** (2026-11-13: el acuerdo prohíbe apps que apoyen a competidores de Wahoo, §7, y transferir o cachear datos, §6) |
| Suunto | API Zone (Cloud API + Guides) | **Sí** (formulario, ~2 semanas; producción con suscripción) | Sí (FIT) | **Sí**, «SuuntoPlus Guides» | **Solicitar ya** |
| Polar | AccessLink v3 | No (alta libre) | Sí | **No** (API de solo lectura) | **Descartado** (2026-11-13: el acuerdo prohíbe competir con Polar Ecosystem, §2.2, e indemnización ilimitada, §5.6) |
| Samsung | Samsung Health Data SDK | Para publicar, sí (socio) | Sí (en el móvil) | No encontrado | **Descartado el SDK directo** (2026-11-13: exige registrar la firma de la app, lo que rompe las compilaciones de terceros de un proyecto abierto; SDK propietario; acuerdo de socio no legible). Se cubre con Health Connect |
| Huawei | Health Kit (HMS) | **Sí** (alta y permisos por app) | Sí (con retraso) | No encontrado | **Descartado** (2026-11-13: a un desarrollador individual solo le abre los datos si la app está en AppGallery; acuerdo no legible) |
| Oura | API v2 (OAuth) | Libre hasta 10 usuarios; luego aprobación | Sí (sueño, VFC, preparación) | No aplica | **Descartado** (2026-11-13: prohíbe productos que compitan con Oura, preámbulo y §4(a)(ix); guardar o cachear datos, §3(m); cederlos a terceros, §4(a)(vi); indemnización ilimitada, §11) |
| Whoop | Developer Platform v2 | Libre hasta 10 usuarios; aprobación lenta | Sí (recuperación, sueño, esfuerzo) | No aplica | **Descartado** (2026-11-13: prohíbe competir con WHOOP, §3.1(16); ceder datos a terceros aunque el usuario consienta, §3.1(3); indemnización ilimitada, §8.3) |
| Zwift | Training API («Zwift Link») | **Solo socios**, cerrada | Sí | **Sí** | Solicitar (difícil); mientras, vía intervals.icu |
| Withings (báscula) | Public API | No (alta libre, límites por plan) | Peso, composición, tensión | No aplica | Hacer (lectura), opcional |
| Hammerhead Karoo | Programa de socios + SDK de extensiones | Sí | Sí (vía socios) | Sí (vía socios, p. ej. JOIN) | Contactar |
| Strava | API v3 | Exige suscripción de pago | Solo para el propio usuario | No | **Descartado** (ver §3) |
| Amazfit / Zepp | Sin API pública | — | Ya (Salud / Health Connect) | No | Nada que hacer |
| Xiaomi (Mi Fitness) | Sin API pública | — | Ya (Salud / Health Connect) | No | Nada que hacer |
| Nike Run Club | Sin API pública | — | Ya (Salud; sin GPS según fuente antigua) | No | Nada que hacer |
| Peloton | Sin API pública | — | Ya (Salud / Health Connect) | No | Nada que hacer |
| iGPSport | Sin API oficial | — | No (salvo FIT a mano) | No | Escribir a la marca; importar FIT |
| Bryton | Sin API pública | — | No (salvo FIT a mano) | No (solo TrainingPeaks) | Escribir a la marca; importar FIT |
| «Remaker» | Sin aclarar | — | — | — | **Pendiente: ¿qué marca es?** |

Añadidas por ser importantes y no estar en la lista: Wear OS/Pixel/Fitbit (dentro de Google), Hammerhead Karoo,
Bryton, Withings y Xiaomi.

## Estado a 2026-11-13

- **Descartadas tras leer sus acuerdos**: Polar, Wahoo, Whoop y Oura (prohíben productos que compitan con ellas, ceder o
  guardar datos en un servidor intermedio e imponen indemnización sin límite), el SDK directo de Samsung (firma de la app
  registrada, SDK propietario) y Huawei (a un particular solo le abre los datos con la app en AppGallery). Lo que sigue
  en este documento sobre cómo solicitarlas queda como registro.
- **Solicitadas**: Suunto (formulario, respuesta prevista hacia el 2026-11-27), Hammerhead (formulario), COROS
  (`api@coros.com`, con la pregunta de si aceptan desarrolladores individuales), Zwift, Bryton, iGPSPORT
  (`global@igpsport.com`) y Amazfit (`developer@zepp.com`).
- **Hecho en código, sin acuerdos**: enviar las sesiones al Apple Watch (WorkoutKit) y a Health Connect (entrenamientos
  planificados). Pendiente: importar archivos FIT.

## 2. Por marca

### Apple Watch — WorkoutKit (sin aprobación)
- Leer: ya hecho con HealthKit.
- Mandar: WorkoutKit (iOS 17+, watchOS 10+) crea `WorkoutPlan` y los programa con `WorkoutScheduler`
  (`requestAuthorization()` y `schedule(_:at:)`); aparecen en la app Entreno del reloj con el icono de Brivel
  [Oficial: WWDC23 10016]. Natación en piscina desde WWDC24 10084 [Oficial].
- Trabajo: traducir el formato de `app/Watch.kt` a `CustomWorkout` (calentamiento, bloques, objetivos de FC/ritmo/potencia).
  Sin servidor. Fuerza: WorkoutKit no tiene series de fuerza [Inferencia: comprobar].

### Google (Health Connect, Wear OS, Pixel Watch, Fitbit)
- Leer: ya hecho con Health Connect.
- Mandar: Health Connect tiene **planes de entrenamiento** (`PlannedExerciseSessionRecord`, requiere
  `FEATURE_PLANNED_EXERCISE`): Brivel escribe la sesión y la app que graba el entreno la lee [Oficial]. No he
  encontrado qué apps de reloj los leen hoy [Pendiente de comprobar].
- Fitbit Web API: apagada el **30-oct-2026**; la sustituye la **Google Health API** (`health.googleapis.com/v4`) con
  OAuth de Google [Oficial: guía de migración]. Solo haría falta para la web (el navegador no lee Health Connect).
  Pedir scopes de salud implica la verificación de apps de Google [Inferencia: comprobar requisitos].

### COROS
- Partner API: OAuth multiusuario, webhooks, sincronización en los dos sentidos, 1.000 llamadas/min [Secundaria: COROS
  vía Nango]. Documentación privada hasta registrarse.
- Solicitud: formulario «Submit an API Application» del soporte de COROS y correo a `api@coros.com` [Oficial].
- Exige plataforma «establecida» [Secundaria]: puede rechazar a una app pequeña. Alternativa sin solicitud: su
  servidor MCP con OAuth, pensado para el propio usuario [Secundaria].

### Wahoo (ELEMNT, KICKR, SYSTM)
- Cloud API: OAuth, actividades, subida de FIT (`workouts_write`) y planes [Secundaria]. Hay que aceptar el acuerdo
  de la API; las apps empiezan en sandbox y Wahoo las revisa para producción [Secundaria].
- Alta en `developers.wahooligan.com`; contacto `wahooapi@wahoofitness.com` [Secundaria].

### Suunto
- API Zone: solicitud por formulario aceptando el acuerdo de la API; respuesta en unas 2 semanas [Oficial: FAQ].
  Desarrollo gratis con límites bajos; **producción con suscripción** [Secundaria].
- Mandar: sesiones estructuradas como **SuuntoPlus Guides** que se sincronizan solas al reloj; hay API de subida
  de entrenos [Oficial: «How to use Suunto Workout Upload Cloud API»].

### Polar
- AccessLink v3: cualquier cuenta de Polar Flow crea el cliente en `admin.polaraccesslink.com`, gratis [Oficial].
- **Solo lectura**: no permite subir sesiones [Oficial 2017 + foros 2023–2025]. Polar solo recibe planes de
  TrainingPeaks (acuerdo propio).
- Cada usuario hay que **registrarlo** en la API tras el OAuth (409 si ya lo está) [Secundaria].

### Samsung (Galaxy Watch)
- Samsung Health Data SDK, en el móvil (como Health Connect). Leer en «modo desarrollador» sin pedir nada; para
  **publicar** hace falta la solicitud de socio (registra paquete y firma SHA-256) [Oficial]. Escribir exige socio.
- Galaxy Watch ya escribe en Health Connect lo básico [Inferencia: comprobar qué se pierde, p. ej. VFC].

### Huawei
- Health Kit de HMS: alta como desarrollador, solicitar el servicio y cada permiso de datos; datos con retraso
  (no en tiempo real) [Secundaria]. API REST en `health-api.cloud.huawei.com`.
- Huawei Health en Android no escribe en Health Connect en todos los países [Inferencia: comprobar]; sí en Apple Salud.

### Oura
- API v2 con OAuth; alta libre; **10 usuarios** hasta que Oura apruebe [Oficial: especificación 1.37]. El usuario
  consiente cada tipo de dato. Contacto: `api-support@ouraring.com` [Secundaria].

### Whoop
- Developer Platform v2 (OAuth, webhooks): ciclos, sueño, entrenos, recuperación. **10 usuarios** sin aprobar; la
  aprobación se pide desde el panel y hay quejas de meses sin respuesta en 2026 [Oficial + foro de Whoop].

### Zwift
- Training API («Zwift Link»): mete sesiones en el calendario de Zwift y devuelve las actividades. **Solo socios**
  (TrainerRoad, Xert, TriDot, Final Surge…); hay plataformas esperando respuesta en 2026 [Secundaria].
- Mientras: intervals.icu ya manda sesiones a Zwift [Secundaria: VIABILIDAD_SIN_INTERVALS §7].

### Withings
- Public API gratis y sin contrato con OAuth; límites de usuarios según plan y webhooks [Oficial + Secundaria].

### Hammerhead Karoo
- Programa de socios («become an official partner») y SDK de extensiones Android; sesiones al Karoo vía socios como
  JOIN [Secundaria]. Sin API pública documentada.

### Strava — descartado
- Desde jun-2026: el desarrollador necesita **suscripción**; prohíbe intermediarios/agregadores (§5.16 de la
  política) y usar los datos con IA; los datos solo se enseñan al propio usuario [Secundaria; leer
  `strava.com/legal/api`]. Brivel no usa IA, pero el relevo de Cloudflare podría contar como intermediario
  [Inferencia]. Sus datos tampoco salen por intervals.icu. **No compensa**, igual que en VIABILIDAD §6.

### Sin API (llegan por Salud / Health Connect)
- **Amazfit/Zepp**: sin API pública; el SDK de Zepp OS es para apps de reloj. Escribe en Health Connect y Salud
  [Secundaria]. Hay accesos no oficiales a su nube: **no usarlos** (condiciones y contraseñas del usuario).
- **Xiaomi**: Mi Fitness → Perfil → Datos de terceros → Health Connect / Salud [Oficial: soporte de Xiaomi].
- **Nike Run Club**: escribe en Salud; según una fuente antigua sin ruta GPS [Secundaria].
- **Peloton**: escribe en Salud y Health Connect; API no oficial, no usar [Secundaria].
- **iGPSport** y **Bryton**: ni API ni Health Connect conocidos; sincronizan con Strava/TrainingPeaks. Opción
  propia: **importar FIT** en Brivel (archivo compartido desde su app) [Inferencia].

## 3. Qué hay que preparar

### Código (sin pedir nada a nadie)
1. **Apple Watch**: WorkoutKit en `iosApp` a partir de `app/Watch.kt` (lo que ya se manda a Garmin).
2. **Android**: escribir `PlannedExerciseSessionRecord` en Health Connect.
3. **Importar FIT** (iGPSport, Bryton, cualquiera): compartir un `.fit` con Brivel y leerlo como actividad.
4. **Base común en el servidor** para las marcas con OAuth: generalizar el relevo de Garmin (`brivel-garmin`) a un
   Worker de conexiones con un traductor por marca (`workout.ts` → `coros.ts`, `wahoo.ts`, `suunto.ts`), mismo
   cifrado al llegar (X25519 + HKDF + AES-GCM), mismo buzón y borrado tras descargar. Las de solo lectura con alta
   libre (Polar, Oura, Whoop, Withings) podrían ir sin relevo si admiten PKCE [Inferencia: comprobar cada una].

### Solicitudes (las envía Gabriel)
Datos comunes, los mismos que en Garmin Starter: Brivel, Founder & Developer, `hola@brivel.app`, https://brivel.app,
app gratuita y open source (AGPL-3.0), sin ánimo de lucro, 0–100 usuarios, datos en la UE (Cloudflare
D1), cifrado de extremo a extremo, política en https://brivel.app/privacidad.

| Orden | Marca | Dónde | Qué pedir |
|---|---|---|---|
| 1 | COROS | Formulario «Submit an API Application» + `api@coros.com` | Partner API: leer actividades y mandar sesiones |
| 2 | Suunto | `apizone.suunto.com` (formulario) | Cloud API + Guides / Workout Upload |
| 3 | Wahoo | `developers.wahooligan.com` | App en sandbox; luego producción con `workouts_write` y planes — **descartado (ver «Estado a 2026-11-13»)** |
| 4 | Whoop | `developer.whoop.com` | App (10 usuarios) y pedir aprobación desde ya (tardan) — **descartado (ver «Estado a 2026-11-13»)** |
| 5 | Oura | `cloud.ouraring.com` | App (10 usuarios) — **descartado (ver «Estado a 2026-11-13»)** |
| 6 | Polar | `admin.polaraccesslink.com` | Cliente AccessLink (inmediato) — **descartado (ver «Estado a 2026-11-13»)** |
| 7 | Zwift | Equipo de partnerships | Training API (probable rechazo; no bloquea) |
| 8 | Hammerhead | Página de socios | Sesiones al Karoo |
| — | iGPSport, Bryton | Soporte | Preguntar si tienen o tendrán API para socios |

Texto base para las solicitudes (adaptar el campo de cada formulario):

> Brivel (https://brivel.app) is a free, open-source endurance training planner developed by one person in Spain.
> It builds a daily plan from the athlete's own data and can send each structured session to their device. We would
> like to read activities [and health metrics] and push planned workouts to [brand] devices for users who connect
> their account. Data is encrypted end to end, stored in the EU (Cloudflare) only until the user's phone downloads
> it, never sold or shared, and never used to train AI models. Expected users: 0–100 in the first year.

### Legal (antes de abrir cada marca a otros; ver `CUMPLIMIENTO.md`)
- Leer y guardar el acuerdo de cada API (como Garmin, fuera del repo si es confidencial).
- EIPD por marca que pase por el servidor (plantilla: `legal/EIPD_GARMIN.md`); actualizar `REGISTRO_ACTIVIDADES.md`.
- Política de privacidad: un apartado por marca y nueva versión del consentimiento.
- Atribución y logos según las normas de marca de cada una.
- Garmin: **nada con datos de Garmin a otros antes del 2026-12-07** (aviso de 30 días).

## 4. Pendiente de comprobar
1. Qué es «Remaker».
2. Qué apps de reloj leen `PlannedExerciseSessionRecord` (Wear OS, Pixel Watch, Samsung).
3. Si WorkoutKit admite fuerza por series.
4. Si COROS acepta apps pequeñas; precios de producción de Suunto.
5. Qué datos se pierden al pasar por Health Connect/Salud con Samsung, Huawei y Amazfit (sobre todo VFC).
6. Las condiciones de Strava en `strava.com/legal/api` (por si cambian).

## 5. Paridad con Strava (objetivo antes de salir, pedido el 2026-11-11)

Lista de Strava [Oficial: support.strava.com, «What devices and apps are compatible with Strava?»]: relojes Apple
Watch, Wear OS, Garmin, COROS, Polar, Suunto, Whoop, Oura, Withings, Amazfit, Huawei, Google Health (Fitbit);
ciclocomputadores Wahoo, Hammerhead, Sigma, Stages, Lezyne, Mio; rodillo y plataformas Zwift, TrainerRoad,
Wahoo SYSTM, ROUVY, Peloton, iFIT, Kinomap.

**Diferencia de fondo:** a Strava son las marcas las que le suben las actividades (Strava solo recibe). Con Brivel cada
marca tendría que aceptarnos o integrarse ella. Así que la paridad se mide por **cobertura** (que los datos de esa
marca lleguen a Brivel), no por tener la misma lista de conexiones directas.

| Marca de la lista de Strava | Cómo llega a Brivel | Depende de terceros |
|---|---|---|
| Garmin | Directo (hecho) | Aviso de 30 días hasta el 2026-12-07 |
| Apple Watch | Salud (hecho) + WorkoutKit (por hacer) | No |
| Wear OS, Google Health/Fitbit | Health Connect (hecho) + planes (por hacer) | No |
| Amazfit, Huawei, Peloton, Samsung | Salud / Health Connect (hecho) | No (directo: sí) |
| COROS, Suunto | Salud / Health Connect y intervals.icu (hecho); directo solicitado el 2026-11-13 | Sí, para conexión directa |
| Wahoo, Polar | Salud / Health Connect y intervals.icu (hecho); directo **descartado** por sus acuerdos | No |
| Whoop, Oura | Salud / Health Connect parcial; directo **descartado** por sus acuerdos | No |
| Withings | Salud / Health Connect; directo con alta libre | No |
| Zwift, TrainerRoad, Wahoo SYSTM, ROUVY, Kinomap, iFIT | Salud / Health Connect o intervals.icu [Inferencia: comprobar cada una] | Directo: solo socios |
| Hammerhead, Sigma, Stages, Lezyne, Mio | intervals.icu o importar FIT (por hacer) | Directo: socios o sin API |

Con lo que no depende de nadie (WorkoutKit, planes de Health Connect, importar FIT) **toda la lista de Strava queda
cubierta en lectura**. Lo que no se puede garantizar antes de salir son las conexiones **directas** que exigen
aprobación (COROS, Suunto, Zwift, Hammerhead; Wahoo, Whoop y Oura quedaron descartadas el 2026-11-13): sus plazos no dependen de Brivel.
**Decisión de Gabriel (2026-11-11): integraciones directas y completas con todas, no solo cobertura.** Consecuencias:
- La fecha de salida ya no depende de Wahoo, Whoop, Oura, Polar, Samsung ni Huawei (descartadas, ver «Estado a 2026-11-13»).
  Siguen abiertas COROS, Suunto, Zwift, Hammerhead, Bryton, iGPSPORT y Amazfit, todas solicitadas.
- Las marcas **sin API** (Amazfit, Nike, Peloton, iGPSport, Bryton, Sigma, Stages, Lezyne, Mio, Xiaomi) solo pueden
  integrarse como lo hacen con Strava: **ellas suben a Brivel**. Hace falta una **API pública de subida de Brivel**
  (OAuth + `POST` de FIT, como la de Strava) y pedir a cada marca que la añada [Inferencia].

Fuentes: [Strava, compatibilidad](https://support.strava.com/en-us/articles/16312772-what-devices-and-apps-are-compatible-with-strava),
[Strava, dispositivos y apps](https://support.strava.com/en-us/collections/19657599-devices-and-third-party-apps).

## Fuentes
- Apple: [WorkoutKit](https://developer.apple.com/documentation/workoutkit), [WWDC23 10016](https://developer.apple.com/videos/play/wwdc2023-10016), [WWDC24 10084](https://developer.apple.com/videos/play/wwdc2024/10084/).
- Google: [Health Connect, planes](https://developer.android.com/health-and-fitness/health-connect/features/training-plans), [Google Health API, migración](https://developers.google.com/health/migration/api-specifications).
- COROS: [solicitud](https://support.coros.com/hc/en-us/articles/17085887816340-Submit-an-API-Application), [Nango](https://nango.dev/docs/integrations/all/coros).
- Wahoo: [soporte, apps asociadas](https://support.wahoofitness.com/hc/en-us/articles/22710368254738), [intervals.icu y Wahoo](https://forum.intervals.icu/t/planned-workouts-with-wahoo-now-supported/54795?page=4).
- Suunto: [API Zone](https://apizone.suunto.com/), [FAQ](https://apizone.suunto.com/faq), [subida de entrenos](https://apizone.suunto.com/how-to-workout-upload), [Tredict, Guides](https://tredict.com/blog/suunto_structured_workouts_with_guides).
- Polar: [AccessLink v3](https://www.polar.com/accesslink-api/), [anuncio (solo lectura)](https://www.polar.com/blog/introducing-polar-open-accesslink-api/), [foro intervals.icu](https://forum.intervals.icu/t/dont-understand-why-polar-doesnt-support-importing-structured-training/69967).
- Samsung: [modo desarrollador](https://developer.samsung.com/health/data/guide/developer-mode.html), [proceso](https://developer.samsung.com/health/data/process.html).
- Huawei: [Health Kit](https://developer.huawei.com/consumer/en/hms/huaweihealth/), [Thryve](https://www.thryve.health/blog/connect-huawei-health-thryve).
- Oura: [autenticación](https://cloud.ouraring.com/docs/authentication), [OpenAPI 1.37](https://api.ouraring.com/v2/static/json/openapi-1.37.json).
- Whoop: [aprobación](https://developer.whoop.com/docs/developing/app-approval), [foro](https://www.community.whoop.com/t/no-response-on-api-app-approval-to-raise-the-10-user-limit/15246).
- Zwift: [terceros](https://support.zwift.com/en_us/linking-zwift-to-another-platform-SypU0LdVr), [DC Rainmaker](https://dcrainmaker.com/2024/11/xert-fascat-join-zwift-integration-trainerroad-soon.html), [foro Zwift](https://forums.zwift.com/t/training-api-partnership-request-fit-pa-survey-submitted-7-oct-no-reply/672347).
- Withings: [developer](https://developer.withings.com/).
- Hammerhead: [extensiones](https://uk.hammerhead.io/pages/extensions-karoo), [JOIN](https://join.cc/cycling-tips/hammerhead-integration-in-join-train-on-your-karoo).
- Strava: [appsforstrava](https://appsforstrava.com/blog/strava-developer-program-changes-2026), [Sahha](https://sahha.ai/blog/strava-api-changes-2026/).
- Amazfit/Xiaomi/Nike/Peloton: [Sahha Amazfit](https://sahha.ai/integrations/amazfit/), [Xiaomi FAQ](https://www.mi.com/my/support/faq/details/KA-517372/), [Nike ayuda](https://www.nike.com/help/a/connect-nrc-health-app), [Sahha Peloton](https://sahha.ai/integrations/peloton/).
- iGPSport/Bryton: [intervals.icu iGPSport](https://forum.intervals.icu/t/request-to-add-igpsport-integration/91944), [intervals.icu Bryton](https://forum.intervals.icu/t/import-from-bryton-devices/57443).
