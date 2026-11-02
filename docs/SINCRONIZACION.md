# Cuentas y sincronización (diseño, 2026-10-30)

Estado: **aprobado por Gabriel; pasos 1 a 4 (núcleo, servidor, web y apps) hechos el 2026-11-02, sin publicar**; falta el paso 5 (legal). Ver «Implementación» al final. Revisado y auditado el 2026-10-30 (cifrado, conflictos, RGPD y Cloudflare); las
correcciones de esa revisión ya están incorporadas y se resumen al final. Servidor: Cloudflare Workers + D1
(`server/`, solo la base hecha: sirve la web y `/api/estado`).

## Idea

Sincronizar es **opcional**: la app sigue funcionando sin cuenta, como ahora. No hay email ni contraseña. Al activarla,
el dispositivo genera un **código de sincronización** (secreto aleatorio de 128 bits, 26 caracteres en base32 y QR).
Con ese código se añaden otros dispositivos. El servidor guarda los datos **cifrados de extremo a extremo** y no puede
leerlos.

**Decidido por Gabriel (2026-10-30): sin recuperación por email.** Perder el código y todos los dispositivos significa
perder la copia del servidor; mientras quede un dispositivo, sus datos siguen ahí y desde él se crea un código nuevo.
La app lo tiene que explicar al activar la sincronización.

## Cifrado

- Del secreto salen, con HKDF-SHA256 y etiquetas distintas: la **clave** AES-256-GCM (nunca sale del dispositivo) y el
  **token** de acceso. El servidor guarda solo `SHA-256(token)` como identificador de la cuenta; con 128 bits de
  entropía no hace falta un hash lento.
- Se cifra el JSON entero de `AppData` (`trainsync.data`). Nonce aleatorio de 96 bits en cada subida.
- **Datos asociados (AAD): identificador de la cuenta y versión.** Así el servidor no puede cambiar un bloque por el
  de otra cuenta ni devolver uno viejo como nuevo: cada dispositivo recuerda la última versión que vio y rechaza una
  menor.
- La clave de intervals.icu **no se sincroniza**: se queda en el almacén seguro de cada dispositivo.
- Web: la clave se guarda como `CryptoKey` no extraíble en IndexedDB (auditoría, punto 1), y la CSP estricta del
  build no se relaja (al añadir la API, solo `connect-src 'self'`).
- Revocar un dispositivo concreto no es posible con un secreto compartido: se hace creando un código nuevo, que borra
  la cuenta anterior.
- Implementación: en el núcleo (web, Android e iOS), con las APIs de cada plataforma (WebCrypto, javax.crypto,
  CryptoKit) o con `cryptography-kotlin`; se decide al implementar.

## API (`server/src/index.ts`)

