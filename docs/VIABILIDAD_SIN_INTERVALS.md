# ¿Puede Brivel separarse de intervals.icu? (estudio de viabilidad, 2026-11-03)

Pedido por Gabriel a partir de su nota del 2026-10-30 («Viabilidad de separarse de intervals.icu»). Etiquetas:
**[Verificado]** fuente primaria leída; **[Secundaria]** blog o prensa, sin confirmar con la fuente oficial;
**[Inferencia]** deducción propia; **[Código]** comprobado en el repositorio.

## Conclusión

**Sí es viable, y en parte ya está hecho.** Brivel ya funciona sin intervals.icu para leer datos: con Apple Salud o
Health Connect monta lo mismo que el motor saca de intervals.icu [Código: `deviceData` en `app/HealthData.kt` estima FC
máxima, LTHR y carga]. Lo que intervals.icu aporta de verdad y no tiene sustituto inmediato es **mandar los entrenos al
reloj Garmin** y la **variabilidad cardiaca (VFC) de los relojes Garmin**.

**Recomendación:** dejar intervals.icu como conexión **opcional y secundaria** y no invertir ahora en su OAuth.
1. Ya: las apps funcionan con Apple Salud y Health Connect como fuente principal (lo que hay hoy).
2. Siguiente: **WorkoutKit** para mandar las sesiones al Apple Watch, sin servidor ni aprobación de nadie.
3. Después: solicitar el programa de Garmin (gratis, admite personas) para **entrenos al reloj y VFC** de Garmin.
4. intervals.icu: mantenerlo solo para uso personal mientras no haya OAuth; si se abre a otros, o OAuth o fuera.

## 1. Qué usa Brivel de intervals.icu hoy [Código: `intervals/IntervalsClient.kt`]

| Uso | ¿Hay sustituto? |
|---|---|
| Actividades (`/activities`) y zonas de cada actividad (`streams`) | **Sí:** entrenos de Apple Salud y Health Connect, con FC. La detección de «zona gris» usa los datos por segundo; con Salud solo habría la FC media [Inferencia] |
| Bienestar: VFC, pulso en reposo, sueño, peso (`/wellness`) | **Parcial:** Apple Watch escribe VFC en Apple Salud; **Garmin no** (ver §3) |
| Ajustes del atleta: LTHR, FTP, ritmos (`/athlete`) | **Sí:** se estiman de los entrenos [Código]; añadir FTP o ritmo a mano en Perfil para quien lo sepa |
| Carreras del calendario (`RACE_A/B/C`) | **Sí:** el objetivo y las carreras ya se crean en la app [Código] |
| **Mandar la sesión al reloj** (`events/bulk`) | **No directo:** WorkoutKit (Apple Watch) o el Training API de Garmin (§4) |
| Renombrar actividades | **No** (función menor; se pierde) |
| Datos en la web | **Sí:** la sincronización ya lleva los datos de Salud del móvil a la web [Código: `syncPayload`] |

## 2. Riesgos de quedarse con intervals.icu

- **OAuth obligatorio para abrir la app a otros:** «Apps intended to be used by more than one person should use OAuth»
  [Verificado: forum.intervals.icu/t/609]. Hay que solicitarlo y queda «Pending» hasta que lo aprueban; el hilo se
  contradice sobre si funciona antes [Verificado: forum.intervals.icu/t/2759].
- **Puede perder los datos de Garmin:** «It is likely that we will have to block access to Garmin data via the API soon»
  (9-10-2026), y luego «There will be an easier path for individuals and apps to access data from Garmin devices»
  (14-10-2026) [Verificado: forum.intervals.icu/t/609]. Garmin es justo el uso principal.
- **Atribución a Garmin** obligatoria si se muestran datos de sus relojes (condiciones de la API §1.1) [Verificado], con
  reglas de Garmin que no se han podido leer (abajo).
- **Dependencia de una persona:** intervals.icu es un proyecto pequeño [Inferencia]; si cambia sus condiciones, Brivel
  depende de ello para la función de «enviar al reloj».

## 3. Qué escriben los relojes en Apple Salud y Health Connect

