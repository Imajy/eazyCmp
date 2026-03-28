package com.aj.api


/**
 * BaseConfig
 *
 * Represents configuration for a specific base URL.
 *
 * This allows the library to support multiple APIs at the same time,
 * each with its own:
 *
 * - base URL
 * - token
 * - default headers
 * - default query parameters
 * - default body parameters
 *
 *
 * Why this exists
 * ---------------
 *
 * Many applications communicate with multiple backend services.
 *
 * Example:
 *
 * Main API
 * https://api.example.com
 *
 * Payment API
 * https://payment.example.com
 *
 * Chat API
 * https://chat.example.com
 *
 *
 * Each service may require:
 *
 * different tokens
 * different headers
 * different default parameters
 *
 *
 * BaseConfig allows each service to be configured independently.
 *
 *
 * Properties
 * ----------
 *
 * baseUrl:
 * Root URL of the API.
 *
 * Example:
 * https://api.example.com
 *
 *
 * token:
 * Authorization token associated with this API.
 *
 * Automatically added to every request header:
 *
 * Authorization: Bearer token
 *
 *
 * defaultHeaders:
 * Headers automatically attached to every request.
 *
 * Example:
 *
 * platform
 * appVersion
 * deviceId
 *
 *
 * defaultQueryParams:
 * Query parameters automatically added to every request URL.
 *
 * Example:
 *
 * lang=en
 * region=IN
 *
 *
 * defaultBodyParams:
 * Body parameters automatically merged with request body.
 *
 * Example:
 *
 * deviceType
 * appVersion
 * tenantId
 *
 *
 * Example final request body:
 *
 * {
 *   "deviceType": "android",
 *   "appVersion": "1.0",
 *   "email": "abc@gmail.com"
 * }
 *
 */
data class BaseConfig(

    val baseUrl: String,

    val token: String?,

    val defaultHeaders: Map<String, String>,

    val defaultQueryParams: Map<String, String>,

    val defaultBodyParams: Map<String, Any?>
)




/**
 * ApiConfig
 *
 * Central configuration registry for all API base URLs.
 *
 *
 * Responsibilities
 * ----------------
 *
 * - stores multiple base configurations
 * - manages tokens per API
 * - provides configuration to ApiClient
 * - allows dynamic token updates
 *
 *
 * Example usage
 * -------------
 *
 * Register main API:
 *
 * ApiConfig.registerBaseUrl(
 *
 *     name = "main",
 *
 *     baseUrl = "https://api.example.com",
 *
 *     token = "abc123",
 *
 *     defaultHeaders = mapOf(
 *         "platform" to "android"
 *     ),
 *
 *     defaultQueryParams = mapOf(
 *         "lang" to "en"
 *     ),
 *
 *     defaultBodyParams = mapOf(
 *         "appVersion" to "1.0"
 *     )
 * )
 *
 *
 * Register another API:
 *
 * ApiConfig.registerBaseUrl(
 *
 *     name = "payment",
 *
 *     baseUrl = "https://pay.example.com",
 *
 *     token = "paymentToken"
 * )
 *
 *
 * Later in API calls:
 *
 * apiClient.get(
 *     base = "main",
 *     endpoint = "users"
 * )
 *
 * apiClient.post(
 *     base = "payment",
 *     endpoint = "orders"
 * )
 *
 *
 * Benefits
 * --------
 *
 * - user does not need to repeatedly pass token
 * - user does not need to repeatedly pass common params
 * - supports multiple backend services
 * - centralized configuration
 *
 */
object ApiConfig {



    /**
     * Stores configuration for each base URL.
     *
     * key → base name
     * value → configuration
     */
    private val configs: MutableMap<String, BaseConfig> = mutableMapOf()



    /**
     * registerBaseUrl
     *
     * Registers a new API configuration.
     *
     * This should typically be called once during app startup.
     *
     *
     * Example:
     *
     * ApiConfig.registerBaseUrl(
     *
     *     name = "main",
     *
     *     baseUrl = "https://api.example.com",
     *
     *     token = "abc123"
     * )
     *
     *
     * Parameters
     * ----------
     *
     * name:
     * Unique identifier for this API configuration.
     *
     * Example:
     *
     * main
     * payment
     * chat
     *
     *
     * baseUrl:
     * Root API URL.
     *
     *
     * token:
     * Authorization token.
     *
     * Automatically added to request header:
     *
     * Authorization: Bearer token
     *
     *
     * defaultHeaders:
     * Headers automatically added to every request.
     *
     *
     * defaultQueryParams:
     * Query parameters automatically added to every request URL.
     *
     *
     * defaultBodyParams:
     * Parameters automatically merged into request body.
     *
     *
     * Important:
     * ----------
     *
     * name must be unique.
     *
     * If same name is used again,
     * previous configuration will be replaced.
     *
     */
    fun registerBaseUrl(

        name: String,

        baseUrl: String,

        token: String? = null,

        defaultHeaders: Map<String, String> = emptyMap(),

        defaultQueryParams: Map<String, String> = emptyMap(),

        defaultBodyParams: Map<String, Any?> = emptyMap()
    ) {

        configs[name] = BaseConfig(

            baseUrl = baseUrl,

            token = token,

            defaultHeaders = defaultHeaders,

            defaultQueryParams = defaultQueryParams,

            defaultBodyParams = defaultBodyParams
        )
    }



    /**
     * updateToken
     *
     * Updates token for an already registered base URL.
     *
     *
     * Common use case:
     *
     * After login or token refresh.
     *
     *
     * Example:
     *
     * ApiConfig.updateToken(
     *
     *     name = "main",
     *
     *     token = "newToken123"
     * )
     *
     *
     * This token will automatically be used
     * in all future API requests.
     *
     *
     * Safe behavior:
     *
     * If base name does not exist,
     * function does nothing.
     *
     */
    fun updateToken(

        name: String,

        token: String
    ) {

        val config = configs[name] ?: return

        configs[name] = config.copy(

            token = token
        )
    }



    /**
     * getConfig
     *
     * Returns configuration for a given base name.
     *
     *
     * Used internally by:
     *
     * ApiClient
     * applyDefaults()
     * mergeBody()
     *
     *
     * Throws error if base is not registered.
     *
     * Example error:
     *
     * Base url not registered: main
     *
     *
     * This helps detect configuration issues early.
     *
     */
    fun getConfig(name: String): BaseConfig {

        return configs[name]

            ?: error("Base url not registered: $name")
    }
}