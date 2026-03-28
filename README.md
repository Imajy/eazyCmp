eazyCmp 🚀

A powerful, lightweight Compose Multiplatform (CMP) toolkit that provides:
•	🎨 Reusable UI components
•	🌐 Ktor-based API client
•	🔄 Flow<Resource> networking pattern
•	⚙️ Configurable base URL system
•	📱 Works on Android, iOS, Desktop

Designed to reduce boilerplate in Compose Multiplatform projects and standardize:
•	UI patterns
•	API calling
•	response handling
•	configuration management

⸻

✨ Features

🎨 UI Components

Feature	Description
🎭 Lottie	Render animations directly from URL or local json.
📅 DatePicker	Single & Range date selection with constraints.
🔽 Dropdown	Generic Single/Multi-select with Search support.
🔘 Checkbox	Custom size checkbox (no bulky Material default).
📏 Dashed Divider	Clean dashed separator for modern UI.
🏗️ Bottom Bar	Generic navigation bar with state handling.
✍️ TextFields	Optimized outlined inputs with validation helpers
📑 Dialog Selectors	Multi-select dialog with state persistence


⸻

🌐 API Layer

Feature	Description
🔗 Ktor Client	Multiplatform HTTP client
🔐 Token Management	Dynamically update auth token
🧩 Base Config	Manage multiple base URLs
🔄 Flow<Resource>	Clean loading/success/error handling
🧠 Auto merge params	Default body/query params auto attach
⚡ Lightweight	No Retrofit / heavy dependencies
💾 Settings Support	Multiplatform key-value storage


⸻

📦 Installation

Add dependency in commonMain

implementation("com.github.Imajy:eazyCmp:Tag")


⸻

📂 Modules Overview

UI module

dropdown
datepicker
textfield
checkbox
bottomBar
dialog
divider
lottie

API module

ApiClient
ApiConfig
HttpClientProvider
Resource
mergeBody
SettingsProvider


⸻

⚙️ API Setup

1️⃣ Register Base URL

ApiConfig.registerBaseUrl(

    name = "main",

    baseUrl = "https://example.com/api/",

    token = null,

    defaultHeaders = mapOf(

        "Content-Type" to "application/json"
    ),

    defaultQueryParams = emptyMap(),

    defaultBodyParams = mapOf(

        "device" to "android"
    )
)


⸻

2️⃣ Update Token

ApiConfig.updateToken(

    name = "main",

    token = "your_token"
)


⸻

3️⃣ Create ApiClient

val apiClient = ApiClient()


⸻

4️⃣ GET API

apiClient.get<MyResponse>(

    base = "main",

    endpoint = "users",

    query = mapOf(

        "page" to "1"
    )

).collect {

    when (it) {

        is Resource.Loading -> {}

        is Resource.Success -> {

            val data = it.data
        }

        is Resource.Error -> {

            println(it.message)
        }
    }
}


⸻

5️⃣ POST API

apiClient.post<MyResponse>(

    base = "main",

    endpoint = "login",

    body = mapOf(

        "email" to "test@mail.com",

        "password" to "123456"
    )

).collect {

    when (it) {

        is Resource.Loading -> {}

        is Resource.Success -> {}

        is Resource.Error -> {}
    }
}


⸻

🔁 Resource Wrapper

sealed class Resource<T>(

    val data: T? = null,

    val message: String? = null
) {

    class Success<T>(data: T) : Resource<T>(data)

    class Error<T>(message: String) : Resource<T>(null, message)

    class Loading<T> : Resource<T>()
}


⸻

🧠 Default Param Merge

defaultBodyParams = mapOf(

    "app_version" to "1.0"
)

request body:

body = mapOf(

    "email" to "abc@mail.com"
)

final body:

{
"app_version":"1.0",
"email":"abc@mail.com"
}


⸻

🖥️ Platform Implementation

provideHttpClient()

expect fun provideHttpClient(): HttpClient

example:

HttpClient {

    install(ContentNegotiation) {

        json(

            Json {

                ignoreUnknownKeys = true

                isLenient = true
            }
        )
    }
}


⸻

provideSettings()

expect fun provideSettings(): Settings


⸻

🧱 Architecture Recommendation

presentation
↓
viewmodel
↓
repository
↓
apiClient


⸻

🎯 Use Cases

✔ CMP apps with shared API layer
✔ scalable UI systems
✔ reusable dropdown & dialogs
✔ form heavy applications
✔ enterprise multi-module apps

⸻

🔮 Planned Additions
•	pagination helper
•	file upload
•	retry interceptor
•	token refresh handler
•	form validator kit
•	theme system
•	compose navigation helpers

⸻

👨‍💻 Author

Ajay Swami

⸻

if you want, i can also provide:

• example project structure
• repository layer template
• BaseViewModel for Flow<Resource>
• form builder using eazyCmp components
• multi-module architecture diagram