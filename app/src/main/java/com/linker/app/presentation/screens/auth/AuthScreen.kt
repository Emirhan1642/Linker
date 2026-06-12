package com.linker.app.presentation.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.presentation.screens.auth.components.*
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Auth Host Screen
 *
 * [isAddingAccount] = true → Account Center'dan "Hesap ekle" ile gelindi.
 *   Bu modda başarılı girişte [onNavigateToAccountCenter] çağrılır,
 *   böylece önceki session'lar korunur.
 */
@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToAccountCenter: () -> Unit = {},
    isAddingAccount: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Mod bilgisini ViewModel'e ilet (NavHost'tan parametre olarak gelir)
    LaunchedEffect(isAddingAccount) {
        viewModel.setAddingAccountMode(isAddingAccount)
    }

    androidx.activity.compose.BackHandler(enabled = uiState.authStep != AuthStep.SIGN_IN) {
        when (uiState.authStep) {
            AuthStep.SIGN_UP, AuthStep.FORGOT_PASSWORD, AuthStep.PHONE_OTP_INPUT ->
                viewModel.setStep(AuthStep.SIGN_IN)
            AuthStep.PHONE_OTP_VERIFY ->
                viewModel.setStep(AuthStep.PHONE_OTP_INPUT)
            AuthStep.PROFILE_SETUP -> {
                viewModel.onSignOut()
                viewModel.setStep(AuthStep.SIGN_IN)
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AuthEffect.NavigateToHome              -> onNavigateToHome()
                is AuthEffect.NavigateToProfileSetup      -> viewModel.setStep(AuthStep.PROFILE_SETUP)
                is AuthEffect.NavigateBackToAccountCenter -> onNavigateToAccountCenter()
                is AuthEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is AuthEffect.ShowInfo  -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Black,
        modifier = Modifier.systemBarsPadding()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState.authStep,
                transitionSpec = {
                    slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)) togetherWith
                            slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350))
                },
                label = "AuthStepTransition"
            ) { step ->
                when (step) {
                    AuthStep.SIGN_IN -> SignInContent(
                        uiState = uiState,
                        onEmailChange = viewModel::onEmailChange,
                        onPasswordChange = viewModel::onPasswordChange,
                        onSignIn = viewModel::onEmailSignIn,
                        onGoToSignUp = { viewModel.setStep(AuthStep.SIGN_UP) },
                        onGoToForgotPassword = { viewModel.setStep(AuthStep.FORGOT_PASSWORD) },
                        onGoToPhoneAuth = { viewModel.setStep(AuthStep.PHONE_OTP_INPUT) },
                        onGoogleSignIn = { scope.launch { snackbarHostState.showSnackbar("Google Sign-In will be implemented once Google Cloud APIs are fully set up for Android.") } }
                    )
                    AuthStep.SIGN_UP -> SignUpContent(
                        uiState = uiState,
                        onEmailChange = viewModel::onEmailChange,
                        onPasswordChange = viewModel::onPasswordChange,
                        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
                        onSignUp = viewModel::onEmailSignUp,
                        onGoToSignIn = { viewModel.setStep(AuthStep.SIGN_IN) }
                    )
                    AuthStep.FORGOT_PASSWORD -> ForgotPasswordContent(
                        uiState = uiState,
                        onEmailChange = viewModel::onEmailChange,
                        onSendReset = viewModel::onForgotPassword,
                        onGoBack = { viewModel.setStep(AuthStep.SIGN_IN) }
                    )
                    AuthStep.PHONE_OTP_INPUT -> PhoneInputContent(
                        uiState = uiState,
                        onPhoneChange = viewModel::onPhoneNumberChange,
                        onSendOtp = {
                            scope.launch { snackbarHostState.showSnackbar("Firebase Phone Auth requires Activity Context.") }
                            viewModel.onSendPhoneOtp()
                        },
                        onGoBack = { viewModel.setStep(AuthStep.SIGN_IN) }
                    )
                    AuthStep.PHONE_OTP_VERIFY -> PhoneOtpVerifyContent(
                        uiState = uiState,
                        onOtpChange = viewModel::onOtpChange,
                        onVerify = viewModel::onVerifyOtp,
                        onGoBack = { viewModel.setStep(AuthStep.PHONE_OTP_INPUT) }
                    )
                    AuthStep.PROFILE_SETUP -> ProfileSetupContent(
                        uiState = uiState,
                        onUsernameChange = viewModel::onUsernameChange,
                        onDisplayNameChange = viewModel::onDisplayNameChange,
                        onComplete = {
                            viewModel.onCompleteProfile()
                        },
                        onGoBack = { viewModel.setStep(AuthStep.SIGN_IN) },
                        onImageClick = { scope.launch { snackbarHostState.showSnackbar("Image picker requires Cloudinary + Permissions setup.") } }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                }
            }
        }
    }
}
