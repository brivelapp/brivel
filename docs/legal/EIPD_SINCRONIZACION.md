# Evaluación de impacto (EIPD): sincronización entre dispositivos

**Estado:** aprobada por Gabriel, responsable del tratamiento, el 2026-11-02. Preparada por Claude a partir de
`docs/SINCRONIZACION.md` y del código. Se puede pasar a la herramienta gratuita de la AEPD, Gestiona EIPD, que sigue
los mismos apartados.

**Revisar** cada vez que cambie lo que se sube, el cifrado o el proveedor (por ejemplo: OAuth de intervals.icu, QR o
cambio de código).

## 1. Por qué hace falta

La lista de la AEPD del art. 35.4 del RGPD pide EIPD «en la mayoría de los casos» cuando se cumplen dos o más criterios.
Aquí se cumplen al menos dos:

- **Criterio 4:** categorías especiales de datos (art. 9): datos de salud (variabilidad cardiaca, pulso, sueño, peso,
  entrenamientos).
- **Criterio 3:** observación o monitorización sistemática a través de apps (datos diarios de salud y de actividad).
- **Probablemente el criterio 1:** el plan de entrenamiento se calcula a partir de hábitos y del estado de salud
  [Inferencia: el cálculo es en el dispositivo, pero los datos que lo alimentan se sincronizan].

Fuente: AEPD, «Listas de tipos de tratamientos de datos que requieren EIPD (art. 35.4)», consultada el 2026-10-30.

## 2. Descripción del tratamiento

| | |
|---|---|
| **Responsable** | Gabriel Miralles Alvarez (persona física), `hola@brivel.app`. Proyecto sin ingresos |
| **Encargado** | Cloudflare, Inc. (Workers y D1), con su Anexo de Tratamiento de Datos v6.4, incorporado a su Self-Serve Subscription Agreement (§6.1) |
| **Finalidad** | Que el usuario tenga los mismos datos en sus dispositivos (web, Android, iOS) |
| **Base legal** | Consentimiento explícito, aparte de las condiciones (arts. 6.1.a y 9.2.a). El límite de peticiones por IP, por interés legítimo (art. 6.1.f) |
| **Interesados** | Usuarios de Brivel de 14 años o más que activan la sincronización. Es opcional |
| **Datos** | Perfil (año de nacimiento, nivel, deportes, días), objetivo, sesiones hechas, pesos levantados, respuestas sobre sueño, energía y agujetas, datos de Apple Salud o Health Connect si están conectados, ID de atleta de intervals.icu. **No** se suben la clave de intervals.icu ni su copia de actividades |
| **Metadatos en el servidor** | Identificador de la copia (SHA-256 del token, sin nombre ni email), versión, fecha del último uso, tamaño relleno a múltiplos de 4 KB. La IP se usa para el límite de peticiones (contadores de 60 s de Cloudflare) y no se guarda en la base de datos |
| **Dónde** | D1 con jurisdicción UE (`jurisdiction: eu`, región EEUR), creada el 2026-11-02. Los Workers pueden ejecutarse fuera de la UE |
| **Transferencias** | A EE. UU. (Cloudflare, Inc.): Marco de Privacidad de Datos UE-EE. UU. y, si cae, cláusulas contractuales tipo (módulos 2 y 3; DPA §6.2 y §6.4) |
| **Plazo** | Hasta que el usuario borre la copia (al momento) o tras 12 meses sin que ningún dispositivo la suba ni la baje (tarea diaria del Worker). El historial de recuperación de D1 (Time Travel) la conserva hasta 7 días más con el plan gratuito |

### Ciclo de vida

1. El dispositivo genera un código aleatorio de 128 bits y lo enseña al usuario. En las apps se guarda en Keystore o en el
   Llavero. En la web no se guarda: se guarda una clave no exportable en IndexedDB.
2. Del código salen, con HKDF-SHA256, la clave AES-256-GCM (no sale del dispositivo) y el token de acceso.
3. El dispositivo cifra los datos, con la cuenta y la versión como datos asociados, y los sube a `/api/boveda`.
4. Otro dispositivo con el mismo código baja la copia, la descifra, la combina con sus datos y sube el resultado.
5. Borrar la copia la elimina de D1 al momento. Los datos de cada dispositivo siguen en él.

