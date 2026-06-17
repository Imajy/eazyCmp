#!/usr/bin/env bash
# Push master — CI publishes version from version.properties automatically.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEFAULT_BRANCH="${EAZYCMP_BRANCH:-master}"
VERSION="$(python3 scripts/version.py current)"
NEXT="$(python3 scripts/version.py next)"

echo "Next publish version : $VERSION"
echo "After CI bump        : $NEXT"
echo "Branch               : $DEFAULT_BRANCH"
echo ""
echo "Push your commits to master — GitHub Actions will publish automatically."
echo "  git push origin $DEFAULT_BRANCH"
echo ""
echo "Live site: https://imajy.github.io/eazyCmp/"
