package com.sportage.companion.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.sportage.companion.data.model.UserSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val currentUser: Flow<UserSession?>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signUp(email: String, password: String): Result<UserSession>
    suspend fun signInWithGoogle(idToken: String): Result<UserSession>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    fun signOut()
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override val currentUser: Flow<UserSession?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            trySend(u?.let { UserSession(it.uid, it.email) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): Result<UserSession> = runCatching {
        val r = auth.signInWithEmailAndPassword(email, password).await()
        UserSession(r.user!!.uid, r.user!!.email)
    }

    override suspend fun signUp(email: String, password: String): Result<UserSession> = runCatching {
        val r = auth.createUserWithEmailAndPassword(email, password).await()
        UserSession(r.user!!.uid, r.user!!.email)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserSession> = runCatching {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        val r = auth.signInWithCredential(cred).await()
        UserSession(r.user!!.uid, r.user!!.email)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    override fun signOut() = auth.signOut()
}