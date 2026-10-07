package com.nathzramirez.thesisflow

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nathzramirez.thesisflow.domain.model.AuthState
import com.nathzramirez.thesisflow.domain.repository.AuthRepository
import com.nathzramirez.thesisflow.domain.repository.ChapterRepository
import com.nathzramirez.thesisflow.domain.repository.NetworkMonitor
import com.nathzramirez.thesisflow.domain.repository.UserRepository
import com.nathzramirez.thesisflow.domain.result.onFailure
import com.nathzramirez.thesisflow.notifications.NotificationLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which part of the app to show. Derived from auth and profile state, never set by screens. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object NeedsOnboarding : SessionState
    /** Signed in, but the profile isn't cached and couldn't be fetched (usually offline). */
    data object ProfileUnavailable : SessionState
    data object Ready : SessionState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chapterRepository: ChapterRepository,
    networkMonitor: NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Drives the "offline" strip. Starts as online, so a cold start never flashes it. */
    val isOffline: StateFlow<Boolean> = networkMonitor.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val profileLoadFailed = MutableStateFlow(false)
    private var profileLoad: Job? = null

    /**
     * Sign-in, sign-out and finishing onboarding all flow through here, so no screen
     * navigates between those stages itself. Signing out anywhere returns to login.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionState> = authRepository.authState
        .flatMapLatest { auth ->
            when (auth) {
                AuthState.SignedOut -> flowOf(SessionState.SignedOut)
                is AuthState.SignedIn -> userRepository.observeCurrentUser()
                    .onEach { user -> if (user == null) loadProfile() }
                    .combine(profileLoadFailed) { user, failed ->
                        when {
                            user == null && failed -> SessionState.ProfileUnavailable
                            user == null -> SessionState.Loading
                            !user.onboardingComplete -> SessionState.NeedsOnboarding
                            else -> SessionState.Ready
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    /** An invite code from a link, kept until the user is signed in and onboarded. */
    val pendingInviteCode: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_PENDING_INVITE, null)

    fun onLinkOpened(url: String?) {
        InviteLinks.parseCode(url, BuildConfig.INVITE_HOST)?.let { code ->
            savedStateHandle[KEY_PENDING_INVITE] = code
        }
    }

    fun onInviteHandled() {
        savedStateHandle[KEY_PENDING_INVITE] = null
    }

    private val _pendingNotificationLink = MutableStateFlow<NotificationLink?>(null)

    /** A tapped notification's screen, kept until the main graph is showing to open it. */
    val pendingNotificationLink: StateFlow<NotificationLink?> = _pendingNotificationLink.asStateFlow()

    fun onNotificationOpened(link: NotificationLink) {
        _pendingNotificationLink.value = link
    }

    fun onNotificationHandled() {
        _pendingNotificationLink.value = null
    }

    /**
     * The chapter's position in its list, which the detail screen shows as its
     * number; null when the chapter isn't on this phone (deleted, or not synced yet).
     */
    suspend fun chapterNumber(groupId: String, chapterId: String): Int? =
        chapterRepository.observeChapters(groupId).first()
            .indexOfFirst { it.id == chapterId }
            .takeIf { it >= 0 }
            ?.plus(1)

    fun retryProfile() = loadProfile()

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Fetches (or creates) the profile when the cache has none, e.g. right after signing in. */
    private fun loadProfile() {
        if (profileLoad?.isActive == true) return
        profileLoadFailed.value = false
        profileLoad = viewModelScope.launch {
            userRepository.refreshCurrentUser().onFailure { profileLoadFailed.value = true }
        }
    }

    private companion object {
        const val KEY_PENDING_INVITE = "pendingInviteCode"
    }
}
