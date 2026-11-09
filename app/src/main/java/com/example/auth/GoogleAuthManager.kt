package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.gmail.GmailClient
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    fun ensureFirebaseInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:272024622828:web:5699185d8af04060ca77c9")
                    .setApiKey("AIzaSyAkrPkABEVDb32eYhm15xOukTbs9bPwPRg")
                    .setProjectId("gen-lang-client-0754074722")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
                Log.d(TAG, "FirebaseApp initialized successfully")
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp initialization exception: ${e.message}")
            }
        }
    }

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val auth = try {
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }

        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun getCurrentUser(): FirebaseUser? {
        return try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        ensureFirebaseInitialized(context)
        val serverClientId = context.getString(R.string.default_web_client_id)

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val userEmail = googleIdTokenCredential.id

                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                val user = authResult.user

                if (user != null) {
                    val resolvedEmail = user.email ?: userEmail
                    GmailClient.setCredentials(token = idToken, email = resolvedEmail)
                    Log.d(TAG, "Successfully authenticated $resolvedEmail with Firebase Auth and Gmail scope")
                    Result.success(user)
                } else {
                    Result.failure(IllegalStateException("Firebase Auth succeeded but user was null"))
                }
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.javaClass.name}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In prompt")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to authenticate with Google: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign out error: ${e.message}")
        }
        GmailClient.disconnect()
    }
}
