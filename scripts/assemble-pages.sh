#!/usr/bin/env bash
# Assemble the GitHub Pages tree: web demo at / and Dokka HTML at /api/.
# Requires a prior Gradle build:
#   ./gradlew :demo-web:jsBrowserDistribution :dokkaGenerate
# See GITHUB_PAGES.md.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEMO_DIR="demo-web/build/dist/js/productionExecutable"
DOCS_DIR="build/dokka/html"

if [[ ! -f "$DEMO_DIR/index.html" ]]; then
  echo "error: missing $DEMO_DIR/index.html" >&2
  echo "run: ./gradlew :demo-web:jsBrowserDistribution" >&2
  exit 1
fi
if [[ ! -f "$DOCS_DIR/index.html" ]]; then
  echo "error: missing $DOCS_DIR/index.html" >&2
  echo "run: ./gradlew :dokkaGenerate" >&2
  exit 1
fi

rm -rf public
mkdir -p public/api
cp -R "$DEMO_DIR"/. public/
cp -R "$DOCS_DIR"/. public/api/
touch public/.nojekyll

echo "assembled $ROOT/public (demo at /, API docs at /api/)"
