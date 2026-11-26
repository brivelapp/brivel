# Para agentes (Claude, Cursor, Codex…)

Corto a propósito: lo que se puede comprobar solo, se comprueba en el código y en el CI, no aquí.

- **Qué es Brivel y hacia dónde va:** `docs/VISION.md` manda. Motor: `docs/MOTOR_ENTRENAMIENTO.md`; diseño: `docs/DISENO_PULSO.md`.
- **Dónde está cada pantalla:** web en `web/e2e/mapa.ts`; Android al principio de
  `androidApp/src/test/kotlin/app/brivel/android/PantallasTest.kt` (nombre, archivo y cómo llegar).
- **Antes de decir que algo está hecho**, si se ve en la web: skill `.claude/skills/verificar-brivel`
  (`scripts/verificar-web.sh`) y mirar las capturas. Se entrega con pruebas, no con un resumen del diff.
- **Reglas:** `scripts/reglas.mjs` y `web/e2e/verificar.spec.ts` fallan con el porqué y cómo arreglarlo. Si te
  corrigen algo que podría volver a pasar, conviértelo en una regla o una prueba ahí, no en una línea de este archivo.
- **El motor:** sus números no cambian sin que pasen la validación y los goldens (`./gradlew :shared:jvmTest`).
- **Ramas:** una por cambio; a `master` solo con el CI en verde. Nunca `git commit -a`: añade los archivos por nombre.
- **Fuera del repo:** desplegar, publicar versiones, subir a `master` o escribir a terceros solo cuando el dueño lo
  pide en ese momento.
