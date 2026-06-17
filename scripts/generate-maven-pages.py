#!/usr/bin/env python3
"""Generate GitHub Pages index from versions.json (releases + commit messages + times)."""

from __future__ import annotations

import html
import json
import sys
from datetime import datetime
from pathlib import Path

GROUP_ID = "com.github.Imajy.eazyCmp"
ARTIFACT_ID = "shared"
SITE_URL = "https://imajy.github.io/eazyCmp/"
MAVEN_REPO_URL = "https://imajy.github.io/eazyCmp/maven-repo/"
GITHUB_REPO = "https://github.com/Imajy/eazyCmp"


def format_when(iso_value: str) -> str:
    if not iso_value:
        return "—"
    try:
        normalized = iso_value.replace("Z", "+00:00")
        dt = datetime.fromisoformat(normalized)
        return dt.strftime("%d %b %Y, %H:%M UTC")
    except ValueError:
        return iso_value


def load_releases(data: dict) -> list[dict]:
    releases = list(data.get("releases") or [])
    if not releases:
        return [
            {
                "version": version,
                "message": f"Release EazyCmp {version}",
                "publishedAt": "",
                "commit": "",
            }
            for version in data.get("versions") or []
        ]

    latest = data.get("latest") or ""
    releases.sort(key=lambda r: r.get("publishedAt", ""), reverse=True)
    if latest:
        for index, release in enumerate(releases):
            if release.get("version") == latest:
                releases.insert(0, releases.pop(index))
                break
    return releases


def commit_link(sha: str) -> str:
    if not sha:
        return ""
    short = sha[:7]
    return f'<a href="{GITHUB_REPO}/commit/{sha}" title="{html.escape(sha)}">{short}</a>'


def render_rows(releases: list[dict], latest: str) -> str:
    if not releases:
        return '<tr><td colspan="4">No versions published yet.</td></tr>'

    rows: list[str] = []
    for release in releases:
        version = release.get("version", "")
        message = html.escape(release.get("message") or f"Release EazyCmp {version}")
        when = format_when(release.get("publishedAt", ""))
        commit = release.get("commit", "")
        commit_cell = commit_link(commit) if commit else "—"
        latest_badge = ' <span class="badge">latest</span>' if version == latest else ""
        dependency = f'implementation("{GROUP_ID}:{ARTIFACT_ID}:{version}")'

        rows.append(
            f"""
        <tr{' class="latest-row"' if version == latest else ''}>
          <td><code>{html.escape(version)}</code>{latest_badge}</td>
          <td class="message">{message}</td>
          <td class="when">{when}<br/><span class="muted">{commit_cell}</span></td>
          <td><pre class="snippet">{html.escape(dependency)}</pre></td>
        </tr>"""
        )
    return "\n".join(rows)


def main() -> None:
    site_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "maven-repo")
    site_dir.mkdir(parents=True, exist_ok=True)

    manifest_path = site_dir / "versions.json"
    data: dict = {"latest": "", "versions": [], "releases": []}
    if manifest_path.exists():
        data = json.loads(manifest_path.read_text())

    releases = load_releases(data)
    latest = data.get("latest") or (releases[0]["version"] if releases else "")
    updated_at = format_when(data.get("updatedAt", ""))
    version_rows = render_rows(releases, latest)
    setup_snippet = f'implementation("{GROUP_ID}:{ARTIFACT_ID}:{latest or "VERSION"}")'

    index_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>EazyCmp Maven Repository</title>
  <style>
    :root {{ font-family: system-ui, sans-serif; color: #111; background: #f6f8fa; }}
    body {{ max-width: 1080px; margin: 2rem auto; padding: 0 1rem 3rem; }}
    h1 {{ margin-bottom: .25rem; }}
    .hero {{
      background: #fff; border: 1px solid #d0d7de; border-radius: 12px;
      padding: 1.25rem 1.5rem; margin: 1.25rem 0 1.75rem;
    }}
    .hero .latest-label {{ font-size: .85rem; color: #57606a; text-transform: uppercase; letter-spacing: .04em; }}
    .hero .latest-value {{ font-size: 1.75rem; font-weight: 700; margin: .25rem 0 .5rem; }}
    .muted {{ color: #57606a; }}
    pre, code {{ background: #fff; border: 1px solid #d0d7de; border-radius: 6px; }}
    pre {{ padding: 1rem; overflow-x: auto; }}
    pre.snippet {{ margin: 0; padding: .5rem .75rem; font-size: .85rem; white-space: pre-wrap; word-break: break-word; }}
    table {{ width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #d0d7de; border-radius: 8px; overflow: hidden; }}
    th, td {{ padding: .85rem 1rem; border-bottom: 1px solid #eaeef2; vertical-align: top; }}
    th {{ text-align: left; background: #f6f8fa; font-size: .92rem; }}
    tr:last-child td {{ border-bottom: none; }}
    tr.latest-row {{ background: #f6ffed; }}
    td.message {{ max-width: 320px; line-height: 1.45; white-space: pre-wrap; word-break: break-word; }}
    td.when {{ min-width: 170px; font-size: .92rem; }}
    .badge {{
      display: inline-block; margin-left: .35rem; padding: .1rem .45rem;
      border-radius: 999px; background: #1a7f37; color: #fff; font-size: .72rem; font-weight: 600;
    }}
    a {{ color: #0969da; text-decoration: none; }}
    a:hover {{ text-decoration: underline; }}
  </style>
</head>
<body>
  <h1>EazyCmp</h1>
  <p class="muted">Kotlin Multiplatform Maven repo — Android, iOS &amp; JVM. Use the latest below, or pin any older version from the table.</p>

  <div class="hero">
    <div class="latest-label">Latest version</div>
    <div class="latest-value"><code>{html.escape(latest or "—")}</code></div>
    <div class="muted">Catalog last updated: {updated_at or "—"} · {len(releases)} release(s)</div>
  </div>

  <h2>Setup (consumer project)</h2>
  <pre>dependencyResolutionManagement {{
    repositories {{
        google()
        mavenCentral()
        maven {{ url = uri("{MAVEN_REPO_URL}") }}
    }}
}}

commonMain.dependencies {{
    {setup_snippet}
}}</pre>

  <h2>Version history</h2>
  <p class="muted">Every row stays on the Maven repo — consumers can keep an older dependency line while others upgrade.</p>
  <table>
    <thead>
      <tr>
        <th>Version</th>
        <th>Release message</th>
        <th>Published</th>
        <th>Gradle dependency</th>
      </tr>
    </thead>
    <tbody>{version_rows}
    </tbody>
  </table>

  <p class="muted" style="margin-top:1.5rem">
    Manifest: <a href="versions.json">versions.json</a> ·
    Source: <a href="{GITHUB_REPO}">GitHub</a>
  </p>
</body>
</html>
"""
    (site_dir / "index.html").write_text(index_html)
    (site_dir / ".nojekyll").touch(exist_ok=True)
    print(f"Generated Pages site in {site_dir} (latest={latest}, releases={len(releases)})")


if __name__ == "__main__":
    main()
