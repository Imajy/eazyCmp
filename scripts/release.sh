#!/usr/bin/env bash
# Publish current version from version.properties (tag push -> CI -> GitHub Pages).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(python3 scripts/version.py current)"
NEXT="$(python3 scripts/version.py next)"
MESSAGE="${1:-Release EazyCmp $VERSION}"

echo "Publishing version: $VERSION"
echo "Tag message       : $MESSAGE"
echo "Next after CI     : $NEXT"

if git rev-parse "$VERSION" >/dev/null 2>&1; then
  echo "Error: tag '$VERSION' already exists"
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: working tree not clean. Commit or stash changes first."
  git status --short
  exit 1
fi

git tag -a "$VERSION" -m "$MESSAGE"
git push origin main
git push origin "$VERSION"

echo ""
echo "Tagged $VERSION — CI will publish to https://imajy.github.io/eazyCmp/"
echo "After publish, CI auto-bumps version.properties to: $NEXT"
