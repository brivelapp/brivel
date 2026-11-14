# Visión — Brivel

> **Estado:** borrador para revisar con Gabriel · **Fecha:** 2026-10-22 · **Objetivo ampliado:** 2026-11-14
> Ordena todo lo demás: cada apartado, función o cambio debe encajar aquí. Si algo no encaja, o se cambia la visión o no
> se hace. Nace de la revisión de la web del mismo día (`REVISION_2026-10-22.md`).

## 1. Qué es

**El sistema operativo de tu cuerpo:** la plataforma de cuantificación personal y rendimiento deportivo que reúne en un
solo sitio lo que hoy está repartido entre Garmin Connect, Apple Health y Google Health, y que además **te dice qué hacer
hoy y por qué**.

La diferencia con esas apps no es tener más gráficas, es cerrar el círculo: **medir → entender → decidir → hacer → medir**.
Garmin decide pero encierra tus datos en su ecosistema; Apple y Google guardan los datos pero no deciden nada.

**Objetivo ampliado (2026-11-14, Gabriel):** una sola app gratuita que permita dejar de usar Runna, TrainingPeaks, Hevy,
Strong, Symmetry, intervals.icu, Garmin Connect, Apple Health y Google Health. Brivel **decide por ti y también te deja
decidir a ti**: entrenar sin plan, crear tus sesiones y analizar lo que hiciste. Qué se toma de cada una, en la sección
3.1.

Dos límites que no dependen de Brivel: el reloj Garmin solo se sincroniza a través de Garmin Connect, y Apple Salud y
Health Connect son el almacén de datos del sistema. Esas tres siguen instaladas y trabajando por debajo; el objetivo es
no tener que abrirlas.

## 2. Para quién

| Perfil | Qué necesita sobre todo | Hoy |
|---|---|---|
| Atletas de resistencia | Plan hacia una carrera, ajuste diario, envío al reloj | Hecho en buena parte (modo carrera) |
| Apasionados del fitness | Plan de fuerza y cardio sin fecha, progreso visible | Hecho en parte (Salud) |
| Buscadores de longevidad | Tendencias de sueño, HRV, FC en reposo, VO2max, peso; mínimos de la OMS. Solo lo que miden relojes y básculas: sin analíticas ni marcadores clínicos (decisión del 2026-10-22) | Falta casi todo (no hay historial) |
| Entrenadores y preparadores | Ver y planificar a varios atletas | Más adelante (Fase 4, decisión del 2026-10-22) |

Los tres primeros son la misma persona en momentos distintos: alguien prepara una carrera, la termina y sigue entrenando
por salud. La app no cambia de modo, cambia de objetivo.

## 3. Qué tomamos de cada una

| | Garmin Connect | Apple Health / Google Health | Brivel |
|---|---|---|---|
| Decide la sesión de hoy | Sí (Garmin Coach, sugerencias diarias) | No | Sí, con el motivo de cada decisión y su evidencia |
| Reúne datos de varias marcas | No (solo Garmin) | Sí | Sí: intervals.icu hoy; Apple Salud y Health Connect con la app móvil |
| Historial y tendencias | Sí | Sí | **Falta** (Fase 2) |
| Explica sus fórmulas | No (Firstbeat es cerrado) | No aplica | Sí: motor determinista y documentado |
| Precio | Gratis + Connect+ de pago | Gratis | Gratis, sin niveles de pago ni anuncios |

### 3.1 Ampliación del 2026-11-14: qué tomamos de las demás

Funciones contrastadas el 2026-11-14 con la fuente enlazada (página oficial o ficha de la tienda, leídas en el resumen
de la búsqueda, no enteras). La columna «Ya hay» sale del código de Brivel.

