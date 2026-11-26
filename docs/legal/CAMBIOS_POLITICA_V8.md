# Política de privacidad v8 y revisión de la EIPD de sincronización: cambios propuestos (pendiente de aprobar)

Borrador del 2026-11-19 para Gabriel. No se publica nada hasta que lo apruebe; al aprobarlo, se redacta en
`web/public/privacidad.html` (también el resumen en inglés), se sube `PRIVACY_POLICY_VERSION` y las apps piden aceptarla.

## Qué cambia en el tratamiento (rama `ciclo-12`)

1. **Entrar con Google o Apple** (opcional). Brivel guarda: un identificador derivado de la cuenta (HMAC; ni correo ni
   nombre, que no se piden), la clave de la copia cifrada con una clave del servidor y, con Apple, un token para
   revocar el acceso al borrar la cuenta. **Para quien entra así, Brivel podría leer su copia**: deja de ser de extremo
   a extremo. Con el código, todo sigue igual.
2. **La copia lleva también la copia de intervals.icu** (hasta 365 días de actividades y bienestar con todos sus
   campos) y **todas las actividades propias**, no solo las 400 más recientes. Las series y los recorridos siguen sin
   subirse.
3. **Sin el tope de 1 MB**: ahora 16 MB por copia, solo contra abusos.
4. **Pedido por Gabriel (pendiente de hacer): los archivos FIT y GPX** —las series y el **recorrido con coordenadas**
   de cada actividad— también se guardarían en el servidor, cifrados, para verlos en otros dispositivos. Hoy la política
   dice que el recorrido no sale del dispositivo: hay que cambiarlo y valorar el riesgo (el recorrido revela dónde vives
   o entrenas), sobre todo para quien entra con Google o Apple.

## Texto que cambia en la política

- **Apartado 4 (sincronización):** dos formas de usarla. *Con el código*: igual que ahora. *Con Google o Apple*: qué
  se guarda (punto 1), que Brivel tiene la clave y podría leer la copia, con qué medidas (claves fuera de la base de
  datos, acceso solo del responsable, sin registros de cuerpos) y que Google o Apple saben que usas Brivel (son
  responsables de su inicio de sesión, con sus propias políticas). Base jurídica: consentimiento explícito (RGPD
  art. 6.1.a y 9.2.a), con la casilla sin marcar. Retirada: «Dejar de entrar con Google» o borrar la copia.
- **Lo que se sube:** quitar «salvo… la copia de intervals.icu» y el límite de 400 actividades.
- **Plazos:** la cuenta se borra con la copia, al dejar de entrar con ella o a los 12 meses sin uso, como la copia.
- **Apartado 5 (Garmin):** para quien entra con Google o Apple, la clave privada del relevo viaja en una copia que
  Brivel podría leer.
- **Seguridad:** «si se filtraran copias cifradas sin sus claves, nadie podría leerlas» sigue valiendo para la base de
  datos; añadir que, con Google o Apple, la clave está en el servidor, cifrada con otra que no está en la base.
- **Destinatarios:** Google (Irlanda) o Apple (Irlanda) solo para el inicio de sesión; no reciben datos de Brivel.
- **Inglés:** el mismo resumen.

## EIPD de sincronización (revisión)

- **Riesgo nuevo:** acceso del responsable o filtración del Worker con las claves → datos de salud legibles de quien
  entra con Google o Apple. Antes el riesgo era bajo porque el servidor no podía leer nada.
- **Medidas:** claves como secretos del Worker, fuera de D1; HMAC sin correo; billete de un solo uso de 2 minutos;
  cookie `__Host-` con `state` y PKCE; sin registros de cuerpos ni cabeceras; borrado y revocación en Apple; la opción
  del código sigue para quien no lo quiera; consentimiento explícito aparte del del código.
- **Riesgo residual propuesto:** medio para quien entra con Google o Apple (antes bajo); bajo con el código.
- **Minimización:** subir la copia de intervals.icu se justifica por la finalidad (que un dispositivo sin conectar a
  intervals.icu vea lo mismo); desconectar intervals.icu la borra de la copia.

## Garmin

Los avisos de 30 días hablan de pantallas, no del almacenamiento; Cloudflare sigue siendo el único encargado. Google y
Apple no reciben datos de Garmin. [Inferencia: no hace falta otro aviso.]