- **Apple Watch → Apple Salud:** VFC (SDNN), pulso, sueño, entrenos; es el caso que Brivel ya lee [Código:
  `HealthKitReader.swift`]. Lo de que Apple Watch escribe la VFC es conocido [Inferencia: no se ha releído la
  documentación de Apple para este estudio].
- **Garmin → Apple Salud y Health Connect:** pasos, pulso, sueño y entrenos; **no** Body Battery, Training Readiness ni
  el estado de la VFC [Secundaria: sahha.ai, 25-10-2026, «HRV Status not written»; Yahoo Tech para la lista de Health
  Connect]. Las páginas de ayuda de Garmin bloquean la lectura automática: **hay que comprobarlo en un móvil** (Garmin
  Connect → Ajustes → Apps conectadas). Health Connect solo desde que Garmin Connect lo añadió en 2025 [Secundaria:
  foros de Garmin].
- **Comprobado por Gabriel el 2026-11-03** en su Garmin Connect (Apple Salud): **la variabilidad cardiaca no aparece**
  entre los datos que comparte.
- **Consecuencia:** sin intervals.icu ni Garmin directo, un usuario de Garmin pierde la VFC, que es la base del
  «cómo estás hoy». Brivel ya sabe trabajar sin VFC (pregunta sueño, energía y agujetas) [Código], pero peor.
- Polar, COROS, Suunto, Samsung, Pixel: sin estudiar aquí.

## 4. Mandar la sesión al reloj sin intervals.icu

| Vía | Para quién | Coste y requisitos | Estado |
|---|---|---|---|
| **WorkoutKit** (`WorkoutScheduler`) | Apple Watch | Gratis, sin aprobación, todo en el iPhone; sesiones por bloques con alertas de pulso, ritmo o potencia | Disponible desde iOS 17 [Verificado: developer.apple.com/documentation/workoutkit; WWDC23 sesión 10016] |
| **Training API de Garmin** | Relojes Garmin | Programa de Garmin, acceso Starter (ver §5); el servidor de Brivel recibe y envía | Reabierto el 21-10-2026 [Secundaria] |
| Archivo FIT de entreno | Garmin y otros | Sin permisos; el usuario lo copia al reloj | Incómodo en el móvil [Inferencia] |
| Wear OS, Samsung, Polar, COROS | — | Sin estudiar | — |

## 5. Garmin directo (Connect Developer Program)

- **Niveles** [Verificado: developer.garmin.com/gc-developer-program/overview]:
  - **Personal**: «for individuals who want to explore, analyze, and build personalized experiences using their own
    data»; actividad, salud; «Pull only»; **sin** Training API.
  - **Starter**: «The starting point for organizations building apps»; Activity, Health, Training, Courses API;
    «Push only»; sin datos históricos; usuarios «Limited» (otra página de Garmin dice «Up to 1,000»).
  - **Enterprise**: ilimitado, con condiciones comerciales a medida.
- **¿Puede solicitarlo una persona?** Sí según Garmin: «from individual developers and startups to companies» [Verificado:
  overview], y el acuerdo es con «you or, if you are entering into this Agreement on behalf of a corporation… the
  corporation» [Verificado: acuerdo, Rev. B, 2026-03-03]. Un blog dice que el formulario pide «businesses, organizations
  and other commercial entities» [Secundaria: sahha.ai]; no se ha podido leer el formulario.
- **Coste:** «no license fees or other payments will be due under this Agreement», aunque Garmin puede cobrar avisando
  con 30 días [Verificado: acuerdo §10.1]. Enterprise: 1.000 $ de entrada y pago por usuario [Secundaria: DC Rainmaker
  vía sahha.ai].
- **Obligaciones que pesan:**
  - Atribución según las Garmin API Brand Guidelines en todo lo que muestre datos de sus relojes (§6.4); Garmin decide si
    se cumple y puede cortar el acceso a los 14 + 14 días (§6.5) [Verificado]. El PDF de las normas no tiene texto
    extraíble: **sin leer**.
  - **Avisar a Garmin con 30 días** y maquetas antes de cada pantalla nueva que muestre sus datos (§6.6) [Verificado].
  - Requisitos de transparencia sobre IA (§15.7) [Verificado: el texto existe; no aplica, Brivel no usa IA].
  - El acuerdo leído es con «Garmin China Shanghai RHQ Co., Ltd.» y trae un anexo de transferencias de China: puede ser la
    versión de otra región [Inferencia: comprobar la versión para la UE al solicitar].
