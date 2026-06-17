#!/usr/bin/env bash
# Push master — CI publishes version from version.properties automatically.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(python3 scripts/version.py current)"
NEXT="$(python3 scripts/version.py next)"

echo "Next publish version : $VERSION"
echo "After CI bump        : $NEXT"
echo ""
echo "Push your commits to master — GitHub Actions will publish automatically."
echo "  git push origin master"
echo ""
echo "Live site: https://imajy.github.io/eazyCmp/"
