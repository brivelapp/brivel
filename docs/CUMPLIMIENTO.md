# CUMPLIMIENTO — Brivel

> **Versión:** 0.3 · **Fecha:** 2026-10-21
> **Ámbito:** app gratuita y open source, responsable en España, usuarios en la UE, que trata datos de salud (FC, HRV, sueño) importados de intervals.icu.
> **No es asesoramiento legal.** Las referencias legales se han contrastado con resúmenes y fuentes secundarias, no siempre con el texto del BOE o del DOUE; antes de publicar la app conviene revisarlas con un profesional.

Etiquetas: **[Verificado]** comprobado en el código o en la app real · **[Fuente]** respaldado por la fuente citada (secundaria salvo que se diga) · **[No verificado]** pendiente de confirmar.

---

## 0. Lo más importante

1. **Los datos de Brivel son datos de salud.** FC, HRV, sueño y métricas continuas de wearables se tratan como categoría especial del art. 9 RGPD. Hace falta **consentimiento explícito**, específico para datos de salud y separado de la aceptación de los términos (art. 9.2.a). [Fuente: guías de cumplimiento sobre wearables; los reguladores tienden a no aceptar el argumento "es un dispositivo de consumo"]
2. **Nada de esto bloquea el uso personal actual** (un solo usuario, en local, con su propia cuenta). **Sí bloquea abrir la app a otras personas**: todo lo marcado "antes de publicar" debe estar hecho antes de la Fase 1 en producción.

---

## 1. Estado por punto

