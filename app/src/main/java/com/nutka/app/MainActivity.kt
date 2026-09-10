package com.nutka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nutka.app.ui.NutkaApp
import com.nutka.app.ui.theme.NutkaTheme

class MainActivity : ComponentActivity() {

    // Same activity-scoped instance Compose's viewModel() resolves to, so this
    // is the running app's ViewModel, not a second copy.
    private val viewModel: NutkaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutkaTheme {
                NutkaApp()
            }
        }
    }

    /**
     * The Downloads folder is usually filled while Nutka is in the background —
     * the user leaves, saves a voice note from another app, and comes back. So
     * the folder is re-read on every resume rather than only at startup.
     */
    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}