Todas con `Authorization: Bearer <token>`, solo HTTPS.

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/boveda` | Devuelve el bloque cifrado; versión en `ETag` |
| `PUT` | `/api/boveda` | Guarda: `If-Match: <versión>` actualiza, `If-None-Match: *` crea. `409` si otro dispositivo subió antes, `413` si pasa de 1 MB |
| `DELETE` | `/api/boveda` | Borra la cuenta y los datos al momento |

La actualización es atómica en D1: `update … where id = ? and version = ?`; si no cambia ninguna fila, `409`.

```sql
create table bovedas (
  id text primary key,        -- sha256 del token
  version integer not null,
  datos blob not null,        -- cifrado; el servidor no tiene la clave
  actualizado text not null
);
```

- **D1 en la UE:** creada a mano el 2026-11-02 con `npx wrangler d1 create brivel --jurisdiction=eu`, con su
  `database_id` en `server/wrangler.jsonc`. La jurisdicción **no se puede cambiar después**, y garantiza dónde se
  guardan los datos (`location_hint` solo es una preferencia). No dejar que `wrangler deploy` la cree sola.
- **Abuso:** límite por IP con el binding de rate limiting de Workers (periodo de 10 o 60 s). Es aproximado, no
  estricto; lo acotan también el tope de 1 MB por cuenta y el límite de altas por IP.
- Plan gratis: Workers 100.000 peticiones al día; D1 5 GB.

## Conflictos

Si dos dispositivos cambian datos sin conexión, el segundo en subir recibe `409`, descarga, combina y vuelve a subir.

La combinación es **a tres bandas**: cada dispositivo guarda la última copia sincronizada (base) y compara base, local
y servidor. Así se respetan también los **borrados** (desmarcar una sesión hecha, quitar un peso): lo añadido o quitado
en local respecto a la base se aplica sobre la versión del servidor.

- Listas (sesiones hechas, pesos, cambios de semana): se aplican altas y bajas de cada lado.
- Perfil y objetivo: si solo cambió un lado, gana ese; si cambiaron los dos, gana el del servidor y la app avisa.
- No se usan relojes de los dispositivos para decidir (pueden ir mal).
- Va en el núcleo Kotlin, con tests en JVM y JS.

## RGPD (comprobado el 2026-10-30 con fuentes primarias)

- **Responsable del tratamiento:** al guardar datos de usuarios en un servidor propio, Brivel (hoy, Gabriel como
  persona física) es responsable y Cloudflare encargado. Los datos son de salud (art. 9). Mientras todo se queda en el
  dispositivo no hay servidor que trate datos; esto solo aplica a quien active la sincronización.
- **¿Son datos personales si están cifrados?** El TJUE (C-413/23 P, EDPS contra JUR, 2025-09-04) dice que los datos
  seudonimizados pueden no ser personales para quien no tiene medios razonables de reidentificar; el EDPB (directrices
  01/2025) insiste en que seudonimizar no saca los datos del RGPD. Como la IP, las horas y el tamaño de las subidas sí
  son personales, **se trata todo como datos de salud** y no se cuenta con ninguna exención.
- **Base legal:** consentimiento explícito al activar la sincronización (art. 9.2.a), separado de los términos y
  revocable borrando la cuenta. El límite de peticiones por IP, por interés legítimo (art. 6.1.f), explicado en la
  política.
- **Menores:** menos de 14 años no pueden consentir solos (LOPDGDD art. 7). Propuesta (ya en `CUMPLIMIENTO.md` #17): no
  admitir menores de 14; pedir la edad al activar la sincronización.
- **Evaluación de impacto (EIPD): obligatoria en la práctica.** La lista de la AEPD (art. 35.4) pide EIPD «en la
  mayoría de los casos» con dos o más criterios, y aquí se cumplen al menos el 4 (categorías especiales del art. 9; no
  exige gran escala) y el 3 (monitorización sistemática a través de apps: pulso, sueño, entrenos diarios), y
  probablemente el 1 (valoración de hábitos para planificar). Hacerla antes del primer deploy con cuentas; la AEPD
  tiene herramienta gratuita (Gestiona EIPD).
- **Registro de actividades (art. 30):** obligatorio aunque sea una sola persona, porque hay datos del art. 9 (la
  excepción de menos de 250 empleados no aplica). Una tabla sencilla basta.
- **Delegado de protección de datos:** no hace falta mientras no sea a gran escala (art. 37.1.c; LOPDGDD art. 34)
  [Inferencia: revisar si crece mucho].
- **Encargado y transferencias:** aceptar el DPA de Cloudflare (v6.3 o la vigente) en su panel: se acepta, no consta
  que entre solo con la cuenta gratis. Cloudflare transfiere a EE. UU. con el Marco de Privacidad de Datos UE-EE. UU.
  y, si cayera, con cláusulas contractuales tipo. D1 con jurisdicción UE guarda los datos en la UE; los Workers que la
  consultan pueden ejecutarse en cualquier país.
- **Brechas:** registrar cualquier brecha (art. 33.5). Si se filtran bloques cifrados y la clave no, el EDPB (directrices
  9/2022) considera improbable el riesgo y no hace falta avisar a los afectados (art. 34.3.a); a la AEPD se notifica
  en 72 h salvo que el riesgo sea improbable, y esa valoración queda escrita.
- **Información (art. 13):** política de privacidad con responsable y contacto, finalidad, base legal, encargado
  (Cloudflare), transferencias, conservación (hasta borrar la cuenta o 12 meses sin uso → borrado automático, decidido el 2026-11-02)
  y derechos. Esperaba al nombre definitivo (decisión 2026-10-29); ya está: Brivel (2026-10-30).
- **Derechos:** borrar = `DELETE /api/boveda`; exportar = en el propio dispositivo; acceso = el bloque cifrado y sus
  metadatos. Como el servidor no sabe a quién pertenece cada bloque, una petición por email solo se puede atender si la
  persona aporta su código (art. 11.2) [Inferencia].
- **Cookies:** ninguna; `localStorage` e IndexedDB solo para lo estrictamente necesario (art. 22.2 LSSI, guía AEPD).

## Auditoría del diseño (2026-10-30)

Revisado como lo haría un auditor de seguridad. Lo que se añade al diseño:

1. **Web: guardar la clave como `CryptoKey` no extraíble en IndexedDB**, no el código en `localStorage`. Un XSS podría
   usarla mientras la página esté abierta, pero no llevársela. En la web el código no se puede volver a mostrar; se
   muestra desde el móvil (Llavero o Keystore).
2. **Versión dentro del cifrado:** el dispositivo cifra con `versión + 1` en los datos asociados y manda
   `If-Match: versión`; el servidor guarda `versión + 1`. Al descargar, el dispositivo comprueba que la versión del
   bloque es la del `ETag` y no menor que la última vista. Un dispositivo nuevo no tiene versión previa: el primer
   bloque se acepta tal cual.
3. **Formato versionado:** el bloque empieza con un byte de formato, para poder cambiar el cifrado más adelante.
4. **Relleno a múltiplos de 4 KB** antes de cifrar, para que el tamaño no revele cuánto se entrena.
5. **Código con letra de control** (base32 de Crockford, sin letras confusas): un error al teclearlo se detecta antes de
   llamar al servidor.
6. **Cambio de código:** primero se sube con el código nuevo y después se borra la cuenta vieja. Los demás
   dispositivos reciben `404` y piden el código nuevo; nunca borran sus datos locales.
7. **Tamaño:** comprobar `Content-Length` y la longitud real del cuerpo (≤ 1 MB) antes de escribir en D1.
8. **Registros:** el Worker no escribe cabeceras ni cuerpos en los logs (el token iría en ellos).
9. **Sin CORS en `/api`:** la web es del mismo origen y las apps no lo necesitan; así otras webs no pueden usar la API
   desde el navegador.
10. **Límite de peticiones** por IP en todas las rutas y por cuenta en `PUT`.
11. **Lo que el diseño no protege (aceptado):** el servidor puede negarse a servir o borrar bloques (disponibilidad,
    no confidencialidad); un dispositivo nuevo no detecta una versión vieja; quien tenga el código tiene los datos.

## Orden de trabajo

1. Núcleo: cifrado, código, combinación a tres bandas; tests JVM y JS.
2. Worker: las 3 rutas, migración de D1, rate limiting; tests contra `wrangler dev`.
3. Web: «Sincronizar» en Perfil (activar, código y QR, añadir dispositivo, borrar).
4. Android e iOS.
5. Textos legales, EIPD, registro de actividades, DPA de Cloudflare; crear D1 con `--jurisdiction=eu`; deploy.

## Implementación

**Paso 1, núcleo (2026-11-02):** `shared/src/commonMain/kotlin/io/github/mrclusterman/trainsync/sync/`, con tests en
`commonTest/.../sync/` que corren en JVM, JS y, en el CI, iOS.

- **Librería:** `cryptography-kotlin` 0.6.0 con el proveedor `optimal`: JDK en Android y JVM, WebCrypto en la web y
  CryptoKit en iOS (CommonCrypto no tiene AES-GCM). Todo es `suspend`, porque WebCrypto no tiene versión bloqueante.
  Fuente: whyoleg.github.io/cryptography-kotlin (proveedores y operaciones).
- **Código (`SyncCode`):** 16 bytes aleatorios en 26 letras de Crockford más una de control **Luhn mod 32** (27 en
  total). El control de Crockford es mod 37 y usa `* ~ $ = U`, molestos de teclear en el móvil. Luhn mod 32 detecta
  cualquier letra cambiada (comprobado en los tests con todas) y casi todos los cambios de orden entre vecinas. Al leer
  da igual mayúsculas, espacios y guiones; O vale 0 e I o L valen 1.
- **Claves (`deriveKeys`):** HKDF-SHA256 con sal de 32 ceros (la de RFC 5869 sin sal) e `info`
  `brivel-sync-key-v1` (clave AES-256) y `brivel-sync-token-v1` (token de 32 bytes). Cuenta = SHA-256(token) en
  hexadecimal.
- **Bloque (`seal` y `open`):** `01 | nonce de 12 bytes | cifrado | etiqueta de 16 bytes`. Datos asociados:
  `1:<cuenta>:<versión>`. Relleno ISO/IEC 7816-4 (`0x80` y ceros) hasta múltiplos de 4096 bytes.
- **Vectores independientes:** `commonTest/.../sync/Vectors.kt` se generó con `node:crypto` (OpenSSL). En cada
  plataforma se comprueba el código, el token, la cuenta y que se descifra un bloque hecho por Node.
- **Combinar (`merge`):** altas y bajas de listas y conjuntos (sesiones hechas, pesos, actividades renombradas); los
  mapas por día o semana (estado del día, elecciones, cambios de semana) campo a campo; perfil, objetivo (junto con el
  inicio de su bloque), atleta de intervals.icu y hora del recordatorio a tres bandas, y si chocan gana el servidor y
  se devuelve en `conflicts`. Lo descargado (copia de intervals.icu y de Salud) sigue la misma regla, pero sin avisar.
  **Se niega a combinar** datos de una versión más nueva de la app (`schema` mayor): los campos que esta no conoce se
  perderían al volver a subir.
- **Lo que se sube (`syncPayload`): todo menos la copia de intervals.icu (`cache`).** La app descarga 365 días de
  actividades y bienestar con todos sus campos; para quien entrena a diario puede pasar de 1 MB [Inferencia: sin medir
  con datos reales]. Esos datos viven en intervals.icu y cada dispositivo con la clave los vuelve a descargar, y así el
  servidor guarda menos datos de salud (minimización, RGPD art. 5.1.c). La base guardada tampoco la lleva, y `merge`
  conserva la de cada dispositivo. Los datos de Salud (`health`, unos KB) sí se suben: la web no puede leerlos.

**Paso 2, servidor (2026-11-02, probado en local, sin publicar):** `server/src/index.ts`,
`server/migrations/0001_bovedas.sql` y `server/test/boveda.test.mjs` (`npm test`: arranca `wrangler dev` con una D1
local nueva).

- **Token:** `Authorization: Bearer <token>`, con los 32 bytes en base64url sin relleno (43 caracteres;
  `SyncKeys.bearer`). Cuenta = SHA-256 de esos bytes; el token no se guarda.
- **Respuestas:** `401` sin token válido; `GET` → `200` con el bloque y `ETag: "n"`, o `404`; `PUT` con
  `If-None-Match: *` → `201` y `ETag: "1"`, o `409` si ya existe; con `If-Match: "n"` → `200` y `ETag: "n+1"`, o `409`
  si otro subió antes (o la cuenta ya no existe); `428` sin ninguna de las dos; `411` sin `Content-Length`; `413` si
  está vacío o pasa de 1 MB; `DELETE` → `204`; `429` al pasar un límite. Todo con `Cache-Control: no-store` y sin CORS.
- **Límites (binding de rate limiting de Workers):** 60 peticiones por minuto por IP en toda la API, 5 altas por
  minuto por IP y 20 subidas por minuto por cuenta.
- **Publicar:** `npm run deploy` aplica la migración en la D1 real y despliega. No se ha hecho: lo decide Gabriel, y
  conviene hacerlo cuando haya web o app que lo use (pasos 3 y 4) y los requisitos legales del paso 5.

**Paso 3, web (2026-11-02, probado en local con dos navegadores, sin publicar):**

- **Sincronizar (`sync/Syncer.kt`, común a web y apps):** baja la copia, la descifra, combina a tres bandas con la base
  guardada (`trainsync.sync`: cuenta, última versión vista y base) y sube solo si cambió. Ante un `409` vuelve a empezar
  (3 intentos). Rechaza una versión menor que la última vista. **Unirse** con un código junta los datos de aquí con los
  de la copia (base vacía: no se borra nada de ningún lado); **crear** solo con un código nuevo, así un código mal
  tecleado o ya borrado da error en vez de crear otra copia. Si la copia desaparece, avisa y nunca borra los datos del
  dispositivo. «Borrar todo» deja de sincronizar: si no, la siguiente sincronización borraría también la copia.
- **Cifrado tras una interfaz (`Aead`):** en las apps, la librería; en la web, WebCrypto con la clave AES derivada como
  **no exportable** (`web/WebSync.kt`). `web/src/sync.ts` guarda en IndexedDB la clave, el token, la cuenta, la fecha
  del consentimiento y la versión de la política aceptada (CUMPLIMIENTO #6); el código no se guarda. Un test en JS
  comprueba que esta clave y la de la librería se entienden y que no se puede exportar.
- **HTTP tras una interfaz (`SyncTransport`):** ktor en las apps; `fetch` en la web, y promesas con la biblioteca
  estándar en vez de kotlinx.coroutines. Con ktor la web pasaba de 755 a 1.248 kB; así queda en 816 kB.
- **Pantalla (`web/src/Sync.tsx`):** «Usar en otros dispositivos» en Hoy y en el inicio. Activar o unirse pide dos
  casillas sin marcar: consentimiento explícito para guardar una copia cifrada con datos de salud (art. 9.2.a) con
  enlace a la política, y tener 14 años o más. El código se enseña una vez, en grupos de 3. Con la sincronización
  activada: «Sincronizar ahora», «Dejar de sincronizar aquí» y «Borrar la copia del servidor». Cada cambio se sube
  1,5 s después. CSP: `connect-src 'self' https://intervals.icu`.

