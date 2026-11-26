#!/usr/bin/env bash
# Verificación de la web (skill .claude/skills/verificar-brivel): compila el núcleo si cambió, construye la web y
# recorre todas las pantallas del mapa (web/e2e/mapa.ts) a 320, 375, 768 y 1280 px. Deja capturas e informe en
# web/verificacion/<ancho>/ y acaba con un resumen. Argumentos extra van a Playwright (p. ej. --grep "375 px").
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21.0.12.1+1}"  # el shell de Claude no carga ~/.bashrc

nucleo=shared/build/dist/js/productionLibrary/brivel-shared.mjs
if [[ ! -f $nucleo || -n $(find shared/src -newer "$nucleo" -print -quit) ]]; then
  echo "Compilando el núcleo JS…"
  ./gradlew :shared:jsBrowserProductionLibraryDistribution --console=plain -q
fi

cd web
[[ -d node_modules ]] || npm ci
npm run build
estado=0
npx playwright test e2e/verificar.spec.ts --reporter=line "$@" || estado=$?

echo
echo "== Resumen (detalle en web/verificacion/<ancho>/informe.md, capturas al lado) =="
for informe in verificacion/*/informe.md; do
  [[ -f $informe ]] || continue
  ancho=$(basename "$(dirname "$informe")")
  distintos() { grep "^- $1" "$informe" | sort -u | wc -l; }  # el pie y las pestañas salen en cada pantalla: una vez
  echo "$ancho px: $(grep -c '^- desborde-pagina' "$informe") pantallas desbordan," \
    "$(distintos se-sale) cosas distintas se salen, $(distintos toque-pequeno) controles distintos pequeños"
done
[[ $estado -eq 0 ]] && echo "Todas las pantallas del mapa se encontraron, sin errores de consola." || echo "FALLO: ver la salida de Playwright arriba."
exit $estado
