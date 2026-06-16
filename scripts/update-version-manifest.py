#!/usr/bin/env python3
"""Add or update a release entry in maven-repo/versions.json."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

GROUP_ID = "com.github.Imajy.eazyCmp"
ARTIFACT_ID = "shared"
REPO_URL = "https://imajy.github.io/eazyCmp/"


def load_manifest(path: Path) -> dict:
    if not path.exists():
        return {
            "groupId": GROUP_ID,
            "artifactId": ARTIFACT_ID,
            "latest": "",
            "versions": [],
            "releases": [],
            "repository": REPO_URL,
            "updatedAt": "",
        }
    return json.loads(path.read_text())


def save_manifest(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def upsert_release(
    data: dict,
    version: str,
    message: str,
    published_at: str,
    commit: str,
) -> dict:
    releases: list[dict] = list(data.get("releases") or [])
    entry = {
        "version": version,
        "message": message.strip() or f"Release EazyCmp {version}",
        "publishedAt": published_at,
        "commit": commit,
    }

    replaced = False
    for index, release in enumerate(releases):
        if release.get("version") == version:
            releases[index] = entry
            replaced = True
            break
    if not replaced:
        releases.insert(0, entry)

    releases.sort(key=lambda r: r.get("publishedAt", ""), reverse=True)

    versions = [r["version"] for r in releases]
    # Keep any legacy version strings not present in releases.
    for legacy in data.get("versions") or []:
        if legacy not in versions:
            versions.append(legacy)

    data["releases"] = releases
    data["versions"] = versions
    data["latest"] = version
    data["groupId"] = GROUP_ID
    data["artifactId"] = ARTIFACT_ID
    data["repository"] = REPO_URL
    data["updatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return data


def git_tag_metadata(version: str) -> tuple[str, str, str]:
    """Return (message, published_at, commit) from local git tag."""
    try:
        message = subprocess.check_output(
            ["git", "log", "-1", "--format=%B", version],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        published_at = subprocess.check_output(
            ["git", "log", "-1", "--format=%cI", version],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        commit = subprocess.check_output(
            ["git", "rev-parse", f"{version}^{{commit}}"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        return message, published_at, commit
    except (subprocess.CalledProcessError, FileNotFoundError):
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        return f"Release EazyCmp {version}", now, ""


def backfill_releases_from_git(data: dict) -> dict:
    releases: list[dict] = list(data.get("releases") or [])
    known = {r.get("version") for r in releases}

    for version in data.get("versions") or []:
        if version in known:
            continue
        message, published_at, commit = git_tag_metadata(version)
        releases.append(
            {
                "version": version,
                "message": message,
                "publishedAt": published_at,
                "commit": commit,
            }
        )

    releases.sort(key=lambda r: r.get("publishedAt", ""), reverse=True)
    data["releases"] = releases
    if releases and not data.get("latest"):
        data["latest"] = releases[0]["version"]
    return data


def main() -> None:
    parser = argparse.ArgumentParser(description="Update EazyCmp versions.json release metadata")
    parser.add_argument("--manifest", default="maven-repo/versions.json")
    parser.add_argument("--version", help="Release version")
    parser.add_argument("--message", default="", help="Release commit/tag message")
    parser.add_argument("--published-at", default="", help="ISO-8601 publish time")
    parser.add_argument("--commit", default="", help="Git commit SHA")
    parser.add_argument("--backfill-git", action="store_true", help="Fill missing releases from git tags")
    args = parser.parse_args()

    manifest_path = Path(args.manifest)
    data = load_manifest(manifest_path)

    if args.backfill_git:
        data = backfill_releases_from_git(data)

    if args.version:
        message = args.message
        published_at = args.published_at
        commit = args.commit
        if not message or not published_at or not commit:
            git_message, git_time, git_commit = git_tag_metadata(args.version)
            message = message or git_message
            published_at = published_at or git_time
            commit = commit or git_commit
        data = upsert_release(data, args.version, message, published_at, commit)

    save_manifest(manifest_path, data)

    if args.version or args.backfill_git:
        root = Path(__file__).resolve().parents[1]
        subprocess.check_call(
            [sys.executable, str(root / "scripts/generate-maven-pages.py"), str(manifest_path.parent)]
        )


if __name__ == "__main__":
    main()
