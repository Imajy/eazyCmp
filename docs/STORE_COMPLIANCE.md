# EazyCmp — App Store & Play Store Compliance Guide

This document explains how EazyCmp is designed to avoid common **app rejection** reasons on Google Play and Apple App Store.

**Version:** 1.0.03-alpha-11

---

## Summary: What EazyCmp Does NOT Do (Safe by Design)

| Risk | Status |
|------|--------|
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | **Not used** — system pickers only |
| `READ_SMS` / `RECEIVE_SMS` | **Not used** — OTP auto-read is host-wired, no SMS permission |
| `ACCESS_BACKGROUND_LOCATION` | **Not requested** |
| `QUERY_ALL_PACKAGES` | **Not used** |
| `AD_ID` / advertising ID | **Not collected** |
| Photo Library read (iOS) | **Not required** — PHPicker returns selected items only |
| Tracking / ATT (iOS) | **NSPrivacyTracking = false** in Privacy Manifest |

---

## Android (Google Play)

### 1. Permissions — Host App Declares, Not Library

EazyCmp **removed all sensitive permissions** from the library `AndroidManifest.xml`.  
Merged manifests will NOT force Camera, Location, Contacts, etc. on apps that don't need them.

Use `PermissionManifest` to generate only what you need:

```kotlin
import com.aj.shared.permission.AppPermission
import com.aj.shared.permission.PermissionManifest

val needed = PermissionManifest.androidPermissionsFor(
    setOf(AppPermission.CAMERA, AppPermission.LOCATION)
)
// Copy output into your app's AndroidManifest.xml
```

Template: [`docs/templates/AndroidManifest.permissions.xml`](templates/AndroidManifest.permissions.xml)

### 2. Photo & Document Picker (Play Photo Picker Policy)

- Gallery: `PickVisualMedia` — no storage permission
- Documents: `OpenDocument` — no storage permission
- **Do not** add `READ_MEDIA_*` or `MANAGE_EXTERNAL_STORAGE` for EazyCmp picker flows

### 3. Camera FileProvider

Library ships a FileProvider at `${applicationId}.eazycmp.camera` for temp camera files.  
No extra host setup required unless you override authorities.

### 4. Location

- `getCurrentLocation()` — single fix, foreground use
- `observeLocation()` — **foreground only**; set `LocationPolicy.isInForeground` from your `LifecycleObserver`
- Do **not** use `observeLocation` in background without your own Foreground Service + `ACCESS_BACKGROUND_LOCATION` declaration and Play declaration form

### 5. Contacts & Microphone

Only declare if your app actually uses these features. Play Console requires a **declared use case** for sensitive permissions.

### 6. ProGuard / R8

Consumer rules ship with the AAR: `consumer-rules.pro`. No extra setup needed.

### 7. Data Safety Form (Play Console)

EazyCmp may access (depending on features you enable):

| Data | Purpose | Encrypted |
|------|---------|-----------|
| Photos/docs user picks | Upload/KYC | In transit (HTTPS) |
| Location (if used) | Branch finder | — |
| Tokens in SecureStorage | Auth | Yes (EncryptedSharedPreferences) |

You are responsible for declaring what **your app** collects. EazyCmp provides `ConsentManager` and `exportUserData()` helpers.

---

## iOS (App Store)

### 1. Privacy Manifest

`PrivacyInfo.xcprivacy` is bundled with the iOS target. It declares:

- No tracking (`NSPrivacyTracking = false`)
- Required Reason APIs: UserDefaults, file timestamps, boot time (for cache/settings)

### 2. Usage Description Strings (Required)

Add to your app's `Info.plist` only for features you use:

```kotlin
PermissionManifest.iosUsageStringsFor(setOf(AppPermission.CAMERA, AppPermission.LOCATION))
```

Template: [`docs/templates/Info.plist.usage-strings.xml`](templates/Info.plist.usage-strings.xml)

**Missing usage strings = automatic rejection.**

### 3. Photo Library

PHPicker is used — **no `NSPhotoLibraryUsageDescription` needed** for gallery pick.

### 4. App Tracking Transparency

EazyCmp does not use IDFA. No `NSUserTrackingUsageDescription` required unless **you** add tracking SDKs.

### 5. Encryption Export

Uses standard HTTPS (Ktor) and Apple Keychain (via settings). Add ITSAppUsesNonExemptEncryption = false in host plist if you only use standard TLS.

---

## Security Features (Review-Safe)

| Feature | Notes |
|---------|-------|
| `ScreenshotBlock` | FLAG_SECURE — allowed for banking/insurance |
| `RootDetection` | Warning only — do not block app access (Play discourages hard blocks) |
| `PiiMask` / `redactPiiFromLog` | API logs redact PAN/Aadhaar/phone when debug enabled |
| `ConsentManager` | GDPR / DPDP consent timestamps |

---

## Checklist Before Submitting

### Android
- [ ] Only declare permissions from `PermissionManifest` for features you use
- [ ] No `READ_MEDIA_IMAGES` unless absolutely required outside EazyCmp
- [ ] Data Safety form matches actual collection
- [ ] Location: foreground only, or proper background declaration
- [ ] `EazyCmp.init(context)` called before Koin

### iOS
- [ ] All usage strings present for enabled features
- [ ] Privacy Nutrition Labels match data collection
- [ ] Privacy Manifest merged (automatic with EazyCmp framework)
- [ ] No photo library permission for gallery-only flows

---

## Support

Open an issue with label `store-compliance` if a reviewer cites EazyCmp specifically.