| App | Qué tomamos | Ya hay | Qué dejamos fuera | Fuente |
|---|---|---|---|---|
| Runna | Plan personal de 5 km a ultra, fuerza junto a la carrera, ritmo objetivo por sesión, envío al reloj | Casi todo: motor con fases, ritmos (`WorkoutText.kt`), fuerza, envío al reloj | App propia de reloj con avisos de voz, comunidad | [App Store](https://apps.apple.com/gb/app/runna/id1594204443) |
| TrainingPeaks | Gráfica de forma, fatiga y forma física; planificar a largo plazo; mover sesiones; constructor de sesiones | Estado de forma, mover sesiones, plan frente a lo hecho | Entrenadores (Fase 4), tienda de planes | [Ayuda oficial](https://help.trainingpeaks.com/hc/en-us/articles/204072624-Basic-Athlete-vs-Premium-Athlete-account) |
| Hevy | Entreno libre, superseries, tipos de serie (normal, calentamiento, descendente, al fallo), 1RM estimado y volumen por ejercicio, series por grupo muscular, calculadora de discos y de calentamiento | Series, rutinas, 1.310 ejercicios, descansos, progresión, series por zona | Red social | [Ayuda oficial](https://help.hevyapp.com/hc/en-us/articles/33106320824727) |
| Strong | Lo mismo, más medidas corporales | Peso corporal | — | [App Store](https://apps.apple.com/app/id464254577) |
| Symmetry | Progreso por grupo muscular, fotos de antes y después | Series por zona | Planes hechos con IA a partir de un escaneo del cuerpo (principio 5), red social | [App Store](https://apps.apple.com/app/id6474446718) [Inferencia: «Symmetric» es esta app] |
| intervals.icu | Análisis de la actividad (vueltas, zonas, desacople), curva de potencia y ritmo, umbrales estimados, efecto del plan futuro en la forma | Carga propia con datos del móvil, proyección de la forma en `plan()` | Gráficas personalizadas con código | [Analizar](https://www.intervals.icu/features/analyze/), [curva de potencia](https://www.intervals.icu/features/power-curve/), [forma](https://www.intervals.icu/features/fitness-chart/) |
| Garmin Connect | Resumen del día, sueño, estrés y energía, rutas, kilómetros del material | Sueño, VFC, FC en reposo, pasos, VO2max, estado de entrenamiento, tiempos previstos | Ajustes del reloj, insignias y retos | [Blog de Garmin](https://www.garmin.com/en-US/blog/general/connect-features-you-should-know-about/) |
| Apple Health | Tendencias y avisos de cambio, fases de sueño | Tendencias con rango normal | Medicación, historial médico, ciclo (de momento) | [Apple](https://www.apple.com/ca/health) |
| Google Health (antes Fitbit, desde el 2026-05-19) | Objetivo semanal de cardio, sueño | Minutos de la OMS por semana | Entrenador con IA de pago, historial médico | [9to5Google](https://9to5google.com/2026/05/07/google-health-fitbit-features/) |

## 4. Principios (no negociables)

1. **Un solo sistema.** Un motor, una navegación, un lenguaje visual. Nada de "modo carrera" y "modo salud" separados.
2. **Lenguaje llano primero, detalle al tocar.** Cada número tiene una frase que lo explica ("Forma: descansado"); el
   término técnico (TSB, HRV, RIR) aparece al tocar, nunca solo.
3. **Toda decisión se explica.** El estado de hoy, la sesión y su intensidad salen de la misma decisión del motor y la
   pantalla muestra ese motivo, no una interpretación aparte.
4. **Evidencia etiquetada.** Cada regla con [Verificado], [Práctica] o [Inferencia] y su fuente.
5. **Tus datos son tuyos.** Exportar y borrar en un toque, consentimiento separado para salud (RGPD), y ningún dato de
   usuario pasa por una IA: el motor no usa IA en tiempo de uso.
6. **Gratis y abierto (AGPL-3.0).** Sin niveles de pago, anuncios ni notificaciones comerciales.
7. **No es un producto sanitario.** Mide y orienta; no diagnostica ni trata.

## 5. Estructura de la app

Cinco apartados (Hoy, Calendario, Entrenar, Progreso y Perfil; Entrenar se decidió el 2026-11-14) y un flujo de
objetivo. Todo lo de hoy (Salud, Plan, Hoy) se reparte aquí; nada queda fuera.

### Objetivo (flujo propio, fuera del perfil)

- **Primera vez:** la app empieza preguntando qué quieres (como Garmin): preparar una carrera, ponerte en forma, ganar
  fuerza y músculo o mejorar tu salud a largo plazo. Después, días y minutos disponibles, y si quieres conectar tu reloj.
- **Al terminar una carrera:** la app lo detecta y vuelve a preguntar (¿otra carrera, mantener la forma, descansar?).
- **Siempre se puede abandonar o reajustar** desde Hoy o Calendario, sin pasar por el perfil.
- Un objetivo de carrera no sustituye a la salud: el suelo de la OMS (150 min y fuerza 2 días) se cumple siempre.

### Hoy

- **Tu cuerpo hoy en una frase**, salida de la misma decisión que la sesión ("Has dormido poco: hoy suave").
- **La sesión de hoy** y sus alternativas en tus otros deportes. Elegir una la convierte en la sesión del día (en la app
  y en el reloj).
- Enviar al reloj, marcar como hecha, "hoy no puedo".
- Check-in opcional (sueño, energía, agujetas) solo si no hay reloj que lo mida, y diciendo para qué sirve.

### Calendario

- **Vista mensual** (y semanal al tocar una semana). El pasado muestra lo que hiciste de verdad; el futuro, el plan.
- Cada actividad hecha aparece emparejada con la sesión planificada y **con un título que dice lo que fue** ("Rodaje Z3",
  "Tempo"), no el nombre automático del ciclocomputador; el título se corrige también en intervals.icu.
- Tocar un día: detalle, mover la sesión, cambiar el tiempo disponible.

### Entrenar (decidido el 2026-11-14, sin construir)

- **Empezar un entrenamiento sin plan:** fuerza libre, o grabar una actividad con el GPS del móvil.
- **Tus rutinas de fuerza y tus sesiones propias de resistencia** (pasos con duración y objetivo), que se pueden poner
  en el calendario y enviar al reloj.
- **La biblioteca de ejercicios**, con el historial y la gráfica de cada uno.
- Rutinas y biblioteca salen de Perfil y pasan aquí.

### Progreso

- Tendencias con tu rango normal: sueño, HRV, FC en reposo, peso, pasos, VO2max, ritmo o potencia umbral y carga
  ("Forma", "Forma física" y "Fatiga" con su explicación, no "TSB/CTL/ATL" a secas).
- Minutos activos y días de fuerza frente a la recomendación de la OMS, semana a semana.
- Hitos: mejores marcas, semanas cumplidas.

### Perfil

- Tus datos, deportes, días y minutos, material (gimnasio o peso corporal), conexiones (reloj, intervals.icu), privacidad
  (exportar, borrar). **Sin objetivo.**

### Entrenadores (más adelante)

- Un espacio aparte para ver y planificar a varios atletas con su permiso. Exige permisos por atleta y probablemente la
  autorización OAuth de intervals.icu en vez de la clave de cada usuario [Inferencia: sin comprobar].

## 6. Un solo motor

- El objetivo decide la periodización: **sin fecha** (bloques de 4 semanas que se renuevan, lo que hoy es Salud) o
  **hacia un evento** (fases base → construcción → pico → taper, lo que hoy es el modo carrera).
- Los mismos datos de entrada para los dos: perfil, disponibilidad, lo hecho de verdad y el estado del día.
- Se reutiliza el código que hay: `training_engine.py` (periodización y catálogo de sesiones) y `health_plan.py`
  (dosis OMS, fuerza, semanas). El trabajo es unir sus salidas en un único plan por usuario, no reescribirlos.

## 7. Arquitectura: todo en el móvil (decidido el 2026-10-22)

- **Apps nativas:** SwiftUI en iOS y Jetpack Compose en Android.
- **Motor en un núcleo compartido** por las dos apps, que corre en el propio móvil. Propuesta: Kotlin Multiplatform
  (decidido). El motor en Python se reescribió una vez en Kotlin con pruebas de equivalencia; desde el 2026-10-29 el
  núcleo de Kotlin es la **única fuente**: el motor del prototipo queda congelado y la validación vive en
  `shared/src/jvmTest/.../engine/ValidationTest.kt` (los casos de referencia, en `shared/src/jvmAndJsTest`).
- **Los datos se quedan en el móvil:** Apple Salud / Health Connect se leen en el dispositivo e intervals.icu se llama
  directamente desde la app con la clave del usuario, guardada en el llavero del sistema.
- **Sin servidor ni cuentas** en las fases 1–3: no hay contraseñas, emails ni base de datos con datos de salud de nadie.
  Exportar y borrar siguen existiendo, en el propio móvil.
- **El servidor solo vuelve si hace falta:** entrenadores (compartir datos entre personas), Garmin directo (envía los
  datos a un servidor) y quizá Strava (su autorización) [Inferencia: sin comprobar].
- **La web actual** (Next.js + FastAPI) queda como prototipo de referencia mientras llegan las apps.
- **Versión web (2026-10-30, en estudio):** el núcleo compila a JavaScript (`js { browser() }` en
  `shared/build.gradle.kts`) y los casos de referencia (`shared/src/jvmAndJsTest`) dan lo mismo en JS que en la JVM; el
  CI lo comprueba con `:shared:jsNodeTest`. La web sería estática, con el motor en el navegador y los datos guardados
  en él, alojable gratis (por ejemplo Cloudflare). intervals.icu acepta llamadas desde el navegador: CORS comprobado el
  2026-10-30 en `/athlete/0` y `/wellness` (permite `authorization` y GET, POST, PUT, DELETE). El navegador no lee Apple
  Salud ni Health Connect, así que en la web los datos vendrían de intervals.icu. **Interfaz decidida el 2026-10-30:**
  propia, en `web/` (Vite + React + TypeScript), que pinta las pantallas que calcula el núcleo (`WebApp.kt` las entrega
  en JSON); Compose Multiplatform para web se descartó por seguir en Beta, dibujar en canvas (peor accesibilidad) y
  reutilizar poco. Hecha Hoy con datos de prueba. Los datos van en `localStorage`; la clave de intervals.icu, en
  `sessionStorage` (se borra al cerrar la pestaña), y una CSP solo deja conectar con intervals.icu. Los tipos de `web/src/types.ts` se escriben a mano; `WebTypesTest` (jsTest) avisa si el núcleo deja de mandar
  alguno de sus campos.
- **Conexiones directas sin intervals.icu (estudiado el 2026-10-30, sin decidir):** el móvil ya puede prescindir de
  intervals.icu con Apple Salud y Health Connect (Garmin escribe en Health Connect actividades, pulso y sueño, desde
  Android 14; no Body Battery ni estrés). Garmin, Strava, Polar, COROS o Wahoo directos necesitan servidor (OAuth con
  secreto y webhooks). El más barato sería Cloudflare Workers + D1 (0 € hasta 100.000 peticiones al día) usado solo
  como relevo: recibe, el móvil descarga y se borra. Avisos: el programa de Garmin podría tener las altas pausadas (sin
  confirmar), Strava exige suscripción y prohíbe intermediarios desde el 2026-06-30, y un servidor con datos de salud
  hace a Brivel responsable del tratamiento (RGPD art. 9; revisar `CUMPLIMIENTO.md` antes).
- **Servidor (2026-10-30, decidido: Cloudflare Workers + D1):** Gabriel lo quiere para alojar la web, cuentas y
  sincronización, Garmin/Strava directos y, al final, el motor como API. Hecha la base en `server/` (sirve `web/dist`
  y `/api/estado` comprueba D1), publicada en https://brivel.app el 2026-11-02 (Worker `brivel`, D1 `brivel` en la UE; sin `workers.dev`). Cuentas y
  sincronización: código de sincronización con cifrado de extremo a extremo, sin email ni contraseña; diseño revisado en
  [`SINCRONIZACION.md`](SINCRONIZACION.md). Esto cambia «sin servidor ni cuentas» de arriba: las cuentas serán
  opcionales y la app seguirá funcionando sin ellas.

**Coste:** lo fijo son las cuentas de desarrollador (Apple ~99 $/año, Google ~25 $ una vez; cifras de memoria, sin
comprobar) y el dominio `brivel.app`. El servidor (Workers + D1) entra en el plan gratis de Cloudflare (100.000
peticiones al día, 5 GB de D1; [`SINCRONIZACION.md`](SINCRONIZACION.md)); solo costaría si se pasa de ahí.

## 8. Datos

| Fuente | Qué aporta | Estado |
|---|---|---|
| intervals.icu | Actividades, carga, umbrales y wellness: sueño (duración, puntuación, calidad), HRV, FC en reposo, pasos, peso, grasa corporal, VO2max, SpO2, tensión arterial, glucosa, fase menstrual, nutrición | Conectado. Campos comprobados en una cuenta real el 2026-10-22 (no todos tienen datos) |
| Manual en la app | Sesiones hechas, check-in del día | Hecho |
| Apple Salud / Health Connect | Lo mismo desde el móvil, sin intervals.icu | Exige app móvil nativa (Fase 3) |
| Strava, Garmin directos | Actividades sin pasar por intervals.icu | Fase 5; Garmin exige aprobación de su programa de desarrolladores [Práctica: sin comprobar] |

## 9. Hoja de ruta

| Fase | Qué | Resultado |
|---|---|---|
| 1. Cohesión | Estructura de la sección 5, un solo motor, flujo de objetivo, lenguaje llano, errores de la revisión | Una app que se entiende de punta a punta. **Hecha (2026-10-22)** en las apps nativas |
| 2. Progreso | Historial y tendencias con los datos de intervals.icu | Lo básico de Garmin Connect y Apple Health. **Hecha la base (2026-10-22):** tendencias con tu rango normal, de intervals.icu o del móvil. **2026-10-29:** estado de entrenamiento (mejorando, manteniendo, recuperando, sobrecarga, perdiendo forma), tiempos previstos de 5 km a maratón (Daniels, con el VO2max del reloj o tu mejor carrera) y últimos entrenamientos |
| 3. Apps nativas | Núcleo compartido, iOS y Android, Apple Salud y Health Connect, notificaciones | Usable sin intervals.icu y sin servidor. **En curso (2026-10-22):** núcleo, las dos apps, Health Connect, Apple Salud y el recordatorio diario opcional hechos; probada en iPhone real (2026-10-29: arranca y vincula intervals.icu, firmada sin cuenta de desarrollador); falta Android real |
| 4. Entrenadores | Varios atletas por cuenta | Abre el perfil profesional |
| 5. Integraciones | Strava y Garmin directos (con servidor) | Plataforma completa |

**Bloques del objetivo ampliado (orden aprobado por Gabriel el 2026-11-14):**

| Orden | Bloque | Qué | Sustituye a | Tamaño |
|---|---|---|---|---|
| 1 | A. Fuerza libre | Pestaña Entrenar, entreno sin plan, superseries, tipos de serie, historial y gráfica por ejercicio (1RM estimado, volumen), calculadora de discos, medidas corporales | Hevy, Strong, Symmetry | M |
| 2 | C. Constructor de sesiones | Sesiones propias de resistencia al calendario y al reloj | TrainingPeaks, intervals.icu, Garmin | M |
| 3 | B. Actividades propias | Importar FIT, guardar la actividad con sus series, detalle (mapa, vueltas, gráficas, zonas), curvas de potencia y ritmo, umbrales propios | intervals.icu | L |
| 4 | G. Grabar con el GPS del móvil | Añadido por Gabriel el 2026-11-14. Va detrás de B porque usa su almacén de actividades | Runna, Garmin | L |
| 5 | D. Plan de carrera | Repasar la vista del plan entero hasta la carrera; los ritmos por sesión ya están | Runna | S |
| 6 | E. Salud | Fases de sueño, estrés, SpO2, respiración, temperatura. Solo lo que miden reloj y báscula | Apple Health, Google Health, Garmin | M |
| 7 | F. Material | Kilómetros por zapatilla y bici | Garmin, intervals.icu | S |

Avisos antes de empezar B y G:

- **B:** la copia cifrada tiene un tope de 1 MB ([`SINCRONIZACION.md`](SINCRONIZACION.md)); las actividades con sus
  series no caben. Hace falta un almacén local propio y otra forma de sincronizarlas. Sin diseñar.
- **G:** la ubicación es una categoría nueva de datos: revisar [`CUMPLIMIENTO.md`](CUMPLIMIENTO.md), la política de
  privacidad y la EIPD, y declarar la ubicación en segundo plano en Google Play. La web no puede grabar con la pantalla
  apagada [Inferencia], así que sería solo de las apps.

## 10. La revisión del 2026-10-22 en esta estructura

| Punto | Dónde | Fase |
|---|---|---|
| 1. "Recuperación" arriba y "Aeróbico base" abajo | Hoy: la frase sale de la decisión del motor | 1 |
| 2. Títulos de las actividades | Calendario (y en intervals.icu) | 1 |
| 3. La alternativa elegida pasa a ser la principal | Hoy | 1 |
| 4. Calendario mensual con lo hecho | Calendario | 1 |
| 5. Forma, fitness y HRV sin explicar | Todos (principio 2) | 1 |
| 6. Para qué sirve Salud | Desaparece: se reparte en Hoy, Calendario y Progreso | 1 |
| 7. Objetivo fuera del perfil, como Garmin | Flujo de objetivo | 1 |
| 8. Historial de sueño, HRV, ritmo, VO2max | Progreso | 2 |

## 11. Decisiones

| Pregunta | Decisión (2026-10-22) |
|---|---|
| Móvil | Apps nativas, todo en el móvil (sección 7) |
| Longevidad | Solo lo que miden relojes y básculas; sin analíticas |
| Entrenadores | Más adelante (Fase 4) |
| Financiación | Cuentas de desarrollador y dominio; el servidor, en el plan gratis de Cloudflare mientras quepa |
| Nombre | **Brivel** (2026-10-30; antes TrainSync). Dominio `brivel.app` comprado; marca sin registrar (sección 12) |

| Pregunta | Decisión (2026-11-14) |
|---|---|
| Objetivo | Sustituir también a Runna, TrainingPeaks, Hevy, Strong, Symmetry e intervals.icu (secciones 1 y 3.1) |
| Navegación | Quinta pestaña, Entrenar (sección 5) |
| Salud | Sigue limitada a reloj y báscula; nutrición, medicación e historial médico, de momento no |
| Grabar con el GPS del móvil | Sí (bloque G) |
| Orden | A → C → B → G → D → E → F (sección 9) |
| Red social y retos | Sin decidir; fuera mientras tanto |

## 12. Nombre

**Decidido el 2026-10-30: Brivel** (*brío* + «-vel»). Sustituye a TrainSync. Hasta el 2026-11-02 el nombre antiguo
siguió en los identificadores internos; ese día pasaron todos a Brivel: paquete y `applicationId`/bundle id
`app.brivel` (antes `io.github.mrclusterman.trainsync`), claves de guardado `brivel.*` y repositorio `brivelapp/brivel`.
La app se instala como una nueva: los datos de la anterior no pasan solos (se pueden llevar con la sincronización).

Criterios: palabra inventada o real, neutra en español e inglés, dos sílabas como mucho y `.app` libre.

Comprobado el 2026-10-30:

- **Dominio:** `brivel.app` comprado en Cloudflare Registrar (caduca el 2027-10-30). `brivel.com` está registrado por otro.
- **App Store** (España y EE. UU., API de búsqueda de Apple): ninguna app con ese nombre.
- **Marcas** (WIPO Global Brand Database, que incluye EUIPO, OEPM y Madrid): ninguna BRIVEL en la UE ni en España;
  solo una en Corea, clase 3. La más parecida es BREVEL (Brevel Ltd., internacional con la UE y España, clases 5 y 29).
  Nada parecido en las clases de una app (9, 10, 41, 42, 44).
- **Registro de la marca en EUIPO:** aplazado hasta que haya presupuesto.
- **Símbolo:** la línea de pulso; icono verde en claro, bosque en oscuro y monocromo para iOS tintado y Android temático.

Descartados:

| Nombre | Motivo |
|---|---|
| Bios | BIOS Health (Cambridge, salud) usa bios.health; bios.app y biosapp.com registrados |
| PhysOS | Ya hay una app "PHYSOS" (Medicina) en App Store |
| Anabios | AnaBios Corp. (San Diego, investigación farmacéutica) |
| Biotos | Libre, pero no convence |
| Brivo | Marca BRIVO registrada en la USPTO, clase 9 (Brivo Systems LLC), con apps en la App Store |
| Palabras reales cortas (Brio, Soma, Cora, Pulsa, Tono, Vigor…) | `.app` registrado y muchas apps con el mismo nombre |
