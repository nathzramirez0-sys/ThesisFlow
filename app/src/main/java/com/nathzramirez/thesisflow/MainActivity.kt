package com.nathzramirez.thesisflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nathzramirez.thesisflow.designsystem.theme.ThesisFlowTheme
import com.nathzramirez.thesisflow.navigation.ThesisFlowRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash up until we know which screen to show, so there's no flash.
        splashScreen.setKeepOnScreenCondition { viewModel.session.value == SessionState.Loading }

        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.onLinkOpened(intent?.dataString)

        setContent {
            ThesisFlowTheme {
                ThesisFlowRoot(viewModel)
            }
        }
    }

    /** singleTop: a link tapped while the app is open arrives here instead of a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onLinkOpened(intent.dataString)
    }
}
