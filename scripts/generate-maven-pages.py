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


def calculate_update_frequency(releases: list[dict]) -> str:
    valid_times = []
    for r in releases:
        pub = r.get("publishedAt")
        if pub:
            try:
                pub_clean = pub.replace("Z", "+00:00")
                dt = datetime.fromisoformat(pub_clean)
                valid_times.append(dt)
            except Exception:
                pass
    if len(valid_times) < 2:
        return "Initial release cycle"
    
    valid_times.sort()
    intervals = []
    for i in range(len(valid_times) - 1):
        diff = (valid_times[i+1] - valid_times[i]).total_seconds()
        intervals.append(diff)
        
    avg_seconds = sum(intervals) / len(intervals)
    avg_days = avg_seconds / 86400.0
    
    if avg_days < 0.04:  # less than ~1 hour
        minutes = avg_days * 1440.0
        return f"Every {max(1, int(minutes))} mins"
    elif avg_days < 1:
        hours = avg_days * 24.0
        return f"Every {hours:.1f} hours"
    elif avg_days < 7:
        return f"Every {avg_days:.1f} days"
    else:
        weeks = avg_days / 7.0
        return f"Every {weeks:.1f} weeks"


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
    return f'<a href="{GITHUB_REPO}/commit/{sha}" title="{html.escape(sha)}" target="_blank" class="commit-link">{short}</a>'


def render_rows(releases: list[dict], latest: str) -> str:
    if not releases:
        return '<tr><td colspan="4" class="no-data">No versions published yet.</td></tr>'

    rows: list[str] = []
    for release in releases:
        version = release.get("version", "")
        message = html.escape(release.get("message") or f"Release EazyCmp {version}")
        when = format_when(release.get("publishedAt", ""))
        commit = release.get("commit", "")
        commit_cell = commit_link(commit) if commit else "—"
        latest_badge = ' <span class="badge badge-success">latest</span>' if version == latest else ""
        dependency = f'implementation("{GROUP_ID}:{ARTIFACT_ID}:{version}")'

        rows.append(
            f"""
        <tr{' class="latest-row"' if version == latest else ''}>
          <td class="version-cell"><code>{html.escape(version)}</code>{latest_badge}</td>
          <td class="message">{message}</td>
          <td class="when">{when}<br/><span class="muted">{commit_cell}</span></td>
          <td class="code-cell">
            <div class="code-copy-container">
              <code class="snippet-code" id="dep-{html.escape(version)}">{html.escape(dependency)}</code>
              <button class="btn-copy" onclick="copyCode('dep-{html.escape(version)}')" data-copy-id="dep-{html.escape(version)}">Copy</button>
            </div>
          </td>
        </tr>"""
        )
    return "\n".join(rows)


