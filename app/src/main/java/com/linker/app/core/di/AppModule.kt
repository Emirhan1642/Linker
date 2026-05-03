package com.linker.app.core.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.linker.app.core.util.SystemTimeProvider
import com.linker.app.core.util.TimeProvider
import com.linker.app.data.bluetooth.BluetoothManager
import com.linker.app.data.bluetooth.BluetoothManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App Module
 * 
 * Provides application-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
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
}
