package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.linker.app.domain.usecase.user.CurrentUserProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CurrentUserProvider using FirebaseAuth
 */
@Singleton
class CurrentUserProviderImpl @Inject constructor(
    private val auth: FirebaseAuth
) : CurrentUserProvider {

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override fun getCurrentUserDisplayName(): String? {
        return auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
    }
}
