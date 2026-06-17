#!/usr/bin/env bash
# EazyCmp Git Helper: Commit, Pull/Rebase (with auto conflict resolution), and Push changes.
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Error: Please provide a commit message."
  echo "Usage: ./scripts/push.sh \"Your commit message\""
  exit 1
fi

COMMIT_MSG="$1"

echo "Staging all changes..."
git add .

echo "Committing changes..."
# We allow empty/no changes to proceed gracefully
git commit -m "$COMMIT_MSG" || echo "No local changes to commit."

echo "Pulling latest commits from remote..."
if ! git pull --rebase origin master; then
  echo "Conflicts detected in auto-generated files. Resolving automatically..."
  
  # Checkout our local version for conflicted/generated files to override remote bots
  git checkout --theirs \
    index.html \
    maven-repo/index.html \
    maven-repo/versions.json \
    versions.json \
    shared/src/commonMain/kotlin/com/aj/shared/internal/EazyCmpBuildInfo.kt \
    version.properties || true
    
  git add .
  git -c core.editor=true rebase --continue
fi

echo "Pushing to master branch..."
git push origin master

echo "Success! Changes have been pushed and release pipeline is triggered."
