package com.linker.app.presentation.screens.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Auth Host Screen
 *
 * Manages Sign-In, Sign-Up, Forgot Password, Phone OTP and Profile Setup
 * sub-screens based on AuthStep state from ViewModel.
 */
@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Handle system back button for inner auth steps
    androidx.activity.compose.BackHandler(enabled = uiState.authStep != AuthStep.SIGN_IN) {
        when (uiState.authStep) {
            AuthStep.SIGN_UP, AuthStep.FORGOT_PASSWORD, AuthStep.PHONE_OTP_INPUT -> viewModel.setStep(AuthStep.SIGN_IN)
            AuthStep.PHONE_OTP_VERIFY -> viewModel.setStep(AuthStep.PHONE_OTP_INPUT)
            AuthStep.PROFILE_SETUP -> {
                // Usually we shouldn't allow backing out of profile setup easily, 
                // but if we do, maybe sign out or just go back to SignIn.
                viewModel.onSignOut()
                viewModel.setStep(AuthStep.SIGN_IN)
            }
            else -> {}
        }
    }

    // Handle side-effects (navigation, toasts)
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is AuthEffect.NavigateToHome -> onNavigateToHome()
                is AuthEffect.NavigateToProfileSetup -> viewModel.setStep(AuthStep.PROFILE_SETUP)
                is AuthEffect.ShowError -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                is AuthEffect.ShowInfo -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Full-screen dark background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding()
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
                    onGoogleSignIn = { Toast.makeText(context, "Google Sign-In will be implemented once Google Cloud APIs are fully set up for Android.", Toast.LENGTH_LONG).show() }
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
                        Toast.makeText(context, "Firebase Phone Auth requires Activity Context. In a real app, this launches standard Firebase reCAPTCHA verification.", Toast.LENGTH_LONG).show() 
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
                        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            viewModel.onCompleteProfile(uid)
                        } else {
                            Toast.makeText(context, "User not found. Please sign in again.", Toast.LENGTH_SHORT).show()
                            viewModel.setStep(AuthStep.SIGN_IN)
                        }
                    },
                    onGoBack = { viewModel.setStep(AuthStep.SIGN_IN) },
                    onImageClick = { Toast.makeText(context, "Image picker requires Cloudinary + Permissions setup in the next steps.", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        // Loading Overlay
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

// ═══════════════════════════════════════════════════════════════════════════════
//  SIGN IN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SignInContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
    onGoToForgotPassword: () -> Unit,
    onGoToPhoneAuth: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Logo
        LinkerLogo()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Welcome Back",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Sign in to continue",
            color = TextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Email
        LinkerTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            placeholder = "Email address",
            keyboardType = KeyboardType.Email,
            errorMessage = uiState.emailError,
            leadingIcon = R.drawable.ic_enhance_user_ai_outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password
        LinkerTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            placeholder = "Password",
            isPassword = true,
            errorMessage = uiState.passwordError,
            leadingIcon = R.drawable.ic_smart_lock_ai_outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Forgot Password
        Text(
            "Forgot password?",
            color = LightPurple,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onGoToForgotPassword() }
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sign In Button
        LinkerGradientButton(text = "Sign In", onClick = onSignIn)

        Spacer(modifier = Modifier.height(24.dp))

        // Divider
        OrDivider()

        Spacer(modifier = Modifier.height(24.dp))

        // Social Buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialButton(iconRes = R.drawable.ic_link_3_outline, label = "Google", onClick = onGoogleSignIn)
            SocialButton(iconRes = R.drawable.ic_ai_send_message_outline, label = "Phone", onClick = onGoToPhoneAuth)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sign Up Prompt
        Row {
            Text("Don't have an account? ", color = TextSecondary, fontSize = 14.sp)
            Text(
                "Sign Up",
                color = AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onGoToSignUp() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SIGN UP
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SignUpContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSignUp: () -> Unit,
    onGoToSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        LinkerLogo()

        Spacer(modifier = Modifier.height(12.dp))

        Text("Create Account", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Join the Linker community", color = TextSecondary, fontSize = 15.sp)

        Spacer(modifier = Modifier.height(40.dp))

        // Email
        LinkerTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            placeholder = "Email address",
            keyboardType = KeyboardType.Email,
            errorMessage = uiState.emailError,
            leadingIcon = R.drawable.ic_enhance_user_ai_outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password
        LinkerTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            placeholder = "Password (min 8 characters)",
            isPassword = true,
            errorMessage = uiState.passwordError,
            leadingIcon = R.drawable.ic_smart_lock_ai_outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Password
        LinkerTextField(
            value = uiState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Confirm password",
            isPassword = true,
            errorMessage = if (uiState.confirmPassword.isNotEmpty() && uiState.password != uiState.confirmPassword) "Passwords do not match" else null,
            leadingIcon = R.drawable.ic_smart_lock_ai_outline
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerGradientButton(text = "Sign Up", onClick = onSignUp)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "By signing up, you agree to our Terms of Service\nand Privacy Policy",
            color = TextHint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row {
            Text("Already have an account? ", color = TextSecondary, fontSize = 14.sp)
            Text(
                "Sign In",
                color = AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onGoToSignIn() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  FORGOT PASSWORD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ForgotPasswordContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSendReset: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Lock Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_smart_lock_ai_outline),
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Reset Password", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Enter the email associated with your\naccount and we'll send a reset link",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            placeholder = "Email address",
            keyboardType = KeyboardType.Email,
            errorMessage = uiState.emailError,
            leadingIcon = R.drawable.ic_enhance_user_ai_outline
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerGradientButton(text = "Send Reset Link", onClick = onSendReset)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Back to Sign In",
            color = LightPurple,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onGoBack() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PHONE INPUT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PhoneInputContent(
    uiState: AuthUiState,
    onPhoneChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ai_send_message_outline),
                contentDescription = null,
                tint = InfoBlue,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Phone Verification", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Enter your phone number with country\ncode to receive a verification code",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerTextField(
            value = uiState.phoneNumber,
            onValueChange = onPhoneChange,
            placeholder = "+90 5XX XXX XXXX",
            keyboardType = KeyboardType.Phone,
            errorMessage = uiState.phoneError,
            leadingIcon = R.drawable.ic_ai_send_message_outline
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerGradientButton(text = "Send Code", onClick = onSendOtp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Use Email Instead",
            color = LightPurple,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onGoBack() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  OTP VERIFY
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PhoneOtpVerifyContent(
    uiState: AuthUiState,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text("Enter Code", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "We sent a 6-digit code to\n${uiState.phoneNumber}",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // OTP Input Boxes
        OtpInputRow(
            otp = uiState.otp,
            onOtpChange = onOtpChange,
            otpLength = 6,
            errorMessage = uiState.otpError
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinkerGradientButton(text = "Verify", onClick = onVerify)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Didn't receive the code? Resend",
            color = LightPurple,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { /* TODO: resend */ }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PROFILE SETUP
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileSetupContent(
    uiState: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onComplete: () -> Unit,
    onGoBack: () -> Unit,
    onImageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Picture Placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(3.dp, LinkerAngularGradient, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(LightGray)
                .clickable { onImageClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_outline),
                contentDescription = "Add photo",
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Tap to add photo", color = TextHint, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(32.dp))

        Text("Complete Your Profile", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Choose a unique username", color = TextSecondary, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(32.dp))

        LinkerTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            placeholder = "Username (e.g. alex_145)",
            errorMessage = uiState.usernameError,
            leadingIcon = R.drawable.ic_profile_outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        LinkerTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChange,
            placeholder = "Display name",
            leadingIcon = R.drawable.ic_user_edit_outline
        )

        Spacer(modifier = Modifier.height(40.dp))

        LinkerGradientButton(text = "Let's Go!", onClick = onComplete)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Linker Logo with gradient text simulation
 */
@Composable
private fun LinkerLogo() {
    Text(
        text = "Linker",
        fontSize = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(GradientRed, GradientYellow, GradientGreen, GradientBlue, GradientPurple)
            )
        )
    )
}

/**
 * Custom dark themed text field matching the Linker design
 */
@Composable
private fun LinkerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: Int? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkGray)
                .border(
                    width = 1.5.dp,
                    color = if (errorMessage != null) ErrorRed else LightGray,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(id = leadingIcon),
                    contentDescription = null,
                    tint = TextHint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                singleLine = true,
                cursorBrush = SolidColor(AccentGreen),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextHint, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            if (isPassword) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (passwordVisible) R.drawable.ic_security_safe_outline else R.drawable.ic_smart_lock_ai_outline
                        ),
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = TextHint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                errorMessage,
                color = ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Gradient accent button
 */
@Composable
private fun LinkerGradientButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(GradientPurple, GradientBlue, GradientGreen)
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * "or" divider
 */
@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(LightGray)
        )
        Text(
            "  or  ",
            color = TextHint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(LightGray)
        )
    }
}

/**
 * Social sign-in circular button
 */
@Composable
private fun SocialButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkGray)
                .border(1.5.dp, LightGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

/**
 * 6-digit OTP input row
 */
@Composable
private fun OtpInputRow(
    otp: String,
    onOtpChange: (String) -> Unit,
    otpLength: Int = 6,
    errorMessage: String? = null
) {
    val focusRequester = remember { FocusRequester() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Hidden text field that captures keyboard input
        BasicTextField(
            value = otp,
            onValueChange = { if (it.length <= otpLength && it.all { c -> c.isDigit() }) onOtpChange(it) },
            modifier = Modifier
                .size(1.dp) // Invisible
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // Visual OTP boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.clickable { focusRequester.requestFocus() }
        ) {
            repeat(otpLength) { index ->
                val char = otp.getOrNull(index)?.toString() ?: ""
                val isFocused = otp.length == index

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkGray)
                        .border(
                            width = 2.dp,
                            brush = if (isFocused) LinkerAngularGradient else SolidColor(LightGray),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = ErrorRed, fontSize = 12.sp)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
