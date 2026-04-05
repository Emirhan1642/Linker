package com.linker.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.usecase.auth.CompleteProfileSetupUseCase
import com.linker.app.domain.usecase.auth.CreateAccountWithEmailUseCase
import com.linker.app.domain.usecase.auth.ObserveAuthStateUseCase
import com.linker.app.domain.usecase.auth.SendPasswordResetEmailUseCase
import com.linker.app.domain.usecase.auth.SendPhoneOtpUseCase
import com.linker.app.domain.usecase.auth.SignInWithEmailUseCase
import com.linker.app.domain.usecase.auth.SignInWithGoogleUseCase
import com.linker.app.domain.usecase.auth.SignOutUseCase
import com.linker.app.domain.usecase.auth.VerifyPhoneOtpUseCase
import com.linker.app.core.notification.PushTokenRegistrar
import com.linker.app.core.security.CredentialEncoder
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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val authStep: AuthStep = AuthStep.SIGN_IN,
    val phoneVerificationId: String? = null,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phoneNumber: String = "",
    val otp: String = "",
    val username: String = "",
    val displayName: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val phoneError: String? = null,
    val otpError: String? = null,
    val usernameError: String? = null,
    val isAddingAccount: Boolean = false
)

enum class AuthStep {
    SIGN_IN, SIGN_UP, PHONE_OTP_INPUT, PHONE_OTP_VERIFY, PROFILE_SETUP, FORGOT_PASSWORD
}

