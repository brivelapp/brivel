# Evaluación de impacto (EIPD): conexión con Garmin Connect

**Estado:** borrador del 2026-11-04, **pendiente de aprobación por Gabriel**, responsable del tratamiento. Preparado por
Claude a partir del apartado 5 de la política de privacidad (`web/public/privacidad.html`) y del código del relevo
(repositorio privado `brivel-garmin`). Completa la EIPD de la sincronización (`EIPD_SINCRONIZACION.md`), que sigue
vigente para la copia cifrada. Se puede pasar a Gestiona EIPD de la AEPD, que sigue los mismos apartados.

Este documento está en un repositorio público: no cita la documentación de Garmin, que es confidencial.

**Revisar** cada vez que cambie lo que llega de Garmin (permisos nuevos), el cifrado, el plazo, el proveedor o lo que se
manda al reloj, y antes de abrir la conexión a otras personas (no antes del 2026-12-04, por el aviso de 30 días a
Garmin).

## 1. Por qué hace falta

Se cumplen al menos dos criterios de la lista de la AEPD (art. 35.4 del RGPD), igual que en la sincronización:

- **Categorías especiales de datos (art. 9):** actividades con pulso, ritmo y potencia; pulso en reposo, estrés, sueño y
  variabilidad de la frecuencia cardiaca.
- **Observación sistemática a través de dispositivos y apps:** el reloj manda datos cada día, sin que el usuario haga
  nada.
- **Probablemente, combinación de datos de responsables distintos:** los de Garmin se juntan con los de Apple Salud o
  Health Connect y con los de Brivel para adaptar el plan [Inferencia: la combinación ocurre en el dispositivo].

Fuente: AEPD, «Listas de tipos de tratamientos de datos que requieren EIPD (art. 35.4)», consultada el 2026-10-30.

## 2. Descripción del tratamiento

| | |
|---|---|
| **Responsable** | Gabriel Miralles Alvarez (persona física), `hola@brivel.app`. Proyecto sin ingresos |
| **Encargado** | Cloudflare, Inc. (Workers y D1), con su Anexo de Tratamiento de Datos v6.4 |
| **Otros responsables** | Garmin trata los datos en Garmin Connect como responsable independiente, según su política. Brivel recibe los datos por su programa de desarrolladores (Health API y Training API) |
| **Finalidad** | Enseñar al usuario sus propios datos de Garmin, adaptar su propio plan y mandar a su reloj las sesiones que planifique. Nada más: ni publicidad, ni perfiles, ni entrenar o evaluar modelos de IA |
| **Base legal** | Consentimiento explícito, aparte de las condiciones (arts. 6.1.a y 9.2.a), que se da al conectar en Perfil y en la pantalla de permisos de Garmin. Se guarda la fecha y la versión (`2026-11-03.4`). Límite de peticiones por IP: interés legítimo (art. 6.1.f) |
| **Interesados** | Usuarios de 14 años o más que conectan Garmin. Es opcional. Hoy, solo Gabriel |
| **Datos que llegan** | Los que el usuario permita en Garmin: actividades (deporte, fecha, duración, distancia, pulso, ritmo, potencia, vueltas, modelo del reloj), resúmenes diarios (pasos, pulso en reposo, estrés), sueño y variabilidad cardiaca |
| **Datos que salen** | Sesiones mandadas al reloj: nombre, deporte, fecha, pasos y objetivos de cada paso. El servidor no las guarda |
| **Qué guarda el servidor** | Identificador de usuario de Garmin (sin nombre ni email); tokens de Garmin cifrados con una clave del servidor (AES-256-GCM); clave pública X25519 del dispositivo; SHA-256 del token del buzón; permisos dados; fechas de alta y caducidad; envíos cifrados para el dispositivo con su fecha y tamaño. Durante los 15 minutos de la conexión: el `state`, el verificador PKCE y la clave pública |
| **Dónde** | D1 `brivel-garmin` con jurisdicción UE, creada el 2026-11-03. Los Workers pueden ejecutarse fuera de la UE |
| **Transferencias** | A EE. UU. (Cloudflare, Inc.): Marco de Privacidad de Datos UE-EE. UU. y, si cae, cláusulas contractuales tipo. Garmin manda los datos desde sus servidores [Inferencia: la transferencia de Garmin a Brivel la cubre la política de Garmin, que el usuario acepta en su app] |
| **Plazo** | Cada envío, hasta que un dispositivo lo descarga y lo confirma, o 30 días (tarea diaria). Tokens y conexión, hasta desconectar desde Brivel o desde Garmin Connect (al momento). El historial de D1 (Time Travel) los conserva hasta 7 días más. En el dispositivo, como el resto de sus datos |