- **Privacidad:** con Starter (solo «push»), Garmin envía los datos al servidor de Brivel. Rompe el modelo actual (el
  servidor no ve datos de salud en claro): habría que hacer de relevo (recibir, que el móvil descargue y borrar) y
  **actualizar la EIPD y la política** [Inferencia].

## 6. Strava (para completar)

Desde junio de 2026 exige suscripción a los desarrolladores Standard y no admite apps que pasen sus datos por terceros;
prohíbe usar sus datos en IA [Secundaria: TechRepublic, foro de intervals.icu t/130240]. Y sus datos no salen por la API
de intervals.icu [Verificado: t/609]. **No compensa.**

## 7. Comparación

| | Quedarse (intervals.icu con OAuth) | Separarse (Salud + WorkoutKit + Garmin directo) |
|---|---|---|
| Trabajo | OAuth y atribución: poco | WorkoutKit: medio; Garmin directo: alto (servidor de relevo, atribución, avisos de 30 días) |
| Depende de aprobación | intervals.icu (OAuth) | Garmin (Starter); WorkoutKit no |
| VFC de Garmin | Sí, mientras Garmin no la bloquee | Solo con Garmin directo |
| Mandar al reloj | Garmin, Wahoo, Zwift (vía intervals.icu) | Apple Watch ya; Garmin con su programa |
| Privacidad | Todo en el móvil | Todo en el móvil salvo Garmin directo (relevo) |
| El usuario necesita | Cuenta de intervals.icu (fricción) | Nada extra con Apple Watch; Garmin Connect |

## Pendiente de comprobar

1. ~~Apple Salud~~: sin VFC (comprobado por Gabriel). Falta Health Connect (Android) y el pulso en reposo.
2. El formulario de Starter de Garmin: si admite a una persona sin empresa.
3. Las Garmin API Brand Guidelines (el PDF no tiene texto; pedirlas o leerlas en un navegador).
4. Cómo queda el acceso a Garmin por la API de intervals.icu tras el anuncio del 14-10-2026.

## Fuentes

- intervals.icu: [acceso a la API](https://forum.intervals.icu/t/api-access-to-intervals-icu/609),
  [OAuth](https://forum.intervals.icu/t/intervals-icu-oauth-support/2759),
  [condiciones](https://forum.intervals.icu/t/intervals-icu-api-terms-and-conditions/114087),
  [Strava en la API](https://forum.intervals.icu/t/strava-api-update-new-terms-subs-required-for-api-access/130240).
- Garmin: [programa](https://developer.garmin.com/gc-developer-program/overview/),
  [portal](https://developerportal.garmin.com/developer-programs/connect-developer-api),
  [acuerdo Rev. B](https://www8.garmin.com/en-US/GARMINCONNECTDEVELOPERPROGRAMAGREEMENT/GARMINCONNECTDEVELOPERPROGRAMAGREEMENT_EN.pdf),
  [normas de marca](https://developer.garmin.com/brand-guidelines/api-brand-guidelines/) (sin leer).
- Secundarias: [Sahha, tarifas 2026](https://sahha.ai/blog/garmin-connect-api-reopened-fees/),
  [Sahha, qué llega a Salud](https://sahha.ai/blog/garmin-developer-program-paused/),
  [the5krunner, clave personal](https://the5krunner.com/2026/10/22/garmin-personal-api/),
  [Yahoo Tech, Health Connect](https://tech.yahoo.com/wearables/articles/garmin-wont-share-data-google-173520201.html),
  [TechRepublic, Strava](https://techrepublic.com/article/news-strava-api-scraping-crackdown).
- Apple: [WorkoutKit](https://developer.apple.com/documentation/workoutkit),
  [WWDC23 10016](https://developer.apple.com/videos/play/wwdc2023-10016).