## 3. Necesidad y proporcionalidad

- **Opcional:** la app funciona igual sin sincronizar.
- **Minimización:** sin email, sin contraseña y sin nombre; identificador seudónimo; la copia de intervals.icu no se
  sube porque cada dispositivo la vuelve a descargar.
- **Cifrado de extremo a extremo:** el responsable y el encargado no pueden leer los datos.
- **Información:** política de privacidad, versión 2026-11-02.2 (`web/public/privacidad.html`), enlazada desde la
  casilla del consentimiento.
- **Consentimiento:** casillas sin marcar, separadas de las condiciones, con la edad mínima. Se guarda la fecha y la
  versión de la política en el dispositivo (CUMPLIMIENTO #6).
- **Derechos:** borrar y retirar el consentimiento desde cualquier dispositivo, al momento. Acceso y portabilidad: los
  datos están en el dispositivo (exportar a JSON en las apps). Sin el código, el responsable no puede saber qué copia
  es de quién (art. 11).
- **Plazos:** hasta que se borre o tras 12 meses sin uso (tarea diaria del Worker). Así no se guardan copias olvidadas
  de quien dejó de usar Brivel.

## 4. Riesgos y medidas

Probabilidad e impacto: bajo, medio o alto. «Inherente» es el riesgo sin medidas; «residual», con las medidas que ya
están en el código.

| # | Amenaza | Inherente | Medidas | Residual |
|---|---|---|---|---|
| R1 | Acceso a la base de datos (brecha en Cloudflare o en la cuenta del responsable) | Alto | Cifrado de extremo a extremo; claves solo en los dispositivos; identificador seudónimo | Bajo |
| R2 | **Cuenta del responsable comprometida** (Cloudflare o GitHub): alguien publica una web modificada que roba claves o datos descifrados | Alto | Código abierto y auditable; despliegue manual por el responsable; CSP estricta; verificación en dos pasos en Cloudflare y GitHub (confirmada por Gabriel el 2026-11-02) | Bajo |
| R3 | XSS en la web: un script usa la clave mientras la página está abierta | Medio | CSP `script-src 'self'`, sin scripts de terceros; clave no exportable (no se la pueden llevar); el código no se guarda | Bajo |
| R4 | El servidor devuelve una copia vieja o de otra cuenta | Medio | Cuenta y versión en los datos asociados; el dispositivo rechaza una versión menor que la última vista | Bajo (no protege la disponibilidad) |
| R5 | Pérdida de datos al combinar o borrar | Medio | Combinación a tres bandas con tests; nunca se borran los datos del dispositivo; «Borrar todo» deja de sincronizar para no propagar el borrado | Bajo |
| R6 | Robo o pérdida del código por el usuario | Medio | Almacén seguro; el código solo se enseña a demanda; el usuario puede borrar la copia. **Sin hacer:** cambiar el código (auditoría 6) | Medio-bajo |
| R7 | Los metadatos revelan hábitos (horas de subida, tamaño) | Bajo | Relleno a 4 KB; la IP no se guarda; sin Workers Logs ni analítica | Bajo |
| R8 | Menores de 14 años | Medio | Declaración de edad obligatoria al activar | Medio-bajo (no se puede verificar) |
| R9 | Transferencias fuera de la UE | Medio | D1 en la UE; Marco de Privacidad UE-EE. UU. y cláusulas tipo; los datos van cifrados | Bajo |
| R10 | Pérdida del código y de todos los dispositivos | Medio | Avisado antes de activar; la confidencialidad no se ve afectada | Aceptado |
| R11 | Caída del servicio o fin del plan gratuito | Bajo | Los datos siguen en cada dispositivo; la app funciona sin servidor | Bajo |

## 5. Conclusión

Con las medidas descritas, el riesgo residual es **bajo o medio-bajo** en todos los casos. No hace falta la consulta
previa a la AEPD (art. 36), que es para riesgo residual alto [Inferencia: según el art. 36.1].
