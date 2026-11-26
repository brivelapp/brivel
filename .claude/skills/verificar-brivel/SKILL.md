---
name: verificar-brivel
description: Verifica la web de Brivel de verdad, en un navegador, antes de dar algo por terminado o de proponer subirlo. Recorre todas las pantallas del mapa (web/e2e/mapa.ts) a 320, 375, 768 y 1280 px, saca capturas y apunta lo que se sale de ancho y lo que es difícil de pulsar. Úsala al acabar cualquier cambio que se vea en la web, antes de decir «hecho», antes de proponer un commit, push o publicación, y cuando Gabriel pida revisar o comprobar cómo se ve.
---

# Verificar Brivel (web)

Nada visible se da por terminado sin pruebas. Gabriel lo ha pedido muchas veces («compruébalo tú mismo», «haz una
captura y comprueba realmente cómo se ve», «revisa antes de subir»).

## 1. Lanzar

```bash
scripts/verificar-web.sh                    # todo: unos 30 s si el núcleo no cambió
scripts/verificar-web.sh --grep "375 px"    # un solo ancho
```

Desde la raíz del repo. El script pone `JAVA_HOME`, recompila el núcleo JS solo si `shared/src` cambió, construye la
web y lanza `web/e2e/verificar.spec.ts`. Si `rtk` deja la salida vacía, usa `rtk bypass scripts/verificar-web.sh`.

## 2. Leer el resultado

- Primero pasan las reglas (`scripts/reglas.mjs`): cada fallo dice el porqué, cómo arreglarlo y de qué corrección de
  Gabriel viene. Arregla la causa; no hay forma de saltarse una regla, y si una está mal, se corrige la regla.
- **Falla** si una pantalla del mapa no aparece, si hay errores en la consola, si algo se sale de ancho o de su caja,
  o si aparece un control de menos de 44 px o una lista con filas de distinta altura que no estaba en
  `web/e2e/pendientes/<ancho>.txt`. Esa lista es lo que ya había: solo puede menguar. Si arreglas uno, la prueba pide
  quitarlo; `PENDIENTES=1 scripts/verificar-web.sh` la reescribe (revisa el diff: solo debe quitar líneas).
- `web/verificacion/<ancho>/informe.md`: todo lo encontrado, pantalla a pantalla.
- `web/verificacion/<ancho>/<id>.png`: **mira con Read** las capturas de las pantallas que tocaste, al menos a 320 y a
  1280 px. El detector no ve la alineación, los tamaños desiguales entre elementos hermanos ni los textos que no se
  entienden, y ahí es donde Gabriel encuentra fallos (ver `docs/` y la regla de diseño Pulso).

## 3. Entregar

Al contar el cambio, enseña las pruebas: qué pantallas miraste, en qué anchos y lo que encontraste. Si quedan
hallazgos sin arreglar, dilo. No digas «funciona» sin haber pasado este paso.

## Mantener el mapa

`web/e2e/mapa.ts` es el mapa de funciones: nombre de cada pantalla, archivo que la pinta y cómo llegar. Si añades,
quitas o mueves una pantalla, cámbialo en el mismo commit. Si una función desaparece por un rediseño, la verificación
falla: o se recupera la función, o Gabriel confirma que se quita y se borra del mapa.

## Lo que no cubre todavía

Android e iOS: el CI saca capturas de iOS por tamaño (artefacto `ios-screenshots`); Android, con el emulador a mano.
Ni el envío a Garmin ni intervals.icu: necesitan cuentas reales.