sealed class AuthEffect {
    data object NavigateToHome : AuthEffect()
    data object NavigateToProfileSetup : AuthEffect()
    data object NavigateBackToAccountCenter : AuthEffect()
    data class ShowError(val message: String) : AuthEffect()
    data class ShowInfo(val message: String) : AuthEffect()
}

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
    private val signOutUseCase: SignOutUseCase,
    private val accountRepository: AccountRepository,
    private val pushTokenRegistrar: PushTokenRegistrar
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _effect = MutableSharedFlow<AuthEffect>()
    val effect: SharedFlow<AuthEffect> = _effect.asSharedFlow()

    val authState = observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.displayName.isNullOrBlank()) {
            _uiState.update { it.copy(authStep = AuthStep.PROFILE_SETUP) }
        }
    }

    fun setAddingAccountMode(isAdding: Boolean) {
        _uiState.update { it.copy(isAddingAccount = isAdding) }
    }

    fun onEmailChange(v: String)           = _uiState.update { it.copy(email = v,           emailError    = null) }
    fun onPasswordChange(v: String)        = _uiState.update { it.copy(password = v,         passwordError = null) }
    fun onConfirmPasswordChange(v: String) = _uiState.update { it.copy(confirmPassword = v) }
    fun onPhoneNumberChange(v: String)     = _uiState.update { it.copy(phoneNumber = v,      phoneError    = null) }
    fun onOtpChange(v: String)             = _uiState.update { it.copy(otp = v,              otpError      = null) }
    fun onUsernameChange(v: String)        = _uiState.update { it.copy(username = v,          usernameError = null) }
    fun onDisplayNameChange(v: String)     = _uiState.update { it.copy(displayName = v) }
    fun setStep(step: AuthStep)            = _uiState.update { it.copy(authStep = step) }

    fun onGoogleSignIn(idToken: String) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        when (val r = signInWithGoogle(idToken)) {
            is Result.Success -> handleSignInSuccess(r.data)
            is Result.Error   -> showError(r.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onEmailSignIn() = viewModelScope.launch {
        val state = _uiState.value
        if (!validateEmailPassword(state)) return@launch
        _uiState.update { it.copy(isLoading = true) }
        when (val r = signInWithEmail(state.email.trim(), state.password)) {
            is Result.Success -> handleSignInSuccess(r.data)
            is Result.Error   -> showError(r.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onEmailSignUp() = viewModelScope.launch {
        val state = _uiState.value
        if (!validateEmailPassword(state)) return@launch
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(passwordError = "Passwords do not match") }; return@launch
        }
        _uiState.update { it.copy(isLoading = true) }
        when (val r = createAccount(state.email.trim(), state.password)) {
            is Result.Success -> _effect.emit(AuthEffect.NavigateToProfileSetup)
            is Result.Error   -> showError(r.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onSendPhoneOtp() = viewModelScope.launch {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.isEmpty()) { _uiState.update { it.copy(phoneError = "Enter your phone number") }; return@launch }
        _uiState.update { it.copy(isLoading = true) }
        when (val r = sendPhoneOtp(phone)) {
            is Result.Success -> _uiState.update { it.copy(phoneVerificationId = r.data, authStep = AuthStep.PHONE_OTP_VERIFY) }
            is Result.Error   -> showError(r.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onVerifyOtp() = viewModelScope.launch {
        val state = _uiState.value
        val vId = state.phoneVerificationId ?: run { showError("Verification ID missing"); return@launch }
        if (state.otp.length != 6) { _uiState.update { it.copy(otpError = "Enter the 6-digit OTP") }; return@launch }
        _uiState.update { it.copy(isLoading = true) }
        when (val r = verifyPhoneOtp(vId, state.otp)) {
            is Result.Success -> handleSignInSuccess(r.data)
            is Result.Error   -> _uiState.update { it.copy(otpError = r.message, isLoading = false) }
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onForgotPassword() = viewModelScope.launch {
        val email = _uiState.value.email.trim()
        if (email.isEmpty()) { _uiState.update { it.copy(emailError = "Enter your email") }; return@launch }
        _uiState.update { it.copy(isLoading = true) }
        when (val r = sendResetEmail(email)) {
            is Result.Success -> _effect.emit(AuthEffect.ShowInfo("Reset email sent to $email"))
            is Result.Error   -> showError(r.message)
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onCompleteProfile(userId: String) = viewModelScope.launch {
        val state = _uiState.value
        if (state.username.isBlank()) { _uiState.update { it.copy(usernameError = "Username required") }; return@launch }
        _uiState.update { it.copy(isLoading = true) }
        when (val r = completeProfileSetup(userId, state.username, state.displayName, null)) {
            is Result.Success -> {
                // Profile setup tamamlandı → email+password ile session kaydet
                saveSession(
                    uid         = r.data.userId,
                    displayName = r.data.displayName.ifBlank { r.data.username },
                    username    = r.data.username,
                    avatarUrl   = r.data.profileImageUrl,
                    email       = state.email.trim(),
                    password    = state.password
                )
                val dest = if (state.isAddingAccount)
                    AuthEffect.NavigateBackToAccountCenter
                else
                    AuthEffect.NavigateToHome
                _effect.emit(dest)
            }
            is Result.Error -> _uiState.update { it.copy(usernameError = r.message) }
            is Result.Loading -> {}
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onSignOut() = viewModelScope.launch { signOutUseCase() }

    // ── handleSignInSuccess ───────────────────────────────────────────────────

    private suspend fun handleSignInSuccess(user: User) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?: run { _effect.emit(AuthEffect.ShowError("Authentication error")); return }

        val doc = try {
            FirebaseFirestore.getInstance()
                .collection("users").document(firebaseUser.uid)
                .get().await()
        } catch (e: Exception) { null }

        val username = doc?.getString("username")

        if (username.isNullOrBlank()) {
            _uiState.update { it.copy(authStep = AuthStep.PROFILE_SETUP) }
            _effect.emit(AuthEffect.NavigateToProfileSetup)
            return
        }

        val displayName = doc.getString("displayName") ?: username
        val avatarUrl   = doc.getString("profileImageUrl")

        // Email + password ile session kaydet
        saveSession(
            uid         = firebaseUser.uid,
            displayName = displayName,
            username    = username,
            avatarUrl   = avatarUrl,
            email       = _uiState.value.email.trim(),
            password    = _uiState.value.password
        )

        val destination = if (_uiState.value.isAddingAccount)
            AuthEffect.NavigateBackToAccountCenter
        else
            AuthEffect.NavigateToHome

        _effect.emit(destination)

        viewModelScope.launch {
            pushTokenRegistrar.registerCurrentToken()
        }
    }

    // ── saveSession ───────────────────────────────────────────────────────────
    //
    // NEDEN email+password saklanıyor?
    //
    // Firebase Android SDK'da refresh token ile doğrudan oturum açmak mümkün değil:
    //   • signInWithCustomToken → backend imzalı JWT gerektirir
    //   • REST token exchange → idToken alınır ama FirebaseAuth.currentUser'a
    //     set edilemez (SDK bunu desteklemiyor)
    //
    // GÜVENLIK:
    //   • CredentialEncoder kullanarak delimiter-free binary encoding
    //   • Android Keystore AES-256-GCM ile şifrelenir
    //   • EncryptedSharedPreferences'da tutulur (ikinci katman şifreleme)
    //   • Anahtar material hiç bellekte/diskte açık tutulmaz
    //
    // KISIT: Google/Phone auth ile eklenen hesaplar için şifre yoktur.
    //        Bu hesaplar için henüz otomatik geçiş desteklenmiyor.

    private suspend fun saveSession(
        uid: String,
        displayName: String,
        username: String,
        avatarUrl: String?,
        email: String,
        password: String
    ) {
        try {
            if (email.isBlank() || password.isBlank()) {
                android.util.Log.w("AuthViewModel", "saveSession: email/password boş, session kaydedilmedi (Google/Phone auth?)")
                return
            }

            // ✅ SECURITY: Use CredentialEncoder for delimiter-free encoding
            val credential = CredentialEncoder.encode(email, password)

            accountRepository.addSession(
                AccountSession(
                    uid            = uid,
                    displayName    = displayName,
                    username       = username,
                    avatarUrl      = avatarUrl,
                    encryptedToken = credential,   // Keystore ile şifrelenir
                    lastUsedAt     = System.currentTimeMillis()
                )
            )

            // Clear Base64 string from memory (best effort)
            // Note: String immutability means we can't truly clear it, but we can drop references
        } catch (e: Exception) {
            android.util.Log.w("AuthViewModel", "saveSession failed: ${e.message}")
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

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

    private fun showError(msg: String) = viewModelScope.launch {
        _effect.emit(AuthEffect.ShowError(msg))
    }
}