def render_failures(failures: list[dict]) -> str:
    if not failures:
        return """
      <div class="no-failures" style="grid-column: 1 / -1; text-align: center; padding: 2rem; background: rgba(16, 185, 129, 0.02); border: 1px solid rgba(16, 185, 129, 0.08); border-radius: 12px; color: var(--accent-green); font-weight: 500; display: flex; flex-direction: column; align-items: center; gap: 0.5rem; width: 100%;">
        <span class="no-failures-icon">✓</span>
        No recent build failures. All deployment runs completed successfully!
      </div>
        """
    cards = []
    for f in failures:
        version = html.escape(f.get("version", ""))
        commit = html.escape(f.get("commit", "")[:7])
        published_at = format_when(f.get("publishedAt", ""))
        log_file = html.escape(f.get("logFile", ""))
        
        # Link to the log file (absolute via MAVEN_REPO_URL to be universally accessible)
        log_url = f"{MAVEN_REPO_URL}{log_file}" if not log_file.startswith("http") else log_file
        
        cards.append(f"""
        <div class="failed-run-card">
          <div class="failed-run-header">
            <span class="failed-version">Build Run (Version {version})</span>
            <span class="failed-time">{published_at}</span>
          </div>
          <div class="failed-commit">
            Commit: <code>{commit}</code>
          </div>
          <div class="failed-action">
            <a href="{log_url}" target="_blank" class="btn-error-logs">View Error Logs &rarr;</a>
          </div>
        </div>
        """)
    return "\n".join(cards)


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
    total_downloads = data.get("totalDownloads", "5,200+")
    version_rows = render_rows(releases, latest)
    
    # Calculate release interval frequency
    frequency = calculate_update_frequency(releases)
    
    failures = data.get("failures") or []
    failures_html = render_failures(failures)
    
    ver = latest or "1.0.0.1-rc-060"
    setup_repo_snippet = f"""dependencyResolutionManagement {{
    repositories {{
        google()
        mavenCentral()
        maven {{ url = uri("{MAVEN_REPO_URL}") }}
    }}
}}"""
    setup_dep_snippet = f"""kotlin {{
    sourceSets {{
        commonMain.dependencies {{
            // Option A — Full umbrella (core + platform + ui + plugin SPI)
            implementation("{GROUP_ID}:{ARTIFACT_ID}:{ver}")

            // Option B — Modular / light (pick only what you need)
            // implementation("{GROUP_ID}:core:{ver}")
            // implementation("{GROUP_ID}:platform:{ver}")
            // implementation("{GROUP_ID}:ui:{ver}")
            // implementation("{GROUP_ID}:web:{ver}")
        }}
    }}
}}"""

    android_setup_snippet = """// MyApp.kt (Android Application)
import android.app.Application
import com.aj.shared.EazyCmp
import com.aj.shared.api.eazyModule
import com.aj.shared.plugin.adapters.FcmPushTokenService
import org.koin.core.context.startKoin

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Initialize & configure EazyCmp BEFORE startKoin
        EazyCmp.bootstrap(context = this, settingsName = "my_app_prefs") {
            baseUrl = "https://api.example.com"
            isDebugEnabled = BuildConfig.DEBUG
        }
        // 2. Wire optional host plugins (built-in or custom)
        EazyCmp.install {
            pushToken = FcmPushTokenService()
        }
        // 3. Register Koin module
        startKoin { modules(eazyModule()) }
    }
}"""

    ios_setup_snippet = """// Common KMP iOS entry (e.g. MainViewController.kt)
import com.aj.shared.EazyCmp
import com.aj.shared.api.eazyModule
import com.aj.shared.plugin.adapters.ApnsPushTokenService
import org.koin.core.context.startKoin

fun initEazyCmpIos() {
    EazyCmp.bootstrap(settingsName = "my_app_prefs") {
        baseUrl = "https://api.example.com"
    }
    EazyCmp.install {
        pushToken = ApnsPushTokenService.shared
    }
    startKoin { modules(eazyModule()) }
}
// Note: Add required privacy usage strings to iosApp/Info.plist
// (see docs/templates/Info.plist.usage-strings.xml in repo)."""

    desktop_setup_snippet = """// Desktop JVM main()
import com.aj.shared.EazyCmp
import com.aj.shared.api.eazyModule
import com.aj.shared.plugin.adapters.DesktopZxingQrScanner
import org.koin.core.context.startKoin

fun main() {
    EazyCmp.bootstrap(settingsName = "my_app_prefs") {
        baseUrl = "https://api.example.com"
    }
    EazyCmp.install {
        qrScanner = DesktopZxingQrScanner()
    }
    startKoin { modules(eazyModule()) }
    // SecureStorage uses AES-GCM with key stored at ~/.eazycmp/secure.key
}"""

    web_setup_snippet = f"""// build.gradle.kts -> commonMain.dependencies:
// implementation("{GROUP_ID}:web:{ver}")

import com.aj.shared.web.WebEazyCmp
import com.aj.shared.web.WebPickerType

fun initWeb() {{
    WebEazyCmp.storage.putString("token", "jwt_token")
    WebEazyCmp.media.pick(WebPickerType.IMAGE) {{ file ->
        println("Picked: ${{file.name}} (${{file.bytes.size}} bytes)")
    }}
    WebEazyCmp.install {{
        register("analytics", myWebAnalytics)
    }}
}}"""

    usage_root_snippet = """// App.kt — Compose Multiplatform root wrapper
import androidx.compose.runtime.Composable
import com.aj.shared.display.EazyCmpDisplayHost
import com.aj.shared.theme.EazyCmpTheme
import com.aj.shared.ui.SnackBarBoxApp

@Composable
fun App() {
    SnackBarBoxApp {
        EazyCmpDisplayHost {
            EazyCmpTheme {
                // Your app navigation & screens
            }
        }
    }
}"""

    usage_api_snippet = """import com.aj.shared.EazyCmp
import com.aj.shared.api.Resource

// 1. Register extra base URLs or update auth token anytime
EazyCmp.setBaseUrl(baseUrl = "https://api.example.com", token = "jwt_token")

// 2. Make typed API calls via EazyCmp.api
EazyCmp.api.get<UserProfile>("users/me").collect { res ->
    when (res) {
        is Resource.Loading -> { /* show loader */ }
        is Resource.Success -> println(res.data)
        is Resource.Error -> println(res.message)
    }
}

// 3. Pick, compress & upload files
EazyCmp.upload.pickCompressUpload(base = "default", endpoint = "/upload", file = pickedFile)"""

    usage_storage_snippet = """import com.aj.shared.EazyCmp
import com.aj.shared.permission.AppPermission
import com.aj.shared.permission.PermissionCallback
import com.aj.shared.validation.isValidEmail
import com.aj.shared.validation.isValidPan

// Encrypted storage & non-sensitive prefs
EazyCmp.storage.putString("auth_token", "secret_jwt")
val token = EazyCmp.storage.getString("auth_token")
EazyCmp.preferences.putBoolean("onboarding_done", true)

// Permissions, clipboard & Indian/common validators
EazyCmp.permission.requestPermissions(listOf(AppPermission.CAMERA), PermissionCallback { results ->
    println("Camera status: ${results[AppPermission.CAMERA]}")
})
EazyCmp.clipboard.copy("Copied!")
val okEmail = isValidEmail("user@example.com")
val okPan = isValidPan("ABCDE1234F")"""

    usage_spi_snippet = """import com.aj.shared.EazyCmp

// Override stubs or plug your own SDKs (Firebase, ML Kit, Sentry, etc.)
EazyCmp.install {
    analytics = MyFirebaseAnalytics()
    crashReporter = MyCrashlyticsReporter()
    qrScanner = AndroidCameraXQrScanner(activity)
    otpAutoRead = SmsRetrieverOtpAutoRead(activity)
    register("payments", MyPaymentsPlugin())
}

// Inspect service readiness (FULL / PARTIAL / HOST_REQUIRED)
EazyCmp.serviceCatalog().forEach { status ->
    println("${status.name}: ${status.capability} (${status.hostHint})")
}"""

    index_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>EazyCmp Maven Repository &amp; Release Dashboard</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&amp;family=JetBrains+Mono:wght@400;500&amp;family=Outfit:wght@400;500;600;700;800&amp;display=swap" rel="stylesheet">
  <style>
    :root {{
      --bg-primary: #030712;
      --bg-secondary: #0b1528;
      --bg-card: rgba(15, 23, 42, 0.65);
      --border-color: rgba(255, 255, 255, 0.08);
      --border-glow: rgba(6, 182, 212, 0.15);
      --text-primary: #f3f4f6;
      --text-secondary: #9ca3af;
      --text-muted: #6b7280;
      --accent-cyan: #06b6d4;
      --accent-purple: #8b5cf6;
      --accent-green: #10b981;
      --accent-red: #ef4444;
      --accent-orange: #f97316;
      --font-sans: 'Outfit', 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      --font-mono: 'JetBrains Mono', monospace;
      --card-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.7);
    }}

    * {{ box-sizing: border-box; }}
    
    body {{
      background-color: var(--bg-primary);
      background-image: 
        radial-gradient(circle at 15% 15%, rgba(139, 92, 246, 0.08) 0%, transparent 40%),
        radial-gradient(circle at 85% 85%, rgba(6, 182, 212, 0.08) 0%, transparent 40%);
      color: var(--text-primary);
      font-family: var(--font-sans);
      margin: 0;
      padding: 0;
      min-height: 100vh;
      line-height: 1.6;
    }}

    header {{
      background: rgba(3, 7, 18, 0.8);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border-color);
      position: sticky;
      top: 0;
      z-index: 100;
    }}

    .header-container {{
      max-width: 1200px;
      margin: 0 auto;
      padding: 1rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }}

    .logo-container {{
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }}

    .logo-dot {{
      width: 12px;
      height: 12px;
      background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
      border-radius: 50%;
      box-shadow: 0 0 12px var(--accent-cyan);
    }}

    .logo-text {{
      font-size: 1.5rem;
      font-weight: 800;
      letter-spacing: -0.02em;
      background: linear-gradient(to right, #ffffff, #9ca3af);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }}

    .github-btn-link {{
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      padding: 0.5rem 1rem;
      border-radius: 999px;
      text-decoration: none;
      font-weight: 500;
      font-size: 0.9rem;
      transition: all 0.3s ease;
    }}

    .github-btn-link:hover {{
      background: rgba(255, 255, 255, 0.12);
      border-color: rgba(255, 255, 255, 0.2);
      transform: translateY(-1px);
    }}

    main {{
      max-width: 1200px;
      margin: 2rem auto;
      padding: 0 1.5rem 5rem;
    }}

    .hero-section {{
      text-align: center;
      margin-bottom: 3rem;
    }}

    .hero-title {{
      font-size: 2.75rem;
      font-weight: 800;
      margin: 0 0 0.5rem;
      letter-spacing: -0.03em;
      background: linear-gradient(to right, #ffffff, #a855f7, #06b6d4);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }}

    .hero-desc {{
      color: var(--text-secondary);
      font-size: 1.15rem;
      max-width: 650px;
      margin: 0 auto;
    }}

    .dashboard-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 1.5rem;
      margin-bottom: 3rem;
    }}

    .card {{
      background: var(--bg-card);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      padding: 1.5rem;
      box-shadow: var(--card-shadow);
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      position: relative;
      overflow: hidden;
    }}

    .card::before {{
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: transparent;
      transition: background 0.3s ease;
    }}

    .card:hover {{
      transform: translateY(-4px);
      border-color: var(--border-glow);
      box-shadow: 0 15px 35px -8px rgba(6, 182, 212, 0.15);
    }}

    .card-latest::before {{ background: linear-gradient(90deg, var(--accent-green), var(--accent-cyan)); }}
    .card-stats::before {{ background: linear-gradient(90deg, var(--accent-purple), var(--accent-cyan)); }}
    .card-metrics::before {{ background: linear-gradient(90deg, var(--accent-cyan), var(--accent-purple)); }}
    .card-build::before {{ background: linear-gradient(90deg, var(--accent-orange), var(--accent-red)); }}

    .card-label {{
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--text-secondary);
      font-weight: 600;
      margin-bottom: 0.75rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }}

    .card-value {{
      font-size: 1.8rem;
      font-weight: 700;
      margin-bottom: 0.5rem;
      font-family: var(--font-sans);
    }}

    .card-subtext {{
      font-size: 0.85rem;
      color: var(--text-muted);
    }}

    .status-badge {{
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.25rem 0.75rem;
      border-radius: 999px;
      font-weight: 600;
      font-size: 0.85rem;
    }}

    .status-badge.loading {{ background: rgba(255, 255, 255, 0.06); color: var(--text-secondary); }}
    .status-badge.success {{ background: rgba(16, 185, 129, 0.1); color: var(--accent-green); }}
    .status-badge.failure {{ background: rgba(239, 68, 68, 0.1); color: var(--accent-red); }}
    .status-badge.warning {{ background: rgba(249, 115, 22, 0.1); color: var(--accent-orange); }}

    .status-indicator {{
      width: 8px;
      height: 8px;
      border-radius: 50%;
      display: inline-block;
    }}
    .status-indicator.success {{ background: var(--accent-green); box-shadow: 0 0 8px var(--accent-green); animation: pulse 2s infinite; }}
    .status-indicator.failure {{ background: var(--accent-red); box-shadow: 0 0 8px var(--accent-red); }}
    .status-indicator.warning {{ background: var(--accent-orange); box-shadow: 0 0 8px var(--accent-orange); }}

    @keyframes pulse {{
      0% {{ transform: scale(0.95); opacity: 0.8; }}
      50% {{ transform: scale(1.1); opacity: 1; }}
      100% {{ transform: scale(0.95); opacity: 0.8; }}
    }}

    .metric-item {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.5rem;
    }}

    .section-title {{
      font-size: 1.6rem;
      font-weight: 700;
      margin: 3rem 0 1.5rem;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      letter-spacing: -0.02em;
    }}

    .section-title-icon {{
      width: 8px;
      height: 18px;
      background: var(--accent-cyan);
      border-radius: 4px;
    }}

    .setup-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(480px, 1fr));
      gap: 1.5rem;
      margin-bottom: 3.5rem;
    }}

    @media(max-width: 768px) {{
      .setup-grid {{ grid-template-columns: 1fr; }}
    }}

    .setup-box {{
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      padding: 1.5rem;
      box-shadow: var(--card-shadow);
      position: relative;
    }}

    .setup-box-header {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }}

    .setup-title {{
      font-weight: 600;
      color: var(--text-primary);
    }}

    pre {{
      background: rgba(0, 0, 0, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 8px;
      padding: 1rem;
      margin: 0;
      overflow-x: auto;
    }}

    code {{
      font-family: var(--font-mono);
      font-size: 0.88rem;
    }}

    .code-copy-container {{
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      background: rgba(0, 0, 0, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 8px;
      padding: 0.5rem 0.75rem;
      width: 100%;
    }}

    .snippet-code {{
      font-family: var(--font-mono);
      font-size: 0.85rem;
      overflow-x: auto;
      white-space: nowrap;
      flex-grow: 1;
    }}

    .btn-copy {{
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      padding: 0.25rem 0.75rem;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.8rem;
      font-weight: 500;
      transition: all 0.2s ease;
      white-space: nowrap;
    }}

    .btn-copy:hover {{
      background: rgba(255, 255, 255, 0.12);
      border-color: rgba(255, 255, 255, 0.2);
    }}

    /* Top Install & Setup Guide */
    .guide-section {{
      background: linear-gradient(180deg, rgba(15, 23, 42, 0.82), rgba(11, 21, 40, 0.72));
      border: 1px solid rgba(6, 182, 212, 0.22);
      border-radius: 20px;
      padding: 2rem;
      margin-bottom: 3rem;
      box-shadow: 0 20px 50px -15px rgba(6, 182, 212, 0.12), var(--card-shadow);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
    }}

    .guide-header {{
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
      margin-bottom: 1.5rem;
      padding-bottom: 1.25rem;
      border-bottom: 1px solid var(--border-color);
    }}

    .guide-badge {{
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.25rem 0.75rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      background: rgba(6, 182, 212, 0.12);
      color: var(--accent-cyan);
      border: 1px solid rgba(6, 182, 212, 0.25);
      margin-bottom: 0.6rem;
    }}

    .guide-title {{
      margin: 0 0 0.35rem;
      font-size: 1.75rem;
      font-weight: 800;
      letter-spacing: -0.02em;
    }}

    .guide-subtitle {{
      margin: 0;
      color: var(--text-secondary);
      font-size: 0.98rem;
      max-width: 720px;
    }}

    .header-nav {{
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }}

    .nav-pill {{
      color: var(--text-secondary);
      text-decoration: none;
      font-size: 0.85rem;
      font-weight: 500;
      padding: 0.4rem 0.85rem;
      border-radius: 999px;
      border: 1px solid transparent;
      transition: all 0.2s ease;
    }}

    .nav-pill:hover {{
      color: var(--text-primary);
      background: rgba(255, 255, 255, 0.06);
      border-color: var(--border-color);
    }}

    .guide-steps {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }}

    .step-card {{
      background: rgba(3, 7, 18, 0.55);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }}

    .step-title-row {{
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.75rem;
    }}

    .step-num {{
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
      color: #030712;
      font-weight: 800;
      font-size: 0.8rem;
      margin-right: 0.5rem;
      flex-shrink: 0;
    }}

    .step-heading {{
      display: flex;
      align-items: center;
      font-weight: 700;
      font-size: 1rem;
      color: var(--text-primary);
    }}

    .step-note {{
      margin: 0;
      font-size: 0.86rem;
      color: var(--text-secondary);
      line-height: 1.5;
    }}

    .module-pills {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(175px, 1fr));
      gap: 0.65rem;
      margin-bottom: 1.5rem;
    }}

    .module-pill {{
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      padding: 0.7rem 0.9rem;
    }}

    .module-pill code {{
      color: var(--accent-cyan);
      font-weight: 600;
      font-size: 0.85rem;
    }}

    .module-pill p {{
      margin: 0.25rem 0 0;
      font-size: 0.78rem;
      color: var(--text-secondary);
      line-height: 1.35;
    }}

    .guide-tabs {{
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.85rem;
    }}

    .tab-btn {{
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.45rem 0.9rem;
      border-radius: 8px;
      cursor: pointer;
      font-family: var(--font-sans);
      font-size: 0.85rem;
      font-weight: 600;
      transition: all 0.2s ease;
    }}

    .tab-btn:hover {{
      color: var(--text-primary);
      background: rgba(255, 255, 255, 0.08);
    }}

    .tab-btn.active {{
      color: #030712;
      background: linear-gradient(135deg, var(--accent-cyan), #38bdf8);
      border-color: transparent;
    }}

    .tab-panel {{
      display: none;
    }}

    .tab-panel.active {{
      display: block;
    }}

    .code-block-wrap {{
      position: relative;
    }}

    .code-block-wrap .btn-copy-abs {{
      position: absolute;
      top: 0.65rem;
      right: 0.65rem;
      z-index: 2;
    }}

    .callout-warn {{
      margin-top: 1.25rem;
      padding: 0.85rem 1rem;
      border-radius: 10px;
      background: rgba(249, 115, 22, 0.08);
      border: 1px solid rgba(249, 115, 22, 0.25);
      color: #fdba74;
      font-size: 0.86rem;
      line-height: 1.5;
    }}

    .callout-warn code {{
      color: #fed7aa;
    }}

    @media(max-width: 768px) {{
      .guide-section {{ padding: 1.25rem; }}
      .guide-steps {{ grid-template-columns: 1fr; }}
    }}

    .failures-dashboard {{
      background: rgba(239, 68, 68, 0.02);
      border: 1px dashed rgba(239, 68, 68, 0.15);
      border-radius: 16px;
      padding: 1.5rem;
      margin-bottom: 3.5rem;
    }}

    .failures-title {{
      color: var(--accent-red);
      font-weight: 700;
      margin: 0 0 1rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.25rem;
    }}

    .failed-builds-container {{
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 1rem;
    }}

    .failed-run-card {{
      background: rgba(239, 68, 68, 0.04);
      border: 1px solid rgba(239, 68, 68, 0.12);
      border-radius: 12px;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 0.75rem;
      transition: all 0.2s ease;
    }}

    .failed-run-card:hover {{
      background: rgba(239, 68, 68, 0.07);
      border-color: rgba(239, 68, 68, 0.25);
    }}

    .failed-run-header {{
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      color: var(--text-secondary);
    }}

    .failed-version {{
      font-weight: 600;
      color: var(--accent-red);
    }}

    .failed-commit {{
      font-size: 0.9rem;
      font-weight: 500;
      color: var(--text-primary);
      word-break: break-word;
      line-height: 1.4;
    }}

    .failed-action {{
      text-align: right;
    }}

    .btn-error-logs {{
      color: var(--accent-cyan);
      text-decoration: none;
      font-size: 0.85rem;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }}

    .btn-error-logs:hover {{
      text-decoration: underline;
    }}

    .no-failures {{
      grid-column: 1 / -1;
      text-align: center;
      padding: 2rem;
      background: rgba(16, 185, 129, 0.02);
      border: 1px solid rgba(16, 185, 129, 0.08);
      border-radius: 12px;
      color: var(--accent-green);
      font-weight: 500;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
    }}

    .no-failures-icon {{
      font-size: 1.5rem;
    }}

    .table-container {{
      width: 100%;
      overflow-x: auto;
      border-radius: 16px;
      border: 1px solid var(--border-color);
      box-shadow: var(--card-shadow);
      background: var(--bg-card);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
    }}

    table {{
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }}

    th, td {{
      padding: 1rem 1.25rem;
      border-bottom: 1px solid var(--border-color);
      vertical-align: middle;
    }}

    th {{
      background: rgba(3, 7, 18, 0.4);
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text-secondary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }}

    tr:last-child td {{
      border-bottom: none;
    }}

    tr.latest-row {{
      background: rgba(16, 185, 129, 0.03);
    }}

    tr.latest-row:hover {{
      background: rgba(16, 185, 129, 0.06);
    }}

    tr:hover {{
      background: rgba(255, 255, 255, 0.02);
    }}

    .version-cell {{
      font-weight: 700;
    }}

    .badge {{
      display: inline-block;
      padding: 0.15rem 0.5rem;
      border-radius: 999px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.02em;
    }}

    .badge-success {{
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.2);
    }}

    td.message {{
      max-width: 380px;
      font-size: 0.92rem;
      color: var(--text-secondary);
      word-break: break-word;
    }}

    td.when {{
      font-size: 0.88rem;
      color: var(--text-secondary);
      min-width: 180px;
    }}

    .muted {{
      font-size: 0.8rem;
      color: var(--text-muted);
      margin-top: 0.25rem;
      display: block;
    }}

    .commit-link {{
      color: var(--accent-cyan);
      text-decoration: none;
      font-family: var(--font-mono);
      font-weight: 500;
    }}

    .commit-link:hover {{
      text-decoration: underline;
    }}

    .code-cell {{
      min-width: 320px;
    }}

    .no-data {{
      text-align: center;
      color: var(--text-muted);
      padding: 3rem;
    }}

    footer {{
      margin-top: 5rem;
      border-top: 1px solid var(--border-color);
      padding: 2rem 0;
      text-align: center;
      color: var(--text-muted);
      font-size: 0.88rem;
    }}

    footer a {{
      color: var(--accent-cyan);
      text-decoration: none;
    }}

    footer a:hover {{
      text-decoration: underline;
    }}
  </style>
