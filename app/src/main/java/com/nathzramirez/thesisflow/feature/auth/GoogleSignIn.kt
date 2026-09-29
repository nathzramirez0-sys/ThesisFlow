package com.nathzramirez.thesisflow.feature.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.nathzramirez.thesisflow.R
import com.nathzramirez.thesisflow.domain.result.AppResult
import com.nathzramirez.thesisflow.domain.result.DomainError

/**
 * Asks Credential Manager for a Google ID token. This lives in the UI layer
 * because the account picker needs an Activity context; the ViewModel only
 * receives the token and hands it to Firebase.
 *
 * `default_web_client_id` is generated from google-services.json once Google
 * sign-in is enabled in the Firebase console.
 */
object GoogleSignIn {

    suspend fun requestIdToken(activityContext: Context): AppResult<String> {
        val option = GetSignInWithGoogleOption
            .Builder(activityContext.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val credential = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                AppResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                AppResult.Failure(DomainError.Unknown("Unexpected credential type ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            AppResult.Failure(DomainError.SignInCancelled)
        } catch (e: NoCredentialException) {
            AppResult.Failure(DomainError.NoGoogleAccount)
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Invalid Google ID token", e)
            AppResult.Failure(DomainError.Unknown(e.message))
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed", e)
            AppResult.Failure(DomainError.Unknown(e.message))
        }
    }

    private const val TAG = "GoogleSignIn"
}