**Paso 4, apps (2026-11-02, sin publicar):**

- **Núcleo (`TrainSyncApp`):** `createSync`, `joinSync`, `syncDevices`, `forgetSync` y `deleteSync`, contra
  `https://brivel.app/api/boveda`. El código se guarda en el almacén seguro (`sync.code`: Keystore en Android, Llavero
  solo de este dispositivo en iOS), así el móvil puede volver a enseñarlo para añadir otro dispositivo. La fecha y la
  versión de la política aceptadas van en `trainsync.syncConsent`. «Borrar todo» también olvida el código. Textos
  comunes en `Texts.kt` (`SYNC_*`); `PRIVACY_NOTE` ya no dice que no hay copias en la nube.
- **Pantallas:** `SyncCard.kt` (Android) y `SyncSection.swift` (iOS), en Perfil y, solo para unirse, en la bienvenida.
  Mismos pasos que la web; el consentimiento y la edad son casillas (Android) o interruptores (iOS) sin marcar.
- **Cuándo sincroniza:** al abrir la app y 1,5 s después de cada cambio. Sin conexión no avisa (se sube en el siguiente
  cambio); los errores del servidor (copia borrada, versión vieja) sí.
- **Comprobado:** test del núcleo con dos `TrainSyncApp` contra el servidor falso; Android visto en el emulador (Perfil
  y consentimiento). iOS solo se compila en el CI.

