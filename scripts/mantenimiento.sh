#!/usr/bin/env bash
# Señales de mantenimiento (skill .claude/skills/mantenimiento-brivel): recoge en un informe lo que pide arreglo, de
# más a menos grave. Solo lee: no toca ramas ni escribe fuera de mantenimiento/ (salvo `npm ci` si faltan los módulos).
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p mantenimiento
informe=mantenimiento/senales.md
repo=brivelapp/brivel
{
  echo "# Señales de mantenimiento ($(date -u +%Y-%m-%dT%H:%MZ), master $(git rev-parse --short origin/master))"
  echo
  echo "## 1. CI de master"
  gh run list -R $repo -b master -L 5 --json conclusion,headSha,databaseId,createdAt \
    --jq '.[] | "- \(if .conclusion == "" then "en marcha" else .conclusion end) \(.headSha[0:7]) run \(.databaseId) \(.createdAt)"' 2>&1
  echo
  echo "## 2. Vulnerabilidades en dependencias (npm audit, solo producción)"
  for d in web server; do
    [[ -d $d/node_modules ]] || (cd $d && npm ci --silent >/dev/null 2>&1)
    r=$(cd $d && npm audit --omit=dev --json 2>/dev/null | node -e '
      let j=JSON.parse(require("fs").readFileSync(0,"utf8")); let v=j.metadata?.vulnerabilities||{};
      console.log(["critical","high","moderate","low"].map(k=>k+" "+(v[k]||0)).join(", "));
      for (const [n,x] of Object.entries(j.vulnerabilities||{})) console.log("  - "+n+": "+x.severity)')
    echo "- $d: $r"
  done
  echo
  echo "## 3. Incidencias abiertas en GitHub"
  gh issue list -R $repo --state open --json number,title,labels \
    --jq 'if length == 0 then "- ninguna" else .[] | "- #\(.number) \(.title)" end' 2>&1
  echo
  echo "## 4. Deuda con trinquete (solo puede menguar)"
  for f in web/e2e/pendientes/*.txt androidApp/verificacion-pendientes/*.txt; do
    echo "- $f: $(grep -c . "$f") entradas"
  done
  [[ -f config/detekt-baseline.xml ]] && echo "- config/detekt-baseline.xml: $(grep -c '<ID>' config/detekt-baseline.xml) avisos"
  echo
  echo "## 5. Correo de usuarios"
  echo "- Lo busca el agente en Gmail (ver la skill): no está al alcance de este script."
} > "$informe"
cat "$informe"
