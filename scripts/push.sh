#!/usr/bin/env bash
# EazyCmp Git Helper: Commit, Pull/Rebase (with auto conflict resolution), and Push changes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -z "${1:-}" ]; then
  echo "Error: Please provide a commit message."
  echo "Usage: ./scripts/push.sh \"Your commit message\""
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: '$ROOT' is not a Git repository (.git directory missing)."
  echo "Initialize or restore the Git repository and remote 'origin' before pushing."
  exit 1
fi

BRANCH="${EAZYCMP_BRANCH:-master}"
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Error: Git remote 'origin' is not configured."
  exit 1
fi

COMMIT_MSG="$1"

GENERATED_FILES=(
  "index.html"
  "maven-repo/index.html"
  "maven-repo/versions.json"
  "versions.json"
  "core/src/commonMain/kotlin/com/aj/shared/internal/EazyCmpBuildInfo.kt"
  "shared/src/commonMain/kotlin/com/aj/shared/internal/EazyCmpBuildInfo.kt"
  "version.properties"
)

is_generated_file() {
  local target="$1"
  local f
  for f in "${GENERATED_FILES[@]}"; do
    if [ "$f" = "$target" ]; then
      return 0
    fi
  done
  return 1
}

in_rebase() {
  test -d "$(git rev-parse --git-path rebase-merge)" || test -d "$(git rev-parse --git-path rebase-apply)"
}

resolve_rebase_conflicts() {
  while in_rebase; do
    local conflicts
    conflicts="$(git diff --name-only --diff-filter=U)"

    if [ -n "$conflicts" ]; then
      echo "Conflicts detected. Checking auto-generated files..."
      local unresolved=()
      while IFS= read -r file; do
        [ -z "$file" ] && continue
        if is_generated_file "$file"; then
          echo "  Resolving generated file (keeping local): $file"
          if ! git checkout --theirs -- "$file" 2>/dev/null; then
            git checkout --ours -- "$file" 2>/dev/null || true
          fi
          git add -- "$file"
        else
          unresolved+=("$file")
        fi
      done <<< "$conflicts"

      if [ "${#unresolved[@]}" -gt 0 ]; then
        echo "Error: Manual conflict resolution required for:"
        printf '  - %s\n' "${unresolved[@]}"
        echo "Resolve these conflicts, then run 'git add <files> && git rebase --continue' (or 'git rebase --abort')."
        return 1
      fi
    fi

    # Continue rebase; skip commit if it became empty after conflict resolution.
    if git diff --cached --quiet && [ -z "$(git diff --name-only --diff-filter=U)" ]; then
      if ! GIT_EDITOR=true git rebase --skip; then
        if [ -z "$(git diff --name-only --diff-filter=U)" ] && in_rebase; then
          echo "Error: 'git rebase --skip' failed without merge conflicts."
          return 1
        fi
      fi
    else
      if ! GIT_EDITOR=true git rebase --continue; then
        # If rebase stopped on a subsequent commit's conflict, the loop handles it.
        if [ -z "$(git diff --name-only --diff-filter=U)" ] && in_rebase; then
          echo "Error: 'git rebase --continue' failed without merge conflicts."
          return 1
        fi
      fi
    fi
  done
}

echo "Staging all changes..."
git add -A

if ! git diff --cached --quiet; then
  echo "Committing changes..."
  git commit -m "$COMMIT_MSG"
else
  echo "No local changes to commit."
fi

echo "Pulling latest commits from origin/$BRANCH..."
if ! git pull --rebase origin "$BRANCH"; then
  if ! in_rebase; then
    echo "Error: 'git pull --rebase origin $BRANCH' failed (not a merge conflict). Check network, auth, or branch name."
    exit 1
  fi
  resolve_rebase_conflicts
fi

echo "Pushing to $BRANCH branch..."
git push origin "$BRANCH"

echo "Success! Changes have been pushed and release pipeline is triggered."