**Sin hacer todavía:** QR (basta el código escrito); cambio de código (auditoría 6); y antes de publicar, el paso 5
(política de privacidad con la sincronización, EIPD, registro de actividades).

## Descartado

- Email y contraseña: guarda emails, necesita enviar correos para recuperar, y el servidor leería los datos salvo
  cifrado aparte.
- Passkeys: posible más adelante como comodidad; la extensión PRF (sacar la clave de cifrado de la passkey) aún no
  funciona igual en todos los navegadores.

## Revisión del 2026-10-30: qué cambió

- **Conflictos:** la primera versión juntaba listas sin más, y lo borrado en un dispositivo volvía a aparecer. Ahora
  es a tres bandas.
- **D1:** la primera versión usaba `location_hint: "weur"` y dejaba que el deploy creara la base. Ahora
  `--jurisdiction=eu`, creada a mano antes del primer deploy.
- **Cifrado:** añadidos los datos asociados (cuenta y versión) contra cambios y vueltas atrás del servidor.
- **Rate limiting:** es aproximado; se suma el tope de tamaño y de altas.
- **RGPD:** añadidos menores de 14, EIPD y que el DPA de Cloudflare hay que aceptarlo.

Fuentes: [D1 jurisdicción](https://developers.cloudflare.com/changelog/post/2025-11-05-d1-jurisdiction/index.md),
[rate limiting](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/),
[DPA de Cloudflare](https://www.cloudflare.com/dpa),
[TJUE C-413/23 P](https://www.garrigues.com/en_GB/new/pseudonymized-data-personal-data-key-points-following-european-court-justices-judgment-edps-v),
[AEPD, lista art. 35.4](https://www.aepd.es/documento/listas-dpia-es-35-4.pdf),
[EDPB 01/2025 seudonimización](https://www.edpb.europa.eu/our-work-tools/documents/public-consultations/2025/guidelines-012025-pseudonymisation_es),
[EDPB 9/2022 brechas](https://www.edpb.europa.eu/system/files/2024-10/edpb_guidelines_202209_personal_data_breach_notification_v2.0_fr_0.pdf),
[RGPD art. 30](https://gdpr-text.com/es/read/article-30), [RGPD art. 34](https://gdpr-text.com/es/read/article-34),
[Cloudflare y RGPD](https://www.cloudflare.com/trust-hub/gdpr/).
