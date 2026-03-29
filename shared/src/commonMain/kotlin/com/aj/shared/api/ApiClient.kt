package com.aj.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 * ApiClient
 *
 * Main networking client of the library.
 *
 * Built on top of Ktor HttpClient and Kotlin Flow.
 *
 *
 * Responsibilities
 * ----------------
 *
 * - performs HTTP requests
 * - automatically applies base URL configuration
 * - automatically attaches token
 * - automatically attaches default headers
 * - automatically attaches default query params
 * - automatically merges default body params
 * - converts API response into Resource<T>
 *
 *
 * Supported Platforms
 * -------------------
 *
 * Android
 * iOS
 * Desktop
 *
 *
 * Supported Request Types
 * -----------------------
 *
 * GET
 * POST
 *
 * (PUT, DELETE, multipart can be added later)
 *
 *
 * Flow lifecycle
 * --------------
 *
 * Every API call emits:
 *
 * 1 → Loading
 * 2 → Success OR Error
 *
 *
 * Example flow sequence:
 *
 * emit(Resource.Loading())
 * emit(Resource.Success(data))
 *
 * OR
 *
 * emit(Resource.Loading())
 * emit(Resource.Error(message))
 *
 *
 * Usage overview
 * --------------
 *
 * Step 1 → register base url
 *
 * ApiConfig.registerBaseUrl(
 *     name = "main",
 *     baseUrl = "https://api.example.com",
 *     token = "abc123"
 * )
 *
 *
 * Step 2 → create client
 *
 * val apiClient = ApiClient()
 *
 *
 * Step 3 → call API
 *
 * apiClient.get<UserResponse>(
 *     base = "main",
 *     endpoint = "users"
 * )
 *
 *
 * Generics explanation
 * --------------------
 *
 * Res → response model
 *
 * Example:
 *
 * data class UserResponse(...)
 *
 *
 * apiClient.get<UserResponse>()
 *
 *
 * Thread safety
 * -------------
 *
 * HttpClient is singleton via HttpClientProvider.
 *
 * ApiClient can be reused safely across the entire app.
 *
 */
class ApiClient(


    /**
     * Shared HttpClient instance.
     *
     * Provided by HttpClientProvider singleton.
     *
     * Can be overridden if custom client is needed.
     */
    val client: HttpClient = HttpClientProvider.client
) {



    /**
     * GET request
     *
     * Used for fetching data from server.
     *
     *
     * Example API:
     *
     * GET https://api.example.com/users?page=1
     *
     *
     * Example usage:
     *
     * apiClient.get<UserResponse>(
     *
     *     base = "main",
     *
     *     endpoint = "users",
     *
     *     query = mapOf(
     *         "page" to "1"
     *     )
     * )
     *
     *
     * Automatic behavior
     * ------------------
     *
     * Adds:
     *
     * Authorization header
     * default headers
     * default query params
     *
     *
     * Parameters
     * ----------
     *
     * base:
     * name of registered base configuration
     *
     *
     * endpoint:
     * API path relative to baseUrl
     *
     *
     * query:
     * optional query parameters
     *
     *
     * Returns
     * -------
     *
     * Flow<Resource<Res>>
     *
     */
    suspend inline fun <reified Res> get(

        base: String,

        endpoint: String,

        query: Map<String, String> = emptyMap()

    ): Flow<Resource<Res>> {

        return flow<Resource<Res>> {

            emit(Resource.Loading())

            try {

                val response = client.get(

                    buildUrl(base, endpoint)

                ) {

                    applyDefaults(base)


                    /**
                     * apply additional query params
                     */
                    query.forEach { entry ->

                        parameter(

                            entry.key,

                            entry.value
                        )
                    }
                }


                /**
                 * deserialize response body
                 */
                val data: Res = response.body()

                emit(Resource.Success(data))

            } catch (e: Exception) {


                /**
                 * error handling
                 */
                emit(

                    Resource.Error<Res>(

                        e.message ?: "unknown error"
                    )
                )
            }
        }
    }




    /**
     * POST request
     *
     * Used for sending data to server.
     *
     *
     * Example API:
     *
     * POST https://api.example.com/login
     *
     * body:
     *
     * {
     *   "email": "abc@gmail.com",
     *   "password": "1234"
     * }
     *
     *
     * Example usage:
     *
     * apiClient.post<LoginResponse>(
     *
     *     base = "main",
     *
     *     endpoint = "login",
     *
     *     body = mapOf(
     *
     *         "email" to "abc@gmail.com",
     *
     *         "password" to "1234"
     *     )
     * )
     *
     *
     * Automatic behavior
     * ------------------
     *
     * merges defaultBodyParams with request body
     *
     *
     * Example:
     *
     * defaultBodyParams:
     *
     * appVersion → 1.0
     *
     *
     * request body:
     *
     * email → abc@gmail.com
     *
     *
     * final body:
     *
     * appVersion → 1.0
     * email → abc@gmail.com
     *
     *
     * Parameters
     * ----------
     *
     * base:
     * name of registered base configuration
     *
     *
     * endpoint:
     * API path
     *
     *
     * body:
     * request payload
     *
     *
     * Returns
     * -------
     *
     * Flow<Resource<Res>>
     *
     */
    suspend inline fun <reified Res> post(

        base: String,

        endpoint: String,

        body: Map<String, Any?> = emptyMap()

    ): Flow<Resource<Res>> {

        return flow<Resource<Res>> {

            emit(Resource.Loading())

            try {


                /**
                 * merge default params with body
                 */
                val finalBody: Map<String, Any?> = mergeBody(

                    base,

                    body
                )


                val response = client.post(

                    buildUrl(base, endpoint)

                ) {

                    contentType(ContentType.Application.Json)

                    applyDefaults(base)

                    setBody(finalBody)
                }


                val data: Res = response.body()

                emit(Resource.Success(data))

            } catch (e: Exception) {

                emit(

                    Resource.Error<Res>(

                        e.message ?: "unknown error"
                    )
                )
            }
        }
    }
}