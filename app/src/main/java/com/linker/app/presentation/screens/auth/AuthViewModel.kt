package com.linker.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linker.app.core.util.Result
import com.linker.app.domain.model.User
import com.linker.app.domain.usecase.auth.CompleteProfileSetupUseCase
import com.linker.app.domain.usecase.auth.CreateAccountWithEmailUseCase
import com.linker.app.domain.usecase.auth.ObserveAuthStateUseCase
import com.linker.app.domain.usecase.auth.SendPasswordResetEmailUseCase
import com.linker.app.domain.usecase.auth.SendPhoneOtpUseCase
import com.linker.app.domain.usecase.auth.SignInWithEmailUseCase
import com.linker.app.domain.usecase.auth.SignInWithGoogleUseCase
import com.linker.app.domain.usecase.auth.SignOutUseCase
import com.linker.app.domain.usecase.auth.VerifyPhoneOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ─────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val authStep: AuthStep = AuthStep.SIGN_IN,
    val phoneVerificationId: String? = null,
    // form fields
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phoneNumber: String = "",
    val otp: String = "",
    // profile setup
    val username: String = "",
    val displayName: String = "",
    // validation errors
    val emailError: String? = null,
    val passwordError: String? = null,
    val phoneError: String? = null,
    val otpError: String? = null,
    val usernameError: String? = null
)

enum class AuthStep {
    SIGN_IN,
    SIGN_UP,
    PHONE_OTP_INPUT,
    PHONE_OTP_VERIFY,
    PROFILE_SETUP,
    FORGOT_PASSWORD
}

sealed class AuthEffect {
    data object NavigateToHome : AuthEffect()
    data object NavigateToProfileSetup : AuthEffect()
    data class ShowError(val message: String) : AuthEffect()
    data class ShowInfo(val message: String) : AuthEffect()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signInWithEmail: SignInWithEmailUseCase,
    private val createAccount: CreateAccountWithEmailUseCase,
    private val sendPhoneOtp: SendPhoneOtpUseCase,
    private val verifyPhoneOtp: VerifyPhoneOtpUseCase,
    private val sendResetEmail: SendPasswordResetEmailUseCase,
    private val completeProfileSetup: CompleteProfileSetupUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _effect = MutableSharedFlow<AuthEffect>()
    val effect: SharedFlow<AuthEffect> = _effect.asSharedFlow()

    /** Tracks Firebase auth state across rotations. */
    val authState = observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.displayName.isNullOrBlank()) {
            _uiState.update { it.copy(authStep = AuthStep.PROFILE_SETUP) }
        }
    }

    // ── Field updates ────────────────────────────────────────────────────────

    fun onEmailChange(value: String)           = _uiState.update { it.copy(email = value,           emailError = null) }
    fun onPasswordChange(value: String)        = _uiState.update { it.copy(password = value,         passwordError = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value) }
    fun onPhoneNumberChange(value: String)     = _uiState.update { it.copy(phoneNumber = value,      phoneError = null) }
    fun onOtpChange(value: String)             = _uiState.update { it.copy(otp = value,              otpError = null) }
    fun onUsernameChange(value: String)        = _uiState.update { it.copy(username = value,          usernameError = null) }
    fun onDisplayNameChange(value: String)     = _uiState.update { it.copy(displayName = value) }

    fun setStep(step: AuthStep) = _uiState.update { it.copy(authStep = step) }

    // ── Google Sign-In ───────────────────────────────────────────────────────

    fun onGoogleSignIn(idToken: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        when (val result = signInWithGoogle(idToken)) {
            is Result.Success -> handleSignInSuccess(result.data)
            is Result.Error   -> showError(result.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Email Sign-In ────────────────────────────────────────────────────────

    fun onEmailSignIn() = viewModelScope.launch {
        val state = _uiState.value
        if (!validateEmailPassword(state)) return@launch

        _uiState.update { it.copy(isLoading = true) }
        when (val result = signInWithEmail(state.email.trim(), state.password)) {
            is Result.Success -> handleSignInSuccess(result.data)
            is Result.Error   -> showError(result.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Email Sign-Up ────────────────────────────────────────────────────────

    fun onEmailSignUp() = viewModelScope.launch {
        val state = _uiState.value
        if (!validateEmailPassword(state)) return@launch
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(passwordError = "Passwords do not match") }
            return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        when (val result = createAccount(state.email.trim(), state.password)) {
            is Result.Success -> _effect.emit(AuthEffect.NavigateToProfileSetup)
            is Result.Error   -> showError(result.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Phone OTP ────────────────────────────────────────────────────────────

    fun onSendPhoneOtp() = viewModelScope.launch {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.isEmpty()) {
            _uiState.update { it.copy(phoneError = "Enter your phone number") }
            return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        // Note: full phone auth requires Activity context for Firebase callbacks.
        // This is handled via the credential manager in MainActivity.
        when (val result = sendPhoneOtp(phone)) {
            is Result.Success -> {
                _uiState.update { it.copy(phoneVerificationId = result.data, authStep = AuthStep.PHONE_OTP_VERIFY) }
            }
            is Result.Error -> showError(result.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onVerifyOtp() = viewModelScope.launch {
        val state = _uiState.value
        val verificationId = state.phoneVerificationId ?: run {
            showError("Verification ID missing"); return@launch
        }
        if (state.otp.length != 6) {
            _uiState.update { it.copy(otpError = "Enter the 6-digit OTP") }
            return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        when (val result = verifyPhoneOtp(verificationId, state.otp)) {
            is Result.Success -> handleSignInSuccess(result.data)
            is Result.Error   -> _uiState.update { it.copy(otpError = result.message, isLoading = false) }
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Forgot Password ──────────────────────────────────────────────────────

    fun onForgotPassword() = viewModelScope.launch {
        val email = _uiState.value.email.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(emailError = "Enter your email") }
            return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        when (val result = sendResetEmail(email)) {
            is Result.Success -> _effect.emit(AuthEffect.ShowInfo("Reset email sent to $email"))
            is Result.Error   -> showError(result.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Profile Setup ────────────────────────────────────────────────────────

    fun onCompleteProfile(userId: String) = viewModelScope.launch {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(usernameError = "Username required") }; return@launch
        }

        _uiState.update { it.copy(isLoading = true) }
        when (val result = completeProfileSetup(userId, state.username, state.displayName, null)) {
            is Result.Success -> _effect.emit(AuthEffect.NavigateToHome)
            is Result.Error   -> _uiState.update { it.copy(usernameError = result.message) }
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    // ── Sign Out ─────────────────────────────────────────────────────────────

    fun onSignOut() = viewModelScope.launch {
        signOutUseCase()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun handleSignInSuccess(user: User) {
        val needsProfileSetup = user.username.isEmpty() || user.username == user.email?.substringBefore('@')
        if (needsProfileSetup) {
            _uiState.update { it.copy(authStep = AuthStep.PROFILE_SETUP) }
            _effect.emit(AuthEffect.NavigateToProfileSetup)
        } else {
            _effect.emit(AuthEffect.NavigateToHome)
        }
    }

    private fun validateEmailPassword(state: AuthUiState): Boolean {
        var valid = true
        if (state.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(emailError = "Enter a valid email") }; valid = false
        }
        if (state.password.length < 8) {
            _uiState.update { it.copy(passwordError = "Password must be at least 8 characters") }; valid = false
        }
        return valid
    }

    private fun showError(message: String) = viewModelScope.launch {
        _effect.emit(AuthEffect.ShowError(message))
    }
}