### Ciclo de vida

1. El usuario acepta en Perfil la casilla del consentimiento (sin marcar, aparte de las condiciones, con la edad mínima).
2. El dispositivo crea un par de claves X25519 y manda solo la pública al relevo, que le devuelve la dirección de Garmin
   (OAuth 2.0 con PKCE) y un `state` aleatorio.
3. El usuario elige los permisos en Garmin. El relevo canjea el código (el secreto de Brivel solo está en el servidor),
   guarda los tokens cifrados y crea un buzón. El dispositivo recoge el token del buzón una sola vez con su `state`.
4. Garmin empuja los datos al relevo. El relevo responde enseguida y, en memoria, convierte el envío al formato de
   Brivel y lo cifra con la clave pública del dispositivo (X25519 con clave efímera, HKDF-SHA256, AES-256-GCM). Solo
   guarda el resultado cifrado. Lo que llega de usuarios no conectados se descarta.
5. El dispositivo descarga su buzón al abrir la app o al volver a ella, descifra y confirma lo descargado, que se borra.
6. Con la sincronización activa, la clave privada y el token del buzón viajan dentro de la copia cifrada, así que
   cualquier dispositivo del usuario puede descargar. No salen al exportar.
7. Al desconectar (Perfil, desde cualquier dispositivo, o quitando el permiso en Garmin Connect), el relevo avisa a
   Garmin y borra la conexión, los tokens y los envíos pendientes.

## 3. Necesidad y proporcionalidad

- **Opcional:** la app funciona igual sin Garmin, con Apple Salud, Health Connect o intervals.icu, o sin ninguno.
- **Necesidad del servidor:** Garmin solo entrega los datos a un servidor, no al móvil. El relevo es el mínimo que lo
  permite: no calcula nada con ellos.
- **Minimización:** sin nombre ni email; el identificador de Garmin es seudónimo para Brivel. El usuario elige los
  permisos en Garmin y los puede cambiar. Se descarta lo que llega de quien no está conectado. Las sesiones al reloj no
  se guardan.
- **Cifrado para el dispositivo:** Brivel y Cloudflare no pueden leer los envíos guardados.
- **Información:** política de privacidad v4, apartado 5, enlazada desde la casilla del consentimiento. Atribución de
  Garmin en cada dato («Garmin [modelo]»), como piden sus normas de marca.
- **Derechos:** desconectar borra todo lo del servidor al momento. Acceso, portabilidad y supresión de lo descargado:
  en el dispositivo (exportar y borrar). Sin el dispositivo, el responsable no puede leer los envíos.
- **Plazos:** cortos. Un envío vive horas (hasta que se abre la app) o, como mucho, 30 días.

## 4. Riesgos y medidas

Probabilidad e impacto: bajo, medio o alto. «Inherente» es el riesgo sin medidas; «residual», con las medidas que ya
están en el código.

