EazyCMP

EazyCMP is a Kotlin Multiplatform toolkit that provides a complete foundation for building modern apps using:
  • Compose Multiplatform UI components
  • Ktor-based API client
  • File picker & permissions abstraction
  • Shared Settings storage
  • Base ViewModel architecture
  • Ready-to-use form components
  • Multipart upload utilities
  • Request dispatcher with priority
  • BottomSheet & UI helpers

Designed to reduce boilerplate in:
  • Android
  • iOS
  • Desktop
  • JVM

⸻

Features

Networking
  • Base URL management
  • Automatic headers injection
  • Token management
  • Multipart file upload
  • Query & body merging
  • Request priority queue
  • Unified response wrapper

Storage
  • Multiplatform Settings (SharedPreferences / NSUserDefaults / JVM)
  • SharedViewModel for global state

File Handling
  • Unified PickedFile model
  • File size validation helpers
  • Image / PDF detection
  • Base64 conversion ready

Permissions
  • Camera permission
  • Storage permission
  • Photos permission abstraction
  • Unified API across platforms

Media Picker
  • Camera capture
  • Image gallery picker
  • PDF picker
  • File picker
  • Multi-file selection

Compose Multiplatform UI Kit
  • Custom Scaffold
  • BottomSheet
  • TextField
  • DatePicker
  • Dropdown
  • RadioButton
  • Checkbox
  • Snackbar
  • Image loader
  • Loading indicator
  • Divider
  • Tabs
  • Theme colors
  • Modifier extensions

⸻

Installation

dependencies {

    implementation("com.aj:eazycmp:1.0.0")

}


⸻

Project Structure

eazycmp
│
├── api
│   ├── ApiClient
│   ├── ApiConfig
│   ├── ApiDispatcher
│   ├── ProvideHttpClient
│   ├── mergeBody
│   └── applyDefaults
│
├── storage
│   ├── provideSettings
│   └── SharedViewModel
│
├── file
│   ├── PickedFile
│   └── PlatformMediaPicker
│
├── permission
│   ├── PermissionManager
│   ├── AppPermission
│   └── PermissionCallback
│
├── ui
│   ├── CustomScaffold
│   ├── BottomSheet
│   ├── TextField
│   ├── DropDown
│   ├── DatePicker
│   ├── SnackBar
│   ├── RadioCheckBox
│   ├── Divider
│   ├── Loading
│   ├── CustomImage
│   ├── Tab
│   └── Color
│
└── base
    ├── BaseViewModel
    ├── Resource
    └── NetworkMonitor


⸻

Setup

Initialize settings in Android App:

class App : Application() {

    override fun onCreate() {

        super.onCreate()

        initSettings(this)

    }

}


⸻

Dependency Injection

val coreModule = module {

    single { json }

    single {

        provideSettings("app_settings")

    }

    single {

        SharedViewModel(

            settings = get(),

            json = get()

        )

    }

    single {

        ApiClient()

    }

}

Start Koin:

startKoin {

    modules(

        coreModule

    )

}


⸻

Base URL Configuration

ApiConfig.registerBaseUrl(

    name = "main",

    baseUrl = "https://api.example.com",

    token = "user_token",

    defaultHeaders = mapOf(

        "platform" to "android"

    ),

    defaultQueryParams = mapOf(

        "lang" to "en"

    ),

    defaultBodyParams = mapOf(

        "deviceType" to "android"

    )

)


⸻

API Call Examples

GET request

apiClient.request<Unit, UserResponse>(

    base = "main",

    endpoint = "users"

)


⸻

POST request

apiClient.request<LoginRequest, LoginResponse>(

    base = "main",

    endpoint = "login",

    method = ApiMethod.POST,

    body = LoginRequest(

        email = "test@gmail.com",

        password = "1234"

    )

)


⸻

Query parameters

apiClient.request<Unit, ProductResponse>(

    base = "main",

    endpoint = "products",

    query = mapOf(

        "page" to "1"

    )

)


⸻

Multipart File Upload

apiClient.request<Unit, UploadResponse>(

    base = "main",

    endpoint = "upload",

    method = ApiMethod.POST,

    files = listOf(

        FilePart(

            name = "document",

            file = pickedFile

        )

    )

)


⸻

PickedFile

PickedFile(

    bytes = byteArray,

    fileName = "aadhar.jpg",

    mimeType = "image/jpeg"

)

Utilities

file.isImage

file.isPdf

file.sizeInMb

file.isUnder2Mb()

file.isUnder4Mb()

file.isUnder10Mb()


⸻

Permissions

PermissionManager.requestPermission(

    AppPermission.Camera

) {

}


⸻

Media Picker

PlatformMediaPicker.open(

    PickerType.ImageGallery

) { files ->

}

Supported types:

PickerType.Camera
PickerType.ImageGallery
PickerType.Pdf
PickerType.File
PickerType.Video


⸻

Attachment Bottom Sheet

CommonAttachmentBottomSheet(

    onCameraClick = { },

    onGalleryClick = { },

    onPdfClick = { }

)


⸻

Resource Wrapper

Resource.Loading

Resource.Success

Resource.Error

Usage:

apiClient.request<Unit, User>(...)

.collectApi(

    scope = viewModelScope

) { data ->

}


⸻

BaseViewModel

class ProfileViewModel(

    private val apiClient: ApiClient

) : BaseViewModel()


⸻

SharedViewModel

sharedViewModel.saveToken(token)

sharedViewModel.token


⸻

Network Monitor

NetworkMonitor.connected


⸻

Compose UI Components

Custom Scaffold

CustomScaffold(

    title = "Home"

) {

}


⸻

BottomSheet

BottomSheet(

    visible = true

) {

}


⸻

TextField

TextField(

    value = name,

    onValueChange = { }

)


⸻

DatePicker

DatePicker(

    selectedDate = date

)


⸻

Dropdown

DropDown(

    items = listOf(

        "Male",

        "Female"

    )

)


⸻

Radio / Checkbox

RadioCheckBox(

    selected = true

)


⸻

Snackbar

SnackBar(

    message = "Saved successfully"
)


⸻

Image Loader

CustomImage(

    url = imageUrl

)


⸻

Loading Indicator

Loading()


⸻

Divider

Divider()


⸻

Tabs

Tab(

    title = "Home"
)


⸻

Colors

AppColors.primary

AppColors.secondary


⸻

Request Priority

ApiPriority.HIGH

ApiPriority.NORMAL

ApiPriority.LOW


⸻

Complete Example Flow

PermissionManager.requestPermission(

    AppPermission.Camera

) {

    PlatformMediaPicker.open(

        PickerType.Camera

    ) { files ->

        val file = files.first()

        if(file.isUnder2Mb()){

            apiClient.request(

                base = "main",

                endpoint = "upload",

                files = listOf(

                    FilePart(

                        name = "file",

                        file = file

                    )

                )

            )

        }

    }

}


⸻

Platform Support

Feature Android iOS Desktop
API yes yes yes
Settings  yes yes yes
Permissions yes yes partial
Media Picker  yes yes yes
Compose UI  yes yes yes


⸻

Why EazyCMP

EazyCMP removes the need to separately implement:
  • networking layer
  • permission handling
  • file picking
  • form components
  • base architecture
  • shared preferences abstraction

Everything works together out of the box.

⸻

Author

Ajay Swami
