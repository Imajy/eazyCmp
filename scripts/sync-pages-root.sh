#!/usr/bin/env bash
# Copy Maven catalog files to repo root so GitHub Pages (master /) shows versions, not README.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

test -f maven-repo/index.html || { echo "maven-repo/index.html missing"; exit 1; }
test -f maven-repo/versions.json || { echo "maven-repo/versions.json missing"; exit 1; }

cp maven-repo/index.html index.html
cp maven-repo/versions.json versions.json
touch .nojekyll

echo "Synced Pages catalog to repo root (index.html, versions.json, .nojekyll)"