| # | Amenaza | Inherente | Medidas | Residual |
|---|---|---|---|---|
| G1 | Acceso a la base de datos del relevo (brecha en Cloudflare o en la cuenta del responsable) | Alto | Envíos cifrados para el dispositivo; tokens cifrados con una clave que no está en la base de datos; token del buzón guardado como SHA-256; sin nombre ni email | Bajo |
| G2 | **Cuenta de Cloudflare del responsable comprometida:** quien la controla puede cambiar el relevo para guardar en claro lo que llega o usar los tokens para pedir datos a Garmin | Alto | Verificación en dos pasos en Cloudflare y GitHub (2026-11-02); despliegue manual por el responsable; repositorio privado; ningún token en el código | Medio-bajo. A diferencia de la sincronización, el servidor ve los datos en claro un instante al llegar: el cifrado no protege de un relevo modificado |
| G3 | Los datos se ven en claro en el servidor mientras se cifran | Medio | Solo en memoria, sin guardar antes; sin registrar cabeceras ni cuerpos; sin Workers Logs ni analítica | Bajo |
| G4 | Envíos falsos que no vienen de Garmin (meter datos falsos en el plan de alguien) | Medio | Solo se aceptan desde las direcciones de Garmin y con el Client ID de Brivel; límite de tamaño; lo que no es de un usuario conectado se descarta | Bajo |
| G5 | Robo del token del buzón (descargar los envíos de otro) | Medio | Token aleatorio de 256 bits, entregado una sola vez y guardado como SHA-256; aun con él, los envíos van cifrados para la clave privada del dispositivo | Bajo |
| G6 | Robo de la clave privada o del token en el dispositivo, o en la copia sincronizada | Medio | Almacén seguro del sistema; la copia sincronizada va cifrada de extremo a extremo; no salen al exportar; desconectar los invalida para todos los dispositivos | Medio-bajo |
| G7 | Tokens de Garmin robados del servidor | Alto | Cifrados con `GARMIN_TOKEN_KEY` (secreto del Worker, fuera de la base de datos y del código); el de acceso caduca pronto y Garmin rota el de refresco; se borran al desconectar | Bajo (salvo G2) |
| G8 | Conservación de más (envíos olvidados, conexiones abandonadas) | Medio | Borrado al descargar; tarea diaria que borra lo de más de 30 días y lo pendiente de más de 15 minutos; Garmin avisa al quitar el permiso y se borra al momento. Sin hacer: borrar las conexiones sin descargas en mucho tiempo [Inferencia: hoy caducan solas si el token de refresco no se renueva] | Bajo |
| G9 | Los metadatos revelan hábitos (hora y tamaño de cada envío) | Bajo | Solo fecha y tamaño, sin contenido legible; vida corta | Bajo |
| G10 | Garmin cambia o retira el acceso | Bajo | Lo descargado sigue en el dispositivo; la app funciona sin Garmin | Bajo |
| G11 | Uso para fines que no son del usuario (publicidad, IA, venta) | Medio | Prohibido por la política y por el acuerdo con Garmin; el responsable no puede leer los envíos | Bajo |
| G12 | Menores de 14 años | Medio | Declaración de edad al conectar | Medio-bajo (no se puede verificar) |
| G13 | Transferencias fuera de la UE | Medio | D1 en la UE; Marco de Privacidad UE-EE. UU. y cláusulas tipo; datos guardados cifrados | Bajo |
| G14 | Una sesión mal formada llega al reloj (riesgo físico al entrenar) | Bajo | Los pasos salen del mismo plan que ve el usuario en Hoy; el reloj enseña la sesión antes de empezar; aviso de salud en el primer uso | Bajo |

## 5. Conclusión

Con las medidas descritas, el riesgo residual es **bajo o medio-bajo** en todos los casos. El mayor es G2: el relevo ve
los datos un instante al llegar, así que la seguridad de la cuenta de Cloudflare del responsable es la medida clave. No
hace falta la consulta previa a la AEPD (art. 36), que es para riesgo residual alto [Inferencia: según el art. 36.1].

Antes de abrir la conexión a otras personas: aprobar esta EIPD, tener respuesta de Garmin al aviso de 30 días, que
`hola@brivel.app` reciba correo y probarla en un iPhone y un Android reales.
