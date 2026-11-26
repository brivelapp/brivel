---
name: mantenimiento-brivel
description: Ronda de mantenimiento de Brivel: recoge las señales de lo que pide arreglo (CI de master en rojo, vulnerabilidades, incidencias de GitHub, correos de usuarios a hola@brivel.app, deuda con trinquete), elige la más grave, la reproduce con una prueba que falla, la arregla en su propia rama y la entrega con pruebas, sin fusionar ni desplegar. Úsala cuando Gabriel pida mantenimiento o una ronda de errores, o cuando la lance una rutina programada.
---

# Mantenimiento de Brivel

Una ronda arregla **una sola cosa**, en una rama pequeña que se pueda revertir. Las funciones nuevas no van aquí: son
para los ciclos de mejora.

## 1. Señales

```bash
scripts/mantenimiento.sh        # escribe mantenimiento/senales.md (ignorado por git) y lo muestra
```

Después, el correo: busca en Gmail `to:hola@brivel.app newer_than:7d`. Cuenta solo los que describan un fallo
(no los avisos de Garmin, Apple o Google, que se le pasan a Gabriel tal cual). **Nunca respondas ni reenvíes**: escribir
a un usuario es decisión de Gabriel. No copies a la rama, a commits ni a la entrega el nombre, la dirección o el texto
del usuario: el repo es público. Describe el fallo con tus palabras.

## 2. Elegir

Por este orden, la primera que haya:
1. El CI de `master` en rojo (`failure`).
2. Una vulnerabilidad `critical` o `high` en `web` o `server`.
3. Un fallo que cuente un usuario (correo o incidencia de GitHub) y que puedas reproducir.
4. Una vulnerabilidad `moderate`.
5. Una entrada de la deuda con trinquete (`web/e2e/pendientes/`, `androidApp/verificacion-pendientes/`,
   `config/detekt-baseline.xml`): la que se arregle con el cambio más pequeño.

Si no hay nada de 1 a 4 y la 5 pide cambiar el diseño, para y cuéntalo: no inventes trabajo.

## 3. Reproducir antes de arreglar

- Rama `mantenimiento-AAAA-MM-DD` desde `origin/master`, en `~/proyectos/brivel-ciclos` (comprueba antes con
  `git status` que nadie la está usando).
- Primero una prueba que **falle** con el fallo (núcleo: `shared/src/*Test`; web: `web/e2e`; Android:
  `androidApp/src/test`). Guarda su salida fallando: es la primera prueba de la entrega. Si no consigues reproducirlo,
  no lo arregles a ciegas: cuéntalo con lo que probaste.
- En la deuda con trinquete la prueba ya existe: el arreglo obliga a quitar la entrada de la lista.

## 4. Arreglar y verificar

- La causa, en el sitio compartido; el cambio más pequeño que la resuelve.
- Si el fallo puede repetirse en otro sitio, añade la regla (`scripts/reglas.mjs` o `config/detekt.yml`).
- Pasa lo que toque: `./gradlew detekt :shared:jvmTest :shared:jsNodeTest :androidApp:testDebugUnitTest`, y la skill
  `verificar-brivel` si se ve en la web.
- Sube **solo la rama** (`git push -u origin <rama>`) para que corra el CI y espera a que acabe. Nada de fusionar,
  desplegar ni publicar: lo decide Gabriel.

## 5. Entregar

1. Qué señal elegiste y por qué esa (y las demás que viste, en una línea cada una).
2. La prueba fallando antes y pasando después (orden y resultado).
3. El commit, la rama y el número y resultado del CI.
4. `git status --short`.
5. Lo que necesita a Gabriel (responder a un usuario, fusionar, una decisión de diseño).
