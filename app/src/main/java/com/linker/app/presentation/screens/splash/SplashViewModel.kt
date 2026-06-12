package com.linker.app.presentation.screens.splash

import androidx.lifecycle.ViewModel
import com.linker.app.domain.model.AccountSession
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    /**
     * Başlangıç noktasını belirler:
     *  1. Firebase oturumu yok            → AUTH
     *  2. Firestore'da profil tamamlanmamış → AUTH  (ProfileSetup'a düşer)
     *  3. Profil tam                       → HOME
     *
     * NOT: Splash'ta artık session kaydedilmiyor.
     * Session kaydı yalnızca kullanıcı email+password girerek giriş yaptığında
     * AuthViewModel.saveSession() tarafından yapılır.
     *
     * Neden? Çünkü Splash'ta elimizde şifre yoktur ve AccountRepository
     * hesap geçişi için email::password credential gerektirir.
     * (Firebase Android SDK refreshToken ile oturum açmayı desteklemiyor)
     *
     * Uygulama tekrar açıldığında Firebase Auth oturumu hâlâ aktifse
     * currentUser zaten doğru kullanıcıyı gösterir, session listeyi
     * görüntülemek için sadece AccountRepository.observeSessions() yeterlidir.
     * EncryptedSharedPreferences'daki session'lar persist edildiğinden
     * uygulama açılışlarında kaybolmaz.
     */
    suspend fun resolveStartDestination(): SplashDestination {
        val result = userRepository.getCurrentUser()
        val user = (result as? com.linker.app.core.util.Result.Success)?.data ?: return SplashDestination.AUTH
        val hasCompleteProfile = user.hasCompletedOnboarding()
        return if (hasCompleteProfile) SplashDestination.HOME else SplashDestination.AUTH
    }
}
