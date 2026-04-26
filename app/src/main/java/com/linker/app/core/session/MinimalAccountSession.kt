package com.linker.app.core.session

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Minimal session for passive accounts
 * 
 * Used for notification actions (reply, react, mark as read) without switching active account.
 * Keeps minimal resources to reduce memory footprint.
 */
data class MinimalAccountSession(
    val userId: String,
    val firebaseAuth: FirebaseAuth,
    val firestore: FirebaseFirestore,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    }
    
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - createdAt > SESSION_TIMEOUT_MS
    }
}
