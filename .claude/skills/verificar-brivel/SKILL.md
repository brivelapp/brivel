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

- **Falla** si una pantalla del mapa no aparece o si hay errores en la consola. Arréglalo antes de seguir.
- `web/verificacion/<ancho>/informe.md`: por pantalla, `desborde-pagina`, `se-sale` y `toque-pequeno` (< 44 px).
  De momento solo se apuntan. Compara con la verificación anterior: lo nuevo lo has causado tú.
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
