package com.aj.shared.api


/**
 * mergeBody
 *
 * Combines default body parameters with request-specific body parameters.
 *
 *
 * Why this function exists
 * ------------------------
 *
 * Many APIs require certain parameters to be sent in every request body.
 *
 * Example:
 *
 * {
 *   "deviceType": "android",
 *   "appVersion": "1.0",
 *   "userId": "123"
 * }
 *
 *
 * Instead of passing these values manually in every API call,
 * they can be configured once in ApiConfig.registerBaseUrl().
 *
 *
 * Example configuration:
 *
 * ApiConfig.registerBaseUrl(
 *      name = "main",
 *      baseUrl = "https://api.example.com",
 *
 *      defaultBodyParams = mapOf(
 *          "deviceType" to "android",
 *          "appVersion" to "1.0"
 *      )
 * )
 *
 *
 * Then while calling API:
 *
 * apiClient.post<LoginResponse>(
 *      base = "main",
 *      endpoint = "login",
 *
 *      body = mapOf(
 *          "email" to "abc@gmail.com",
 *          "password" to "1234"
 *      )
 * )
 *
 *
 * Final merged body automatically becomes:
 *
 * {
 *   "deviceType": "android",
 *   "appVersion": "1.0",
 *   "email": "abc@gmail.com",
 *   "password": "1234"
 * }
 *
 *
 * Priority rule
 * -------------
 *
 * If the same key exists in both:
 *
 * defaultBodyParams
 * AND
 * body
 *
 * then the value from body will override the default value.
 *
 *
 * Example:
 *
 * defaultBodyParams:
 * "deviceType" → "android"
 *
 * body:
 * "deviceType" → "ios"
 *
 * Result:
 * "deviceType" → "ios"
 *
 *
 * Parameters
 * ----------
 *
 * baseName:
 * Name of the registered base configuration.
 *
 * body:
 * Request specific body parameters.
 *
 *
 * Returns
 * -------
 *
 * Map<String, Any?>
 *
 * A merged map containing:
 *
 * defaultBodyParams + request body params
 *
 *
 * Used internally by:
 *
 * ApiClient.post()
 * ApiClient.put()
 * multipart requests
 *
 *
 * Library user does NOT need to call this manually.
 *
 */
fun mergeBody(

    baseName: String,

    body: Map<String, Any?>?

): Map<String, Any?> {

    val config: BaseConfig = ApiConfig.getConfig(baseName)


    /**
     * final map that will be sent in API request
     */
    val finalMap: MutableMap<String, Any?> = mutableMapOf()


    /**
     * Step 1:
     * add default params
     */
    finalMap.putAll(

        config.defaultBodyParams
    )


    /**
     * Step 2:
     * override with request params
     */
    if (body != null) {

        finalMap.putAll(body)
    }


    return finalMap
}