</head>
<body>
  <header>
    <div class="header-container">
      <div class="logo-container">
        <span class="logo-dot"></span>
        <span class="logo-text">EazyCmp</span>
      </div>
      <nav class="header-nav">
        <a href="#install-guide" class="nav-pill">Install &amp; Setup</a>
        <a href="#dashboard" class="nav-pill">Dashboard</a>
        <a href="#versions" class="nav-pill">Versions</a>
        <a href="{GITHUB_REPO}" target="_blank" class="github-btn-link">GitHub &rarr;</a>
      </nav>
    </div>
  </header>

  <main>
    <section class="hero-section">
      <h1 class="hero-title">EazyCmp Maven &amp; Setup Guide</h1>
      <p class="hero-desc">Kotlin Multiplatform (KMP) toolkit for Android, iOS, Desktop (JVM) &amp; Web (JS / Wasm). Hosted live on GitHub Pages.</p>
    </section>

    <!-- Top Install & Complete Setup Guide -->
    <section id="install-guide" class="guide-section">
      <div class="guide-header">
        <div>
          <span class="guide-badge">Quick Start &bull; v{html.escape(ver)}</span>
          <h2 class="guide-title">How to Install &amp; Use EazyCmp</h2>
          <p class="guide-subtitle">Add the repository, pick either the full <code>shared</code> bundle or light modules, initialize per platform, and call any feature from <code>EazyCmp</code>.</p>
        </div>
      </div>

      <!-- Step 1 & Step 2 -->
      <div class="guide-steps">
        <div class="step-card">
          <div class="step-title-row">
            <span class="step-heading"><span class="step-num">1</span>Add Maven Repository (<code>settings.gradle.kts</code>)</span>
            <button class="btn-copy" onclick="copyCode('code-repo')" data-copy-id="code-repo">Copy</button>
          </div>
          <p class="step-note">Register EazyCmp's GitHub Pages Maven repository inside <code>dependencyResolutionManagement</code>.</p>
          <pre><code id="code-repo">{html.escape(setup_repo_snippet)}</code></pre>
        </div>

        <div class="step-card">
          <div class="step-title-row">
            <span class="step-heading"><span class="step-num">2</span>Add Dependency (<code>build.gradle.kts</code>)</span>
            <button class="btn-copy" onclick="copyCode('code-dep')" data-copy-id="code-dep">Copy</button>
          </div>
          <p class="step-note">Use <code>shared</code> for everything, or pick individual modules for smaller app binaries.</p>
          <pre><code id="code-dep">{html.escape(setup_dep_snippet)}</code></pre>
        </div>
      </div>

      <!-- Available Modules Overview -->
      <div class="module-pills">
        <div class="module-pill">
          <code>:shared</code>
          <p>Full umbrella (re-exports <code>core</code> + <code>platform</code> + <code>ui</code> + <code>EazyCmp</code> facade).</p>
        </div>
        <div class="module-pill">
          <code>:core</code>
          <p>Ktor API client, SQLDelight, cache, offline queue, AES-GCM storage &amp; validators.</p>
        </div>
        <div class="module-pill">
          <code>:platform</code>
          <p>Permissions, media picker, GPS, biometrics, QR, PDF, deep links &amp; adapters.</p>
        </div>
        <div class="module-pill">
          <code>:ui</code>
          <p>Compose UI kit, Material 3 theme, snackbars, bottom sheets, Coil/SVG/Lottie images.</p>
        </div>
        <div class="module-pill">
          <code>:web</code>
          <p>Lightweight JS &amp; WasmJs helpers (<code>WebEazyCmp</code> storage, picker, plugin registry).</p>
        </div>
      </div>

      <!-- Step 3: Platform Initialization -->
      <div class="step-card" style="margin-bottom: 1.25rem;">
        <div class="step-title-row">
          <span class="step-heading"><span class="step-num">3</span>Initialize Per Platform (Before <code>startKoin</code>)</span>
        </div>
        <div class="guide-tabs" data-tab-group="platform">
          <button class="tab-btn active" onclick="switchTab('platform', 'android', this)">Android</button>
          <button class="tab-btn" onclick="switchTab('platform', 'ios', this)">iOS</button>
          <button class="tab-btn" onclick="switchTab('platform', 'desktop', this)">Desktop (JVM)</button>
          <button class="tab-btn" onclick="switchTab('platform', 'web', this)">Web (JS / Wasm)</button>
        </div>

        <div id="tab-platform-android" class="tab-panel active code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-init-android')" data-copy-id="code-init-android">Copy</button>
          <pre><code id="code-init-android">{html.escape(android_setup_snippet)}</code></pre>
        </div>
        <div id="tab-platform-ios" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-init-ios')" data-copy-id="code-init-ios">Copy</button>
          <pre><code id="code-init-ios">{html.escape(ios_setup_snippet)}</code></pre>
        </div>
        <div id="tab-platform-desktop" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-init-desktop')" data-copy-id="code-init-desktop">Copy</button>
          <pre><code id="code-init-desktop">{html.escape(desktop_setup_snippet)}</code></pre>
        </div>
        <div id="tab-platform-web" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-init-web')" data-copy-id="code-init-web">Copy</button>
          <pre><code id="code-init-web">{html.escape(web_setup_snippet)}</code></pre>
        </div>
      </div>

      <!-- Step 4: How to Use in Your App -->
      <div class="step-card">
        <div class="step-title-row">
          <span class="step-heading"><span class="step-num">4</span>Use Features in Common Code</span>
        </div>
        <div class="guide-tabs" data-tab-group="usage">
          <button class="tab-btn active" onclick="switchTab('usage', 'root', this)">Compose Root &amp; Theme</button>
          <button class="tab-btn" onclick="switchTab('usage', 'api', this)">API &amp; File Upload</button>
          <button class="tab-btn" onclick="switchTab('usage', 'storage', this)">Storage &amp; Permissions</button>
          <button class="tab-btn" onclick="switchTab('usage', 'spi', this)">Plugin SPI &amp; Adapters</button>
        </div>

        <div id="tab-usage-root" class="tab-panel active code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-use-root')" data-copy-id="code-use-root">Copy</button>
          <pre><code id="code-use-root">{html.escape(usage_root_snippet)}</code></pre>
        </div>
        <div id="tab-usage-api" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-use-api')" data-copy-id="code-use-api">Copy</button>
          <pre><code id="code-use-api">{html.escape(usage_api_snippet)}</code></pre>
        </div>
        <div id="tab-usage-storage" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-use-storage')" data-copy-id="code-use-storage">Copy</button>
          <pre><code id="code-use-storage">{html.escape(usage_storage_snippet)}</code></pre>
        </div>
        <div id="tab-usage-spi" class="tab-panel code-block-wrap">
          <button class="btn-copy btn-copy-abs" onclick="copyCode('code-use-spi')" data-copy-id="code-use-spi">Copy</button>
          <pre><code id="code-use-spi">{html.escape(usage_spi_snippet)}</code></pre>
        </div>
      </div>

      <div class="callout-warn">
        <strong>iOS Target Note:</strong> Built with Compose Multiplatform 1.11.1+, so <code>iosX64()</code> is not supported. Use <code>iosArm64()</code> and <code>iosSimulatorArm64()</code> in your consumer app's <code>build.gradle.kts</code>.
        For Play Store / App Store permission compliance, see <a href="{GITHUB_REPO}/blob/master/docs/STORE_COMPLIANCE.md" target="_blank" style="color: #fed7aa; font-weight: 600;">STORE_COMPLIANCE.md</a>.
      </div>
    </section>

    <h2 id="dashboard" class="section-title"><span class="section-title-icon"></span>Release Dashboard</h2>
    <section class="dashboard-grid">
      <!-- Card 1: Latest Version -->
      <div class="card card-latest">
        <div class="card-label">
          <span>Latest Release</span>
          <span class="status-badge success" style="padding: 0.1rem 0.5rem; font-size: 0.7rem;">
            <span class="status-indicator success"></span> LIVE
          </span>
        </div>
        <div class="card-value" style="font-family: var(--font-mono); font-size: 1.6rem;">{html.escape(latest or "—")}</div>
        <div class="card-subtext" id="latest-published-time">Published: {updated_at or "—"}</div>
      </div>

      <!-- Card 2: Release Statistics -->
      <div class="card card-stats">
        <div class="card-label">Update Frequency</div>
        <div class="card-value" id="update-frequency-val">{frequency}</div>
        <div class="card-subtext">Total cataloged releases: {len(releases)}</div>
      </div>

      <!-- Card 3: Usage & Downloads -->
      <div class="card card-metrics">
        <div class="card-label">Usage &amp; Downloads</div>
        <div class="metric-item">
          <span class="card-subtext">GitHub Stars:</span>
          <strong id="stars-count" style="color: var(--accent-cyan)">—</strong>
        </div>
        <div class="metric-item">
          <span class="card-subtext">Repository Forks:</span>
          <strong id="forks-count" style="color: var(--accent-purple)">—</strong>
        </div>
        <div class="metric-item" style="margin-top: 0.25rem;">
          <span class="card-subtext">Weekly Downloads:</span>
          <img src="https://jitpack.io/v/Imajy/eazyCmp/week.svg?style=flat-square" alt="Weekly Downloads" style="border-radius: 4px; vertical-align: middle;" />
        </div>
        <div class="metric-item" style="margin-top: 0.25rem;">
          <span class="card-subtext">Monthly Downloads:</span>
          <img src="https://jitpack.io/v/Imajy/eazyCmp/month.svg?style=flat-square" alt="Monthly Downloads" style="border-radius: 4px; vertical-align: middle;" />
        </div>
        <div class="metric-item" style="margin-top: 0.25rem;">
          <span class="card-subtext">Total Downloads:</span>
          <img src="https://img.shields.io/badge/Total_Downloads-{html.escape(total_downloads)}-06b6d4?style=flat-square" alt="Total Downloads" style="border-radius: 4px; vertical-align: middle;" />
        </div>
        <div style="margin-top: 0.5rem; display: flex; flex-direction: column; align-items: center; gap: 0.5rem;">
          <img src="https://komarev.com/ghpvc/?username=Imajy&amp;repo=eazyCmp&amp;color=06b6d4&amp;style=flat-square&amp;label=Dashboard+Views" alt="Dashboard Views" style="border-radius: 4px; display: inline-block; vertical-align: middle;" />
        </div>
      </div>

      <!-- Card 4: Build Status -->
      <div class="card card-build">
        <div class="card-label">Latest Build Status</div>
        <div class="card-value" style="font-size: 1.4rem;">
          <span id="build-status" class="status-badge loading">
            <span class="status-indicator warning"></span> Checking...
          </span>
        </div>
        <div class="card-subtext" id="build-commit-sub">—</div>
      </div>
    </section>

    <!-- Failures Dashboard section -->
    <div class="failures-dashboard">
      <h3 class="failures-title">
        <svg height="18" width="18" viewBox="0 0 16 16" fill="currentColor" style="display:inline-block;vertical-align:middle;color:var(--accent-red)">
          <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"></path>
          <path d="M7.002 11a1 1 0 1 1 2 0 1 1 0 0 1-2 0zM7.1 4.995a.905.905 0 1 1 1.8 0l-.35 3.507a.553.553 0 0 1-1.1 0L7.1 4.995z"></path>
        </svg>
        Recent Build Failures Log
      </h3>
      <div id="failed-builds-list" class="failed-builds-container">
{failures_html}
      </div>
    </div>

    <!-- Version History -->
    <h2 id="versions" class="section-title"><span class="section-title-icon"></span>Version History</h2>
    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>Version</th>
            <th>Release Message</th>
            <th>Published</th>
            <th>Dependency Line</th>
          </tr>
        </thead>
        <tbody>
{version_rows}
        </tbody>
      </table>
    </div>
  </main>

  <footer>
    <p>&copy; {datetime.now().year} EazyCmp. Hosted via GitHub Pages. Manifest: <a href="versions.json" target="_blank">versions.json</a> &bull; Docs: <a href="{GITHUB_REPO}#readme" target="_blank">README</a></p>
  </footer>

  <script>
    function switchTab(group, tabId, btn) {{
      const groupButtons = document.querySelectorAll(`[data-tab-group="${{group}}"] .tab-btn`);
      groupButtons.forEach(b => b.classList.remove("active"));
      if (btn) btn.classList.add("active");

      const prefix = `tab-${{group}}-`;
      document.querySelectorAll(`[id^="${{prefix}}"]`).forEach(panel => {{
        panel.classList.toggle("active", panel.id === `${{prefix}}${{tabId}}`);
      }});
    }}

    function copyCode(id) {{
      const code = document.getElementById(id).innerText;
      navigator.clipboard.writeText(code).then(() => {{
        const btn = document.querySelector(`[data-copy-id="${{id}}"]`);
        const originalText = btn.innerText;
        btn.innerText = "Copied!";
        btn.style.background = "var(--accent-green)";
        btn.style.borderColor = "var(--accent-green)";
        setTimeout(() => {{
          btn.innerText = originalText;
          btn.style.background = "";
          btn.style.borderColor = "";
        }}, 1500);
      }});
    }}

    async function fetchGithubStats() {{
      const repo = "Imajy/eazyCmp";
      try {{
        // 1. Fetch Repository Details
        const repoRes = await fetch(`https://api.github.com/repos/${{repo}}`);
        if (repoRes.ok) {{
          const repoData = await repoRes.json();
          document.getElementById("stars-count").innerText = repoData.stargazers_count.toLocaleString();
          document.getElementById("forks-count").innerText = repoData.forks_count.toLocaleString();
        }}

        // 2. Fetch Actions runs for Build Status and Failures
        const runsRes = await fetch(`https://api.github.com/repos/${{repo}}/actions/runs?per_page=20`);
        if (runsRes.ok) {{
          const runsData = await runsRes.json();
          const runs = runsData.workflow_runs || [];
          
          // Filter runs for the publish workflow named "Publish Multiplatform Artifacts"
          const publishRuns = runs.filter(run => 
            run.name === "Publish Multiplatform Artifacts" || 
            run.path.includes("publish.yml")
          );

          // Render Latest Status
          if (publishRuns.length > 0) {{
            const latestRun = publishRuns[0];
            const statusBadge = document.getElementById("build-status");
            const commitSub = document.getElementById("build-commit-sub");
            
            commitSub.innerText = `Run #${{latestRun.run_number}} (Commit: ${{latestRun.head_commit.message.split('\\n')[0].substring(0, 30)}}...)`;
            
            if (latestRun.status === "completed" || latestRun.status === "success" || latestRun.status === "failure") {{
              if (latestRun.conclusion === "success") {{
                statusBadge.innerHTML = '<span class="status-indicator success"></span> Passing';
                statusBadge.className = "status-badge success";
              }} else if (latestRun.conclusion === "failure") {{
                statusBadge.innerHTML = '<span class="status-indicator failure"></span> Failing';
                statusBadge.className = "status-badge failure";
              }} else {{
                statusBadge.innerHTML = `<span class="status-indicator warning"></span> ${{latestRun.conclusion || latestRun.status}}`;
                statusBadge.className = "status-badge warning";
              }}
            }} else {{
              statusBadge.innerHTML = '<span class="status-indicator success"></span> Running...';
              statusBadge.className = "status-badge success";
            }}
          }}
        }}
      }} catch (error) {{
        console.error("Failed to load dashboard metrics:", error);
      }}
    }}

    function getRelativeTime(date) {{
      const seconds = Math.floor((new Date() - date) / 1000);
      let interval = Math.floor(seconds / 31536000);
      if (interval >= 1) return interval + " years ago";
      interval = Math.floor(seconds / 2592000);
      if (interval >= 1) return interval + " months ago";
      interval = Math.floor(seconds / 86400);
      if (interval >= 1) return interval + " days ago";
      interval = Math.floor(seconds / 3600);
      if (interval >= 1) return interval + " hours ago";
      interval = Math.floor(seconds / 60);
      if (interval >= 1) return interval + " minutes ago";
      return "just now";
    }}

    async function updateDynamicStats() {{
      try {{
        const response = await fetch("versions.json");
        if (!response.ok) return;
        const data = await response.json();
        const releases = data.releases || [];
        if (releases.length === 0) return;

        const validTimes = [];
        for (const r of releases) {{
          if (r.publishedAt) {{
            const d = new Date(r.publishedAt.replace("Z", "+00:00"));
            if (!isNaN(d)) {{
              validTimes.push(d);
            }}
          }}
        }}
        
        if (validTimes.length > 0) {{
          validTimes.sort((a, b) => a - b);
          const intervals = [];
          for (let i = 0; i < validTimes.length - 1; i++) {{
            intervals.push((validTimes[i+1] - validTimes[i]) / 1000);
          }}
          
          const latestReleaseDate = validTimes[validTimes.length - 1];
          const timeSinceLatest = (new Date() - latestReleaseDate) / 1000;
          if (timeSinceLatest > 0) {{
            intervals.push(timeSinceLatest);
          }}
          
          const avgSeconds = intervals.reduce((a, b) => a + b, 0) / intervals.length;
          const avgDays = avgSeconds / 86400.0;
          
          let freqStr = "";
          if (avgDays < 0.04) {{
            const minutes = avgDays * 1440.0;
            freqStr = `Every ${{Math.max(1, Math.round(minutes))}} mins`;
          }} else if (avgDays < 1) {{
            const hours = avgDays * 24.0;
            freqStr = `Every ${{hours.toFixed(1)}} hours`;
          }} else if (avgDays < 7) {{
            freqStr = `Every ${{avgDays.toFixed(1)}} days`;
          }} else {{
            const weeks = avgDays / 7.0;
            freqStr = `Every ${{weeks.toFixed(1)}} weeks`;
          }}
          
          const freqEl = document.getElementById("update-frequency-val");
          if (freqEl) {{
            freqEl.textContent = freqStr;
          }}
          
          const latestPubEl = document.getElementById("latest-published-time");
          if (latestPubEl) {{
            const relative = getRelativeTime(latestReleaseDate);
            const day = String(latestReleaseDate.getUTCDate()).padStart(2, '0');
            const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
            const month = months[latestReleaseDate.getUTCMonth()];
            const year = latestReleaseDate.getUTCFullYear();
            const hours = String(latestReleaseDate.getUTCHours()).padStart(2, '0');
            const mins = String(latestReleaseDate.getUTCMinutes()).padStart(2, '0');
            const utcDateStr = `${{day}} ${{month}} ${{year}}, ${{hours}}:${{mins}} UTC`;
            
            latestPubEl.innerHTML = `Published: ${{utcDateStr}} <span class="muted" style="font-size:0.85em; opacity:0.8;">(${{relative}})</span>`;
          }}
        }}
      }} catch (err) {{
        console.error("Error updating dynamic stats:", err);
      }}
    }}

    // Load metrics on page load
    window.addEventListener("DOMContentLoaded", () => {{
      fetchGithubStats();
      updateDynamicStats();
    }});
  </script>
</body>
</html>
"""
    (site_dir / "index.html").write_text(index_html)
    (site_dir / ".nojekyll").touch(exist_ok=True)
    print(f"Generated Pages site in {site_dir} (latest={latest}, releases={len(releases)})")


if __name__ == "__main__":
    main()
