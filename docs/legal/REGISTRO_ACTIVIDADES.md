# Registro de actividades de tratamiento (RGPD art. 30)

Obligatorio aunque Brivel sea de una sola persona, porque hay datos de salud (art. 30.5: la excepción de menos de 250
empleados no aplica a las categorías especiales). Actualizado el 2026-11-04.

**Responsable:** Gabriel Miralles Alvarez (persona física). Contacto: `hola@brivel.app`. No hay delegado de protección
de datos: no es obligatorio mientras no sea a gran escala (art. 37.1.c; LOPDGDD art. 34) [Inferencia].

## 1. Sincronización entre dispositivos (opcional)

| Campo (art. 30.1) | |
|---|---|
| Fines | Que el usuario tenga los mismos datos en sus dispositivos |
| Base legal | Consentimiento explícito (arts. 6.1.a y 9.2.a); límite de peticiones por IP: interés legítimo (art. 6.1.f) |
| Interesados | Usuarios de 14 años o más que la activan |
| Datos | Copia cifrada de extremo a extremo del perfil, objetivo, sesiones, pesos, respuestas de cómo está y datos de Salud; identificador seudónimo; versión, fecha del último uso y tamaño; IP solo en contadores de 60 s |
| Destinatarios | Cloudflare, Inc. como encargado (DPA v6.4). Nadie más |
| Transferencias | EE. UU.: Marco de Privacidad UE-EE. UU.; si cae, cláusulas contractuales tipo. D1 con jurisdicción UE |
| Plazo de supresión | Hasta que el usuario la borre o tras 12 meses sin subirla ni bajarla (borrado automático diario); historial de D1 hasta 7 días más |
| Seguridad (art. 32) | AES-256-GCM en el dispositivo, claves solo en los dispositivos, HKDF-SHA256, datos asociados con cuenta y versión, relleno a 4 KB, HTTPS, CSP estricta, límites de peticiones, sin registros de cabeceras ni cuerpos. Detalle en `docs/SINCRONIZACION.md` y en `docs/legal/EIPD_SINCRONIZACION.md` |

## 2. Servir la web (brivel.app)

| Campo (art. 30.1) | |
|---|---|
| Fines | Entregar la web y protegerla de ataques y abusos |
| Base legal | Interés legítimo (art. 6.1.f) |
| Interesados | Visitantes de brivel.app |
| Datos | IP, fecha y hora, dirección pedida, datos del navegador |
| Destinatarios | Cloudflare, Inc. como encargado |
| Transferencias | EE. UU.: Marco de Privacidad UE-EE. UU.; si cae, cláusulas contractuales tipo |
| Plazo de supresión | Brivel no guarda estos datos (sin Workers Logs ni analítica); Cloudflare los trata el tiempo necesario según su DPA |
| Seguridad | HTTPS (dominio .app con HSTS precargado), CSP estricta |

## 3. Correo de contacto y ejercicio de derechos

| Campo (art. 30.1) | |
|---|---|
| Fines | Responder consultas y atender los derechos del RGPD |
| Base legal | Interés legítimo en responder (art. 6.1.f); cumplir la ley al atender derechos (art. 6.1.c) |
| Interesados | Quien escribe a `hola@brivel.app` |
| Datos | Dirección de correo y contenido del mensaje |
| Destinatarios | Cloudflare (Email Routing reenvía el correo) y Google (buzón de Gmail del responsable) |
| Transferencias | EE. UU. (Cloudflare y Google): Marco de Privacidad UE-EE. UU. [Inferencia: comprobar la certificación de Google en dataprivacyframework.gov] |
| Plazo de supresión | Hasta resolver la petición y, si es un derecho del RGPD, el tiempo de posibles reclamaciones |
| Seguridad | Cuenta de Gmail con verificación en dos pasos [Pendiente de confirmar por Gabriel] |

## 4. Conexión con Garmin Connect (opcional)

| Campo (art. 30.1) | |
|---|---|
| Fines | Enseñar al usuario sus datos de Garmin, adaptar su plan y mandar sesiones a su reloj |
| Base legal | Consentimiento explícito (arts. 6.1.a y 9.2.a); límite de peticiones por IP: interés legítimo (art. 6.1.f) |
| Interesados | Usuarios de 14 años o más que conectan Garmin |
| Datos | Actividades, resúmenes diarios, sueño y variabilidad cardiaca (los que permita el usuario), guardados cifrados para su dispositivo; identificador de Garmin; tokens cifrados; clave pública del dispositivo; permisos; fechas y tamaños. Sesiones al reloj: solo de paso |
| Destinatarios | Cloudflare, Inc. como encargado (DPA v6.4). Garmin recibe las sesiones al reloj y el aviso de desconexión, como responsable independiente |
| Transferencias | EE. UU.: Marco de Privacidad UE-EE. UU.; si cae, cláusulas contractuales tipo. D1 con jurisdicción UE |
| Plazo de supresión | Envíos: al descargarlos o a los 30 días. Conexión y tokens: al desconectar. Historial de D1 hasta 7 días más |
| Seguridad (art. 32) | X25519 + HKDF-SHA256 + AES-256-GCM para el dispositivo al llegar, tokens con AES-256-GCM y clave fuera de la base de datos, token del buzón como SHA-256, OAuth 2.0 con PKCE, solo envíos desde Garmin, sin registros. Detalle en `docs/legal/EIPD_GARMIN.md` |

## Registro de brechas (art. 33.5)

Anotar **cualquier** brecha, aunque no haya que notificarla: qué pasó, qué datos, a cuántas personas afecta, qué se
hizo y la valoración del riesgo. A la AEPD se notifica en 72 h salvo que el riesgo sea improbable; si solo se filtran
copias cifradas sin sus claves, el riesgo se considera improbable (EDPB, directrices 9/2022) y esa valoración se deja
escrita aquí.

| Fecha | Qué pasó | Datos y personas | Medidas | Valoración | ¿Notificada? |
|---|---|---|---|---|---|
| | | | | | |
