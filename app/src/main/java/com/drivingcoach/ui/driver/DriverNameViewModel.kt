package com.drivingcoach.ui.driver

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivingcoach.R
import com.drivingcoach.data.profile.DriverProfileStore
import com.drivingcoach.data.profile.DriverProfileStore.NameValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives first-run driver naming (SRS DR-02 … DR-05).
 *
 * The screen has exactly one job — obtain a usable display name and record that the profile
 * is complete — so the state here is deliberately small.
 */
@HiltViewModel
class DriverNameViewModel @Inject constructor(
    private val driverProfileStore: DriverProfileStore
) : ViewModel() {

    data class UiState(
        val canSubmit: Boolean = false,
        val isSaving: Boolean = false,
        /** Null while the input is acceptable, or untouched — we do not scold an empty field. */
        @StringRes val errorLabel: Int? = null
    )

    sealed interface Event {
        object NavigateToHome : Event
        data class ShowError(@StringRes val messageLabel: Int) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Re-evaluates the entered name as the driver types.
     *
     * "Too short" is reported only once the field has been touched and then emptied back
     * below the bound — an error badge on a field nobody has typed in yet reads as an
     * accusation rather than guidance. Over-length, by contrast, is always worth surfacing
     * immediately, because the driver can see they are still typing.
     */
    fun onNameChanged(input: String) {
        val validation = driverProfileStore.validate(input)

        _uiState.value = _uiState.value.copy(
            canSubmit = validation is NameValidation.Valid,
            errorLabel = when (validation) {
                is NameValidation.Valid -> null
                NameValidation.TooShort -> if (input.isEmpty()) null else R.string.driver_name_error_short
                NameValidation.TooLong -> R.string.driver_name_error_long
            }
        )
    }

    /**
     * Validates, persists and navigates.
     *
     * Re-validates rather than trusting [UiState.canSubmit]: the button state is a UI
     * affordance, and the store is the authority on what is storable. A save failure keeps
     * the driver on the screen with an explanation — silently continuing to Home would
     * produce exactly the "the app forgot me" behaviour this screen exists to end.
     */
    fun onSubmit(input: String) {
        if (_uiState.value.isSaving) return

        val validation = driverProfileStore.validate(input)
        if (validation !is NameValidation.Valid) {
            _uiState.value = _uiState.value.copy(
                canSubmit = false,
                errorLabel = if (validation == NameValidation.TooLong) {
                    R.string.driver_name_error_long
                } else {
                    R.string.driver_name_error_short
                }
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch {
            val saved = runCatching { driverProfileStore.saveName(validation.value) }

            if (saved.getOrNull() is NameValidation.Valid) {
                _events.emit(Event.NavigateToHome)
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _events.emit(Event.ShowError(R.string.driver_name_error_save))
            }
        }
    }
}
