package com.nathzramirez.thesisflow.domain.model

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val uid: String) : AuthState
}
