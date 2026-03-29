package com.aj.shared.api

import androidx.lifecycle.ViewModel
import com.aj.shared.ui.AppSnackbarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

abstract class BaseViewModel : ViewModel() {

    private val _baseState = MutableStateFlow(BaseUiState())

    val baseState = _baseState.asStateFlow()

    protected fun setLoading(value: Boolean) {

        _baseState.update {

            it.copy(isLoading = value)

        }

    }

    protected fun setError(message: String?) {

        _baseState.update {

            it.copy(error = message)

        }
        AppSnackbarManager.show(message)

    }

    /**
     * IMPORTANT
     * extension defined INSIDE BaseViewModel
     */
    protected fun <T> Flow<Resource<T>>.collectApi(

        scope: CoroutineScope,

        onSuccess: (T?) -> Unit

    ) {

        onEach { result ->

            when (result) {

                is Resource.Loading -> {

                    setLoading(true)

                }

                is Resource.Error -> {

                    setLoading(false)

                    setError(result.message)

                }

                is Resource.Success -> {

                    setLoading(false)

                    onSuccess(result.data)

                }

            }

        }.launchIn(scope)

    }

}

data class BaseUiState(

    val isLoading: Boolean = false,

    val error: String? = null

)