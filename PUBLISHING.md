# Publishing EazyCmp

## Coordinates

| Artifact | Gradle dependency |
|----------|-------------------|
| Umbrella | `com.github.Imajy.eazyCmp:shared:<version>` |
| Core | `com.github.Imajy.eazyCmp:core:<version>` |
| UI | `com.github.Imajy.eazyCmp:ui:<version>` |
| Platform | `com.github.Imajy.eazyCmp:platform:<version>` |
| Web | `com.github.Imajy.eazyCmp:web:<version>` |

Version source: `version.properties` (current: **1.0.0.1-rc-060**).

Repository: `https://imajy.github.io/eazyCmp/maven-repo/`

## Local publish

```bash
./gradlew publishAllPublicationsToMavenLocal -Peazycmp.skip.ios=true -x test --parallel
```

## CI publish

`.github/workflows/publish.yml` publishes when `core/`, `ui/`, `platform/`, `shared/`, `web/`, or Gradle config changes.
