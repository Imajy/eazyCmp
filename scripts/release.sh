#!/usr/bin/env bash
# Publish current version from version.properties (tag push -> CI -> GitHub Pages).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEFAULT_BRANCH="${EAZYCMP_BRANCH:-master}"
VERSION="$(python3 scripts/version.py current)"
NEXT="$(python3 scripts/version.py next)"
MESSAGE="${1:-Release EazyCmp $VERSION}"

echo "Publishing version: $VERSION"
echo "Branch            : $DEFAULT_BRANCH"
echo "Tag message       : $MESSAGE"
echo "Next after CI     : $NEXT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: working tree not clean. Commit or stash changes first."
  git status --short
  exit 1
fi

if git rev-parse "$VERSION" >/dev/null 2>&1; then
  if git ls-remote --tags origin "refs/tags/$VERSION" | grep -q .; then
    echo "Error: tag '$VERSION' already exists on GitHub."
    echo "Run: python3 scripts/version.py current   (check next version)"
    echo "Or delete remote tag if you need to republish."
    exit 1
  fi
  echo "Tag '$VERSION' exists locally — will push to GitHub (skip recreate)."
else
  git tag -a "$VERSION" -m "$MESSAGE"
fi

git push origin "$DEFAULT_BRANCH"
git push origin "$VERSION"

echo ""
echo "Tagged $VERSION — CI will publish to https://imajy.github.io/eazyCmp/"
echo "After publish, CI auto-bumps version.properties on $DEFAULT_BRANCH to: $NEXT"
