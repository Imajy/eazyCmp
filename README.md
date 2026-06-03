<p align="center">
  <img src="assets/logo.png" width="180"/>
</p>

<h1 align="center">EazyCMP</h1>

<p align="center">
  Kotlin Multiplatform Toolkit for API, UI, Permissions, Media Picker & PDF Generation
</p>

<div align="center">
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-blue" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Multiplatform-green" alt="Compose" />
  <img src="https://img.shields.io/badge/Ktor-Client-orange" alt="Ktor" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
  <img src="https://img.shields.io/badge/version-1.0.03--alpha--10-purple" alt="Version" />
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20iOS%20%7C%20Desktop-blue" alt="Platform" />
</div>

---

**EazyCMP** is a comprehensive Kotlin Multiplatform (KMP) toolkit designed to eliminate boilerplate code when building mobile (Android & iOS) and desktop applications. It provides unified, out-of-the-box abstractions for networking, storage, permission handling, file picking, and premium Compose Multiplatform UI components.

## Table of Contents
1. [Installation](#1-installation)
2. [Setup & Dependency Injection](#2-setup--dependency-injection)
3. [Networking Module (`api`)](#3-networking-module-api)
4. [Storage / State Module (`storage`)](#4-storage--state-module-storage)
5. [Permissions Module (`permission`)](#5-permissions-module-permission)
6. [Media Picker (`picker`)](#6-media-picker-picker)
7. [PDF Generator (`print`)](#7-pdf-generator-print)
8. [Compose UI Kit Components](#8-compose-ui-kit-components)
9. [Custom Modifiers](#9-custom-modifiers)
10. [Extensions](#10-extensions)

---

## 1. Installation

Add the dependency to your shared module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.Imajy:eazyCmp:1.0.03-alpha-10")
        }
    }
}
```

Make sure your repository list includes JitPack:
```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

---

## 2. Setup & Dependency Injection

EazyCMP uses **Koin** internally for Dependency Injection and **Multiplatform Settings** for local storage.

### Initialize Storage Name
Before launching dependency injection, configure your local settings file name:
```kotlin
import com.aj.shared.api.initSettingsName

initSettingsName("my_custom_app_pref")
```

### Configure Koin
To register the library's core dependencies, load `eazyModule()` inside your app's dependency injection startup:
```kotlin
import com.aj.shared.api.eazyModule
import org.koin.core.context.startKoin

startKoin {
    modules(
        eazyModule() // Registers ApiClient, SharedViewModel, Settings, etc.
    )
}
```

---

## 3. Networking Module (`api`)

EazyCMP simplifies Ktor network requests with base URL configuration, default parameter merging, custom file uploading, and priority dispatch queues.

### Base URL Registration
You can register multiple API endpoints with different names, default headers, query params, or body arguments:
```kotlin
import com.aj.shared.api.ApiConfig

ApiConfig.registerBaseUrl(
    name = "main_server",
    baseUrl = "https://api.example.com/v1/",
    token = "bearer_auth_token_here",
    defaultHeaders = mapOf("Accept-Language" to "en"),
    defaultQueryParams = mapOf("client" to "mobile"),
    defaultBodyParams = mapOf("version" to "1.0.0")
)
```

To update authorization tokens dynamically (e.g., after login):
```kotlin
ApiConfig.updateToken("main_server", "new_login_token")
```

### Performing API Requests
Inject `ApiClient` and use `.request<RequestType, ResponseType>()` which returns a reactive `Flow<Resource<ResponseType>>`.

#### Standard GET Request
```kotlin
import com.aj.shared.api.ApiClient
import com.aj.shared.api.Resource
import kotlinx.coroutines.flow.collect

val apiClient: ApiClient = get() // Inject via Koin

apiClient.request<Unit, UserResponse>(
    base = "main_server",
    endpoint = "profile"
).collect { resource ->
    when (resource) {
        is Resource.Loading -> println("Loading profile...")
        is Resource.Success -> println("Profile data: ${resource.data}")
        is Resource.Error -> println("Failed to load: ${resource.message}")
    }
}
```

#### POST Request with Body and Dispatch Priority
```kotlin
import com.aj.shared.api.ApiMethod
import com.aj.shared.api.ApiPriority
import com.aj.shared.api.RequestOptions

apiClient.request<LoginRequest, LoginResponse>(
    base = "main_server",
    endpoint = "login",
    method = ApiMethod.POST,
    body = LoginRequest(email = "user@mail.com", password = "123"),
    options = RequestOptions(priority = ApiPriority.HIGH) // HIGH, NORMAL, or LOW priority
)
```

#### Multipart Upload
```kotlin
import com.aj.shared.api.FilePart
import com.aj.shared.api.BodyType

val photoFile = PickedFile(bytes = fileBytes, fileName = "avatar.jpg", mimeType = "image/jpeg")

apiClient.request<UploadMetadata, UploadResponse>(
    base = "main_server",
    endpoint = "upload-avatar",
    method = ApiMethod.POST,
    body = UploadMetadata(userId = "123"),
    files = listOf(
        FilePart(name = "avatar_file", file = photoFile)
    ),
    bodyType = BodyType.FORM_DATA
)
```

### BaseViewModel Integration
EazyCMP's `BaseViewModel` automatically handles loading states, error dialogues, and lifecycle-scoped API collections.
```kotlin
import com.aj.shared.api.BaseViewModel
import androidx.lifecycle.viewModelScope

class ProfileViewModel(private val api: ApiClient) : BaseViewModel() {

    fun loadProfile() {
        api.request<Unit, UserResponse>("main_server", "profile")
            .collectApi(
                scope = viewModelScope,
                showLoading = true,  // Automatically updates baseState.isLoading
                showError = true    // Automatically shows SnackBar error messages
            ) { profileData ->
                // Handle success payload safely
                println("Profile name: ${profileData?.name}")
            }
    }
}
```

---

## 4. Storage / State Module (`storage`)

EazyCMP includes `SharedViewModel` to securely store strings and serialized objects natively.

```kotlin
import com.aj.shared.api.SharedViewModel
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val token: String, val username: String)

val sharedViewModel: SharedViewModel = get() // Inject via Koin

// Save data
sharedViewModel.setString("user_role", "admin")
sharedViewModel.setObject("session_data", UserSession("tok_99", "Ajay"))

// Retrieve data
val role = sharedViewModel.getString("user_role") // returns "" if empty
val session = sharedViewModel.getObject<UserSession>("session_data") // returns null if empty

// Clear preferences
sharedViewModel.clear()
```

---

## 5. Permissions Module (`permission`)

Handle OS runtime permissions uniformly across Android & iOS inside your Composable views.

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.aj.shared.permission.PermissionManager
import com.aj.shared.permission.AppPermission
import com.aj.shared.permission.PermissionStatus
import kotlinx.coroutines.launch

@Composable
fun PermissionDemo() {
    val scope = rememberCoroutineScope()
    val permissionManager = remember { PermissionManager() }
    
    // 1. Mandatory Launcher Registration (must be done in Composable scope)
    permissionManager.RegisterPermissionLauncher()

    Button(onClick = {
        scope.launch {
            // 2. Request permissions programmatically
            permissionManager.requestPermissions(
                permissions = listOf(AppPermission.CAMERA, AppPermission.LOCATION)
            ) { results ->
                results.forEach { result ->
                    when (result.status) {
                        PermissionStatus.GRANTED -> println("${result.permission} granted!")
                        PermissionStatus.DENIED -> println("${result.permission} denied.")
                        PermissionStatus.PERMANENTLY_DENIED -> println("Permanently denied.")
                    }
                }
            }
        }
    }) {
        Text("Request Permissions")
    }
}
```

---

## 6. Media Picker (`picker`)

Pick files and images without requesting system permissions or writing platform-specific code.

### PlatformMediaPicker Usage
```kotlin
import androidx.compose.runtime.remember
import com.aj.shared.picker.PlatformMediaPicker
import com.aj.shared.picker.PickerType
import com.aj.shared.picker.DocumentConfig

@Composable
fun FilePickerDemo() {
    val mediaPicker = remember { PlatformMediaPicker() }
    
    // 1. Register Launchers
    mediaPicker.RegisterLaunchers()

    Button(onClick = {
        // 2. Launch Picker
        mediaPicker.launch(
            type = PickerType.DOCUMENT,
            documentConfig = DocumentConfig(mimeTypes = listOf("application/pdf"))
        ) { pickedFile ->
            pickedFile?.let {
                println("Picked file name: ${it.fileName}")
                println("Size: ${it.sizeInMb} MB")
                println("Is Image: ${it.isImage}")
                println("Is PDF: ${it.isPdf}")
                
                // Helper to convert to Base64 String
                val base64 = it.toBase64Image()
            }
        }
    }) {
        Text("Pick PDF File")
    }
}
```

### Complete Upload Bottom Sheet
EazyCMP provides a ready-made `CommonAttachmentBottomSheet` that automatically handles permission checks and opens CAMERA, GALLERY, or STORAGE pickers under the hood.
```kotlin
import com.aj.shared.picker.CommonAttachmentBottomSheet
import com.aj.shared.permission.AppPermission

@Composable
fun DocumentUploadScreen() {
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        CommonAttachmentBottomSheet(
            permissions = listOf(AppPermission.CAMERA, AppPermission.GALLERY, AppPermission.STORAGE),
            onFilePicked = { file ->
                showSheet = false
                if (file != null) {
                    println("Ready to upload: ${file.fileName}")
                }
            }
        )
    }
}
```

---

## 7. PDF Generator (`print`)

Generate, view, download, or share PDFs dynamically from any Compose Multiplatform layout hierarchy using `PdfManager`.

```kotlin
import androidx.compose.runtime.remember
import com.aj.shared.print.rememberPdfManager

@Composable
fun ReceiptScreen() {
    val pdfManager = rememberPdfManager()

    Button(onClick = {
        pdfManager.generateAndShare(
            fileName = "receipt.pdf",
            onStart = { println("Generating PDF started...") },
            onComplete = { println("PDF share dialog displayed.") }
        ) {
            // Write standard Compose layout to compile directly into a PDF page
            Column(modifier = Modifier.padding(24.dp)) {
                Text("INVOICE #99827", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text("Thank you for your purchase!")
            }
        }
    }) {
        Text("Generate & Share PDF")
    }
}
```

---

## 8. Compose UI Kit Components

EazyCMP provides a suite of ready-to-use, polished components built with Modern Design Guidelines.

### `CustomScaffold`
A wrapper around Material3 Scaffold with built-in loading states, background gradients, custom top-bars, back buttons, and actions.
```kotlin
import com.aj.shared.ui.CustomScaffold

CustomScaffold(
    title = "Dashboard",
    showBack = true,
    onBackClick = { /* Go Back */ },
    isLoading = viewModel.baseState.value.isLoading, // Shows custom loading dialog overlay
    gradient = myCustomBrushBackground, // Screen background gradient
    action1Img = myVectorOrPainter,
    action1Click = { /* Action clicked */ }
) { padding ->
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Main screen content goes here")
    }
}
```

### `CommonDropDown` (with Smart Truncation)
Allows both single and multi-select values. In multi-select mode, it dynamically calculates its actual width: it displays as many selected items as can fit, appending `+N` for the remaining items (e.g., `"Apple, Orange +2"`).
```kotlin
import com.aj.shared.ui.CommonDropDown

CommonDropDown(
    label = "Fruits",
    placeholder = "Select Fruits",
    items = listOf("Apple", "Orange", "Banana", "Cherry", "Grapes"),
    selectedItems = mySelectedFruitsList,
    isMultiSelect = true,
    itemLabel = { it }, // Custom mapping label function
    onItemsSelected = { selectedList ->
        mySelectedFruitsList = selectedList
    }
)
```

### `EasyDatePicker` / `GenericDatePicker`
Material3 dialog supporting date restrictions (past only, future only, min/max range) and date-range pickers.
```kotlin
import com.aj.shared.ui.EasyDatePicker
import com.aj.shared.ui.DateRestrictionType

EasyDatePicker(
    show = showDatePicker,
    isRangePicker = false,
    restrictionType = DateRestrictionType.FUTURE_ONLY, // Restricts user to select future dates only
    onDismiss = { showDatePicker = false },
    onDateSelected = { startDateMillis, _ ->
        println("Selected date: $startDateMillis")
    }
)
```

### `GenericBottomSheet`
Slide-up modal bottom sheet that consumes focus clears keyboard, and handles dismiss actions.
```kotlin
import com.aj.shared.ui.GenericBottomSheet

GenericBottomSheet(
    show = showSheet,
    title = "Filters",
    onDismiss = { showSheet = false }
) {
    Column {
        Text("Filter options go here")
    }
}
```

### `OutLinedSimpleTextField`
Polished text field with validation, error messages, leading/trailing icons, and focus-handling support.
```kotlin
import com.aj.shared.ui.OutLinedSimpleTextField

OutLinedSimpleTextField(
    value = textVal,
    onValueChange = { textVal = it },
    placeholderText = "Enter full name",
    label = "Full Name",
    error = if (textVal.isEmpty()) "Field cannot be empty" else null,
    modifier = Modifier.fillMaxWidth()
)
```

### `CommonButton`
A clickable button containing start/end icons, custom typography, and built-in click debouncing (prevents double clicking).
```kotlin
import com.aj.shared.ui.CommonButton

CommonButton(
    label = "Save Changes",
    icon = Icons.Default.Done,
    onClick = { saveAction() }
)
```

### `GenericTabs`
A card-shaped tab selector displaying horizontal options with smooth gradient selection transitions.
```kotlin
import com.aj.shared.tab.GenericTabs

GenericTabs(
    selected = activeTab,
    list = listOf("Home", "Payments", "Settings"),
    onTabSelected = { activeTab = it }
)
```

### `CustomImage` (Coil3 + SVG + Lottie)
A highly advanced image wrapper. It automatically parses content types and supports:
- Loading standard network URLs or Local Resource Bytes.
- SVG vectors natively.
- Interactive online or local Lottie Animations (using a simple URL or JSON string).
- Beautiful customized `Placeholder` overlays.
```kotlin
import com.aj.shared.ui.CustomImage
import com.aj.shared.ui.Placeholder

// Network Image
CustomImage(model = "https://example.com/photo.png")

// Animated Lottie URL Loader
CustomImage(
    model = "https://example.com/loading_animation.json",
    placeholder = Placeholder.LottieUrl("https://example.com/fallback.json")
)
```

---

## 9. Custom Modifiers

EazyCMP provides beautiful, custom canvas modifiers to upgrade the design aesthetics of your layouts.

### Animated `beamBorder`
Applies a premium, infinitely running border light effect to any composable box layout. Excellent for notifications, success states, or highlighted cards.
```kotlin
import com.aj.shared.ui.beamBorder
import com.aj.shared.theme.successBrush

Box(
    modifier = Modifier
        .size(200.dp, 80.dp)
        .beamBorder(radius = 10, brush = successBrush)
) {
    Text("Highlighted Premium Card")
}
```

### `dashedBorder`
Applies custom dashed path bounds around widgets.
```kotlin
import com.aj.shared.ui.dashedBorder

Box(
    modifier = Modifier
        .size(100.dp)
        .dashedBorder(color = Color.Gray, strokeWidth = 2.dp, cornerRadius = 8.dp)
)
```

---

## 10. Extensions

Handy utility extensions to format types quickly.

### Date Formatting Extensions
Format date outputs safely using Kotlin Serialization time instances:
```kotlin
import com.aj.shared.extension.toDdMmmYyyy
import com.aj.shared.extension.toServerDate
import com.aj.shared.extension.currentDate

// String / Long timestamp formatting
val formattedDate = "2026-06-03T12:00:00".toDdMmmYyyy() // "03 Jun 2026"
val apiFormat = System.currentTimeMillis().toServerDate() // "2026-06-03"

// Date utilities
val today = currentDate() // "03 Jun 2026"
val currentYear = currentYear() // "2026"
```

### String Formatting Extensions
```kotlin
import com.aj.shared.extension.toTitleCase

val title = "hello world".toTitleCase() // "Hello world"
```

---

## Author
**Ajay Swami**
