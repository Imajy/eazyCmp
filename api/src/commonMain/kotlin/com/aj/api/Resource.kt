package com.aj.api

/**
 * Resource
 *
 * A generic wrapper class used to represent the state of an API response.
 *
 * This is commonly used with Kotlin Flow or suspend functions to provide
 * a consistent structure for handling:
 *
 * 1. Loading state
 * 2. Success state
 * 3. Error state
 *
 * Why use Resource?
 * ------------------
 * API calls are asynchronous and can result in different states.
 * Instead of returning raw data, Resource provides a predictable structure
 * which allows UI layers (Compose, SwiftUI, Desktop UI) to react properly.
 *
 * This pattern helps to:
 *
 * - show loading indicators
 * - display data when available
 * - handle API errors safely
 * - avoid try/catch in UI layer
 *
 *
 * Structure
 * ----------
 * Resource has three possible states:
 *
 * Loading  → API call is in progress
 * Success  → API returned data successfully
 * Error    → API call failed
 *
 *
 * Example Flow response:
 *
 * emit(Resource.Loading())
 * emit(Resource.Success(data))
 *
 * OR
 *
 * emit(Resource.Loading())
 * emit(Resource.Error("Something went wrong"))
 *
 *
 * Example usage in ViewModel:
 *
 * apiClient.get<UserResponse>(
 *      base = "main",
 *      endpoint = "users"
 * ).collect { result ->
 *
 *      when(result) {
 *
 *          is Resource.Loading -> {
 *              // show progress indicator
 *          }
 *
 *          is Resource.Success -> {
 *              val data = result.data
 *          }
 *
 *          is Resource.Error -> {
 *              val message = result.message
 *          }
 *      }
 * }
 *
 *
 * Generic type T
 * --------------
 * T represents the expected response model.
 *
 * Example:
 *
 * Resource<UserResponse>
 * Resource<List<Product>>
 * Resource<LoginResponse>
 *
 *
 * Notes for library users:
 * ------------------------
 * - Always handle all 3 states in UI layer
 * - Never assume data is non-null
 * - Error message may come from:
 *      - network failure
 *      - serialization issue
 *      - server error
 *
 *
 * Example UI pattern (Compose):
 *
 * when(val state = viewModel.state.collectAsState().value) {
 *
 *      is Resource.Loading -> LoadingView()
 *
 *      is Resource.Success -> ContentView(state.data)
 *
 *      is Resource.Error -> ErrorView(state.message)
 * }
 *
 *
 * Works on:
 *
 * Android
 * iOS
 * Desktop
 *
 */
sealed class Resource<T>(

    val data: T? = null,

    val message: String? = null
) {

    /**
     * Success state
     *
     * Indicates API call completed successfully
     * and valid data is available.
     *
     * Example:
     *
     * Resource.Success(UserResponse)
     */
    class Success<T>(

        data: T
    ) : Resource<T>(data)



    /**
     * Error state
     *
     * Indicates API call failed.
     *
     * message → error description
     * data → optional cached data if available
     *
     * Example:
     *
     * Resource.Error("Invalid token")
     */
    class Error<T>(

        message: String,

        data: T? = null
    ) : Resource<T>(data, message)



    /**
     * Loading state
     *
     * Indicates API call is in progress.
     *
     * UI layer should show progress indicator.
     *
     * Example:
     *
     * Resource.Loading()
     */
    class Loading<T>(

        data: T? = null
    ) : Resource<T>(data)
}