| # | Requisito | ¿Aplica? | Estado hoy | Cuándo | Base / nota |
|---|---|---|---|---|---|
| 1 | Política de privacidad | Sí | **Hecha (2026-11-02)**: `web/public/privacidad.html` → https://brivel.app/privacidad. **Versión 2 (2026-11-02.2):** añade la sincronización (apartado 4); publicada con la API | Revisar con cada cambio de datos (sincronización, OAuth) | RGPD arts. 13-14. Cubre: datos solo en el dispositivo (incluidos los de salud de Apple Salud y Health Connect), intervals.icu como servicio independiente, Cloudflare como encargado (IP; DPA v6.4 incorporado a su Self-Serve Subscription Agreement §6.1 [Verificado: cloudflare.com/terms y /cloudflare-customer-dpa, 2026-11-02]; transferencias a EE. UU. con el Marco de Privacidad UE-EE. UU. y cláusulas tipo de respaldo, DPA §6.2 y §6.4), derechos y AEPD. Las apps aún no enlazan la política: hace falta antes de las tiendas (Apple y Google piden la URL) |
| 2 | Términos de uso | Sí | **Hechos (2026-11-02)**: `web/public/terminos.html` → https://brivel.app/terminos | — | No es consejo médico ni producto sanitario, el usuario entrena bajo su responsabilidad, edad mínima 14, sin garantía en la medida que permita la ley, AGPL y ley española. La web enseña el aviso de salud (`HEALTH_NOTICE`) antes de empezar, como las apps |
| 3 | Política de reembolso | **No**, mientras sea gratis | — | Si hay pago o donaciones con contraprestación | — |
| 4 | Política de cookies | Hoy no hay cookies (descrito en la política de privacidad, §6) | [Verificado: 0 cookies en / y /plan] | Fase 1 (cookie de sesión) | Las cookies técnicas estrictamente necesarias (sesión, autenticación) están exentas de consentimiento (art. 22.2 LSSI, guía AEPD) [Fuente: resúmenes de la guía AEPD]. Se describirán en la política de privacidad |
| 5 | Banner de consentimiento de cookies | **No**, mientras solo haya cookies técnicas | — | Solo si se añade analítica o terceros | Si se añade, debe tener "rechazar" con el mismo peso que "aceptar" (ver #9) |
| 6 | Consentimientos en formularios | Sí | **Hecho para la sincronización (2026-11-02)**: dos casillas sin marcar (datos de salud y edad), separadas de las condiciones, en web, Android e iOS; se guarda la fecha y la versión de la política en el dispositivo | Revisar si se añade otro formulario | Art. 9.2.a. El servidor no guarda el consentimiento (no sabe quién es cada copia): se demuestra con el diseño, que no deja crear una copia sin marcar las casillas [Inferencia] |
| 7 | Sin datos innecesarios | Sí | Parcial | Fase 1 | Hoy no se guarda nada; `/athlete` de intervals.icu devuelve email y otros datos que el código no usa ni guarda [Verificado]. **Conflicto:** el modelo de la Memoria guarda `raw_data` JSONB completo de actividades y wellness "para auditoría": revisar en Fase 1 y guardar solo los campos usados |
| 8 | Auditar SDKs de terceros | Sí | Hecho para el frontend | Revisar en cada dependencia nueva | [Verificado: el navegador solo contacta con el propio dominio]. Las fuentes se sirven desde el propio servidor (`next/font`), lo que evita el problema de la sentencia de Múnich de 2022 sobre Google Fonts [Fuente]. Sin analítica ni trackers. Backend: solo intervals.icu (y Ollama local, opcional) |
| 9 | Eliminar dark patterns | Sí (principio) | Sin incidencias | Siempre | Sin casillas premarcadas, sin urgencias falsas, borrar la cuenta igual de fácil que crearla. **Compromiso público** (README y pie de la web, 2026-10-21): gratis, sin niveles de pago, sin anuncios y sin notificaciones comerciales. Nace del análisis de reseñas (`replica/fixes.md`): es la queja principal contra Garmin Connect+ y la versión gratis de TrainingPeaks. **Revisar en cada versión:** ninguna función bloqueada ni contenido difuminado para empujar a pagar, ningún SDK de anuncios o analítica (ver #8) y ninguna notificación que no sea del propio entrenamiento |
| 10 | Eliminar costes ocultos | **No** (gratis) | — | Si hay pagos | — |
| 11 | Eliminar reseñas falsas | No hay reseñas | — | Si se muestran testimonios | Directiva Ómnibus: prohibido publicar reseñas sin verificar que son de usuarios reales; hay que indicar cómo se verifican [Fuente] |
| 12 | Eliminar afirmaciones sin respaldo | **Sí** | Parcial | Antes de publicar | El motor etiqueta cada regla ([Verificado]/[Práctica]/[Inferencia]). Revisar textos públicos: la Memoria dice "científicamente fundamentados" y parte de las reglas son práctica o inferencia. Usar "basado en literatura citada" |
| 13 | Texto alternativo | Sí | Cumple | Siempre | No hay imágenes; los iconos SVG son decorativos (`aria-hidden`) junto a texto; el icono de zona gris tiene `aria-label` [Verificado]. Toda imagen futura necesita `alt` |
| 14 | Contraste de color | Sí | **Corregido** | Siempre | Auditado con la fórmula WCAG: 2 fallos corregidos (chip Z6 3,63 → 4,82; botón sobre acento 3,31 → 5,29). El resto ≥ 4,5:1 [Verificado] |
| 15 | Navegación por teclado | Sí | Cumple en principio | Revisión manual pendiente | Elementos nativos (`button`, `a`, `details`, `dialog` con Esc), foco visible [Verificado en código]. Falta una prueba manual completa solo con teclado |
| 16 | Datos del responsable | Sí | **Hecho (2026-11-02)**: nombre y `hola@brivel.app` en privacidad y términos | Si hay ingresos (publicidad, patrocinio, donaciones), añadir domicilio y NIF | RGPD art. 13: identidad y contacto. **LSSI no aplica mientras no haya actividad económica** [Verificado: FAQ oficial, lssi.digital.gob.es/lssi/la-ley/preguntas-frecuentes: «La Ley no se aplicará a una página web personal cuando su titular no realice ningún tipo de actividad económica a través de la misma»; hay actividad económica con ingresos directos o indirectos, «con independencia de que éstos permitan sufragar el coste»]. Por eso no se publican domicilio ni NIF. **Antes del deploy:** `hola@brivel.app` tiene que funcionar (Cloudflare Email Routing) |
| 17 | Consentimiento de edad (menores) | Sí | **Hecho para la sincronización**: declaración de 14 años o más al activarla; también en los términos | — | España: 14 años (LOPDGDD art. 7) [Fuente: AEPD]. No se puede verificar (EIPD, R8) |
| 18 | Enlace de baja en emails | Solo si se envían emails | No hay emails | Si se añaden avisos por email | Emails no transaccionales: dirección o enlace de baja en cada envío (LSSI art. 21) [Fuente: resoluciones AEPD] |
| 19 | Licencias de fuentes e imágenes | Sí | **Cumple** | — | Figtree e IBM Plex Mono: SIL OFL 1.1; DM Mono, Geist e Instrument Sans (prototipo): SIL OFL 1.1 [Fuente: repositorios y Google Fonts, secundario]. Iconos propios. Proyecto bajo **AGPL-3.0-only** (`LICENSE`, texto oficial de gnu.org); avisos en `THIRD_PARTY_NOTICES.md`. Enlace al código fuente en el pie de la web y en los términos (2026-11-02); falta en las apps |
| 20 | Solicitud de borrado de datos | Sí | **Hecho**: «Borrar todo» en el dispositivo y «Borrar la copia del servidor» (al momento; el historial de D1 la conserva hasta 7 días) | — | RGPD art. 17; responder en ≤ 1 mes, ampliable 2 meses avisando (art. 12.3) [Fuente: decisiones publicadas por el EDPB] |

---

## 2. Otros puntos que salen de la revisión

| Punto | Nota |
|---|---|
| Accesibilidad legal (European Accessibility Act) | Desde el 28-06-2025, estándar EN 301 549 (WCAG 2.1 AA). Las microempresas que prestan servicios estarían exentas [Fuente, secundaria; alcance exacto no verificado]. **Decisión:** usar WCAG 2.1 AA como objetivo de todos modos |
| Portabilidad (art. 20 RGPD) | Exportar los datos del usuario en formato legible por máquina, junto al borrado |
| Seguridad (art. 32 RGPD) | API keys cifradas (AES-256-GCM), contraseñas con scrypt, TLS al desplegar. Login limitado por cuenta, recuperación de contraseña con enlace de un solo uso que cierra las demás sesiones y verificación del email [Verificado en tests]. Sin conexión, la web guarda en el dispositivo las páginas ya vistas (datos de salud incluidos) y las borra al cerrar sesión. Pendiente al desplegar: SMTP configurado (sin él los enlaces quedan en el log) y límite de intentos compartido si hay varias instancias |
| Cribado previo al ejercicio | **Decisión 2026-10-21: aviso legal y de salud en vez de cuestionario** (`prototype/web/src/components/HealthNotice.tsx`): no es servicio médico, cuándo consultar y cuándo parar; guardar el perfil confirma que se entrena bajo propia responsabilidad. Riesgo asumido: sin cuestionario el plan no limita la intensidad a quien tenga enfermedad o síntomas. El algoritmo del ACSM (Riebe 2015) con preguntas propias en `prototype/backend/app/services/screening.py`. **No usar el PAR-Q+**: su licencia prohíbe incorporarlo a herramientas electrónicas sin permiso escrito de la PAR-Q+ Collaboration [Fuente: eparmedx.com]. El cribado no diagnostica: solo recomienda consultar y limita la intensidad del plan. Apps nativas (2026-10-29): el algoritmo está en el núcleo (`shared/.../engine/Screening.kt`) y su tope se aplica en todos los objetivos si el perfil guarda respuestas, pero las apps no hacen el cuestionario, de acuerdo con esta decisión. **Decisión confirmada el 2026-10-29** tras revisar a la competencia: ni COROS (términos UE, 23-04-2023: "no es consejo médico", el usuario asume los riesgos, edad mínima 16), ni Garmin (manuales: "consulta a tu médico antes de empezar o modificar un programa de ejercicio"), ni Runnea (aviso legal y páginas de la app) hacen cuestionario; todas se apoyan en avisos. Sin leer: los términos de Garmin Connect (la página carga el texto con JavaScript) y la "Política de contratación" de Runnea (shop.runnea.academy no respondía) |
| Producto sanitario | Un planificador de entrenamiento sin finalidad médica en principio no es producto sanitario [No verificado]. Evitar afirmaciones de diagnóstico o tratamiento |
| Proveedor de datos (intervals.icu) | **Revisado 2026-11-02.** Las condiciones de la API (forum.intervals.icu/t/114087, vigentes desde el 23-10-2025) permiten cualquier uso lícito, también comercial, y no hablan de claves personales ni de OAuth [Verificado]. Pero el operador pide en el foro que las apps para varias personas usen OAuth: «Apps intended to be used by more than one person should use OAuth and Bearer tokens» (forum.intervals.icu/t/609) [Verificado]. Las apps piden hoy la clave personal: **antes de abrirlas a otros, pasar a OAuth** (cliente público con PKCE; solicitud en intervals.icu/oauth/apply, queda «Pending» hasta que la aprueban). Garmin: si se muestran datos de dispositivos Garmin, hay que atribuirlos según su guía de marca (condiciones §1.1); el foro avisa (9-10-2026) de que quizá bloqueen los datos de Garmin por la API. La web no usa intervals.icu |
| Historial de git con datos personales | La Memoria guardaba el ID de atleta y datos fisiológicos del desarrollador. Se anonimizaron y se reescribió todo el historial (2026-10-20) [Verificado: ningún commit los contiene]. Pero tras reescribir y hacer force push, los commits antiguos siguen accesibles en GitHub "directamente por su SHA-1 en vistas en caché" [Fuente: GitHub Docs]. No hay forks ni PR que los referencien [Verificado: 0 y 0]. **Hecho (2026-10-20):** repo borrado en GitHub y recreado con el historial limpio [Inferencia: GitHub Docs solo propone contactar con Soporte, que "no eliminará datos no sensibles"]. El commit antiguo `55fae10` ya responde "No commit found" [Verificado] |

---

## 3. Fuentes

- RGPD art. 9 y wearables: [GDPR Local](https://gdprlocal.com/gdpr-for-wearable-technology/), [Thryve](https://www.thryve.health/blog/wearable-data-special-category-gdpr), [Momentum](https://www.themomentum.ai/blog/gdpr-consent-requirements-health-data)
- Edad de consentimiento: [AEPD, infografía](https://www.aepd.es/infografias/infografia-consentimiento-menores.pdf), [INEAF](https://www.ineaf.es/tribuna/rgpd-tratamiento-de-datos-de-menores/)
- LSSI art. 10: [Papelea](https://papelea.com/es/leyes/ley-de-servicios-de-la-sociedad-de-la-informacion-y-comercio-electronico-lssi-ce/titulo-ii_capitulo-ii_seccion-1-obligaciones), [Infoautónomos](https://www.infoautonomos.com/tecnologia-pymes-autonomos/la-ley-de-servicios-de-la-sociedad-de-la-informacion-lssi)
- Cookies: [ECIJA sobre la guía AEPD](https://www.ecija.com/actualidad-insights/guia-aepd-uso-cookies/)
- LSSI art. 21: [AEPD E/02319/2017](https://www.aepd.es/documento/e-02319-2017.pdf)
- Borrado y plazos: [EDPB, decisión BE 2019-05](https://www.edpb.europa.eu/system/files/2021-10/be_2019-05_right_to_erasure_decisionpublic.pdf)
- Google Fonts y RGPD: [The Hacker News](https://thehackernews.com/2022/01/german-court-rules-websites-embedding.html), [Captain Compliance](https://captaincompliance.com/education/google-fonts-and-gdpr-what-the-munich-ruling-requires-and-how-to-self-host-your-way-out-of-exposure/)
- Reseñas (Ómnibus): [Make Influence](https://www.makeinfluence.com/en/academy/fake-and-manipulated-reviews-what-the-eu-omnibus-directive-bans), [Mondaq](https://mondaq.com/dodd-frank-consumer-protection-act/1189542/the-omnibus-directive-consumer-reviews)
- European Accessibility Act: [e-include](https://e-include.eu/web-accessibility/european-accessibility-act/), [Herzog](https://herzoglaw.co.il/en/news-and-insights/the-european-accessibility-act-key-considerations-for-e-commerce-providers/)
- Datos sensibles en el historial de git: [GitHub Docs, Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- Licencias de fuentes: [DM Mono](https://gitee.com/doneself/dm-mono), [Geist](https://fr.github.com/vercel/geist-font), [Instrument Sans](https://fonts.google.com/specimen/Instrument%2BSans/about)

## 4. Sincronización: documentos del RGPD (2026-11-02)

- **EIPD:** `docs/legal/EIPD_SINCRONIZACION.md`, aprobada por Gabriel el 2026-11-02. Riesgo residual bajo o
  medio-bajo; sin consulta previa a la AEPD.
- **Registro de actividades (art. 30) y registro de brechas (art. 33.5):** `docs/legal/REGISTRO_ACTIVIDADES.md`.
- **Encargado:** el DPA v6.4 de Cloudflare forma parte de su Self-Serve Subscription Agreement (§6.1) [Verificado el
  2026-11-02].
- **Plazo:** borrado automático tras 12 meses sin uso (subir o bajar), decidido el 2026-11-02; tarea diaria del Worker.
- **Verificación en dos pasos** en Cloudflare y GitHub: activada, según Gabriel (2026-11-02).
- **Publicada** con la API y la política v2 el 2026-11-02.
