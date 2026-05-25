package com.linker.app.core.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.linker.app.core.util.SystemTimeProvider
import com.linker.app.core.util.TimeProvider
import com.linker.app.data.bluetooth.BluetoothManager
import com.linker.app.data.bluetooth.BluetoothManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * App Module
 * 
 * Provides application-level dependencies including Firebase services.
 * 
 * **Firebase Initialization:**
 * Firebase is initialized in LinkerApp.onCreate() before this module is used.
 * All providers here use getInstance() which requires prior initialization.
 * 
 * **Architecture:**
 * - Application-scoped singletons
 * - Firebase services configured with offline persistence
 * - Bluetooth and time utilities
 * 
 * @see com.linker.app.LinkerApp.initializeFirebase
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    /**
     * Provides Firebase Auth instance
     * 
     * CRITICAL: Firebase must be initialized in Application.onCreate() first.
     * This provider validates Firebase is ready before returning the instance.
     * 
     * @throws IllegalStateException if Firebase is not initialized
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        try {
            // Validate Firebase is initialized
            val app = FirebaseApp.getInstance()
            require(!app.options.projectId.isNullOrEmpty()) {
                "Firebase not properly initialized"
            }
            
            return FirebaseAuth.getInstance()
        } catch (e: IllegalStateException) {
            android.util.Log.e("AppModule", "Firebase Auth initialization failed", e)
            throw IllegalStateException(
                "Firebase must be initialized in Application.onCreate() before accessing Auth",
                e
            )
        }
    }
    
    /**
     * Provides Firebase Firestore instance with offline persistence
     * 
     * CONFIGURATION:
     * - Offline persistence enabled for better UX
     * - Cache size: 100 MB (default)
     * - SSL enabled for security
     * 
     * @throws IllegalStateException if Firebase is not initialized
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        try {
            // Validate Firebase is initialized
            val app = FirebaseApp.getInstance()
            require(!app.options.projectId.isNullOrEmpty()) {
                "Firebase not properly initialized"
            }
            
            val firestore = FirebaseFirestore.getInstance()
            
            // Configure Firestore with offline persistence
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)  // Enable offline persistence
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)  // Unlimited cache
                .build()
            
            firestore.firestoreSettings = settings
            
            android.util.Log.d("AppModule", "Firestore configured with offline persistence")
            
            return firestore
        } catch (e: IllegalStateException) {
            android.util.Log.e("AppModule", "Firebase Firestore initialization failed", e)
            throw IllegalStateException(
                "Firebase must be initialized in Application.onCreate() before accessing Firestore",
                e
            )
        }
    }
    
    /**
     * Provides Firebase Storage instance
     * 
     * CRITICAL: Firebase must be initialized in Application.onCreate() first.
     * 
     * @throws IllegalStateException if Firebase is not initialized
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        try {
            // Validate Firebase is initialized
            val app = FirebaseApp.getInstance()
            require(!app.options.projectId.isNullOrEmpty()) {
                "Firebase not properly initialized"
            }
            
            return FirebaseStorage.getInstance()
        } catch (e: IllegalStateException) {
            android.util.Log.e("AppModule", "Firebase Storage initialization failed", e)
            throw IllegalStateException(
                "Firebase must be initialized in Application.onCreate() before accessing Storage",
                e
            )
        }
    }
    
    /**
     * Provides Firebase Analytics instance
     * 
     * ANALYTICS:
     * - User behavior tracking
     * - Screen view tracking
     * - Custom event logging
     * - User properties
     * 
     * PRIVACY:
     * - Respects user consent
     * - Can be disabled in debug builds
     * - GDPR compliant when properly configured
     * 
     * @throws IllegalStateException if Firebase is not initialized
     */
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(
        @ApplicationContext context: Context
    ): com.google.firebase.analytics.FirebaseAnalytics {
        try {
            // Validate Firebase is initialized
            val app = FirebaseApp.getInstance()
            require(!app.options.projectId.isNullOrEmpty()) {
                "Firebase not properly initialized"
            }
            
            val analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
            
            // Disable analytics in debug builds (optional)
            // analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
            
            android.util.Log.d("AppModule", "Firebase Analytics initialized")
            
            return analytics
        } catch (e: IllegalStateException) {
            android.util.Log.e("AppModule", "Firebase Analytics initialization failed", e)
            throw IllegalStateException(
                "Firebase must be initialized in Application.onCreate() before accessing Analytics",
                e
            )
        }
    }
    
    @Provides
    @Singleton
    fun provideBluetoothManager(impl: BluetoothManagerImpl): BluetoothManager {
        return impl
    }
    
    @Provides
    @Singleton
    fun provideTimeProvider(impl: SystemTimeProvider): TimeProvider {
        return impl
    }
    
    /**
     * Provides Firebase Remote Config instance
     * 
     * @throws IllegalStateException if Firebase is not initialized
     */
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        try {
            // Validate Firebase is initialized
            val app = FirebaseApp.getInstance()
            require(!app.options.projectId.isNullOrEmpty()) {
                "Firebase not properly initialized"
            }
            
            return FirebaseRemoteConfig.getInstance()
        } catch (e: IllegalStateException) {
            android.util.Log.e("AppModule", "Firebase Remote Config initialization failed", e)
            throw IllegalStateException(
                "Firebase must be initialized in Application.onCreate() before accessing Remote Config",
                e
            )
        }
    }
    
    /**
     * Provides application-scoped CoroutineScope
     * 
     * LIFECYCLE: Lives for the entire application lifetime.
     * Use this for long-running operations that should survive activity/fragment lifecycle.
     * 
     * CONFIGURATION:
     * - SupervisorJob: Child coroutine failures don't cancel siblings
     * - Dispatchers.Default: Optimized for CPU-intensive work
     * 
     * USAGE:
     * ```kotlin
     * class MyManager @Inject constructor(
     *     @ApplicationScope private val scope: CoroutineScope
     * ) {
     *     fun startBackgroundWork() {
     *         scope.launch { /* work */ }
     *     }
     * }
     * ```
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * Qualifier for application-scoped CoroutineScope
 * 
 * Use this to inject a CoroutineScope that lives for the entire app lifetime.
 * Prevents memory leaks from manually created scopes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
