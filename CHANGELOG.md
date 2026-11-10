# Changelog

All notable changes to this project are documented here.

---

## 1.0.0.1-rc-060

### Critical fixes
- **Desktop SecureStorage**: AES-GCM encryption with key at `~/.eazycmp/secure.key` (no more plaintext prefs)
- **Desktop permissions**: no longer always `GRANTED` — uses `NOT_REQUIRED` / `UNAVAILABLE` / camera detection
- **Stub clarity**: QR scanner, push token, OTP auto-read marked `@HostRequired` with install hints

### Architecture
- **Plugin SPI** consolidated in `core` (`EazyCmp.install`, `EazyPlugin`, `EazyRegistry`, `EazyServiceCatalog`)
- **Module split** (light apps): `core` / `platform` / `ui` / `web` / `shared`
- **Built-in adapters**: `AndroidCameraXQrScanner`, `DesktopZxingQrScanner`, `FcmPushTokenService`, `SmsRetrieverOtpAutoRead`, `ApnsPushTokenService`
- **Web parity**: `WebEazyCmp` + `WebSecureStorage` + `WebMediaPicker` (JS / Wasm / JVM)
- **Gradle**: JVM compile + module tests verified locally

### Docs
- README version / platforms / modular install / Plugin SPI / adapters / web
- Host integration sample version sync

---

## 1.0.0

Initial release — API client, settings, file picker, permissions, Compose UI kit, uploads.
